/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

/**
 * Helpers for detecting "server-granted" creative-like flight.
 * <p>
 * In vanilla, {@link Abilities#mayfly} is set by the server via abilities packets.
 * This is true for Creative/Spectator, and can also be true on some servers that grant /fly
 * in non-creative gamemodes.
 */
public enum ServerFlyUtils {
    ;

    /**
     * Returns true if the server currently allows the player to fly (Creative/Spectator or server-granted fly).
     */
    public static boolean hasServerCreativeFly(LocalPlayer player) {
        if (player == null) return false;
        Abilities ab = player.getAbilities();
        if (ab == null) return false;
        return ab.mayfly || player.isSpectator();
    }

    /**
     * Returns true if flight is allowed, but the player is not in Creative/Spectator.
     * Useful if you specifically want to detect "server gave fly in survival-like modes".
     */
    public static boolean hasServerGrantedFlyInSurvivalLike(LocalPlayer player) {
        if (player == null) return false;
        Abilities ab = player.getAbilities();
        if (ab == null) return false;

        if (ab.instabuild || player.isSpectator()) return false;
        return ab.mayfly;
    }
}
