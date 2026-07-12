/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public enum FoodUtil {
    ;

    public static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.get(DataComponents.FOOD) != null;
    }

    public static int getFoodLevel(Player player) {
        if (player == null) return 0;
        FoodData hungerManager = player.getFoodData();
        if (hungerManager == null) return 0;
        return hungerManager.getFoodLevel();
    }

    public static float getSaturationLevel(Player player) {
        if (player == null) return 0f;
        FoodData hungerManager = player.getFoodData();
        if (hungerManager == null) return 0f;
        return hungerManager.getSaturationLevel();
    }

    public static int getNutrition(ItemStack stack) {
        Object food = getFoodComponent(stack);
        if (food instanceof FoodProperties fc) {
            return fc.nutrition();
        }
        return food == null ? 0 : readInt(food, "nutrition", "getNutrition");
    }

    public static float getSaturation(ItemStack stack) {
        Object food = getFoodComponent(stack);
        if (food instanceof FoodProperties fc) {
            return fc.saturation();
        }
        return food == null ? 0f : readFloat(food, "saturation", "getSaturation", "getSaturationModifier", "saturationModifier");
    }

    public static boolean canAlwaysEat(ItemStack stack) {
        Object food = getFoodComponent(stack);
        if (food instanceof FoodProperties fc) {
            return readBoolean(fc, "canAlwaysEat", "isAlwaysEdible");
        }
        return food != null && readBoolean(food, "canAlwaysEat", "isAlwaysEdible");
    }

    public static float scoreFood(ItemStack stack) {
        if (!isFood(stack)) return Float.NEGATIVE_INFINITY;
        int nutrition = getNutrition(stack);
        float saturation = getSaturation(stack);
        float score = nutrition * 10.0f + saturation * 20.0f;
        score += scoreEffects(stack);
        return score;
    }

    public static List<MobEffectInstance> getFoodEffects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        List<MobEffectInstance> out = new java.util.ArrayList<>();
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return Collections.emptyList();
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect apply) {
                for (MobEffectInstance inst : apply.effects()) {
                    out.add(new MobEffectInstance(inst));
                }
            }
        }
        return out;
    }

    private static float scoreEffects(ItemStack stack) {
        Object food = getFoodComponent(stack);
        if (food == null) return 0f;
        List<?> effects = readList(food, "effects", "getEffects", "getStatusEffects");
        if (effects.isEmpty()) return 0f;

        float score = 0f;
        for (Object entry : effects) {
            if (entry == null) continue;
            float chance = readFloat(entry, "probability", "getProbability", "getChance");
            if (chance <= 0f) chance = 1f;

            MobEffect effect = extractStatusEffect(entry);
            if (effect == null) {
                score += 2.0f * chance;
                continue;
            }

            MobEffectCategory cat = effect.getCategory();
            float add = switch (cat) {
                case BENEFICIAL -> 6.0f;
                case HARMFUL -> -6.0f;
                default -> 2.0f;
            };
            score += add * chance;
        }
        return score;
    }

    private static MobEffect extractStatusEffect(Object effectEntry) {
        Object obj = readObject(effectEntry, "effect", "getEffect", "getFirst", "getLeft");
        if (obj instanceof MobEffectInstance inst) {
            Object type = inst.getEffect();
            if (type instanceof MobEffect effect) return effect;
            if (type instanceof net.minecraft.core.Holder<?> regEntry) {
                Object v = regEntry.value();
                if (v instanceof MobEffect effect) return effect;
            }
        }

        if (obj instanceof MobEffect effect) {
            return effect;
        }

        if (obj instanceof net.minecraft.core.Holder<?> regEntry) {
            Object v = regEntry.value();
            if (v instanceof MobEffect effect) {
                return effect;
            }
        }

        Object fromInstance = readObject(obj, "getEffectType", "getEffect");
        if (fromInstance instanceof MobEffect effect) {
            return effect;
        }

        Object value = readObject(obj, "value");
        if (value instanceof MobEffect effect) {
            return effect;
        }

        return null;
    }

    private static Object getFoodComponent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.get(DataComponents.FOOD);
    }

    private static int readInt(Object target, String... methods) {
        Number n = readNumber(target, methods);
        return n == null ? 0 : n.intValue();
    }

    private static float readFloat(Object target, String... methods) {
        Number n = readNumber(target, methods);
        return n == null ? 0f : n.floatValue();
    }

    private static boolean readBoolean(Object target, String... methods) {
        Object v = readObject(target, methods);
        return v instanceof Boolean b && b;
    }

    private static Number readNumber(Object target, String... methods) {
        Object v = readObject(target, methods);
        return v instanceof Number ? (Number) v : null;
    }

    private static List<?> readList(Object target, String... methods) {
        Object v = readObject(target, methods);
        if (v instanceof List<?> list) return list;
        return Collections.emptyList();
    }

    private static Object readObject(Object target, String... methods) {
        if (target == null) return null;
        for (String name : methods) {
            try {
                Method m = target.getClass().getMethod(name);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
