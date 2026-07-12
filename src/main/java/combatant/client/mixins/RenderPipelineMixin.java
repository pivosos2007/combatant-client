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

package combatant.client.mixins;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import combatant.client.mixininterface.IRenderPipeline;
import combatant.client.render.engine.rhi.clip.ShapeClipRenderPassContract;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineMixin implements IRenderPipeline {
    @Unique
    private boolean combatant$lineSmooth;

    @Unique
    private ShapeClipRenderPassContract combatant$shapeClipContract = ShapeClipRenderPassContract.NONE;

    @Override
    public void combatant$setLineSmooth(boolean lineSmooth) {
        this.combatant$lineSmooth = lineSmooth;
    }

    @Override
    public boolean combatant$getLineSmooth() {
        return combatant$lineSmooth;
    }

    @Override
    public void combatant$setShapeClipContract(ShapeClipRenderPassContract contract) {
        this.combatant$shapeClipContract = contract == null ? ShapeClipRenderPassContract.NONE : contract;
    }

    @Override
    public ShapeClipRenderPassContract combatant$getShapeClipContract() {
        return combatant$shapeClipContract;
    }
}
