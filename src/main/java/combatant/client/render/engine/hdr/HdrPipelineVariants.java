/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.hdr;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.iris.IrisRuntime;
import net.minecraft.client.renderer.ShaderDefines;
import org.joml.Vector4fc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Logical variants share RenderPearl's compiled-pipeline cache and resource-reload lifetime. */
public final class HdrPipelineVariants {
    public static final String LEGACY_COLOR = "COMBATANT_HDR_LEGACY_COLOR";
    private static final Map<String, RenderPipeline> SOURCES = new ConcurrentHashMap<>();
    private static final Map<RenderPipeline, Map<String, RenderPipeline>> VARIANTS = new WeakHashMap<>();

    private HdrPipelineVariants() { }

    public static void observe(RenderPipeline pipeline) {
        if (IrisRuntime.isModLoaded()) return;
        SOURCES.put(pipeline.getLocation().toString(), pipeline);
    }

    public static CompiledRenderPipeline select(CompiledRenderPipeline compiled,
            List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> attachments) {
        if (!HdrScene.active() || IrisRuntime.isModLoaded() || attachments.isEmpty()
                || attachments.getFirst() == null) return compiled;
        if (!(compiled instanceof FrontendRenderPipeline frontend)) return compiled;
        RenderPipeline base = SOURCES.get(frontend.name());
        if (base == null || base instanceof Variant) return compiled;
        ColorTargetState[] states = base.getColorTargetStates().toArray(ColorTargetState[]::new);
        if (states.length != attachments.size()) return compiled;
        boolean sceneAttachment = HdrScene.ownsColorAttachment(attachments.getFirst().textureView());
        // OIT accumulates into its own FP16 color attachment and composites into the HDR world later.
        // That attachment is deliberately not owned by HdrScene, but its *color* must be scene-linear.
        boolean oitColor = !sceneAttachment && isOitColorAccumulation(base, attachments);
        if (!sceneAttachment && !oitColor) return compiled;
        boolean changed = false;
        boolean hdr = false;
        for (int i = 0; i < states.length; i++) {
            var attachment = attachments.get(i);
            if (attachment == null || states[i] == null) continue;
            GpuFormat format = attachment.textureView().texture().getFormat();
            hdr |= format == SceneColor.FORMAT;
            if (states[i].format() == GpuFormat.RGBA8_UNORM && format == SceneColor.FORMAT) {
                states[i] = new ColorTargetState(states[i].blendFunction(), format, states[i].writeMask());
                changed = true;
            }
        }
        boolean legacy = hdr && legacyColor(base);
        if (!changed && !legacy) return compiled;
        String key = Arrays.toString(states) + ":" + legacy;
        RenderPipeline variant = VARIANTS.computeIfAbsent(base, ignored -> new HashMap<>())
                .computeIfAbsent(key, ignored -> new Variant(base, states, legacy));
        return RenderSystem.getCompiledPipeline(variant);
    }

    private static boolean isOitColorAccumulation(RenderPipeline pipeline,
            List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> attachments) {
        if (attachments.size() != 1 || attachments.getFirst() == null
                || attachments.getFirst().textureView() == null
                || attachments.getFirst().textureView().texture().getFormat() != SceneColor.FORMAT) return false;
        var flags = pipeline.getShaderDefines().flags();
        if (!flags.contains("OIT_ACCUMULATE") || flags.contains("OIT_ALPHA_ONLY")
                || flags.contains("OIT_TRANSMITTANCE") || flags.contains("OIT_DEPTH_BOUNDS")) return false;
        String namespace = pipeline.getLocation().getNamespace();
        return namespace.equals("minecraft") || namespace.equals("sodium")
                || namespace.equals("combatant") && legacyColor(pipeline);
    }

    private static boolean legacyColor(RenderPipeline base) {
        if (base.getShaderDefines().flags().contains("OIT_ALPHA_ONLY")) return false;
        String namespace = base.getLocation().getNamespace();
        String path = base.getLocation().getPath();
        if (namespace.equals("minecraft") || namespace.equals("sodium")) {
            String fragment = String.valueOf(base.getShaders().get(ShaderType.FRAGMENT));
            return !fragment.contains("oit_composite") && !fragment.contains("blit")
                    && !fragment.contains("post/") && !fragment.contains("depth");
        }
        if (base.getColorTargetStates().getFirst().format() == SceneColor.FORMAT) return false;
        return base instanceof IRenderPipeline extended
                && extended.combatant$getMetadata().domain() == PipelineDomain.WORLD
                || path.startsWith("pipeline/world_") || path.startsWith("pipeline/rig_");
    }

    private static final class Variant extends RenderPipeline {
        Variant(RenderPipeline base, ColorTargetState[] states, boolean legacy) {
            super(base.getLocation().withSuffix("__hdr_" + Integer.toUnsignedString(Arrays.hashCode(states), 36)
                            + (legacy ? "_linear" : "")),
                    base.getShaders(), legacy ? base.getShaderDefines().withOverrides(
                            ShaderDefines.builder().define(LEGACY_COLOR).build()) : base.getShaderDefines(),
                    base.getBindGroupLayouts(), states, base.getDepthStencilState(), base.getPolygonMode(),
                    base.isCull(), base.getVertexFormatBindings().toArray(VertexFormat[]::new),
                    base.getPrimitiveTopology(), base.pushConstantSize(), base.getSortKey());
            if (base instanceof IRenderPipeline from && (Object) this instanceof IRenderPipeline to) {
                to.combatant$setMetadata(from.combatant$getMetadata());
                to.combatant$setContract(from.combatant$getContract());
                to.combatant$setLineSmooth(from.combatant$getLineSmooth());
                to.combatant$setShapeClipContract(from.combatant$getShapeClipContract());
            }
            RenderPipelineRegistry.global().registerNative(this);
        }
    }
}
