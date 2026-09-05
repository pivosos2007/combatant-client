/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.shader;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.IdentifierException;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FileUtil;
import org.apache.commons.io.IOUtils;
import combatant.client.util.logging.DebugLog;

import java.io.Reader;
import java.util.Map;
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
    public static final String ANALYTIC_CLIP_SUFFIX = "__analytic_clip";
    public static final String UI_UNDERLAY_SUFFIX = "__ui_underlay";
    private static final String ANALYTIC_CLIP_DEFINE = "#define COMBATANT_ANALYTIC_CLIP 1\n";
    private static final String UI_UNDERLAY_DEFINE = "#define COMBATANT_UI_UNDERLAY 1\n";

    public static boolean isCombatantExtendedSource(Identifier id) {
        return COMBATANT_NAMESPACE.equals(id.getNamespace()) && isExtendedSourcePath(id.getPath());
    }

    public static boolean isShaderSourceOrInclude(Identifier id) {
        String path = id.getPath();
        return ShaderType.byLocation(id) != null
                || path.endsWith(".glsl")
                || path.endsWith(VERT_EXTENSION)
                || path.endsWith(FRAG_EXTENSION);
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
        Map<Identifier, Resource> allResources = resourceManager.listResources("shaders", CombatantShaderSources::isShaderSourceOrInclude);
        GlslPreprocessor processor = createImportProcessor(allResources, sourceId);
        try (Reader reader = resource.openAsReader()) {
            String raw = IOUtils.toString(reader);
            if (analyticClip) raw = injectDefineAfterVersion(raw, ANALYTIC_CLIP_DEFINE);
            if (uiUnderlay) raw = injectDefineAfterVersion(raw, UI_UNDERLAY_DEFINE);
            return String.join("", processor.process(raw));
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

    private static GlslPreprocessor createImportProcessor(Map<Identifier, Resource> allResources, Identifier id) {
        final Identifier baseId = id.withPath(FileUtil::getFullResourcePath);
        return new GlslPreprocessor() {
            private final Set<Identifier> processed = new it.unimi.dsi.fastutil.objects.ObjectArraySet<>();

            @Override
            public String applyImport(boolean inline, String name) {
                Identifier importId;
                try {
                    if (inline) {
                        importId = baseId.withPath(path -> FileUtil.normalizeResourcePath(path + name));
                    } else {
                        importId = Identifier.parse(name).withPrefix("shaders/include/");
                    }
                } catch (IdentifierException e) {
                    return "#error " + e.getMessage();
                }

                if (!processed.add(importId)) {
                    return null;
                }

                Resource imported = allResources.get(importId);
                if (imported == null) {
                    return "#error Missing import " + importId;
                }

                try (Reader reader = imported.openAsReader()) {
                    return IOUtils.toString(reader);
                } catch (Exception e) {
                    return "#error " + e.getMessage();
                }
            }
        };
    }

}
