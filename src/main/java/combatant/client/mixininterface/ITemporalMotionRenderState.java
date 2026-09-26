/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixininterface;

import combatant.client.render.engine.temporal.TemporalMotionState;
import org.jetbrains.annotations.Nullable;

/** Carries producer-side temporal motion metadata with a vanilla render state. */
public interface ITemporalMotionRenderState {
    @Nullable TemporalMotionState combatant$getTemporalMotionState();
    void combatant$setTemporalMotionState(@Nullable TemporalMotionState state);
}
