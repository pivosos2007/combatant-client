/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySearchScope;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.player.inventory.InventorySwapRequest;
import combatant.client.util.player.inventory.InventorySwapVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

@ModuleInfo(
        id = "automend",
        displayName = "AutoMend",
        aliases = {"AutoExperience", "AutoRepair", "PacketExp"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.automend.description")
public final class AutoMend extends Module {
    private static final int ROTATION_PRIORITY = 45;
    private static final String TARGET_ARMOR = "armor";
    private static final String TARGET_MAIN_HAND = "main_hand";
    private static final String TARGET_OFF_HAND = "off_hand";

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanMapValue targets = group("autoMendTargets", "targets", defaultTargets());
    private final NumberValue<Float> repairBelow =
            num("autoMendRepairBelow", "repair_below", 85.0F, 5.0F, 99.0F);
    private final EnumValue<Mode> mode =
            enumSetting("autoMendMode", "mode", Mode.NORMAL, Mode.values());
    private final NumberValue<Integer> packetBurst =
            visibleWhen(num("autoMendPacketBurst", "packet_burst", 3, 2, 8), () -> mode.get() == Mode.PACKET);
    private final NumberValue<Integer> cooldownTicks =
            num("autoMendCooldownTicks", "cooldown_ticks", 1, 0, 20);

    private int cooldown;
    private boolean pendingRotation;

    @Override
    public void onEnable() {
        resetState(false);
    }

    @Override
    public void onDisable() {
        resetState(true);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.isPaused()) {
            resetState(true);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (pendingRotation || player.isUsingItem() || !needsRepair(player)) return;
        if (!hasExperienceBottle(player)) return;
        if (requiresInventoryClick() && isMovingForInventory(player)) return;

        pendingRotation = true;
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE || !isEnabled() || !pendingRotation) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !needsRepair(player)) {
            pendingRotation = false;
            return;
        }

        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), 90.0F),
                player,
                java.util.List.of(),
                1,
                4.0F,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(this::throwExperience)
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private void throwExperience() {
        pendingRotation = false;

        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.gameMode == null || !needsRepair(player)) return;

        Predicate<ItemStack> predicate = stack -> stack != null && !stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE);
        InventorySwapRequest request = InventorySwapRequest.builder(predicate, () -> useExperience(player))
                .scope(InventorySearchScope.FULL)
                .visibility(InventorySwapVisibility.SILENT)
                .restore(true)
                .policy(InventorySwapPolicy.NONE)
                .actionKind(InventoryActionKind.USE_ITEM)
                .build();

        if (InventorySwap.INSTANCE.execute(request)) {
            cooldown = cooldownTicks.get();
        }
    }

    private void useExperience(LocalPlayer player) {
        int count = mode.get() == Mode.PACKET ? packetBurst.get() : 1;
        for (int i = 0; i < count; i++) {
            InteractionResult result = InventorySwap.INSTANCE.useItem(InteractionHand.MAIN_HAND);
            if (result == null || !result.consumesAction()) break;
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
        }
    }

    private boolean needsRepair(LocalPlayer player) {
        Holder<Enchantment> mending = getEnchantmentEntry(Enchantments.MENDING);
        if (mending == null) return false;

        if (targets.get(TARGET_ARMOR)) {
            if (needsRepair(player.getItemBySlot(EquipmentSlot.HEAD), mending)) return true;
            if (needsRepair(player.getItemBySlot(EquipmentSlot.CHEST), mending)) return true;
            if (needsRepair(player.getItemBySlot(EquipmentSlot.LEGS), mending)) return true;
            if (needsRepair(player.getItemBySlot(EquipmentSlot.FEET), mending)) return true;
        }
        if (targets.get(TARGET_MAIN_HAND) && needsRepair(player.getMainHandItem(), mending)) return true;
        return targets.get(TARGET_OFF_HAND) && needsRepair(player.getOffhandItem(), mending);
    }

    private boolean needsRepair(ItemStack stack, Holder<Enchantment> mending) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) return false;
        if (EnchantmentHelper.getItemEnchantmentLevel(mending, stack) <= 0) return false;
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) return false;
        float remaining = (maxDamage - stack.getDamageValue()) * 100.0F / maxDamage;
        return remaining <= repairBelow.get();
    }

    private Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        if (mc.level == null) return null;
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private boolean hasExperienceBottle(LocalPlayer player) {
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getItem(slot).is(Items.EXPERIENCE_BOTTLE)) return true;
        }
        return false;
    }

    private boolean requiresInventoryClick() {
        Predicate<ItemStack> predicate = stack -> stack != null && !stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE);
        return !InventorySwap.INSTANCE.findHotbar(predicate).found()
                && InventorySwap.INSTANCE.findInventory(predicate).found();
    }

    private boolean isMovingForInventory(LocalPlayer player) {
        if (player.isSprinting()) return true;
        if (player.input == null || player.input.keyPresses == null) return false;
        Input input = player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right();
    }

    private void resetState(boolean releaseRotation) {
        cooldown = 0;
        pendingRotation = false;
        if (releaseRotation) RotationManager.INSTANCE.release(this);
    }

    private static Map<String, Boolean> defaultTargets() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TARGET_ARMOR, true);
        defaults.put(TARGET_MAIN_HAND, true);
        defaults.put(TARGET_OFF_HAND, true);
        return defaults;
    }

    public enum Mode implements EnumValue.IdProvider {
        NORMAL("normal"),
        PACKET("packet");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
