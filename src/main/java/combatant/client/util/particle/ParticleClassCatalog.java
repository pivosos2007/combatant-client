/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.particle;

import combatant.client.util.logging.DebugLog;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.particle.Particle;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Catalog of client particle implementation classes.
 *
 * <p>Vanilla entries are discovered directly from Minecraft class files, so the picker is complete
 * before a particle has ever spawned. Runtime observations are merged on top for modded particle
 * implementations which are not part of the Minecraft container.</p>
 *
 * <p>The picker stores the actual runtime binary class name. This is important on remapped Fabric
 * runtimes: {@link Particle#getClass()} and the catalog still use exactly the same namespace.</p>
 */
public enum ParticleClassCatalog {
    ;

    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id, String.CASE_INSENSITIVE_ORDER);

    private static final Set<String> OBSERVED = ConcurrentHashMap.newKeySet();
    private static volatile List<Entry> mergedSnapshot;
    private static volatile CompletableFuture<List<Entry>> vanillaDiscovery;

    /**
     * Starts vanilla class discovery off-thread. Safe to call repeatedly during client prewarm.
     */
    public static void prewarm(Executor executor) {
        if (executor == null || vanillaDiscovery != null) return;
        synchronized (ParticleClassCatalog.class) {
            if (vanillaDiscovery == null) {
                vanillaDiscovery = CompletableFuture.supplyAsync(ParticleClassCatalog::discoverVanillaEntries, executor);
            }
        }
    }

    public static void observe(Particle particle) {
        if (particle == null) return;
        String className = particle.getClass().getName();
        if (className.isBlank() || !OBSERVED.add(className)) return;
        mergedSnapshot = null;
    }

    public static List<Entry> entries(Set<String> selectedIds) {
        LinkedHashMap<String, Entry> merged = new LinkedHashMap<>();
        for (Entry entry : discoveredEntries()) {
            merged.putIfAbsent(entry.id(), entry);
        }

        if (selectedIds != null) {
            for (String id : selectedIds) {
                if (id == null || id.isBlank()) continue;
                merged.putIfAbsent(id, new Entry(id, labelForClassName(id)));
            }
        }

        List<Entry> out = new ArrayList<>(merged.values());
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<Entry> discoveredEntries() {
        List<Entry> snapshot = mergedSnapshot;
        if (snapshot != null) return snapshot;

        synchronized (ParticleClassCatalog.class) {
            snapshot = mergedSnapshot;
            if (snapshot != null) return snapshot;

            LinkedHashMap<String, Entry> found = new LinkedHashMap<>();
            for (Entry entry : awaitVanillaEntries()) {
                found.putIfAbsent(entry.id(), entry);
            }
            for (String className : OBSERVED) {
                found.putIfAbsent(className, new Entry(className, labelForClassName(className)));
            }

            ArrayList<Entry> sorted = new ArrayList<>(found.values());
            sorted.sort(ENTRY_ORDER);
            mergedSnapshot = List.copyOf(sorted);
            return mergedSnapshot;
        }
    }

    /**
     * The picker needs a complete vanilla list on first open. Prewarm normally finishes this before
     * ClickGUI is opened; if it did not, the first particle picker waits for that one shared scan
     * instead of returning a permanently/temporarily empty catalog.
     */
    private static List<Entry> awaitVanillaEntries() {
        CompletableFuture<List<Entry>> future = vanillaDiscovery;
        if (future == null) {
            synchronized (ParticleClassCatalog.class) {
                future = vanillaDiscovery;
                if (future == null) {
                    future = CompletableFuture.completedFuture(discoverVanillaEntries());
                    vanillaDiscovery = future;
                }
            }
        }

        try {
            return future.join();
        } catch (RuntimeException error) {
            DebugLog.warnOnce(
                    "particle-class-catalog-discovery",
                    "Failed to discover vanilla particle classes: %s",
                    error
            );
            return List.of();
        }
    }

    private static List<Entry> discoverVanillaEntries() {
        ModContainer minecraft = FabricLoader.getInstance().getModContainer("minecraft").orElse(null);
        if (minecraft == null || minecraft.getRootPaths().isEmpty()) {
            DebugLog.warnOnce(
                    "particle-class-catalog-no-minecraft-root",
                    "Cannot discover vanilla particle classes: Minecraft mod root is unavailable"
            );
            return List.of();
        }

        String particleInternalName = Particle.class.getName().replace('.', '/');
        String runtimePackage = Particle.class.getPackageName();
        String runtimePackagePath = runtimePackage.replace('.', '/');

        Map<String, String> superByClass = new HashMap<>();
        for (Path root : minecraft.getRootPaths()) {
            scanMinecraftRoot(root, runtimePackagePath, superByClass);
        }

        if (!superByClass.containsKey(particleInternalName)) {
            // Some loader roots do not expose package directories in the same shape as the runtime
            // class name. Fall back to a full Minecraft-container header scan before giving up.
            superByClass.clear();
            for (Path root : minecraft.getRootPaths()) {
                scanClassHeaders(root, superByClass);
            }
        }

        Map<String, Boolean> descendantMemo = new HashMap<>();
        ArrayList<Entry> out = new ArrayList<>();
        for (String internalName : superByClass.keySet()) {
            if (particleInternalName.equals(internalName)) continue;
            if (!isParticleSubclass(internalName, particleInternalName, superByClass, descendantMemo, new HashSet<>())) {
                continue;
            }

            String binaryName = internalName.replace('/', '.');
            out.add(new Entry(binaryName, labelForClassName(binaryName)));
        }

        out.sort(ENTRY_ORDER);
        return List.copyOf(out);
    }

    private static void scanMinecraftRoot(Path root, String runtimePackagePath, Map<String, String> superByClass) {
        if (root == null) return;
        Path packageRoot = root.resolve(runtimePackagePath);
        if (!Files.isDirectory(packageRoot)) return;
        scanClassHeaders(packageRoot, superByClass);
    }

    private static void scanClassHeaders(Path start, Map<String, String> superByClass) {
        if (start == null || !Files.exists(start)) return;

        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> readClassHeader(path, superByClass));
        } catch (IOException error) {
            DebugLog.warnOnce(
                    "particle-class-catalog-scan:" + start,
                    "Failed to scan Minecraft classes for particle implementations at %s: %s",
                    start,
                    error
            );
        }
    }

    private static void readClassHeader(Path path, Map<String, String> superByClass) {
        try (InputStream input = Files.newInputStream(path)) {
            ClassReader reader = new ClassReader(input);
            String className = reader.getClassName();
            if (className == null || className.isBlank()) return;
            superByClass.putIfAbsent(className, reader.getSuperName());
        } catch (IOException | RuntimeException error) {
            // One malformed/unreadable class must not poison the complete catalog.
            DebugLog.warnOnce(
                    "particle-class-catalog-class:" + path,
                    "Failed to inspect class %s while discovering particles: %s",
                    path,
                    error
            );
        }
    }

    private static boolean isParticleSubclass(
            String className,
            String particleClassName,
            Map<String, String> superByClass,
            Map<String, Boolean> memo,
            Set<String> visiting
    ) {
        Boolean cached = memo.get(className);
        if (cached != null) return cached;
        if (!visiting.add(className)) {
            memo.put(className, false);
            return false;
        }

        String superName = superByClass.get(className);
        boolean matches;
        if (superName == null) {
            matches = false;
        } else if (particleClassName.equals(superName)) {
            matches = true;
        } else {
            matches = isParticleSubclass(superName, particleClassName, superByClass, memo, visiting);
        }

        visiting.remove(className);
        memo.put(className, matches);
        return matches;
    }

    public static String labelForClassName(String className) {
        if (className == null || className.isBlank()) return "";

        int packageSplit = className.lastIndexOf('.');
        String localName = packageSplit >= 0 ? className.substring(packageSplit + 1) : className;
        String[] nested = localName.split("\\$");
        StringBuilder label = new StringBuilder(localName.length() + 8);
        for (String part : nested) {
            if (part == null || part.isBlank()) continue;
            if (!label.isEmpty()) label.append(" · ");
            label.append(humanize(part));
        }
        return label.length() == 0 ? className : label.toString();
    }

    private static String humanize(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return "";

        String base = simpleName;
        if (base.endsWith("Particle") && base.length() > "Particle".length()) {
            base = base.substring(0, base.length() - "Particle".length());
        }

        StringBuilder out = new StringBuilder(base.length() + 8);
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            char prev = i > 0 ? base.charAt(i - 1) : 0;
            char next = i + 1 < base.length() ? base.charAt(i + 1) : 0;
            boolean boundary = i > 0 && Character.isUpperCase(ch)
                    && (Character.isLowerCase(prev) || (next != 0 && Character.isLowerCase(next)));
            if (boundary) out.append(' ');
            out.append(ch);
        }

        String text = out.toString().replace('_', ' ').trim();
        if (text.isEmpty()) return simpleName;
        if (text.length() == 1) return text.toUpperCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public record Entry(String id, String label) {
    }
}
