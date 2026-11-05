/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VehicleMotion {

    private VehicleMotion() {
    }

    public static boolean hasHorizontalInput(Minecraft mc) {
        return axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown()) != 0
                || axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown()) != 0;
    }

    public static Vec3 horizontal(Minecraft mc, LocalPlayer player, double speed) {
        return horizontal(mc, player.getYRot(), speed);
    }

    public static Vec3 horizontal(Minecraft mc, float yawDegrees, double speed) {
        int forward = axis(mc.options.keyUp.isDown(), mc.options.keyDown.isDown());
        int strafe = axis(mc.options.keyLeft.isDown(), mc.options.keyRight.isDown());
        if (forward == 0 && strafe == 0) return Vec3.ZERO;

        double yaw = Math.toRadians(yawDegrees);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double x = -sin * forward + cos * strafe;
        double z = cos * forward + sin * strafe;
        double len = Math.hypot(x, z);
        return new Vec3(x / len * speed, 0.0, z / len * speed);
    }

    public static double waterSurfaceDistance(Entity vehicle) {
        if (vehicle.isInLava() || vehicle.onGround()) return Double.NaN;
        Level level = vehicle.level();
        AABB box = vehicle.getBoundingBox();
        double feet = box.minY;
        double inset = Math.min(0.12D, Math.min(box.getXsize(), box.getZsize()) * 0.2D);
        double surface = waterSurface(level, vehicle.getX(), vehicle.getZ(), feet);
        if (Double.isNaN(surface)) return Double.NaN;
        double first = waterSurface(level, box.minX + inset, box.minZ + inset, feet);
        double second = waterSurface(level, box.minX + inset, box.maxZ - inset, feet);
        double third = waterSurface(level, box.maxX - inset, box.minZ + inset, feet);
        double fourth = waterSurface(level, box.maxX - inset, box.maxZ - inset, feet);
        if (Double.isNaN(first) || Double.isNaN(second)
                || Double.isNaN(third) || Double.isNaN(fourth)
                || Math.abs(first - surface) > 0.03D
                || Math.abs(second - surface) > 0.03D
                || Math.abs(third - surface) > 0.03D
                || Math.abs(fourth - surface) > 0.03D) return Double.NaN;
        double distance = surface - feet;
        return distance >= -0.20D && distance <= 1.20D ? distance : Double.NaN;
    }

    private static double waterSurface(Level level, double x, double z, double feet) {
        BlockPos pos = BlockPos.containing(x, feet - 0.24D, z);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
            pos = BlockPos.containing(x, feet + 0.04D, z);
            fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return Double.NaN;
        }
        for (int i = 0; i < 4; i++) {
            BlockPos above = pos.above();
            FluidState next = level.getFluidState(above);
            if (!next.is(FluidTags.WATER)) break;
            if (!next.isSource()) return Double.NaN;
            pos = above;
            fluid = next;
            if (i == 3) return Double.NaN;
        }
        return pos.getY() + fluid.getHeight(level, pos);
    }

    private static int axis(boolean positive, boolean negative) {
        return (positive ? 1 : 0) - (negative ? 1 : 0);
    }
}
