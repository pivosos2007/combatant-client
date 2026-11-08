/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;

/**
 * Helpers for detecting "server-granted" creative-like flight.
 * <p>
 * In vanilla, {@link Abilities#mayfly} is set by the server via abilities packets.
 * This is true for Creative/Spectator, and can also be true on some servers that grant /fly
 * in non-creative gamemodes.
 */
public enum ServerFlyUtils {
    ;

    private record Grant(LocalPlayer player, boolean mayFly, float flySpeed) { }

    private static volatile Grant lastServerGrant;

    /** Records only the actual clientbound abilities packet, before client-side overrides. */
    public static void observeServerAbilities(LocalPlayer player, ClientboundPlayerAbilitiesPacket packet) {
        if (player == null || packet == null) return;
        lastServerGrant = new Grant(player, packet.canFly(), packet.getFlyingSpeed());
    }

    /** Unlike local Abilities.mayfly, this cannot be enabled by Flight/Abilities spoofing. */
    public static boolean hasVerifiedServerFlight(LocalPlayer player) {
        Grant grant = lastServerGrant;
        return player != null && grant != null && grant.player() == player && grant.mayFly();
    }

    public static boolean hasVerifiedActiveServerFlight(LocalPlayer player) {
        return hasVerifiedServerFlight(player)
                && player.getAbilities().mayfly && player.getAbilities().flying;
    }

    public static float serverFlyingSpeed(LocalPlayer player) {
        Grant grant = lastServerGrant;
        return player != null && grant != null && grant.player() == player ? grant.flySpeed() : 0.05f;
    }

    /** Local capability check; it is NOT proof of server permission. */
    public static boolean hasServerCreativeFly(LocalPlayer player) {
        if (player == null) return false;
        Abilities ab = player.getAbilities();
        return ab != null && (ab.mayfly || player.isSpectator());
    }

    /** Local capability check; it is NOT proof of server permission. */
    public static boolean hasServerGrantedFlyInSurvivalLike(LocalPlayer player) {
        if (player == null) return false;
        Abilities ab = player.getAbilities();
        return ab != null && !ab.instabuild && !player.isSpectator() && ab.mayfly;
    }
}
