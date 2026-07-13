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
            Map.entry(Enchantments.PROTECTION, new EnchantMeta("protection", "armor")),
            Map.entry(Enchantments.FIRE_PROTECTION, new EnchantMeta("fire_protection", "armor")),
            Map.entry(Enchantments.FEATHER_FALLING, new EnchantMeta("feather_falling", "armor")),
            Map.entry(Enchantments.BLAST_PROTECTION, new EnchantMeta("blast_protection", "armor")),
            Map.entry(Enchantments.PROJECTILE_PROTECTION, new EnchantMeta("projectile_protection", "armor")),
            Map.entry(Enchantments.RESPIRATION, new EnchantMeta("respiration", "armor")),
            Map.entry(Enchantments.AQUA_AFFINITY, new EnchantMeta("aqua_affinity", "armor")),
            Map.entry(Enchantments.THORNS, new EnchantMeta("thorns", "armor")),
            Map.entry(Enchantments.DEPTH_STRIDER, new EnchantMeta("depth_strider", "armor")),
            Map.entry(Enchantments.FROST_WALKER, new EnchantMeta("frost_walker", "armor")),
            Map.entry(Enchantments.SOUL_SPEED, new EnchantMeta("soul_speed", "armor")),
            Map.entry(Enchantments.SWIFT_SNEAK, new EnchantMeta("swift_sneak", "armor")),
            Map.entry(Enchantments.BINDING_CURSE, new EnchantMeta("binding_curse", "armor")),

            /* ================= MELEE ================= */
            Map.entry(Enchantments.SHARPNESS, new EnchantMeta("sharpness", "melee")),
            Map.entry(Enchantments.SMITE, new EnchantMeta("smite", "melee")),
            Map.entry(Enchantments.BANE_OF_ARTHROPODS, new EnchantMeta("bane_of_arthropods", "melee")),
            Map.entry(Enchantments.KNOCKBACK, new EnchantMeta("knockback", "melee")),
            Map.entry(Enchantments.FIRE_ASPECT, new EnchantMeta("fire_aspect", "melee")),
            Map.entry(Enchantments.LOOTING, new EnchantMeta("looting", "melee")),
            Map.entry(Enchantments.SWEEPING_EDGE, new EnchantMeta("sweeping_edge", "melee")),

            /* ================= TOOLS ================= */
            Map.entry(Enchantments.EFFICIENCY, new EnchantMeta("efficiency", "tools")),
            Map.entry(Enchantments.SILK_TOUCH, new EnchantMeta("silk_touch", "tools")),
            Map.entry(Enchantments.FORTUNE, new EnchantMeta("fortune", "tools")),

            /* ================= BOW ================= */
            Map.entry(Enchantments.POWER, new EnchantMeta("power", "bow")),
            Map.entry(Enchantments.PUNCH, new EnchantMeta("punch", "bow")),
            Map.entry(Enchantments.FLAME, new EnchantMeta("flame", "bow")),
            Map.entry(Enchantments.INFINITY, new EnchantMeta("infinity", "bow")),

            /* ================= FISHING ================= */
            Map.entry(Enchantments.LUCK_OF_THE_SEA, new EnchantMeta("luck_of_the_sea", "fishing")),
            Map.entry(Enchantments.LURE, new EnchantMeta("lure", "fishing")),

            /* ================= TRIDENT ================= */
            Map.entry(Enchantments.LOYALTY, new EnchantMeta("loyalty", "trident")),
            Map.entry(Enchantments.IMPALING, new EnchantMeta("impaling", "trident")),
            Map.entry(Enchantments.RIPTIDE, new EnchantMeta("riptide", "trident")),
            Map.entry(Enchantments.CHANNELING, new EnchantMeta("channeling", "trident")),

            /* ================= CROSSBOW ================= */
            Map.entry(Enchantments.MULTISHOT, new EnchantMeta("multishot", "crossbow")),
            Map.entry(Enchantments.QUICK_CHARGE, new EnchantMeta("quick_charge", "crossbow")),
            Map.entry(Enchantments.PIERCING, new EnchantMeta("piercing", "crossbow")),

            /* ================= MACE ================= */
            Map.entry(Enchantments.DENSITY, new EnchantMeta("density", "mace")),
            Map.entry(Enchantments.BREACH, new EnchantMeta("breach", "mace")),
            Map.entry(Enchantments.WIND_BURST, new EnchantMeta("wind_burst", "mace")),

            /* ================= OTHER ================= */
            Map.entry(Enchantments.LUNGE, new EnchantMeta("lunge", "other")),
            Map.entry(Enchantments.MENDING, new EnchantMeta("mending", "other")),
            Map.entry(Enchantments.UNBREAKING, new EnchantMeta("unbreaking", "other")),
            Map.entry(Enchantments.VANISHING_CURSE, new EnchantMeta("vanishing_curse", "other"))
    );
}
