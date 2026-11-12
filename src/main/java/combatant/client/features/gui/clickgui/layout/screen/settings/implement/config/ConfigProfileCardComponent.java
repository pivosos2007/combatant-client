/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.config.profile.ConfigProfileDateFormatter;
import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsCardTransition;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigProfileCardComponent {
    private final Map<String, Float> bindingStrokeOpacity = new HashMap<>();
    public CardHit render(ConfigProfileMeta meta,
                          float x,
                          float y,
                          float w,
                          float h,
                          float mx,
                          float my,
                          boolean selected,
                          boolean activeProfile,
                          boolean currentBound,
                          List<String> boundContexts,
                          SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, w, h);
        float selectedMix = selected ? 0.16f : activeProfile ? 0.12f : 0f;
        int top = SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategorySelectedLeft(), selectedMix);
        int topStrong = SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategorySelectedRight(), selectedMix);
        int bottom = hover ? SettingsGuiPalette.mix(palette.moduleCardBottom(), palette.menuCategoryHoverLeft(), 0.18f) : palette.moduleCardBottom();
        int bottomStrong = hover ? SettingsGuiPalette.mix(palette.moduleCardBottomStrong(), palette.menuCategoryHoverRight(), 0.14f) : palette.moduleCardBottomStrong();

        try (var transition = SettingsCardTransition.beginCard()) {
        LayoutRender2D.roundedQuad(x, y, w, h, 10f, top, topStrong, bottom, bottomStrong);
        String bindingKey = meta.type().folderName() + ':' + meta.getId();
        float opacity = bindingStrokeOpacity.getOrDefault(bindingKey, 0f);
        opacity = combatant.client.render.engine.animation.AnimationUtility.snap(
                combatant.client.render.engine.animation.AnimationUtility.approach(opacity, currentBound ? 1f : 0f,
                        combatant.client.render.engine.animation.AnimationUtility.deltaTime(), 12f),
                currentBound ? 1f : 0f, 0.005f);
        if (opacity > 0f) {
            bindingStrokeOpacity.put(bindingKey, opacity);
            float phase = combatant.client.render.engine.animation.AnimationUtility.time(1f / 6400f);
            LayoutRender2D.roundedStrokeAngular(x + 0.9f, y + 0.9f,
                    w - 1.8f, h - 1.8f, 10f, 1.7f,
                    LayoutRender2D.alpha(palette.menuCategorySelectedLeft(), 0.55f * opacity),
                    LayoutRender2D.alpha(palette.menuCategorySelectedRight(), 0.72f * opacity), phase);
        } else {
            bindingStrokeOpacity.remove(bindingKey);
        }

        float iconBox = 30f;
        float iconBoxX = x + 12f;
        float iconBoxY = y + 8.4f;
        Renderer2D.COLOR.roundedRectCorners(
                iconBoxX,
                iconBoxY,
                iconBox,
                iconBox,
                8f,
                8f,
                8f,
                8f,
                1.1f,
                LayoutRender2D.alpha(palette.menuCategorySelectedRight(), selected ? 0.88f : 0.62f)
        );
        Renderer2D.COLOR.svg(
                "folder-cog",
                iconBoxX + 6.4f,
                iconBoxY + 6.4f,
                17.2f,
                17.2f,
                SvgRenderOptions.overrideColor(palette.menuCategoryText())
        );

        float textX = x + 50f;
        float titleSize = 17.6f;
        float titleX = textX;
        float titleY = y + 7.6f;
        float titleW = w - 160f;
        float titleH = 20f;
        String title = LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), meta.name(), titleSize, titleW);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(), title, titleX, titleY, titleSize, palette.moduleTitleText(), false);

        float authorSize = 13.6f;
        String author = LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), meta.author(), authorSize, w - 160f);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), author, textX, y + 28.8f, authorSize, palette.moduleDescriptionText(), false);

        LayoutRender2D.rectQuad(
                x,
                y + 46f,
                w,
                1f,
                palette.moduleDividerStart(),
                palette.moduleDividerEnd(),
                palette.moduleDividerEnd(),
                palette.moduleDividerStart()
        );

        float rowSize = 14.4f;
        float labelX = x + 14f;
        float valueX = x + 100f;
        float row1Y = y + 56f;
        float row2Y = y + 76f;
        int labelColor = palette.moduleDescriptionIcon();
        int valueColor = palette.moduleDescriptionText();

        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), ClickGuiI18n.tr("clickgui.settings.config.card.created", "Created:"), labelX, row1Y, rowSize, labelColor, false);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), ConfigProfileDateFormatter.created(meta), rowSize, w - 220f),
                valueX,
                row1Y,
                rowSize,
                valueColor,
                false
        );
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), ClickGuiI18n.tr("clickgui.settings.config.card.updated", "Updated:"), labelX, row2Y, rowSize, labelColor, false);
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), ConfigProfileDateFormatter.updated(meta), rowSize, w - 220f),
                valueX,
                row2Y,
                rowSize,
                valueColor,
                false
        );

        if (!boundContexts.isEmpty()) {
            float contextY = y + 102f;
            Renderer2D.COLOR.svg("map-pin", x + 14f, contextY - 0.6f,
                    14f, 14f, SvgRenderOptions.overrideColor(
                            LayoutRender2D.alpha(palette.menuCategorySelectedRight(), currentBound ? 0.95f : 0.75f)));
            String summary = boundContexts.getFirst();
            if (boundContexts.size() > 1) summary += "  +" + (boundContexts.size() - 1);
            LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_REGULAR.renderer(),
                    LayoutRender2D.fitText(BuiltinFontCatalog.ONEST_REGULAR.renderer(), summary,
                            13.2f, w - 164f),
                    x + 32f, contextY, 13.2f, palette.moduleDescriptionText(), false);
        }

        float btn = 32f;
        float gap = 8f;
        float by = y + h - 40f;
        float trashX = x + w - 14f - btn;
        float downloadX = trashX - gap - btn;
        float diffX = downloadX - gap - btn;

        drawAction(diffX, by, btn, "diff", false, mx, my, palette);
        drawAction(downloadX, by, btn, "download", false, mx, my, palette);
        drawAction(trashX, by, btn, "trash-2", false, mx, my, palette);

        // File export and the per-server auto-load binding.
        float small = 24f;
        float smallGap = 6f;
        float smallY = y + 8.8f;
        float bindX = x + w - 14f - small;
        float exportX = bindX - smallGap - small;
        drawAction(exportX, smallY, small, "folder-output", false, mx, my, palette);
        drawAction(bindX, smallY, small, "map-pin", !boundContexts.isEmpty(), mx, my, palette);

        return new CardHit(x, y, w, h,
                new ActionHit(titleX, titleY - 2f, titleW, titleH, CardActionType.RENAME),
                new ActionHit(diffX, by, btn, btn, CardActionType.DIFF),
                new ActionHit(downloadX, by, btn, btn, CardActionType.APPLY),
                new ActionHit(trashX, by, btn, btn, CardActionType.DELETE),
                new ActionHit(exportX, smallY, small, small, CardActionType.EXPORT),
                new ActionHit(bindX, smallY, small, small, CardActionType.BIND));
        }
    }

    private void drawAction(float x, float y, float size, String icon, boolean active, float mx, float my, SettingsGuiPalette palette) {
        boolean hover = ClickGuiMath.insideRect(mx, my, x, y, size, size);
        int bgA = hover ? SettingsGuiPalette.mix(palette.moduleCardTop(), palette.menuCategoryHoverLeft(), 0.36f) : LayoutRender2D.alpha(palette.moduleCardTop(), 0.88f);
        int bgB = hover ? SettingsGuiPalette.mix(palette.moduleCardTopStrong(), palette.menuCategoryHoverRight(), 0.28f) : LayoutRender2D.alpha(palette.moduleCardTopStrong(), 0.88f);
        if (active) {
            bgA = SettingsGuiPalette.mix(bgA, palette.menuCategorySelectedLeft(), 0.55f);
            bgB = SettingsGuiPalette.mix(bgB, palette.menuCategorySelectedRight(), 0.55f);
        }
        LayoutRender2D.roundedQuad(x, y, size, size, 8f, bgA, bgB, bgB, bgA);
        float inset = size >= 30f ? 8f : 5.2f;
        Renderer2D.COLOR.svg(
                icon,
                x + inset,
                y + inset,
                size - inset * 2f,
                size - inset * 2f,
                SvgRenderOptions.overrideColor(palette.menuCategoryText())
        );
    }

    public enum CardActionType {SELECT, RENAME, DIFF, APPLY, DELETE, EXPORT, BIND}

    public record CardHit(float x, float y, float w, float h, ActionHit title, ActionHit diff, ActionHit apply,
                          ActionHit delete, ActionHit export, ActionHit bind) {
    }

    public record ActionHit(float x, float y, float w, float h, CardActionType type) {
    }
}
