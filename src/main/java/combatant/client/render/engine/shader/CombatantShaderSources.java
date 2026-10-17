/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FileUtil;
import org.apache.commons.io.IOUtils;
import combatant.client.util.logging.DebugLog;

import java.io.Reader;
import java.io.IOException;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps Combatant's RenderPipeline shader source names stable while still feeding Mojang's
 * shader reload/compilation cache with already-preprocessed GLSL source.
 */
public enum CombatantShaderSources {
    ;
    public static final String COMBATANT_NAMESPACE = "combatant";
    public static final String SHADER_PREFIX = "shaders/";
    public static final String VERT_EXTENSION = ".vert";
    public static final String FRAG_EXTENSION = ".frag";
    public static final String COMPUTE_EXTENSION = ".comp";
    public static final String TESS_CONTROL_EXTENSION = ".tesc";
    public static final String TESS_EVALUATION_EXTENSION = ".tese";
    public static final String GEOMETRY_EXTENSION = ".geom";
    public static final String ANALYTIC_CLIP_SUFFIX = "__analytic_clip";
    public static final String UI_UNDERLAY_SUFFIX = "__ui_underlay";
    private static final String ANALYTIC_CLIP_DEFINE = "#define COMBATANT_ANALYTIC_CLIP 1\n";
    private static final String UI_UNDERLAY_DEFINE = "#define COMBATANT_UI_UNDERLAY 1\n";
    private static final Pattern INCLUDE_DIRECTIVE = Pattern.compile("(?m)^[ \t]*#include[ \t]+([\"<])([^\"<>\r\n]+)[\">][ \t]*(?:\r?\n|$)");

    public static boolean isCombatantExtendedSource(Identifier id) {
        return COMBATANT_NAMESPACE.equals(id.getNamespace()) && isExtendedSourcePath(id.getPath());
    }

    public static boolean isShaderSourceOrInclude(Identifier id) {
        String path = id.getPath();
        return ShaderType.byLocation(id) != null
                || path.endsWith(".glsl")
                || path.endsWith(VERT_EXTENSION)
                || path.endsWith(FRAG_EXTENSION)
                || path.endsWith(COMPUTE_EXTENSION)
                || path.endsWith(TESS_CONTROL_EXTENSION)
                || path.endsWith(TESS_EVALUATION_EXTENSION)
                || path.endsWith(GEOMETRY_EXTENSION);
    }

    public static ShaderType typeByLocation(Identifier id) {
        ShaderType vanilla = ShaderType.byLocation(id);
        if (vanilla != null) return vanilla;

        String path = id.getPath();
        if (path.endsWith(VERT_EXTENSION)) return ShaderType.VERTEX;
        if (path.endsWith(FRAG_EXTENSION)) return ShaderType.FRAGMENT;
        return null;
    }

    public static Identifier canonicalShaderId(Identifier resourceId) {
        String path = resourceId.getPath();
        if (path.startsWith(SHADER_PREFIX)) {
            path = path.substring(SHADER_PREFIX.length());
        }
        if (path.endsWith(VERT_EXTENSION) || path.endsWith(FRAG_EXTENSION)) {
            path = path.substring(0, path.length() - 5);
        } else if (path.endsWith(".vsh") || path.endsWith(".fsh")) {
            path = path.substring(0, path.length() - 4);
        }
        return resourceId.withPath(path);
    }

    /** Deterministic, single-axis shader permutation used by analytic UI clip pipelines. */
    public static Identifier analyticClipVariantId(Identifier baseId) {
        String path = baseId.getPath();
        int extension = shaderExtensionStart(path);
        return baseId.withPath(extension >= 0
                ? path.substring(0, extension) + ANALYTIC_CLIP_SUFFIX + path.substring(extension)
                : path + ANALYTIC_CLIP_SUFFIX);
    }

    public static boolean isAnalyticClipVariant(Identifier id) {
        return id != null && id.getPath().contains(ANALYTIC_CLIP_SUFFIX);
    }

    public static Identifier analyticClipBaseId(Identifier variantId) {
        if (!isAnalyticClipVariant(variantId)) return variantId;
        return variantId.withPath(variantId.getPath().replace(ANALYTIC_CLIP_SUFFIX, ""));
    }

    /** Independent shader axis used only by glass draws that consume accumulated UI. */
    public static Identifier uiUnderlayVariantId(Identifier baseId) {
        String path = baseId.getPath();
        int extension = shaderExtensionStart(path);
        return baseId.withPath(extension >= 0
                ? path.substring(0, extension) + UI_UNDERLAY_SUFFIX + path.substring(extension)
                : path + UI_UNDERLAY_SUFFIX);
    }

    public static boolean isUiUnderlayVariant(Identifier id) {
        return id != null && id.getPath().contains(UI_UNDERLAY_SUFFIX);
    }

    public static Identifier uiUnderlayBaseId(Identifier variantId) {
        if (!isUiUnderlayVariant(variantId)) return variantId;
        return variantId.withPath(variantId.getPath().replace(UI_UNDERLAY_SUFFIX, ""));
    }

    public static Identifier extendedResourceId(Identifier id, ShaderType type) {
        String path = id.getPath();
        if (!path.startsWith(SHADER_PREFIX)) {
            path = SHADER_PREFIX + path;
        }
        if (!path.endsWith(VERT_EXTENSION) && !path.endsWith(FRAG_EXTENSION)
                && !path.endsWith(".vsh") && !path.endsWith(".fsh")) {
            path += type == ShaderType.VERTEX ? VERT_EXTENSION : FRAG_EXTENSION;
        }
        return id.withPath(path);
    }

    public static Identifier vanillaResourceId(Identifier id, ShaderType type) {
        String path = id.getPath();
        if (!path.startsWith(SHADER_PREFIX)) {
            path = SHADER_PREFIX + path;
        }
        if (!path.endsWith(VERT_EXTENSION) && !path.endsWith(FRAG_EXTENSION)
                && !path.endsWith(".vsh") && !path.endsWith(".fsh")) {
            path += type == ShaderType.VERTEX ? ".vsh" : ".fsh";
        }
        return id.withPath(path);
    }


    /**
     * Loads native compute/tessellation/geometry stages through Combatant's include resolver.
     * These stages remain outside ShaderManager: RenderPearl handles only vertex/fragment stages.
     */
    public static String loadNativeStage(ResourceManager resourceManager, Identifier id, String extension) {
        if (resourceManager == null) throw new IllegalArgumentException("resourceManager");
        if (id == null) throw new IllegalArgumentException("id");
        if (!isNativeStageExtension(extension)) throw new IllegalArgumentException("Unsupported native shader extension: " + extension);

        Identifier resourceId = nativeStageResourceId(id, extension);
        Resource resource = resourceManager.getResource(resourceId)
                .orElseThrow(() -> new IllegalStateException("Missing native shader resource: " + resourceId));
        return load(resourceManager, resourceId, resource);
    }

    public static Identifier nativeStageResourceId(Identifier id, String extension) {
        String path = id.getPath();
        if (!path.startsWith(SHADER_PREFIX)) path = SHADER_PREFIX + path;
        if (!path.endsWith(extension)) path += extension;
        return id.withPath(path);
    }

    private static boolean isNativeStageExtension(String extension) {
        return COMPUTE_EXTENSION.equals(extension)
                || TESS_CONTROL_EXTENSION.equals(extension)
                || TESS_EVALUATION_EXTENSION.equals(extension)
                || GEOMETRY_EXTENSION.equals(extension);
    }

    public static String load(ResourceManager resourceManager, Identifier id) {
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isPresent()) {
            return load(resourceManager, id, resource.get());
        }

        ShaderType inferred = typeByLocation(id);
        if (inferred != null) {
            return load(resourceManager, id, inferred);
        }

        throw new IllegalStateException("Missing shader resource: " + id);
    }

    public static String load(ResourceManager resourceManager, Identifier id, ShaderType type) {
        boolean analyticClip = isAnalyticClipVariant(id);
        boolean uiUnderlay = isUiUnderlayVariant(id);
        Identifier sourceId = uiUnderlayBaseId(analyticClipBaseId(id));
        Optional<Resource> direct = resourceManager.getResource(sourceId);
        if (direct.isPresent()) {
            return load(resourceManager, id, sourceId, direct.get(), analyticClip, uiUnderlay);
        }

        Identifier extendedId = extendedResourceId(sourceId, type);
        Optional<Resource> extended = resourceManager.getResource(extendedId);
        if (extended.isPresent()) {
            return load(resourceManager, id, extendedId, extended.get(), analyticClip, uiUnderlay);
        }

        Identifier vanillaId = vanillaResourceId(sourceId, type);
        Optional<Resource> vanilla = resourceManager.getResource(vanillaId);
        if (vanilla.isPresent()) {
            return load(resourceManager, id, vanillaId, vanilla.get(), analyticClip, uiUnderlay);
        }

        throw new IllegalStateException("Missing shader resource: " + id + " (also tried " + extendedId + " and " + vanillaId + ")");
    }

    public static String load(ResourceManager resourceManager, Identifier id, Resource resource) {
        return load(resourceManager, id, id, resource, false, false);
    }

    private static String load(ResourceManager resourceManager, Identifier requestedId, Identifier sourceId,
                               Resource resource, boolean analyticClip, boolean uiUnderlay) {
        try (Reader reader = resource.openAsReader()) {
            String raw = IOUtils.toString(reader);
            if (analyticClip) raw = injectDefineAfterVersion(raw, ANALYTIC_CLIP_DEFINE);
            if (uiUnderlay) raw = injectDefineAfterVersion(raw, UI_UNDERLAY_DEFINE);
            return expandIncludes(resourceManager, sourceId, raw, new HashSet<>());
        } catch (Exception e) {
            DebugLog.error("[ShaderSource] failed to read shader source: " + requestedId + " (base " + sourceId + ")", e);
            throw new RuntimeException("Failed to read shader source: " + requestedId + " (base " + sourceId + ")", e);
        }
    }

    private static int shaderExtensionStart(String path) {
        if (path.endsWith(VERT_EXTENSION) || path.endsWith(FRAG_EXTENSION)) return path.length() - 5;
        if (path.endsWith(".vsh") || path.endsWith(".fsh")) return path.length() - 4;
        return -1;
    }

    static String injectDefineAfterVersion(String source, String define) {
        if (source == null || source.isEmpty()) return define + (source == null ? "" : source);
        int version = source.indexOf("#version");
        if (version < 0) return define + source;
        int lineEnd = source.indexOf('\n', version);
        if (lineEnd < 0) return source + "\n" + define;
        return source.substring(0, lineEnd + 1) + define + source.substring(lineEnd + 1);
    }

    private static boolean isExtendedSourcePath(String path) {
        return path.endsWith(VERT_EXTENSION) || path.endsWith(FRAG_EXTENSION);
    }

    /**
     * Deterministic expansion for Combatant shader variants and native stages. Resolve directly
     * through ResourceManager to preserve pack priority. RenderPearl/ShaderC owns final VS/FS
     * compilation; this layer only resolves Combatant includes and injected feature defines.
     *
     * Only GLSL #include is supported; paths resolve from the active ResourceManager.
     */
    private static String expandIncludes(ResourceManager resources, Identifier sourceId,
                                         String source, Set<Identifier> included) throws IOException {
        Matcher matches = INCLUDE_DIRECTIVE.matcher(source);
        StringBuilder output = new StringBuilder(source.length());
        int cursor = 0;
        int currentLine = 1;
        while (matches.find()) {
            output.append(source, cursor, matches.start());
            currentLine += countNewlines(source, cursor, matches.start());
            boolean relative = "\"".equals(matches.group(1));
            String name = matches.group(2).trim();
            Identifier includeId;
            try {
                if (relative) {
                    Identifier parent = sourceId.withPath(FileUtil::getFullResourcePath);
                    includeId = parent.withPath(path -> FileUtil.normalizeResourcePath(path + name));
                } else {
                    includeId = Identifier.parse(name).withPrefix("shaders/include/");
                }
            } catch (IdentifierException error) {
                throw new IOException("Invalid shader include '" + name + "' in " + sourceId, error);
            }
            if (!isShaderSourceOrInclude(includeId)) {
                throw new IOException("Unsupported shader include " + includeId + " in " + sourceId);
            }
            if (included.add(includeId)) {
                Resource include = resources.getResource(includeId)
                        .orElseThrow(() -> new IOException("Missing shader include " + includeId + " in " + sourceId));
                try (Reader reader = include.openAsReader()) {
                    String content = IOUtils.toString(reader);
                    output.append("\n#line 1\n");
                    output.append(expandIncludes(resources, includeId, content, included));
                    output.append("\n#line ").append(currentLine + 1).append('\n');
                }
            } else {
                output.append('\n'); // Deduplicate common include dependencies within the same shader stage.
            }
            currentLine += countNewlines(source, matches.start(), matches.end());
            cursor = matches.end();
        }
        return output.append(source, cursor, source.length()).toString();
    }

    private static int countNewlines(String value, int start, int end) {
        int result = 0;
        for (int i = start; i < end; i++) if (value.charAt(i) == '\n') result++;
        return result;
    }
}
