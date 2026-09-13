/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.prewarm;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.features.gui.clickgui.layout.screen.modules.ModulesMenuCategory;
import combatant.client.features.gui.clickgui.layout.screen.settings.MenuScreen;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import combatant.client.util.resources.asset.AssetAutoLoader;

import java.util.List;

public enum RenderPrewarmContributors {
    INSTANCE;

    /**
     * Usage/performance hints only. SVG discovery/loading remains owned by SvgRegistry; adding an
     * icon here merely asks the MSDF prewarm pass to prepare a likely first-frame dependency.
     */
    private static final List<String> CORE_GUI_SVG_USAGE_HINTS = List.of(
            "arrow", "check", "save", "x", "palette", "brush", "paintbrush", "copy",
            "trash", "folder-cog", "folder-pen", "copyleft", "user-pen", "columns-3-cog"
    );

    @EventHandler(priority = 1000)
    private void onCollect(RenderPrewarmCollectEvent event) {
        collectCoreGui(event);
        collectEnabledModules(event);
        collectEnabledHud(event);
    }

    private static void collectCoreGui(RenderPrewarmCollectEvent event) {
        for (AssetAutoLoader.FontDefinition font : AssetAutoLoader.fontAssets()) {
            if (font.prewarm()) {
                event.font(font.info());
            }
        }

        for (ModulesMenuCategory category : ModulesMenuCategory.values()) {
            event.svg(category.icon());
        }
        for (MenuScreen.Category category : MenuScreen.Category.values()) {
            if (category.svgIcon()) {
                event.svg(category.token());
            }
        }

        for (String svgHint : CORE_GUI_SVG_USAGE_HINTS) {
            event.svg(svgHint);
        }
    }

    private static void collectEnabledModules(RenderPrewarmCollectEvent event) {
        for (Module module : ModuleManager.getModules()) {
            if (module != null && module.isEnabled()) {
                module.collectRenderPrewarm(event);
            }
        }
    }

    private static void collectEnabledHud(RenderPrewarmCollectEvent event) {
        for (DraggableHudElement element : DraggableHudElementRegistry.getWidgets()) {
            if (element != null && element.isEnabled()) {
                element.collectRenderPrewarm(event);
            }
        }
        for (AbstractHudElement element : StaticHudElementRegistry.getAll()) {
            if (element != null && element.isEnabled()) {
                element.collectRenderPrewarm(event);
            }
        }
    }
}
