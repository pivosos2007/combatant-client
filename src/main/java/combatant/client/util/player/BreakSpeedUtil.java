/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Break-speed math for an arbitrary hotbar stack, not just the held one.
 *
 * <p>Mirrors {@code Player.getDestroySpeed} and {@code BlockBehaviour.getDestroyProgress} for
 * 26.2. Vanilla reads efficiency from the {@code MINING_EFFICIENCY} attribute, and the server only
 * refreshes equipment attributes during the player's own tick. A tool swapped in for a single packet
 * therefore brings its base speed and correct-tool check but not its Efficiency: that still comes
 * from whatever the server held on its last tick. Callers pass that stack as
 * {@code efficiencySource}; the attribute itself is ignored because the client's copy follows the
 * client-held item, not the server's.</p>
 */
public final class BreakSpeedUtil {

    private BreakSpeedUtil() {
    }

    /** Fraction of the block broken per tick with {@code stack}; 0 = unbreakable, >= 1 = instant. */
    public static float progressPerTick(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState state,
                                        ItemStack stack) {
        return progressPerTick(player, level, pos, state, stack, stack);
    }

    /**
     * Same as {@link #progressPerTick(LocalPlayer, ClientLevel, BlockPos, BlockState, ItemStack)}, but
     * the Efficiency bonus comes from {@code efficiencySource} (the stack whose attributes the server
     * has applied) while speed and correct-tool checks use {@code stack} (the stack in hand).
     */
    public static float progressPerTick(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState state,
                                        ItemStack stack, ItemStack efficiencySource) {
        if (player == null || level == null || state == null || state.isAir()) return 0.0f;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0f) return 0.0f;
        if (hardness == 0.0f) return 1.0f;
        boolean correctTool = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        return destroySpeed(player, level, stack, efficiencySource, state) / hardness / (correctTool ? 30.0f : 100.0f);
    }

    public static float destroySpeed(LocalPlayer player, ClientLevel level, ItemStack stack,
                                     ItemStack efficiencySource, BlockState state) {
        float speed = stack.getDestroySpeed(state);
        if (speed > 1.0f) {
            int efficiency = efficiencyLevel(level, efficiencySource);
            if (efficiency > 0) speed += efficiency * efficiency + 1;
        }

        if (MobEffectUtil.hasDigSpeed(player)) {
            speed *= 1.0f + (MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2f;
        }

        MobEffectInstance fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
        if (fatigue != null) {
            speed *= switch (fatigue.getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1E-4f;
            };
        }

        speed *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            AttributeInstance submerged = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
            if (submerged != null) speed *= (float) submerged.getValue();
        }
        if (!player.onGround()) speed /= 5.0f;
        return speed;
    }

    /** Hotbar slot (0-8) that breaks {@code state} fastest, or -1 when nothing can break it. */
    public static int bestHotbarSlot(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState state) {
        if (player == null || level == null) return -1;
        int bestSlot = -1;
        float bestProgress = 0.0f;
        for (int slot = 0; slot < 9; slot++) {
            float progress = progressPerTick(player, level, pos, state, player.getInventory().getItem(slot));
            if (progress > bestProgress) {
                bestProgress = progress;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static int efficiencyLevel(ClientLevel level, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment efficiency = registry.getValue(Enchantments.EFFICIENCY);
        if (efficiency == null) return 0;
        Holder<Enchantment> holder = registry.wrapAsHolder(efficiency);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }
}
