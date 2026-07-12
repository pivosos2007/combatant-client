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

package combatant.client.util.projectile;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import combatant.client.mixins.accessors.PersistentProjectileEntityAccessor;

public enum TrajectoryData {
    ;

    public static TrajectoryInfo.Typed getRenderedTrajectoryInfo(Player player, ItemStack stack, boolean alwaysShowBow) {
        if (player == null || stack == null || stack.isEmpty()) {
            return null;
        }

        if (stack.is(Items.BOW)) {
            int useTime = alwaysShowBow && player.getTicksUsingItem() < 1 ? 40 : player.getTicksUsingItem();
            TrajectoryInfo info = TrajectoryInfo.bowWithUsageDuration(player, useTime);
            return info != null ? info.typed(TrajectoryType.ARROW) : null;
        }

        if (stack.is(Items.CROSSBOW)) {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged != null) {
                for (ItemStack projectile : charged.itemCopies()) {
                    if (projectile.is(Items.FIREWORK_ROCKET)) {
                        return TrajectoryInfo.FIREBALL.typed(TrajectoryType.FIREBALL);
                    }
                }
            }
            return TrajectoryInfo.BOW_FULL_PULL.typed(TrajectoryType.ARROW);
        }

        if (stack.is(Items.FISHING_ROD)) {
            return TrajectoryInfo.FISHING_ROD.typed(TrajectoryType.FISHING_BOBBER);
        }
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            return TrajectoryInfo.POTION.typed(TrajectoryType.POTION);
        }
        if (stack.is(Items.TRIDENT)) {
            return TrajectoryInfo.TRIDENT.typed(TrajectoryType.TRIDENT);
        }
        if (stack.is(Items.SNOWBALL)) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.SNOWBALL);
        }
        if (stack.is(Items.ENDER_PEARL)) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.ENDER_PEARL);
        }
        if (stack.is(Items.EGG)) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.EGG);
        }
        if (stack.is(Items.EXPERIENCE_BOTTLE)) {
            return TrajectoryInfo.EXP_BOTTLE.typed(TrajectoryType.EXP_BOTTLE);
        }
        if (stack.is(Items.FIRE_CHARGE)) {
            return TrajectoryInfo.FIREBALL.typed(TrajectoryType.FIREBALL);
        }
        if (stack.is(Items.WIND_CHARGE)) {
            return TrajectoryInfo.WIND_CHARGE.typed(TrajectoryType.WIND_CHARGE);
        }

        return null;
    }

    public static TrajectoryInfo.Typed getTrajectoryInfoForEntity(Entity entity) {
        if (entity == null) {
            return null;
        }

        if (entity instanceof AbstractArrow persistent) {
            boolean inGround;
            try {
                inGround = ((PersistentProjectileEntityAccessor) persistent).combatant$isInGround();
            } catch (Throwable ignored) {
                inGround = false;
            }
            return inGround ? null : new TrajectoryInfo(0.05, 0.3).typed(TrajectoryType.ARROW);
        }
        if (entity instanceof AbstractThrownPotion) {
            return TrajectoryInfo.POTION.typed(TrajectoryType.POTION);
        }
        if (entity instanceof ThrownTrident trident) {
            return trident.isNoPhysics() ? null : TrajectoryInfo.TRIDENT.typed(TrajectoryType.TRIDENT);
        }
        if (entity instanceof ThrownEnderpearl) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.ENDER_PEARL);
        }
        if (entity instanceof Snowball) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.SNOWBALL);
        }
        if (entity instanceof ThrownExperienceBottle) {
            return TrajectoryInfo.EXP_BOTTLE.typed(TrajectoryType.EXP_BOTTLE);
        }
        if (entity instanceof ThrownEgg) {
            return TrajectoryInfo.GENERIC.typed(TrajectoryType.EGG);
        }
        if (entity instanceof FireworkRocketEntity || entity instanceof Fireball) {
            return TrajectoryInfo.FIREBALL.typed(TrajectoryType.FIREBALL);
        }
        if (entity instanceof WindCharge) {
            return TrajectoryInfo.WIND_CHARGE.typed(TrajectoryType.WIND_CHARGE);
        }

        return null;
    }
}
