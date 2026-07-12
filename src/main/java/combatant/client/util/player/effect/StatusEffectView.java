/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Syncs status effects from client entities (no raw packets) and provides HUD-friendly subsets.
 */
public enum StatusEffectView {
    ;

    private static final List<Holder<MobEffect>> HUD_EFFECTS = List.of(
            MobEffects.STRENGTH,
            MobEffects.FIRE_RESISTANCE,
            MobEffects.SPEED,
            MobEffects.SLOWNESS,
            MobEffects.RESISTANCE,
            MobEffects.ABSORPTION,
            MobEffects.REGENERATION
    );

    public static void sync(Minecraft mc) {
        if (mc == null || mc.level == null) return;
        StatusEffectInference.tick(mc);
        var players = mc.level.players();
        if (players == null || players.isEmpty()) {
            StatusEffectTracker.pruneMissing(java.util.Set.of());
            StatusEffectTracker.tickHeuristics();
            return;
        }
        HashSet<Integer> ids = new HashSet<>(players.size());
        for (Player player : players) {
            if (player == null) continue;
            ids.add(player.getId());
            StatusEffectTracker.syncEntity(player.getId(), player.getActiveEffects());
        }
        StatusEffectTracker.pruneMissing(ids);
        StatusEffectTracker.tickHeuristics();
    }

    public static List<MobEffectInstance> collectHudEffects(Player player) {
        if (player == null) return List.of();
        List<MobEffectInstance> active = new ArrayList<>(StatusEffectAccess.all(player));
        if (active.isEmpty()) return List.of();

        List<MobEffectInstance> out = new ArrayList<>();
        for (Holder<MobEffect> effect : HUD_EFFECTS) {
            for (MobEffectInstance inst : active) {
                if (inst.getEffect().equals(effect)) {
                    out.add(inst);
                    break;
                }
            }
        }
        return out;
    }
}
