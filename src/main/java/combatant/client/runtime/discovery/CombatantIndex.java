/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.discovery;

import combatant.client.util.logging.DebugLog;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime view of the component indexes emitted by the annotation processor. */
public enum CombatantIndex {
    ;

    private static final String[] INDEX_NAMES = {"main", "dev", "vulkanDebug"};
    private static volatile Map<Kind, List<String>> entries;

    public static List<String> entries(Kind kind) {
        return load().getOrDefault(kind, List.of());
    }

    public static List<String> entries(Kind kind, String basePackage) {
        List<String> names = entries(kind);
        if (basePackage == null || basePackage.isBlank()) return names;
        String prefix = basePackage.endsWith(".") ? basePackage : basePackage + ".";
        ArrayList<String> filtered = new ArrayList<>();
        for (String name : names) {
            if (name.equals(basePackage) || name.startsWith(prefix)) filtered.add(name);
        }
        return List.copyOf(filtered);
    }

    public static List<String> classNames(Kind kind) {
        return entries(kind);
    }

    public static List<String> classNames(Kind kind, String basePackage) {
        return entries(kind, basePackage);
    }

    private static Map<Kind, List<String>> load() {
        Map<Kind, List<String>> snapshot = entries;
        if (snapshot != null) return snapshot;
        synchronized (CombatantIndex.class) {
            snapshot = entries;
            if (snapshot != null) return snapshot;

            EnumMap<Kind, Set<String>> collected = new EnumMap<>(Kind.class);
            ClassLoader loader = CombatantIndex.class.getClassLoader();
            for (String name : INDEX_NAMES) {
                readResources(loader, "META-INF/combatant/index/" + name + ".idx", collected);
            }

            EnumMap<Kind, List<String>> frozen = new EnumMap<>(Kind.class);
            for (Map.Entry<Kind, Set<String>> entry : collected.entrySet()) {
                ArrayList<String> names = new ArrayList<>(entry.getValue());
                names.sort(String.CASE_INSENSITIVE_ORDER);
                frozen.put(entry.getKey(), List.copyOf(names));
            }
            entries = Collections.unmodifiableMap(frozen);
            return entries;
        }
    }

    private static void readResources(ClassLoader loader,
                                      String resourceName,
                                      EnumMap<Kind, Set<String>> collected) {
        try {
            Enumeration<URL> resources = loader.getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        resource.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank() || line.startsWith("#")) continue;
                        int split = line.indexOf('\t');
                        if (split <= 0 || split == line.length() - 1) continue;
                        Kind kind = Kind.valueOf(line.substring(0, split));
                        collected.computeIfAbsent(kind, ignored -> new LinkedHashSet<>())
                                .add(line.substring(split + 1));
                    }
                }
            }
        } catch (Throwable error) {
            DebugLog.error("Failed to read generated component index %s", error, resourceName);
        }
    }

    public enum Kind {
        MODULE,
        MODULE_LIST_INFO_PROVIDER,
        HUD,
        COMMAND,
        CLICK_GUI,
        SOUND,
        ASSET_HOOK,
        TEXTURE_CATALOG,
        FONT_CATALOG,
        SCRIPT_CATALOG,
        RESOURCE_CATALOG,
        UI_SCRIPT,
        PIPELINE,
        CONFIG
    }
}
