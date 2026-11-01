/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.playermodel;

public interface PlayerModelRenderState {
    PlayerModels.Snapshot combatant$getReplacement();
    void combatant$setReplacement(PlayerModels.Snapshot replacement);
}
