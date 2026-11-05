/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Flat, proxy-free player state transferred to the isolated JavaScript runtime. */
public final class PlayerRigScriptContext {
    private PlayerRigScriptContext() {
    }

    /**
     * Indices 0..33 intentionally retain the original bridge layout for resource-pack addons.
     * New interpolated vanilla render-state values are appended from index 34 onward.
     */
    interface Sink {
        void set(int index, double value);
        void set(int index, float value);
        void set(int index, int value);
        void set(int index, boolean value);
        void set(int index, String value);
    }

    private static final class ArraySink implements Sink {
        final Object[] values = new Object[PlayerRigInputBuffer.VALUE_COUNT];
        public void set(int i, double v) { values[i] = v; }
        public void set(int i, float v) { values[i] = v; }
        public void set(int i, int v) { values[i] = v; }
        public void set(int i, boolean v) { values[i] = v; }
        public void set(int i, String v) { values[i] = v; }
    }

    public static Object[] pack(AbstractClientPlayer player, AvatarRenderState state,
                                float tickDelta, float deltaSeconds,
                                String style, float strength, int swingIndex,
                                float attackProgress, float attackTimeSeconds,
                                float attackDurationSeconds, boolean attackActive) {
        ArraySink sink = new ArraySink();
        fill(sink, player, state, tickDelta, deltaSeconds, style, strength, swingIndex, attackProgress, attackTimeSeconds, attackDurationSeconds, attackActive);
        return sink.values;
    }

    /** Writes all fields directly into the existing V8 input buffer without boxing. */
    public static void writeNative(PlayerRigInputBuffer input, AbstractClientPlayer player, AvatarRenderState state,
                                float tickDelta, float deltaSeconds,
                                String style, float strength, int swingIndex,
                                float attackProgress, float attackTimeSeconds,
                                float attackDurationSeconds, boolean attackActive) {
        fill(input, player, state, tickDelta, deltaSeconds, style, strength, swingIndex, attackProgress, attackTimeSeconds, attackDurationSeconds, attackActive);
    }

    private static void fill(Sink sink, AbstractClientPlayer player, AvatarRenderState state,
                                float tickDelta, float deltaSeconds,
                                String style, float strength, int swingIndex,
                                float attackProgress, float attackTimeSeconds,
                                float attackDurationSeconds, boolean attackActive) {
        if (player == null || state == null) {
            throw new IllegalArgumentException("Player rig script player/state must not be null");
        }
        var velocity = player.getDeltaMovement();
        ItemStack useItem = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        String useArm = "none";
        if (player.isUsingItem()) {
            boolean mainHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            useArm = arm.getSerializedName();
        }

        Entity vehicle = player.getVehicle();
        boolean boatLeft = false;
        boolean boatRight = false;
        float boatLeftTime = 0f;
        float boatRightTime = 0f;
        if (vehicle instanceof AbstractBoat boat) {
            boatLeft = boat.getPaddleState(0);
            boatRight = boat.getPaddleState(1);
            boatLeftTime = boat.getRowingTime(0, tickDelta);
            boatRightTime = boat.getRowingTime(1, tickDelta);
        }
        String vehicleType = vehicle != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString()
                : "none";

        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        sink.set(0, player.getUUID().toString());
        sink.set(1, player.tickCount + tickDelta);
        sink.set(2, tickDelta);
        sink.set(3, deltaSeconds);
        sink.set(4, player.getYRot(tickDelta));
        sink.set(5, player.getXRot(tickDelta));
        sink.set(6, attackProgress);
        sink.set(7, velocity.x);
        sink.set(8, velocity.y);
        sink.set(9, velocity.z);
        sink.set(10, player.onGround());
        sink.set(11, player.isShiftKeyDown());
        sink.set(12, player.isSprinting());
        sink.set(13, player.isVisuallySwimming());
        sink.set(14, player.isFallFlying());
        sink.set(15, player.isPassenger());
        sink.set(16, player.isUsingItem());
        sink.set(17, player.getPose().name().toLowerCase(Locale.ROOT));
        sink.set(18, player.getMainArm().getSerializedName());
        sink.set(19, BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
        sink.set(20, BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString());
        sink.set(21, style != null ? style : "Hybrid");
        sink.set(22, Math.max(0f, Math.min(2f, strength)));
        sink.set(23, player.onClimbable());
        sink.set(24, player.isInWater());
        sink.set(25, player.isUnderWater());
        sink.set(26, player.getPose() == Pose.SWIMMING && !player.isInWater());
        sink.set(27, player.fallDistance);
        sink.set(28, player.getY());
        sink.set(29, useItem.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT));
        sink.set(30, BuiltInRegistries.ITEM.getKey(useItem.getItem()).toString());
        sink.set(31, useArm);
        sink.set(32, player.isUsingItem() ? player.getTicksUsingItem() : 0);
        sink.set(33, swingIndex);
        sink.set(34, attackTimeSeconds);
        sink.set(35, attackActive);
        sink.set(36, state.bodyRot);
        sink.set(37, state.yRot);
        sink.set(38, state.xRot);
        sink.set(39, state.walkAnimationPos);
        sink.set(40, state.walkAnimationSpeed);
        sink.set(41, state.speedValue);
        sink.set(42, state.swimAmount);
        sink.set(43, state.fallFlyingTimeInTicks);
        sink.set(44, state.shouldApplyFlyingYRot);
        sink.set(45, state.flyingYRot);
        sink.set(46, state.isCrouching);
        sink.set(47, state.isFallFlying);
        sink.set(48, state.isVisuallySwimming);
        sink.set(49, state.isPassenger);
        sink.set(50, state.isUsingItem);
        sink.set(51, state.isInWater);
        sink.set(52, boatLeft);
        sink.set(53, boatRight);
        sink.set(54, boatLeftTime);
        sink.set(55, boatRightTime);
        sink.set(56, vehicleType);
        sink.set(57, horizontalSpeed);
        sink.set(58, state.ageInTicks / 20.0);
        sink.set(59, state.ticksUsingItem);
        sink.set(60, state.currentSwing != null
                        ? (state.currentSwing.hand() == InteractionHand.MAIN_HAND
                            ? player.getMainArm() : player.getMainArm().getOpposite()).getSerializedName()
                        : player.getMainArm().getSerializedName());
        sink.set(61, state.currentSwing != null ? state.currentSwing.animation().type().getSerializedName() : "none");
        sink.set(62, state.swingAnimation);
        sink.set(63, state.x);
        sink.set(64, state.y);
        sink.set(65, state.z);
        sink.set(66, state.leftArmPose != null ? state.leftArmPose.name().toLowerCase(Locale.ROOT) : "empty");
        sink.set(67, state.rightArmPose != null ? state.rightArmPose.name().toLowerCase(Locale.ROOT) : "empty");
        sink.set(68, state.maxCrossbowChargeDuration);
        sink.set(69, player.getAbilities().flying);
        sink.set(70, player.getAttackStrengthScale(tickDelta));
        sink.set(71, attackDurationSeconds);
    }
}
