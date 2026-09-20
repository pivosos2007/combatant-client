/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.mixininterface.ITemporalMotionRenderState;
import combatant.client.render.engine.deferred.DeferredMotionState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateTemporalMixin implements ITemporalMotionRenderState {
    @Unique private @Nullable DeferredMotionState combatant$temporalMotionState;

    @Override
    public @Nullable DeferredMotionState combatant$getTemporalMotionState() {
        return combatant$temporalMotionState;
    }

    @Override
    public void combatant$setTemporalMotionState(@Nullable DeferredMotionState state) {
        combatant$temporalMotionState = state;
    }
}
