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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import combatant.client.util.logging.DebugLog;

import java.util.*;

/**
 * Client-side cache of status effects for entities, synced from live entity data
 * and optionally extended by heuristic particle detection.
 */
public enum StatusEffectTracker {
    ;

    private static final Map<Integer, List<MobEffectInstance>> EFFECTS = new HashMap<>();
    private static final Map<Integer, List<Holder<MobEffect>>> HEURISTIC = new HashMap<>();
    private static final Map<Integer, List<Holder<MobEffect>>> HEURISTIC_HIDE_DURATION = new HashMap<>();

    public static void syncEntity(int entityId, Iterable<MobEffectInstance> effects) {
        List<MobEffectInstance> actual = new ArrayList<>();
        if (effects != null) {
            for (MobEffectInstance inst : effects) {
                if (inst == null) continue;
                actual.add(copyInstance(inst));
            }
        }

        List<Holder<MobEffect>> heur = HEURISTIC.get(entityId);
        if (heur != null && !heur.isEmpty()) {
            heur.removeIf(effect -> containsEffect(actual, effect));
            if (heur.isEmpty()) {
                HEURISTIC.remove(entityId);
            } else {
                HEURISTIC.put(entityId, heur);
            }
            List<Holder<MobEffect>> hide = HEURISTIC_HIDE_DURATION.get(entityId);
            if (hide != null) {
                hide.removeIf(effect -> !heur.contains(effect));
                if (hide.isEmpty()) {
                    HEURISTIC_HIDE_DURATION.remove(entityId);
                } else {
                    HEURISTIC_HIDE_DURATION.put(entityId, hide);
                }
            }
        }
        if (heur == null || heur.isEmpty()) {
            HEURISTIC_HIDE_DURATION.remove(entityId);
        }

        if (heur != null && !heur.isEmpty()) {
            List<MobEffectInstance> previous = EFFECTS.get(entityId);
            for (Holder<MobEffect> effect : heur) {
                if (containsEffect(actual, effect)) continue;
                MobEffectInstance cached = findInstance(previous, effect);
                actual.add(cached != null ? cached : new MobEffectInstance(effect, -1, 0, false, true, true));
            }
        }

        if (actual.isEmpty()) {
            EFFECTS.remove(entityId);
        } else {
            EFFECTS.put(entityId, actual);
        }
    }

    public static void apply(int entityId,
                             Holder<MobEffect> effect,
                             int amplifier,
                             int duration,
                             boolean ambient,
                             boolean showParticles,
                             boolean showIcon) {
        apply(entityId, effect, amplifier, duration, ambient, showParticles, showIcon, false);
    }

    public static void apply(int entityId,
                             Holder<MobEffect> effect,
                             int amplifier,
                             int duration,
                             boolean ambient,
                             boolean showParticles,
                             boolean showIcon,
                             boolean heuristic) {
        apply(entityId, effect, amplifier, duration, ambient, showParticles, showIcon, heuristic, true);
    }

    public static void apply(int entityId,
                             Holder<MobEffect> effect,
                             int amplifier,
                             int duration,
                             boolean ambient,
                             boolean showParticles,
                             boolean showIcon,
                             boolean heuristic,
                             boolean hideDuration) {
        DebugLog.info("Effect apply eid=%d (%s) %s amp=%d dur=%d ambient=%s particles=%s icon=%s",
                entityId,
                resolveName(entityId),
                effect.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"),
                amplifier,
                duration,
                ambient,
                showParticles,
                showIcon
        );
        List<MobEffectInstance> list = new ArrayList<>(EFFECTS.getOrDefault(entityId, Collections.emptyList()));
        list.removeIf(inst -> inst.getEffect().equals(effect));
        list.add(new MobEffectInstance(effect, duration, amplifier, ambient, showParticles, showIcon));
        EFFECTS.put(entityId, list);

        List<Holder<MobEffect>> heur = new ArrayList<>(HEURISTIC.getOrDefault(entityId, Collections.emptyList()));
        heur.removeIf(e -> e.equals(effect));
        if (heuristic) heur.add(effect);
        if (heur.isEmpty()) {
            HEURISTIC.remove(entityId);
        } else {
            HEURISTIC.put(entityId, heur);
        }

        List<Holder<MobEffect>> hide = new ArrayList<>(HEURISTIC_HIDE_DURATION.getOrDefault(entityId, Collections.emptyList()));
        hide.removeIf(e -> e.equals(effect));
        if (heuristic && hideDuration) hide.add(effect);
        if (hide.isEmpty()) {
            HEURISTIC_HIDE_DURATION.remove(entityId);
        } else {
            HEURISTIC_HIDE_DURATION.put(entityId, hide);
        }
    }

    /**
     * Returns true if this entity already has the specified effect tracked.
     */
    public static boolean has(int entityId, Holder<MobEffect> effect) {
        List<MobEffectInstance> list = EFFECTS.get(entityId);
        if (list == null) return false;
        for (MobEffectInstance inst : list) {
            if (inst.getEffect().equals(effect)) return true;
        }
        return false;
    }

    /**
     * Returns true if effect is tracked as heuristic (not received via packet).
     */
    public static boolean isHeuristic(int entityId, Holder<MobEffect> effect) {
        List<Holder<MobEffect>> list = HEURISTIC.get(entityId);
        if (list == null) return false;
        for (Holder<MobEffect> e : list) {
            if (e.equals(effect)) return true;
        }
        return false;
    }

    public static boolean shouldHideDuration(int entityId, Holder<MobEffect> effect) {
        List<Holder<MobEffect>> list = HEURISTIC_HIDE_DURATION.get(entityId);
        if (list == null) return false;
        for (Holder<MobEffect> e : list) {
            if (e.equals(effect)) return true;
        }
        return false;
    }

    public static void remove(int entityId, Holder<MobEffect> effect) {
        List<MobEffectInstance> list = EFFECTS.get(entityId);
        if (list == null) return;
        DebugLog.info("Effect remove eid=%d (%s) %s",
                entityId,
                resolveName(entityId),
                effect.unwrapKey().map(k -> k.identifier().toString()).orElse("unknown"));
        list.removeIf(inst -> inst.getEffect().equals(effect));
        if (list.isEmpty()) {
            EFFECTS.remove(entityId);
        } else {
            EFFECTS.put(entityId, list);
        }

        List<Holder<MobEffect>> heur = HEURISTIC.get(entityId);
        if (heur != null) {
            heur.removeIf(e -> e.equals(effect));
            if (heur.isEmpty()) HEURISTIC.remove(entityId);
        }
        List<Holder<MobEffect>> hide = HEURISTIC_HIDE_DURATION.get(entityId);
        if (hide != null) {
            hide.removeIf(e -> e.equals(effect));
            if (hide.isEmpty()) HEURISTIC_HIDE_DURATION.remove(entityId);
        }
    }

    public static List<MobEffectInstance> get(int entityId) {
        List<MobEffectInstance> list = EFFECTS.get(entityId);
        if (list == null) return Collections.emptyList();
        return List.copyOf(list);
    }

    public static void clearEntity(int entityId) {
        EFFECTS.remove(entityId);
        HEURISTIC.remove(entityId);
        HEURISTIC_HIDE_DURATION.remove(entityId);
    }

    public static void pruneMissing(java.util.Set<Integer> aliveIds) {
        if (aliveIds == null || aliveIds.isEmpty()) {
            EFFECTS.clear();
            HEURISTIC.clear();
            HEURISTIC_HIDE_DURATION.clear();
            return;
        }
        EFFECTS.keySet().removeIf(id -> !aliveIds.contains(id));
        HEURISTIC.keySet().removeIf(id -> !aliveIds.contains(id));
        HEURISTIC_HIDE_DURATION.keySet().removeIf(id -> !aliveIds.contains(id));
    }

    /**
     * Called every render frame to prune heuristic effects that stopped emitting particles.
     */
    public static void tick(float partialTicks) {
        StatusEffectHeuristics.pruneStale(System.currentTimeMillis());
    }

    public static void tickHeuristics() {
        StatusEffectHeuristics.pruneStale(System.currentTimeMillis());
    }

    private static String resolveName(int entityId) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "unknown";
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return "unknown";
        if (e instanceof Player p) return p.getName().getString();
        return e.getName().getString();
    }

    private static MobEffectInstance copyInstance(MobEffectInstance inst) {
        return new MobEffectInstance(
                inst.getEffect(),
                inst.getDuration(),
                inst.getAmplifier(),
                inst.isAmbient(),
                inst.isVisible(),
                inst.showIcon()
        );
    }

    private static boolean containsEffect(List<MobEffectInstance> list, Holder<MobEffect> effect) {
        if (list == null || list.isEmpty()) return false;
        for (MobEffectInstance inst : list) {
            if (inst.getEffect().equals(effect)) return true;
        }
        return false;
    }

    private static MobEffectInstance findInstance(List<MobEffectInstance> list, Holder<MobEffect> effect) {
        if (list == null || list.isEmpty()) return null;
        for (MobEffectInstance inst : list) {
            if (inst.getEffect().equals(effect)) return inst;
        }
        return null;
    }
}
