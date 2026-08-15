/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.theme;


import combatant.client.features.theme.Theme;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ThemeComponent {
    private static final String ADD_CARD_LABEL = "\u0414\u043e\u0431\u0430\u0432\u0438\u0442\u044c \u0442\u0435\u043c\u0443";

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> swatchHoverByKey = new HashMap<>();
    private final Map<String, Float> selectedAnimByTheme = new HashMap<>();
    private final Set<String> liveThemeIds = new HashSet<>();
    private float scroll;
    private float smoothedScroll;
    private boolean scrollbarVisible;
    private boolean draggingScrollbar;
    private float scrollbarX;
    private float scrollbarY;
    private float scrollbarW;
    private float scrollbarH;
    private float scrollbarThumbY;
    private float scrollbarThumbH;
    private float scrollbarMaxScroll;
    private float scrollbarDragOffset;

    private static void drawThemeGradientRect(float x,
                                              float y,
                                              float w,
                                              float h,
                                              float radius,
                                              Themes.GradientSpec gradient,
                                              int fallbackStart,
                                              int fallbackEnd) {
        if (gradient != null && gradient.enabled()) {
            int start = SettingsGuiPalette.withAlpha(gradient.start(), 220);
            int end = SettingsGuiPalette.withAlpha(gradient.end(), 220);
            ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, radius, start, end, gradient.angleDeg());
            return;
        }
        int start = SettingsGuiPalette.withAlpha(fallbackStart, 220);
        int end = SettingsGuiPalette.withAlpha(fallbackEnd, 220);
        ClickGuiRenderer.drawRoundedRectGradient(x, y, w, h, radius, start, end, 0f);
    }

    private static List<Themes.ThemeEntry> filteredEntries() {
        List<Themes.ThemeEntry> all = Theme.themes();
        if (!ClickGuiSearch.isActive() || ClickGuiSearch.getText().isEmpty()) return all;
        List<Themes.ThemeEntry> out = new ArrayList<>();
        for (Themes.ThemeEntry entry : all) {
            if (ClickGuiSearch.matches(entry.name()) || ClickGuiSearch.matches(entry.getId())) {
                out.add(entry);
            }
        }
        return out;
    }

    private static boolean shouldShowAddCard() {
        if (!ClickGuiSearch.isActive() || ClickGuiSearch.getText().isEmpty()) return true;
        return ClickGuiSearch.matches(ADD_CARD_LABEL);
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    public void scroll(float mx, float my, float menuX, float menuY, float menuW, float menuH, double amount, float scale) {
        float x = menuX + 31f * scale;
        float y = menuY + 33f * scale;
        float w = menuW - 42f * scale;
        float h = menuH - 39f * scale;
        if (!ClickGuiMath.insideRect(mx, my, x, y, w, h)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != 0) return false;
        if (!isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        if (button == 0) {
            draggingScrollbar = false;
        }
    }

    public ThemeAction click(float mx, float my, int button) {
        if (isScrollbarHovered(mx, my)) return null;
        if (button != 0 && button != 1) return null;
        for (Hit hit : hits) {
            if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (hit.deleteButton()) return button == 0 && hit.custom() ? new ThemeAction(ThemeActionType.DELETE, hit.themeId()) : null;
            if (hit.addCard()) return button == 0 ? new ThemeAction(ThemeActionType.ADD, null) : null;
            if (hit.themeId() == null || hit.themeId().isBlank()) return null;
            if (button == 1 && hit.custom()) return new ThemeAction(ThemeActionType.EDIT, hit.themeId());
            if (button == 0) return new ThemeAction(ThemeActionType.SELECT, hit.themeId());
        }
        return null;
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        hits.clear();

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        updateSelectedAnimations(Theme.themes());
        List<Themes.ThemeEntry> entries = filteredEntries();
        boolean includeAddCard = shouldShowAddCard();
        int cardsTotal = entries.size() + (includeAddCard ? 1 : 0);
        if (cardsTotal <= 0) return;

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;

        int columns = 2;
        float gap = 6f * scale;
        float cardW = (areaW - gap * (columns - 1)) / columns;
        float cardH = 46f * scale;
        float stepY = cardH + gap;

        int rows = (cardsTotal + columns - 1) / columns;
        float contentH = rows * stepY - gap;
        float maxScroll = Math.max(0f, contentH - areaH);
        updateScrollbarMetrics(menuX, menuY, menuH, areaH, contentH, maxScroll, scale);
        if (draggingScrollbar) {
            scrollToMouse(my);
        }

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);
        // The selection halo belongs to the card, but extends a few pixels beyond it.
        // Keep the scroll viewport while leaving enough room for that halo at its edges.
        float glowClipPadding = 4.5f * scale;
        boolean clipped = ScissorFunction.pushRaw(
                areaX - glowClipPadding,
                areaY - glowClipPadding,
                areaW + glowClipPadding * 2f,
                areaH + glowClipPadding * 2f
        );

        int idx = 0;
        for (Themes.ThemeEntry entry : entries) {
            int col = idx % columns;
            int row = idx / columns;
            float x = areaX + col * (cardW + gap);
            float y = areaY + row * stepY + smoothedScroll;
            if (y + cardH < areaY || y > areaY + areaH) {
                idx++;
                continue;
            }
            boolean custom = !entry.builtin();
            drawThemeCard(entry, x, y, cardW, cardH, mx, my, scale, palette, selectedAnim(entry.getId()));
            if (custom) {
                float deleteSize = deleteButtonSize(scale);
                hits.add(new Hit(deleteButtonX(x, cardW, scale), deleteButtonY(y, scale), deleteSize, deleteSize, entry.getId(), false, true, true));
            }
            hits.add(new Hit(x, y, cardW, cardH, entry.getId(), false, custom, false));
            idx++;
        }

        if (includeAddCard) {
            int col = idx % columns;
            int row = idx / columns;
            float x = areaX + col * (cardW + gap);
            float y = areaY + row * stepY + smoothedScroll;
            if (!(y + cardH < areaY || y > areaY + areaH)) {
                drawAddCard(x, y, cardW, cardH, scale, palette);
                hits.add(new Hit(x, y, cardW, cardH, null, true, false, false));
            }
        }
        if (clipped) ScissorFunction.pop();
        renderScrollbar(menuX, menuY, menuH, areaH, contentH, maxScroll, scale, palette);
    }

    private void updateSelectedAnimations(List<Themes.ThemeEntry> entries) {
        String currentId = Theme.currentId();
        float dt = AnimationUtility.deltaTime();
        liveThemeIds.clear();

        for (Themes.ThemeEntry entry : entries) {
            String id = entry.getId();
            liveThemeIds.add(id);
            float target = id.equals(currentId) ? 1f : 0f;
            float value = selectedAnimByTheme.getOrDefault(id, target);
            value = AnimationUtility.approach(value, target, dt, target > value ? 11.5f : 9.5f);
            value = AnimationUtility.snap(value, target, 0.002f);
            selectedAnimByTheme.put(id, value);
        }

        selectedAnimByTheme.keySet().removeIf(id -> !liveThemeIds.contains(id));
    }

    private float selectedAnim(String themeId) {
        return selectedAnimByTheme.getOrDefault(themeId, themeId.equals(Theme.currentId()) ? 1f : 0f);
    }

    private void drawThemeCard(Themes.ThemeEntry entry,
                               float x,
                               float y,
                               float w,
                               float h,
                               float mx,
                               float my,
                               float scale,
                               SettingsGuiPalette palette,
                               float selectedAnim) {
        Themes.Theme t = entry.theme();
        float selected = AnimationUtility.easeInOutCubic(selectedAnim);
        int selectedAccent = SettingsGuiPalette.withAlpha(t.accent(), Math.round(82f * selected));
        int selectedAccentSoft = SettingsGuiPalette.withAlpha(t.accentSoft(), Math.round(48f * selected));

        if (selected > 0.001f) {
            LayoutRender2D.roundedSoftShadow(
                    x - 0.45f * scale,
                    y - 0.45f * scale,
                    w + 0.9f * scale,
                    h + 0.9f * scale,
                    5.35f * scale,
                    (3.6f + 1.4f * selected) * scale,
                    0.020f * selected,
                    SettingsGuiPalette.withAlpha(t.accent(), Math.round(62f * selected))
            );
        }

        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(
                x,
                y,
                w,
                h,
                5f * scale,
                palette.moduleCardTop(),
                palette.moduleCardTopStrong(),
                palette.moduleCardBottom(),
                palette.moduleCardBottomStrong()
        );
        if (selected > 0.001f) {
            LayoutRender2D.roundedQuad(
                    x,
                    y,
                    w,
                    h,
                    5f * scale,
                    selectedAccent,
                    selectedAccentSoft,
                    SettingsGuiPalette.withAlpha(t.accentSoft(), Math.round(24f * selected)),
                    SettingsGuiPalette.withAlpha(t.accent(), Math.round(38f * selected))
            );
            LayoutRender2D.roundedStrokeQuad(
                    x + 0.35f * scale,
                    y + 0.35f * scale,
                    w - 0.7f * scale,
                    h - 0.7f * scale,
                    4.7f * scale,
                    (0.55f + 0.35f * selected) * scale,
                    SettingsGuiPalette.withAlpha(t.accent(), Math.round(235f * selected)),
                    SettingsGuiPalette.withAlpha(t.accentSoft(), Math.round(190f * selected)),
                    SettingsGuiPalette.withAlpha(t.accentSoft(), Math.round(115f * selected)),
                    SettingsGuiPalette.withAlpha(t.accent(), Math.round(180f * selected))
            );

        }
        float dividerInset = 5f * scale;
        LayoutRender2D.rectQuad(
                x + dividerInset,
                y + 15.5f * scale,
                w - dividerInset * 2f,
                0.5f * scale,
                SettingsGuiPalette.mix(palette.moduleDividerStart(), SettingsGuiPalette.withAlpha(t.accent(), 210), selected * 0.78f),
                SettingsGuiPalette.mix(palette.moduleDividerEnd(), SettingsGuiPalette.withAlpha(t.accentSoft(), 190), selected * 0.72f),
                SettingsGuiPalette.mix(palette.moduleDividerEnd(), SettingsGuiPalette.withAlpha(t.accentSoft(), 145), selected * 0.58f),
                SettingsGuiPalette.mix(palette.moduleDividerStart(), SettingsGuiPalette.withAlpha(t.accent(), 175), selected * 0.68f)
        );

        boolean custom = !entry.builtin();
        float titleSize = 9.2f * scale;
        float titleReserve = custom ? 40f * scale : 12f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), entry.name(), titleSize, w - titleReserve);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                title,
                x + 6f * scale,
                y + 3.8f * scale,
                titleSize,
                SettingsGuiPalette.mix(0xFFFFFFFF, SettingsGuiPalette.withAlpha(t.accent(), 255), selected * 0.24f),
                false
        );
        if (custom) {
            float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), title, titleSize);
            float iconSize = 7f * scale;
            float iconX = Math.min(x + w - 28f * scale, x + 7f * scale + tw + 3f * scale);
            Renderer2D.COLOR.svg("paintbrush", iconX, y + 5.3f * scale, iconSize, iconSize, SvgRenderOptions.overrideColor(SettingsGuiPalette.withAlpha(t.accent(), 230)));
            drawCardDeleteButton(x, y, w, mx, my, scale, palette);
        }

        float swatchPadX = 6f * scale;
        float chipsX = x + swatchPadX;
        float chipsY = y + 20f * scale;
        float chipsW = w - swatchPadX * 2f;
        float chipsBottom = y + h - 5f * scale;
        float chipsH = Math.max(2f * scale, chipsBottom - chipsY);

        Themes.GradientSpec[] gradients = new Themes.GradientSpec[]{
                entry.windowGradient(),
                entry.cardGradient(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        };
        int[] colorA = new int[]{
                t.windowBg(),
                t.cardEnabled(),
                t.windowHeader(),
                t.surface(),
                t.surfaceHover(),
                t.accent(),
                t.accentSoft(),
                t.windowStroke(),
                t.strokeSoft(),
                t.textPrimary(),
                t.textMuted(),
                t.windowBg()
        };
        int[] colorB = new int[]{
                t.windowHeader(),
                t.accentSoft(),
                t.windowStroke(),
                t.surfaceHover(),
                t.windowHeader(),
                t.accentSoft(),
                t.strokeSoft(),
                t.strokeSoft(),
                t.textMuted(),
                t.textMuted(),
                t.surface(),
                t.accentSoft()
        };

        int columns = colorA.length;
        float chipSize = Math.min(10.2f * scale, chipsH);
        float minGap = scale;
        if (columns > 1) {
            float maxChipFromWidth = (chipsW - minGap * (columns - 1)) / columns;
            chipSize = Math.min(chipSize, maxChipFromWidth);
        }
        chipSize = Math.max(6f * scale, chipSize);
        float chipGap = columns <= 1 ? 0f : Math.max(minGap, (chipsW - chipSize * columns) / (columns - 1));
        float hoverBoost = 0.75f * scale;
        float yCenterOffset = (chipsH - chipSize) * 0.5f;

        for (int i = 0; i < colorA.length; i++) {
            int col = i % columns;
            int row = i / columns;
            float cx = chipsX + col * (chipSize + chipGap);
            float cy = chipsY + yCenterOffset + row * (chipSize + chipGap);
            boolean hover = ClickGuiMath.insideRect(mx, my, cx, cy, chipSize, chipSize);
            String key = entry.getId() + "#" + i;
            float anim = swatchHoverByKey.getOrDefault(key, 0f);
            anim = AnimationUtility.approach(anim, hover ? 1f : 0f, 0.20f);
            anim = AnimationUtility.snap(anim, hover ? 1f : 0f, 0.01f);
            swatchHoverByKey.put(key, anim);

            float size = chipSize + hoverBoost * anim;
            float ox = cx - (size - chipSize) * 0.5f;
            float oy = cy - (size - chipSize) * 0.5f;

            drawThemeGradientRect(
                    ox,
                    oy,
                    size,
                    size,
                    2.3f * scale,
                    gradients[i],
                    colorA[i],
                    colorB[i]
            );

            // TODO: Show swatch tooltip on hover (name/value), once UX spec is provided.
        }
    }

    private static float deleteButtonSize(float scale) {
        return 12f * scale;
    }

    private static float deleteButtonX(float x, float w, float scale) {
        return x + w - 16f * scale;
    }

    private static float deleteButtonY(float y, float scale) {
        return y + 2.2f * scale;
    }

    private void drawCardDeleteButton(float x,
                                      float y,
                                      float w,
                                      float mx,
                                      float my,
                                      float scale,
                                      SettingsGuiPalette palette) {
        float size = deleteButtonSize(scale);
        float bx = deleteButtonX(x, w, scale);
        float by = deleteButtonY(y, scale);
        boolean hover = ClickGuiMath.insideRect(mx, my, bx, by, size, size);
        int bg = hover
                ? SettingsGuiPalette.withAlpha(0xFFE5484D, 64)
                : SettingsGuiPalette.withAlpha(palette.moduleCardBottomStrong(), 90);
        int stroke = hover
                ? SettingsGuiPalette.withAlpha(0xFFFF6B6F, 160)
                : SettingsGuiPalette.withAlpha(palette.moduleDividerEnd(), 80);
        LayoutRender2D.roundedQuad(bx, by, size, size, 3.5f * scale, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(bx, by, size, size, 3.5f * scale, 0.45f * scale, stroke);
        Renderer2D.COLOR.svg(
                "trash-2",
                bx + 2.4f * scale,
                by + 2.3f * scale,
                7.2f * scale,
                7.2f * scale,
                SvgRenderOptions.overrideColor(SettingsGuiPalette.withAlpha(hover ? 0xFFFFC4C7 : 0xFFFFFFFF, hover ? 245 : 160))
        );
    }

    private void drawAddCard(float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
        ClickGuiRenderer.drawBlur(x, y, w, h, 5f * scale, 0xFF000000, 200f / 255f);
        LayoutRender2D.roundedQuad(
                x,
                y,
                w,
                h,
                5f * scale,
                palette.moduleCardTop(),
                palette.moduleCardTopStrong(),
                palette.moduleCardBottom(),
                palette.moduleCardBottomStrong()
        );

        float plusSize = 11f * scale;
        float plusW = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterMedium(), "+", plusSize);
        float plusH = ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), plusSize);
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                "+",
                x + (w - plusW) * 0.5f,
                y + 5.5f * scale,
                plusSize,
                palette.panelText(),
                false
        );

        float titleSize = 7.6f * scale;
        String title = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), ADD_CARD_LABEL, titleSize, w - 12f * scale);
        float tw = ClickGuiRenderer.textWidth(ClickGuiRenderer.getInterRegular(), title, titleSize);
        float ty = y + h - plusH - 10f * scale;
        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                title,
                x + (w - tw) * 0.5f,
                ty,
                titleSize,
                palette.moduleDescriptionText(),
                false
        );
    }

    private void renderScrollbar(float menuX, float menuY, float menuH, float areaH, float contentH, float maxScroll, float scale, SettingsGuiPalette palette) {
        updateScrollbarMetrics(menuX, menuY, menuH, areaH, contentH, maxScroll, scale);
        if (!scrollbarVisible) return;

        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        boolean hover = isScrollbarHovered(mx, my);
        if (hover || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }

        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarY, scrollbarW, scrollbarH, 2f * scale,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );
        LayoutRender2D.roundedQuad(
                scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 2f * scale,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
        );
    }

    private void updateScrollbarMetrics(float menuX, float menuY, float menuH, float areaH, float contentH, float maxScroll, float scale) {
        scrollbarVisible = maxScroll > 0f && contentH > areaH + 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }

        scrollbarW = 4f * scale;
        scrollbarX = menuX + 400f * scale - 35f * scale - scrollbarW + 50f * scale;
        scrollbarY = menuY + 33f * scale;
        scrollbarH = menuH - 58f * scale;
        scrollbarThumbH = Math.max(20f * scale, scrollbarH * (areaH / contentH));
        float ratio = scrollbarMaxScroll <= 0f ? 0f : (-smoothedScroll / scrollbarMaxScroll);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    private boolean isScrollbarHovered(float mx, float my) {
        if (!scrollbarVisible) return false;
        float pad = 4f;
        return ClickGuiMath.insideRect(mx, my, scrollbarX - pad, scrollbarY - pad, scrollbarW + pad * 2f, scrollbarH + pad * 2f);
    }

    private void scrollToMouse(float my) {
        if (!scrollbarVisible || scrollbarMaxScroll <= 0f) return;
        float span = scrollbarH - scrollbarThumbH;
        if (span <= 0.5f) return;
        float thumbTop = AnimationUtility.clamp(my - scrollbarDragOffset, scrollbarY, scrollbarY + span);
        float ratio = (thumbTop - scrollbarY) / span;
        scroll = -scrollbarMaxScroll * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    public enum ThemeActionType {
        SELECT,
        ADD,
        EDIT,
        DELETE
    }

    public record ThemeAction(ThemeActionType type, String themeId) {
    }

    private record Hit(float x, float y, float w, float h, String themeId, boolean addCard, boolean custom, boolean deleteButton) {
    }
}
