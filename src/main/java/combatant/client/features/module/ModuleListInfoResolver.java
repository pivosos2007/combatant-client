/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import combatant.client.runtime.discovery.CombatantIndex;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.runtime.error.FailureIsolation;
import combatant.client.util.logging.DebugLog;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/** Resolves compile-time indexed ModuleList text suppliers once per module class. */
public final class ModuleListInfoResolver {
    private ModuleListInfoResolver() { }

    private static final Map<String, String> INDEXED_METHODS = readIndex();
    private static final Accessor NONE = module -> null;
    private static final ClassValue<Accessor> ACCESSORS = new ClassValue<>() {
        @Override
        protected Accessor computeValue(Class<?> type) {
            for (Class<?> owner = type; owner != null && Module.class.isAssignableFrom(owner);
                 owner = owner.getSuperclass()) {
                String name = INDEXED_METHODS.get(owner.getName());
                if (name != null) return create(owner, name);
            }
            return NONE;
        }
    };

    private static Map<String, String> readIndex() {
        Map<String, String> result = new HashMap<>();
        for (String entry : CombatantIndex.entries(CombatantIndex.Kind.MODULE_LIST_INFO_PROVIDER)) {
            int separator = entry.lastIndexOf('#');
            if (separator > 0 && separator < entry.length() - 1) {
                result.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
        }
        return Map.copyOf(result);
    }

    /** Pre-resolves method access when modules register, not during the first HUD frame. */
    public static void prepare(Module module) {
        if (module != null) ACCESSORS.get(module.getClass());
    }

    /** Queries current text without allowing the source module to control appearance. */
    public static String query(Module module) {
        if (module == null || !ErrorHandler.canRun(module)) return null;
        try {
            String text = ACCESSORS.get(module.getClass()).get(module);
            return text == null || text.isBlank() ? null : text.strip();
        } catch (Throwable error) {
            FailureIsolation.reportModule(module, "ModuleList info", error);
            return null;
        }
    }

    private static Accessor create(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
                    || method.getReturnType() != String.class) {
                throw new IllegalStateException("Invalid indexed ModuleList provider: " + type.getName() + "#" + name);
            }
            method.setAccessible(true);
            MethodHandle handle = MethodHandles.lookup().unreflect(method)
                    .asType(MethodType.methodType(String.class, Module.class));
            return module -> (String) handle.invokeExact(module);
        } catch (ReflectiveOperationException | SecurityException error) {
            DebugLog.error("Failed to bind ModuleList info provider: %s", error, type.getName() + "#" + name);
            return NONE;
        }
    }

    @FunctionalInterface
    private interface Accessor {
        String get(Module module) throws Throwable;
    }
}
