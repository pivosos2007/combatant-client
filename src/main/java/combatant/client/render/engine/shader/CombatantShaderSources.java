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
        Optional<Resource> direct = resourceManager.getResource(id);
        if (direct.isPresent()) {
            return load(resourceManager, id, direct.get());
        }

        Identifier extendedId = extendedResourceId(id, type);
        Optional<Resource> extended = resourceManager.getResource(extendedId);
        if (extended.isPresent()) {
            return load(resourceManager, extendedId, extended.get());
        }

        Identifier vanillaId = vanillaResourceId(id, type);
        Optional<Resource> vanilla = resourceManager.getResource(vanillaId);
        if (vanilla.isPresent()) {
            return load(resourceManager, vanillaId, vanilla.get());
        }

        throw new IllegalStateException("Missing shader resource: " + id + " (also tried " + extendedId + " and " + vanillaId + ")");
    }

    public static String load(ResourceManager resourceManager, Identifier id, Resource resource) {
        Map<Identifier, Resource> allResources = resourceManager.listResources("shaders", CombatantShaderSources::isShaderSourceOrInclude);
        GlslPreprocessor processor = createImportProcessor(allResources, id);
        try (Reader reader = resource.openAsReader()) {
            String raw = IOUtils.toString(reader);
            return String.join("", processor.process(raw));
        } catch (Exception e) {
            DebugLog.error("[ShaderSource] failed to read shader source: " + id, e);
            throw new RuntimeException("Failed to read shader source: " + id, e);
        }
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
