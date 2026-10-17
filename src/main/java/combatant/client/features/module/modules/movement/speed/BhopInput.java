/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement.speed;

/**
 * What Speed's bunny-hop mode puts into the input packet for one tick. It only ever sets the jump and sprint bits
 * of the keyboard state, never a velocity, so the movement that follows is vanilla physics that GrimAC's prediction
 * can reproduce. Pure so the key combinations (opposite keys cancel, nothing happens in the air) are covered by
 * unit tests.
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
