/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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

    static Map<String, Object> renderContext(HoldMyItems.RenderScope scope) {
        LocalPlayer player = scope.player();
        ItemStack mainStack = player.getMainHandItem();
        ItemStack offStack = player.getOffhandItem();
        Map<String, Object> mainItem = item(mainStack, player);
        Map<String, Object> offItem = item(offStack, player);
        Map<String, Object> renderedItem = scope.item() == mainStack
                ? mainItem
                : scope.item() == offStack ? offItem : item(scope.item(), player);

        Map<String, Object> map = new Object2ObjectOpenHashMap<>(32);
        map.put("player", player(player, scope.tickDelta(), scope.swingCount(), mainItem, offItem));
        map.put("item", renderedItem);
        map.put("hand", scope.hand() == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand");
        map.put("mainHand", scope.mainHand());
        map.put("bl", scope.rightArm());
        map.put("swingProgress", scope.scriptSwingProgress());
        map.put("rawSwingProgress", scope.rawSwingProgress());
        map.put("mainHandSwingProgress", scope.mainHandSwingProgress());
        map.put("offHandSwingProgress", scope.offHandSwingProgress());
        map.put("equipProgress", scope.equipProgress());
        map.put("deltaTime", scope.deltaSeconds());
        map.put("swingMHand", scope.mainHand() && scope.swingProgress() > 0.0f);
        map.put("swingOHand", !scope.mainHand() && scope.swingProgress() > 0.0f);
        map.put("mainHandSwitchEvent", scope.mainHandSwitchEvent());
        map.put("offHandSwitchEvent", scope.offHandSwitchEvent());
        map.put("blockBreaking", scope.blockBreaking());
        HoldMyItems.MotionSettings motion = scope.motionSettings();
        map.put("motion", Map.of(
                "swing", motion.swingStrength(),
                "swordSwing", motion.swordSwingStrength(),
                "offhandSwing", motion.offhandSwingStrength(),
                "movement", motion.movementStrength(),
                "look", motion.lookStrength(),
                "switch", motion.switchStrength(),
                "use", motion.useStrength(),
                "impact", motion.impactStrength(),
                "replaceSwing", motion.replaceSwing(),
                "swingStyle", motion.swingStyle()
        ));
        map.put("matrices", 0);
        map.put("particles", List.of());
        Minecraft minecraft = Minecraft.getInstance();
        map.put("inspectPressed", minecraft.getWindow() != null && InputConstants.isKeyDown(minecraft.getWindow(), 74));
        return map;
    }

    private static Map<String, Object> player(LocalPlayer player, float tickDelta, int swingCount,
                                              Map<String, Object> mainItem, Map<String, Object> offItem) {
        Map<String, Object> map = new Object2ObjectOpenHashMap<>(24);
        var velocity = player.getDeltaMovement();
        map.put("health", player.getHealth());
        map.put("sneaking", player.isShiftKeyDown());
        map.put("onGround", player.onGround());
        map.put("swimming", player.isVisuallySwimming());
        map.put("climbing", player.onClimbable());
        map.put("crawling", player.getPose() == Pose.SWIMMING && !player.isInWater());
        map.put("underWater", player.isUnderWater());
        map.put("inWater", player.isInWater());
        map.put("riptide", player.isAutoSpinAttack());
        map.put("usingItem", player.isUsingItem());
        map.put("activeHand", player.isUsingItem() ? handName(player.getUsedItemHand()) : "none");
        map.put("x", player.getX());
        map.put("y", player.getY());
        map.put("z", player.getZ());
        map.put("yaw", player.getYRot(tickDelta));
        map.put("pitch", player.getXRot(tickDelta));
        map.put("age", player.tickCount + tickDelta);
        map.put("swingCount", swingCount);
        map.put("hasVehicle", player.isPassenger());
        map.put("velocity", Map.of("x", velocity.x, "y", velocity.y, "z", velocity.z));
        map.put("mainItem", mainItem);
        map.put("offItem", offItem);
        return map;
    }

    private static Map<String, Object> item(ItemStack stack, LocalPlayer player) {
        ItemStack safe = stack != null ? stack : ItemStack.EMPTY;
        StaticItemData staticData = STATIC_ITEMS.computeIfAbsent(safe.getItem(), HmiContextFactory::staticItem);
        String useAction = safe.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT);
        boolean empty = safe.isEmpty();
        Map<String, Object> map = new Object2ObjectOpenHashMap<>(20);
        map.put("id", staticData.id());
        map.put("name", safe.getHoverName().getString());
        map.put("empty", empty);
        map.put("useAction", useAction);
        map.put("tags", staticData.tags());
        map.put("block", staticData.block());
        map.put("lantern", staticData.lantern());
        map.put("throwable", staticData.throwableItem());
        map.put("enchanted", !safe.getEnchantments().isEmpty());
        var chargedProjectiles = safe.get(DataComponents.CHARGED_PROJECTILES);
        map.put("chargedCrossbow", chargedProjectiles != null && !chargedProjectiles.isEmpty());
        map.put("cooldown", player != null && player.getCooldowns().isOnCooldown(safe));
        map.put("translate", false);
        map.put("customTranslate", false);
        map.put("spearData", Map.of("canDamage", true, "canDismount", true, "canKnockback", true, "hitImpact", false));
        return map;
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
