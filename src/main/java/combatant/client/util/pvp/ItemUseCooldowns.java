/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp;

import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import combatant.client.config.values.ItemCooldownRulesValue;

import java.util.*;

/**
 * Generic owner-based item-use cooldown model.
 * <p>
 * Any system can record and read state for any owner:
 * - local player: {@link #SELF_OWNER};
 * - opponents: real player UUIDs;
 * - future systems: any stable UUID they own.
 * <p>
 * PvP cooldown settings only provide rule definitions. The runtime state lives here and is shared by the
 * local facade, opponent facade, NameTags, HUD overlays and future visualizers.
 */
public enum ItemUseCooldowns {
    ;
    public static final UUID SELF_OWNER = new UUID(0L, 0L);

    private static final Map<UUID, Map<Item, ActiveCooldown>> ACTIVE = new HashMap<>();
    private static final Map<UUID, Map<Item, UseWindow>> WINDOWS = new HashMap<>();

    public static RuleUseResult recordSelfUse(Item item) {
        return recordUse(SELF_OWNER, item);
    }

    public static RuleUseResult recordUse(UUID ownerId, Item item) {
        ItemCooldownRulesValue.Rule rule = CooldownRegistry.getRule(item);
        if (rule == null) return RuleUseResult.IGNORED;
        return recordUse(ownerId, item, rule);
    }

    public static RuleUseResult recordUse(UUID ownerId, Item item, ItemCooldownRulesValue.Rule rule) {
        if (ownerId == null || item == null || rule == null || !rule.enabled()) return RuleUseResult.IGNORED;
        if (rule.seconds() <= 0 && rule.uses() <= 1) return RuleUseResult.IGNORED;

        prune(ownerId, item);
        if (isCooling(ownerId, item)) return RuleUseResult.ALREADY_COOLING;

        int maxUses = Math.max(1, rule.uses());
        if (maxUses <= 1) {
            windowMap(ownerId).remove(item);
            if (rule.seconds() > 0) {
                startCooldown(ownerId, item, rule.seconds());
                return RuleUseResult.STARTED_COOLDOWN;
            }
            return RuleUseResult.IGNORED;
        }

        long now = Util.getMillis();
        long windowMs = Math.max(0, rule.windowSeconds()) * 1000L;
        Map<Item, UseWindow> windows = windowMap(ownerId);
        UseWindow current = windows.get(item);
        boolean expired = current != null && current.hasDeadline() && current.endMs <= now;
        boolean mismatch = current != null && current.maxUses != maxUses;

        int nextUses;
        long startMs;
        long endMs;
        long totalMs;
        if (current == null || expired || mismatch) {
            nextUses = 1;
            startMs = now;
            totalMs = windowMs;
            endMs = windowMs > 0L ? now + windowMs : 0L;
        } else {
            nextUses = Math.min(maxUses, current.uses + 1);
            startMs = current.startMs;
            totalMs = current.totalMs;
            endMs = current.endMs;
        }

        if (nextUses >= maxUses) {
            windows.remove(item);
            if (rule.seconds() > 0) {
                startCooldown(ownerId, item, rule.seconds());
                return RuleUseResult.STARTED_COOLDOWN;
            }
            return RuleUseResult.RECORDED_WINDOW_USE;
        }

        windows.put(item, new UseWindow(startMs, endMs, totalMs, nextUses, maxUses));
        return RuleUseResult.RECORDED_WINDOW_USE;
    }

    public static void startSelfCooldown(Item item, int seconds) {
        startCooldown(SELF_OWNER, item, seconds);
    }

    public static void startCooldown(UUID ownerId, Item item, int seconds) {
        if (ownerId == null || item == null || seconds <= 0) return;
        long totalMs = seconds * 1000L;
        activeMap(ownerId).put(item, new ActiveCooldown(Util.getMillis() + totalMs, totalMs));
        windowMap(ownerId).remove(item);
    }

    public static boolean isSelfCooling(Item item) {
        return isCooling(SELF_OWNER, item);
    }

    public static boolean isCooling(UUID ownerId, Item item) {
        if (ownerId == null || item == null) return false;
        Map<Item, ActiveCooldown> map = ACTIVE.get(ownerId);
        if (map == null) return false;

        ActiveCooldown cooldown = map.get(item);
        if (cooldown == null) return false;

        if (cooldown.endMs <= Util.getMillis()) {
            map.remove(item);
            return false;
        }
        return true;
    }

    public static ItemCooldownSnapshot selfSnapshot(Item item) {
        return snapshot(SELF_OWNER, item);
    }

    public static ItemCooldownSnapshot snapshot(UUID ownerId, Item item) {
        if (ownerId == null || item == null) return ItemCooldownSnapshot.empty(item);
        prune(ownerId, item);
        long now = Util.getMillis();

        ActiveCooldown cooldown = activeMap(ownerId).get(item);
        boolean cooling = cooldown != null && cooldown.endMs > now;
        long cooldownRemaining = cooling ? Math.max(0L, cooldown.endMs - now) : 0L;
        long cooldownTotal = cooling ? Math.max(0L, cooldown.totalMs) : 0L;

        UseWindow window = windowMap(ownerId).get(item);
        boolean windowActive = window != null && (!window.hasDeadline() || window.endMs > now);
        long windowRemaining = 0L;
        long windowTotal = 0L;
        int uses = 0;
        int maxUses = Math.max(1, CooldownRegistry.getMaxUses(item));
        if (windowActive) {
            uses = window.uses;
            maxUses = Math.max(1, window.maxUses);
            windowTotal = Math.max(0L, window.totalMs);
            windowRemaining = window.hasDeadline() ? Math.max(0L, window.endMs - now) : 0L;
        }

        return new ItemCooldownSnapshot(item, cooling, cooldownRemaining, cooldownTotal, uses, maxUses, windowActive, windowRemaining, windowTotal);
    }

    public static Map<Item, ItemCooldownSnapshot> selfSnapshots() {
        return snapshots(SELF_OWNER);
    }

    public static Map<Item, ItemCooldownSnapshot> snapshots(UUID ownerId) {
        Map<Item, ItemCooldownSnapshot> out = new LinkedHashMap<>();
        if (ownerId == null) return out;

        Set<Item> trackedItems = CooldownRegistry.trackedItems();
        for (Item item : trackedItems) {
            ItemCooldownSnapshot snapshot = snapshot(ownerId, item);
            if (snapshot.visible()) out.put(item, snapshot);
        }
        return out;
    }

    public static void clearSelf() {
        clear(SELF_OWNER);
    }

    public static void clear(UUID ownerId) {
        if (ownerId == null) return;
        ACTIVE.remove(ownerId);
        WINDOWS.remove(ownerId);
    }

    public static void clearAll() {
        ACTIVE.clear();
        WINDOWS.clear();
    }

    public static void tick() {
        for (UUID ownerId : ACTIVE.keySet().toArray(UUID[]::new)) {
            Map<Item, ActiveCooldown> active = ACTIVE.get(ownerId);
            if (active != null) {
                for (Item item : active.keySet().toArray(Item[]::new)) {
                    prune(ownerId, item);
                }
                if (active.isEmpty()) ACTIVE.remove(ownerId);
            }
        }

        for (UUID ownerId : WINDOWS.keySet().toArray(UUID[]::new)) {
            Map<Item, UseWindow> windows = WINDOWS.get(ownerId);
            if (windows != null) {
                for (Item item : windows.keySet().toArray(Item[]::new)) {
                    prune(ownerId, item);
                }
                if (windows.isEmpty()) WINDOWS.remove(ownerId);
            }
        }
    }

    private static void prune(UUID ownerId, Item item) {
        long now = Util.getMillis();
        Map<Item, ActiveCooldown> active = ACTIVE.get(ownerId);
        if (active != null) {
            ActiveCooldown cooldown = active.get(item);
            if (cooldown != null && cooldown.endMs <= now) active.remove(item);
        }

        Map<Item, UseWindow> windows = WINDOWS.get(ownerId);
        if (windows != null) {
            UseWindow window = windows.get(item);
            if (window != null && window.hasDeadline() && window.endMs <= now) windows.remove(item);
        }
    }

    private static Map<Item, ActiveCooldown> activeMap(UUID ownerId) {
        return ACTIVE.computeIfAbsent(ownerId, ignored -> new HashMap<>());
    }

    private static Map<Item, UseWindow> windowMap(UUID ownerId) {
        return WINDOWS.computeIfAbsent(ownerId, ignored -> new HashMap<>());
    }

    public enum RuleUseResult {
        IGNORED,
        RECORDED_WINDOW_USE,
        STARTED_COOLDOWN,
        ALREADY_COOLING
    }

    private record ActiveCooldown(long endMs, long totalMs) {
    }

    private record UseWindow(long startMs, long endMs, long totalMs, int uses, int maxUses) {
        boolean hasDeadline() {
            return endMs > 0L && totalMs > 0L;
        }
    }
}
