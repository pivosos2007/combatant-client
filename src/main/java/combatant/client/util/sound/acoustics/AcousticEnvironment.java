/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.acoustics;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Performs world queries on Minecraft's client thread; no OpenAL state is accessed here. */
public final class AcousticEnvironment {
    private static final double MAX_OCCLUSION_DISTANCE_SQR = 64.0 * 64.0;
    private static final int ROOM_PROBE_DISTANCE = 16;

    private AcousticEnvironment() {
    }

    public static AcousticProfile sample(ClientLevel level, Player player, Vec3 listener, Vec3 sound,
                                         boolean underwater, boolean occlusion, AcousticProfile.Room room) {
        int filter = 0;
        boolean wet = (underwater || room != AcousticProfile.Room.NONE)
                && (player.isUnderWater() || submergedInWater(level, sound));
        if (underwater && wet) filter = player.isUnderWater() ? 10 : 9;
        if (filter == 0 && occlusion) filter = occlusion(level, player, listener, sound);
        return new AcousticProfile(filter, wet ? AcousticProfile.Room.NONE : room);
    }

    private static boolean submergedInWater(ClientLevel level, Vec3 sound) {
        BlockPos pos = BlockPos.containing(sound);
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && sound.y - pos.getY() < fluid.getHeight(level, pos);
    }

    private static int occlusion(ClientLevel level, Player player, Vec3 listener, Vec3 sound) {
        double distanceSqr = listener.distanceToSqr(sound);
        if (distanceSqr < 2.25 || distanceSqr > MAX_OCCLUSION_DISTANCE_SQR) return 0;

        BlockPos sourceBlock = BlockPos.containing(sound);
        int blocked = 0;
        Vec3 lateral = sound.subtract(listener).cross(new Vec3(0.0, 1.0, 0.0));
        if (lateral.lengthSqr() < 1.0E-5) lateral = new Vec3(1.0, 0.0, 0.0);
        else lateral = lateral.normalize();

        if (blocked(level, player, listener, sound, sourceBlock)) blocked++;
        if (blocked(level, player, listener.add(lateral.scale(0.3)),
                sound.add(lateral.scale(0.3)), sourceBlock)) blocked++;
        if (blocked(level, player, listener.subtract(lateral.scale(0.3)),
                sound.subtract(lateral.scale(0.3)), sourceBlock)) blocked++;
        return blocked == 0 ? 0 : Math.min(8, (blocked * 8 + 1) / 3);
    }

    private static boolean blocked(ClientLevel level, Player player, Vec3 from, Vec3 to, BlockPos sourceBlock) {
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(sourceBlock);
    }

    /** Samples the listener's room once per environment refresh, rather than once per source. */
    public static AcousticProfile.Room room(ClientLevel level, Vec3 listener) {
        BlockPos center = BlockPos.containing(listener);
        if (level.canSeeSky(center) || level.getBrightness(LightLayer.SKY, center) > 7) {
            return AcousticProfile.Room.NONE;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int ceiling = 0;
        for (int dy = 1; dy <= 24; dy++) {
            cursor.set(center.getX(), center.getY() + dy, center.getZ());
            BlockState state = level.getBlockState(cursor);
            if (state.is(BlockTags.LEAVES)) return AcousticProfile.Room.NONE;
            if (state.isSolidRender()) {
                ceiling = dy;
                break;
            }
        }
        if (ceiling == 0) return AcousticProfile.Room.NONE;

        int walls = 0;
        int distance = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= ROOM_PROBE_DISTANCE; step++) {
                cursor.set(center.getX() + direction.getStepX() * step, center.getY(),
                        center.getZ() + direction.getStepZ() * step);
                if (level.getBlockState(cursor).isSolidRender()) {
                    walls++;
                    distance += step;
                    break;
                }
            }
        }
        if (walls < 2) return AcousticProfile.Room.NONE;
        return ceiling >= 5 && distance >= walls * 4
                ? AcousticProfile.Room.CAVE : AcousticProfile.Room.TUNNEL;
    }
}
