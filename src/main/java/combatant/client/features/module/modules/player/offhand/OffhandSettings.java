/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.offhand;

import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Every Offhand setting the helper components read; the module declares them, the components only read. */
public record OffhandSettings(
        NumberValue<Float> healthThreshold,
        NumberValue<Float> elytraHealth,
        NumberValue<Float> crystalDistance,
        BooleanValue fallCheck,
        BooleanValue saveTaliks,
        BooleanMapValue threats,
        BooleanMapValue fallbacks,
        NumberValue<Integer> shieldFallbackPriority,
        NumberValue<Integer> crystalFallbackPriority,
        NumberValue<Integer> enchantedFallbackPriority,
        NumberValue<Integer> gappleFallbackPriority,
        NumberValue<Integer> foodFallbackPriority,
        BooleanValue passiveSwaps,
        BooleanValue crystalWhenSafe,
        NumberValue<Float> crystalSafeHealth,
        BooleanMapValue itemProfiles,
        EnumValue<SafeOffhand> safeOffhand,
        EnumValue<ConsumeHand> consumeHand,
        EnumValue<CombatConsume> consumeCombat,
        NumberValue<Integer> consumeCooldown,
        BooleanValue autoConsume,
        EnumValue<ConsumeControl> consumeControl,
        BooleanValue useGapples,
        NumberValue<Float> gappleHealth,
        BooleanValue useFood,
        NumberValue<Integer> hungerThreshold,
        NumberValue<Float> foodEmergencyHealth,
        EnumValue<EatMode> eatMode,
        BooleanValue returnSlot,
        BooleanValue inventoryRefill,
        BooleanValue refillOpenInventory,
        NumberValue<Integer> refillMinDelay,
        NumberValue<Integer> refillMaxDelay,
        NumberValue<Integer> refillHotbarSlot,
        BooleanValue refillSaveEnchanted,
        BooleanValue refillPreferHotbar,
        BooleanValue refillCloseAfter) {

    public static final String THREAT_BROKEN_ARMOR = "broken_armor";
    public static final String THREAT_MACE = "mace";
    public static final String THREAT_PROJECTILES = "projectiles";
    public static final String FALLBACK_SHIELD = "shield";
    public static final String FALLBACK_CRYSTAL = "crystal";
    public static final String FALLBACK_ENCHANTED = "enchanted_golden_apple";
    public static final String FALLBACK_GAPPLE = "golden_apple";
    public static final String FALLBACK_FOOD = "food";
    public static final String PROFILE_TOTEM = "totem";
    public static final String PROFILE_SHIELD = "shield";
    public static final String PROFILE_CRYSTAL = "crystal";
    public static final String PROFILE_GAPPLE = "golden_apple";
    public static final String PROFILE_ENCHANTED = "enchanted_golden_apple";
    public static final String PROFILE_FOOD = "food";

    public enum SafeOffhand implements EnumValue.IdProvider {
        KEEP("keep"), TOTEM("totem"), CONTEXTUAL("contextual"), SHIELD("shield"),
        CRYSTAL("crystal"), GAPPLE("gapple"), FOOD("food");
        private final String id;
        SafeOffhand(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    public enum ConsumeControl implements EnumValue.IdProvider {
        AUTO("auto"), SEMI("semi"), DISABLED("disabled");
        private final String id;
        ConsumeControl(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    public enum ConsumeHand implements EnumValue.IdProvider {
        MAIN_HAND("main_hand"), OFF_HAND("off_hand"), AUTO("auto");
        private final String id;
        ConsumeHand(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    public enum CombatConsume implements EnumValue.IdProvider {
        OUT_OF_COMBAT("out_of_combat"), EMERGENCY_ONLY("emergency_only"), ALLOW("allow");
        private final String id;
        CombatConsume(String id) { this.id = id; }
        @Override public String id() { return id; }
    }

    @Getter
    @RequiredArgsConstructor
    public enum EatMode implements EnumValue.IdProvider {
        INSTANT("instant"),
        EXACT("exact");

        private final String id;
    }
}
