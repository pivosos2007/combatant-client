/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.ClientPlayerAbilitiesEvent;
import net.minecraft.client.Minecraft;

/** Records server-authorized flight state before local modules process it. */
public enum ServerAbilitiesObserver {
    INSTANCE;

    @EventHandler(priority = 100)
    private void onServerAbilities(ClientPlayerAbilitiesEvent event) {
        ServerFlyUtils.observeServerAbilities(Minecraft.getInstance().player, event.getPacket());
    }
}
