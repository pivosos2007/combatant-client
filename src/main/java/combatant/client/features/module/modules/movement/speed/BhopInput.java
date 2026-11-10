/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.speed;

/**
 * Computes jump and sprint flags for one bunny-hop input tick.
 * When enabled and grounded, a jump requires unopposed input on at least one axis;
 * sprint is requested only for unopposed forward input.
 */
public record BhopInput(boolean jump, boolean sprint) {

    public static final BhopInput NONE = new BhopInput(false, false);

    /**
     * @param allowed false while swimming, sneaking, climbing, riding or gliding
     */
    public static BhopInput decide(boolean allowed, boolean onGround,
                                   boolean forward, boolean backward, boolean left, boolean right) {
        if (!allowed || !onGround) return NONE;
        boolean walking = forward != backward;
        boolean strafing = left != right;
        if (!walking && !strafing) return NONE;
        return new BhopInput(true, walking && forward);
    }
}
