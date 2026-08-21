/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;

import java.util.ArrayDeque;

/**
 * Lightweight bridge into Minecraft's profiler stack.
 *
 * <p>When jtracy is available, {@link Profiler#get()} is backed by Mojang's
 * TracyZoneFiller. Otherwise it is the singleton inactive profiler, so the
 * hot path can skip all label construction/allocation.</p>
 */
public enum ProfilerPhase {
    ;

    private static final ThreadLocal<ArrayDeque<OpenZone>> MANUAL_ZONES = new ThreadLocal<>();

    public static boolean isActive() {
        return Profiler.get() != InactiveProfiler.INSTANCE;
    }

    public static Scope scope(String label) {
        ProfilerFiller profiler = Profiler.get();
        if (profiler == InactiveProfiler.INSTANCE) return Scope.NOOP;
        return new Scope(profiler.zone(sanitize(label)));
    }

    public static void begin(String label) {
        ProfilerFiller profiler = Profiler.get();
        if (profiler == InactiveProfiler.INSTANCE) return;

        ArrayDeque<OpenZone> stack = MANUAL_ZONES.get();
        if (stack == null) {
            stack = new ArrayDeque<>(4);
            MANUAL_ZONES.set(stack);
        }

        String safeLabel = sanitize(label);
        stack.addLast(new OpenZone(safeLabel, profiler.zone(safeLabel)));
    }

    public static void end(String label) {
        ArrayDeque<OpenZone> stack = MANUAL_ZONES.get();
        if (stack == null || stack.isEmpty()) return;

        OpenZone zone = stack.removeLast();
        zone.zone.close();
        if (stack.isEmpty()) {
            MANUAL_ZONES.remove();
        }
    }

    public static String current() {
        ArrayDeque<OpenZone> stack = MANUAL_ZONES.get();
        if (stack == null || stack.isEmpty()) return "unknown";
        return stack.peekLast().label;
    }

    public static void clearCurrent() {
        ArrayDeque<OpenZone> stack = MANUAL_ZONES.get();
        if (stack == null) return;
        while (!stack.isEmpty()) {
            stack.removeLast().zone.close();
        }
        MANUAL_ZONES.remove();
    }

    private static String sanitize(String label) {
        return label == null || label.isBlank() ? "combatant:unknown" : label;
    }

    private record OpenZone(String label, Zone zone) {
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null);
        private Zone zone;

        private Scope(Zone zone) {
            this.zone = zone;
        }

        @Override
        public void close() {
            Zone current = zone;
            if (current == null) return;
            zone = null;
            current.close();
        }
    }
}
