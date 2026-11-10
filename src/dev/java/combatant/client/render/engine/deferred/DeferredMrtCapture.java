/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.resource.TransientTargetDescriptor;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * 26.3 RenderPearl MRT geometry capture; only Sodium opaque/cutout pipelines write slots 1..5.
 * Minecraft feature renderers run in a *separate*, vanilla single-target RenderPass.
 *
 * <p>Resources are owned by the existing Combatant frame-transient pool and released at the
 * frame's presentation boundary. This class never creates raw GL FBOs or Vulkan images.</p>
 */
final class DeferredMrtCapture {
    static final GpuFormat SURFACE_FORMAT = GpuFormat.RGBA8_UNORM;
    static final GpuFormat GEOMETRY_FORMAT = GpuFormat.RGBA8_UNORM;
    static final GpuFormat AUXILIARY_FORMAT = GpuFormat.RGBA8_UNORM;
    static final GpuFormat MATERIAL_FORMAT = GpuFormat.RGBA8_UNORM;
    static final GpuFormat MATERIAL_ID_FORMAT = GpuFormat.R32_UINT;

    private boolean active;
    private boolean debugEnabled;
    private long frame = Long.MIN_VALUE;
    private @Nullable GpuTextureView surface;
    private @Nullable GpuTextureView geometry;
    private @Nullable GpuTextureView auxiliary;
    private @Nullable GpuTextureView material;
    private @Nullable GpuTextureView materialId;
    private @Nullable GpuTextureView sceneDepth;

    boolean canCapture() {
        return MaterialAtlasManager.global().ready()
                && CombatantRenderSystem.rhi().capabilities().maxColorAttachments() >= 6;
    }

    void configureSodiumPipeline(RenderPipeline.Builder builder) {
        // Only used by the 26.3 ShaderChunkRenderer#createShader non-translucent path.
        builder.withColorTargetState(1, new ColorTargetState(Optional.empty(), SURFACE_FORMAT, ColorTargetState.WRITE_ALL));
        builder.withColorTargetState(2, new ColorTargetState(Optional.empty(), GEOMETRY_FORMAT, ColorTargetState.WRITE_ALL));
        builder.withColorTargetState(3, new ColorTargetState(Optional.empty(), AUXILIARY_FORMAT, ColorTargetState.WRITE_ALL));
        builder.withColorTargetState(4, new ColorTargetState(Optional.empty(), MATERIAL_FORMAT, ColorTargetState.WRITE_ALL));
        builder.withColorTargetState(5, new ColorTargetState(Optional.empty(), MATERIAL_ID_FORMAT, ColorTargetState.WRITE_ALL));
        builder.withBindGroupLayout(MaterialAtlasManager.BIND_GROUP);
        builder.withShaderDefine("COMBATANT_DEFERRED_GBUFFER");
    }

    RenderPass openGeometryPass(CommandEncoder encoder, Supplier<String> label,
                                GpuTextureView sceneColor, Optional<Vector4fc> clearColor,
                                @Nullable GpuTextureView depth, OptionalDouble clearDepth) {
        RenderSystem.assertOnRenderThread();
        int width = sceneColor.getWidth(0), height = sceneColor.getHeight(0);
        long current = CombatantRenderSystem.resources().frameId();
        if (frame == current && active) {
            throw new IllegalStateException("G-buffer capture opened twice in frame " + current);
        }
        reset();
        if (depth == null || !canCapture()) {
            throw new IllegalStateException("Sodium deferred requires depth and six color attachments");
        }
        // The initial 26.3 port supports only single-sample MRT. Never mix sample counts.
        if (sceneColor.texture() instanceof combatant.client.mixininterface.IMsaaTexture msaa
                && msaa.combatant$isMsaa()) {
            throw new IllegalStateException("MSAA MRT is not yet ported; disable Minecraft MSAA for deferred smoke test");
        }
        surface = allocate("surface", width, height, SURFACE_FORMAT);
        geometry = allocate("geometry", width, height, GEOMETRY_FORMAT);
        auxiliary = allocate("auxiliary", width, height, AUXILIARY_FORMAT);
        material = allocate("material", width, height, MATERIAL_FORMAT);
        materialId = allocate("material_id", width, height, MATERIAL_ID_FORMAT);
        sceneDepth = depth;
        frame = current;
        RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(label)
                .withColorAttachment(sceneColor, clearColor)
                .withColorAttachment(surface, Optional.of(new Vector4f(0f, 0f, 0f, 0f)))
                .withColorAttachment(geometry, Optional.of(new Vector4f(0f, 0f, 0f, 0f)))
                .withColorAttachment(auxiliary, Optional.of(new Vector4f(0f, 0f, 0f, 0f)))
                .withColorAttachment(material, Optional.of(new Vector4f(0f, 0f, 0f, 0f)))
                // Integer attachment: use the validity of the cleared surface to identify pixels.
                .withColorAttachment(materialId, Optional.empty());
        descriptor.withDepthAttachment(depth, clearDepth);
        RenderPass pass = encoder.createRenderPass(descriptor.build());
        active = true;
        return pass;
    }

    private GpuTextureView allocate(String name, int width, int height, GpuFormat format) {
        TextureTarget target = CombatantRenderSystem.resources().frameTransient(new TransientTargetDescriptor(
                "deferred.gbuffer." + name, width, height, false, format, 1,
                TransientTargetDescriptor.Lifetime.FRAME, "DeferredMrtCapture"));
        if (target == null || target.getColorTextureView() == null) {
            throw new IllegalStateException("Failed G-buffer allocation: " + name);
        }
        return target.getColorTextureView();
    }

    void publishDebug(GpuTextureView sceneColor) {
        if (!active || frame != CombatantRenderSystem.resources().frameId()) return;
        if (!debugEnabled) return;
        if (surface == null) return;
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.renderpearl.api.textures.FilterMode.NEAREST);
        CombatantRenderSystem.rhi().drawFullscreen(FullscreenDrawCommand.builder("Deferred 26.3 surface MRT debug")
                .colorAttachment(sceneColor)
                .pipeline(DeferredRuntimeAssets.temporalPresent())
                .sampler("u_Source", surface, sampler).build());
    }

    void setDebugEnabled(boolean enabled) { debugEnabled = enabled; }

    void reset() {
        active = false;
        surface = geometry = auxiliary = material = materialId = sceneDepth = null;
        frame = Long.MIN_VALUE;
    }

    boolean active() { return active && frame == CombatantRenderSystem.resources().frameId(); }
    @Nullable GpuTextureView surface() { return active() ? surface : null; }
    @Nullable GpuTextureView geometry() { return active() ? geometry : null; }
    @Nullable GpuTextureView auxiliary() { return active() ? auxiliary : null; }
    @Nullable GpuTextureView material() { return active() ? material : null; }
    @Nullable GpuTextureView materialId() { return active() ? materialId : null; }
    @Nullable GpuTextureView sceneDepth() { return active() ? sceneDepth : null; }
}
