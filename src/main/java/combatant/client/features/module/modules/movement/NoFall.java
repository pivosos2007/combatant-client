/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.scaffold.ScaffoldFacePositionFactory;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.block.scaffold.ScaffoldTargetFinder;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.simulation.PlayerSimulationCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

//todo Description
@ModuleInfo(
        id = "nofall",
        displayName = "NoFall",
        category = ModuleCategory.MOVEMENT
)
public class NoFall extends Module {

    private static final int MLG_COLLISION_PREDICTION_TICKS = 20;
    private static final int MLG_SCAFFOLDING_SNEAK_TICKS = 3;
    private static final int MLG_HOTBAR_RESET_TICKS = 1;
    private static final long MLG_PICKUP_WATER_MIN_DELAY_MS = 200L;
    private static final long MLG_PICKUP_WATER_MAX_DELAY_MS = 1000L;
    private static final Set<Item> NETHER_MLG_ITEMS = Set.of(
            Items.SCAFFOLDING,
            Items.COBWEB,
            Items.POWDER_SNOW_BUCKET,
            Items.HAY_BLOCK,
            Items.SLIME_BLOCK,
            Items.HONEY_BLOCK,
            Items.TWISTING_VINES
    );
    private static final Set<Item> NORMAL_MLG_ITEMS;

    static {
        java.util.LinkedHashSet<Item> items = new java.util.LinkedHashSet<>(NETHER_MLG_ITEMS);
        items.add(Items.WATER_BUCKET);
        NORMAL_MLG_ITEMS = Set.copyOf(items);
    }

    private final EnumValue<Mode> mode =
            enumSetting("mode", "mode", Mode.GROUD_SPOOF, Mode.values());
    private final NumberValue<Float> packetJumpFallDistance =
            visibleWhen(num("packet_jump_fall_distance", 3.0f, 0.0f, 20.0f), () -> mode.get() == Mode.PACKET_JUMP);
    private final EnumValue<PacketJumpTiming> packetJumpTiming =
            visibleWhen(enumSetting("packet_jump_timing", "packet_jump_timing", PacketJumpTiming.LANDING, PacketJumpTiming.values()),
                    () -> mode.get() == Mode.PACKET_JUMP);
    private final BooleanValue packetJumpResetFallDistance =
            visibleWhen(bool("packet_jump_reset_fall_distance", true),
                    () -> mode.get() == Mode.PACKET_JUMP && packetJumpTiming.get() == PacketJumpTiming.FALLING);
    private final NumberValue<Float> vulcanFallDistance =
            visibleWhen(num("vulcan_fall_distance", 7.0f, 3.0f, 40.0f), () -> mode.get() == Mode.VULCAN);
    private final NumberValue<Float> mlgMinFallDistance =
            visibleWhen(num("mlg_min_fall_distance", 4.0f, 3.0f, 50.0f), () -> mode.get() == Mode.MLG);
    private final BooleanValue mlgNoRotations =
            visibleWhen(bool("mlg_no_rotations", false), () -> mode.get() == Mode.MLG);
    private final List<PlacedWater> lastWaterPlacements = new ArrayList<>();
    private boolean packetJumpFalling;
    private boolean packetJumpInternalSend;
    private int lastPacketJumpTick = Integer.MIN_VALUE;
    private double lastMoveX = Double.NaN;
    private double lastMoveY = Double.NaN;
    private double lastMoveZ = Double.NaN;
    private MlgPlan currentMlgPlan;
    private int mlgSneakTicks;

    {
        setDefaultBind("GRAVE_ACCENT");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) return;

        LocalPlayer player = client.player;
        if (mode.get() == Mode.MLG) {
            tickMlg(client, player);
            return;
        }

        if (player.onGround()) {
            packetJumpFalling = false;
        }
        if (player.isShiftKeyDown()) return;

        switch (mode.get()) {
            case GROUD_SPOOF -> {
                if (isDangerousFall(player)) {
                    client.getConnection().send(
                            new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision)
                    );
                }
            }
            case PACKET_JUMP -> {
                packetJumpFalling = !player.onGround() && player.fallDistance > packetJumpFallDistance.get();
                if (packetJumpTiming.get() == PacketJumpTiming.FALLING
                        && packetJumpFalling
                        && player.tickCount != lastPacketJumpTick) {
                    sendPacketJumpSpoof(client, player, player.getX(), player.getY() + 1.0E-9, player.getZ());
                    lastPacketJumpTick = player.tickCount;
                    if (packetJumpResetFallDistance.get()) {
                        player.fallDistance = 0.0f;
                    }
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        if (mode.get() != Mode.MLG) {
            currentMlgPlan = null;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null) {
            currentMlgPlan = null;
            return;
        }

        currentMlgPlan = getCurrentGoal(client.player);
        if (currentMlgPlan == null || mlgNoRotations.get()) {
            return;
        }

        RotationTarget target = new RotationTarget(
                currentMlgPlan.rotation(),
                client.player,
                List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, 100, this);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (mode.get() != Mode.MLG || mlgSneakTicks <= 0) {
            return;
        }

        mlgSneakTicks--;
        event.setSneak(true);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (packetJumpInternalSend) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        LocalPlayer player = client.player;
        if (player.isShiftKeyDown()) {
            rememberMovePacket(packet, player);
            return;
        }

        switch (mode.get()) {
            case PACKET_JUMP -> {
                packetJumpFalling = !player.onGround() && player.fallDistance > packetJumpFallDistance.get();
                if (packetJumpTiming.get() == PacketJumpTiming.LANDING
                        && packet.isOnGround()
                        && packetJumpFalling) {
                    packetJumpFalling = false;
                    sendPacketJumpSpoof(
                            client,
                            player,
                            resolveTracked(lastMoveX, player.getX()),
                            resolveTracked(lastMoveY, player.getY()) + 1.0E-9,
                            resolveTracked(lastMoveZ, player.getZ())
                    );
                }
            }
            case VULCAN -> {
                if (player.fallDistance > vulcanFallDistance.get()) {
                    ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(true);
                    player.fallDistance = 0.0f;
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                }
            }
            case NO_GROUND -> ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(false);
            default -> {
            }
        }

        rememberMovePacket(packet, player);
    }

    private boolean isDangerousFall(LocalPlayer player) {
        return player != null
                && player.getDeltaMovement().y < -0.5
                && player.fallDistance > 2.0f;
    }

    private void sendPacketJumpSpoof(Minecraft client, LocalPlayer player, double x, double y, double z) {
        if (client.getConnection() == null || player == null) return;

        packetJumpInternalSend = true;
        try {
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    x,
                    y,
                    z,
                    false,
                    player.horizontalCollision
            ));
        } finally {
            packetJumpInternalSend = false;
        }
    }

    private void rememberMovePacket(ServerboundMovePlayerPacket packet, LocalPlayer player) {
        lastMoveX = packet.getX(player.getX());
        lastMoveY = packet.getY(player.getY());
        lastMoveZ = packet.getZ(player.getZ());
    }

    private double resolveTracked(double value, double fallback) {
        return Double.isNaN(value) ? fallback : value;
    }

    private void resetState() {
        packetJumpFalling = false;
        packetJumpInternalSend = false;
        lastPacketJumpTick = Integer.MIN_VALUE;
        lastMoveX = Double.NaN;
        lastMoveY = Double.NaN;
        lastMoveZ = Double.NaN;
        currentMlgPlan = null;
        mlgSneakTicks = 0;
        lastWaterPlacements.clear();
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void tickMlg(Minecraft client, LocalPlayer player) {
        if (player == null || client.gameMode == null) {
            currentMlgPlan = null;
            return;
        }

        MlgPlan plan = currentMlgPlan;
        if (plan == null) {
            return;
        }

        if (plan.type() == MlgPlanType.PLACE
                && (player.onGround()
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.getAbilities().flying)) {
            currentMlgPlan = null;
            return;
        }

        BlockHitResult hitResult;
        Rotation useRotation;
        if (mlgNoRotations.get()) {
            if (plan.type() == MlgPlanType.PICKUP_WATER) {
                Rotation playerRotation = new Rotation(player.getYRot(), player.getXRot(), true).normalize();
                hitResult = traceTarget(player, playerRotation, ClipContext.Fluid.SOURCE_ONLY);
                if (!plan.matches(hitResult)) {
                    return;
                }
                useRotation = playerRotation;
            } else {
                hitResult = plan.target().toHitResult();
                useRotation = plan.rotation();
            }
        } else {
            Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
            if (currentRotation == null) {
                return;
            }

            hitResult = traceTarget(
                    player,
                    currentRotation,
                    plan.type() == MlgPlanType.PICKUP_WATER ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
            );
            if (!plan.matches(hitResult)) {
                return;
            }
            useRotation = currentRotation;
        }

        executeMlgPlacement(player, plan, hitResult, useRotation);
        currentMlgPlan = null;
    }

    private MlgPlan getCurrentGoal(LocalPlayer player) {
        MlgPlan placement = getCurrentMlgPlacementPlan(player);
        if (placement != null) {
            return placement;
        }
        return getCurrentPickupPlan(player);
    }

    private MlgPlan getCurrentMlgPlacementPlan(LocalPlayer player) {
        if (player == null || player.fallDistance <= mlgMinFallDistance.get()) {
            return null;
        }

        MlgSource source = findClosestMlgSource(player);
        if (source == null) {
            return null;
        }

        BlockPos collision = findMlgCollision(player);
        if (collision == null || isFallDamageBlocking(collision)) {
            return null;
        }

        BlockPos targetPos = collision.above();
        ScaffoldPlacementTarget target = ScaffoldTargetFinder.findTarget(
                player,
                targetPos,
                source.stack(),
                List.of(BlockPos.ZERO),
                null,
                false,
                new ScaffoldFacePositionFactory(
                        player,
                        player.getEyePosition(),
                        ScaffoldFacePositionFactory.Mode.CENTER,
                        null,
                        0.0
                ),
                player.getEyePosition()
        );
        if (target == null) {
            return null;
        }
        if (!isWithinMlgBlockRange(player, target.getHitVec())) {
            return null;
        }

        return new MlgPlan(MlgPlanType.PLACE, targetPos, target, target.getRotation(), source.hand(), source.hotbarSlot(), source.item());
    }

    private MlgPlan getCurrentPickupPlan(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        MlgSource source = findClosestSpecificItemSource(player, Items.BUCKET);
        if (source == null) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        lastWaterPlacements.removeIf(placement ->
                now - placement.timeMs() > MLG_PICKUP_WATER_MAX_DELAY_MS
                        || !client.level.getBlockState(placement.pos()).is(Blocks.WATER));

        for (PlacedWater placement : lastWaterPlacements) {
            if (now - placement.timeMs() < MLG_PICKUP_WATER_MIN_DELAY_MS) {
                continue;
            }

            Vec3 hitVec = Vec3.atCenterOf(placement.pos()).add(0.0, 0.45, 0.0);
            Rotation rotation = Rotation.lookingAt(hitVec, player.getEyePosition()).normalize();
            if (!isWithinMlgBlockRange(player, hitVec)) {
                continue;
            }

            return new MlgPlan(MlgPlanType.PICKUP_WATER, placement.pos(), null, rotation, source.hand(), source.hotbarSlot(), source.item());
        }

        return null;
    }

    private MlgSource findClosestMlgSource(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        Set<Item> allowedItems = player.level().dimension() == Level.NETHER
                ? NETHER_MLG_ITEMS
                : NORMAL_MLG_ITEMS;
        ItemStack offhand = player.getOffhandItem();
        if (isAllowedMlgItem(offhand, allowedItems)) {
            return new MlgSource(InteractionHand.OFF_HAND, -1, offhand, offhand.getItem());
        }

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        int bestSlot = -1;
        int bestDistance = Integer.MAX_VALUE;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isAllowedMlgItem(stack, allowedItems)) {
                continue;
            }

            int distance = hotbarDistance(selectedSlot, slot);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
                bestStack = stack;
            }
        }

        if (bestSlot == -1) {
            return null;
        }

        return new MlgSource(InteractionHand.MAIN_HAND, bestSlot, bestStack, bestStack.getItem());
    }

    private MlgSource findClosestSpecificItemSource(LocalPlayer player, Item item) {
        if (player == null || item == null) {
            return null;
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(item)) {
            return new MlgSource(InteractionHand.OFF_HAND, -1, offhand, item);
        }

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        int bestSlot = -1;
        int bestDistance = Integer.MAX_VALUE;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }

            int distance = hotbarDistance(selectedSlot, slot);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
                bestStack = stack;
            }
        }

        if (bestSlot == -1) {
            return null;
        }

        return new MlgSource(InteractionHand.MAIN_HAND, bestSlot, bestStack, item);
    }

    private boolean isAllowedMlgItem(ItemStack stack, Set<Item> allowedItems) {
        return stack != null && !stack.isEmpty() && allowedItems.contains(stack.getItem());
    }

    private int hotbarDistance(int from, int to) {
        int diff = Math.abs(from - to);
        return Math.min(diff, 9 - diff);
    }

    private BlockPos findMlgCollision(LocalPlayer player) {
        PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForLocalPlayer();
        if (simulation == null) {
            return null;
        }

        for (int tick = 1; tick <= MLG_COLLISION_PREDICTION_TICKS; tick++) {
            PlayerSimulationCache.SimulatedPlayerSnapshot snapshot = simulation.getSnapshotAt(tick);
            if (!snapshot.onGround()) {
                continue;
            }

            Vec3 pos = snapshot.pos();
            return BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
        }

        return null;
    }

    private boolean isFallDamageBlocking(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return false;
        }

        var state = client.level.getBlockState(pos);
        return state.is(Blocks.WATER)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.SLIME_BLOCK);
    }

    private BlockHitResult traceTarget(LocalPlayer player, Rotation rotation, ClipContext.Fluid fluidHandling) {
        if (player == null || rotation == null || player.level() == null) {
            return null;
        }

        double range = getMlgBlockRange(player);
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                fluidHandling,
                player
        ));
        return hitResult instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private void executeMlgPlacement(LocalPlayer player, MlgPlan plan, BlockHitResult hitResult, Rotation useRotation) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameMode == null || player == null || plan == null || hitResult == null || useRotation == null) {
            return;
        }
        if (!canReachMlgTarget(player, plan, hitResult)) {
            return;
        }

        if (plan.hotbarSlot() >= 0) {
            InventorySwap.INSTANCE.leaseHotbar(this, plan.hotbarSlot(), MLG_HOTBAR_RESET_TICKS);
        }

        boolean success;
        if (plan.type() == MlgPlanType.PICKUP_WATER) {
            InteractionResult useResult = interactItemWithRotation(player, plan.hand(), useRotation);
            success = useResult != null && useResult.consumesAction();
        } else {
            InteractionResult result = client.gameMode.useItemOn(player, plan.hand(), hitResult);
            success = result != null && result.consumesAction();

            if (!success && result == InteractionResult.PASS) {
                InteractionResult useResult = interactItemWithRotation(player, plan.hand(), useRotation);
                success = useResult != null && useResult.consumesAction();
            }
        }

        if (success) {
            player.swing(plan.hand());
            if (plan.item() == Items.SCAFFOLDING) {
                mlgSneakTicks = MLG_SCAFFOLDING_SNEAK_TICKS;
            }
            if (plan.type() == MlgPlanType.PLACE && plan.item() == Items.WATER_BUCKET) {
                lastWaterPlacements.add(new PlacedWater(plan.targetPos(), System.currentTimeMillis()));
            } else if (plan.type() == MlgPlanType.PICKUP_WATER && plan.item() == Items.BUCKET) {
                lastWaterPlacements.removeIf(placement -> placement.pos().equals(plan.targetPos()));
            }
        }
    }

    private boolean canReachMlgTarget(LocalPlayer player, MlgPlan plan, BlockHitResult hitResult) {
        if (player == null || plan == null || hitResult == null) {
            return false;
        }
        if (!plan.matches(hitResult)) {
            return false;
        }

        return isWithinMlgBlockRange(player, hitResult.getLocation());
    }

    private boolean isWithinMlgBlockRange(LocalPlayer player, Vec3 point) {
        if (player == null || point == null) {
            return false;
        }
        double range = getMlgBlockRange(player);
        return player.getEyePosition().distanceToSqr(point) <= range * range;
    }

    private double getMlgBlockRange(LocalPlayer player) {
        return player != null ? player.blockInteractionRange() : 0.0;
    }

    private InteractionResult interactItemWithRotation(LocalPlayer player, InteractionHand hand, Rotation rotation) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameMode == null || player == null || rotation == null) {
            return InteractionResult.FAIL;
        }
        Rotation normalized = rotation.normalize();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        final InteractionResult[] resultHolder = {InteractionResult.PASS};
        InteractionUtil.sendSequencedPacket(id ->
                new ServerboundUseItemPacket(hand, id, normalized.yaw(), normalized.pitch()));

        InteractionResult result = stack.use(client.level, player, hand);
        if (result instanceof InteractionResult.Success success) {
            ItemStack newStack = Objects.requireNonNullElseGet(success.heldItemTransformedTo(), () -> player.getItemInHand(hand));
            if (newStack != stack) {
                player.setItemInHand(hand, newStack);
            }
        }
        resultHolder[0] = result;
        return resultHolder[0];
    }

    public enum Mode {
        GROUD_SPOOF,
        PACKET_JUMP,
        VULCAN,
        NO_GROUND,
        MLG
    }

    public enum PacketJumpTiming {
        LANDING,
        FALLING
    }

    private enum MlgPlanType {
        PLACE,
        PICKUP_WATER
    }

    private record MlgSource(InteractionHand hand, int hotbarSlot, ItemStack stack, Item item) {
    }

    private record MlgPlan(
            MlgPlanType type,
            BlockPos targetPos,
            ScaffoldPlacementTarget target,
            Rotation rotation,
            InteractionHand hand,
            int hotbarSlot,
            Item item
    ) {
        private boolean matches(BlockHitResult hitResult) {
            if (hitResult == null) {
                return false;
            }

            return switch (type) {
                case PLACE -> target != null && target.matches(hitResult);
                case PICKUP_WATER -> targetPos.equals(hitResult.getBlockPos());
            };
        }
    }

    private record PlacedWater(BlockPos pos, long timeMs) {
    }
}
