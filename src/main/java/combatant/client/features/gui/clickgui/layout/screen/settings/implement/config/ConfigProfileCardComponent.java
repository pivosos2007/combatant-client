/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.config.profile.ConfigProfileDateFormatter;
import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;

public final class ConfigProfileCardComponent {
    public CardHit render(ConfigProfileMeta meta,
                          float x,
                          float y,
                          float w,
                          float h,
                          float mx,
                          float my,
                          boolean selected,
                          float scale,
                          SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        int top = selected ? SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), 0.16f) : palette.moduleCardTop();
        int topStrong = selected ? SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), 0.16f) : palette.moduleCardTopStrong();
        int bottom = hover ? SettingsGuiPalette.mix(palette.moduleCardBottom(), palette.menuCategoryHoverLeft(), 0.18f) : palette.moduleCardBottom();
        int bottomStrong = hover ? SettingsGuiPalette.mix(palette.moduleCardBottomStrong(), palette.menuCategoryHoverRight(), 0.14f) : palette.moduleCardBottomStrong();

        try (var transition = SettingsCardTransition.beginCard(x, y, w, h, 5f * scale, scale, palette)) {
        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(x, y, w, h, 5f * scale, top, topStrong, bottom, bottomStrong);

        float iconBox = 15f * scale;
        float iconBoxX = x + 6f * scale;
        float iconBoxY = y + 4.2f * scale;
        Renderer2D.COLOR.roundedRectCorners(
                iconBoxX,
                iconBoxY,
                iconBox,
                iconBox,
                4f * scale,
                4f * scale,
                4f * scale,
                4f * scale,
                1.1f,
                LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selected ? 0.88f : 0.62f)
        );
        Renderer2D.COLOR.svg(
                "folder-cog",
                iconBoxX + 3.2f * scale,
                iconBoxY + 3.2f * scale,
                8.6f * scale,
                8.6f * scale,
                SvgRenderOptions.overrideColor(palette.menuCategoryText())
        );

        float textX = x + 25f * scale;
        float titleSize = 8.8f * scale;
        float titleX = textX;
        float titleY = y + 3.8f * scale;
        float titleW = w - 72f * scale;
        float titleH = 10f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), meta.name(), titleSize, titleW);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), title, titleX, titleY, titleSize, palette.moduleTitleText(), false);

        float authorSize = 6.8f * scale;
        String author = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), meta.author(), authorSize, w - 72f * scale);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), author, textX, y + 14.4f * scale, authorSize, palette.moduleDescriptionText(), false);

        LayoutRender2D.rectQuad(
                x,
                y + 23f * scale,
                w,
                0.5f * scale,
                palette.moduleDividerStart(),
                palette.moduleDividerEnd(),
                palette.moduleDividerEnd(),
                palette.moduleDividerStart()
        );

        float rowSize = 7.2f * scale;
        float labelX = x + 7f * scale;
        float valueX = x + 50f * scale;
        float row1Y = y + 28f * scale;
        float row2Y = y + 38f * scale;
        int labelColor = palette.moduleDescriptionIcon();
        int valueColor = palette.moduleDescriptionText();

        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), ClickGuiI18n.tr("clickgui.settings.config.card.created", "Created:"), labelX, row1Y, rowSize, labelColor, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), ConfigProfileDateFormatter.created(meta), rowSize, w - 110f * scale),
                valueX,
                row1Y,
                rowSize,
                valueColor,
                false
        );
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), ClickGuiI18n.tr("clickgui.settings.config.card.updated", "Updated:"), labelX, row2Y, rowSize, labelColor, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(),
                ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), ConfigProfileDateFormatter.updated(meta), rowSize, w - 110f * scale),
                valueX,
                row2Y,
                rowSize,
                valueColor,
                false
        );

        float btn = 16f * scale;
        float gap = 4f * scale;
        float by = y + h - 20f * scale;
        float trashX = x + w - 7f * scale - btn;
        float downloadX = trashX - gap - btn;
        float diffX = downloadX - gap - btn;

        drawAction(diffX, by, btn, "diff", mx, my, scale, palette);
        drawAction(downloadX, by, btn, "download", mx, my, scale, palette);
        drawAction(trashX, by, btn, "trash-2", mx, my, scale, palette);

        return new CardHit(x, y, w, h,
                new ActionHit(titleX, titleY - scale, titleW, titleH, CardActionType.RENAME),
                new ActionHit(diffX, by, btn, btn, CardActionType.DIFF),
                new ActionHit(downloadX, by, btn, btn, CardActionType.APPLY),
                new ActionHit(trashX, by, btn, btn, CardActionType.DELETE));
        }
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

    public enum CardActionType {SELECT, RENAME, DIFF, APPLY, DELETE}

    public record CardHit(float x, float y, float w, float h, ActionHit title, ActionHit diff, ActionHit apply,
                          ActionHit delete) {
    }

    public record ActionHit(float x, float y, float w, float h, CardActionType type) {
    }
}
