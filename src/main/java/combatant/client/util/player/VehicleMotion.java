/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Helpers for steering a ridden vehicle from the local player's movement keys.
 */
public final class VehicleMotion {

    private VehicleMotion() {
    }

    /** True when any horizontal movement key is held. */
    public static boolean hasHorizontalInput(Minecraft mc) {
        return axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) != 0
                || axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) != 0;
    }

    /** Horizontal vector of the given length pointing where the player's keys and yaw point; zero without input. */
    public static Vec3 horizontal(Minecraft mc, LocalPlayer player, double speed) {
        int forward = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        int strafe = axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
        if (forward == 0 && strafe == 0) return Vec3.ZERO;

        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = -sin * forward + cos * strafe;
        double z = cos * forward + sin * strafe;
        double len = Math.hypot(x, z);
        return new Vec3(x / len * speed, 0.0, z / len * speed);
    }

    private static int axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }
}
