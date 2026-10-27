/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.interaction;

import combatant.client.mixins.accessors.LocalPlayerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Which faces of a block a click may name.
 *
 * <p>A vanilla ray can only enter a face whose plane the eyes are in front of, and anticheats reject a click
 * on any other face (GrimAC PositionPlace: "placed a block against a hidden face"). Clicks go out before the
 * tick's movement packet, so the server judges them from the last position it received; the position the
 * client holds now is what the following packet carries. A face counts as visible only when it is in front
 * of the eyes at both.</p>
 *
 * <p>The planes are those of the unit cube. Block shapes lie inside it, so the test never accepts a face the
 * real shape hides from the server.</p>
 */
public final class FaceVisibility {

    private FaceVisibility() {
    }

    /** Feet position the server last received in a movement packet. */
    public static Vec3 lastSentPosition(LocalPlayer player) {
        LocalPlayerAccessor accessor = (LocalPlayerAccessor) player;
        return new Vec3(accessor.combatant$getLastSentX(), accessor.combatant$getLastSentY(),
                accessor.combatant$getLastSentZ());
    }

    /** Eye position the server last received. */
    public static Vec3 lastSentEyePosition(LocalPlayer player) {
        return lastSentPosition(player).add(0.0, player.getEyeHeight(), 0.0);
    }

    /**
     * Eyes the server judges a click or attack from when it goes out before this tick's movement packet: the
     * last sent ones. A passenger sends no player movement packets, so its current eyes stand in.
     */
    public static Vec3 serverEyePosition(LocalPlayer player) {
        return player.isPassenger() ? player.getEyePosition() : lastSentEyePosition(player);
    }

    /** Whether {@code face} of the block at {@code pos} faces the eyes now and where the server last saw them. */
    public static boolean isVisible(LocalPlayer player, BlockPos pos, Direction face) {
        return clearance(player, pos, face) >= 0.0;
    }

    /** Whether {@code eye} is on the outer side of, or on, the plane of {@code face} of the unit cube at {@code pos}. */
    public static boolean isVisibleFrom(Vec3 eye, BlockPos pos, Direction face) {
        return signedDistance(eye, pos, face) >= 0.0;
    }

    /**
     * The visible face of {@code pos} the eyes are furthest in front of, or null when every face is hidden
     * from one of the two eye positions.
     */
    public static Direction bestFace(LocalPlayer player, BlockPos pos) {
        Direction best = null;
        double bestClearance = 0.0;
        for (Direction face : Direction.values()) {
            double clearance = clearance(player, pos, face);
            if (clearance >= bestClearance) {
                bestClearance = clearance;
                best = face;
            }
        }
        return best;
    }

    /**
     * How far in front of {@code face}'s plane the eyes are, at the closer of the current and the server-known
     * position; negative when the face is hidden from either.
     */
    public static double clearance(LocalPlayer player, BlockPos pos, Direction face) {
        return Math.min(signedDistance(player.getEyePosition(), pos, face),
                signedDistance(serverEyePosition(player), pos, face));
    }

    private static double signedDistance(Vec3 eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN -> pos.getY() - eye.y;
            case UP -> eye.y - (pos.getY() + 1.0);
            case NORTH -> pos.getZ() - eye.z;
            case SOUTH -> eye.z - (pos.getZ() + 1.0);
            case WEST -> pos.getX() - eye.x;
            case EAST -> eye.x - (pos.getX() + 1.0);
        };
    }
}
