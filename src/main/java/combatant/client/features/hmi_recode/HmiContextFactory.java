/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HmiContextFactory {
    private static final Map<Item, StaticItemData> STATIC_ITEMS = new Reference2ObjectOpenHashMap<>();

    private HmiContextFactory() {
    }

    static Object[] renderContext(HoldMyItems.RenderScope scope) {
        LocalPlayer player = scope.player();
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        Object[] mainItem = item(mainStack, player);
        Object[] offItem = item(offStack, player);
        int renderedItem = scope.item() == mainStack ? 0 : scope.item() == offStack ? 1 : 2;
        Object[] extraItem = renderedItem == 2 ? item(scope.item(), player) : null;
        HoldMyItems.MotionSettings motion = scope.motionSettings();
        Minecraft minecraft = Minecraft.getInstance();
        return new Object[]{
                player(player, scope.tickDelta(), scope.swingCount()),
                mainItem,
                offItem,
                renderedItem,
                extraItem,
                scope.hand() == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand",
                scope.mainHand(),
                scope.rightArm(),
                scope.scriptSwingProgress(),
                scope.rawSwingProgress(),
                scope.mainHandSwingProgress(),
                scope.offHandSwingProgress(),
                scope.equipProgress(),
                scope.deltaSeconds(),
                scope.mainHand() && scope.swingProgress() > 0.0f,
                !scope.mainHand() && scope.swingProgress() > 0.0f,
                scope.mainHandSwitchEvent(),
                scope.offHandSwitchEvent(),
                scope.blockBreaking(),
                new Object[]{
                        motion.swingStrength(), motion.swordSwingStrength(), motion.offhandSwingStrength(),
                        motion.movementStrength(), motion.lookStrength(), motion.switchStrength(),
                        motion.useStrength(), motion.impactStrength(), motion.replaceSwing(), motion.swingStyle()
                },
                minecraft.getWindow() != null && InputConstants.isKeyDown(minecraft.getWindow(), 74)
        };
    }

    private static Object[] player(LocalPlayer player, float tickDelta, int swingCount) {
        var velocity = player.getDeltaMovement();
        return new Object[]{
                player.getHealth(), player.isShiftKeyDown(), player.onGround(), player.isVisuallySwimming(),
                player.onClimbable(), player.getPose() == Pose.SWIMMING && !player.isInWater(),
                player.isUnderWater(), player.isInWater(), player.isAutoSpinAttack(), player.isUsingItem(),
                player.isUsingItem() ? handName(player.getUsedItemHand()) : "none",
                player.getX(), player.getY(), player.getZ(), player.getYRot(tickDelta), player.getXRot(tickDelta),
                player.tickCount + tickDelta, swingCount, player.isPassenger(),
                velocity.x, velocity.y, velocity.z
        };
    }

    private static Object[] item(ItemStack stack, LocalPlayer player) {
        ItemStack safe = stack != null ? stack : ItemStack.EMPTY;
        StaticItemData staticData = STATIC_ITEMS.computeIfAbsent(safe.getItem(), HmiContextFactory::staticItem);
        String useAction = safe.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT);
        boolean empty = safe.isEmpty();
        var chargedProjectiles = safe.get(DataComponents.CHARGED_PROJECTILES);
        return new Object[]{
                staticData.id(), safe.getHoverName().getString(), empty, useAction, staticData.tags(),
                staticData.block(), staticData.lantern(), staticData.throwableItem(),
                !safe.getEnchantments().isEmpty(), chargedProjectiles != null && !chargedProjectiles.isEmpty(),
                player != null && player.getCooldowns().isOnCooldown(safe), false, false
        };
    }

    static void invalidateCaches() {
        STATIC_ITEMS.clear();
    }

    private static StaticItemData staticItem(Item item) {
        return new StaticItemData(
                BuiltInRegistries.ITEM.getKey(item).toString(),
                item.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).toList(),
                item instanceof BlockItem,
                Block.byItem(item) instanceof LanternBlock,
                item instanceof SplashPotionItem || item instanceof ProjectileItem
        );
    }

    private static String handName(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
    }

    private record StaticItemData(String id, List<String> tags, boolean block, boolean lantern,
                                  boolean throwableItem) {
    }
}
