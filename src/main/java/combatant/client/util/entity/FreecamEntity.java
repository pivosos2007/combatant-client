/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;

import java.util.UUID;

public class FreecamEntity extends RemotePlayer {
    private static final int FREECAM_ID_BASE = -1_100_000;
    private static final int FREECAM_ID_RANGE = 10_000;
    private static int nextFreecamEntityId = FREECAM_ID_BASE;

    public FreecamEntity(ClientLevel world) {
        super(world, new GameProfile(UUID.randomUUID(), "freecam"));
        this.setId(allocateFreecamEntityId(world));
        this.noPhysics = true;
        this.setInvisible(true);
        this.setInvulnerable(true);
    }

    private static int allocateFreecamEntityId(ClientLevel world) {
        for (int i = 0; i < FREECAM_ID_RANGE; i++) {
            int candidate = nextFreecamEntityId--;
            if (nextFreecamEntityId < FREECAM_ID_BASE - FREECAM_ID_RANGE) {
                nextFreecamEntityId = FREECAM_ID_BASE;
            }
            if (world.getEntity(candidate) == null) {
                return candidate;
            }
        }
        return FREECAM_ID_BASE;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return false; // якорь: в мире может существовать, но не рисуется
    }
}
