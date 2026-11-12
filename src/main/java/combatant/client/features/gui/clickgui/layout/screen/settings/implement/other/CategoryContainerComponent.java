/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.other;

import combatant.client.features.gui.clickgui.layout.screen.settings.MenuScreen;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.category.CategoryComponent;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import java.util.ArrayList;
import java.util.List;

public final class CategoryContainerComponent {
    private final List<CategoryComponent> categoryComponents = new ArrayList<>();

    public CategoryContainerComponent() {
        for (MenuScreen.Category category : MenuScreen.Category.values()) {
            categoryComponents.add(new CategoryComponent(category));
        }
    }

    public void render(float x, float y, float mx, float my, MenuScreen.Category selected) {
        float offset = 0f;
        for (CategoryComponent component : categoryComponents) {
            component.render(x + 12f, y + offset, mx, my, selected);
            offset += 58f;
            if (component.category() == MenuScreen.Category.THEMES) {
                renderSeparator(x, y + offset + 10f);
                offset += 20f;
            } else if (component.category() == MenuScreen.Category.RELATIONS) {
                renderSeparator(x, y + offset - 8f);
                offset += 20f;
            }
        }
    }

    public MenuScreen.Category click(float x, float y, float mx, float my) {
        float offset = 0f;
        for (CategoryComponent component : categoryComponents) {
            if (component.click(x + 12f, y + offset, mx, my)) {
                return component.category();
            }
            offset += 58f;
            if (component.category() == MenuScreen.Category.THEMES
                    || component.category() == MenuScreen.Category.RELATIONS) {
                offset += 20f;
            }
        }
        return null;
    }

    private void renderSeparator(float x, float y) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float sx = x + 16f;
        float sy = y;
        float sw = 52f;
        float sh = 1f;
        LayoutRender2D.rectQuad(
                sx,
                sy,
                sw,
                sh,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.65f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.72f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.72f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.65f)
        );
    }
}
