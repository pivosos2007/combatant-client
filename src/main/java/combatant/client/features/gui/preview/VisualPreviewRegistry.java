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
import combatant.client.features.module.modules.visuals.PlayerModel;
import combatant.client.features.gui.preview.provider.PlayerModelVisualPreviewProvider;
import net.minecraft.client.resources.language.I18n;
import java.util.Arrays;
import java.util.List;
import combatant.client.features.gui.preview.provider.HandVisualPreviewProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Explicit module capability registry. Absence means no preview button. */
public enum VisualPreviewRegistry {
    ;

    private static final Map<String, Function<? super Module, ? extends VisualPreviewProvider>> MODULE_PROVIDERS =
            new ConcurrentHashMap<>();
    private static final Map<String, Function<? super Module, List<PreviewAction>>> MODULE_ACTIONS =
            new ConcurrentHashMap<>();

    /** Optional named preview actions; independent of the default single-preview contract. */
    public record PreviewAction(String id, String label,
                                Function<? super Module, ? extends VisualPreviewProvider> provider) {}

    static {
        registerModule("playermodel", module -> module instanceof PlayerModel playerModel
                ? new PlayerModelVisualPreviewProvider(playerModel) : null);
        registerModuleActions("playermodel", module -> module instanceof PlayerModel
                ? Arrays.stream(PlayerModel.Target.values())
                    .map(target -> new PreviewAction(target.key(),
                            I18n.get("clickgui.modules.visual_preview") + " — " + target.label(),
                            owner -> new PlayerModelVisualPreviewProvider((PlayerModel) owner, target)))
                    .toList()
                : List.of());
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

    public static void registerModuleActions(String moduleId,
                                             Function<? super Module, List<PreviewAction>> actions) {
        String id = normalize(moduleId);
        if (!id.isEmpty() && actions != null) MODULE_ACTIONS.put(id, actions);
    }

    public static void unregisterModule(String moduleId) {
        MODULE_PROVIDERS.remove(normalize(moduleId));
        MODULE_ACTIONS.remove(normalize(moduleId));
    }

    public static boolean supports(Module module) {
        return module != null && (MODULE_PROVIDERS.containsKey(normalize(module.name()))
                || MODULE_ACTIONS.containsKey(normalize(module.name())));
    }

    public static List<PreviewAction> actions(Module module) {
        if (module == null) return List.of();
        Function<? super Module, List<PreviewAction>> factory = MODULE_ACTIONS.get(normalize(module.name()));
        if (factory != null) {
            List<PreviewAction> result = factory.apply(module);
            return result == null ? List.of() : List.copyOf(result);
        }
        Function<? super Module, ? extends VisualPreviewProvider> provider = MODULE_PROVIDERS.get(normalize(module.name()));
        return provider == null ? List.of() : List.of(new PreviewAction("default",
                I18n.get("clickgui.modules.visual_preview"), provider));
    }

    public static VisualPreviewProvider providerFor(Module module) {
        if (module == null) return null;
        Function<? super Module, ? extends VisualPreviewProvider> factory =
                MODULE_PROVIDERS.get(normalize(module.name()));
        return factory == null ? null : factory.apply(module);
    }

    public static boolean open(Module module) {
        List<PreviewAction> available = actions(module);
        return !available.isEmpty() && open(module, available.getFirst().id());
    }

    public static boolean open(Module module, String actionId) {
        if (module == null || actionId == null) return false;
        for (PreviewAction action : actions(module)) {
            if (!action.id().equals(actionId)) continue;
            VisualPreviewProvider provider = action.provider().apply(module);
            if (provider == null) return false;
            VisualPreviewScreen.open(provider);
            return true;
        }
        return false;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
