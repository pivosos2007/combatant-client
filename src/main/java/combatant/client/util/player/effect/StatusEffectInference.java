/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;
import combatant.client.util.item.FoodUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum StatusEffectInference {
    ;

    private static final long CONSUME_WINDOW_MS = 3000L;
    private static final long POTION_WINDOW_MS = 2500L;
    private static final double PLAYER_MATCH_DIST = 6.0;
    private static final double POTION_MATCH_DIST = 4.5;

    private static final Map<Integer, UseState> USE_STATE = new HashMap<>();
    private static final Map<Integer, List<PendingEffect>> PENDING = new HashMap<>();
    private static final Map<Integer, PotionPending> POTIONS = new HashMap<>();
    private static long lastTickMs;

    public static void tick(Minecraft mc) {
        if (mc == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTickMs < 15L) return;
        lastTickMs = now;

        List<? extends Player> players = mc.level.players();
        java.util.HashSet<Integer> alive = new java.util.HashSet<>();
        for (Player player : players) {
            if (player == null) continue;
            alive.add(player.getId());
            if (mc.player != null && player.getId() == mc.player.getId()) continue;
            updateUseState(player, now);
        }
        USE_STATE.keySet().removeIf(id -> !alive.contains(id));
        PENDING.keySet().removeIf(id -> !alive.contains(id));
        prunePending(now);
        updatePotionEntities(mc, now);
    }

    public static boolean isParticleNearPlayer(Player player, Vec3 pos) {
        if (player == null || pos == null) return false;
        return player.position().distanceToSqr(pos) <= (PLAYER_MATCH_DIST * PLAYER_MATCH_DIST);
    }

    public static boolean isDefensiveConsumeLikely(Player player) {
        if (player == null) return false;
        if (isConsumableUse(player.isUsingItem(), player.getUseItem())) {
            return true;
        }
        UseState state = USE_STATE.get(player.getId());
        return state != null && isConsumableUse(state.using, state.stack);
    }

    public static boolean hasPendingDefensiveEffect(Player player) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        List<PendingEffect> list = PENDING.get(player.getId());
        if (list != null) {
            for (PendingEffect pending : list) {
                if (pending.expiresAt >= now && isDefensiveHealthEffect(pending.effect)) {
                    return true;
                }
            }
        }

        Vec3 pos = player.position();
        for (PotionPending pending : POTIONS.values()) {
            if (pending.effects.isEmpty()) continue;
            if (pending.pos.distanceToSqr(pos) > POTION_MATCH_DIST * POTION_MATCH_DIST) continue;
            for (PendingEffect effect : pending.effects) {
                if (effect.expiresAt >= now && isDefensiveHealthEffect(effect.effect)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isDefensiveHealthEffect(Holder<MobEffect> effect) {
        return effect != null
                && (effect.equals(MobEffects.RESISTANCE)
                || effect.equals(MobEffects.REGENERATION)
                || effect.equals(MobEffects.ABSORPTION)
                || effect.equals(MobEffects.HEALTH_BOOST)
                || effect.equals(MobEffects.SATURATION));
    }

    public static ResolvedEffect resolve(Player target,
                                         Holder<MobEffect> effect,
                                         Vec3 particlePos) {
        return resolve(target, effect, particlePos, true);
    }

    public static ResolvedEffect resolve(Player target,
                                         Holder<MobEffect> effect,
                                         Vec3 particlePos,
                                         boolean consume) {
        if (target == null || effect == null) return null;
        long now = System.currentTimeMillis();
        PendingEffect direct = findPending(target.getId(), effect, now, consume);
        if (direct != null) {
            int duration = direct.showDuration ? direct.duration : -1;
            return new ResolvedEffect(direct.amplifier, duration, direct.showDuration);
        }
        PendingEffect fromPotion = findPotionPending(effect, particlePos);
        if (fromPotion == null) return null;
        return new ResolvedEffect(fromPotion.amplifier, -1, false);
    }

    private static void updateUseState(Player player, long now) {
        int id = player.getId();
        UseState state = USE_STATE.computeIfAbsent(id, k -> new UseState());
        boolean usingNow = player.isUsingItem();
        if (state.using && !usingNow) {
            ItemStack stack = state.stack;
            ItemUseAnimation action = state.action;
            if (stack != null && !stack.isEmpty()) {
                handleConsume(id, stack, action, now);
            }
        }
        state.using = usingNow;
        if (usingNow) {
            ItemStack active = player.getUseItem();
            state.stack = active == null ? ItemStack.EMPTY : active.copy();
            state.action = active == null ? ItemUseAnimation.NONE : active.getUseAnimation();
        } else {
            state.stack = ItemStack.EMPTY;
            state.action = ItemUseAnimation.NONE;
        }
    }

    private static void handleConsume(int entityId, ItemStack stack, ItemUseAnimation action, long now) {
        if (stack == null || stack.isEmpty()) return;
        boolean drink = action == ItemUseAnimation.DRINK;
        boolean eat = action == ItemUseAnimation.EAT;
        if (!drink && !eat) return;

        if (drink) {
            List<MobEffectInstance> potion = readPotionEffects(stack);
            boolean splash = stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
            if (!potion.isEmpty()) {
                addPending(entityId, potion, !splash, now);
                return;
            }
        }

        if (eat || FoodUtil.isFood(stack)) {
            List<MobEffectInstance> food = FoodUtil.getFoodEffects(stack);
            if (!food.isEmpty()) {
                addPending(entityId, food, true, now);
            }
        }
    }

    private static boolean isConsumableUse(boolean using, ItemStack stack) {
        if (!using || stack == null || stack.isEmpty()) return false;
        ItemUseAnimation action = stack.getUseAnimation();
        return action == ItemUseAnimation.DRINK || action == ItemUseAnimation.EAT || FoodUtil.isFood(stack);
    }

    private static void addPending(int entityId,
                                   List<MobEffectInstance> effects,
                                   boolean showDuration,
                                   long now) {
        if (effects == null || effects.isEmpty()) return;
        long expiresAt = now + CONSUME_WINDOW_MS;
        List<PendingEffect> list = new ArrayList<>(PENDING.getOrDefault(entityId, List.of()));
        for (MobEffectInstance inst : effects) {
            if (inst == null) continue;
            list.add(new PendingEffect(
                    inst.getEffect(),
                    inst.getAmplifier(),
                    inst.getDuration(),
                    showDuration,
                    expiresAt
            ));
        }
        if (list.isEmpty()) {
            PENDING.remove(entityId);
        } else {
            PENDING.put(entityId, list);
        }
    }

    private static PendingEffect findPending(int entityId,
                                             Holder<MobEffect> effect,
                                             long now,
                                             boolean consume) {
        List<PendingEffect> list = PENDING.get(entityId);
        if (list == null || list.isEmpty()) return null;
        PendingEffect best = null;
        for (PendingEffect entry : list) {
            if (entry.expiresAt < now) continue;
            if (!entry.effect.equals(effect)) continue;
            if (best == null || entry.expiresAt > best.expiresAt) {
                best = entry;
            }
        }
        if (best != null && consume) {
            list.remove(best);
            if (list.isEmpty()) {
                PENDING.remove(entityId);
            } else {
                PENDING.put(entityId, list);
            }
        }
        return best;
    }

    private static PendingEffect findPotionPending(Holder<MobEffect> effect, Vec3 pos) {
        if (pos == null) return null;
        double bestDist2 = Double.MAX_VALUE;
        PendingEffect best = null;
        for (PotionPending pending : POTIONS.values()) {
            if (pending.effects.isEmpty()) continue;
            double dist2 = pending.pos.distanceToSqr(pos);
            if (dist2 > POTION_MATCH_DIST * POTION_MATCH_DIST) continue;
            for (PendingEffect entry : pending.effects) {
                if (!entry.effect.equals(effect)) continue;
                if (dist2 < bestDist2) {
                    bestDist2 = dist2;
                    best = entry;
                }
            }
        }
        return best;
    }

    private static void prunePending(long now) {
        if (PENDING.isEmpty()) return;
        List<Integer> empty = new ArrayList<>();
        for (var entry : PENDING.entrySet()) {
            List<PendingEffect> list = entry.getValue();
            list.removeIf(e -> e.expiresAt < now);
            if (list.isEmpty()) empty.add(entry.getKey());
        }
        for (int id : empty) PENDING.remove(id);
    }

    private static void updatePotionEntities(Minecraft mc, long now) {
        if (mc.level == null) return;
        boolean any = false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractThrownPotion potion)) continue;
            ItemStack stack = potion.getItem();
            if (stack == null || stack.isEmpty()) continue;
            List<MobEffectInstance> effects = readPotionEffects(stack);
            if (effects.isEmpty()) continue;

            List<PendingEffect> list = new ArrayList<>();
            for (MobEffectInstance inst : effects) {
                list.add(new PendingEffect(inst.getEffect(), inst.getAmplifier(), inst.getDuration(), false, now + POTION_WINDOW_MS));
            }
            POTIONS.put(entity.getId(), new PotionPending(entity.position(), list, now));
            any = true;
        }
        if (!any && !POTIONS.isEmpty()) {
            // keep previous entries but let them expire
        }
        prunePotions(now);
    }

    private static void prunePotions(long now) {
        if (POTIONS.isEmpty()) return;
        POTIONS.entrySet().removeIf(e -> (now - e.getValue().seenAt) > POTION_WINDOW_MS);
    }

    private static List<MobEffectInstance> readPotionEffects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return List.of();
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance inst : contents.getAllEffects()) {
            if (inst == null) continue;
            out.add(new MobEffectInstance(inst));
        }
        return out;
    }

    private static final class UseState {
        private boolean using;
        private ItemStack stack = ItemStack.EMPTY;
        private ItemUseAnimation action = ItemUseAnimation.NONE;
    }

    private record PendingEffect(Holder<MobEffect> effect,
                                 int amplifier,
                                 int duration,
                                 boolean showDuration,
                                 long expiresAt) {
    }

    private record PotionPending(Vec3 pos, List<PendingEffect> effects, long seenAt) {
    }

    public record ResolvedEffect(int amplifier, int duration, boolean showDuration) {
    }
}
