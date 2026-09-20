/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import combatant.client.render.engine.deferred.DeferredMotionState;
import org.jetbrains.annotations.Nullable;

/** Carries exact producer-side temporal motion metadata with a vanilla render state. */
public interface ITemporalMotionRenderState {
    @Nullable DeferredMotionState combatant$getTemporalMotionState();

    void combatant$setTemporalMotionState(@Nullable DeferredMotionState state);
}
