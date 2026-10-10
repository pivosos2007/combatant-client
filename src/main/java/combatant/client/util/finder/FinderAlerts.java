/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import combatant.client.features.module.Notifier;
import combatant.client.features.command.CommandOutput;

/**
 * One place for "found something" feedback so every finder behaves the same way:
 * a HUD toast, optionally a chat line (so coordinates survive after the toast fades), and
 * optionally a sound.
 */
public final class FinderAlerts {

    private FinderAlerts() {
    }

    public static void found(String finder, String what, int x, int y, int z, boolean chat, boolean sound) {
        Minecraft mc = Minecraft.getInstance();
        int distance = mc.player == null ? -1 : (int) Math.sqrt(mc.player.distanceToSqr(x + 0.5, y + 0.5, z + 0.5));
        String coords = x + " " + y + " " + z;
        String suffix = distance >= 0 ? " (" + distance + "m)" : "";

        Notifier.info(finder + ": " + what + " at " + coords + suffix);

        if (chat && mc.player != null) {
            CommandOutput.send(finder + ": " + what + " " + coords + suffix);
        }
        if (sound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.6f));
        }
    }
}
