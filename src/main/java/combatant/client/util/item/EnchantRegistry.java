/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Map;

public enum EnchantRegistry {
    ;

    public static final Map<ResourceKey<Enchantment>, EnchantMeta> REGISTRY = Map.ofEntries(

            /* ================= ARMOR ================= */
            Map.entry(Enchantments.PROTECTION, new EnchantMeta("protection", "За", "armor")),
            Map.entry(Enchantments.FIRE_PROTECTION, new EnchantMeta("fire_protection", "Огн", "armor")),
            Map.entry(Enchantments.FEATHER_FALLING, new EnchantMeta("feather_falling", "Нев", "armor")),
            Map.entry(Enchantments.BLAST_PROTECTION, new EnchantMeta("blast_protection", "Взр", "armor")),
            Map.entry(Enchantments.PROJECTILE_PROTECTION, new EnchantMeta("projectile_protection", "Сн", "armor")),
            Map.entry(Enchantments.RESPIRATION, new EnchantMeta("respiration", "Дых", "armor")),
            Map.entry(Enchantments.AQUA_AFFINITY, new EnchantMeta("aqua_affinity", "Хдб", "armor")),
            Map.entry(Enchantments.THORNS, new EnchantMeta("thorns", "Ши", "armor")),
            Map.entry(Enchantments.DEPTH_STRIDER, new EnchantMeta("depth_strider", "Пдв", "armor")),
            Map.entry(Enchantments.FROST_WALKER, new EnchantMeta("frost_walker", "Лед", "armor")),
            Map.entry(Enchantments.SOUL_SPEED, new EnchantMeta("soul_speed", "Душ", "armor")),
            Map.entry(Enchantments.SWIFT_SNEAK, new EnchantMeta("swift_sneak", "Прв", "armor")),
            Map.entry(Enchantments.BINDING_CURSE, new EnchantMeta("binding_curse", "ПрН", "armor")),

            /* ================= MELEE ================= */
            Map.entry(Enchantments.SHARPNESS, new EnchantMeta("sharpness", "Ос", "melee")),
            Map.entry(Enchantments.SMITE, new EnchantMeta("smite", "Не", "melee")),
            Map.entry(Enchantments.BANE_OF_ARTHROPODS, new EnchantMeta("bane_of_arthropods", "Чл", "melee")),
            Map.entry(Enchantments.KNOCKBACK, new EnchantMeta("knockback", "От", "melee")),
            Map.entry(Enchantments.FIRE_ASPECT, new EnchantMeta("fire_aspect", "Зг", "melee")),
            Map.entry(Enchantments.LOOTING, new EnchantMeta("looting", "До", "melee")),
            Map.entry(Enchantments.SWEEPING_EDGE, new EnchantMeta("sweeping_edge", "Раз", "melee")),

            /* ================= TOOLS ================= */
            Map.entry(Enchantments.EFFICIENCY, new EnchantMeta("efficiency", "Эф", "tools")),
            Map.entry(Enchantments.SILK_TOUCH, new EnchantMeta("silk_touch", "Ше", "tools")),
            Map.entry(Enchantments.FORTUNE, new EnchantMeta("fortune", "Пр", "tools")),

            /* ================= BOW ================= */
            Map.entry(Enchantments.POWER, new EnchantMeta("power", "Уд", "bow")),
            Map.entry(Enchantments.PUNCH, new EnchantMeta("punch", "Си", "bow")),
            Map.entry(Enchantments.FLAME, new EnchantMeta("flame", "От", "bow")),
            Map.entry(Enchantments.INFINITY, new EnchantMeta("infinity", "Во", "bow")),

            /* ================= FISHING ================= */
            Map.entry(Enchantments.LUCK_OF_THE_SEA, new EnchantMeta("luck_of_the_sea", "Бе", "fishing")),
            Map.entry(Enchantments.LURE, new EnchantMeta("lure", "Уд", "fishing")),

            /* ================= TRIDENT ================= */
            Map.entry(Enchantments.LOYALTY, new EnchantMeta("loyalty", "При", "trident")),
            Map.entry(Enchantments.IMPALING, new EnchantMeta("impaling", "Ве", "trident")),
            Map.entry(Enchantments.RIPTIDE, new EnchantMeta("riptide", "Пр", "trident")),
            Map.entry(Enchantments.CHANNELING, new EnchantMeta("channeling", "Тя", "trident")),

            /* ================= CROSSBOW ================= */
            Map.entry(Enchantments.MULTISHOT, new EnchantMeta("multishot", "Гр", "crossbow")),
            Map.entry(Enchantments.QUICK_CHARGE, new EnchantMeta("quick_charge", "ТрВ", "crossbow")),
            Map.entry(Enchantments.PIERCING, new EnchantMeta("piercing", "ПрС", "crossbow")),

            /* ================= MACE ================= */
            Map.entry(Enchantments.DENSITY, new EnchantMeta("density", "Пл", "mace")),
            Map.entry(Enchantments.BREACH, new EnchantMeta("breach", "Прб", "mace")),
            Map.entry(Enchantments.WIND_BURST, new EnchantMeta("wind_burst", "Прв", "mace")),

            /* ================= OTHER ================= */
            Map.entry(Enchantments.LUNGE, new EnchantMeta("lunge", "Рыв", "other")),
            Map.entry(Enchantments.MENDING, new EnchantMeta("mending", "Поч", "other")),
            Map.entry(Enchantments.UNBREAKING, new EnchantMeta("unbreaking", "Прч", "other")),
            Map.entry(Enchantments.VANISHING_CURSE, new EnchantMeta("vanishing_curse", "ПУ", "other"))
    );
}
