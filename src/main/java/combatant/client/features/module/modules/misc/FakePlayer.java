/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.UUID;

//todo Description
@ModuleInfo(
        id = "fakeplayer",
        displayName = "FakePlayer",
        category = ModuleCategory.MISC
)
public class FakePlayer extends Module {

    private static final String SETTING_COPY_INVENTORY = "copy_inventory";
    private static final String SETTING_AUTO_TOTEM = "auto_totem";
    private static final float SMASH_THRESHOLD = 1.5f;
    private static final float SMASH_BASE_DAMAGE_PER_BLOCK = 1.0f;
    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue copyInventory =
            bool("fakePlayerCopyInventory", SETTING_COPY_INVENTORY, false);
    private final BooleanValue autoTotem =
            bool("fakePlayerAutoTotem", SETTING_AUTO_TOTEM, true);
    private static int nextFakeEntityId = -1_000_000;
    private Player fakePlayer;
    private int deathTime;

    public FakePlayer() {
        AttackEntityCallback.EVENT.register((player, world, hand, target, hitResult) -> {
            if (!isEnabled()) return InteractionResult.PASS;
            if (mc.player == null || mc.level == null) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (fakePlayer == null || target != fakePlayer) return InteractionResult.PASS;
            if (!(player instanceof Player attacker)) return InteractionResult.PASS;
            if (fakePlayer.hurtTime > 0) return InteractionResult.PASS;

            handleAttack(attacker, fakePlayer);
            return InteractionResult.PASS;
        });
    }

    /**
     * Extracts the MAINHAND attack-damage modifier amount from an ItemStack.
     * <p>
     * Compatibility fallback for client-side attack callbacks that sometimes observe
     * EntityAttributes.ATTACK_DAMAGE without the weapon modifiers applied.
     */
    private static float getMainHandAttackDamageBonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0f;

        final double[] add = {0.0};
        final double[] mulBase = {0.0};
        final double[] mulTotal = {1.0};

        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) return;
            AttributeModifier.Operation op = modifier.operation();
            double amount = modifier.amount();
            if (op == AttributeModifier.Operation.ADD_VALUE) {
                add[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                mulBase[0] += amount;
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                mulTotal[0] *= (1.0 + amount);
            }
        });

        // For vanilla weapons this is effectively just the ADD_VALUE sum.
        double base = 0.0;
        double value = (base + add[0] + base * mulBase[0]) * mulTotal[0];
        return (float) value;
    }

    @Override
    public void onEnable() {
        if (mc.level == null || mc.player == null) return;

        despawnFake();

        String baseName = mc.player.getGameProfile().name();
        String fakeName = baseName + "_fake";
        fakePlayer = new net.minecraft.client.player.RemotePlayer(
                mc.level,
                new GameProfile(UUID.fromString("66123666-6666-6666-6666-666666666600"), fakeName)
        );

        Vec3 pos = mc.player.position().add(1.0, 0.0, 0.0);
        fakePlayer.snapTo(pos.x, pos.y, pos.z, mc.player.getYRot(), mc.player.getXRot());
        fakePlayer.setYHeadRot(mc.player.getYRot());

        if (copyInventory.get()) {
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().copy());
            fakePlayer.setItemInHand(InteractionHand.OFF_HAND, mc.player.getOffhandItem().copy());

            // OtherClientPlayerEntity primarily renders/uses equipment slots, not the backing inventory indices.
            // Copy both to keep UI and damage/armor calculations consistent.
            ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD).copy();
            ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST).copy();
            ItemStack legs = mc.player.getItemBySlot(EquipmentSlot.LEGS).copy();
            ItemStack feet = mc.player.getItemBySlot(EquipmentSlot.FEET).copy();

            fakePlayer.setItemSlot(EquipmentSlot.HEAD, head);
            fakePlayer.setItemSlot(EquipmentSlot.CHEST, chest);
            fakePlayer.setItemSlot(EquipmentSlot.LEGS, legs);
            fakePlayer.setItemSlot(EquipmentSlot.FEET, feet);

            // Inventory armor indices (vanilla): 36=FEET, 37=LEGS, 38=CHEST, 39=HEAD
            fakePlayer.getInventory().setItem(36, feet.copy());
            fakePlayer.getInventory().setItem(37, legs.copy());
            fakePlayer.getInventory().setItem(38, chest.copy());
            fakePlayer.getInventory().setItem(39, head.copy());

            fakePlayer.setItemSlot(EquipmentSlot.MAINHAND, fakePlayer.getMainHandItem());
            fakePlayer.setItemSlot(EquipmentSlot.OFFHAND, fakePlayer.getOffhandItem());
        }

        fakePlayer.setHealth(fakePlayer.getMaxHealth());
        fakePlayer.setAbsorptionAmount(0f);
        fakePlayer.setId(allocateFakeEntityId());

        mc.level.addEntity(fakePlayer);
        deathTime = 0;
    }

    @Override
    public void onDisable() {
        despawnFake();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (fakePlayer == null) return;

        if (autoTotem.get() && fakePlayer.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
            fakePlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }

        if (fakePlayer.isDeadOrDying()) {
            deathTime++;
            if (deathTime > 10) {
                setEnabled(false);
            }
        } else {
            deathTime = 0;
        }
    }

    private int allocateFakeEntityId() {
        int id = nextFakeEntityId--;
        while (mc.level != null && mc.level.getEntity(id) != null) {
            id = nextFakeEntityId--;
        }
        if (nextFakeEntityId > -1) {
            nextFakeEntityId = -1_000_000;
        }
        return id;
    }

    private void despawnFake() {
        if (fakePlayer == null) return;
        fakePlayer.discard();
        fakePlayer = null;
        deathTime = 0;
    }

    private void handleAttack(Player attacker, Player target) {
        if (mc.level == null) return;


        ItemStack weapon = attacker.getMainHandItem();
        float baseDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);

        float handBase = (float) attacker.getAttributeBaseValue(Attributes.ATTACK_DAMAGE); // обычно 1.0
        float weaponBonus = getMainHandAttackDamageBonus(weapon);

// Если значение атрибута совпадает с базой (без оружия), значит weapon-модификаторы не учлись — добавляем вручную.
        if (weaponBonus > 0.0f && Math.abs(baseDamage - handBase) < 0.0001f) {
            baseDamage += weaponBonus;
        }

        float cooldown = attacker.getAttackStrengthScale(0.5f);
        float cooldownScale = 0.2f + cooldown * cooldown * 0.8f;
        float damage = baseDamage * cooldownScale;

        float effectBonus = 0f;
        if (attacker.hasEffect(MobEffects.STRENGTH)) {
            MobEffectInstance inst = attacker.getEffect(MobEffects.STRENGTH);
            if (inst != null) effectBonus += 3f * (inst.getAmplifier() + 1);
        }
        if (attacker.hasEffect(MobEffects.WEAKNESS)) {
            MobEffectInstance inst = attacker.getEffect(MobEffects.WEAKNESS);
            if (inst != null) effectBonus -= 4f * (inst.getAmplifier() + 1);
        }
        damage += effectBonus * cooldownScale;

        boolean smash = isMaceSmash(attacker, weapon);
        DamageSource source = smash
                ? mc.level.damageSources().mace(attacker)
                : mc.level.damageSources().playerAttack(attacker);

        damage += getSharpnessBonus(weapon) * cooldownScale;
        if (smash) {
            float smashDamage = getSmashDamage(attacker, weapon) * cooldownScale;
            damage += smashDamage;
        }

        boolean critical = isCritical(attacker, cooldown);
        if (critical) {
            damage *= 1.5f;
        }

        if (damage <= 0f) return;

        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float finalDamage = CombatRules.getDamageAfterAbsorb(target, damage, source, armor, toughness);

        applyDamage(target, source, finalDamage);

        if (critical) {
            mc.level.playSound(mc.player, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
        }
        mc.level.playSound(mc.player, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);

        int fireAspect = getEnchantmentLevel(Enchantments.FIRE_ASPECT, weapon);
        if (fireAspect > 0) {
            target.igniteForSeconds(4 * fireAspect);
        }
    }

    private void applyDamage(Player target, DamageSource source, float damage) {
        if (damage <= 0f) return;
        target.handleDamageEvent(source);

        float absorption = target.getAbsorptionAmount();
        float left = damage;
        if (absorption > 0f) {
            float absorbed = Math.min(absorption, left);
            absorption -= absorbed;
            left -= absorbed;
            target.setAbsorptionAmount(absorption);
        }

        if (left > 0f) {
            target.setHealth(target.getHealth() - left);
        }

        if (target.isDeadOrDying() && autoTotem.get()
                && target.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            target.setHealth(10f);
            if (mc.player != null && mc.player.connection != null) {
                new ClientboundEntityEventPacket(target, EntityEvent.PROTECTED_FROM_DEATH)
                        .handle(mc.player.connection);
            }
        }
    }

    private boolean isCritical(Player attacker, float cooldown) {
        if (cooldown < 0.9f) return false;
        if (attacker.onGround()) return false;
        if (attacker.onClimbable()) return false;
        if (attacker.isInWater()) return false;
        if (attacker.isPassenger()) return false;
        return attacker.fallDistance > 0.0f;
    }

    private boolean isMaceSmash(Player attacker, ItemStack weapon) {
        if (!(weapon.getItem() instanceof MaceItem)) return false;
        if (!MaceItem.canSmashAttack(attacker)) return false;
        return attacker.fallDistance > SMASH_THRESHOLD;
    }

    private float getSmashDamage(Player attacker, ItemStack weapon) {
        float perBlock = getSmashDamagePerBlock(weapon);
        float fall = Math.max(0f, (float) (attacker.fallDistance - SMASH_THRESHOLD));
        return perBlock * fall;
    }

    private float getSmashDamagePerBlock(ItemStack weapon) {
        int density = getEnchantmentLevel(Enchantments.DENSITY, weapon);
        return SMASH_BASE_DAMAGE_PER_BLOCK + density * 0.5f;
    }

    private float getSharpnessBonus(ItemStack weapon) {
        int sharp = getEnchantmentLevel(Enchantments.SHARPNESS, weapon);
        if (sharp <= 0) return 0f;
        return 0.5f * sharp + 0.5f;
    }

    private int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = entryOf(key);
        if (entry == null) return 0;
        return EnchantmentHelper.getItemEnchantmentLevel(entry, stack);
    }

    private Holder<Enchantment> entryOf(ResourceKey<Enchantment> key) {
        if (key == null || mc.level == null) return null;
        Registry<Enchantment> registry =
                mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment value = registry.getValue(key);
        if (value == null) return null;
        return registry.wrapAsHolder(value);
    }

}
