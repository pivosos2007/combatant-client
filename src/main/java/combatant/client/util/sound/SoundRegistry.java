/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.resources.Identifier;
import combatant.client.util.logging.DebugLog;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Thread-safe lazy registry for annotated enum catalogs. */
public final class SoundRegistry {
    private static final SoundRegistry INSTANCE = new SoundRegistry();

    private final Map<SoundKey, SoundDefinition> byKey = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Identifier, SoundDefinition> byId = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<String> discoveredScopes = new HashSet<>();

    private SoundRegistry() {
    }

    public static SoundRegistry get() {
        return INSTANCE;
    }

    public SoundDefinition resolve(SoundKey key) {
        if (key == null) throw new IllegalArgumentException("Sound key cannot be null");
        SoundDefinition found = byKey.get(key);
        if (found != null) return found;
        discover(key.getClass().getPackageName());
        found = byKey.get(key);
        if (found == null) throw new IllegalArgumentException("Unregistered sound key: " + key);
        return found;
    }

    public SoundDefinition find(Identifier id) {
        return id == null ? null : byId.get(id);
    }

    private synchronized void registerCatalog(Class<?> type) {
        if (type == null || !type.isEnum() || !SoundKey.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Sound catalog must be an enum implementing SoundKey");
        }

        SoundCatalog catalog = type.getAnnotation(SoundCatalog.class);
        if (catalog == null) {
            throw new IllegalArgumentException(type.getName() + " must be annotated with @SoundCatalog");
        }

        Object[] constants = type.getEnumConstants();
        if (constants == null) return;
        for (Object constant : constants) {
            SoundKey key = (SoundKey) constant;
            if (byKey.containsKey(key)) continue;
            String name = ((Enum<?>) constant).name();
            SoundAsset asset = annotation(type, name);
            SoundDefinition definition = definition(catalog, asset, name);
            SoundDefinition duplicate = byId.putIfAbsent(definition.id(), definition);
            if (duplicate != null && !duplicate.equals(definition)) {
                throw new IllegalStateException("Duplicate sound id " + definition.id());
            }
            byKey.put(key, duplicate == null ? definition : duplicate);
        }
    }

    /** Discovers every annotated catalog without initializing unrelated scanned classes. */
    public synchronized void discover(String... basePackages) {
        String[] packages = basePackages == null ? new String[0] : Arrays.stream(basePackages)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .toArray(String[]::new);
        String scope = packages.length == 0 ? "*" : String.join(";", packages);
        if (discoveredScopes.contains("*") || !discoveredScopes.add(scope)) return;
        ClassGraph graph = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .ignoreClassVisibility();
        if (packages.length > 0) graph = graph.acceptPackages(packages);
        try (ScanResult scan = graph.scan()) {
            for (ClassInfo info : scan.getClassesWithAnnotation(SoundCatalog.class.getName())) {
                try {
                    Class<?> type = Class.forName(info.getName(), false, SoundRegistry.class.getClassLoader());
                    registerCatalog(type);
                } catch (Throwable error) {
                    DebugLog.error("Failed to register sound catalog: %s", error, info.getName());
                }
            }
        } catch (Throwable error) {
            discoveredScopes.remove(scope);
            throw new IllegalStateException("Sound catalog discovery failed for " + scope, error);
        }
    }

    public Map<Identifier, SoundDefinition> snapshot() {
        synchronized (byId) {
            return Map.copyOf(byId);
        }
    }

    private static SoundAsset annotation(Class<?> type, String fieldName) {
        try {
            Field field = type.getField(fieldName);
            SoundAsset asset = field.getAnnotation(SoundAsset.class);
            if (asset == null) {
                throw new IllegalArgumentException(type.getName() + "." + fieldName + " must be annotated with @SoundAsset");
            }
            return asset;
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException("Cannot inspect sound constant " + type.getName() + "." + fieldName, e);
        }
    }

    private static SoundDefinition definition(SoundCatalog catalog, SoundAsset asset, String fieldName) {
        String localId = asset.id().isBlank() ? fieldName.toLowerCase(Locale.ROOT) : asset.id();
        String idPath = join(catalog.idPrefix(), localId);
        String resourcePath = join(catalog.root(), asset.value());
        Identifier id = Identifier.fromNamespaceAndPath(catalog.namespace(), idPath);
        Identifier resource = Identifier.fromNamespaceAndPath(catalog.namespace(), resourcePath);
        return new SoundDefinition(id, resource, asset.gain(), asset.pitch(), asset.looping(), asset.spatial(),
                asset.rolloff(), asset.referenceDistance(), asset.maxDistance());
    }

    private static String join(String left, String right) {
        String a = left == null ? "" : left.replace('\\', '/').replaceAll("^/+|/+$", "");
        String b = right == null ? "" : right.replace('\\', '/').replaceAll("^/+|/+$", "");
        return a.isEmpty() ? b : b.isEmpty() ? a : a + "/" + b;
    }
}
