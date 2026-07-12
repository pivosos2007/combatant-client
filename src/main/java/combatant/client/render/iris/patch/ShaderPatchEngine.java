/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris.patch;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import combatant.client.util.logging.DebugLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public enum ShaderPatchEngine {
    ;
    private static final String INDEX_RESOURCE = "assets/combatant/shaders/iris-patches/index.json";
    private static final CopyOnWriteArrayList<String> EXTRA_MANIFEST_RESOURCES = new CopyOnWriteArrayList<>();
    private static final AtomicReference<Repository> REPOSITORY = new AtomicReference<>(Repository.load(List.of()));
    private static final Map<String, String> DIAGNOSTICS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> LOADING_SHADER_PACK_NAME = new ThreadLocal<>();
    private static final AtomicReference<String> GLOBAL_LOADING_SHADER_PACK_NAME = new AtomicReference<>();

    /**
     * Legacy validation session: all repository manifests are visible. Runtime Iris hooks should use
     * {@link #newSession(String)} so Combatant does not probe unrelated shaderpacks.
     */
    public static Session newSession() {
        Repository repository = repository();
        return new Session("<all>", repository.targetsByPath, Set.copyOf(repository.manifestsById.keySet()), false);
    }

    public static Session newSession(String shaderPackName) {
        Repository.Selection selection = repository().select(shaderPackName);
        return new Session(shaderPackName, selection.targetsByPath(), selection.manifestIds(), selection.nameGated());
    }

    public static boolean registerManifestResource(String manifestResourcePath) {
        String normalized = normalizeResourcePath(manifestResourcePath);
        if (normalized.isBlank()) return false;
        if (EXTRA_MANIFEST_RESOURCES.contains(normalized)) return true;
        EXTRA_MANIFEST_RESOURCES.add(normalized);
        REPOSITORY.set(Repository.load(List.copyOf(EXTRA_MANIFEST_RESOURCES)));
        DebugLog.info("[IrisPatch] addon manifest registered: %s", normalized);
        return true;
    }

    public static void beginShaderPackLoad(String shaderPackName) {
        if (shaderPackName == null || shaderPackName.isBlank()) {
            LOADING_SHADER_PACK_NAME.remove();
            return;
        }
        LOADING_SHADER_PACK_NAME.set(shaderPackName);
        GLOBAL_LOADING_SHADER_PACK_NAME.set(shaderPackName);
        DebugLog.info("[IrisPatch] shaderpack load begin: '%s'", shaderPackName);
    }

    public static void endShaderPackLoad(String shaderPackName) {
        String active = LOADING_SHADER_PACK_NAME.get();
        if (active != null && (shaderPackName == null || active.equals(shaderPackName))) {
            LOADING_SHADER_PACK_NAME.remove();
        }
        String global = GLOBAL_LOADING_SHADER_PACK_NAME.get();
        if (global != null && (shaderPackName == null || global.equals(shaderPackName))) {
            GLOBAL_LOADING_SHADER_PACK_NAME.compareAndSet(global, null);
        }
        DebugLog.info("[IrisPatch] shaderpack load end: '%s'", shaderPackName == null ? "" : shaderPackName);
    }

    public static String loadingShaderPackName() {
        String threadLocal = LOADING_SHADER_PACK_NAME.get();
        if (threadLocal != null && !threadLocal.isBlank()) {
            return threadLocal;
        }
        return GLOBAL_LOADING_SHADER_PACK_NAME.get();
    }

    public static List<String> diagnostics() {
        return DIAGNOSTICS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
    }

    public static List<String> targetPaths() {
        return repository().targetsByPath.keySet().stream().sorted().toList();
    }

    public static List<String> targetPaths(String shaderPackName) {
        return repository().select(shaderPackName).targetsByPath.keySet().stream().sorted().toList();
    }

    private static Repository repository() {
        return REPOSITORY.get();
    }

    private static void setDiagnostic(Target target, String value) {
        DIAGNOSTICS.put(diagnosticKey(target), value);
    }

    private static boolean contains(List<String> lines, String marker) {
        if (marker == null || marker.isBlank()) {
            return false;
        }
        for (String line : lines) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String diagnosticKey(Target target) {
        return target.manifestId + " " + target.path;
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizeResourcePath(String path) {
        if (path == null || path.isBlank()) return "";
        String normalized = path.trim().replace('\\', '/');
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }

    @FunctionalInterface
    public interface SourceProvider {
        ImmutableList<String> load(String path);
    }

    public static final class Session {
        private final String shaderPackName;
        private final Map<String, List<Target>> targetsByPath;
        private final Set<String> activeManifestIds;
        private final Map<String, Boolean> manifestApplicability = new ConcurrentHashMap<>();
        private final ThreadLocal<Boolean> preflighting = ThreadLocal.withInitial(() -> false);
        private final boolean nameGated;
        private boolean announced;

        private Session(String shaderPackName,
                        Map<String, List<Target>> targetsByPath,
                        Set<String> activeManifestIds,
                        boolean nameGated) {
            this.shaderPackName = shaderPackName == null ? "" : shaderPackName;
            this.targetsByPath = targetsByPath;
            this.activeManifestIds = activeManifestIds;
            this.nameGated = nameGated;
        }

        public boolean isActive() {
            return !targetsByPath.isEmpty();
        }

        public ImmutableList<String> patch(String path, ImmutableList<String> source, SourceProvider sourceProvider) {
            if (path == null || source == null || preflighting.get()) {
                return source;
            }

            announceOnce();
            List<Target> candidates = targetsByPath.get(normalizePath(path));
            if (candidates == null || candidates.isEmpty()) {
                return source;
            }

            DebugLog.renderThread("[IrisPatch] path=%s candidates=%d sourceLines=%d", normalizePath(path), candidates.size(), source.size());
            for (Target target : candidates) {
                if (contains(source, target.marker)) {
                    setDiagnostic(target, "already applied");
                    DebugLog.renderThread("[IrisPatch] %s %s already applied marker=%s", target.manifestId, target.path, target.marker);
                    return source;
                }
                boolean applicable = manifestApplicability.computeIfAbsent(
                        target.manifestId,
                        ignored -> preflight(target.manifestId, sourceProvider)
                );
                if (!applicable) {
                    DebugLog.renderThread("[IrisPatch] %s %s skipped: manifest preflight rejected earlier", target.manifestId, target.path);
                    continue;
                }
                try {
                    ShaderPatchCompiler.CompiledPatch compiled = ShaderPatchCompiler.compile(
                            target.program,
                            source,
                            ShaderPatchCompiler.CompileMode.APPLICATION,
                            target.stages
                    );
                    ImmutableList<String> patched = ShaderPatchCompiler.apply(compiled, source);
                    setDiagnostic(target, "applied edits=" + compiled.edits().size());
                    DebugLog.info("[IrisPatch] %s %s applied: schema=%d stages=%s edits=%d marker=%s",
                            target.manifestId,
                            target.path,
                            target.program.schemaVersion(),
                            target.stages,
                            compiled.edits().size(),
                            target.marker);
                    return patched;
                } catch (ShaderPatchCompiler.CompileException e) {
                    // Do not poison the whole manifest here. Actual source can already contain Combatant-owned
                    // includes from earlier targets, while preflight is the compatibility decision point.
                    setDiagnostic(target, "rejected after preflight: " + e.getMessage());
                    DebugLog.warn("[IrisPatch] %s %s rejected after preflight: %s", target.manifestId, target.path, e.getMessage());
                }
            }

            DebugLog.renderThread("[IrisPatch] path=%s no candidate applied", normalizePath(path));
            return source;
        }

        private void announceOnce() {
            if (announced) {
                return;
            }
            announced = true;
            if (targetsByPath.isEmpty()) {
                DebugLog.info("[IrisPatch] session inactive: shaderPack='%s' nameGated=%s matchedManifests=0", shaderPackName, nameGated);
            } else {
                DebugLog.info("[IrisPatch] session active: shaderPack='%s' nameGated=%s manifests=%d targetPaths=%d targets=%d",
                        shaderPackName,
                        nameGated,
                        activeManifestIds.size(),
                        targetsByPath.size(),
                        targetsByPath.values().stream().mapToInt(List::size).sum());
            }
        }

        private boolean preflight(String manifestId, SourceProvider sourceProvider) {
            Manifest manifest = repository().manifestsById.get(manifestId);
            if (manifest == null) {
                DebugLog.warn("[IrisPatch] preflight rejected: missing manifest %s", manifestId);
                return false;
            }
            if (!activeManifestIds.contains(manifestId)) {
                DebugLog.renderThread("[IrisPatch] preflight skipped: manifest=%s not selected for shaderPack='%s'", manifestId, shaderPackName);
                return false;
            }

            DebugLog.info("[IrisPatch] preflight start: manifest=%s shaderPack='%s' targets=%d", manifestId, shaderPackName, manifest.targets.size());
            preflighting.set(true);
            try {
                for (Target target : manifest.targets) {
                    ImmutableList<String> source = sourceProvider.load(target.path);
                    if (source == null) {
                        setDiagnostic(target, "preflight rejected: source unavailable");
                        DebugLog.warn("[IrisPatch] %s %s preflight rejected: source unavailable", target.manifestId, target.path);
                        return false;
                    }
                    try {
                        ShaderPatchCompiler.CompiledPatch compiled = ShaderPatchCompiler.compile(
                                target.program,
                                source,
                                ShaderPatchCompiler.CompileMode.PREFLIGHT,
                                target.stages
                        );
                        setDiagnostic(target, "preflight passed edits=" + compiled.edits().size());
                        DebugLog.renderThread("[IrisPatch] %s %s preflight passed: schema=%d stages=%s sourceLines=%d edits=%d",
                                target.manifestId,
                                target.path,
                                target.program.schemaVersion(),
                                target.stages,
                                source.size(),
                                compiled.edits().size());
                    } catch (ShaderPatchCompiler.CompileException e) {
                        setDiagnostic(target, "preflight rejected: " + e.getMessage());
                        DebugLog.warn("[IrisPatch] %s %s preflight rejected: %s", target.manifestId, target.path, e.getMessage());
                        return false;
                    }
                }
                DebugLog.info("[IrisPatch] preflight accepted: manifest=%s shaderPack='%s'", manifestId, shaderPackName);
                return true;
            } finally {
                preflighting.set(false);
            }
        }
    }

    private record Target(
            String manifestId,
            int priority,
            String path,
            String marker,
            Set<ShaderPatchCompiler.ShaderStage> stages,
            ShaderPatchCompiler.PatchProgram program
    ) {
    }

    private record Manifest(String id, int priority, Identity identity, List<Target> targets) {
    }

    private record Identity(String family, String profileId, String policy, Pattern packNamePattern) {
        private boolean matches(String shaderPackName) {
            if (shaderPackName == null || shaderPackName.isBlank()) {
                return false;
            }
            if (packNamePattern != null) {
                return packNamePattern.matcher(shaderPackName).matches();
            }
            String normalized = shaderPackName.toLowerCase(Locale.ROOT);
            return normalized.contains(family.toLowerCase(Locale.ROOT)) || normalized.contains(profileId.toLowerCase(Locale.ROOT));
        }
    }

    private record Repository(Map<String, List<Target>> targetsByPath, Map<String, Manifest> manifestsById) {

        private static Repository load(List<String> extraManifestResources) {
            Map<String, List<Target>> targets = new HashMap<>();
            Map<String, Manifest> manifests = new HashMap<>();
            try {
                JsonObject index = readObject(INDEX_RESOURCE);
                requireSchema(index, INDEX_RESOURCE);
                for (JsonElement element : requireArray(index, "manifests")) {
                    addManifest(targets, manifests, element.getAsString());
                }
                if (extraManifestResources != null) {
                    for (String manifestResource : extraManifestResources) {
                        addManifest(targets, manifests, manifestResource);
                    }
                }
            } catch (Exception e) {
                DebugLog.error("[IrisPatch] Failed to load shader patch repository", e);
            }
            targets.replaceAll((path, list) -> list.stream()
                    .sorted(Comparator.comparingInt(Target::priority).reversed())
                    .toList());
            return new Repository(Map.copyOf(targets), Map.copyOf(manifests));
        }

        private static void addManifest(Map<String, List<Target>> targets,
                                        Map<String, Manifest> manifests,
                                        String manifestResource)
                throws IOException, ShaderPatchCompiler.CompileException {
            Manifest manifest = loadManifest(manifestResource);
            if (manifests.putIfAbsent(manifest.id, manifest) != null) {
                throw new IllegalArgumentException("Duplicate shader patch manifest " + manifest.id);
            }
            for (Target target : manifest.targets) {
                targets.computeIfAbsent(target.path, ignored -> new ArrayList<>()).add(target);
            }
        }

        private static Manifest loadManifest(String manifestPath)
                throws IOException, ShaderPatchCompiler.CompileException {
            JsonObject manifestJson = readObject(manifestPath);
            requireSchema(manifestJson, manifestPath);
            String manifestId = requireString(manifestJson, "id");
            int priority = manifestJson.has("priority") ? manifestJson.get("priority").getAsInt() : 0;
            Identity identity = readIdentity(requireObject(manifestJson, "identity"));

            // Compatibility is descriptive. Runtime patchability is decided by preflight against the active pack.
            requireObject(manifestJson, "compatibility");

            String base = parent(manifestPath);
            ArrayList<Target> targets = new ArrayList<>();
            for (JsonElement element : requireArray(manifestJson, "targets")) {
                JsonObject targetJson = element.getAsJsonObject();
                String path = normalizePath(requireString(targetJson, "path"));
                String patchPath = resolve(base, requireString(targetJson, "patch"));
                ShaderPatchCompiler.PatchProgram program;
                try (InputStreamReader reader = readResource(patchPath)) {
                    program = ShaderPatchCompiler.parse(reader);
                }
                targets.add(new Target(
                        manifestId,
                        priority,
                        path,
                        requireString(targetJson, "marker"),
                        readStages(targetJson),
                        program
                ));
            }
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("Shader patch manifest has no targets: " + manifestId);
            }
            return new Manifest(manifestId, priority, identity, List.copyOf(targets));
        }

        private static Set<ShaderPatchCompiler.ShaderStage> readStages(JsonObject targetJson)
                throws ShaderPatchCompiler.CompileException {
            if (targetJson.has("stage")) {
                return Set.of(ShaderPatchCompiler.ShaderStage.parse(targetJson.get("stage").getAsString()));
            }
            if (!targetJson.has("stages")) {
                return Set.of();
            }
            HashSet<ShaderPatchCompiler.ShaderStage> stages = new HashSet<>();
            JsonArray array = requireArray(targetJson, "stages");
            for (JsonElement element : array) {
                stages.add(ShaderPatchCompiler.ShaderStage.parse(element.getAsString()));
            }
            return Set.copyOf(stages);
        }

        private static Identity readIdentity(JsonObject identity) {
            String family = requireString(identity, "family");
            String profile = requireString(identity, "profile");
            String policy = requireString(identity, "policy");
            Pattern namePattern = null;
            if (identity.has("packNameRegex")) {
                try {
                    namePattern = Pattern.compile(identity.get("packNameRegex").getAsString(), Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException e) {
                    throw new IllegalArgumentException("Invalid shaderpack identity regex", e);
                }
            }
            return new Identity(family, profile, policy, namePattern);
        }

        private static JsonObject readObject(String resource) throws IOException {
            try (InputStreamReader reader = readResource(resource)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }

        private static InputStreamReader readResource(String resource) throws IOException {
            InputStream stream = ShaderPatchEngine.class.getClassLoader().getResourceAsStream(resource);
            if (stream == null) {
                throw new IOException("Missing resource " + resource);
            }
            return new InputStreamReader(stream, StandardCharsets.UTF_8);
        }

        private static void requireSchema(JsonObject object, String resource) {
            if (!object.has("schemaVersion") || object.get("schemaVersion").getAsInt() != 1) {
                throw new IllegalArgumentException("Unsupported shader patch manifest schema in " + resource);
            }
        }

        private static JsonObject requireObject(JsonObject object, String key) {
            if (!object.has(key) || !object.get(key).isJsonObject()) {
                throw new IllegalArgumentException("Missing object " + key);
            }
            return object.getAsJsonObject(key);
        }

        private static JsonArray requireArray(JsonObject object, String key) {
            if (!object.has(key) || !object.get(key).isJsonArray()) {
                throw new IllegalArgumentException("Missing array " + key);
            }
            return object.getAsJsonArray(key);
        }

        private static String requireString(JsonObject object, String key) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
                throw new IllegalArgumentException("Missing string " + key);
            }
            return object.get(key).getAsString();
        }

        private static String parent(String path) {
            int separator = path.lastIndexOf('/');
            return separator < 0 ? "" : path.substring(0, separator + 1);
        }

        private static String resolve(String base, String path) {
            return path.startsWith("/") ? path.substring(1) : base + path;
        }

        private Selection select(String shaderPackName) {
            if (shaderPackName == null || shaderPackName.isBlank()) {
                return new Selection(Map.of(), Set.of(), true);
            }

            HashSet<String> selectedManifests = new HashSet<>();
            for (Manifest manifest : manifestsById.values()) {
                if (manifest.identity.matches(shaderPackName)) {
                    selectedManifests.add(manifest.id);
                }
            }
            if (selectedManifests.isEmpty()) {
                return new Selection(Map.of(), Set.of(), true);
            }

            Map<String, List<Target>> selectedTargets = new HashMap<>();
            for (Map.Entry<String, List<Target>> entry : targetsByPath.entrySet()) {
                ArrayList<Target> list = new ArrayList<>();
                for (Target target : entry.getValue()) {
                    if (selectedManifests.contains(target.manifestId)) {
                        list.add(target);
                    }
                }
                if (!list.isEmpty()) {
                    selectedTargets.put(entry.getKey(), List.copyOf(list));
                }
            }
            return new Selection(Map.copyOf(selectedTargets), Set.copyOf(selectedManifests), true);
        }

        private record Selection(Map<String, List<Target>> targetsByPath, Set<String> manifestIds, boolean nameGated) {
        }
    }
}
