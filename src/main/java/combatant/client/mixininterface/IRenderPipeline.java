/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.mixininterface;

import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;
import combatant.client.render.engine.pipeline.RenderPipelineContract;
import combatant.client.render.engine.rhi.pipeline.PipelineMetadata;

public interface IRenderPipeline {
    void combatant$setLineSmooth(boolean lineSmooth);

    boolean combatant$getLineSmooth();

    void combatant$setShapeClipContract(ShapeClipRenderPassContract contract);

    ShapeClipRenderPassContract combatant$getShapeClipContract();

    void combatant$setContract(RenderPipelineContract contract);

    RenderPipelineContract combatant$getContract();

    void combatant$setMetadata(PipelineMetadata metadata);

    PipelineMetadata combatant$getMetadata();
}
