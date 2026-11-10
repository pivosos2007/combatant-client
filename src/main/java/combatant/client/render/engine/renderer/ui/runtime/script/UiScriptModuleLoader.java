/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import combatant.client.config.ConfigPaths;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads UI script modules with recursive dependency inlining before V8 evaluation.
 *
 * <p>Runtime imports support side-effect imports and unaliased named imports;
 * {@code import type} is ignored. Development validation rejects imports whose bindings
 * cannot be represented by the inliner.</p>
 */
public final class UiScriptModuleLoader {
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?:(.+?)\\s+from\\s+)?['\"]([^'\"]+)['\"]\\s*;?\\s*$"
    );
    private static final Pattern IMPORTED_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private static Loaded loadExisting(ResourceManager manager, UiScriptModuleId id) throws IOException {
        if (id.hasExtension()) {
            return loadExact(manager, id);
        }
        IOException last = null;
        for (String extension : new String[]{"js", "ts"}) {
            try {
                return loadExact(manager, id.withExtension(extension));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("UI script not found: " + id);
    }

    private static Loaded loadExact(ResourceManager manager, UiScriptModuleId id) throws IOException {
        Loaded override = loadOverride(id);
        if (override != null) {
            return override;
        }
        Identifier identifier = Identifier.fromNamespaceAndPath(id.namespace(), id.resourceManagerPath());
        Optional<Resource> resource = manager.getResource(identifier);
        if (resource.isEmpty()) {
            throw new IOException("UI script not found: " + identifier);
        }
        try (var stream = resource.get().open()) {
            return new Loaded(id, identifier, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Loaded loadOverride(UiScriptModuleId id) throws IOException {
        for (Path root : overrideRoots()) {
            for (Path candidate : overrideCandidates(root, id)) {
                if (!Files.isRegularFile(candidate)) continue;
                String source = Files.readString(candidate, StandardCharsets.UTF_8);
                Identifier identifier = Identifier.fromNamespaceAndPath(id.namespace(), "ui/" + id.path());
                return new Loaded(id, identifier, source);
            }
        }
        return null;
    }

    private static Set<Path> overrideRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        String legacyNamespace = ConfigPaths.legacyNamespaceName();
        addConfiguredRoots(roots, System.getProperty(legacyNamespace + ".ui.path"));
        addConfiguredRoots(roots, System.getenv((legacyNamespace + "_UI_PATH").toUpperCase(Locale.ROOT)));
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            roots.add(gameDir.resolve(ConfigPaths.root()).resolve("ui"));
            roots.add(gameDir.resolve(legacyNamespace + "-ui"));
            roots.add(gameDir.resolve(legacyNamespace).resolve("ui"));
        } catch (Throwable ignored) {
        }
        roots.add(Path.of("src", "main", "resources"));
        return roots;
    }

    private static void addConfiguredRoots(Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String part : raw.split("[;|]")) {
            if (part == null || part.isBlank()) continue;
            roots.add(Path.of(part.trim()));
        }
    }

    private static Set<Path> overrideCandidates(Path root, UiScriptModuleId id) {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();
        candidates.add(root.resolve(id.resourcePath()));
        candidates.add(root.resolve(id.namespace()).resolve(id.path()));
        candidates.add(root.resolve(id.resourceManagerPath()));
        candidates.add(root.resolve(id.path()));
        return candidates;
    }

    private static String inlineImports(ResourceManager manager,
                                        UiScriptModuleId owner,
                                        String source,
                                        Set<String> seen,
                                        Deque<String> stack) throws IOException {
        if (source == null || source.isBlank()) return source != null ? source : "";
        Matcher matcher = IMPORT_PATTERN.matcher(source);
        StringBuilder imports = new StringBuilder();
        while (matcher.find()) {
            String clause = matcher.group(1);
            String spec = matcher.group(2);
            ImportKind importKind = validateImportClause(owner, clause, spec);
            if (importKind == ImportKind.TYPE_ONLY) continue;

            UiScriptModuleId dependency = resolveImport(owner, spec);
            if (dependency == null) continue;
            String key = dependency.toString();
            if (stack.contains(key)) {
                if (UiRuntimeValidation.enabled()) {
                    throw new IOException("Circular UI script import: " + importChain(stack, key));
                }
                continue;
            }
            if (!seen.add(key)) continue;
            Loaded loaded = loadExisting(manager, dependency);
            stack.addLast(key);
            try {
                imports.append(inlineImports(manager, loaded.getId(), loaded.source(), seen, stack)).append('\n');
            } finally {
                stack.removeLast();
            }
        }
        return imports + matcher.replaceAll("");
    }

    private static ImportKind validateImportClause(UiScriptModuleId owner, String clause, String spec) throws IOException {
        if (clause == null || clause.isBlank()) return ImportKind.RUNTIME;

        String trimmed = clause.trim();
        if (trimmed.startsWith("type ")) {
            // Type-only imports have no runtime binding and must not pull declaration files into
            // the executable dependency stream. The import statement is removed after this scan.
            return ImportKind.TYPE_ONLY;
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String names = trimmed.substring(1, trimmed.length() - 1).trim();
            if (names.isEmpty()) return ImportKind.RUNTIME;
            for (String rawName : names.split(",")) {
                String name = rawName.trim();
                if (name.isEmpty()) continue;
                if (IMPORTED_NAME.matcher(name).matches()) continue;
                return unsupportedImport(owner, clause, spec);
            }
            return ImportKind.RUNTIME;
        }

        return unsupportedImport(owner, clause, spec);
    }

    private static ImportKind unsupportedImport(UiScriptModuleId owner, String clause, String spec) throws IOException {
        if (UiRuntimeValidation.enabled()) {
            throw new IOException(
                    "Unsupported UI script import in " + owner + ": import " + clause + " from \"" + spec + "\". "
                            + "Runtime modules support side-effect imports, type-only imports, and named imports without aliases."
            );
        }
        return ImportKind.RUNTIME;
    }

    private enum ImportKind {
        RUNTIME,
        TYPE_ONLY
    }

    private static String importChain(Deque<String> stack, String repeated) {
        StringJoiner chain = new StringJoiner(" -> ");
        for (String entry : stack) chain.add(entry);
        chain.add(repeated);
        return chain.toString();
    }

    private static UiScriptModuleId resolveImport(UiScriptModuleId owner, String spec) {
        if (spec == null || spec.isBlank()) return null;
        String normalized = spec.replace('\\', '/').trim();
        if (!normalized.startsWith(".")) {
            return UiScriptModuleId.of(normalized);
        }
        String base = owner != null ? owner.path() : "";
        int slash = base.lastIndexOf('/');
        String dir = slash >= 0 ? base.substring(0, slash + 1) : "";
        java.nio.file.Path resolved = java.nio.file.Path.of(dir).resolve(normalized).normalize();
        return new UiScriptModuleId(owner != null ? owner.namespace() : "combatant", resolved.toString().replace('\\', '/'));
    }

    public UiScriptModule load(ResourceManager manager, UiScriptModuleId id) throws IOException {
        if (manager == null) {
            throw new IOException("ResourceManager is null.");
        }
        UiScriptModuleId moduleId = id != null ? id : new UiScriptModuleId("combatant", "main");
        Loaded loaded = loadExisting(manager, moduleId);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.addLast(loaded.getId().toString());
        return new UiScriptModule(
                loaded.getId(),
                UiScriptSourceKind.fromPath(loaded.getId().path()),
                inlineImports(manager, loaded.getId(), loaded.source(), seen, stack),
                new UiProps(Map.of("resource", loaded.identifier().toString()))
        );
    }

    private record Loaded(UiScriptModuleId id, Identifier identifier, String source) {
        public UiScriptModuleId getId() {
            return id;
        }
    }
}
