/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.RigRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rig.gpu.RigGpuMeshCache;
import combatant.client.render.engine.rig.shader.RigBonesUniforms;
import combatant.client.render.engine.rig.shader.RigDeformUniforms;
import combatant.client.render.engine.rig.shader.RigRibbonUniforms;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * World entity GPU skinning, with the same indexed rig vertex ABI used by the general 3D engine.
 * Vanilla/RenderPearl pipelines perform depth testing, while immutable geometry lives in VRAM.
 * With an active Iris shaderpack, the dedicated compute adapter skins vertices into Iris' own
 * ENTITY layout and draws through mapped ENTITIES programs at the proper Iris pass boundaries.
 * Unsupported devices and special passes retain the vanilla ordered collector.
 */
public final class PlayerModelGpuRenderer {
    private record Submission(PlayerModels.Snapshot snapshot, Matrix4f matrix, int light, int overlay) {}
    /** Mod-agnostic payload; optional Iris classes must not link in vanilla mode. */
    public record IrisDraw(PlayerModels.Snapshot snapshot, Matrix4f model, int light, int overlay) {}
    private static final List<Submission> PENDING = new ArrayList<>(2);
    private static boolean disabled;

    private PlayerModelGpuRenderer() {}

    public static boolean trySubmit(PlayerModels.Snapshot snapshot, PoseStack matrices,
                                    RenderType type, int light, int overlay, int tint, int outlineColor) {
        if (disabled || snapshot == null || matrices == null || type == null
                || (!IrisRuntime.isShaderpackRendererActive() && MsaaWorldTarget.isActive())
                || type.isOutline() || type.hasBlending() || outlineColor != 0
                || tint != -1 || overlay != OverlayTexture.NO_OVERLAY) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.gameRenderer == null
                || mc.gameRenderer.mainRenderTarget() == null) return false;
        try {
            // Preflight the *complete* asset while the vanilla collector can still be chosen.
            // Uploading at render time would otherwise turn a failed GPU allocation into a
            // missing body for the current frame rather than a CPU fallback.
            if (IrisRuntime.isShaderpackRendererActive()) {
                if (!IrisRuntime.preparePlayerRig(snapshot.asset())) return false;
            } else {
                var rhi = CombatantRenderSystem.rhi();
                for (var surface : snapshot.asset().surfaces()) {
                    RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                }
            }
            PENDING.add(new Submission(snapshot, new Matrix4f(matrices.last().pose()), light, overlay));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
            return false;
        }
    }

    /** Called inside WORLD_BEFORE_TRANSLUCENT after the world matrix/target are prepared. */
    public static void render(RenderTarget target) {
        if (PENDING.isEmpty() || IrisRuntime.isShaderpackRendererActive()) return;
        List<Submission> draws = List.copyOf(PENDING);
        PENDING.clear(); // No submitted render state can outlive this flush on failure.
        if (disabled || target == null
                || MsaaWorldTarget.isActive() || target.getColorTextureView() == null
                || target.getDepthTextureView() == null) return;
        try {
            var rhi = CombatantRenderSystem.rhi();
            var commands = new ArrayList<RhiDrawCommand>(draws.size() * 2);
            for (Submission draw : draws) {
                PlayerModels.Snapshot snapshot = draw.snapshot();
                AbstractTexture texture = Minecraft.getInstance().getTextureManager()
                        .getTexture(snapshot.asset().texture());
                if (texture == null || texture.getTextureView() == null || texture.getSampler() == null)
                    throw new IllegalStateException("Player model texture lost GPU residency");
                GpuBufferSlice bones = RigBonesUniforms.upload(snapshot.rig().rig());
                GpuBufferSlice deform = RigDeformUniforms.upload(snapshot.rig().rig().deform());
                GpuBufferSlice ribbon = RigRibbonUniforms.upload(snapshot.rig().rig().ribbon());
                for (var surface : snapshot.asset().surfaces()) {
                    var gpuMesh = RigGpuMeshCache.global().acquire(rhi, surface.mesh());
                    commands.add(RhiDrawCommand.builder("PlayerModel GPU skinning")
                            .pipeline(surface.translucent()
                                    ? RigRenderPipelines.RIG_ENTITY_TRANSLUCENT_NO_DEPTH_WRITE
                                    : RigRenderPipelines.RIG_ENTITY_CUTOUT)
                            .colorAttachment(target.getColorTextureView())
                            .depthAttachment(target.getDepthTextureView())
                            .mesh(gpuMesh.handle())
                            .transform(draw.matrix())
                            .uniform(RigBonesUniforms.BLOCK_NAME, bones)
                            .uniform(RigDeformUniforms.BLOCK_NAME, deform)
                            .uniform(RigRibbonUniforms.BLOCK_NAME, ribbon)
                            .sampler("u_Texture", texture.getTextureView(), texture.getSampler())
                            .build());
                }
            }
            if (!commands.isEmpty()) rhi.drawMeshes(commands);
        } catch (RuntimeException | LinkageError failure) {
            // The next frame immediately falls back to the ordered CPU collector.
            disable(failure);
        }
    }

    /** Called by Iris inside its real G-buffer/forward boundaries, before shaderpack composite. */
    public static void renderIris(boolean translucent) {
        if (PENDING.isEmpty() || IrisRuntime.isRenderingShadowPass()) return;
        var draws = new ArrayList<IrisDraw>(PENDING.size());
        for (Submission submission : PENDING) {
            draws.add(new IrisDraw(
                    submission.snapshot(), submission.matrix(), submission.light(), submission.overlay()));
        }
        IrisRuntime.drawPlayerRigs(draws, translucent);
        if (translucent) PENDING.clear();
    }

    public static void reset() {
        PENDING.clear();
        disabled = false;
        IrisRuntime.invalidatePlayerRigs();
    }

    private static void disable(Throwable reason) {
        disabled = true;
        PENDING.clear();
        DebugLog.warnOnce("playermodel.gpu.fallback",
                "[PlayerModel] GPU rig renderer unavailable; using ordered CPU fallback: %s: %s",
                reason.getClass().getSimpleName(), reason.getMessage());
    }
}
