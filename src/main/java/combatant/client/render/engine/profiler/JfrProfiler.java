/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;

public enum JfrProfiler {
    ;
    private static final boolean DEV = DevProfilerBridge.available("JfrProfiler");

    public static synchronized StartResult start(Preset preset) {
        if (!DEV) return StartResult.failed("dev build required");
        Object devPreset = devPreset(preset);
        if (devPreset == null) {
            return StartResult.failed("dev build required");
        }
        Object result = DevProfilerBridge.invoke("JfrProfiler", "start", new Class<?>[]{devPreset.getClass()}, devPreset);
        return startResult(result);
    }

    public static synchronized DumpResult dumpSnapshot() {
        if (!DEV) return DumpResult.failed("dev build required");
        Object result = DevProfilerBridge.invoke("JfrProfiler", "dumpSnapshot", new Class<?>[0]);
        return dumpResult(result);
    }

    public static synchronized StopResult stop() {
        if (!DEV) return StopResult.failed("dev build required");
        Object result = DevProfilerBridge.invoke("JfrProfiler", "stop", new Class<?>[0]);
        return stopResult(result);
    }

    public static synchronized String statusLine() {
        if (!DEV) return "Profiler JFR: dev build required";
        return DevProfilerBridge.string("JfrProfiler", "statusLine", "Profiler JFR: dev build required", new Class<?>[0]);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object devPreset(Preset preset) {
        Class<?> type = DevProfilerBridge.type("JfrProfiler");
        if (type == null) return null;
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (!"Preset".equals(nested.getSimpleName()) || !nested.isEnum()) continue;
            return Enum.valueOf((Class<? extends Enum>) nested.asSubclass(Enum.class), preset.name());
        }
        return null;
    }

    private static StartResult startResult(Object result) {
        if (result == null) return StartResult.failed("dev build required");
        return new StartResult(
                bool(result, "started"),
                bool(result, "alreadyRunning"),
                preset(value(result, "preset")),
                path(value(result, "path")),
                instant(value(result, "startedAt")),
                string(value(result, "error"))
        );
    }

    private static DumpResult dumpResult(Object result) {
        if (result == null) return DumpResult.failed("dev build required");
        return new DumpResult(
                bool(result, "dumped"),
                bool(result, "notRunning"),
                path(value(result, "path")),
                string(value(result, "error"))
        );
    }

    private static StopResult stopResult(Object result) {
        if (result == null) return StopResult.failed("dev build required");
        return new StopResult(
                bool(result, "stopped"),
                bool(result, "notRunning"),
                preset(value(result, "preset")),
                path(value(result, "path")),
                instant(value(result, "startedAt")),
                string(value(result, "error"))
        );
    }

    private static Object value(Object owner, String methodName) {
        if (owner == null) return null;
        try {
            Method method = owner.getClass().getMethod(methodName);
            return method.invoke(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean bool(Object owner, String methodName) {
        Object value = value(owner, methodName);
        return value instanceof Boolean b && b;
    }

    private static Path path(Object value) {
        return value instanceof Path path ? path : null;
    }

    private static Instant instant(Object value) {
        return value instanceof Instant instant ? instant : null;
    }

    private static String string(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Preset preset(Object value) {
        return value instanceof Enum<?> e ? Preset.valueOf(e.name()) : null;
    }

    public enum Preset {
        GC("gc"),
        ALLOC("alloc"),
        FULL("full");

        private final String id;

        Preset(String id) {
            this.id = id;
        }

        public static Preset parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return FULL;
            }
            return switch (raw.toLowerCase()) {
                case "gc" -> GC;
                case "alloc", "allocation", "allocs" -> ALLOC;
                case "full", "all", "profile" -> FULL;
                default -> null;
            };
        }

        public String id() {
            return id;
        }

        public String getId() {
            return id;
        }

        public boolean capturesAllocations() {
            return this == ALLOC || this == FULL;
        }
    }

    public record StartResult(boolean started, boolean alreadyRunning, Preset preset, Path path, Instant startedAt,
                              String error) {
        private static StartResult failed(String error) {
            return new StartResult(false, false, null, null, null, error);
        }
    }

    public record DumpResult(boolean dumped, boolean notRunning, Path path, String error) {
        private static DumpResult failed(String error) {
            return new DumpResult(false, false, null, error);
        }
    }

    public record StopResult(boolean stopped, boolean notRunning, Preset preset, Path path, Instant startedAt,
                             String error) {
        private static StopResult failed(String error) {
            return new StopResult(false, false, null, null, null, error);
        }
    }
}
