/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.placer;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PostPlayerUpdateEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.block.scaffold.ScaffoldTargetFinder;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class BlockPlacer {

    private final Minecraft mc = Minecraft.getInstance();

    private final Module module;
    private final Object requester;
    private final SlotFinder slotFinder;
    private final DoubleSupplier rangeSupplier;
    private final DoubleSupplier wallRangeSupplier;
    private final IntSupplier cooldownMinSupplier;
    private final IntSupplier cooldownMaxSupplier;
    private final IntSupplier slotResetDelayMinSupplier;
    private final IntSupplier slotResetDelayMaxSupplier;
    private final IntSupplier sneakTicksSupplier;
    private final BooleanSupplier constructFailResultSupplier;
    private final BooleanSupplier ignoreOpenInventorySupplier;
    private final BooleanSupplier ignoreUsingItemSupplier;
    private final Supplier<RotationMode> rotationModeSupplier;
    private final Supplier<MovementCorrection> movementCorrectionSupplier;
    private final int rotationPriority;

    private final LinkedHashMap<BlockPos, Boolean> blocks = new LinkedHashMap<>();
    private final LinkedHashSet<BlockPos> inaccessible = new LinkedHashSet<>();

    private boolean registered;
    private int ticksToWait;
    private boolean ranAction;
    private int sneakTimes;
    private PlacementPlan currentPlacement;

    public BlockPlacer(
            Module module,
            Object requester,
            int rotationPriority,
            SlotFinder slotFinder,
            DoubleSupplier rangeSupplier,
            DoubleSupplier wallRangeSupplier,
            IntSupplier cooldownMinSupplier,
            IntSupplier cooldownMaxSupplier,
            IntSupplier slotResetDelayMinSupplier,
            IntSupplier slotResetDelayMaxSupplier,
            IntSupplier sneakTicksSupplier,
            BooleanSupplier constructFailResultSupplier,
            BooleanSupplier ignoreOpenInventorySupplier,
            BooleanSupplier ignoreUsingItemSupplier,
            Supplier<RotationMode> rotationModeSupplier,
            Supplier<MovementCorrection> movementCorrectionSupplier
    ) {
        this.module = module;
        this.requester = requester;
        this.rotationPriority = rotationPriority;
        this.slotFinder = slotFinder;
        this.rangeSupplier = rangeSupplier;
        this.wallRangeSupplier = wallRangeSupplier;
        this.cooldownMinSupplier = cooldownMinSupplier;
        this.cooldownMaxSupplier = cooldownMaxSupplier;
        this.slotResetDelayMinSupplier = slotResetDelayMinSupplier;
        this.slotResetDelayMaxSupplier = slotResetDelayMaxSupplier;
        this.sneakTicksSupplier = sneakTicksSupplier;
        this.constructFailResultSupplier = constructFailResultSupplier;
        this.ignoreOpenInventorySupplier = ignoreOpenInventorySupplier;
        this.ignoreUsingItemSupplier = ignoreUsingItemSupplier;
        this.rotationModeSupplier = rotationModeSupplier;
        this.movementCorrectionSupplier = movementCorrectionSupplier;
    }

    private static int randomBetween(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static boolean isInteractable(BlockState state) {
        if (state == null) {
            return false;
        }

        Block block = state.getBlock();
        return block instanceof BedBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof EnderChestBlock
                || block instanceof CraftingTableBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock
                || block instanceof EnchantingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof SmithingTableBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof BeaconBlock
                || block instanceof NoteBlock;
    }

    public void enable() {
        if (registered) {
            return;
        }
        Events.BUS.register(this);
        registered = true;
    }

    public void disable() {
        if (registered) {
            Events.BUS.unregister(this);
            registered = false;
        }
        reset();
        InventorySwap.INSTANCE.releaseHotbar(requester);
    }

    public void tick() {
        if (registered) {
            InventorySwap.INSTANCE.tick();
        }
    }

    public void update(Iterable<BlockPos> positions) {
        LinkedHashSet<BlockPos> next = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null) {
                next.add(pos.immutable());
            }
        }

        blocks.entrySet().removeIf(entry -> !next.contains(entry.getKey()));
        for (BlockPos pos : next) {
            blocks.putIfAbsent(pos, Boolean.FALSE);
        }

        if (currentPlacement != null && !next.contains(currentPlacement.pos())) {
            currentPlacement = null;
        }
    }

    public void clear() {
        blocks.clear();
        inaccessible.clear();
        currentPlacement = null;
    }

    public boolean isDone() {
        return blocks.isEmpty();
    }

    public List<BlockPos> getQueuedPositions() {
        return List.copyOf(blocks.keySet());
    }

    public BlockPos getCurrentPlacementPos() {
        return currentPlacement == null ? null : currentPlacement.pos();
    }

    @EventHandler(priority = -20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        if (!registered || !module.isEnabled()) {
            return;
        }

        if (ticksToWait > 0) {
            ticksToWait--;
        } else if (ranAction) {
            ranAction = false;
            ticksToWait = randomBetween(cooldownMinSupplier.getAsInt(), cooldownMaxSupplier.getAsInt());
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            currentPlacement = null;
            return;
        }

        boolean inventoryOpen = !ignoreOpenInventorySupplier.getAsBoolean() && ClientScreen.current() instanceof AbstractContainerScreen<?>;
        boolean usingItem = !ignoreUsingItemSupplier.getAsBoolean() && player.isUsingItem();
        if (inventoryOpen || usingItem || blocks.isEmpty()) {
            currentPlacement = null;
            return;
        }

        PlacementSlot slot = slotFinder.find(null);
        if (slot == null || slot.stack().isEmpty()) {
            currentPlacement = null;
            return;
        }

        inaccessible.clear();
        currentPlacement = scheduleCurrentPlacement(player, slot.stack());
        if (currentPlacement == null || rotationModeSupplier.get() == RotationMode.NO_ROTATION) {
            return;
        }

        RotationTarget rotationTarget = new RotationTarget(
                currentPlacement.target().getRotation(),
                player,
                List.of(),
                1,
                4.0f,
                false,
                movementCorrectionSupplier.get(),
                new RestrictedSingleUseAction(() -> {
                    PlacementPlan plan = currentPlacement;
                    LocalPlayer currentPlayer = mc.player;
                    if (plan == null || currentPlayer == null || ticksToWait > 0) {
                        return;
                    }

                    Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
                    if (currentRotation == null) {
                        return;
                    }

                    BlockHitResult currentHit = traceTarget(
                            currentPlayer,
                            currentRotation,
                            Math.max(rangeSupplier.getAsDouble(), wallRangeSupplier.getAsDouble())
                    );
                    if (currentHit == null || !plan.target().getInteractedBlockPos().equals(currentHit.getBlockPos())) {
                        return;
                    }

                    doPlacement(currentPlayer, plan);
                })
        );
        RotationManager.INSTANCE.setRotationTarget(rotationTarget, rotationPriority, requester);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!registered || !module.isEnabled()) {
            return;
        }

        if (sneakTimes > 0) {
            sneakTimes--;
            event.setSneak(true);
        }
    }

    @EventHandler
    private void onPostPlayerUpdate(PostPlayerUpdateEvent event) {
        if (!registered || !module.isEnabled()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || currentPlacement == null || ticksToWait > 0) {
            return;
        }

        if (rotationModeSupplier.get() != RotationMode.NORMAL) {
            doPlacement(player, currentPlacement);
        }
    }

    private PlacementPlan scheduleCurrentPlacement(LocalPlayer player, ItemStack stack) {
        for (Map.Entry<BlockPos, Boolean> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (inaccessible.contains(pos) || isBlocked(player, pos)) {
                continue;
            }

            ScaffoldPlacementTarget target = ScaffoldTargetFinder.findTarget(
                    player,
                    pos,
                    stack,
                    List.of(BlockPos.ZERO),
                    null,
                    wallRangeSupplier.getAsDouble() > 0.0
            );
            if (target == null) {
                continue;
            }

            if (!canReach(player, target.getInteractedBlockPos(), target.getRotation())) {
                inaccessible.add(pos);
                continue;
            }

            if (isInteractable(player.level().getBlockState(target.getInteractedBlockPos()))) {
                sneakTimes = Math.max(0, sneakTicksSupplier.getAsInt() - 1);
            }

            return new PlacementPlan(pos, target);
        }

        return null;
    }

    private boolean isBlocked(LocalPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        if (!state.canBeReplaced()) {
            inaccessible.add(pos);
            return true;
        }

        List<Entity> entities = player.level().getEntities(
                player,
                new AABB(pos),
                entity -> entity != null && !entity.isRemoved() && !entity.isSpectator()
        );
        if (!entities.isEmpty()) {
            inaccessible.add(pos);
            return true;
        }

        return false;
    }

    private void doPlacement(LocalPlayer player, PlacementPlan plan) {
        currentPlacement = null;
        blocks.remove(plan.pos());

        PlacementSlot slot = slotFinder.find(plan.pos());
        if (slot == null) {
            return;
        }

        // LB verifies normal placements against the rotation that actually got sent to the server,
        // not the raw planned target rotation.
        Rotation verificationRotation = RotationManager.INSTANCE.getServerRotation();

        if (!canReach(player, plan.target().getInteractedBlockPos(), verificationRotation)) {
            return;
        }

        BlockHitResult hitResult = raytraceTarget(
                player,
                plan.target().getInteractedBlockPos(),
                verificationRotation,
                plan.target().getDirection()
        );
        if (hitResult == null) {
            return;
        }

        if (slot.hotbarSlot() >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(
                    requester,
                    slot.hotbarSlot(),
                    randomBetween(slotResetDelayMinSupplier.getAsInt(), slotResetDelayMaxSupplier.getAsInt())
            );
        }

        if (slot.stack().getItem() instanceof BlockItem && !player.level().getBlockState(plan.pos()).canBeReplaced()) {
            return;
        }

        InteractionResult result = mc.gameMode.useItemOn(player, slot.hand(), hitResult);
        if (result != null && result.consumesAction()) {
            player.swing(slot.hand());
            ranAction = true;
        }
    }

    private BlockHitResult raytraceTarget(LocalPlayer player, BlockPos interactedPos, Rotation rotation, net.minecraft.core.Direction direction) {
        BlockHitResult hitResult = traceTarget(player, rotation, Math.max(rangeSupplier.getAsDouble(), wallRangeSupplier.getAsDouble()));
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK && interactedPos.equals(hitResult.getBlockPos())) {
            return new BlockHitResult(hitResult.getLocation(), direction, interactedPos, false);
        }

        if (constructFailResultSupplier.getAsBoolean()) {
            return new BlockHitResult(Vec3.atCenterOf(interactedPos), direction, interactedPos, false);
        }

        return null;
    }

    private boolean canReach(LocalPlayer player, BlockPos interactedPos, Rotation rotation) {
        double wallRange = wallRangeSupplier.getAsDouble();
        if (Vec3.atCenterOf(interactedPos).distanceToSqr(player.getEyePosition()) <= wallRange * wallRange) {
            return true;
        }

        BlockHitResult hitResult = traceTarget(player, rotation, rangeSupplier.getAsDouble());
        return hitResult != null && interactedPos.equals(hitResult.getBlockPos());
    }

    private BlockHitResult traceTarget(LocalPlayer player, Rotation rotation, double range) {
        if (player == null || rotation == null || player.level() == null) {
            return null;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        return hitResult instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private void reset() {
        ticksToWait = 0;
        ranAction = false;
        sneakTimes = 0;
        clear();
    }

    public enum RotationMode {
        NORMAL,
        NO_ROTATION
    }

    @FunctionalInterface
    public interface SlotFinder {
        PlacementSlot find(BlockPos targetPos);
    }

    public record PlacementSlot(int hotbarSlot, InteractionHand hand, ItemStack stack) {
    }

    private record PlacementPlan(BlockPos pos, ScaffoldPlacementTarget target) {
    }
}
