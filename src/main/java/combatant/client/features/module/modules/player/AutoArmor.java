/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;

import java.util.Set;

//todo Description
@ModuleInfo(
        id = "autoarmor",
        displayName = "AutoArmor",
        category = ModuleCategory.PLAYER
)
public class AutoArmor extends Module {

    private static final String SETTING_MODE = "mode";
    private static final String SETTING_ARMOR_ITEMS = "armor_items";
    private final EnumValue<Mode> mode =
            enumSetting("autoArmorMode", SETTING_MODE, Mode.AUTO, Mode.values());
    private final ItemIdSetValue armorItems =
            visibleWhen(itemList("armor_items", SETTING_ARMOR_ITEMS, TextListSetting.PickerMode.EQUIPPABLE_ARMOR),
                    () -> mode.get() == Mode.WHITELIST);
    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null || mc.level == null) return;
        if (ClientScreen.current() != null && !(ClientScreen.current() instanceof InventoryScreen))
            return;
        if (mc.player.inventoryMenu == null) return;

        int syncId = mc.player.inventoryMenu.containerId;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;

            ItemStack equipped = mc.player.getItemBySlot(slot);
            if (slot == EquipmentSlot.CHEST && equipped.is(Items.ELYTRA)) continue;

            Candidate best = findBestCandidate(slot);
            if (best != null && isBetter(best.stack, equipped, slot)) {
                int from = InventorySwap.mapInventoryToScreenSlot(best.invSlot);
                int to = armorScreenSlot(slot);
                InventorySwap.INSTANCE.swapScreenSlots(from, to);
                return;
            }

            if (isBroken(equipped)) {
                if (best != null) {
                    int from = InventorySwap.mapInventoryToScreenSlot(best.invSlot);
                    int to = armorScreenSlot(slot);
                    InventorySwap.INSTANCE.swapScreenSlots(from, to);
                    return;
                }
                int armorSlot = armorScreenSlot(slot);
                if (hasInventorySpace()) {
                    mc.gameMode.handleContainerInput(syncId, armorSlot, 0, ContainerInput.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
    }

    private Candidate findBestCandidate(EquipmentSlot slot) {
        if (mc.player == null) return null;
        Set<String> whitelist = armorItems.get();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            if (!isEquippableForSlot(stack, slot)) continue;
            if (isBroken(stack)) continue;
            if (hasCurseOfBinding(stack)) continue;
            if (mode.get() == Mode.WHITELIST && !isWhitelisted(stack, whitelist)) continue;

            double score = scoreArmor(stack, slot);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot >= 0 ? new Candidate(bestSlot, mc.player.getInventory().getItem(bestSlot).copy()) : null;
    }

    private boolean isWhitelisted(ItemStack stack, Set<String> ids) {
        if (ids == null || ids.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && ids.contains(id.toString());
    }

    private boolean isEquippableForSlot(ItemStack stack, EquipmentSlot slot) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot() == slot;
    }

    private boolean isBetter(ItemStack candidate, ItemStack current, EquipmentSlot slot) {
        if (candidate == null || candidate.isEmpty()) return false;
        if (current == null || current.isEmpty()) return true;
        if (hasCurseOfBinding(current)) return false;
        return scoreArmor(candidate, slot) > scoreArmor(current, slot);
    }

    private double scoreArmor(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return Double.NEGATIVE_INFINITY;

        double[] armor = new double[]{0.0};
        double[] toughness = new double[]{0.0};
        double[] kbResist = new double[]{0.0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) {
                armor[0] += modifier.amount();
            } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                toughness[0] += modifier.amount();
            } else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
                kbResist[0] += modifier.amount();
            }
        });

        int protection = getEnchantmentLevel(Enchantments.PROTECTION, stack);
        int blast = getEnchantmentLevel(Enchantments.BLAST_PROTECTION, stack);
        int projectile = getEnchantmentLevel(Enchantments.PROJECTILE_PROTECTION, stack);
        int fire = getEnchantmentLevel(Enchantments.FIRE_PROTECTION, stack);
        int unbreaking = getEnchantmentLevel(Enchantments.UNBREAKING, stack);
        int mending = getEnchantmentLevel(Enchantments.MENDING, stack);

        return armor[0] * 10.0
                + toughness[0] * 4.0
                + kbResist[0] * 2.0
                + protection * 1.4
                + blast * 0.6
                + projectile * 0.4
                + fire * 0.3
                + unbreaking * 0.1
                + mending * 0.2;
    }

    private boolean hasCurseOfBinding(ItemStack stack) {
        Holder<Enchantment> curse = getEnchantmentEntry(Enchantments.BINDING_CURSE);
        return curse != null && EnchantmentHelper.getItemEnchantmentLevel(curse, stack) > 0;
    }

    private int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = getEnchantmentEntry(key);
        return entry == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(entry, stack);
    }

    private Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        if (mc.level == null) return null;
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private boolean isBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        int max = stack.getMaxDamage();
        if (max <= 0) return false;
        return (double) stack.getDamageValue() / max > 0.98;
    }

    private boolean hasInventorySpace() {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private int armorScreenSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> 5;
            case CHEST -> 6;
            case LEGS -> 7;
            case FEET -> 8;
            default -> -1;
        };
    }

    public enum Mode {
        AUTO,
        WHITELIST
    }

    private record Candidate(int invSlot, ItemStack stack) {
    }
}
