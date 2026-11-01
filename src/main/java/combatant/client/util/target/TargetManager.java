/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.target;

import combatant.client.events.Events;
import combatant.client.events.impl.EventTargetChanged;
import combatant.client.features.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * Shared target view backed by independent, dynamically published target providers.
 *
 * A provider is identified by its owner object and an optional channel name. No
 * combat module is registered or referenced in the manager itself. Publication
 * priorities are chosen by the provider; a smaller priority does not prevent
 * the provider from keeping its own target and never overrides other owners.
 *
 * All methods are to be called on the Minecraft client thread. Snapshots are
 * short-lived leases, preventing disabled or crashed providers from pinning a
 * target indefinitely. The explicitly forced target is the only non-expiring lease.
 */
public enum TargetManager {
    ;

    private static final String MAIN = "main";
    private static final long PROVIDER_LEASE_MS = 600L;
    private static final long ATTACK_HOLD_MS = 700L;
    private static final int ATTACK_PRIORITY = 300;
    private static final int CROSSHAIR_PRIORITY = 100;
    private static final int FORCE_PRIORITY = 1000;
    private static final Object ATTACK_OWNER = new Object();
    private static final Object CROSSHAIR_OWNER = new Object();
    private static final Object FORCE_OWNER = new Object();

    private static final IdentityHashMap<Object, Map<String, TargetState>> PROVIDERS = new IdentityHashMap<>();
    private static LivingEntity current;
    private static Source currentSource;
    private static Object currentOwner;
    private static String currentChannel;

    /** Meaning of the target, not a list of modules which may publish one. */
    public enum Source { FORCE, MODULE, PREDICTION, ATTACK, CROSSHAIR }

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

    public static LivingEntity getTarget() { return current; }

    public static LivingEntity getTarget(boolean includeCrosshair) {
        return includeCrosshair ? current : selectCurrent(false).entity();
    }

    public static Source getTargetSource() { return currentSource; }

    /** Manual attack history is independent of the currently highest-priority module. */
    public static LivingEntity getRecentAttackTarget() {
        TargetState attack = state(ATTACK_OWNER, MAIN);
        return isActive(ATTACK_OWNER, attack, System.currentTimeMillis()) ? attack.entity() : null;
    }

    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living && TargetingUtil.isValidCombatTarget(living)) {
            put(ATTACK_OWNER, MAIN, living, Source.ATTACK, ATTACK_PRIORITY,
                    ATTACK_HOLD_MS, "attack", true, 0);
            refreshCurrent();
        }
    }

    public static void setForcedTarget(LivingEntity target) {
        if (target == null) {
            clear(FORCE_OWNER);
        } else {
            put(FORCE_OWNER, MAIN, target, Source.FORCE, FORCE_PRIORITY,
                    0L, "forced", true, 0);
            refreshCurrent();
        }
    }

    /** Publishes or clears the default channel for any owner. */
    public static void publish(Object owner, LivingEntity target, Source source, int priority) {
        publish(owner, MAIN, target, source, priority, "active", true, 0);
    }

    /** Additional channels allow one owner to publish both active and predicted targets. */
    public static void publish(Object owner, String channel, LivingEntity target, Source source, int priority) {
        publish(owner, channel, target, source, priority, "active", true, 0);
    }

    /** Optional status metadata is reported in moduleSnapshots; it does not affect arbitration. */
    public static void publish(Object owner, LivingEntity target, Source source, int priority,
                               String phase, boolean actionable, int planned) {
        publish(owner, MAIN, target, source, priority, phase, actionable, planned);
    }

    public static void publish(Object owner, String channel, LivingEntity target, Source source, int priority,
                               String phase, boolean actionable, int planned) {
        if (owner == null || source == null || channel == null || channel.isBlank()) return;
        if (target == null || !TargetingUtil.isValidCombatTarget(target)
                || owner instanceof Module module && !module.isEnabled()) {
            clear(owner, channel);
            return;
        }
        put(owner, channel, target, source, priority, PROVIDER_LEASE_MS,
                phase == null ? "active" : phase, actionable, Math.max(0, planned));
        refreshCurrent();
    }

    public static void clear(Object owner) {
        if (owner != null && PROVIDERS.remove(owner) != null) refreshCurrent();
    }

    public static void clear(Object owner, String channel) {
        if (owner == null || channel == null) return;
        Map<String, TargetState> states = PROVIDERS.get(owner);
        if (states == null || states.remove(channel) == null) return;
        if (states.isEmpty()) PROVIDERS.remove(owner);
        refreshCurrent();
    }

    /** Diagnostics, not a secondary arbitration path. All providers use the same map. */
    public static Map<String, ModuleSnapshot> moduleSnapshots() {
        Map<String, ModuleSnapshot> out = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (var entry : PROVIDERS.entrySet()) {
            if (!(entry.getKey() instanceof Module module)) continue;
            for (var channel : entry.getValue().entrySet()) {
                TargetState s = channel.getValue();
                if (!isActive(module, s, now)) continue;
                String id = module.name() + (channel.getKey().equals(MAIN) ? "" : "/" + channel.getKey());
                out.put(id, new ModuleSnapshot(id, s.entity(), s.phase(), s.actionable(),
                        s.planned(), s.priority(), s.updatedAtMs()));
            }
        }
        return Map.copyOf(out);
    }

    public record ModuleSnapshot(String id, LivingEntity target, String phase,
                                 boolean actionable, int planned, int priority, long updatedAtMs) { }

    public static void clearAll() {
        PROVIDERS.clear();
        setCurrent(TargetSelection.EMPTY);
    }

    private static void put(Object owner, String channel, LivingEntity target, Source source,
                            int priority, long leaseMs, String phase, boolean actionable, int planned) {
        PROVIDERS.computeIfAbsent(owner, unused -> new LinkedHashMap<>())
                .put(channel, new TargetState(target, source, priority, System.currentTimeMillis(),
                        leaseMs, phase, actionable, planned));
    }

    private static TargetState state(Object owner, String channel) {
        Map<String, TargetState> states = PROVIDERS.get(owner);
        return states == null ? null : states.get(channel);
    }

    private static void updateCrosshair(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && TargetingUtil.isValidCombatTarget(living)) {
            put(CROSSHAIR_OWNER, MAIN, living, Source.CROSSHAIR, CROSSHAIR_PRIORITY,
                    PROVIDER_LEASE_MS, "crosshair", false, 0);
        } else {
            PROVIDERS.remove(CROSSHAIR_OWNER);
        }
    }

    private static boolean isActive(Object owner, TargetState state, long now) {
        if (state == null || !TargetingUtil.isValidCombatTarget(state.entity())) return false;
        if (owner instanceof Module module && !module.isEnabled()) return false;
        return state.leaseMs() == 0L || now - state.updatedAtMs() <= state.leaseMs();
    }

    private static void purgeInvalids() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Object, Map<String, TargetState>>> iter = PROVIDERS.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            entry.getValue().values().removeIf(state -> !isActive(entry.getKey(), state, now));
            if (entry.getValue().isEmpty()) iter.remove();
        }
    }

    private static void refreshCurrent() {
        setCurrent(selectCurrent(true));
    }

    private static void setCurrent(TargetSelection next) {
        LivingEntity previous = current;
        Source previousSource = currentSource;
        current = next.entity();
        currentSource = next.source();
        currentOwner = next.owner();
        currentChannel = next.channel();
        if (previous != current || previousSource != currentSource) {
            Events.BUS.post(new EventTargetChanged(previous, previousSource, current, currentSource));
        }
    }

    private static TargetSelection selectCurrent(boolean includeCrosshair) {
        TargetSelection best = TargetSelection.EMPTY;
        int bestPriority = Integer.MIN_VALUE;
        long now = System.currentTimeMillis();
        for (var entry : PROVIDERS.entrySet()) {
            Object owner = entry.getKey();
            for (var channel : entry.getValue().entrySet()) {
                TargetState state = channel.getValue();
                if (state.source() == Source.CROSSHAIR && !includeCrosshair) continue;
                if (!isActive(owner, state, now)) continue;
                // Retain the current owner at equal priority to avoid flickering between
                // providers that publish at different points during the same client tick.
                if (state.priority() < bestPriority) continue;
                if (state.priority() == bestPriority
                        && !(owner == currentOwner && channel.getKey().equals(currentChannel))) continue;
                bestPriority = state.priority();
                best = new TargetSelection(state.entity(), state.source(), owner, channel.getKey());
            }
        }
        return best;
    }

    private record TargetState(LivingEntity entity, Source source, int priority, long updatedAtMs,
                               long leaseMs, String phase, boolean actionable, int planned) { }

    private record TargetSelection(LivingEntity entity, Source source, Object owner, String channel) {
        private static final TargetSelection EMPTY = new TargetSelection(null, null, null, null);
    }
}
