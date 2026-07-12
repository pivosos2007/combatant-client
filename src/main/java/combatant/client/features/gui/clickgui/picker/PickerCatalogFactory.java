/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.picker;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.util.item.EnchantMeta;
import combatant.client.util.item.EnchantRegistry;
import combatant.client.util.screen.ScreenCatalog;

import java.util.*;

public enum PickerCatalogFactory {
    ;
    private static final Comparator<PickerEntryData> ENTRY_ORDER = Comparator
            .comparing(PickerEntryData::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PickerEntryData::id, String.CASE_INSENSITIVE_ORDER);

    public static PickerCatalog forMode(TextListSetting.PickerMode mode) {
        if (mode == null) return owner -> List.of();
        return switch (mode) {
            case SCREENS -> PickerCatalogFactory::screenEntries;
            case BLOCKS -> owner -> blockEntries();
            case ITEMS -> owner -> itemEntries();
            case EQUIPPABLE_ARMOR -> owner -> equippableArmorEntries();
            case ENCHANTMENTS -> owner -> enchantmentEntries();
            case ALL -> owner -> allEntries();
            case SOUNDS -> owner -> soundEntries();
            case LIVING_ENTITIES -> owner -> livingEntityEntries();
            case ENTITIES -> owner -> entityEntries();
            case TEXT -> owner -> List.of();
        };
    }

    private static List<PickerEntryData> screenEntries(TextListSetting owner) {
        Set<String> selected = owner == null ? Set.of() : owner.getValueSet();
        List<PickerEntryData> out = new ArrayList<>();
        for (ScreenCatalog.Entry entry : ScreenCatalog.entries(selected)) {
            out.add(new PickerEntryData(entry.getId(), entry.label(), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> blockEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String label = I18n.get(block.getDescriptionId());
            ItemStack stack = block.asItem().getDefaultInstance();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> itemEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack stack = item.getDefaultInstance();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> equippableArmorEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!isArmorEquippable(stack)) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> enchantmentEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EnchantMeta meta : EnchantRegistry.REGISTRY.values()) {
            String id = "minecraft:" + meta.key();
            out.add(new PickerEntryData(id, humanizeSnakeCase(meta.key()), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> allEntries() {
        Set<String> seen = new LinkedHashSet<>();
        List<PickerEntryData> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!seen.add(id)) continue;
            String label = I18n.get(block.getDescriptionId());
            ItemStack stack = block.asItem().getDefaultInstance();
            out.add(new PickerEntryData(id, label, stack));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!seen.add(id)) continue;
            ItemStack stack = item.getDefaultInstance();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> soundEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (SoundEvent sound : BuiltInRegistries.SOUND_EVENT) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id == null) continue;
            out.add(new PickerEntryData(id.toString(), soundLabel(id), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> livingEntityEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Class<?> baseClass = type.getBaseClass();
            if (baseClass == null || !net.minecraft.world.entity.LivingEntity.class.isAssignableFrom(baseClass))
                continue;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            String label = I18n.get(type.getDescriptionId());
            out.add(new PickerEntryData(id.toString(), label, ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> entityEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            String label = I18n.get(type.getDescriptionId());
            out.add(new PickerEntryData(id.toString(), label, ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static String humanizeSnakeCase(String id) {
        if (id == null || id.isBlank()) return "";
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(id.length() + 4);
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part, 1, part.length());
        }
        return out.toString();
    }

    private static String soundLabel(Identifier id) {
        if (id == null) return "";
        String label = humanizeSnakeCase(id.getPath().replace('.', '_'));
        if ("minecraft".equals(id.getNamespace())) return label;
        return id.getNamespace() + ": " + label;
    }

    private static boolean isArmorEquippable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot slot = eq.slot();
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }
}
