/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import combatant.client.util.player.PlayerHealthResolver;
import combatant.client.util.player.effect.StatusEffectInference;
import combatant.client.util.player.effect.StatusEffectTracker;

public enum LethalStrikeCalculator {
    ;

    private static final float FULL_STRENGTH_THRESHOLD = 0.9f;
    private static final float LETHAL_MARGIN = 1.25f;

    public static boolean canKillNow(Player attacker, LivingEntity target, int cooldownTicks) {
        if (attacker == null || target == null || !target.isAlive() || target.isRemoved()) {
            return false;
        }
        if (!target.isAttackable() || target.isInvulnerable()) {
            return false;
        }
        if (target instanceof Player player && hasUnstableDefensiveState(player)) {
            return false;
        }

        float damage = estimateFinalDamage(attacker, target, Math.max(0, cooldownTicks));
        float effectiveHealth = PlayerHealthResolver.resolve(target).totalHealth();
        return damage >= effectiveHealth + LETHAL_MARGIN;
    }

    private static float estimateFinalDamage(Player attacker, LivingEntity target, int cooldownTicks) {
        ItemStack weapon = attacker.getMainHandItem();
        DamageSource source = getDamageSource(attacker, weapon);

        float cooldown = attacker.getAttackStrengthScale(cooldownTicks + 0.5f);
        float cooldownScale = 0.2f + cooldown * cooldown * 0.8f;

        float baseDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float handBase = (float) attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        float weaponBonus = getMainHandAttackDamageBonus(weapon);
        if (weaponBonus > 0.0f && Math.abs(baseDamage - handBase) < 0.0001f) {
            baseDamage += weaponBonus;
        }

        float enchantDamage = getEnchantmentDamage(attacker, target, weapon, source, baseDamage) - baseDamage;
        if (enchantDamage < 0.0f) {
            enchantDamage = 0.0f;
        }

        float damage = baseDamage * cooldownScale + enchantDamage * cooldown;
        damage += weapon.getItem().getAttackDamageBonus(target, damage, source);
        if (!(attacker.level() instanceof ServerLevel)) {
            damage += getClientMaceDensityBonus(attacker, target, weapon, source);
        }

        if (cooldown > FULL_STRENGTH_THRESHOLD && isCriticalHit(attacker, target)) {
            damage *= 1.5f;
        }

        if (damage <= 0.0f) {
            return 0.0f;
        }

        damage = applyArmor(attacker, target, weapon, source, damage);
        damage = applyResistance(target, damage);
        damage = applyProtection(target, source, damage);
        return Math.max(damage, 0.0f);
    }

    private static DamageSource getDamageSource(Player attacker, ItemStack weapon) {
        if (attacker.level() != null) {
            return weapon.getDamageSource(attacker);
        }
        return attacker.damageSources().playerAttack(attacker);
    }

    private static float getEnchantmentDamage(Player attacker,
                                              Entity target,
                                              ItemStack weapon,
                                              DamageSource source,
                                              float baseDamage) {
        if (attacker.level() instanceof ServerLevel serverWorld) {
            return EnchantmentHelper.modifyDamage(serverWorld, weapon, target, source, baseDamage);
        }

        float damage = baseDamage;
        int sharpness = getEnchantmentLevel(attacker, Enchantments.SHARPNESS, weapon);
        if (sharpness > 0) {
            damage += 1.0f + (sharpness - 1) * 0.5f;
        }

        int smite = getEnchantmentLevel(attacker, Enchantments.SMITE, weapon);
        if (smite > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            damage += smite * 2.5f;
        }

        int bane = getEnchantmentLevel(attacker, Enchantments.BANE_OF_ARTHROPODS, weapon);
        if (bane > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            damage += bane * 2.5f;
        }

        return damage;
    }

    private static float applyArmor(Player attacker,
                                    LivingEntity target,
                                    ItemStack weapon,
                                    DamageSource source,
                                    float damage) {
        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        if (attacker.level() instanceof ServerLevel) {
            return CombatRules.getDamageAfterAbsorb(target, damage, source, armor, toughness);
        }

        float f = 2.0f + toughness / 4.0f;
        float g = Mth.clamp(armor - damage / f, armor * 0.2f, 20.0f);
        float armorEffectiveness = getArmorEffectiveness(attacker, weapon, g / 25.0f);
        armorEffectiveness = Mth.clamp(armorEffectiveness, 0.0f, 1.0f);
        return damage * (1.0f - armorEffectiveness);
    }

    private static float getArmorEffectiveness(Player attacker, ItemStack weapon, float baseArmorEffectiveness) {
        int breach = getEnchantmentLevel(attacker, Enchantments.BREACH, weapon);
        if (breach <= 0) {
            return baseArmorEffectiveness;
        }
        return baseArmorEffectiveness - 0.15f * breach;
    }

    private static float getClientMaceDensityBonus(Player attacker,
                                                   Entity target,
                                                   ItemStack weapon,
                                                   DamageSource source) {
        if (!(weapon.getItem() instanceof MaceItem) || !MaceItem.canSmashAttack(attacker)) {
            return 0.0f;
        }

        int density = getEnchantmentLevel(attacker, Enchantments.DENSITY, weapon);
        return density > 0 ? (float) (density * 0.5f * attacker.fallDistance) : 0.0f;
    }

    private static float applyResistance(LivingEntity target, float damage) {
        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance == null) {
            return damage;
        }

        int reduced = 25 - (resistance.getAmplifier() + 1) * 5;
        return Math.max(damage * reduced / 25.0f, 0.0f);
    }

    private static float applyProtection(LivingEntity target, DamageSource source, float damage) {
        float protection = getProtectionAmount(target);
        if (protection <= 0.0f || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return damage;
        }
        return CombatRules.getDamageAfterMagicAbsorb(damage, protection);
    }

    private static float getProtectionAmount(LivingEntity target) {
        float total = 0.0f;
        total += getEnchantmentLevel(target, Enchantments.PROTECTION, target.getItemBySlot(EquipmentSlot.HEAD));
        total += getEnchantmentLevel(target, Enchantments.PROTECTION, target.getItemBySlot(EquipmentSlot.CHEST));
        total += getEnchantmentLevel(target, Enchantments.PROTECTION, target.getItemBySlot(EquipmentSlot.LEGS));
        total += getEnchantmentLevel(target, Enchantments.PROTECTION, target.getItemBySlot(EquipmentSlot.FEET));
        return total;
    }

    private static float getMainHandAttackDamageBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0f;
        }

        final double[] add = {0.0};
        final double[] mulBase = {0.0};
        final double[] mulTotal = {1.0};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
                return;
            }
            AttributeModifier.Operation op = modifier.operation();
            double amount = modifier.amount();
            if (op == AttributeModifier.Operation.ADD_VALUE) {
                add[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                mulBase[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                mulTotal[0] *= 1.0 + amount;
            }
        });

        return (float) ((add[0] + mulBase[0]) * mulTotal[0]);
    }

    private static boolean isCriticalHit(Player attacker, Entity target) {
        return attacker.fallDistance > 0.0f
                && !attacker.onGround()
                && !attacker.onClimbable()
                && !attacker.isInWater()
                && !attacker.isMobilityRestricted()
                && !attacker.isPassenger()
                && target instanceof LivingEntity
                && !attacker.isSprinting();
    }

    private static boolean hasUnstableDefensiveState(Player player) {
        if (StatusEffectInference.isDefensiveConsumeLikely(player)
                || StatusEffectInference.hasPendingDefensiveEffect(player)) {
            return true;
        }

        int entityId = player.getId();
        for (MobEffectInstance effect : StatusEffectTracker.get(entityId)) {
            Holder<MobEffect> type = effect.getEffect();
            if (StatusEffectInference.isDefensiveHealthEffect(type)
                    && StatusEffectTracker.isHeuristic(entityId, type)) {
                return true;
            }
        }
        return false;
    }

    private static int getEnchantmentLevel(LivingEntity entity, ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = getEnchantmentEntry(entity, key);
        return entry != null ? EnchantmentHelper.getItemEnchantmentLevel(entry, stack) : 0;
    }

    private static Holder<Enchantment> getEnchantmentEntry(LivingEntity entity, ResourceKey<Enchantment> key) {
        if (entity == null || entity.level() == null) {
            return null;
        }

        Registry<Enchantment> registry = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }
}
