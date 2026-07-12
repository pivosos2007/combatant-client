/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import combatant.client.util.logging.DebugLog;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum ModuleAutoLoader {
    ;

    private static final Map<String, DisableRequest> DISABLE_REQUESTS = new LinkedHashMap<>();
    private static boolean discoveryStarted;
    private static boolean discoveryFinished;

    /**
     * Registers a startup-only exclusion requested by an enabled addon.
     *
     * <p>The reference may be a module id, alias, simple class name, or fully-qualified
     * class name. Requests stop being accepted as soon as module discovery starts.</p>
     */
    public static synchronized boolean acceptsDisableRequests() {
        return !discoveryStarted;
    }

    public static synchronized boolean isDiscoveryFinished() {
        return discoveryFinished;
    }

    public static synchronized boolean wasDisableRequestMatched(String moduleReference, String addonId) {
        DisableRequest request = DISABLE_REQUESTS.get(normalize(moduleReference));
        if (request == null || !request.matched) return false;
        String owner = normalize(addonId);
        return request.owners.contains(owner.isEmpty() ? "unknown" : owner);
    }

    public static synchronized boolean requestDisable(String moduleReference, String addonId) {
        String reference = normalize(moduleReference);
        if (reference.isEmpty() || discoveryStarted) {
            return false;
        }

        String owner = normalize(addonId);
        DisableRequest request = DISABLE_REQUESTS.computeIfAbsent(reference, ignored -> new DisableRequest());
        request.owners.add(owner.isEmpty() ? "unknown" : owner);
        return true;
    }

    public static void load(String basePackage) {
        synchronized (ModuleAutoLoader.class) {
            if (discoveryStarted) {
                DebugLog.warn("Module discovery has already started; duplicate load request ignored");
                return;
            }
            discoveryStarted = true;
        }

        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages(basePackage)
                .scan()) {
            Set<String> candidates = new LinkedHashSet<>();
            addAnnotatedCandidates(scan, candidates, ModuleInfo.class);

            for (String className : candidates) {
                loadCandidate(className);
            }
        } finally {
            synchronized (ModuleAutoLoader.class) {
                discoveryFinished = true;
            }
            logUnmatchedDisableRequests();
        }
    }

    private static void addAnnotatedCandidates(ScanResult scan,
                                               Set<String> candidates,
                                               Class<?> annotationType) {
        for (ClassInfo info : scan.getClassesWithAnnotation(annotationType.getName())) {
            candidates.add(info.getName());
        }
    }

    private static void loadCandidate(String className) {
        try {
            Class<?> cls = Class.forName(className, false, ModuleAutoLoader.class.getClassLoader());
            if (!Module.class.isAssignableFrom(cls)) {
                DebugLog.config("Skipping annotated non-module: %s", cls.getName());
                return;
            }

            ModuleInfo info = cls.getAnnotation(ModuleInfo.class);
            Set<String> disabledBy = findDisableOwners(cls, info);
            if (!disabledBy.isEmpty()) {
                DebugLog.config(
                        "Skipping addon-disabled module: %s (id=%s, addons=%s)",
                        cls.getName(),
                        info == null ? "" : info.id(),
                        String.join(",", disabledBy)
                );
                return;
            }

            int modifiers = cls.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers) || cls.isAnnotation()) {
                DebugLog.config("Skipping non-concrete module class: %s", cls.getName());
                return;
            }

            Constructor<?> constructor;
            try {
                constructor = cls.getDeclaredConstructor();
            } catch (NoSuchMethodException ignored) {
                DebugLog.error("Failed to load module %s: no no-args constructor", null, cls.getName());
                return;
            }

            constructor.setAccessible(true);
            Module module = (Module) constructor.newInstance();
            module.postInit();

            DebugLog.config("Loaded module: %s", cls.getName());
        } catch (Throwable t) {
            DebugLog.error("Failed to load module: %s", t, className);
        }
    }

    private static Set<String> findDisableOwners(Class<?> moduleClass, ModuleInfo info) {
        List<String> references = new ArrayList<>(6);
        references.add(moduleClass.getName());
        references.add(moduleClass.getSimpleName());
        if (info != null) {
            references.add(info.id());
            for (String alias : info.aliases()) {
                references.add(alias);
            }
        }

        Set<String> owners = new LinkedHashSet<>();
        synchronized (ModuleAutoLoader.class) {
            for (String reference : references) {
                DisableRequest request = DISABLE_REQUESTS.get(normalize(reference));
                if (request == null) continue;
                request.matched = true;
                owners.addAll(request.owners);
            }
        }
        return owners;
    }

    private static void logUnmatchedDisableRequests() {
        synchronized (ModuleAutoLoader.class) {
            for (Map.Entry<String, DisableRequest> entry : DISABLE_REQUESTS.entrySet()) {
                DisableRequest request = entry.getValue();
                if (request.matched) continue;
                DebugLog.warn(
                        "[Addons] Built-in module exclusion did not match any module: reference=%s addons=%s",
                        entry.getKey(),
                        String.join(",", request.owners)
                );
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class DisableRequest {
        private final Set<String> owners = new LinkedHashSet<>();
        private boolean matched;
    }
}
