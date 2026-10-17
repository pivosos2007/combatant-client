/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.server;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

public final class DonutSmp {

    private DonutSmp() {
    }

    public static boolean isConnected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        ServerData server = mc.getCurrentServer();
        return server != null && isDonutAddress(server.ip);
    }

    public static boolean isDonutAddress(String address) {
        return address != null && address.toLowerCase(Locale.ROOT).contains("donutsmp");
    }
}
