/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.other;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;

public final class SearchComponent {
    public void render(float x, float y, float w, float h) {
        boolean typing = ClickGuiSearch.isActive();
        String text = ClickGuiSearch.getText();
        float scale = w / 80f;

        ClickGuiRenderer.drawBlur(x, y, w, h, 3f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(
                x, y, w, h, 3f * scale,
                LayoutRender2D.argb(155, 18, 19, 20),
                LayoutRender2D.argb(155, 5, 6, 7),
                LayoutRender2D.argb(155, 5, 6, 7),
                LayoutRender2D.argb(155, 18, 19, 20)
        );
        LayoutRender2D.roundedStroke(x, y, w, h, 3f * scale, 0.5f * scale, LayoutRender2D.argb(225, 18, 19, 20));

        float dividerX = x + 65.5f * scale;
        LayoutRender2D.rect(dividerX, y + 4f * scale, 0.5f * scale, h - 8f * scale, LayoutRender2D.argb(55, 155, 155, 155));

        String display = text.isEmpty() && !typing ? "Search" : text;
        float tx = x + 4f * scale;
        float ts = 12f * scale;
        float th = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterRegular(), ts);
        float ty = y + (h - th) * 0.5f - 0.5f * scale;
        boolean clipped = ScissorFunction.pushRaw(x + scale, y, w - 16f * scale, h);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                display,
                tx,
                ty,
                ts,
                typing ? 0xFFFFFFFF : 0xFF878894,
                false
        );
        if (typing && (System.currentTimeMillis() % 1000L < 500L)) {
            float cw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), text, ts);
            float caretY = ty + Math.max(0f, (th - 7f * scale) * 0.5f);
            ClickGuiRenderer.drawRect(tx + cw + 0.5f * scale, caretY, 0.5f * scale, 7f * scale, 0xFFFFFFFF);
        }
        if (clipped) ScissorFunction.pop();

        TextRenderer icons = Fonts.renderer("Icons", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        float iconSize = 8f * scale;
        String icon = "s";
        float iconW = ClickGuiRenderer.textWidth(icons, icon, iconSize);
        float iconH = ClickGuiRenderer.textHeight(icons, iconSize);
        float iconX = dividerX + (w - (dividerX - x) - iconW) * 0.5f;
        float iconY = y + (h - iconH) * 0.5f - 0.35f * scale;
        int iconColor = typing ? 0xFFF5F5FF : 0xFF878894;
        ClickGuiRenderer.drawText(icons, icon, iconX, iconY, iconSize, iconColor, false);
    }

    public boolean click(float x, float y, float w, float h, float mx, float my, int button) {
        if (button != 0) return false;
        boolean inside = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        if (inside) {
            ClickGuiSearch.setActive(true);
            return true;
        }
        ClickGuiSearch.deactivate();
        return false;
    }
}
