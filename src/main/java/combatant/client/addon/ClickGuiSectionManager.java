/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.clickgui.CombatantClickGuiSection;
import combatant.client.api.v0.clickgui.CombatantClickGuiRenderContext;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.sections.ClickGuiSection;
import combatant.client.features.gui.clickgui.sections.ClickGuiSectionInfo;
import combatant.client.util.logging.DebugLog;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum ClickGuiSectionManager {
    ;

    private static final String BUILTIN_PACKAGE = "combatant.client";
    private static final Map<String, OrderedEntry> BUILTIN_SECTIONS = new LinkedHashMap<>();
    private static final Map<String, Entry> ADDON_SECTIONS = new LinkedHashMap<>();
    private static boolean discoveryComplete;

    public static synchronized boolean register(String addonId,
                                                String sectionId,
                                                String label,
                                                CombatantClickGuiSection section) {
        String normalizedAddonId = normalize(addonId);
        String normalizedSectionId = normalize(sectionId);
        if (normalizedAddonId.isBlank() || normalizedSectionId.isBlank() || section == null) {
            return false;
        }
        String id = normalizedAddonId + ":" + normalizedSectionId;
        ensureBuiltinsDiscovered();
        if (BUILTIN_SECTIONS.containsKey(id) || ADDON_SECTIONS.containsKey(id)) {
            return false;
        }
        String title = label == null || label.isBlank() ? normalizedSectionId : label.trim();
        ADDON_SECTIONS.put(id, new Entry(
                normalizedAddonId,
                id,
                normalizedSectionId,
                title,
                new ApiSectionAdapter(section)
        ));
        return true;
    }

    public static synchronized void unregisterAddon(String addonId) {
        String normalizedAddonId = normalize(addonId);
        ADDON_SECTIONS.entrySet().removeIf(entry -> {
            if (!entry.getValue().addonId().equals(normalizedAddonId)) {
                return false;
            }
            try {
                entry.getValue().section().onDeselected();
            } catch (Throwable ignored) {
            }
            return true;
        });
    }

    public static synchronized List<Entry> sections() {
        ensureBuiltinsDiscovered();
        ArrayList<Entry> sections = new ArrayList<>(BUILTIN_SECTIONS.size() + ADDON_SECTIONS.size());
        BUILTIN_SECTIONS.values().stream()
                .sorted(Comparator.comparingInt(OrderedEntry::order).thenComparing(entry -> entry.value().id()))
                .map(OrderedEntry::value)
                .filter(ClickGuiSectionManager::isAvailable)
                .forEach(sections::add);
        ADDON_SECTIONS.values().stream()
                .filter(entry -> AddonManager.isActive(entry.addonId()))
                .forEach(sections::add);
        return List.copyOf(sections);
    }

    private static void ensureBuiltinsDiscovered() {
        if (discoveryComplete) {
            return;
        }
        discoveryComplete = true;

        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .acceptPackages(BUILTIN_PACKAGE)
                .scan()) {
            List<ClassInfo> candidates = new ArrayList<>(
                    scan.getClassesWithAnnotation(ClickGuiSectionInfo.class.getName())
            );
            candidates.sort(Comparator.comparing(ClassInfo::getName));
            for (ClassInfo candidate : candidates) {
                registerBuiltin(candidate.getName());
            }
        } catch (Throwable t) {
            DebugLog.warnOnce("clickgui-section-discovery", "Failed to discover ClickGUI sections", t);
        }
    }

    private static void registerBuiltin(String className) {
        try {
            Class<?> type = Class.forName(className, false, ClickGuiSectionManager.class.getClassLoader());
            ClickGuiSectionInfo info = type.getAnnotation(ClickGuiSectionInfo.class);
            if (info == null || !ClickGuiSection.class.isAssignableFrom(type)) {
                DebugLog.warn("Skipping invalid ClickGUI section: %s", className);
                return;
            }
            for (String requiredMod : info.requiredMods()) {
                String modId = normalize(requiredMod);
                if (!modId.isBlank() && !FabricLoader.getInstance().isModLoaded(modId)) return;
            }

            int modifiers = type.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers) || type.isAnnotation()) {
                DebugLog.warn("Skipping non-concrete ClickGUI section: %s", className);
                return;
            }

            String id = normalizeQualifiedId(info.id());
            if (id.isBlank()) {
                DebugLog.warn("Skipping ClickGUI section with an invalid id: %s", className);
                return;
            }
            if (BUILTIN_SECTIONS.containsKey(id) || ADDON_SECTIONS.containsKey(id)) {
                DebugLog.warn("Skipping duplicate ClickGUI section id '%s': %s", id, className);
                return;
            }

            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            ClickGuiSection section = (ClickGuiSection) constructor.newInstance();
            String localId = id.substring(id.indexOf(':') + 1);
            String label = info.label().isBlank() ? localId : info.label().trim();
            BUILTIN_SECTIONS.put(id, new OrderedEntry(
                    info.order(),
                    new Entry("combatant", id, localId, label, section)
            ));
            DebugLog.config("Loaded ClickGUI section: %s", className);
        } catch (NoSuchMethodException e) {
            DebugLog.warnOnce(
                    "clickgui-section-load:" + className,
                    "Failed to load ClickGUI section %s: no no-args constructor",
                    className,
                    e
            );
        } catch (Throwable t) {
            DebugLog.warnOnce(
                    "clickgui-section-load:" + className,
                    "Failed to load ClickGUI section: %s",
                    className,
                    t
            );
        }
    }

    private static boolean isAvailable(Entry entry) {
        try {
            return entry.section().isAvailable();
        } catch (Throwable t) {
            DebugLog.warnOnce(
                    "clickgui-section-availability:" + entry.id(),
                    "ClickGUI section is unavailable: %s",
                    entry.id(),
                    t
            );
            return false;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeQualifiedId(String value) {
        String id = normalize(value);
        if (id.isBlank()) {
            return "";
        }
        if (!id.contains(":")) {
            id = "combatant:" + id;
        }
        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1 || id.indexOf(':', separator + 1) >= 0) {
            return "";
        }
        return id;
    }

    public record Entry(String addonId,
                        String id,
                        String localId,
                        String label,
                        ClickGuiSection section) {
    }

    private record OrderedEntry(int order, Entry value) {
    }

    private record ApiSectionAdapter(CombatantClickGuiSection section) implements ClickGuiSection {
        @Override
        public void layout(float x, float y, float w, float h) {
            section.layout(x, y, w, h);
        }

        @Override
        public void render(float mouseX, float mouseY) {
            section.render(
                    new CombatantClickGuiRenderContext(
                            ClickGuiRenderer.currentRenderer(),
                            ClickGuiRenderer.getInterRegular(),
                            ClickGuiRenderer.getInterMedium()
                    ),
                    mouseX,
                    mouseY
            );
        }

        @Override
        public void renderGlassPass(float alphaFactor) {
            section.renderGlassPass(alphaFactor);
        }

        @Override
        public boolean mousePressed(float mouseX, float mouseY, int button) {
            return section.mousePressed(mouseX, mouseY, button);
        }

        @Override
        public void mouseReleased(float mouseX, float mouseY, int button) {
            section.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
            return section.mouseScrolled(mouseX, mouseY, amount);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return section.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            return section.charTyped(chr, modifiers);
        }

        @Override
        public void onSelected() {
            section.onSelected();
        }

        @Override
        public void onDeselected() {
            section.onDeselected();
        }

        @Override
        public boolean isVisible() {
            return section.isVisible();
        }
    }
}
