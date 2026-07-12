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
import combatant.client.render.engine.text.FontInfo;

public enum RenderPrewarmContributors {
    INSTANCE;

    @EventHandler(priority = 1000)
    private void onCollect(RenderPrewarmCollectEvent event) {
        collectCoreGui(event);
        collectEnabledModules(event);
        collectEnabledHud(event);
    }

    private static void collectCoreGui(RenderPrewarmCollectEvent event) {
        event.font("Inter", FontInfo.Type.Regular)
                .font("Inter", FontInfo.Type.Bold)
                .font("Inter", FontInfo.Type.Italic)
                .font("InterMedium", FontInfo.Type.Regular)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("Onest", FontInfo.Type.Regular)
                .font("Onest", FontInfo.Type.Bold)
                .font("OnestMedium", FontInfo.Type.Regular)
                .font("OnestBold", FontInfo.Type.Regular)
                .font("OnestLight", FontInfo.Type.Regular)
                .font("Monsterrat", FontInfo.Type.Regular)
                .font("Comfortaa", FontInfo.Type.Regular)
                .font("Icons", FontInfo.Type.Regular)
                .font("IconsNur", FontInfo.Type.Regular)
                .font("GuiIcons", FontInfo.Type.Regular)
                .font("RichIcons", FontInfo.Type.Regular);

        for (ModulesMenuCategory category : ModulesMenuCategory.values()) {
            event.svg(category.icon());
        }
        for (MenuScreen.Category category : MenuScreen.Category.values()) {
            if (category.svgIcon()) {
                event.svg(category.token());
            }
        }

        event.svg("arrow")
                .svg("check")
                .svg("save")
                .svg("x")
                .svg("palette")
                .svg("brush")
                .svg("paintbrush")
                .svg("copy")
                .svg("trash")
                .svg("folder-cog")
                .svg("user-pen")
                .svg("columns-3-cog");
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
