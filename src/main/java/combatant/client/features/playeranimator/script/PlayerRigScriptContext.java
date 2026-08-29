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
    public static Object[] pack(AbstractClientPlayer player, AvatarRenderState state,
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
        return new Object[]{
                // Original bridge (0..33).
                player.getUUID().toString(),                       // 0
                player.tickCount + tickDelta,                      // 1
                tickDelta,                                        // 2
                deltaSeconds,                                     // 3
                player.getYRot(tickDelta),                         // 4
                player.getXRot(tickDelta),                         // 5
                attackProgress,                                   // 6
                velocity.x,                                       // 7
                velocity.y,                                       // 8
                velocity.z,                                       // 9
                player.onGround(),                                // 10
                player.isShiftKeyDown(),                           // 11
                player.isSprinting(),                              // 12
                player.isVisuallySwimming(),                       // 13
                player.isFallFlying(),                             // 14
                player.isPassenger(),                              // 15
                player.isUsingItem(),                              // 16
                player.getPose().name().toLowerCase(Locale.ROOT), // 17
                player.getMainArm().getSerializedName(),           // 18
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString(), // 19
                BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString(),  // 20
                style != null ? style : "Hybrid",                 // 21
                Math.max(0f, Math.min(2f, strength)),              // 22
                player.onClimbable(),                              // 23
                player.isInWater(),                               // 24
                player.isUnderWater(),                            // 25
                player.getPose() == Pose.SWIMMING && !player.isInWater(), // 26
                player.fallDistance,                              // 27
                player.getY(),                                    // 28
                useItem.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT), // 29
                BuiltInRegistries.ITEM.getKey(useItem.getItem()).toString(), // 30
                useArm,                                            // 31
                player.isUsingItem() ? player.getTicksUsingItem() : 0, // 32
                swingIndex,                                        // 33

                // Render-time extensions (34+). These are populated by AvatarRenderer before JS.
                attackTimeSeconds,                                 // 34
                attackActive,                                      // 35
                state.bodyRot,                                     // 36 world body yaw, degrees
                state.yRot,                                        // 37 relative head yaw, degrees
                state.xRot,                                        // 38 head pitch, degrees
                state.walkAnimationPos,                            // 39 interpolated limb phase
                state.walkAnimationSpeed,                          // 40 interpolated limb amplitude
                state.speedValue,                                  // 41 vanilla speed normalization
                state.swimAmount,                                  // 42 vanilla swim blend
                state.fallFlyingTimeInTicks,                       // 43 interpolated flight time
                state.shouldApplyFlyingYRot,                       // 44
                state.flyingYRot,                                  // 45
                state.isCrouching,                                 // 46
                state.isFallFlying,                                // 47
                state.isVisuallySwimming,                          // 48
                state.isPassenger,                                 // 49
                state.isUsingItem,                                 // 50
                state.isInWater,                                   // 51
                boatLeft,                                          // 52
                boatRight,                                         // 53
                boatLeftTime,                                      // 54 radians/continuous rowing phase from vanilla boat
                boatRightTime,                                     // 55
                vehicleType,                                       // 56
                horizontalSpeed,                                   // 57
                state.ageInTicks / 20.0,                             // 58 interpolated render clock, seconds
                state.ticksUsingItem,                               // 59 interpolated vanilla use ticks
                state.attackArm != null ? state.attackArm.getSerializedName() : player.getMainArm().getSerializedName(), // 60
                state.swingAnimationType != null ? state.swingAnimationType.getSerializedName() : "none", // 61
                state.attackTime,                                   // 62 vanilla interpolated attack progress
                state.x,                                            // 63 interpolated render X
                state.y,                                            // 64 interpolated render Y
                state.z,                                            // 65 interpolated render Z
                state.leftArmPose != null ? state.leftArmPose.name().toLowerCase(Locale.ROOT) : "empty",  // 66
                state.rightArmPose != null ? state.rightArmPose.name().toLowerCase(Locale.ROOT) : "empty",// 67
                state.maxCrossbowChargeDuration,                    // 68
                player.getAbilities().flying,                        // 69 creative flight, distinct from elytra
                player.getAttackStrengthScale(tickDelta),            // 70 current vanilla melee recharge
                attackDurationSeconds                                // 71 duration captured at swing start
        };
    }
}
