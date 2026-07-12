/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.pipeline;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central metadata registry for all Combatant render pipelines.
 */
public final class RenderPipelineRegistry {
    private static final RenderPipelineRegistry GLOBAL = new RenderPipelineRegistry();

    private final Map<String, RenderPipelineSpec> byId = new LinkedHashMap<>();
    private final Map<RenderPipeline, RenderPipelineSpec> byNativePipeline = new IdentityHashMap<>();

    public static RenderPipelineRegistry global() {
        return GLOBAL;
    }

    private static RenderPipelineSpec specFromNative(RenderPipeline pipeline) {
        PipelineKey key = PipelineKey.of(pipeline);
        com.mojang.blaze3d.PrimitiveTopology primitive = pipeline != null
                ? pipeline.getPrimitiveTopology()
                : com.mojang.blaze3d.PrimitiveTopology.TRIANGLES;
        VertexFormat format = pipeline != null ? pipeline.getVertexFormatBinding(0) : null;
        boolean lineMode = primitive == com.mojang.blaze3d.PrimitiveTopology.LINES
                || primitive == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINES
                || primitive == com.mojang.blaze3d.PrimitiveTopology.DEBUG_LINE_STRIP;

        return RenderPipelineSpec.builder(key)
                .vertexLayout(VertexLayoutSpec.of(key.path(), format, primitive))
                .depth(inferDepthPolicy(key.path(), pipeline))
                .vertexShader(id(pipeline != null ? pipeline.getVertexShader() : null))
                .fragmentShader(id(pipeline != null ? pipeline.getFragmentShader() : null))
                .metadata(PipelineMetadata.builder(PipelineDomain.UNKNOWN)
                        .lineMode(lineMode)
                        .build())
                .build();
    }

    private static DepthPolicy inferDepthPolicy(String path, RenderPipeline pipeline) {
        if (pipeline == null || pipeline.getDepthStencilState() == null
                || pipeline.getDepthStencilState().depthTest() == null) {
            return DepthPolicy.NONE;
        }
        return path != null && path.startsWith("pipeline/world_")
                ? DepthPolicy.MAIN_FRAMEBUFFER
                : DepthPolicy.NONE;
    }

    private static String id(Identifier id) {
        return id != null ? id.toString() : "";
    }

    public void register(RenderPipelineSpec spec) {
        if (spec == null) return;
        byId.put(spec.getId(), spec);
    }

    public void register(RenderPipeline pipeline, RenderPipelineSpec spec) {
        if (spec == null) return;
        register(spec);
        if (pipeline != null) byNativePipeline.put(pipeline, spec);
    }

    public void registerNative(RenderPipeline pipeline) {
        register(pipeline, specFromNative(pipeline));
    }

    public RenderPipelineSpec get(String id) {
        return byId.get(id);
    }

    public RenderPipelineSpec get(PipelineKey key) {
        return key != null ? get(key.getId()) : null;
    }

    public RenderPipelineSpec get(RenderPipeline pipeline) {
        if (pipeline == null) return null;
        RenderPipelineSpec spec = byNativePipeline.get(pipeline);
        if (spec != null) return spec;
        spec = byId.get(PipelineKey.of(pipeline).getId());
        if (spec != null) byNativePipeline.put(pipeline, spec);
        return spec;
    }

    public RenderPipelineSpec require(RenderPipeline pipeline) {
        RenderPipelineSpec spec = get(pipeline);
        if (spec == null) {
            throw new IllegalStateException("Missing RenderPipelineSpec for " + (pipeline != null ? pipeline.getLocation() : "null"));
        }
        return spec;
    }

    public Collection<RenderPipelineSpec> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }

    public void clear() {
        byId.clear();
        byNativePipeline.clear();
    }
}
