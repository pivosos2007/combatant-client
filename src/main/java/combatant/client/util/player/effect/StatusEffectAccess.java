/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.effect;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads effect state from the per-entity tracker before the entity's own effect map.
 * A tracked instance takes precedence for individual lookups; bulk reads return
 * the tracked list when nonempty, otherwise a copy of active entity effects.
 */
public enum StatusEffectAccess {
    ;

    public static boolean has(LivingEntity entity, Holder<MobEffect> effect) {
        return get(entity, effect) != null;
    }

    /**
     * @return the first matching tracked instance, the entity's active effect,
     *         or null when no effect is available or an argument is null
     */
    public static MobEffectInstance get(LivingEntity entity, Holder<MobEffect> effect) {
        if (entity == null || effect == null) {
            return null;
        }

        for (MobEffectInstance instance : StatusEffectTracker.get(entity.getId())) {
            if (instance.getEffect().equals(effect)) {
                return instance;
            }
        }

        return entity.getEffect(effect);
    }

    public static List<MobEffectInstance> all(LivingEntity entity) {
        if (entity == null) {
            return List.of();
        }

        List<MobEffectInstance> tracked = StatusEffectTracker.get(entity.getId());
        if (!tracked.isEmpty()) {
            return tracked;
        }

        return new ArrayList<>(entity.getActiveEffects());
    }
}
