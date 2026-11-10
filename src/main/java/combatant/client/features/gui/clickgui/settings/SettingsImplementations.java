/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.settings;

import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.misc.ClickGui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects complete implementations, not a color skin. An implementation owns the entire
 * render/measure/input contract. JS-backed implementations may register through the same
 * contract using an adapter; the registry has no dependency on a particular UI language.
 */
public final class SettingsImplementations {
    public static final String AUTOMATIC = "native-auto";
    public static final String COMPACT = "native-compact";
    public static final String DETAILED = "native-detailed";

    private static final Map<String, SettingsImplementation> IMPLEMENTATIONS = new ConcurrentHashMap<>();

    static {
        registerBuiltin(new JavaSettingsImplementation(AUTOMATIC, null));
        registerBuiltin(new JavaSettingsImplementation(COMPACT, SettingRenderSurface.MODULES));
        registerBuiltin(new JavaSettingsImplementation(DETAILED, SettingRenderSurface.MAIN_SETTINGS));
    }

    private SettingsImplementations() { }

    private static void registerBuiltin(SettingsImplementation implementation) {
        IMPLEMENTATIONS.put(implementation.id(), implementation);
    }

    /** Register Java or script-backed settings implementations before choosing them. */
    public static synchronized void register(SettingsImplementation implementation) {
        if (implementation == null || implementation.id() == null || implementation.id().isBlank())
            throw new IllegalArgumentException("Settings implementation must have a non-empty id");
        if (IMPLEMENTATIONS.containsKey(implementation.id()))
            throw new IllegalArgumentException("Duplicate settings implementation: " + implementation.id());
        IMPLEMENTATIONS.put(implementation.id(), implementation);
        ClickGui gui = ModuleManager.get(ClickGui.class);
        if (gui != null) gui.registerSettingsImplementation(implementation.id());
    }

    public static synchronized Map<String, SettingsImplementation> available() {
        return Map.copyOf(IMPLEMENTATIONS);
    }

    public static SettingsImplementation active() {
        ClickGui gui = ModuleManager.get(ClickGui.class);
        String id = gui == null ? AUTOMATIC : gui.settingsImplementationId();
        return IMPLEMENTATIONS.getOrDefault(id, IMPLEMENTATIONS.get(AUTOMATIC));
    }
}
