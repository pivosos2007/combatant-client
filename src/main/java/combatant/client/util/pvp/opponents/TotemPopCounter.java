/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.opponents;

import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standalone per-player totem pop counter utility.
 * <p>
 * The utility owns only pop-count state. It has no module dependency and does not mutate cooldown state by itself.
 * Packet trackers or visual systems may call recordPop(...) and may separately call cooldown utilities if needed.
 */
public enum TotemPopCounter {
    ;
    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();
    private static final BooleanValue ENABLED =
            new BooleanValue("totem_pop_counter", true);
    private static final NumberValue<Integer> RESET_AFTER_SECONDS =
            new NumberValue<>("totem_pop_reset_seconds", 300, 0, 600);
    private static volatile Options options = Options.DEFAULT;

    public static void configure(Options nextOptions) {
        options = nextOptions != null ? nextOptions : Options.DEFAULT;
        ENABLED.set(options.enabled());
        if (!enabled()) {
            clear();
        } else {
            prune(Util.getMillis());
        }
    }

    public static Options options() {
        return options.withEnabled(enabled()).withResetAfterMs(resetAfterMs());
    }

    public static BooleanValue enabledValue() {
        return ENABLED;
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static NumberValue<Integer> resetAfterSecondsValue() {
        return RESET_AFTER_SECONDS;
    }

    public static int resetAfterSeconds() {
        return clampResetSeconds(RESET_AFTER_SECONDS.get());
    }

    public static long resetAfterMs() {
        return resetAfterSeconds() * 1000L;
    }

    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
        configure(options().withEnabled(enabled));
    }

    public static void setResetOnDeath(boolean resetOnDeath) {
        configure(options().withResetOnDeath(resetOnDeath));
    }

    public static void setResetOnLogout(boolean resetOnLogout) {
        configure(options().withResetOnLogout(resetOnLogout));
    }

    /**
     * @param resetAfterMs inactivity timeout in milliseconds; {@code 0} disables timeout pruning.
     */
    public static void setResetAfterMs(long resetAfterMs) {
        long seconds = resetAfterMs <= 0L ? 0L : Math.round(resetAfterMs / 1000.0);
        RESET_AFTER_SECONDS.set(clampResetSeconds((int) Math.min(Integer.MAX_VALUE, seconds)));
        configure(options().withResetAfterMs(resetAfterMs));
    }

    public static void setResetAfterSeconds(int seconds) {
        int clamped = clampResetSeconds(seconds);
        RESET_AFTER_SECONDS.set(clamped);
        configure(options().withResetAfterMs(clamped * 1000L));
    }

    public static void recordPop(Player player) {
        if (player == null) return;
        recordPop(player.getUUID(), player.getName().getString());
    }

    public static void recordPop(UUID playerId) {
        recordPop(playerId, null);
    }

    public static void recordPop(UUID playerId, String name) {
        if (playerId == null || !enabled()) return;
        long now = Util.getMillis();
        prune(now);

        Entry current = ENTRIES.get(playerId);
        int nextCount = current != null ? current.count + 1 : 1;
        String resolvedName = firstNonBlank(name, current != null ? current.name : null);
        long firstPopMs = current != null ? current.firstPopMs : now;
        ENTRIES.put(playerId, new Entry(playerId, resolvedName, nextCount, firstPopMs, now));
    }

    public static int getCount(UUID playerId) {
        return snapshot(playerId).count();
    }

    public static TotemPopSnapshot snapshot(UUID playerId) {
        if (playerId == null || !enabled()) return TotemPopSnapshot.empty(playerId);
        prune(Util.getMillis());
        Entry entry = ENTRIES.get(playerId);
        return entry != null ? entry.snapshot() : TotemPopSnapshot.empty(playerId);
    }

    public static Map<UUID, TotemPopSnapshot> snapshots() {
        if (!enabled()) return Collections.emptyMap();
        prune(Util.getMillis());
        Map<UUID, TotemPopSnapshot> out = new LinkedHashMap<>();
        for (var entry : ENTRIES.entrySet()) {
            out.put(entry.getKey(), entry.getValue().snapshot());
        }
        return Collections.unmodifiableMap(out);
    }

    public static void reset(UUID playerId) {
        if (playerId == null) return;
        ENTRIES.remove(playerId);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static void onPlayerDeath(UUID playerId) {
        if (playerId == null) return;
        if (options.resetOnDeath()) {
            reset(playerId);
        }
    }

    public static void onPlayerLogout(UUID playerId) {
        if (playerId == null) return;
        if (options.resetOnLogout()) {
            reset(playerId);
        }
    }

    public static void tick() {
        if (!enabled()) {
            clear();
            return;
        }
        prune(Util.getMillis());
    }

    private static void prune(long now) {
        long timeoutMs = resetAfterMs();
        if (timeoutMs <= 0L) return;
        ENTRIES.entrySet().removeIf(entry -> now - entry.getValue().lastPopMs > timeoutMs);
    }

    private static int clampResetSeconds(int seconds) {
        if (seconds < 0) return 0;
        return Math.min(600, seconds);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }

    public record Options(boolean enabled,
                          boolean resetOnDeath,
                          boolean resetOnLogout,
                          long resetAfterMs) {
        public static final Options DEFAULT = new Options(true, true, true, 300_000L);

        public Options {
            resetAfterMs = Math.max(0L, resetAfterMs);
        }

        public Options withEnabled(boolean enabled) {
            return new Options(enabled, resetOnDeath, resetOnLogout, resetAfterMs);
        }

        public Options withResetOnDeath(boolean resetOnDeath) {
            return new Options(enabled, resetOnDeath, resetOnLogout, resetAfterMs);
        }

        public Options withResetOnLogout(boolean resetOnLogout) {
            return new Options(enabled, resetOnDeath, resetOnLogout, resetAfterMs);
        }

        public Options withResetAfterMs(long resetAfterMs) {
            return new Options(enabled, resetOnDeath, resetOnLogout, resetAfterMs);
        }
    }

    private record Entry(UUID playerId, String name, int count, long firstPopMs, long lastPopMs) {
        TotemPopSnapshot snapshot() {
            return new TotemPopSnapshot(playerId, name, count, firstPopMs, lastPopMs);
        }
    }
}
