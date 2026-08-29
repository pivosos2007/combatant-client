/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.script;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Flat, proxy-free player state transferred to the isolated JavaScript runtime. */
public final class PlayerRigScriptContext {
    private PlayerRigScriptContext() {
    }

    public static Object[] pack(AbstractClientPlayer player, float tickDelta, float deltaSeconds,
                                String style, float strength, int swingIndex, float attackProgress) {
        if (player == null) throw new IllegalArgumentException("Player rig script player must not be null");
        var velocity = player.getDeltaMovement();
        ItemStack useItem = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        String useArm = "none";
        if (player.isUsingItem()) {
            boolean mainHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND;
            HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            useArm = arm.getSerializedName();
        }
        return new Object[]{
                player.getUUID().toString(),
                player.tickCount + tickDelta,
                tickDelta,
                deltaSeconds,
                player.getYRot(tickDelta),
                player.getXRot(tickDelta),
                attackProgress,
                velocity.x,
                velocity.y,
                velocity.z,
                player.onGround(),
                player.isShiftKeyDown(),
                player.isSprinting(),
                player.isVisuallySwimming(),
                player.isFallFlying(),
                player.isPassenger(),
                player.isUsingItem(),
                player.getPose().name().toLowerCase(java.util.Locale.ROOT),
                player.getMainArm().getSerializedName(),
                BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString(),
                BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).toString(),
                style != null ? style : "Hybrid",
                Math.max(0f, Math.min(2f, strength)),
                player.onClimbable(),
                player.isInWater(),
                player.isUnderWater(),
                player.getPose() == Pose.SWIMMING && !player.isInWater(),
                player.fallDistance,
                player.getY(),
                useItem.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT),
                BuiltInRegistries.ITEM.getKey(useItem.getItem()).toString(),
                useArm,
                player.isUsingItem() ? player.getTicksUsingItem() : 0,
                swingIndex
        };
    }
}
