/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.TPSSync;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.MovementUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.simulation.PlayerSimulationCache;

//todo Description
@ModuleInfo(
        id = "windjump",
        displayName = "WindJump",
        category = ModuleCategory.MOVEMENT
)
public class WindJump extends Module {

    private static final String SETTING_ITEM_USAGE_MODE = "item_usage_mode";
    private static final String ACTION_WINDJUMP = "windjump";
    private static final String ACTION_WINDTHROW = "windthrow";

    private static final float MIN_DOWN_PITCH = 60f;
    private static final double MAX_BLOCK_DISTANCE = 1.5;
    private static final double MIN_BLOCK_DISTANCE = 0.3;
    private static final double FALL_TRIGGER_DISTANCE = 1.0;
    private static final double FALL_TRIGGER_VERTICAL_SPEED = -0.05; // must be falling
    private static final double WINDTHROW_TRIGGER_VERTICAL_SPEED = -0.12;
    private static final double MAX_FALL_RAYCAST = 6.0; // extend ray when falling fast
    private static final double WIND_CHARGE_SPEED = 1.5;
    private static final double IMPACT_POINT_Y_OFFSET = 0.25;
    private static final double MAX_THROW_PREDICTION_TICKS = 3.0;
    private static final int USE_EXPIRE_TICKS = 2;
    private static final int WIND_CHARGE_COOLDOWN_TICKS = 10;
    private static final int HOTBAR_RESET_TICKS = 0;
    private static final double MOTION_YAW_EPSILON = 0.08;
    private static final int PREDICTION_TICKS = 7;
    private static final double HORIZONTAL_LEAD_FACTOR = 0.32;
    private static final double VERTICAL_LEAD_FACTOR = 0.85;
    private static final double JUMP_VERTICAL_BONUS = 0.16;
    private static final double MAX_HORIZONTAL_LEAD = 0.65;
    private static final double IMPACT_BACKTRACK_FACTOR = 0.55;
    private static final double MAX_IMPACT_BACKTRACK = 0.55;
    private static final double JUMP_START_HORIZONTAL_LEAD_FACTOR = 0.08;
    private static final double JUMP_START_VERTICAL_LEAD_FACTOR = 0.18;
    private static final double JUMP_START_MAX_HORIZONTAL_LEAD = 0.12;
    private static final double JUMP_START_IMPACT_BACKTRACK_FACTOR = 0.85;
    private static final double JUMP_START_MAX_IMPACT_BACKTRACK = 0.80;
    private static final double JUMP_THROW_INPUT_DELAY_TICKS = 0.08;
    private static final double JUMP_START_MIN_LEAD_TICKS = 0.08;
    private static final double JUMP_START_MAX_LEAD_TICKS = 0.28;

    private static final float THROW_PITCH = 90.0f; // straight down
    private final EnumValue<WindThrowMode> windThrowMode =
            enumSetting("windThrowMode", SETTING_ITEM_USAGE_MODE, WindThrowMode.UNLOCKED, WindThrowMode.UNLOCKED, WindThrowMode.LEGIT);
    private PendingUse pendingUse = null;
    private int nextAllowedUseTick = 0;
    private int lastJumpAge = Integer.MIN_VALUE;

    {
        addAction(ACTION_WINDJUMP, "LEFT_ALT");
        addAction(ACTION_WINDTHROW, "LEFT_ALT+X");
    }

    @Override
    public void onDisable() {
        pendingUse = null;
        nextAllowedUseTick = 0;
        lastJumpAge = Integer.MIN_VALUE;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        LocalPlayer player = mc.player;
        boolean windthrowHeld = isActionHeld(ACTION_WINDTHROW);

        if (processHeldWindJump(player)) {
            return;
        }

        // WindThrow on press
        if (isActionPressedOnce(ACTION_WINDTHROW)) {
            performWindThrow(player, false, 0.0);
        }

        // Auto-trigger windthrow when key held and ground is approaching
        if (windthrowHeld) {
            double vy = player.getDeltaMovement().y;
            if (vy <= WINDTHROW_TRIGGER_VERTICAL_SPEED && !player.onGround()) {
                double rayDist = fallRayDistance(vy);
                double dist = distanceToGround(player, rayDist);

                boolean withinImmediate = dist >= MIN_BLOCK_DISTANCE && dist <= FALL_TRIGGER_DISTANCE;
                boolean willReachNextTick = dist > FALL_TRIGGER_DISTANCE
                        && dist <= rayDist
                        && (dist + vy) <= FALL_TRIGGER_DISTANCE;

                if (withinImmediate || willReachNextTick) {
                    if (!queueBestWindChargeUse(player, false, false, false)) {
                        performWindThrow(player, true, rayDist);
                    }
                }
            }
        }
    }

    @Override
    public void onFrame(float tickDelta) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        LocalPlayer player = mc.player;
        if (!player.isFallFlying() || !isActionHeld(ACTION_WINDJUMP)) return;

        if (getWindChargeHand(player) == null) return;

        double previewTicks = Math.max(0.0, tickDelta);
        Vec3 predictedPos = predictSelfPosition(player, previewTicks, true, false);

        HitResult hit = player.level().clip(new ClipContext(
                predictedPos.add(0.0, 0.2, 0.0),
                predictedPos.add(0.0, -MAX_BLOCK_DISTANCE, 0.0),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        double distance = predictedPos.y - hit.getLocation().y();
        if (distance < MIN_BLOCK_DISTANCE || distance > MAX_BLOCK_DISTANCE) return;

        queueBestWindChargeUse(player, false, true, false);
    }

    private boolean performWindThrow(LocalPlayer player, boolean allowNearGround, double nearGroundRange) {
        if (player == null) return false;
        if (player.inventoryMenu == null) return false;
        if (!hasWindCharge(player)) return false;

        double vy = player.getDeltaMovement().y;
        double range = allowNearGround ? Math.max(FALL_TRIGGER_DISTANCE, Math.min(MAX_FALL_RAYCAST, Math.max(FALL_TRIGGER_DISTANCE, nearGroundRange))) : 0.0;
        boolean nearGround = allowNearGround && vy <= WINDTHROW_TRIGGER_VERTICAL_SPEED && isNearGround(player, range);
        boolean onGround = player.onGround();
        boolean groundOk = onGround || nearGround;
        if (!groundOk) return false; // only from ground / near-ground

        return queueBestWindChargeUse(player, onGround, false, false);
    }

    private boolean processHeldWindJump(LocalPlayer player) {
        if (player == null || !isActionHeld(ACTION_WINDJUMP)) {
            return false;
        }

        if (player.isFallFlying()
                || player.isSwimming()
                || player.isInWater()
                || player.isInLava()) {
            return false;
        }

        if (!findCollision(player)) {
            return false;
        }

        return queueHeldWindJumpUse(player);
    }

    private boolean canJumpNow(LocalPlayer player) {
        return player != null
                && !player.isPassenger()
                && !player.getAbilities().flying
                && (player.onGround() || player.isInWater() || player.isInLava());
    }

    @EventHandler(priority = -1000)
    private void onMovementInput(MovementInputEvent event) {
        if (!isEnabled() || !isActionHeld(ACTION_WINDJUMP)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (canStartHeldWindJump(player)) {
            event.setJump(true);
        }
    }

    @EventHandler(priority = -1000)
    private void onPlayerJump(PlayerJumpEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        lastJumpAge = player.tickCount;
    }

    @EventHandler(priority = -1000)
    private void onSync(EventSync event) {
        if (!isEnabled()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        MultiPlayerGameMode manager = Minecraft.getInstance().gameMode;
        if (player == null || manager == null || pendingUse == null) return;

        PendingUse use = pendingUse;
        if (player.tickCount > use.expireAge()) {
            pendingUse = null;
            return;
        }
        if (player.tickCount < nextAllowedUseTick) {
            pendingUse = null;
            return;
        }
        if (player.getCooldowns().isOnCooldown(Items.WIND_CHARGE.getDefaultInstance())) {
            pendingUse = null;
            return;
        }

        pendingUse = null;

        event.setRotation(use.yaw(), use.pitch(), true);
        executePendingUse(use);
    }

    private void executePendingUse(PendingUse use) {
        LocalPlayer player = Minecraft.getInstance().player;
        MultiPlayerGameMode manager = Minecraft.getInstance().gameMode;
        if (player == null || manager == null || use == null) return;

        float yaw = use.yaw();
        float pitch = use.pitch();
        int syncId = player.inventoryMenu != null ? player.inventoryMenu.containerId : -1;
        boolean swapped = use.sourceScreenSlot() >= 0 && use.targetScreenSlot() >= 0 && syncId >= 0;

        float prevYaw = player.getYRot();
        float prevPitch = player.getXRot();
        try {
            if (use.hotbarSlot() >= 0) {
                InventorySwap.INSTANCE.leaseHotbar(this, use.hotbarSlot(), HOTBAR_RESET_TICKS);
            }
            if (swapped) {
                InventorySwap.INSTANCE.swapScreenSlots(use.sourceScreenSlot(), use.targetScreenSlot());
            }
            player.setYRot(yaw);
            player.setXRot(pitch);
            InteractionResult result = manager.useItem(player, use.hand());
            if (!result.consumesAction()) return;
            player.swing(use.hand());
            nextAllowedUseTick = player.tickCount + WIND_CHARGE_COOLDOWN_TICKS;
        } finally {
            player.setYRot(prevYaw);
            player.setXRot(prevPitch);
            if (swapped) {
                InventorySwap.INSTANCE.swapScreenSlots(use.sourceScreenSlot(), use.targetScreenSlot());
            }
        }
    }

    private int findWindChargeSlot(LocalPlayer player, int originalSlot) {
        // prefer hotbar (excluding current slot), then rest of inventory
        for (int i = 0; i < 9; i++) {
            if (i == originalSlot) continue;
            if (player.getInventory().getItem(i).is(Items.WIND_CHARGE)) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.WIND_CHARGE)) return i;
        }
        return -1;
    }

    private InteractionHand getWindChargeHand(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (isWindCharge(main)) return InteractionHand.MAIN_HAND;
        if (isWindCharge(off)) return InteractionHand.OFF_HAND;
        return null;
    }

    private boolean isWindCharge(ItemStack stack) {
        return stack != null && stack.getItem() == Items.WIND_CHARGE;
    }

    private boolean hasWindCharge(LocalPlayer player) {
        if (isWindCharge(player.getMainHandItem()) || isWindCharge(player.getOffhandItem())) {
            return true;
        }
        return findWindChargeSlot(player, ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot()) != -1;
    }

    private int findWindChargeHotbarSlot(LocalPlayer player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(Items.WIND_CHARGE)) {
                return i;
            }
        }
        return -1;
    }

    private boolean queueHeldWindJumpUse(LocalPlayer player) {
        if (player == null || pendingUse != null || player.tickCount < nextAllowedUseTick) {
            return false;
        }

        UseSource source = resolveHeldWindJumpSource(player);
        if (source == null) {
            return false;
        }

        Rotation rotation = buildJumpStartRotation(player);
        pendingUse = new PendingUse(
                source.hand(),
                rotation.yaw(),
                rotation.pitch(),
                source.sourceScreenSlot(),
                source.targetScreenSlot(),
                source.hotbarSlot(),
                player.tickCount + USE_EXPIRE_TICKS
        );
        return true;
    }

    private boolean canStartHeldWindJump(LocalPlayer player) {
        if (!canJumpNow(player)) {
            return false;
        }
        if (player == null || player.inventoryMenu == null) {
            return false;
        }
        if (player.getCooldowns().isOnCooldown(Items.WIND_CHARGE.getDefaultInstance())) {
            return false;
        }
        return resolveHeldWindJumpSource(player) != null;
    }

    private UseSource resolveHeldWindJumpSource(LocalPlayer player) {
        if (player == null) return null;
        if (isWindCharge(player.getOffhandItem())) {
            return new UseSource(InteractionHand.OFF_HAND, -1, -1, -1);
        }
        if (isWindCharge(player.getMainHandItem())) {
            return new UseSource(InteractionHand.MAIN_HAND, -1, -1, -1);
        }

        int hotbarSlot = findWindChargeHotbarSlot(player);
        if (hotbarSlot == -1) {
            return null;
        }

        return new UseSource(InteractionHand.MAIN_HAND, -1, -1, hotbarSlot);
    }

    private boolean queueBestWindChargeUse(LocalPlayer player,
                                           boolean allowInventorySwap,
                                           boolean preferJumpPrediction,
                                           boolean jumpStartThrow) {
        if (player == null) return false;
        if (pendingUse != null) return false;
        if (player.tickCount < nextAllowedUseTick) return false;

        UseSource source = resolveUseSource(player, allowInventorySwap);
        if (source == null) return false;

        Rotation rotation = computeThrowRotation(player, preferJumpPrediction, jumpStartThrow);
        if (rotation == null) return false;

        int expireAge = player.tickCount + USE_EXPIRE_TICKS;
        pendingUse = new PendingUse(
                source.hand(),
                rotation.yaw(),
                rotation.pitch(),
                source.sourceScreenSlot(),
                source.targetScreenSlot(),
                source.hotbarSlot(),
                expireAge
        );
        return true;
    }

    private UseSource resolveUseSource(LocalPlayer player, boolean allowInventorySwap) {
        if (player == null) return null;
        if (isWindCharge(player.getMainHandItem())) {
            return new UseSource(InteractionHand.MAIN_HAND, -1, -1, -1);
        }
        if (isWindCharge(player.getOffhandItem())) {
            return new UseSource(InteractionHand.OFF_HAND, -1, -1, -1);
        }
        if (!allowInventorySwap) {
            return null;
        }

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        int windSlot = findWindChargeSlot(player, selectedSlot);
        if (windSlot == -1) {
            return null;
        }

        return new UseSource(
                InteractionHand.MAIN_HAND,
                InventorySwap.mapInventoryToScreenSlot(windSlot),
                InventorySwap.mapHotbarToScreenSlot(selectedSlot),
                -1
        );
    }

    private Rotation computeThrowRotation(LocalPlayer player,
                                          boolean preferJumpPrediction,
                                          boolean jumpStartThrow) {
        if (player == null) return null;
        if (jumpStartThrow) {
            return buildJumpStartRotation(player);
        }

        Vec3 eyePos = player.getEyePosition();
        double leadTicks = Mth.clamp(
                initialLeadTicks(player, preferJumpPrediction, jumpStartThrow),
                jumpStartThrow ? 0.02 : 0.15,
                MAX_THROW_PREDICTION_TICKS
        );
        Vec3 impactPoint = null;

        for (int i = 0; i < 3; i++) {
            Vec3 predictedSelf = predictSelfPosition(player, leadTicks, preferJumpPrediction, jumpStartThrow);
            impactPoint = findGroundImpactPoint(player, predictedSelf, jumpStartThrow);
            if (impactPoint == null) {
                break;
            }

            double flightTicks = Mth.clamp(
                    eyePos.distanceTo(impactPoint) / effectiveWindChargeSpeed(),
                    jumpStartThrow ? 0.02 : 0.15,
                    MAX_THROW_PREDICTION_TICKS
            );
            if (Math.abs(flightTicks - leadTicks) < 0.1) {
                break;
            }
            leadTicks = flightTicks;
        }

        if (impactPoint == null) {
            return fallbackRotation(player);
        }

        Vec3 diff = impactPoint.subtract(eyePos);
        double horizontalSq = diff.x * diff.x + diff.z * diff.z;
        if (horizontalSq < 0.0025) {
            return fallbackRotation(player);
        }

        Rotation rotation = Rotation.lookingAt(impactPoint, eyePos);
        float yaw = horizontalSq < 0.01 ? resolveMotionYaw(player, rotation.yaw()) : rotation.yaw();
        float pitch = Mth.clamp(Math.max(rotation.pitch(), MIN_DOWN_PITCH), MIN_DOWN_PITCH, THROW_PITCH);
        return new Rotation(yaw, pitch, false).normalize();
    }

    private Rotation buildJumpStartRotation(LocalPlayer player) {
        float directionYaw = MovementUtil.getMovementDirectionYaw(player, player.getYRot()) - 180.0f;
        return new Rotation(directionYaw, 80.0f, false).normalize();
    }

    private Vec3 predictSelfPosition(LocalPlayer player,
                                     double leadTicks,
                                     boolean preferJumpPrediction,
                                     boolean jumpStartThrow) {
        double totalTicks = leadTicks;
        if (jumpStartThrow) {
            totalTicks += JUMP_THROW_INPUT_DELAY_TICKS;
        } else if (preferJumpPrediction && isRecentJump(player)) {
            totalTicks += 0.18;
        }
        totalTicks = Mth.clamp(totalTicks, 0.0, MAX_THROW_PREDICTION_TICKS);

        Vec3 position = player.position();
        Vec3 velocity = resolvePredictionVelocity(player, jumpStartThrow);
        Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0, velocity.z);

        double horizontalTicks = totalTicks * (jumpStartThrow ? JUMP_START_HORIZONTAL_LEAD_FACTOR : HORIZONTAL_LEAD_FACTOR);
        Vec3 horizontalLead = horizontalVelocity.scale(horizontalTicks);
        double horizontalLength = horizontalLead.horizontalDistance();
        double maxHorizontalLead = jumpStartThrow ? JUMP_START_MAX_HORIZONTAL_LEAD : MAX_HORIZONTAL_LEAD;
        if (horizontalLength > maxHorizontalLead && horizontalLength > 1.0E-6) {
            horizontalLead = horizontalLead.scale(maxHorizontalLead / horizontalLength);
        }

        double verticalLead = velocity.y * totalTicks * (jumpStartThrow ? JUMP_START_VERTICAL_LEAD_FACTOR : VERTICAL_LEAD_FACTOR);
        if (preferJumpPrediction && isRecentJump(player) && velocity.y > 0.0) {
            double bonusScale = jumpStartThrow ? 0.12 : 0.35;
            double bonusCap = jumpStartThrow ? JUMP_VERTICAL_BONUS * 0.4 : JUMP_VERTICAL_BONUS;
            verticalLead += Math.min(bonusCap, velocity.y * bonusScale);
        }

        return position.add(horizontalLead.x, verticalLead, horizontalLead.z);
    }

    private Vec3 findGroundImpactPoint(LocalPlayer player, Vec3 predictedSelf, boolean jumpStartThrow) {
        if (player == null || predictedSelf == null || player.level() == null) return null;

        double verticalRoom = Math.max(0.0, predictedSelf.y - player.getY());
        double rayDistance = Mth.clamp(
                2.2 + verticalRoom + Math.max(0.0, player.getDeltaMovement().y) * 5.0,
                MAX_BLOCK_DISTANCE,
                MAX_FALL_RAYCAST
        );
        Vec3 start = new Vec3(predictedSelf.x, predictedSelf.y + 0.25, predictedSelf.z);
        Vec3 end = start.add(0.0, -rayDistance, 0.0);
        HitResult hit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            Vec3 impact = hit.getLocation().add(0.0, IMPACT_POINT_Y_OFFSET, 0.0);
            Vec3 horizontalVelocity = new Vec3(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
            double speed = horizontalVelocity.horizontalDistance();
            if (speed > 1.0E-6) {
                double factor = jumpStartThrow ? JUMP_START_IMPACT_BACKTRACK_FACTOR : IMPACT_BACKTRACK_FACTOR;
                double maxBacktrack = jumpStartThrow ? JUMP_START_MAX_IMPACT_BACKTRACK : MAX_IMPACT_BACKTRACK;
                double backtrack = Math.min(maxBacktrack, speed * factor);
                impact = impact.subtract(horizontalVelocity.normalize().scale(backtrack));
            }
            return impact;
        }
        return null;
    }

    private Rotation fallbackRotation(LocalPlayer player) {
        return new Rotation(resolveMotionYaw(player, player.getYRot()), THROW_PITCH, false).normalize();
    }

    private double initialLeadTicks(LocalPlayer player, boolean preferJumpPrediction, boolean jumpStartThrow) {
        Vec3 velocity = resolvePredictionVelocity(player, jumpStartThrow);
        if (jumpStartThrow) {
            double lead = JUMP_THROW_INPUT_DELAY_TICKS
                    + Math.min(0.03, velocity.horizontalDistance() * 0.04)
                    + Math.min(0.04, Math.max(0.0, velocity.y) * 0.08);
            if (preferJumpPrediction && isRecentJump(player)) {
                lead += 0.01;
            }
            return Mth.clamp(lead, JUMP_START_MIN_LEAD_TICKS, JUMP_START_MAX_LEAD_TICKS);
        }

        double lead = 0.35
                + Math.min(0.85, velocity.horizontalDistance() * 1.15)
                + Math.min(0.65, Math.max(0.0, velocity.y) * 1.35);
        if (preferJumpPrediction && isRecentJump(player)) {
            lead += 0.45;
        }
        return Math.min(MAX_THROW_PREDICTION_TICKS, lead);
    }

    private boolean isRecentJump(LocalPlayer player) {
        if (player == null || lastJumpAge == Integer.MIN_VALUE) return false;
        return player.tickCount - lastJumpAge <= 2;
    }

    private boolean findCollision(LocalPlayer player) {
        if (player == null || player.isFallFlying() || player.isSwimming() || player.isInWater() || player.isInLava()) {
            return false;
        }

        PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForLocalPlayer();
        if (simulation == null) {
            return false;
        }

        return simulation.getSnapshotAt(PREDICTION_TICKS).onGround();
    }

    private Vec3 resolvePredictionVelocity(LocalPlayer player, boolean jumpStartThrow) {
        Vec3 velocity = player.getDeltaMovement();
        return velocity;
    }

    private float resolveMotionYaw(LocalPlayer player, float fallbackYaw) {
        if (player == null) return fallbackYaw;

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.horizontalDistanceSqr() > MOTION_YAW_EPSILON * MOTION_YAW_EPSILON) {
            return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(velocity.z, velocity.x)) - 90.0f);
        }

        return Mth.wrapDegrees(MovementUtil.getMovementDirectionYaw(player, fallbackYaw));
    }

    private boolean isNearGround(LocalPlayer player, double maxDist) {
        if (player == null || player.level() == null) return false;
        double dist = distanceToGround(player, maxDist);
        return dist >= 0 && dist <= maxDist;
    }

    private double distanceToGround(LocalPlayer player, double maxDist) {
        if (player == null || player.level() == null) return Double.POSITIVE_INFINITY;
        Vec3 start = player.position();
        Vec3 end = start.add(0, -maxDist, 0);
        HitResult hit = player.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            return start.y - hit.getLocation().y();
        }
        return Double.POSITIVE_INFINITY;
    }

    private double fallRayDistance(double vy) {
        return Math.max(FALL_TRIGGER_DISTANCE, Math.min(MAX_FALL_RAYCAST, Math.abs(vy) * 2.0));
    }

    private double serverTickDelta() {
        double delta = 1.0;
        TPSSync tpsSync = Modules.get(TPSSync.class);
        if (tpsSync != null && tpsSync.isEnabled()) {
            delta = tpsSync.getServerTickDelta();
        }
        return Math.max(0.05, delta);
    }

    private double effectiveWindChargeSpeed() {
        return WIND_CHARGE_SPEED * serverTickDelta();
    }

    @Getter
    @RequiredArgsConstructor
    private enum WindThrowMode implements EnumValue.IdProvider {
        UNLOCKED("UNLOCKED"),
        LEGIT("LEGIT");

        private final String id;
    }

    private record UseSource(InteractionHand hand, int sourceScreenSlot, int targetScreenSlot, int hotbarSlot) {
    }

    private record PendingUse(InteractionHand hand, float yaw, float pitch, int sourceScreenSlot, int targetScreenSlot,
                              int hotbarSlot, int expireAge) {
    }
}
