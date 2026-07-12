/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.relations;

import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.PlayerHeadRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class RelationPlayerCardComponent {
    public CardHit render(String name,
                          float x,
                          float y,
                          float w,
                          float h,
                          float mx,
                          float my,
                          float scale,
                          SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        float opacity = ClickGuiRenderer.getRenderAlphaMultiplier();
        if (opacity <= 0.01f) {
            return new CardHit(0f, 0f, 0f, 0f, null);
        }
        int top = hover ? SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategoryHoverLeft(), 0.14f) : palette.moduleCardTop();
        int topStrong = hover ? SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategoryHoverRight(), 0.12f) : palette.moduleCardTopStrong();
        int bottom = hover ? SettingsGuiPalette.mix(palette.moduleCardBottom(), palette.menuCategoryHoverLeft(), 0.18f) : palette.moduleCardBottom();
        int bottomStrong = hover ? SettingsGuiPalette.mix(palette.moduleCardBottomStrong(), palette.menuCategoryHoverRight(), 0.14f) : palette.moduleCardBottomStrong();

        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(x, y, w, h, 5f * scale, top, topStrong, bottom, bottomStrong);

        float head = 19f * scale;
        float headX = x + 7f * scale;
        float headY = y + (h - head) * 0.5f;
        renderHead(name, headX, headY, head, scale, palette, opacity);

        float textX = headX + head + 7f * scale;
        float titleSize = 8.8f * scale;
        float titleW = Math.max(1f, w - (textX - x) - 32f * scale);
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), name, titleSize, titleW);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                textX,
                y + (h - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), titleSize)) * 0.5f,
                titleSize,
                palette.moduleTitleText(),
                false
        );

        float btn = 16f * scale;
        float deleteX = x + w - 7f * scale - btn;
        float deleteY = y + (h - btn) * 0.5f;
        drawAction(deleteX, deleteY, btn, "trash-2", mx, my, scale, palette);

        return new CardHit(x, y, w, h, new ActionHit(deleteX, deleteY, btn, btn));
    }

    private void renderHead(String name, float x, float y, float size, float scale, SettingsGuiPalette palette, float opacity) {
        Identifier skin = SkinManager.getSkin(name);
        GuiGraphicsExtractor ctx = ViewportContext.getCurrentContext();
        if (skin != null && ctx != null) {
            int alpha = Math.round(255f * AnimationUtility.clamp01(opacity));
            PlayerHeadRenderer.drawRounded(
                    ctx,
                    x,
                    y,
                    size,
                    4f * scale,
                    skin,
                    new RenderColor(255, 255, 255, alpha),
                    true,
                    new RenderColor(255, 255, 255, Math.round(42f * AnimationUtility.clamp01(opacity))),
                    0.8f * scale,
                    false
            );
            return;
        }

        Renderer2D.COLOR.roundedRect(x, y, size, size, 4f * scale, 1.0f, LayoutRender2D.alpha(palette.panelMuted(), 0.22f * opacity));
        Renderer2D.COLOR.roundedRectStroke(x, y, size, size, 4f * scale, 1.0f, 0.55f * scale, LayoutRender2D.alpha(palette.panelMuted(), 0.62f * opacity));
    }

    private void drawAction(float x, float y, float size, String icon, float mx, float my, float scale, SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, size, size);
        int bgA = hover ? SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategoryHoverLeft(), 0.36f) : LayoutRender2D.alpha(palette.moduleCardTop(), 0.88f);
        int bgB = hover ? SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategoryHoverRight(), 0.28f) : LayoutRender2D.alpha(palette.moduleCardTopStrong(), 0.88f);
        LayoutRender2D.roundedQuad(x, y, size, size, 4f * scale, bgA, bgB, bgB, bgA);
        Renderer2D.COLOR.svg(
                icon,
                x + 4f * scale,
                y + 4f * scale,
                size - 8f * scale,
                size - 8f * scale,
                SvgRenderOptions.overrideColor(palette.menuCategoryText())
        );
    }

    public record CardHit(float x, float y, float w, float h, ActionHit delete) {
    }

    public record ActionHit(float x, float y, float w, float h) {
    }
}
