/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.interaction;

import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import combatant.client.util.player.BreakSpeedUtil;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/**
 * Shared execution layer for block actions.
 *
 * <p>Target selection belongs to modules. This class owns the parts which must stay coherent across
 * modules: server-facing rotation, reach/visibility validation, vanilla destroy state, hand/tool
 * selection through {@link InventorySwap}, and the final block interaction call.</p>
 *
 * <p>The pipeline deliberately does not implement packet-mining tricks. Mining goes through
 * {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#startDestroyBlock} and
 * {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#continueDestroyBlock}; modules such
 * as NoDelay may alter vanilla progress independently.</p>
 */
public final class BlockInteractionPipeline {

    public static final BlockInteractionPipeline INSTANCE = new BlockInteractionPipeline();

    private final Minecraft mc = Minecraft.getInstance();

    private Object miningOwner;
    private BlockPos miningPos;
    private Direction miningFace;
    private Object aimingOwner;
    private BlockPos aimingPos;
    private BlockAimResolver.Aim aimingPoint;

    private BlockInteractionPipeline() {
    }

    /**
     * Progress ONE block mining transaction. Target selection/locking belongs to the caller.
     *
     * A rotation request only expresses intent. It does not authorize a break by itself:
     * processing an AngleSmooth step is NOT equivalent to a server-aligned rotation.
     * The caller must submit the same target each tick until completion/cancellation.
     */
    public boolean mine(Object owner, BlockPos pos, Direction face, MiningPlan plan) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (owner == null || player == null || level == null || mc.gameMode == null || pos == null || plan == null) {
            stopMining(owner);
            return false;
        }
        if (!isMineable(level, pos, level.getBlockState(pos))) {
            if (Objects.equals(miningOwner, owner) && pos.equals(miningPos)) stopMining(owner);
            return false;
        }

        // Never start rotating to a target without a reachable, visible voxel-shape surface.
        // Keep the same aim point through every smooth step; re-solve only if occluded/out of reach.
        BlockAimResolver.Aim previous = Objects.equals(aimingOwner, owner) && pos.equals(aimingPos)
                ? aimingPoint : null;
        BlockAimResolver.Aim aim = BlockAimResolver.resolve(player, level, pos, face, plan.range(), previous);
        if (aim == null) {
            if (Objects.equals(aimingOwner, owner) || Objects.equals(miningOwner, owner)) stopMining(owner);
            return false;
        }
        if (!Objects.equals(aimingOwner, owner) || !pos.equals(aimingPos)) {
            if (miningOwner != null && !Objects.equals(miningOwner, owner)) return false;
            aimingOwner = owner;
            aimingPos = pos.immutable();
        }
        aimingPoint = aim;

        if (!plan.rotation().enabled()) {
            RotationManager.INSTANCE.release(owner, false);
            if (plan.strictNoRotation() && !canInteract(player, pos,
                    RotationManager.INSTANCE.getServerRotation(), plan.range(), 0.0)) return false;
            return executeMiningTick(owner, player, level, pos, aim.face(), plan);
        }

        Vec3 aimPoint = aim.point();
        Rotation desired = Rotation.lookingAt(aimPoint, player.getEyePosition());
        RotationPlan rotation = plan.rotation();
        RotationTarget target = new RotationTarget(
                desired.normalize(), null, aimPoint,
                rotation.processors(), rotation.resetTicks(), rotation.resetThreshold(),
                rotation.considerInventory(), rotation.movementCorrection(), null
        );
        RotationManager manager = RotationManager.INSTANCE;
        manager.setRotationTarget(target, rotation.priority(), owner);

        // No deferred mining callback: callbacks captured previous positions and could run
        // after a new target was selected. Check CURRENT ownership + packet rotation now.
        if (!manager.ownsActiveRotation(owner)) return false;
        Rotation serverRotation = manager.getServerRotation();
        if (serverRotation == null || serverRotation.angleTo(desired) > 2.5f) return false;
        if (!canInteract(player, pos, serverRotation, plan.range(), plan.wallRange())) return false;

        BlockHitResult hit = trace(player, serverRotation, plan.range());
        Direction hitFace = hit != null && hit.getType() == HitResult.Type.BLOCK
                && pos.equals(hit.getBlockPos()) ? hit.getDirection() : aim.face();
        return executeMiningTick(owner, player, level, pos, hitFace, plan);
    }

    private boolean executeMiningTick(Object owner,
                                      LocalPlayer player,
                                      ClientLevel level,
                                      BlockPos pos,
                                      Direction face,
                                      MiningPlan plan) {
        if (mc.gameMode == null) return false;

        if (miningOwner != null && !Objects.equals(miningOwner, owner)) return false;
        if (miningOwner != null && !pos.equals(miningPos)) stopMiningInternal(owner, false);

        if (plan.autoTool()) {
            BlockState state = level.getBlockState(pos);
            int slot = BreakSpeedUtil.bestHotbarSlot(player, level, pos, state, plan.requireCorrectTool());
            if (plan.requireCorrectTool() && state.requiresCorrectToolForDrops() && slot < 0) return false;
            // Tool types with vanilla mineable tags should not be broken by an ineffective hand.
            if (plan.requireSuitableTool() && hasToolTag(state)
                    && (slot < 0 || player.getInventory().getItem(slot).getDestroySpeed(state) <= 1.0f)) return false;
            // Never progress the destroy state with a tool that could not be selected.
            if (slot >= 0 && !InventorySwap.INSTANCE.leaseHotbar(owner, slot, plan.toolLeaseTicks())) {
                return false;
            }
        } else {
            InventorySwap.INSTANCE.releaseHotbar(owner);
            if (plan.requireSuitableTool() && hasToolTag(level.getBlockState(pos))
                    && player.getMainHandItem().getDestroySpeed(level.getBlockState(pos)) <= 1.0f) return false;
        }

        boolean changedTarget = !Objects.equals(miningOwner, owner) || !pos.equals(miningPos);
        if (changedTarget) {
            boolean started = mc.gameMode.startDestroyBlock(pos, face);
            if (started) {
                miningOwner = owner;
                miningPos = pos.immutable();
                miningFace = face;
                if (plan.swing()) player.swing(InteractionHand.MAIN_HAND);
            }
            return started;
        }

        boolean continued = mc.gameMode.continueDestroyBlock(pos, miningFace != null ? miningFace : face);
        if (continued && plan.swing()) player.swing(InteractionHand.MAIN_HAND);
        return continued;
    }

    private static boolean hasToolTag(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
                || state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE);
    }

    public void stopMining(Object owner) {
        if (owner == null) return;
        if (Objects.equals(miningOwner, owner)) {
            stopMiningInternal(owner, true);
        } else {
            InventorySwap.INSTANCE.releaseHotbar(owner);
            RotationManager.INSTANCE.release(owner);
        }
        if (Objects.equals(aimingOwner, owner)) {
            aimingOwner = null;
            aimingPos = null;
            aimingPoint = null;
        }
    }

    /** Preview feasibility before a planner commits to a block. */
    public BlockAimResolver.Aim resolveMiningAim(LocalPlayer player, ClientLevel level,
                                                 BlockPos pos, Direction face, double range) {
        return BlockAimResolver.resolve(player, level, pos, face, range, null);
    }

    /** Fast validity check for an already locked mining transaction. */
    public BlockAimResolver.Aim resolveMiningAim(Object owner, LocalPlayer player, ClientLevel level,
                                                 BlockPos pos, Direction face, double range) {
        BlockAimResolver.Aim previous = Objects.equals(aimingOwner, owner) && pos.equals(aimingPos)
                ? aimingPoint : null;
        return BlockAimResolver.resolve(player, level, pos, face, range, previous);
    }

    private void stopMiningInternal(Object owner, boolean releaseRotation) {
        if (mc.gameMode != null && miningPos != null) {
            mc.gameMode.stopDestroyBlock();
        }
        miningOwner = null;
        miningPos = null;
        miningFace = null;
        InventorySwap.INSTANCE.releaseHotbar(owner);
        if (releaseRotation) RotationManager.INSTANCE.release(owner);
    }

    public boolean isMining(Object owner, BlockPos pos) {
        return owner != null && pos != null && Objects.equals(miningOwner, owner) && pos.equals(miningPos);
    }

    public BlockPos miningPos(Object owner) {
        return Objects.equals(miningOwner, owner) ? miningPos : null;
    }

    /**
     * Final common path for block use/place actions. Rotation/target planning may happen before this
     * call, but slot leasing and the actual interaction are centralized here.
     */
    public InteractionResult useOnBlock(Object owner,
                                        LocalPlayer player,
                                        InteractionHand hand,
                                        int hotbarSlot,
                                        int slotResetTicks,
                                        BlockHitResult hitResult,
                                        boolean swing) {
        if (owner == null || player == null || hand == null || hitResult == null || mc.gameMode == null) {
            return InteractionResult.FAIL;
        }

        if (hand == InteractionHand.MAIN_HAND && hotbarSlot >= 0) {
            if (!InventorySwap.INSTANCE.leaseHotbar(owner, hotbarSlot, Math.max(0, slotResetTicks))) {
                return InteractionResult.FAIL;
            }
        }

        InteractionResult result = mc.gameMode.useItemOn(player, hand, hitResult);
        if (result != null && result.consumesAction() && swing) {
            player.swing(hand);
        }
        return result;
    }

    /** Schedules an arbitrary block action behind the same world-point rotation semantics. */
    public void rotateTo(Object owner, Vec3 targetPoint, RotationPlan plan, Runnable action) {
        LocalPlayer player = mc.player;
        if (player == null || targetPoint == null) return;
        rotateTo(owner, Rotation.lookingAt(targetPoint, player.getEyePosition()).normalize(), targetPoint, plan, action);
    }

    /**
     * Rotation entry point for callers which already solved an exact block-face rotation.
     * {@code targetPoint} is optional but should be supplied for block targets so smoothers have
     * stable identity and distance information without a fake Entity.
     */
    public void rotateTo(Object owner, Rotation desired, Vec3 targetPoint, RotationPlan plan, Runnable action) {
        if (owner == null || desired == null || plan == null) return;
        if (!plan.enabled()) {
            if (action != null) action.run();
            return;
        }

        RotationTarget target = new RotationTarget(
                desired.normalize(),
                null,
                targetPoint,
                plan.processors(),
                plan.resetTicks(),
                plan.resetThreshold(),
                plan.considerInventory(),
                plan.movementCorrection(),
                action != null ? new RestrictedSingleUseAction(action) : null
        );
        RotationManager.INSTANCE.setRotationTarget(target, plan.priority(), owner);
    }

    public static boolean canInteract(LocalPlayer player,
                                      BlockPos pos,
                                      Rotation rotation,
                                      double range,
                                      double wallRange) {
        if (player == null || player.level() == null || pos == null) return false;
        double distanceSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
        double safeRange = Math.max(0.0, range);

        if (rotation != null) {
            BlockHitResult hit = trace(player, rotation, safeRange);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && pos.equals(hit.getBlockPos())) {
                return true;
            }
        }

        double safeWallRange = Math.max(0.0, wallRange);
        return safeWallRange > 0.0 && distanceSq <= safeWallRange * safeWallRange;
    }

    public static BlockHitResult trace(LocalPlayer player, Rotation rotation, double range) {
        if (player == null || player.level() == null || rotation == null) return null;
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(Math.max(0.0, range)));
        HitResult hit = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        return hit instanceof BlockHitResult blockHit ? blockHit : null;
    }

    public static Direction faceFor(LocalPlayer player, BlockPos pos) {
        if (player == null || pos == null) return Direction.UP;
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) return dy >= 0.0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return dx >= 0.0 ? Direction.EAST : Direction.WEST;
        return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static Vec3 faceCenter(BlockPos pos, Direction face) {
        Direction resolved = face != null ? face : Direction.UP;
        return Vec3.atCenterOf(pos).add(
                resolved.getStepX() * 0.499,
                resolved.getStepY() * 0.499,
                resolved.getStepZ() * 0.499
        );
    }

    public static boolean isMineable(ClientLevel level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir() || state.canBeReplaced()) return false;
        return state.getDestroySpeed(level, pos) >= 0.0f;
    }

    public record RotationPlan(boolean enabled,
                               List<RotationProcessor> processors,
                               int resetTicks,
                               float resetThreshold,
                               boolean considerInventory,
                               MovementCorrection movementCorrection,
                               int priority) {
        public RotationPlan {
            processors = processors != null ? List.copyOf(processors) : List.of();
            resetTicks = Math.max(1, resetTicks);
            resetThreshold = Math.max(0.05f, resetThreshold);
            movementCorrection = movementCorrection != null ? movementCorrection : MovementCorrection.SILENT;
        }

        public static RotationPlan disabled() {
            return new RotationPlan(false, List.of(), 1, 1.0f, false, MovementCorrection.OFF, 0);
        }
    }

    public record MiningPlan(double range,
                             double wallRange,
                             boolean swing,
                             boolean autoTool,
                             int toolLeaseTicks,
                             RotationPlan rotation,
                             boolean strictNoRotation,
                             boolean requireCorrectTool,
                             boolean requireSuitableTool) {
        public MiningPlan {
            range = Math.max(0.0, range);
            wallRange = Math.max(0.0, wallRange);
            toolLeaseTicks = Math.max(1, toolLeaseTicks);
            rotation = rotation != null ? rotation : RotationPlan.disabled();
        }
    }
}
