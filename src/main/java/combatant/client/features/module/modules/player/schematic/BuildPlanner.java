/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.mixins.accessors.BlockItemInvoker;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.schematic.BlockUse;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.StateAdjustments;
import combatant.client.util.schematic.StateComparison;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Picks the face and hit point that reproduce a schematic block state.
 *
 * <p>Rather than encode per-block placement rules, every candidate click is run through the block's own
 * {@code getStateForPlacement} with the player briefly turned to the aim rotation, exactly what the server
 * will do with the click. A click that yields the wanted state is exact; one that yields the right block in
 * another state (a stair facing the wrong way, a single slab where a double one goes) is approximate. Derived
 * state (power, lit, connections) does not count, and neither does what clicking the placed block sets later
 * (a repeater's delay, a comparator's mode, a note).</p>
 */
final class BuildPlanner {

    private static final double[] FACE_OFFSETS = {0.0, -0.3, 0.3};
    // The vertical centre of a side face is the top/bottom boundary for stairs, slabs and trapdoors: a click
    // planned there flips half on the smallest rotation rounding, so side faces are aimed clear of it.
    private static final double[] SIDE_HEIGHTS = {-0.25, 0.25};
    // Pistons, observers, droppers and dispensers face along the nearest axis of the look: when the coarse
    // points give the wrong axis, points near the face edges tilt the look far enough to change it.
    private static final double[] FINE_OFFSETS = {-0.44, -0.15, 0.15, 0.44};
    /** Degrees a planned look may drift and still give the same facing; matches the builder's check at click time. */
    static final float LOOK_MARGIN = 2.0f;

    private BuildPlanner() {
    }

    /**
     * One click that places the target. {@code sneak}: the support is a block that would open or toggle on a
     * plain click, so the click goes out sneaking (vanilla then places against it), aimed from the lower eyes.
     */
    record Plan(BlockPos target, BlockPos support, Direction face, Vec3 hit, Rotation rotation,
                boolean exact, double distanceSq, boolean sneak) {
    }

    static Plan plan(LocalPlayer player, Level level, BlockPos target, BlockState desired, ItemStack stack,
                     double range, boolean aimAtHit) {
        if (!(stack.getItem() instanceof BlockItem)) return null;
        Vec3 eyes = player.getEyePosition();
        Vec3 crouchedEyes = player.position().add(0.0, player.getEyeHeight(Pose.CROUCHING), 0.0);
        if (StateComparison.fewerPieces(level.getBlockState(target), desired)) {
            return planStack(player, level, target, desired, stack, range, aimAtHit);
        }
        Search search = new Search(player, level, target, desired, stack, range * range, aimAtHit);
        for (int pass = 0; pass < 2; pass++) {
            boolean fine = pass == 1;
            // The fine pass only helps blocks whose facing follows the look, and only when nothing exact came up.
            if (fine && (search.best == null || search.best.exact || !lookDependent(desired))) break;
            for (Direction toSupport : Direction.values()) {
                BlockPos support = target.relative(toSupport);
                BlockState supportState = level.getBlockState(support);
                if (supportState.canBeReplaced() || supportState.getShape(level, support).isEmpty()) continue;
                boolean sneak = isInteractive(supportState);
                search.face(support, toSupport.getOpposite(), sneak ? crouchedEyes : eyes, sneak, fine);
            }
        }
        return search.best;
    }

    /**
     * A click on the block already in the cell that adds a piece (the second slab of a double one, another candle).
     * Aims at the middle of the outline's face, like {@link #planClick}; the first face the ray really hits and
     * the item's own placement rule accepts wins.
     */
    private static Plan planStack(LocalPlayer player, Level level, BlockPos target, BlockState desired,
                                  ItemStack stack, double range, boolean aimAtHit) {
        VoxelShape shape = level.getBlockState(target).getShape(level, target);
        if (shape.isEmpty()) return null;
        AABB box = shape.bounds().move(target);
        Vec3 eyes = player.getEyePosition();
        double rangeSq = range * range;
        Vec3 center = box.getCenter();
        Plan best = null;
        for (Direction face : Direction.values()) {
            Vec3 point = new Vec3(
                    face.getStepX() == 0 ? center.x : face.getStepX() > 0 ? box.maxX - 0.01 : box.minX + 0.01,
                    face.getStepY() == 0 ? center.y : face.getStepY() > 0 ? box.maxY - 0.01 : box.minY + 0.01,
                    face.getStepZ() == 0 ? center.z : face.getStepZ() > 0 ? box.maxZ - 0.01 : box.minZ + 0.01);
            double distanceSq = eyes.distanceToSqr(point);
            if (distanceSq > rangeSq || best != null && best.distanceSq <= distanceSq) continue;
            Rotation rotation = Rotation.lookingAt(point, eyes);
            BlockHitResult hit = trace(player, eyes, rotation, Math.sqrt(distanceSq) + 0.5);
            if (hit == null || !hit.getBlockPos().equals(target) || hit.getDirection() != face) continue;
            BlockState result = simulate(player, level, stack, target, target, face, hit.getLocation(),
                    aimAtHit ? rotation : null);
            if (result == null || !exact(result, desired)) continue;
            best = new Plan(target, target, face, hit.getLocation(), rotation, true, distanceSq, false);
        }
        return best;
    }

    /** Best click found so far over the faces of the target's neighbours. */
    private static final class Search {
        private final LocalPlayer player;
        private final Level level;
        private final BlockPos target;
        private final BlockState desired;
        private final ItemStack stack;
        private final double rangeSq;
        private final boolean aimAtHit;
        private final boolean lookDependent;
        private Plan best;

        Search(LocalPlayer player, Level level, BlockPos target, BlockState desired, ItemStack stack, double rangeSq,
               boolean aimAtHit) {
            this.player = player;
            this.level = level;
            this.target = target;
            this.desired = desired;
            this.stack = stack;
            this.rangeSq = rangeSq;
            this.aimAtHit = aimAtHit;
            this.lookDependent = lookDependent(desired);
        }

        /** A look a couple of degrees off still gives the same facing: the click survives rotation rounding. */
        private boolean steady(BlockPos support, Direction face, Vec3 point, Rotation rotation) {
            float[][] nudges = {{LOOK_MARGIN, 0f}, {-LOOK_MARGIN, 0f}, {0f, LOOK_MARGIN}, {0f, -LOOK_MARGIN}};
            for (float[] nudge : nudges) {
                Rotation nudged = new Rotation(rotation.yaw() + nudge[0],
                        Math.max(-90.0f, Math.min(90.0f, rotation.pitch() + nudge[1])));
                BlockState result = simulate(player, level, stack, target, support, face, point, nudged);
                if (result == null || !exact(result, desired)) return false;
            }
            return true;
        }

        void face(BlockPos support, Direction face, Vec3 eyes, boolean sneak, boolean fine) {
            Vec3 center = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            // A face pointing away from the eyes cannot be clicked without looking through the support block.
            double facing = (eyes.x - center.x) * face.getStepX() + (eyes.y - center.y) * face.getStepY()
                    + (eyes.z - center.z) * face.getStepZ();
            if (facing <= 0.0) return;
            double[] across = fine ? FINE_OFFSETS : FACE_OFFSETS;
            double[] heights = fine ? FINE_OFFSETS : face.getAxis() == Direction.Axis.Y ? FACE_OFFSETS : SIDE_HEIGHTS;
            for (double u : across) {
                for (double v : heights) point(support, face, pointOnFace(center, face, u, v), eyes, sneak);
            }
        }

        private void point(BlockPos support, Direction face, Vec3 point, Vec3 eyes, boolean sneak) {
            double distanceSq = eyes.distanceToSqr(point);
            if (distanceSq > rangeSq) return;
            Rotation rotation = Rotation.lookingAt(point, eyes);
            BlockState result = simulate(player, level, stack, target, support, face, point, aimAtHit ? rotation : null);
            if (result == null || result.getBlock() != desired.getBlock()) return;
            boolean exact = exact(result, desired) && (!aimAtHit || !lookDependent || steady(support, face, point, rotation));
            Plan candidate = new Plan(target, support, face, point, rotation, exact, distanceSq, sneak);
            if (!better(candidate, best)) return;
            BlockHitResult hit = trace(player, eyes, rotation, Math.sqrt(distanceSq) + 0.5);
            if (hit == null || !hit.getBlockPos().equals(support) || hit.getDirection() != face) return;
            best = candidate;
        }
    }

    /**
     * A plain (not sneaking) click on the block at {@code pos} itself, to step a repeater's delay, a comparator's
     * mode, a note or a lever. Aims at the middle of the block's outline and at the middle of each face of it
     * that looks towards the eyes; the first one the ray really hits wins.
     */
    static Plan planClick(LocalPlayer player, Level level, BlockPos pos, double range) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) return null;
        AABB box = shape.bounds().move(pos);
        Vec3 eyes = player.getEyePosition();
        double rangeSq = range * range;
        Plan best = null;
        Vec3 center = box.getCenter();
        best = clickAt(player, pos, center, eyes, rangeSq, best);
        for (Direction face : Direction.values()) {
            Vec3 onFace = new Vec3(
                    face.getStepX() == 0 ? center.x : face.getStepX() > 0 ? box.maxX - 0.01 : box.minX + 0.01,
                    face.getStepY() == 0 ? center.y : face.getStepY() > 0 ? box.maxY - 0.01 : box.minY + 0.01,
                    face.getStepZ() == 0 ? center.z : face.getStepZ() > 0 ? box.maxZ - 0.01 : box.minZ + 0.01);
            best = clickAt(player, pos, onFace, eyes, rangeSq, best);
        }
        return best;
    }

    private static Plan clickAt(LocalPlayer player, BlockPos pos, Vec3 point, Vec3 eyes, double rangeSq, Plan best) {
        double distanceSq = eyes.distanceToSqr(point);
        if (distanceSq > rangeSq || best != null && best.distanceSq <= distanceSq) return best;
        Rotation rotation = Rotation.lookingAt(point, eyes);
        BlockHitResult hit = trace(player, eyes, rotation, Math.sqrt(distanceSq) + 0.5);
        if (hit == null || !hit.getBlockPos().equals(pos)) return best;
        return new Plan(pos, pos, hit.getDirection(), hit.getLocation(), rotation, true, distanceSq, false);
    }

    /**
     * Whether a click producing {@code result} builds {@code desired}: equal up to derived state (see
     * {@link StateComparison}) and to what a few clicks on the placed block still set (delay, mode, note).
     */
    static boolean exact(BlockState result, BlockState desired) {
        // One candle of three or the first slab of a double one is the right step; the cell stays open for the rest.
        return result == desired || StateComparison.fewerPieces(result, desired)
                || StateAdjustments.clicksNeeded(result, desired) != StateAdjustments.IMPOSSIBLE;
    }

    /**
     * Whether the look can change how this block comes out: the redstone parts {@link RedstoneRules} knows, plus
     * anything with a facing (stairs, furnaces, chests, pumpkins, barrels...), whose placement rule reads the
     * player's yaw or look.
     */
    static boolean lookDependent(BlockState desired) {
        return RedstoneRules.lookDecides(desired) || desired.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || desired.hasProperty(BlockStateProperties.FACING) || desired.hasProperty(BlockStateProperties.ROTATION_16);
    }

    /**
     * The horizontal direction to look along so a click on this cell comes out exact, or {@code null} when none
     * does from here. First the planned click with the look turned to each side (a stair or furnace that only
     * faces wrong); then the side face of each placed neighbour looked at head-on, high and low (a top slab or
     * an upside-down stair the floor click cannot give). The walker uses it to pick a spot facing that way.
     */
    static Direction lookFixing(LocalPlayer player, Level level, ItemStack stack, Plan plan, BlockState desired) {
        float pitch = plan.rotation().pitch();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState result = simulate(player, level, stack, plan.target(), plan.support(), plan.face(), plan.hit(),
                    new Rotation(side.toYRot(), pitch));
            if (result != null && exact(result, desired)) return side;
        }
        for (Direction toSupport : Direction.Plane.HORIZONTAL) {
            BlockPos support = plan.target().relative(toSupport);
            BlockState supportState = level.getBlockState(support);
            if (supportState.canBeReplaced() || supportState.getShape(level, support).isEmpty()) continue;
            Direction face = toSupport.getOpposite();
            Vec3 center = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, 0.0, face.getStepZ() * 0.5);
            Rotation headOn = new Rotation(toSupport.toYRot(), 0.0f);
            for (double v : SIDE_HEIGHTS) {
                BlockState result = simulate(player, level, stack, plan.target(), support, face, center.add(0.0, v, 0.0),
                        headOn);
                if (result != null && exact(result, desired)) return toSupport;
            }
        }
        return null;
    }

    /** Half a step of the 16-way rotation, in degrees. */
    static final float ROTATION_HALF_STEP = 11.25f;

    /** The yaw that sits in the middle of 16-step rotation {@code rotation} (vanilla {@code RotationSegment}: r * 22.5). */
    static float yawForRotation(int rotation) {
        return net.minecraft.world.level.block.state.properties.RotationSegment.convertToDegrees(rotation & 15);
    }

    /** The 16-step rotation vanilla derives from a yaw. */
    static int rotationForYaw(float yaw) {
        return net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(yaw);
    }

    /**
     * The first yaw, nearest {@code fromYaw} first, among the 16 rotation centres for which {@code exactAt} says the
     * click comes out exact; {@code null} when none does. Centres, not edges, so a small aiming error keeps the
     * rotation.
     */
    static Float chooseRotationYaw(float fromYaw, java.util.function.Predicate<Float> exactAt) {
        Float best = null;
        float bestGap = Float.MAX_VALUE;
        for (int r = 0; r < 16; r++) {
            float yaw = yawForRotation(r);
            float gap = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - fromYaw));
            if (gap < bestGap && exactAt.test(yaw)) {
                best = yaw;
                bestGap = gap;
            }
        }
        return best;
    }

    /**
     * The yaw to face so a block with a 16-step rotation comes out exact, or {@code null} when no yaw does from
     * this click. The walker uses it to pick a standing spot whose line to the cell has that yaw.
     */
    static Float rotationFixing(LocalPlayer player, Level level, ItemStack stack, Plan plan, BlockState desired) {
        if (!desired.hasProperty(BlockStateProperties.ROTATION_16)) return null;
        float pitch = plan.rotation().pitch();
        return chooseRotationYaw(plan.rotation().yaw(), yaw -> {
            BlockState result = simulate(player, level, stack, plan.target(), plan.support(), plan.face(), plan.hit(),
                    new Rotation(yaw, pitch));
            return result != null && exact(result, desired);
        });
    }

    /**
     * Whether the line from the eyes to a cell, offset {@code dx}/{@code dz}, has a yaw that stays within the
     * rotation step of {@code wantedYaw} even when the click lands a little off the cell centre.
     */
    static boolean yawAllows(double dx, double dz, float wantedYaw) {
        double dist = Math.hypot(dx, dz);
        if (dist < 0.75) return false;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double gap = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - wantedYaw));
        double spread = Math.toDegrees(Math.asin(Math.min(1.0, 0.35 / dist)));
        return gap + spread <= ROTATION_HALF_STEP;
    }

    private static boolean better(Plan candidate, Plan best) {
        if (best == null) return true;
        if (candidate.exact != best.exact) return candidate.exact;
        // Sneaking costs a tick to crouch and moves the eyes; a plain click wins a tie.
        if (candidate.sneak != best.sneak) return !candidate.sneak;
        return candidate.distanceSq < best.distanceSq;
    }

    private static Vec3 pointOnFace(Vec3 center, Direction face, double u, double v) {
        return switch (face.getAxis()) {
            case X -> center.add(0.0, v, u);
            case Y -> center.add(u, 0.0, v);
            case Z -> center.add(u, v, 0.0);
        };
    }

    /**
     * The state the block item would place for this click. {@code rotation} temporarily replaces the player's
     * look so facing-dependent blocks resolve the way the server will see them; {@code null} keeps the
     * player's own rotation.
     */
    static BlockState simulate(LocalPlayer player, Level level, ItemStack stack, BlockPos target, BlockPos support,
                               Direction face, Vec3 hit, Rotation rotation) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        try {
            if (rotation != null) {
                player.setYRot(rotation.yaw());
                player.setXRot(rotation.pitch());
            }
            BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(hit, face, support, false));
            if (!context.canPlace() || !context.getClickedPos().equals(target)) return null;
            // The item's rule, not the block's: a torch item turns into a wall torch when the look says so.
            BlockState state = ((BlockItemInvoker) blockItem).combatant$getPlacementState(context);
            if (state == null || !state.canSurvive(level, target)) return null;
            if (!level.isUnobstructed(state, target, CollisionContext.placementContext(player))) return null;
            return state;
        } catch (RuntimeException e) {
            return null;
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    static BlockHitResult trace(LocalPlayer player, Rotation rotation, double range) {
        return trace(player, player.getEyePosition(), rotation, range);
    }

    static BlockHitResult trace(LocalPlayer player, Vec3 eyes, Rotation rotation, double range) {
        if (rotation == null) return null;
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hit = player.level().clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    static boolean matches(BlockHitResult hit, Plan plan) {
        return hit != null && hit.getBlockPos().equals(plan.support()) && hit.getDirection() == plan.face();
    }

    /**
     * Clicking these without sneaking opens, toggles or feeds them instead of placing against them: containers,
     * hoppers, droppers, dispensers, crafters, doors, levers, buttons, repeaters, comparators, note blocks,
     * daylight sensors, workstations. The builder places against them sneaking, the vanilla rule.
     */
    static boolean isInteractive(BlockState state) {
        Block block = state.getBlock();
        // Anything whose class overrides a use hook (composter, cake, bell, dust, dragon egg, candles, cave
        // vines...) is sneaked against; the list below names the well-known ones for readers.
        return BlockUse.reactsToClick(state)
                || block instanceof BaseEntityBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof BedBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof NoteBlock
                || block instanceof DiodeBlock
                || block instanceof LoomBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof DaylightDetectorBlock
                || block instanceof CakeBlock
                || block instanceof CandleCakeBlock
                || block instanceof ComposterBlock
                || block instanceof FlowerPotBlock
                || block instanceof RespawnAnchorBlock;
    }
}
