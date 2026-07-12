/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public enum PlayerHealthResolver {
    ;

    private static final float TAB_OVERRIDE_DELTA = 2.0f;

    public static HealthSnapshot resolve(LivingEntity entity) {
        if (entity instanceof Player player) {
            return resolve(player);
        }
        if (entity == null) {
            return new HealthSnapshot(0.0f, 1.0f, 0.0f, false);
        }

        float health = Math.max(0.0f, entity.getHealth());
        float absorption = Math.max(0.0f, entity.getAbsorptionAmount());
        float maxHealth = Math.max(1.0f, entity.getMaxHealth() + absorption);
        return new HealthSnapshot(health + absorption, maxHealth, absorption, false);
    }

    public static HealthSnapshot resolve(Player player) {
        if (player == null) {
            return new HealthSnapshot(0.0f, 1.0f, 0.0f, false);
        }

        float entityHealth = Math.max(0.0f, player.getHealth());
        float entityAbsorption = Math.max(0.0f, player.getAbsorptionAmount());
        float entityTotal = entityHealth + entityAbsorption;
        float entityMax = Math.max(1.0f, player.getMaxHealth() + entityAbsorption);

        float tabHealth = getTabListHealth(player);
        if (!Float.isFinite(tabHealth) || !shouldUseTabHealth(player, entityHealth, entityTotal, tabHealth)) {
            return new HealthSnapshot(entityTotal, entityMax, entityAbsorption, false);
        }

        float resolvedMax = Math.max(Math.max(1.0f, player.getMaxHealth()), tabHealth);
        return new HealthSnapshot(tabHealth, resolvedMax, 0.0f, true);
    }

    public static float totalHealth(LivingEntity entity) {
        return resolve(entity).totalHealth();
    }

    public static float maxHealth(LivingEntity entity) {
        return resolve(entity).maxHealth();
    }

    private static boolean shouldUseTabHealth(Player player, float entityHealth, float entityTotal, float tabHealth) {
        if (!player.isAlive()) {
            return false;
        }
        if (entityHealth <= 0.0f && tabHealth > 0.0f) {
            return true;
        }
        return Math.abs(entityTotal - tabHealth) >= TAB_OVERRIDE_DELTA;
    }

    private static float getTabListHealth(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || player == null) {
            return Float.NaN;
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        if (scoreboard == null) {
            return Float.NaN;
        }

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        if (!isHealthObjective(objective)) {
            return Float.NaN;
        }

        ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(player.getGameProfile()), objective);
        if (score == null) {
            String name = player.getGameProfile() != null ? player.getGameProfile().name() : null;
            if (name == null || name.isBlank()) {
                return Float.NaN;
            }
            score = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective);
        }

        return score == null ? Float.NaN : Math.max(0.0f, score.value());
    }

    private static boolean isHealthObjective(Objective objective) {
        if (objective == null) {
            return false;
        }
        return objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS
                || objective.getCriteria() == ObjectiveCriteria.HEALTH;
    }

    public record HealthSnapshot(float totalHealth, float maxHealth, float absorption, boolean fromTabList) {
    }
}
