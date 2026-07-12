/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import combatant.client.render.ViewObstructionFadeState;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements ViewObstructionFadeState {
    @Unique
    private boolean combatant$viewObstructionFadeActive;

    @Unique
    private float combatant$viewObstructionFadeAlpha = 1.0f;

    @Unique
    private boolean combatant$seeInvisibleFadeActive;

    @Override
    public boolean combatant$isViewObstructionFadeActive() {
        return combatant$viewObstructionFadeActive;
    }

    @Override
    public float combatant$getViewObstructionFadeAlpha() {
        return combatant$viewObstructionFadeAlpha;
    }

    @Override
    public void combatant$setViewObstructionFadeActive(boolean active) {
        this.combatant$viewObstructionFadeActive = active;
    }

    @Override
    public void combatant$setViewObstructionFadeAlpha(float alpha) {
        this.combatant$viewObstructionFadeAlpha = alpha;
    }

    @Override
    public boolean combatant$isSeeInvisibleFadeActive() {
        return combatant$seeInvisibleFadeActive;
    }

    @Override
    public void combatant$setSeeInvisibleFadeActive(boolean active) {
        this.combatant$seeInvisibleFadeActive = active;
    }
}
