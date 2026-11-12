/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.iris.IrisRuntime;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps fullscreen post-processing output formats aligned with Combatant's HDR ping-pong buffers.
 * Variants are created only during a Combatant-owned HDR frame; shaderpack targets are excluded.
 */
public final class HdrFullscreenPipelines {
    private static final Map<RenderPipeline, RenderPipeline> HDR_VARIANTS = new WeakHashMap<>();

    private HdrFullscreenPipelines() { }

    public static RenderPipeline forAttachment(RenderPipeline pipeline, GpuTextureView attachment) {
        if (!HdrScene.active() || IrisRuntime.isModLoaded() || pipeline == null || attachment == null
                || attachment.texture().getFormat() != SceneColor.FORMAT
                || pipeline.getLocation() == null || !"combatant".equals(pipeline.getLocation().getNamespace())
                || !(pipeline instanceof IRenderPipeline extended)
                || extended.combatant$getMetadata() == null
                || !extended.combatant$getMetadata().fullscreen()
                || pipeline.getColorTargetStates().size() != 1) {
            return pipeline;
        }

        ColorTargetState original = pipeline.getColorTargetStates().getFirst();
        if (original == null || original.format() != GpuFormat.RGBA8_UNORM) return pipeline;

        synchronized (HDR_VARIANTS) {
            RenderPipeline variant = HDR_VARIANTS.computeIfAbsent(pipeline, HdrFullscreenPipelines::createHdrVariant);
            RenderPipelineRegistry registry = RenderPipelineRegistry.global();
            if (registry.get(variant) == null) registry.registerNative(variant);
            return variant;
        }
    }

    private static RenderPipeline createHdrVariant(RenderPipeline pipeline) {
        return new Variant(pipeline);
    }

    private static final class Variant extends RenderPipeline {
        private Variant(RenderPipeline pipeline) {
            super(pipeline.getLocation().withSuffix("__fullscreen_hdr"),
                    pipeline.getShaders(), pipeline.getShaderDefines(), pipeline.getBindGroupLayouts(),
                    new ColorTargetState[]{new ColorTargetState(pipeline.getColorTargetStates().getFirst().blendFunction(),
                            SceneColor.FORMAT, pipeline.getColorTargetStates().getFirst().writeMask())},
                    pipeline.getDepthStencilState(), pipeline.getPolygonMode(), pipeline.isCull(),
                    pipeline.getVertexFormatBindings().toArray(VertexFormat[]::new),
                    pipeline.getPrimitiveTopology(), pipeline.pushConstantSize(), pipeline.getSortKey());
            if (pipeline instanceof IRenderPipeline source && (Object) this instanceof IRenderPipeline destination) {
                destination.combatant$setMetadata(source.combatant$getMetadata());
                destination.combatant$setContract(source.combatant$getContract());
                destination.combatant$setLineSmooth(source.combatant$getLineSmooth());
                destination.combatant$setShapeClipContract(source.combatant$getShapeClipContract());
            }
        }
    }
}
