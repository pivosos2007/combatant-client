/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public enum DevProfilerBridge {
    ;

    private static final String PACKAGE = "combatant.client.render.engine.profiler.";
    private static final Class<?> MISSING = Missing.class;
    private static final ConcurrentMap<String, Class<?>> TYPES = new ConcurrentHashMap<>();

    public static boolean available(String simpleName) {
        return type(simpleName) != null;
    }

    static Object invoke(String simpleName, String methodName, Class<?>[] parameterTypes, Object... args) {
        Class<?> type = type(simpleName);
        if (type == null) return null;
        try {
            Method method = type.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean bool(String simpleName, String methodName, boolean fallback, Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(simpleName, methodName, parameterTypes, args);
        return value instanceof Boolean b ? b : fallback;
    }

    static String string(String simpleName, String methodName, String fallback, Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(simpleName, methodName, parameterTypes, args);
        return value instanceof String s ? s : fallback;
    }

    @SuppressWarnings("unchecked")
    static List<String> lines(String simpleName, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(simpleName, methodName, parameterTypes, args);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    static AutoCloseable closeable(String simpleName, String methodName, Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(simpleName, methodName, parameterTypes, args);
        return value instanceof AutoCloseable closeable ? closeable : null;
    }

    static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    static Class<?> type(String simpleName) {
        Class<?> cached = TYPES.get(simpleName);
        if (cached != null) {
            return cached == MISSING ? null : cached;
        }
        Class<?> resolved;
        try {
            resolved = Class.forName(PACKAGE + "Dev" + simpleName);
        } catch (Throwable ignored) {
            resolved = MISSING;
        }
        Class<?> previous = TYPES.putIfAbsent(simpleName, resolved);
        Class<?> value = previous == null ? resolved : previous;
        return value == MISSING ? null : value;
    }

    private static final class Missing {
    }
}
