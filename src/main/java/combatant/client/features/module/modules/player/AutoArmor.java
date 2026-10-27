/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.modules.player.autoarmor.ProtectionFocus;
import combatant.client.util.item.EnchantUtil;
import combatant.client.util.player.inventory.InventoryActionKind;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.PacketWindow;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Set;

/**
 * Keeps the best armor on. Equipping is a single shift-click of the better piece into an empty armor slot; a worn
 * piece that has to make room is shift-clicked out first (one click per attempt, the next tick re-evaluates).
 * Without an open inventory a click needs a stationary player, so nothing is sent while moving.
 */
@ModuleInfo(
        id = "autoarmor",
        displayName = "AutoArmor",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.autoarmor.description")
public class AutoArmor extends Module {

    private static final String SETTING_MODE = "mode";
    private static final String SETTING_ARMOR_ITEMS = "armor_items";
    private static final double ELYTRA_PREFERRED_SCORE = 10_000.0;

    private final EnumValue<Mode> mode =
            enumSetting("autoArmorMode", SETTING_MODE, Mode.AUTO, Mode.values());
    private final ItemIdSetValue armorItems =
            visibleWhen(itemList("armor_items", SETTING_ARMOR_ITEMS, TextListSetting.PickerMode.EQUIPPABLE_ARMOR),
                    () -> mode.get() == Mode.WHITELIST);
    private final EnumValue<ProtectionFocus> protectionFocus =
            enumSetting("autoArmorProtectionFocus", "protection_focus", ProtectionFocus.BALANCED, ProtectionFocus.values());
    private final EnumValue<ChestPiece> chestPiece =
            enumSetting("autoArmorChestPiece", "chest_piece", ChestPiece.KEEP_ELYTRA, ChestPiece.values());
    private final NumberValue<Float> minImprovement =
            num("autoArmorMinImprovement", "min_improvement", 0.0f, 0.0f, 5.0f);
    private final NumberValue<Integer> durabilityWeight =
            num("autoArmorDurabilityWeight", "durability_weight", 30, 0, 100);
    private final NumberValue<Integer> brokenPercent =
            num("autoArmorBrokenPercent", "broken_percent", 98, 50, 100);
    private final BooleanValue unequipBroken = bool("autoArmorUnequipBroken", "unequip_broken", true);
    private final NumberValue<Integer> delayTicks = num("autoArmorDelayTicks", "delay_ticks", 0, 0, 20);
    private final BooleanValue inventoryOnly = bool("autoArmorInventoryOnly", "inventory_only", false);
    private final Minecraft mc = Minecraft.getInstance();
    private int cooldown;

    @Override
    public void onEnable() {
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.cancelQueuedInventoryActions(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null || mc.level == null) return;
        boolean inventoryOpen = ClientScreen.current() instanceof InventoryScreen;
        if (ClientScreen.current() != null && !inventoryOpen) return;
        if (inventoryOnly.get() && !inventoryOpen) return;
        if (mc.player.inventoryMenu == null) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (InventorySwap.INSTANCE.hasQueuedInventoryAction(this, InventoryActionKind.INVENTORY_CLICK)) return;
        // No inventory screen: the click is sent as a plain window click, which Grim flags on a moving player.
        if (!inventoryOpen && !PacketWindow.INSTANCE.stationaryForClick()) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            if (handleSlot(slot)) {
                cooldown = delayTicks.get();
                return;
            }
        }
    }

    /** Sends at most one click for {@code slot}; true when one was sent or queued. */
    private boolean handleSlot(EquipmentSlot slot) {
        ItemStack equipped = mc.player.getItemBySlot(slot);
        if (slot == EquipmentSlot.CHEST && equipped.is(Items.ELYTRA) && chestPiece.get() == ChestPiece.KEEP_ELYTRA
                && !isBroken(equipped)) {
            return false;
        }

        int best = findBestCandidate(slot);
        boolean replace = best >= 0 && (isBroken(equipped) || isBetter(mc.player.getInventory().getItem(best), equipped, slot));
        if (replace) {
            if (equipped.isEmpty()) return quickMoveInventorySlot(best);
            if (hasCurseOfBinding(equipped)) return false;
            // The armor slot is taken: shift-clicking the candidate would not equip it, so make room first.
            if (hasInventorySpace()) return quickMoveArmorSlot(slot);
            return swapIntoArmorSlot(best, slot);
        }

        if (!equipped.isEmpty() && isBroken(equipped) && unequipBroken.get() && !hasCurseOfBinding(equipped)
                && hasInventorySpace()) {
            return quickMoveArmorSlot(slot);
        }
        return false;
    }

    private boolean quickMoveInventorySlot(int inventorySlot) {
        int screenSlot = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        return screenSlot >= 0 && InventorySwap.INSTANCE.quickMoveSlot(screenSlot, this);
    }

    private boolean quickMoveArmorSlot(EquipmentSlot slot) {
        return InventorySwap.INSTANCE.quickMoveSlot(armorScreenSlot(slot), this);
    }

    /** No room to take the worn piece off: exchange the two stacks with the pickup swap. */
    private boolean swapIntoArmorSlot(int inventorySlot, EquipmentSlot slot) {
        int from = InventorySwap.mapInventoryToScreenSlot(inventorySlot);
        return from >= 0 && InventorySwap.INSTANCE.swapScreenSlots(from, armorScreenSlot(slot), this);
    }

    /** Best wearable piece for {@code slot} in the inventory (0-35), or -1. Only real armor (or a wanted elytra). */
    private int findBestCandidate(EquipmentSlot slot) {
        Set<String> whitelist = armorItems.get();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty() || !isEquippableForSlot(stack, slot)) continue;
            if (isBroken(stack) || hasCurseOfBinding(stack)) continue;
            if (!isWantedKind(stack, slot)) continue;
            if (mode.get() == Mode.WHITELIST && !isWhitelisted(stack, whitelist)) continue;

            double score = score(stack, slot);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Elytra only when the chest option asks for it; everything else must really protect (armor or toughness). */
    private boolean isWantedKind(ItemStack stack, EquipmentSlot slot) {
        if (stack.is(Items.ELYTRA)) return slot == EquipmentSlot.CHEST && chestPiece.get() == ChestPiece.ELYTRA;
        return isRealArmor(stack, slot);
    }

    private boolean isRealArmor(ItemStack stack, EquipmentSlot slot) {
        double[] total = {0.0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR) || attribute.is(Attributes.ARMOR_TOUGHNESS)) total[0] += modifier.amount();
        });
        return total[0] > 0.0;
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
        if (slot == EquipmentSlot.CHEST && candidate.is(Items.ELYTRA)) return !current.is(Items.ELYTRA);
        return score(candidate, slot) > score(current, slot) + minImprovement.get() * ProtectionFocus.ARMOR_WEIGHT;
    }

    private double score(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return Double.NEGATIVE_INFINITY;
        if (stack.is(Items.ELYTRA)) {
            return chestPiece.get() == ChestPiece.ELYTRA ? ELYTRA_PREFERRED_SCORE : Double.NEGATIVE_INFINITY;
        }

        double[] armor = {0.0};
        double[] toughness = {0.0};
        double[] kbResist = {0.0};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) {
                armor[0] += modifier.amount();
            } else if (attribute.is(Attributes.ARMOR_TOUGHNESS)) {
                toughness[0] += modifier.amount();
            } else if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
                kbResist[0] += modifier.amount();
            }
        });

        double base = protectionFocus.get().score(armor[0], toughness[0], kbResist[0],
                level(Enchantments.PROTECTION, stack), level(Enchantments.BLAST_PROTECTION, stack),
                level(Enchantments.PROJECTILE_PROTECTION, stack), level(Enchantments.FIRE_PROTECTION, stack),
                level(Enchantments.UNBREAKING, stack), level(Enchantments.MENDING, stack));
        return base * ProtectionFocus.durabilityFactor(stack.getDamageValue(), stack.getMaxDamage(),
                durabilityWeight.get() / 100.0);
    }

    private boolean hasCurseOfBinding(ItemStack stack) {
        return level(Enchantments.BINDING_CURSE, stack) > 0;
    }

    private int level(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key,
                      ItemStack stack) {
        return EnchantUtil.level(mc.level, key, stack);
    }

    private boolean isBroken(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return ProtectionFocus.isWorn(stack.getDamageValue(), stack.getMaxDamage(), brokenPercent.get());
    }

    private boolean hasInventorySpace() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) return true;
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

    /** What the chest slot holds: leave a worn elytra alone, always the best chestplate, or an elytra. */
    public enum ChestPiece implements EnumValue.IdProvider {
        KEEP_ELYTRA("keep_elytra"),
        CHESTPLATE("chestplate"),
        ELYTRA("elytra");

        private final String id;

        ChestPiece(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
