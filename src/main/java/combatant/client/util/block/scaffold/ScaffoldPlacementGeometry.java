/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.scaffold;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry a block placement has to satisfy to look like a vanilla click, as strict anticheats check it:
 * <ul>
 *   <li>the eye is on the outer side of the clicked face's plane (GrimAC PositionPlace, zero slack, measured at the
 *       position of the last movement packet, which at the start of a tick is the position the client has now);</li>
 *   <li>a ray along the sent rotation hits the supporting block, measured from the position of the movement packet
 *       that FOLLOWS the click (GrimAC RotationPlace). At the start of a tick that is the position after this tick's
 *       movement, so the ray has to hit from where the player is now and from where he is about to be.</li>
 * </ul>
 * Aiming at the middle of the clicked face from the current eye satisfies the first rule and breaks the second one
 * for a bridging player: the eye hangs a few centimetres over the block's edge, the aim is nearly straight down, and
 * one tick of walking later the same ray passes in front of the block.
 */
public final class ScaffoldPlacementGeometry {
    /** Grim's eye box has no tolerance while position packets arrive every tick, so stay a hair on the right side. */
    public static final double FACE_MARGIN = 0.002;

    private static final double RAY_LENGTH = 12.0;
    /** Fraction of the movement step added on top of the prediction when scoring candidate rotations. */
    private static final double OVERSHOOT = 0.35;
    private static final double SAFE_INSET = 0.08;
    private static final double[] INSETS = {0.35, 0.25, 0.15, SAFE_INSET, 0.0};
    private static final float TURN_SLACK = 6.0f;
    private static final float YAW_SLACK = 10.0f;
    private static final float HALF_TURN_MARGIN = 0.2f;
    private static final float PITCH_STEP = 1.5f;
    private static final int FREE_YAW_STEPS = 3;
    private static final float FREE_YAW_STEP = 8.0f;

    private ScaffoldPlacementGeometry() {
    }

    /** How far the eye lies on the outer side of the face plane of {@code shape}; negative when it is behind it. */
    public static double outwardDistance(Vec3 eye, Direction face, AABB shape) {
        return switch (face) {
            case UP -> eye.y - shape.maxY;
            case DOWN -> shape.minY - eye.y;
            case NORTH -> shape.minZ - eye.z;
            case SOUTH -> eye.z - shape.maxZ;
            case WEST -> shape.minX - eye.x;
            case EAST -> eye.x - shape.maxX;
        };
    }

    public static boolean rayHitsBox(Vec3 eye, Vec3 direction, AABB box) {
        if (box.contains(eye)) {
            return true;
        }
        return box.clip(eye, eye.add(direction.scale(RAY_LENGTH))).isPresent();
    }

    /** Face of the unit block at {@code pos} that the ray enters through, or null when it misses. */
    public static Direction firstHitFace(Vec3 eye, Vec3 direction, BlockPos pos) {
        BlockHitResult hit = AABB.clip(List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), eye, eye.add(direction.scale(RAY_LENGTH)), pos);
        return hit == null ? null : hit.getDirection();
    }

    /**
     * Whether clicking {@code face} of the block at {@code pos} now, with {@code rotation} going out in this tick's
     * movement packet, passes both rules. {@code eyeNext} is the predicted eye position after this tick's movement.
     */
    public static boolean isSafe(Vec3 eyeNow, Vec3 eyeNext, Rotation rotation, BlockPos pos, Direction face, AABB shape) {
        if (eyeNow == null || eyeNext == null || rotation == null || shape == null) {
            return false;
        }
        if (outwardDistance(eyeNow, face, shape) < FACE_MARGIN) {
            return false;
        }
        Vec3 direction = rotation.directionVector();
        AABB cube = new AABB(pos);
        return rayHitsBox(eyeNow, direction, cube) && rayHitsBox(eyeNext, direction, cube);
    }

    /** Free-aim variant of {@link #chooseRotation(Vec3, Vec3, BlockPos, Direction, Rotation, Float)}. */
    public static Rotation chooseRotation(Vec3 eyeNow, Vec3 eyeNext, BlockPos pos, Direction face, Rotation current) {
        return chooseRotation(eyeNow, eyeNext, pos, face, current, null);
    }

    /**
     * Picks the rotation that keeps the ray on the block from the eye now until the end of the tick with room to
     * spare. Among the aims that keep at least {@link #SAFE_INSET} of room it prefers a ray that enters through the
     * clicked face, then the smallest turn from {@code current} (candidates within {@link #TURN_SLACK} degrees of the
     * smallest turn count as equal, so the head does not creep to the edge of what is allowed), then the most room.
     *
     * <p>{@code yawAnchor} is the player's own yaw when the movement is silently corrected to the sent rotation: that
     * correction only reproduces the intended walking direction for yaws that are a multiple of 45 degrees away from
     * the player's, anything else turns into a sideways drift of the same size as the error. Then only those yaws are
     * tried. With {@code null} the yaw is free and stays within a few degrees of looking straight at the block.
     * Returns null when no aim works.
     */
    public static Rotation chooseRotation(Vec3 eyeNow, Vec3 eyeNext, BlockPos pos, Direction face, Rotation current, Float yawAnchor) {
        Vec3 mid = eyeNow.add(eyeNext).scale(0.5);
        Vec3 overshoot = eyeNext.add(eyeNext.subtract(eyeNow).scale(OVERSHOOT));
        Vec3[] eyes = {eyeNow, eyeNext, overshoot};
        AABB cube = new AABB(pos);

        float straight = straightAtBlockYaw(mid, pos, face);
        List<Candidate> candidates = new ArrayList<>();
        for (float yaw : candidateYaws(straight, yawAnchor)) {
            for (float pitch = -90.0f; pitch <= 90.0f; pitch += PITCH_STEP) {
                Rotation rotation = new Rotation(yaw, pitch, false);
                Vec3 direction = rotation.directionVector();
                if (!rayHitsBox(eyeNow, direction, cube) || !rayHitsBox(eyeNext, direction, cube)) {
                    continue;
                }
                double inset = robustInset(eyes, direction, pos);
                if (inset < 0.0) {
                    continue;
                }
                boolean faceFirst = firstHitFace(eyeNow, direction, pos) == face;
                float turn = current == null ? 0.0f : current.angleTo(rotation);
                candidates.add(new Candidate(rotation, inset, faceFirst, turn, Math.abs(Mth.wrapDegrees(yaw - straight))));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        List<Candidate> pool = filterIfAny(candidates, c -> c.inset >= SAFE_INSET);
        pool = filterIfAny(pool, c -> c.faceFirst);
        // Look as straight at the block as the allowed yaws permit: a 45 degree squint is just as safe but pointless.
        float smallestDeviation = Float.MAX_VALUE;
        for (Candidate c : pool) {
            smallestDeviation = Math.min(smallestDeviation, c.yawDeviation);
        }
        float deviationLimit = smallestDeviation + YAW_SLACK;
        pool = filterIfAny(pool, c -> c.yawDeviation <= deviationLimit);
        float smallestTurn = Float.MAX_VALUE;
        for (Candidate c : pool) {
            smallestTurn = Math.min(smallestTurn, c.turn);
        }
        float limit = smallestTurn + TURN_SLACK;
        Candidate best = null;
        for (Candidate c : pool) {
            if (c.turn > limit) {
                continue;
            }
            if (best == null || c.inset > best.inset + 1.0E-9 || (Math.abs(c.inset - best.inset) <= 1.0E-9 && c.turn < best.turn)) {
                best = c;
            }
        }
        return best.rotation;
    }

    /** Yaw that looks straight at the block: level with the eye along the face, half way through it along its normal. */
    private static float straightAtBlockYaw(Vec3 mid, BlockPos pos, Direction face) {
        double x = face.getAxis() == Direction.Axis.X ? 0.5 : Mth.clamp(mid.x - pos.getX(), 0.12, 0.88);
        double z = face.getAxis() == Direction.Axis.Z ? 0.5 : Mth.clamp(mid.z - pos.getZ(), 0.12, 0.88);
        Vec3 aim = new Vec3(pos.getX() + x, mid.y, pos.getZ() + z);
        Vec3 delta = aim.subtract(mid);
        return Math.hypot(delta.x, delta.z) < 1.0E-3 ? 0.0f : Rotation.lookingAt(aim, mid).yaw();
    }

    private static float[] candidateYaws(float straight, Float yawAnchor) {
        if (yawAnchor != null) {
            float[] yaws = new float[8];
            for (int i = 0; i < yaws.length; i++) {
                yaws[i] = Mth.wrapDegrees(yawAnchor + 45.0f * i);
            }
            return yaws;
        }
        float[] yaws = new float[2 * FREE_YAW_STEPS + 1];
        for (int i = 0; i < yaws.length; i++) {
            yaws[i] = Mth.wrapDegrees(straight + (i - FREE_YAW_STEPS) * FREE_YAW_STEP);
        }
        return yaws;
    }

    private static List<Candidate> filterIfAny(List<Candidate> pool, java.util.function.Predicate<Candidate> keep) {
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : pool) {
            if (keep.test(c)) {
                kept.add(c);
            }
        }
        return kept.isEmpty() ? pool : kept;
    }

    private record Candidate(Rotation rotation, double inset, boolean faceFirst, float turn, float yawDeviation) {
    }

    /**
     * Moves a yaw that points (nearly) straight away from the player's own yaw a hair inside the half turn, on the
     * side the server already has. The sent yaw is carried as an unwrapped number: one that lands just past the
     * half turn (-180.01) makes the smooth return to the player's yaw go the long way round to -360, and the first
     * movement packet after it jumps back by 360, which GrimAC's AimModulo360 reports. Landing a little inside
     * returns the short way. Moonwalking aims exactly there, so this applies to every bridge.
     */
    public static Rotation avoidHalfTurn(Rotation rotation, float playerYaw, Rotation serverRotation) {
        float relative = Mth.wrapDegrees(rotation.yaw() - playerYaw);
        float limit = 180.0f - HALF_TURN_MARGIN;
        if (Math.abs(relative) <= limit) {
            return rotation;
        }
        float side = serverRotation != null && Math.abs(Mth.wrapDegrees(serverRotation.yaw() - playerYaw)) > 90.0f
                ? Math.signum(Mth.wrapDegrees(serverRotation.yaw() - playerYaw))
                : Math.signum(relative);
        return new Rotation(playerYaw + (side == 0.0f ? limit : side * limit), rotation.pitch(), false);
    }

    /**
     * The point of the clicked face to put in the packet: where the view ray meets the block, moved onto the face.
     * Always inside the face, so cursor checks (InvalidPlaceCursor, FabricatedPlace) and the server's own bounds
     * check are satisfied whatever the ray does.
     */
    public static Vec3 clickPoint(Vec3 eyeNow, Rotation rotation, BlockPos pos, Direction face, AABB shape) {
        Vec3 hit = new AABB(pos).clip(eyeNow, eyeNow.add(rotation.directionVector().scale(RAY_LENGTH))).orElse(shape.getCenter());
        double x = face.getAxis() == Direction.Axis.X ? (face == Direction.EAST ? shape.maxX : shape.minX) : inside(hit.x, shape.minX, shape.maxX);
        double y = face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? shape.maxY : shape.minY) : inside(hit.y, shape.minY, shape.maxY);
        double z = face.getAxis() == Direction.Axis.Z ? (face == Direction.SOUTH ? shape.maxZ : shape.minZ) : inside(hit.z, shape.minZ, shape.maxZ);
        return new Vec3(x, y, z);
    }

    private static double inside(double value, double min, double max) {
        double lo = min + 0.01;
        double hi = max - 0.01;
        return lo > hi ? (min + max) * 0.5 : Mth.clamp(value, lo, hi);
    }

    /** Largest inset of the block for which every eye's ray still hits it, or -1 when even the full block is missed. */
    private static double robustInset(Vec3[] eyes, Vec3 direction, BlockPos pos) {
        AABB cube = new AABB(pos);
        for (double inset : INSETS) {
            AABB core = cube.inflate(-inset);
            boolean all = true;
            for (Vec3 eye : eyes) {
                if (!rayHitsBox(eye, direction, core)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return inset;
            }
        }
        return -1.0;
    }
}
