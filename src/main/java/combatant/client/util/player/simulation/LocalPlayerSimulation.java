/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.simulation;

import net.minecraft.world.entity.player.Player;

final class LocalPlayerSimulation extends PlayerMovementSimulation {

    LocalPlayerSimulation(Player player, SimulatedInput input) {
        super(player, input);
    }
}
