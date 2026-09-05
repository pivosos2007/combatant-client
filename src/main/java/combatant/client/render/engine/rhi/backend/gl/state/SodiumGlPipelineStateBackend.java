/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.gl.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.backend.gl.GlNativeStateTracker;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;


/**
 * GL implementation for out-of-band RenderPipeline state.
 *
 * <p>GL_LINE_SMOOTH, MSAA and shape-clip render-pass state are applied through RHI. The command
 * encoder mixin owns only the hook points, not the GL policy.</p>
 */
public final class SodiumGlPipelineStateBackend implements PipelineStateBackend {
    private final MsaaControl msaa;
    private final ShapeClipBackend clip;

    public SodiumGlPipelineStateBackend(MsaaControl msaa, ShapeClipBackend clip) {
        this.msaa = msaa;
        this.clip = clip;
    }

    private static void applyLineSmooth(RenderPipeline pipeline) {
        boolean enabled = pipeline instanceof IRenderPipeline combatantPipeline && combatantPipeline.combatant$getLineSmooth();
        if (enabled) {
            GlNativeStateTracker.lineSmooth(true);
            // Wide lines are expanded into geometry before draw submission. Keep native GL lines at 1px: relying
            // on glLineWidth for thickness is undefined on modern core-profile drivers and produces GL_INVALID_VALUE
            // on common AMD/Mesa paths where the supported aliased line range is effectively [1, 1].
            GlNativeStateTracker.lineWidth(1.0f);
        } else {
            resetLineSmooth();
        }
    }

    private static void resetLineSmooth() {
        GlNativeStateTracker.lineSmooth(false);
        GlNativeStateTracker.lineWidth(1.0f);
    }

    @Override
    public void applyPipelineState(RenderPipeline pipeline) {
        applyLineSmooth(pipeline);
        msaa.applyPipelineState(pipeline);

        ShapeClipRenderPassContract contract = ShapeClipRenderPassContract.NONE;
        if (pipeline instanceof IRenderPipeline combatantPipeline) {
            contract = combatantPipeline.combatant$getShapeClipContract();
        }
        clip.bindPipeline(pipeline, contract);
        clip.applyNativeState();
    }

    @Override
    public void invalidateForeignState() {
        GlNativeStateTracker.invalidateAll();
    }

    @Override
    public void resetRenderPassState() {
        resetLineSmooth();
        msaa.resetPipelineState();
        clip.endRenderPass();
    }
}
