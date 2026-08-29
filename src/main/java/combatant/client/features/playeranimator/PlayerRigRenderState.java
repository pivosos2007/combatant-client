/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator;

/** Render-state extension carrying the solved rig from extraction into body and feature submits. */
public interface PlayerRigRenderState {
    PlayerRigInstance combatant$getPlayerRig();

    void combatant$setPlayerRig(PlayerRigInstance rig);
}
