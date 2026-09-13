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
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Single-line relation entry used by the simplified Relations screen.
 */
public final class RelationPlayerCardComponent {

    public CardHit renderRow(String name,
                             int relationColor,
                             boolean selected,
                             float x,
                             float y,
                             float w,
                             float h,
                             float mx,
                             float my,
                             float scale,
                             SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        if (hover) SystemCursor.set(SystemCursor.CursorType.HAND);

        float radius = 5.0f * scale;
        int baseA = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurface(), relationColor, selected ? 0.08f : 0.0f),
                selected ? 154 : (hover ? 132 : 88)
        );
        int baseB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), relationColor, selected ? 0.18f : (hover ? 0.09f : 0.02f)),
                selected ? 166 : (hover ? 142 : 94)
        );
        LayoutRender2D.roundedQuad(x, y, w, h, radius, baseA, baseB, baseB, baseA);

        int stroke = selected
                ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), relationColor, 0.50f), 194)
                : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), hover ? 100 : 58);
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.5f * scale, stroke);

        float head = Math.min(h - 7f * scale, 18f * scale);
        float headX = x + 5f * scale;
        float headY = y + (h - head) * 0.5f;
        renderHead(name, headX, headY, head, 4.0f * scale, scale, palette, 1f);

        float deleteSize = 15f * scale;
        float deleteX = x + w - deleteSize - 4f * scale;
        float deleteY = y + (h - deleteSize) * 0.5f;
        boolean deleteVisible = hover || selected;
        boolean deleteHover = deleteVisible && ClickGuiMath.insideRect(mx, my, deleteX, deleteY, deleteSize, deleteSize);

        float textX = headX + head + 6f * scale;
        float rightEdge = deleteVisible ? deleteX - 5f * scale : x + w - 7f * scale;
        float titleW = Math.max(1f, rightEdge - textX);
        float titleSize = 8.2f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), name, titleSize, titleW);
        float titleY = y + (h - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), titleSize)) * 0.5f;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                textX,
                titleY,
                titleSize,
                palette.moduleTitleText(),
                false
        );

        if (deleteVisible) {
            if (deleteHover) {
                int danger = 0xFFFF6B6B;
                int bg = SettingsGuiPalette.withAlpha(
                        SettingsGuiPalette.mix(palette.controlSurfaceHover(), danger, 0.16f),
                        142
                );
                LayoutRender2D.roundedQuad(
                        deleteX, deleteY, deleteSize, deleteSize, 4f * scale,
                        bg, bg, bg, bg
                );
            }
            float icon = 7.0f * scale;
            int danger = 0xFFFF6B6B;
            int iconColor = deleteHover
                    ? SettingsGuiPalette.mix(palette.menuCategoryText(), danger, 0.56f)
                    : SettingsGuiPalette.mix(palette.panelMuted(), danger, selected ? 0.36f : 0.22f);
            Renderer2D.COLOR.svg(
                    "trash-2",
                    deleteX + (deleteSize - icon) * 0.5f,
                    deleteY + (deleteSize - icon) * 0.5f,
                    icon,
                    icon,
                    SvgRenderOptions.overrideColor(iconColor)
            );
        }

        return new CardHit(x, y, w, h, deleteX, deleteY, deleteSize, deleteSize, deleteVisible);
    }

    private void renderHead(String name,
                            float x,
                            float y,
                            float size,
                            float radius,
                            float scale,
                            SettingsGuiPalette palette,
                            float opacity) {
        Identifier skin = SkinManager.getSkin(name);
        GuiGraphicsExtractor ctx = ViewportContext.getCurrentContext();
        if (skin != null && ctx != null) {
            int alpha = Math.round(255f * AnimationUtility.clamp01(opacity));
            PlayerHeadRenderer.drawRounded(
                    ctx,
                    x,
                    y,
                    size,
                    radius,
                    skin,
                    new RenderColor(255, 255, 255, alpha),
                    true,
                    new RenderColor(255, 255, 255, Math.round(38f * AnimationUtility.clamp01(opacity))),
                    0.65f * scale,
                    false
            );
            return;
        }

        Renderer2D.COLOR.roundedRect(
                x,
                y,
                size,
                size,
                radius,
                1.0f,
                LayoutRender2D.alpha(palette.panelMuted(), 0.22f * opacity)
        );
        Renderer2D.COLOR.roundedRectStroke(
                x,
                y,
                size,
                size,
                radius,
                1.0f,
                0.5f * scale,
                LayoutRender2D.alpha(palette.panelMuted(), 0.58f * opacity)
        );
    }

    public record CardHit(float x,
                          float y,
                          float w,
                          float h,
                          float deleteX,
                          float deleteY,
                          float deleteW,
                          float deleteH,
                          boolean deleteVisible) {
        public boolean contains(float mx, float my) {
            return ClickGuiMath.insideRect(mx, my, x, y, w, h);
        }

        public boolean containsDelete(float mx, float my) {
            return deleteVisible && ClickGuiMath.insideRect(mx, my, deleteX, deleteY, deleteW, deleteH);
        }
    }
}
