/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.util.logging.DebugLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum HudElementAutoLoader {
    ;

    public static void load(String basePackage) {
        load(basePackage, true, true);
    }

    public static void loadDraggable(String basePackage) {
        load(basePackage, true, false);
    }

    public static void loadStatic(String basePackage) {
        load(basePackage, false, true);
    }

    private static void load(String basePackage, boolean includeDraggable, boolean includeStatic) {
        List<RegisteredHudElement> elements = new ArrayList<>();

        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages(basePackage)
                .scan()) {
            for (ClassInfo info : scan.getClassesWithAnnotation(HudElementRegister.class.getName())) {
                addCandidate(elements, info);
            }
            for (ClassInfo info : scan.getClassesWithAnnotation(HudElementInfo.class.getName())) {
                addCandidate(elements, info);
            }
        }

        elements.sort(Comparator
                .comparingInt(RegisteredHudElement::order)
                .thenComparing(element -> element.type().getName()));

        for (RegisteredHudElement element : elements) {
            register(element.type(), includeDraggable, includeStatic);
        }
    }

    private static void register(Class<?> type, boolean includeDraggable, boolean includeStatic) {
        try {
            Object instance = instanceFor(type);

            if (instance instanceof DraggableHudElement draggable) {
                if (!includeDraggable) return;
                DraggableHudElementRegistry.register(draggable);
                return;
            }

            if (instance instanceof AbstractHudElement hudElement) {
                if (!includeStatic) return;
                StaticHudElementRegistry.register(hudElement);
                return;
            }

            DebugLog.config("Skipping @HudElementRegister non-HUD element: %s", type.getName());
        } catch (Throwable t) {
            DebugLog.error("Failed to load HUD element: %s", t, type.getName());
        }
    }

    private static void addCandidate(List<RegisteredHudElement> elements, ClassInfo info) {
        if (info.isAbstract()) return;

        try {
            Class<?> type = info.loadClass();
            int order = 0;
            HudElementInfo elementInfo = type.getAnnotation(HudElementInfo.class);
            if (elementInfo != null) {
                order = elementInfo.order();
            } else {
                HudElementRegister register = type.getAnnotation(HudElementRegister.class);
                if (register == null) return;
                order = register.order();
            }

            elements.add(new RegisteredHudElement(type, order));
        } catch (Throwable t) {
            DebugLog.error("Failed to inspect HUD element: %s", t, info.getName());
        }
    }

    private static Object instanceFor(Class<?> type) throws ReflectiveOperationException {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!type.isAssignableFrom(field.getType())) continue;

            field.setAccessible(true);
            Object instance = field.get(null);
            if (instance != null) return instance;
        }

        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private record RegisteredHudElement(Class<?> type, int order) {
    }
}
