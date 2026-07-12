/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.aiming;

/**
 * Runnable that can only be executed once.
 * <p>
 * Ported from LiquidBounce (CCBlueX) concept.
 */
public final class RestrictedSingleUseAction {

    private final Runnable action;
    private boolean used;

    public RestrictedSingleUseAction(Runnable action) {
        this.action = action;
    }

    public void invoke() {
        if (used) return;
        used = true;
        if (action != null) {
            action.run();
        }
    }
}
