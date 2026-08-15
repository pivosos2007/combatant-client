/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.isolated;

import combatant.client.events.UsedImplicitly;
import combatant.client.runtime.annotation.RuntimeAssertion;
import combatant.client.runtime.annotation.RuntimeAssertionPhase;
import combatant.client.runtime.annotation.RuntimeResume;
import combatant.client.runtime.annotation.RuntimeSuspend;

@UsedImplicitly
public final class CombatantIsolatedRuntime {
    private boolean active = true;

    @UsedImplicitly
    @RuntimeSuspend
    private void suspend() {
        active = false;
    }

    @UsedImplicitly
    @RuntimeResume
    private void resume() {
        active = true;
    }

    @UsedImplicitly
    @RuntimeAssertion(phase = RuntimeAssertionPhase.SUSPENDED)
    private boolean assertSuspended() {
        return !active;
    }

    @UsedImplicitly
    @RuntimeAssertion(phase = RuntimeAssertionPhase.ACTIVE)
    private boolean assertActive() {
        return active;
    }
}
