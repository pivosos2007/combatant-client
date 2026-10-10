/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.events.Events;
import combatant.client.events.impl.EventTargetChanged;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import combatant.client.features.module.Module;


public enum TargetManager {
    ;

    private static final long DEFAULT_ATTACK_HOLD_MS = 700L;
    private static final EnumMap<Source, TargetState> STATES = new EnumMap<>(Source.class);
    private static final Map<String, ModuleSnapshot> MODULE_STATES = new LinkedHashMap<>();
    private static LivingEntity current;
    private static Source currentSource;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) {
            clearAll();
            return;
        }

        updateCrosshair(mc);
        purgeInvalids();
        refreshCurrent();
    }

    public static LivingEntity getTarget() {
        return current;
    }

    public static LivingEntity getTarget(boolean includeCrosshair) {
        if (includeCrosshair) {
            return current;
        }
        TargetSelection selection = selectCurrent(false);
        return selection.entity();
    }

    public static Source getTargetSource() {
        return currentSource;
    }

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living && TargetingUtil.isValidCombatTarget(living)) {
            push(Source.ATTACK, living, DEFAULT_ATTACK_HOLD_MS);
        }
    }

    public static void setForcedTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.FORCE);
            refreshCurrent();
            return;
        }
        push(Source.FORCE, target, 0L);
        refreshCurrent();
    }

    public static void setModuleTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.MODULE);
            refreshCurrent();
            return;
        }
        push(Source.MODULE, target, 0L);
        refreshCurrent();
    }

    public static void setAutoCrystalTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_CRYSTAL);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_CRYSTAL, target, 0L);
        refreshCurrent();
    }

    public static void setAutoAnchorTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_ANCHOR);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_ANCHOR, target, 0L);
        refreshCurrent();
    }

    public static void setAutoBedTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_BED);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_BED, target, 0L);
        refreshCurrent();
    }

    public static void setPredictionTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.PREDICTION);
            refreshCurrent();
            return;
        }
        push(Source.PREDICTION, target, 0L);
        refreshCurrent();
    }

    public static void setElytraTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.ELYTRA);
            refreshCurrent();
            return;
        }
        push(Source.ELYTRA, target, 0L);
        refreshCurrent();
    }

    public static void publish(Module module, LivingEntity target, String phase, boolean actionable, int planned) {
        if (module == null) return;
        CombatTargetProvider descriptor = module.getClass().getAnnotation(CombatTargetProvider.class);
        if (descriptor == null) return;
        String id = descriptor.value();
        if (!module.isEnabled() || target == null || !TargetingUtil.isValidCombatTarget(target)) {
            MODULE_STATES.remove(id);
        } else {
            MODULE_STATES.put(id, new ModuleSnapshot(id, target, phase == null ? "idle" : phase,
                    actionable, Math.max(0, planned), descriptor.priority(), System.currentTimeMillis()));
        }
        refreshCurrent();
    }

    public static void clear(Module module) {
        if (module == null) return;
        CombatTargetProvider descriptor = module.getClass().getAnnotation(CombatTargetProvider.class);
        if (descriptor != null && MODULE_STATES.remove(descriptor.value()) != null) refreshCurrent();
    }

    public static Map<String, ModuleSnapshot> moduleSnapshots() {
        return Map.copyOf(MODULE_STATES);
    }

    public record ModuleSnapshot(String id, LivingEntity target, String phase,
                                 boolean actionable, int planned, int priority, long updatedAtMs) {}

    public static void clear(Source source) {
        if (source == null) return;
        STATES.remove(source);
    }

    public static void clearAll() {
        STATES.clear();
        MODULE_STATES.clear();
        if (current != null) {
            LivingEntity prev = current;
            Source prevSource = currentSource;
            current = null;
            currentSource = null;
            Events.BUS.post(new EventTargetChanged(prev, prevSource, null, null));
        } else {
            current = null;
            currentSource = null;
        }
    }

    private static void push(Source source, LivingEntity target, long holdMs) {
        if (source == null || target == null) return;
        STATES.put(source, new TargetState(target, System.currentTimeMillis(), holdMs));
    }

    private static void updateCrosshair(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity living
                && TargetingUtil.isValidCombatTarget(living)) {
            push(Source.CROSSHAIR, living, 0L);
        } else {
            clear(Source.CROSSHAIR);
        }
    }

    private static void purgeInvalids() {
        long now = System.currentTimeMillis();
        MODULE_STATES.values().removeIf(snapshot ->
                !TargetingUtil.isValidCombatTarget(snapshot.target())
                        || now - snapshot.updatedAtMs() > 350L);
        STATES.entrySet().removeIf(entry -> {
            TargetState state = entry.getValue();
            if (state == null || state.entity == null) return true;
            if (!TargetingUtil.isValidCombatTarget(state.entity)) return true;
            if (state.holdMs <= 0L) return false;
            return (now - state.lastSeenMs) > state.holdMs;
        });
    }

    private static void refreshCurrent() {
        LivingEntity prev = current;
        Source prevSource = currentSource;

        TargetSelection next = selectCurrent(true);
        LivingEntity nextEntity = next.entity();
        Source nextSource = next.source();

        if (prev != nextEntity || prevSource != nextSource) {
            current = nextEntity;
            currentSource = nextSource;
            Events.BUS.post(new EventTargetChanged(prev, prevSource, nextEntity, nextSource));
        }
    }

    private static TargetSelection selectCurrent(boolean includeCrosshair) {
        for (Source source : new Source[]{Source.FORCE, Source.ELYTRA, Source.AUTO_CRYSTAL, Source.AUTO_ANCHOR, Source.AUTO_BED, Source.MODULE, Source.PREDICTION, Source.ATTACK, Source.CROSSHAIR}) {
            if (!includeCrosshair && source == Source.CROSSHAIR) continue;
            TargetState state = STATES.get(source);
            if (state != null && TargetingUtil.isValidCombatTarget(state.entity)) {
                return new TargetSelection(state.entity, source);
            }
        }
        ModuleSnapshot module = MODULE_STATES.values().stream()
                .filter(snapshot -> TargetingUtil.isValidCombatTarget(snapshot.target()))
                .max(java.util.Comparator.comparingInt(ModuleSnapshot::priority))
                .orElse(null);
        if (module != null) return new TargetSelection(module.target(), Source.MODULE_STATE);
        return TargetSelection.EMPTY;
    }

    public enum Source {
        FORCE,
        ELYTRA,
        AUTO_CRYSTAL,
        AUTO_ANCHOR,
        AUTO_BED,
        MODULE,
        PREDICTION,
        ATTACK,
        CROSSHAIR,
        MODULE_STATE
    }

    private record TargetState(LivingEntity entity, long lastSeenMs, long holdMs) {
    }

    private record TargetSelection(LivingEntity entity, Source source) {
        private static final TargetSelection EMPTY = new TargetSelection(null, null);
    }
}
