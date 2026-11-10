/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events.impl;

import combatant.client.events.Event;
import lombok.Getter;

/**
 * Resolves a manual attack before Minecraft.startAttack runs. The first handler
 * to resolve the event replaces vanilla's return value: true reports a handled
 * attack, false rejects it. Later handlers cannot replace that decision.
 */
@Getter
public final class ManualAttackEvent extends Event {
    private boolean resolved;
    private boolean returnValue;

    public void resolve(boolean returnValue) {
        if (resolved) return;
        resolved = true;
        this.returnValue = returnValue;
    }
}
