/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;

/**
 * Vulkan pipeline state bridge. Fixed Vulkan state is injected while pipelines are compiled.
 */
public final class VulkanPipelineStateBackend implements PipelineStateBackend {
    private final MsaaControl msaa;
    private final ShapeClipBackend clip;

    public VulkanPipelineStateBackend(MsaaControl msaa, ShapeClipBackend clip) {
        this.msaa = msaa;
        this.clip = clip;
    }

    @Override
    public void applyPipelineState(RenderPipeline pipeline) {
        msaa.applyPipelineState(pipeline);

        ShapeClipRenderPassContract contract = ShapeClipRenderPassContract.NONE;
        if (pipeline instanceof IRenderPipeline combatantPipeline) {
            contract = combatantPipeline.combatant$getShapeClipContract();
        }
        clip.bindPipeline(pipeline, contract);
        clip.applyNativeState();
    }

    @Override
    public void resetRenderPassState() {
        msaa.resetPipelineState();
        clip.endRenderPass();
    }
}
