/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import combatant.client.features.module.Module;
import combatant.client.features.module.modules.visuals.Chams;
import combatant.client.features.module.modules.visuals.ViewModel;
import combatant.client.features.gui.preview.provider.HandVisualPreviewProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Explicit module capability registry. Absence means no preview button. */
public enum VisualPreviewRegistry {
    ;

    private static final Map<String, Function<? super Module, ? extends VisualPreviewProvider>> MODULE_PROVIDERS =
            new ConcurrentHashMap<>();

    static {
        registerModule("chams", module -> module instanceof Chams
                ? new HandVisualPreviewProvider(module)
                : null);
        registerModule("viewmodel", module -> module instanceof ViewModel
                ? new HandVisualPreviewProvider(module)
                : null);
    }

    public static void registerModule(String moduleId,
                                      Function<? super Module, ? extends VisualPreviewProvider> providerFactory) {
        String id = normalize(moduleId);
        if (id.isEmpty() || providerFactory == null) return;
        MODULE_PROVIDERS.put(id, providerFactory);
    }

    public static void unregisterModule(String moduleId) {
        MODULE_PROVIDERS.remove(normalize(moduleId));
    }

    public static boolean supports(Module module) {
        return module != null && MODULE_PROVIDERS.containsKey(normalize(module.name()));
    }

    public static VisualPreviewProvider providerFor(Module module) {
        if (module == null) return null;
        Function<? super Module, ? extends VisualPreviewProvider> factory =
                MODULE_PROVIDERS.get(normalize(module.name()));
        return factory == null ? null : factory.apply(module);
    }

    public static boolean open(Module module) {
        VisualPreviewProvider provider = providerFor(module);
        if (provider == null) return false;
        VisualPreviewScreen.open(provider);
        return true;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
