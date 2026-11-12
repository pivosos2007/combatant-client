/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.config.profile.ConfigProfileDiff;
import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.input.InputKeys;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.SingleLineTextInput;

import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Locale;

/** Side-by-side comparison of the live settings and a saved profile. */
public final class ConfigDiffPanelComponent {
    private static final int COLOR_NEW = 0xFF6CD39C;
    private static final int COLOR_CHANGED = 0xFFF1B96A;
    private static final int COLOR_REMOVED = 0xFFEA818B;

    private final SingleLineTextInput searchInput = new SingleLineTextInput(80);
    private boolean searchFocused;
    private float searchX, searchY, searchW, searchH;
    private float clearX, clearY, clearW, clearH;
    private TextRenderer regular;
    private TextRenderer semibold;
    private long fontGeneration = -1;

    private float scroll, smoothedScroll, openAnim;
    private boolean openTarget;
    private float saveHoverAnim, closeHoverAnim;
    private boolean scrollbarVisible, draggingScrollbar;
    private float scrollbarX, scrollbarY, scrollbarW, scrollbarH;
    private float scrollbarThumbY, scrollbarThumbH, scrollbarMaxScroll, scrollbarDragOffset;
    private float panelX, panelY, panelW, panelH;
    private float saveX, saveY, saveW, saveH;
    private float closeX, closeY, closeW, closeH;
    private ConfigProfileDiff cachedDiff;
    private String cachedSearch;
    private List<ConfigProfileDiff.Entry> cachedMatching = List.of();
    private final Map<ConfigProfileDiff.Value, String> cachedDetails = new IdentityHashMap<>();
    private int cachedChangeCount;

    private TextRenderer regular() {
        refreshFonts();
        return regular;
    }

    private TextRenderer semibold() {
        refreshFonts();
        return semibold;
    }

    private void refreshFonts() {
        long generation = Fonts.generation();
        if (regular == null || semibold == null || fontGeneration != generation) {
            regular = Fonts.renderer("SourceSans3", FontInfo.Type.Regular, BuiltinFontCatalog.ONEST_REGULAR.renderer());
            semibold = Fonts.renderer("SourceSans3Semibold", FontInfo.Type.Regular, BuiltinFontCatalog.ONEST_MEDIUM.renderer());
            fontGeneration = generation;
        }
    }

    public void resetScroll() {
        scroll = smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    public void open() {
        if (!openTarget) {
            searchInput.clear();
            searchFocused = false;
        }
        openTarget = true;
    }

    public void close() {
        openTarget = false;
        draggingScrollbar = false;
        searchFocused = false;
        searchInput.unfocus();
    }

    public boolean isActive() { return openTarget || openAnim > 0.001f; }
    public boolean isOpen() { return openTarget; }

    public void pointerFocus(float mx, float my) {
        if (!ClickGuiMath.insideRect(mx, my, panelX, panelY, panelW, panelH)) {
            searchFocused = false;
            searchInput.unfocus();
        }
    }

    public void render(float x, float y, float w, float h,
                       ConfigProfileMeta selected, ConfigProfileDiff diff, String error, SettingsGuiPalette palette) {
        float dt = AnimationUtility.deltaTime();
        float target = openTarget ? 1f : 0f;
        openAnim = AnimationUtility.approach(openAnim, target, dt, openTarget ? 12f : 13f);
        openAnim = AnimationUtility.snap(openAnim, target, 0.01f);
        if (!openTarget && openAnim <= 0.001f) {
            openAnim = 0f;
            return;
        }

        refreshFonts();
        panelX = x; panelY = y; panelW = w; panelH = h;

        LayoutRender2D.flushRenderer();
        LayoutRender2D.drawBlur(x, y, w, h, 14f, 0xFFFFFFFF, openAnim);
        LayoutRender2D.roundedSoftShadow(x, y, w, h, 14f, 16f, 0.018f,
                LayoutRender2D.alpha(palette.panelShadow(), openAnim));
        LayoutRender2D.roundedQuad(x, y, w, h, 14f,
                LayoutRender2D.alpha(palette.panelBgLeft(), openAnim * 0.30f),
                LayoutRender2D.alpha(palette.panelBgRight(), openAnim * 0.30f),
                LayoutRender2D.alpha(palette.panelBgRight(), openAnim * 0.30f),
                LayoutRender2D.alpha(palette.panelBgLeft(), openAnim * 0.30f));
        LayoutRender2D.roundedStroke(x, y, w, h, 14f, 0.9f,
                LayoutRender2D.alpha(palette.panelStroke(), openAnim));

        drawText(ClickGuiI18n.tr("clickgui.settings.config.diff.title", "Compare settings"),
                x + 18f, y + 15.4f, 18f, palette.panelText(), true);
        renderHeaderActions(x, y, w, ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), palette);
        LayoutRender2D.rect(x + 16f, y + 50f, w - 32f,
                1.1f, LayoutRender2D.alpha(palette.panelDivider(), openAnim));

        renderSearch(x + 16f, y + 62f, w - 32f, 34f, palette);
        renderLegend(x + 18f, y + 106f, palette);

        float bodyX = x + 16f;
        float bodyY = y + 138f;
        float bodyW = w - 32f;
        float bodyH = Math.max(1f, h - 150f);
        List<ConfigProfileDiff.Entry> matching = matchedEntries(diff);
        float contentH = contentHeight(selected, diff, error, matching);
        float maxScroll = Math.max(0f, contentH - bodyH);
        updateScrollbarMetrics(bodyX, bodyY, bodyW, bodyH, maxScroll);
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());
        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        boolean clipped = ScissorFunction.pushRaw(bodyX, bodyY, bodyW, bodyH);
        renderBody(bodyX, bodyY + smoothedScroll, bodyW - (scrollbarVisible ? 14f : 0f),
                bodyY, bodyH, selected, diff, error, matching, palette);
        if (clipped) ScissorFunction.pop();
        renderScrollbar(palette);
    }

    private void renderSearch(float x, float y, float w, float h, SettingsGuiPalette palette) {
        searchX = x; searchY = y; searchW = w; searchH = h;
        boolean hovering = ClickGuiMath.insideRect(ClickGuiRenderer.getMouseX(), ClickGuiRenderer.getMouseY(), x, y, w, h);
        float hover = hovering ? 1f : 0f;
        if (hovering) SystemCursor.set(SystemCursor.CursorType.TEXT);
        int bg = LayoutRender2D.alpha(
                SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.07f + hover * 0.08f),
                openAnim);
        LayoutRender2D.roundedQuad(x, y, w, h, 8f, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(x, y, w, h, 8f, 1f,
                SettingsGuiPalette.withAlpha(searchFocused ? palette.menuCategorySelectedRight() : palette.panelStroke(),
                        Math.round((searchFocused ? 175f : 105f) * openAnim)));
        Renderer2D.COLOR.svg("search", x + 8f, y + 8f, 18f, 18f,
                SvgRenderOptions.overrideColor(LayoutRender2D.alpha(palette.panelMuted(), openAnim)));

        float fontSize = 16.6f;
        float textX = x + 34f;
        float textY = y + 6.6f;
        clearW = 26f;
        clearH = h;
        clearX = x + w - clearW - 2f;
        clearY = y;
        float fieldW = Math.max(1f, clearX - textX - 4f);
        if (searchInput.text().isEmpty() && !searchFocused) {
            drawText(ClickGuiI18n.tr("clickgui.settings.config.diff.search", "Search modules"),
                    textX, textY, fontSize, palette.panelMuted(), false);
        }
        searchInput.render(textX, textY, fieldW, h, regular(), fontSize,
                LayoutRender2D.alpha(palette.panelText(), openAnim),
                SettingsGuiPalette.withAlpha(palette.menuCategorySelectedRight(), Math.round(125f * openAnim)), searchFocused);
        if (!searchInput.text().isEmpty()) {
            Renderer2D.COLOR.svg("x", clearX + 6f, y + 8f,
                    18f, 18f,
                    SvgRenderOptions.overrideColor(LayoutRender2D.alpha(palette.panelMuted(), openAnim)));
        }
    }

    private void renderLegend(float x, float y, SettingsGuiPalette palette) {
        float itemX = x;
        itemX = legendItem(itemX, y, ClickGuiI18n.tr("clickgui.settings.config.diff.added", "New"), COLOR_NEW);
        itemX = legendItem(itemX + 18f, y,
                ClickGuiI18n.tr("clickgui.settings.config.diff.changed_kind", "Changed"), COLOR_CHANGED);
        legendItem(itemX + 18f, y,
                ClickGuiI18n.tr("clickgui.settings.config.diff.removed", "Removed"), COLOR_REMOVED);
    }

    private float legendItem(float x, float y, String label, int color) {
        LayoutRender2D.roundedQuad(x, y + 6f, 6.8f, 6.8f,
                2.8f, LayoutRender2D.alpha(color, openAnim),
                LayoutRender2D.alpha(color, openAnim), LayoutRender2D.alpha(color, openAnim),
                LayoutRender2D.alpha(color, openAnim));
        drawText(label, x + 12f, y, 14.4f, color, false);
        return x + 12f + LayoutRender2D.textWidth(regular(), label, 14.4f);
    }

    private void renderHeaderActions(float x, float y, float w, float mx, float my, SettingsGuiPalette palette) {
        float size = 38f;
        float groupW = size * 2f;
        float groupX = x + w - groupW - 12f;
        float groupY = y + 6f;
        saveX = groupX; saveY = groupY; saveW = size; saveH = size;
        closeX = groupX + size; closeY = groupY; closeW = size; closeH = size;
        boolean saveHover = ClickGuiMath.insideRect(mx, my, saveX, saveY, saveW, saveH);
        boolean closeHover = ClickGuiMath.insideRect(mx, my, closeX, closeY, closeW, closeH);
        saveHoverAnim = AnimationUtility.approach(saveHoverAnim, saveHover ? 1f : 0f, AnimationUtility.deltaTime(), 10f);
        closeHoverAnim = AnimationUtility.approach(closeHoverAnim, closeHover ? 1f : 0f, AnimationUtility.deltaTime(), 10f);
        int bg = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillBase(), palette.moduleCardTop(), 0.25f), openAnim);
        LayoutRender2D.roundedQuad(groupX, groupY, groupW, size, 8f, bg, bg, bg, bg);
        LayoutRender2D.roundedStroke(groupX, groupY, groupW, size, 8f,
                1f, LayoutRender2D.alpha(palette.panelStroke(), openAnim));
        drawHeaderIcon(saveX, saveY, size, "save", saveHoverAnim, palette);
        drawHeaderIcon(closeX, closeY, size, "x", closeHoverAnim, palette);
        if (saveHover || closeHover) SystemCursor.set(SystemCursor.CursorType.MOVE);
    }

    private void drawHeaderIcon(float x, float y, float size, String icon, float hover, SettingsGuiPalette palette) {
        int color = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelMuted(), palette.panelText(), 0.65f + hover * 0.35f), openAnim);
        Renderer2D.COLOR.svg(icon, x + 10.4f, y + 10.4f,
                size - 20.8f, size - 20.8f, SvgRenderOptions.overrideColor(color));
    }

    /** Called before card hit testing; consumes any click within this floating window. */
    public HeaderAction headerAction(float mx, float my, int button) {
        if (!isActive() || button != InputKeys.MOUSE_LEFT) return HeaderAction.NONE;
        if (!ClickGuiMath.insideRect(mx, my, panelX, panelY, panelW, panelH)) {
            searchFocused = false;
            searchInput.unfocus();
            return HeaderAction.NONE;
        }
        if (!openTarget) return HeaderAction.CONSUMED;
        if (ClickGuiMath.insideRect(mx, my, closeX, closeY, closeW, closeH)) {
            searchFocused = false;
            searchInput.unfocus();
            return HeaderAction.CLOSE;
        }
        if (ClickGuiMath.insideRect(mx, my, saveX, saveY, saveW, saveH)) {
            searchFocused = false;
            searchInput.unfocus();
            return HeaderAction.SAVE;
        }
        if (ClickGuiMath.insideRect(mx, my, searchX, searchY, searchW, searchH)) {
            if (!searchInput.text().isEmpty() && ClickGuiMath.insideRect(mx, my, clearX, clearY, clearW, clearH)) {
                searchInput.clear();
                resetScroll();
            } else {
                searchFocused = true;
                searchInput.beginDrag(mx, false);
            }
            return HeaderAction.CONSUMED;
        }
        searchFocused = false;
        searchInput.unfocus();
        return HeaderAction.CONSUMED;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!openTarget) return false;
        if (key == InputKeys.F && (modifiers & combatant.client.input.InputModifierMask.SHORTCUT) != 0) {
            combatant.client.features.gui.clickgui.ClickGuiSearch.unfocus();
            searchFocused = true;
            return true;
        }
        if (!searchFocused) {
            if (key != InputKeys.ESCAPE) return false;
            close();
            return true;
        }
        if (key == InputKeys.ESCAPE || key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
            searchFocused = false;
            searchInput.unfocus();
            return true;
        }
        String previous = searchInput.text();
        searchInput.keyPressed(key, scan, modifiers);
        if (!previous.equals(searchInput.text())) resetScroll();
        return true;
    }

    public boolean charTyped(char c) {
        if (!openTarget || !searchFocused) return false;
        searchInput.type(c);
        resetScroll();
        return true;
    }

    private List<ConfigProfileDiff.Entry> matchedEntries(ConfigProfileDiff diff) {
        String query = searchInput.text().trim().toLowerCase(Locale.ROOT);
        if (cachedDiff == diff && query.equals(cachedSearch)) return cachedMatching;
        cachedDiff = diff;
        cachedSearch = query;
        cachedDetails.clear();
        cachedChangeCount = 0;
        if (diff == null) {
            cachedMatching = List.of();
            return cachedMatching;
        }
        List<ConfigProfileDiff.Entry> matches = new ArrayList<>();
        for (ConfigProfileDiff.Entry entry : diff.entries()) {
            if (!query.isEmpty() && !entry.displayName().toLowerCase(Locale.ROOT).contains(query)
                    && !entry.ownerId().toLowerCase(Locale.ROOT).contains(query)) continue;
            matches.add(entry);
            for (ConfigProfileDiff.Value value : entry.values()) {
                cachedChangeCount++;
                String detail = switch (value.kind()) {
                    case ADDED -> stringify(value.selectedValue());
                    case REMOVED -> stringify(value.currentValue());
                    case CHANGED -> stringify(value.currentValue()) + "  →  " + stringify(value.selectedValue());
                };
                cachedDetails.put(value, detail);
            }
        }
        cachedMatching = List.copyOf(matches);
        return cachedMatching;
    }

    private void renderBody(float x, float y, float w, float clipY, float clipH,
                            ConfigProfileMeta selected, ConfigProfileDiff diff, String error,
                            List<ConfigProfileDiff.Entry> matching, SettingsGuiPalette palette) {
        if (error != null && !error.isBlank()) {
            drawVisible(ClickGuiI18n.tr("clickgui.settings.config.diff.error", "Diff error: %s", error),
                    x, y, w, 16.4f, palette.panelMuted(), false, clipY, clipH);
            return;
        }
        if (selected == null || diff == null) {
            drawVisible(ClickGuiI18n.tr("clickgui.settings.config.diff.not_loaded", "Comparison unavailable"),
                    x, y, w, 16.4f, palette.panelMuted(), false, clipY, clipH);
            return;
        }
        float cy = y;
        drawVisible(selected.name(), x, cy, w, 18f, palette.panelText(), true, clipY, clipH);
        cy += 28f;
        if (diff.isEmpty()) {
            drawVisible(ClickGuiI18n.tr("clickgui.settings.config.diff.no_differences", "No differences."),
                    x, cy, w, 16.4f, palette.panelMuted(), false, clipY, clipH);
            return;
        }
        if (matching.isEmpty()) {
            drawVisible(ClickGuiI18n.tr("clickgui.settings.config.diff.no_matches", "No matching modules."),
                    x, cy, w, 16.4f, palette.panelMuted(), false, clipY, clipH);
            return;
        }
        drawVisible(ClickGuiI18n.tr("clickgui.settings.config.diff.changed", "%s changed options", cachedChangeCount),
                x, cy, w, 15.6f, palette.panelMuted(), false, clipY, clipH);
        cy += 28f;
        for (ConfigProfileDiff.Entry entry : matching) {
            drawVisible(entry.displayName(), x, cy, w - 60f,
                    18f, palette.panelText(), true, clipY, clipH);
            drawVisible(String.valueOf(entry.values().size()), x + w - 44f, cy,
                    40f, 16f, palette.panelMuted(), false, clipY, clipH);
            cy += 30f;
            for (ConfigProfileDiff.Value value : entry.values()) {
                if (cy + 46f < clipY || cy > clipY + clipH) {
                    cy += 46f;
                    continue;
                }
                int tint = colorOf(value.kind());
                if (cy + 40f >= clipY && cy <= clipY + clipH) {
                    LayoutRender2D.roundedQuad(x + 2f, cy + 4f, 4f, 32f,
                            2f, LayoutRender2D.alpha(tint, openAnim),
                            LayoutRender2D.alpha(tint, openAnim),
                            LayoutRender2D.alpha(tint, openAnim),
                            LayoutRender2D.alpha(tint, openAnim));
                }
                drawVisible(value.key(), x + 16f, cy,
                        w - 138f, 16.4f, palette.panelText(), true, clipY, clipH);
                drawVisible(kindLabel(value.kind()), x + w - 118f, cy,
                        116f, 14.6f, tint, false, clipY, clipH);
                drawVisible(cachedDetails.getOrDefault(value, ""), x + 16f, cy + 20f, w - 18f,
                        15.4f, SettingsGuiPalette.mix(palette.panelMuted(), tint, 0.28f),
                        false, clipY, clipH);
                cy += 46f;
            }
            cy += 14f;
        }
    }

    private float contentHeight(ConfigProfileMeta selected, ConfigProfileDiff diff, String error,
                                List<ConfigProfileDiff.Entry> matching) {
        if (selected == null || diff == null || error != null) return 32f;
        if (diff.isEmpty() || matching.isEmpty()) return 60f;
        float total = 56f;
        for (ConfigProfileDiff.Entry entry : matching) {
            total += 44f + 46f * entry.values().size();
        }
        return total;
    }

    private static String stringify(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
        return text.length() > 260 ? text.substring(0, 257) + "..." : text;
    }

    private static int colorOf(ConfigProfileDiff.Kind kind) {
        return switch (kind) {
            case ADDED -> COLOR_NEW;
            case CHANGED -> COLOR_CHANGED;
            case REMOVED -> COLOR_REMOVED;
        };
    }

    private static String kindLabel(ConfigProfileDiff.Kind kind) {
        return switch (kind) {
            case ADDED -> ClickGuiI18n.tr("clickgui.settings.config.diff.added", "New");
            case CHANGED -> ClickGuiI18n.tr("clickgui.settings.config.diff.changed_kind", "Changed");
            case REMOVED -> ClickGuiI18n.tr("clickgui.settings.config.diff.removed", "Removed");
        };
    }

    private void drawVisible(String value, float x, float y, float width, float size, int color,
                             boolean strong, float clipY, float clipH) {
        if (y + 22f < clipY || y > clipY + clipH) return;
        TextRenderer font = strong ? semibold() : regular();
        String line = LayoutRender2D.fitText(font, value, size, Math.max(1f, width));
        drawText(line, x, y, size, color, strong);
    }

    private void drawText(String value, float x, float y, float size, int color, boolean strong) {
        LayoutRender2D.drawText(strong ? semibold() : regular(), value,
                x, y, size, LayoutRender2D.alpha(color, openAnim), false);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (!openTarget || openAnim <= 0.01f || button != InputKeys.MOUSE_LEFT || !isScrollbarHovered(mx, my))
            return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public void mouseReleased(int button) {
        if (button == InputKeys.MOUSE_LEFT) draggingScrollbar = false;
    }

    public boolean scroll(float mx, float my, double amount) {
        if (!openTarget || openAnim <= 0.01f || !ClickGuiMath.insideRect(mx, my, panelX, panelY, panelW, panelH))
            return false;
        scroll += (float) (amount * 20f);
        return true;
    }

    private void renderScrollbar(SettingsGuiPalette palette) {
        if (!scrollbarVisible) return;
        float mx = ClickGuiRenderer.getMouseX(), my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) SystemCursor.set(SystemCursor.CursorType.SCROLL);
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH, 4f,
                LayoutRender2D.alpha(palette.moduleScrollTrackA(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackA(), openAnim));
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 4f,
                LayoutRender2D.alpha(palette.moduleScrollHandleA(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleA(), openAnim));
    }

    private void updateScrollbarMetrics(float x, float y, float w, float h, float maxScroll) {
        scrollbarVisible = maxScroll > 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }
        scrollbarW = 6f;
        scrollbarX = x + w - scrollbarW - 3f;
        scrollbarY = y + 4f;
        scrollbarH = Math.max(1f, h - 8f);
        scrollbarThumbH = Math.max(32f, scrollbarH * (scrollbarH / (scrollbarMaxScroll + scrollbarH)));
        float ratio = scrollbarMaxScroll <= 0f ? 0f : -smoothedScroll / scrollbarMaxScroll;
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    private boolean isScrollbarHovered(float mx, float my) {
        if (!scrollbarVisible) return false;
        float pad = 4f;
        return ClickGuiMath.insideRect(mx, my, scrollbarX - pad, scrollbarY - pad,
                scrollbarW + pad * 2f, scrollbarH + pad * 2f);
    }

    private void scrollToMouse(float my) {
        if (!scrollbarVisible || scrollbarMaxScroll <= 0f) return;
        float span = scrollbarH - scrollbarThumbH;
        if (span <= 0.5f) return;
        float top = AnimationUtility.clamp(my - scrollbarDragOffset, scrollbarY, scrollbarY + span);
        scroll = -scrollbarMaxScroll * AnimationUtility.clamp((top - scrollbarY) / span, 0f, 1f);
    }

    public enum HeaderAction { NONE, SAVE, CLOSE, CONSUMED }
}
