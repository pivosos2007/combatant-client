/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class AutoCrystalAttempt {
    private final long time;
    private final int attempts;
    private final float distanceSq;
    private final Vec3 pos;

    public AutoCrystalAttempt(LocalPlayer player, long time, int attempts, Vec3 pos) {
        this.time = time;
        this.attempts = attempts;
        this.pos = pos;
        this.distanceSq = player != null && pos != null ? (float) player.distanceToSqr(pos) : 0.0f;
    }

    public AutoCrystalAttempt incremented(LocalPlayer player) {
        return new AutoCrystalAttempt(player, time, attempts + 1, pos);
    }

    public boolean shouldRemove(LocalPlayer player) {
        return player == null || pos == null || Math.abs(distanceSq - (float) player.distanceToSqr(pos)) >= 1.0f;
    }

    public boolean canSetBlocked(int ping) {
        return attempts >= Math.max(1.0f, ping / 25.0f);
    }
}
