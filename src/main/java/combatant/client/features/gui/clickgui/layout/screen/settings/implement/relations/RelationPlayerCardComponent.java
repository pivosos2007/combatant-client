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
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Compact relation-row/profile renderer.
 *
 * Relation entries intentionally do not cast individual outer shadows anymore.
 * The list lives on a single rounded management surface, so per-row shadows only
 * made the old screen noisy and exposed hard viewport clipping.
 */
public final class RelationPlayerCardComponent {

    public CardHit renderRow(String name,
                             String relationLabel,
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

        float radius = 4.5f * scale;
        int baseA = SettingsGuiPalette.withAlpha(palette.controlSurface(), selected ? 178 : (hover ? 142 : 104));
        int baseB = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(palette.controlSurfaceHover(), relationColor, selected ? 0.18f : (hover ? 0.08f : 0.02f)),
                selected ? 184 : (hover ? 148 : 108)
        );
        LayoutRender2D.roundedQuad(x, y, w, h, radius, baseA, baseB, baseB, baseA);

        int stroke = selected
                ? SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelStroke(), relationColor, 0.54f), 212)
                : SettingsGuiPalette.withAlpha(palette.glassEdgeSoft(), hover ? 112 : 74);
        LayoutRender2D.roundedStroke(x, y, w, h, radius, 0.55f * scale, stroke);

        float head = Math.min(h - 6f * scale, 15f * scale);
        float headX = x + 5f * scale;
        float headY = y + (h - head) * 0.5f;
        renderHead(name, headX, headY, head, 3.5f * scale, scale, palette, 1f);

        float textX = headX + head + 5.5f * scale;
        float titleSize = 7.5f * scale;
        float labelSize = 5.35f * scale;
        float rightPad = 8f * scale;
        float titleW = Math.max(1f, w - (textX - x) - rightPad);
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), name, titleSize, titleW);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                textX,
                y + 4.2f * scale,
                titleSize,
                palette.moduleTitleText(),
                false
        );

        String relation = ClickGuiRenderer.fitText(
                ClickGuiRenderer.getInterRegular(),
                relationLabel == null ? "" : relationLabel,
                labelSize,
                titleW
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                relation,
                textX,
                y + h - 8.7f * scale,
                labelSize,
                SettingsGuiPalette.mix(palette.panelMuted(), relationColor, selected ? 0.34f : 0.18f),
                false
        );

        float dot = 4.2f * scale;
        Renderer2D.COLOR.roundedRect(
                x + w - 7.5f * scale,
                y + (h - dot) * 0.5f,
                dot,
                dot,
                dot * 0.5f,
                0.75f,
                SettingsGuiPalette.withAlpha(relationColor, selected ? 244 : 176)
        );
        return new CardHit(x, y, w, h);
    }

    public void renderProfile(String name,
                              String relationLabel,
                              int relationColor,
                              float x,
                              float y,
                              float w,
                              float h,
                              float scale,
                              SettingsGuiPalette palette) {
        float head = Math.min(31f * scale, h - 8f * scale);
        float headX = x;
        float headY = y + (h - head) * 0.5f;
        renderHead(name, headX, headY, head, 6f * scale, scale, palette, 1f);

        float textX = headX + head + 8f * scale;
        float titleSize = 10.2f * scale;
        float titleW = Math.max(1f, w - (textX - x));
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), name, titleSize, titleW);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                textX,
                y + 4.0f * scale,
                titleSize,
                palette.panelText(),
                false
        );

        float labelSize = 6.0f * scale;
        String relation = relationLabel == null ? "" : relationLabel;
        float labelW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), relation, labelSize);
        float pillW = Math.min(titleW, labelW + 12f * scale);
        float pillH = 11.5f * scale;
        float pillY = y + h - pillH - 3.0f * scale;
        int pillA = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelPillBase(), relationColor, 0.25f), 150);
        int pillB = SettingsGuiPalette.withAlpha(SettingsGuiPalette.mix(palette.panelPillActive(), relationColor, 0.16f), 160);
        LayoutRender2D.roundedQuad(textX, pillY, pillW, pillH, 3.4f * scale, pillA, pillB, pillB, pillA);
        LayoutRender2D.roundedStroke(
                textX,
                pillY,
                pillW,
                pillH,
                3.4f * scale,
                0.45f * scale,
                SettingsGuiPalette.withAlpha(relationColor, 154)
        );
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), relation, labelSize, pillW - 8f * scale),
                textX + 4f * scale,
                pillY + 2.7f * scale,
                labelSize,
                palette.panelText(),
                false
        );
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

    public record CardHit(float x, float y, float w, float h) {
    }
}
