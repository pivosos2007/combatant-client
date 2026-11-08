/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import combatant.client.features.hmi_recode.script.HmiInputBuffer;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class HmiContextFactory {
    private static final Map<Item, StaticItemData> STATIC_ITEMS = new Reference2ObjectOpenHashMap<>();

    private HmiContextFactory() {
    }

    public static Object[] renderContext(HoldMyItems.RenderScope scope) {
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
                minecraft.getWindow() != null && combatant.client.input.CombatantInput.state().isPressed(
                        combatant.client.input.InputDevice.KEYBOARD, combatant.client.input.InputKeys.J)
        };
    }

    /** Fill the V8-owned native input directly. No Object[] or boxed numeric values are produced. */
    public static void writeNative(HoldMyItems.RenderScope scope, HmiInputBuffer out) {
        LocalPlayer player = scope.player();
        var velocity = player.getDeltaMovement();
        out.put(0, player.getHealth());
        out.put(1, player.isShiftKeyDown());
        out.put(2, player.onGround());
        out.put(3, player.isVisuallySwimming());
        out.put(4, player.onClimbable());
        out.put(5, player.getPose() == Pose.SWIMMING && !player.isInWater());
        out.put(6, player.isUnderWater());
        out.put(7, player.isInWater());
        out.put(8, player.isAutoSpinAttack());
        out.put(9, player.isUsingItem());
        out.meta(12, player.isUsingItem() ? handName(player.getUsedItemHand()) : "none");
        out.put(10, 0);
        out.put(11, player.getX());
        out.put(12, player.getY());
        out.put(13, player.getZ());
        out.put(14, player.getYRot(scope.tickDelta()));
        out.put(15, player.getXRot(scope.tickDelta()));
        out.put(16, player.tickCount + scope.tickDelta());
        out.put(17, scope.swingCount());
        out.put(18, player.isPassenger());
        out.put(19, velocity.x);
        out.put(20, velocity.y);
        out.put(21, velocity.z);

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        writeItem(out, 22, 0, main, player);
        writeItem(out, 31, 4, off, player);
        int selected = scope.item() == main ? 0 : scope.item() == off ? 1 : 2;
        // The third item is only visible to JS for a replaced render item.
        // Do not resolve the empty-item metadata on ordinary first-person frames.
        if (selected == 2) writeItem(out, 40, 8, scope.item(), player);
        out.put(49, selected);
        out.put(50, scope.hand() == InteractionHand.MAIN_HAND);
        out.put(51, scope.mainHand());
        out.put(52, scope.rightArm());
        out.put(53, scope.scriptSwingProgress());
        out.put(54, scope.rawSwingProgress());
        out.put(55, scope.mainHandSwingProgress());
        out.put(56, scope.offHandSwingProgress());
        out.put(57, scope.equipProgress());
        out.put(58, scope.deltaSeconds());
        out.put(59, scope.mainHand() && scope.swingProgress() > 0f);
        out.put(60, !scope.mainHand() && scope.swingProgress() > 0f);
        out.put(61, scope.mainHandSwitchEvent());
        out.put(62, scope.offHandSwitchEvent());
        out.put(63, scope.blockBreaking());

        HoldMyItems.MotionSettings m = scope.motionSettings();
        out.put(64, m.swingStrength());
        out.put(65, m.swordSwingStrength());
        out.put(66, m.offhandSwingStrength());
        out.put(67, m.movementStrength());
        out.put(68, m.lookStrength());
        out.put(69, m.switchStrength());
        out.put(70, m.useStrength());
        out.put(71, m.impactStrength());
        out.put(72, m.replaceSwing());
        out.meta(13, m.swingStyle());
        Minecraft minecraft = Minecraft.getInstance();
        out.put(73, minecraft.getWindow() != null && combatant.client.input.CombatantInput.state().isPressed(
                combatant.client.input.InputDevice.KEYBOARD, combatant.client.input.InputKeys.J));
    }

    private static void writeItem(HmiInputBuffer out, int base, int metaBase,
                                  ItemStack stack, LocalPlayer player) {
        ItemStack safe = stack != null ? stack : ItemStack.EMPTY;
        StaticItemData data = STATIC_ITEMS.computeIfAbsent(safe.getItem(), HmiContextFactory::staticItem);
        out.meta(metaBase, data.id());
        out.meta(metaBase + 1, safe.getHoverName().getString());
        out.meta(metaBase + 2, safe.getUseAnimation().getSerializedName().toLowerCase(Locale.ROOT));
        out.meta(metaBase + 3, data.tags());
        out.put(base, safe.isEmpty());
        out.put(base + 1, data.block());
        out.put(base + 2, data.lantern());
        out.put(base + 3, data.throwableItem());
        out.put(base + 4, !safe.getEnchantments().isEmpty());
        var charged = safe.get(DataComponents.CHARGED_PROJECTILES);
        out.put(base + 5, charged != null && !charged.isEmpty());
        out.put(base + 6, player != null && player.getCooldowns().isOnCooldown(safe));
        out.put(base + 7, false);
        out.put(base + 8, false);
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
                item instanceof ProjectileItem
        );
    }

    private static String handName(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand";
    }

    private record StaticItemData(String id, List<String> tags, boolean block, boolean lantern,
                                  boolean throwableItem) {
    }
}
