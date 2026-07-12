/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.main;

import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.config.MainConfig;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class MainSettingsComponent {
    private static final float PARALLAX_MAX_ANGLE = 4.0f;
    private static final float PARALLAX_DEPTH = 2.2f;
    private static final float PARALLAX_PERSPECTIVE = 1.0f;
    private static final float PARALLAX_SCALE_BOOST = 0.010f;
    private static final float PARALLAX_CONTENT_SHIFT = 1.1f;
    private final Map<Category, List<Setting>> settingsByCategory = new EnumMap<>(Category.class);
    private final List<SettingRow> rows = new ArrayList<>();
    private final List<SettingHit> settingHits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final Map<Category, Float> categoryHoverAnim = new EnumMap<>(Category.class);
    private Category selected = Category.IMAGE;
    private float scroll;
    private float smoothedScroll;
    private boolean draggingScrollbar;
    private boolean scrollbarVisible;
    private float scrollbarX;
    private float scrollbarY;
    private float scrollbarW;
    private float scrollbarH;
    private float scrollbarThumbY;
    private float scrollbarThumbH;
    private float scrollbarMaxScroll;
    private float scrollbarDragOffset;
    private float rightX;
    private float rightY;
    private float rightW;
    private float rightH;
    private float contentX;
    private float contentY;
    private float contentW;
    private float contentH;
    private float lastSettingScale = 1.0f;
    public MainSettingsComponent() {
        rebuildSettings();
    }

    private static List<Setting> buildSettings(List<SettingDef> defs, MainConfig owner) {
        List<Setting> out = SettingFactory.fromDefs(defs);
        for (Setting setting : out) {
            setting.setParent(owner);
            setting.preflightI18n();
        }
        return out;
    }

    private static float settingScale(float menuScale) {
        return Math.max(1.0f, menuScale * 0.66f);
    }

    private static Parallax computeParallax(float mx, float my, float x, float y, float w, float h, float hover) {
        float ease = smooth(Math.max(0f, Math.min(1f, hover)));
        if (ease <= 0.001f || w <= 0f || h <= 0f) return Parallax.NONE;
        float nx = clamp((mx - x) / w * 2f - 1f, -1f, 1f);
        float ny = clamp((my - y) / h * 2f - 1f, -1f, 1f);
        return new Parallax(
                -nx * PARALLAX_MAX_ANGLE * ease,
                ny * PARALLAX_MAX_ANGLE * ease,
                1f + PARALLAX_SCALE_BOOST * ease,
                nx * PARALLAX_CONTENT_SHIFT * ease,
                ny * PARALLAX_CONTENT_SHIFT * ease,
                true
        );
    }

    private static RenderWarpStack.Scope pushParallax(Parallax p, float x, float y, float w, float h) {
        if (p == null || !p.active()) return Renderer2D.pushWarp(null);
        return Renderer2D.pushPerspectiveWarp(x, y, w, h, p.yawDeg(), p.pitchDeg(), 0f, PARALLAX_DEPTH, PARALLAX_PERSPECTIVE, p.scale());
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        ensureSettings();
        lastSettingScale = settingScale(scale);
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        float areaX = menuX + 31f * scale;
        float areaY = menuY + 33f * scale;
        float areaW = menuW - 42f * scale;
        float areaH = menuH - 39f * scale;
        float gap = 8f * scale;
        float leftW = 112f * scale;

        float leftX = areaX;
        float leftY = areaY;
        float leftH = areaH;
        rightX = leftX + leftW + gap;
        rightY = areaY;
        rightW = Math.max(1f, areaW - leftW - gap);
        rightH = areaH;

        drawPanel(leftX, leftY, leftW, leftH, scale, palette);
        drawPanel(rightX, rightY, rightW, rightH, scale, palette);
        renderCategoryList(leftX, leftY, leftW, mx, my, scale, palette);
        renderSettingsPanel(mx, my, scale, palette);
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (button != 0 || !isScrollbarHovered(mx, my)) return false;
        draggingScrollbar = true;
        scrollbarDragOffset = ClickGuiMath.insideRect(mx, my, scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH)
                ? my - scrollbarThumbY
                : scrollbarThumbH * 0.5f;
        scrollToMouse(my);
        return true;
    }

    public boolean mouseClicked(float mx, float my, int button) {
        if (button != 0 && button != 1) return false;
        if (isScrollbarHovered(mx, my)) return true;

        for (CategoryHit hit : categoryHits) {
            if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (hit.category() != selected) {
                selected = hit.category();
                resetScroll();
            }
            return true;
        }

        if (!ClickGuiMath.insideRect(mx, my, rightX, rightY, rightW, rightH)) return false;
        if (ClickGuiMath.insideRect(mx, my, contentX, contentY, contentW, contentH)) {
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, lastSettingScale)) {
                for (SettingHit hit : settingHits) {
                    if (!ClickGuiMath.insideRect(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
                    hit.setting().mouseClicked(mx, my, button);
                    return true;
                }
            }
        }
        return true;
    }

    public void mouseReleased(float mx, float my, int button) {
        if (button == 0) draggingScrollbar = false;
        List<Setting> settings = settingsByCategory.get(selected);
        if (settings == null) return;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, lastSettingScale)) {
            for (Setting setting : settings) {
                setting.mouseReleased(mx, my, button);
            }
        }
    }

    public void scroll(float mx, float my, double amount) {
        if (!ClickGuiMath.insideRect(mx, my, rightX, rightY, rightW, rightH)) return;
        scroll += (float) (amount * 22f);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    private void renderCategoryList(float x, float y, float w, float mx, float my, float scale, SettingsGuiPalette palette) {
        categoryHits.clear();
        float pad = 7f * scale;
        float rowH = 20f * scale;
        float rowY = y + 9f * scale;

        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            boolean active = category == selected;
            boolean hover = ClickGuiMath.insideRect(mx, my, x + pad, rowY, w - pad * 2f, rowH);
            float hoverAnim = AnimationUtility.approach(categoryHoverAnim.getOrDefault(category, 0f), hover ? 1f : 0f, 0.20f);
            categoryHoverAnim.put(category, hoverAnim);
            int bg = active
                    ? LayoutRender2D.alpha(palette.menuCategorySelectedRight(), 0.86f)
                    : hoverAnim > 0.01f ? LayoutRender2D.alpha(palette.menuCategoryHoverRight(), 0.72f * hoverAnim) : 0;
            if (bg != 0) {
                LayoutRender2D.roundedQuad(x + pad, rowY, w - pad * 2f, rowH, 5f * scale, bg, bg, bg, bg);
            }

            if (i < categories.length - 1) {
                renderCategorySeparator(x, rowY + rowH + 2f * scale, w, scale, palette);
            }

            int text = active ? palette.menuHeaderText() : LayoutRender2D.alpha(palette.panelMuted(), 0.72f + 0.20f * hoverAnim);
            ClickGuiRenderer.drawText(
                    ClickGuiRenderer.getInterMedium(),
                    category.title,
                    x + 13f * scale,
                    rowY + 6f * scale,
                    7f * scale,
                    text,
                    false
            );
            categoryHits.add(new CategoryHit(category, x + pad, rowY, w - pad * 2f, rowH));
            rowY += 24f * scale;
        }
    }

    private void renderCategorySeparator(float x, float y, float w, float scale, SettingsGuiPalette palette) {
        float sx = x + 12f * scale;
        float sw = Math.max(1f, w - 24f * scale);
        LayoutRender2D.rectQuad(
                sx,
                y,
                sw,
                0.5f * scale,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.24f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.34f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.34f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.24f)
        );
    }

    private void renderSettingsPanel(float mx, float my, float scale, SettingsGuiPalette palette) {
        buildRows();
        float pad = 8f * scale;
        float titleY = rightY + 8f * scale;
        float titleSize = 8f * scale;
        int text = palette.menuHeaderText();

        LayoutRender2D.rectQuad(
                rightX + pad,
                titleY + 11f * scale,
                rightW - pad * 2f,
                0.5f * scale,
                LayoutRender2D.alpha(palette.menuLineLow(), 0.55f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.82f),
                LayoutRender2D.alpha(palette.menuLineStrong(), 0.82f),
                LayoutRender2D.alpha(palette.menuLineLow(), 0.55f)
        );
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), selected.title, rightX + pad, titleY, titleSize, text, false);

        contentX = rightX + pad;
        contentY = rightY + 27f * scale;
        contentW = Math.max(1f, rightW - pad * 2f - (needsScrollbar() ? 7f * scale : 0f));
        contentH = Math.max(1f, rightH - 35f * scale);

        float totalH = totalRowsHeight();
        float maxScroll = Math.max(0f, totalH - contentH);
        updateScrollbarMetrics(maxScroll, totalH, scale);
        if (draggingScrollbar) {
            scrollToMouse(my);
        }
        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);

        settingHits.clear();
        boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
        float y = contentY + smoothedScroll;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, lastSettingScale)) {
            for (SettingRow row : rows) {
                float h = row.height();
                if (h > 0.5f) {
                    settingHits.add(new SettingHit(row.setting(), contentX, y, contentW, h));
                    if (y + h >= contentY - 1f * scale && y <= contentY + contentH + 1f * scale) {
                        row.setting().render(contentX, y, contentW, mx, my);
                    }
                }
                y += h + row.gap();
            }
        }
        if (clipped) ScissorFunction.pop();

        renderScrollbar(scale, palette);
    }

    private void buildRows() {
        rows.clear();
        List<Setting> settings = settingsByCategory.get(selected);
        if (settings == null) return;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, lastSettingScale)) {
            for (Setting setting : settings) {
                float anim = setting.updateVisibilityAnim();
                boolean targetVisible = setting.isVisibilityTargetVisible();
                if (!targetVisible && anim <= 0.01f) continue;
                rows.add(new SettingRow(setting, anim, setting.getHeight() * anim, 6f * anim));
            }
        }
    }

    private float totalRowsHeight() {
        float out = 0f;
        for (SettingRow row : rows) {
            out += row.height() + row.gap();
        }
        return out;
    }

    private boolean needsScrollbar() {
        return scrollbarVisible;
    }

    private void updateScrollbarMetrics(float maxScroll, float totalH, float scale) {
        scrollbarVisible = maxScroll > 0f && totalH > contentH + 0.5f;
        scrollbarMaxScroll = maxScroll;
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }

        scrollbarW = 4f * scale;
        scrollbarX = rightX + rightW - 10f * scale;
        scrollbarY = contentY;
        scrollbarH = contentH;
        scrollbarThumbH = Math.max(18f * scale, scrollbarH * (contentH / Math.max(contentH, totalH)));
        float ratio = scrollbarMaxScroll <= 0f ? 0f : (-smoothedScroll / scrollbarMaxScroll);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(ratio, 0f, 1f);
    }

    private void renderScrollbar(float scale, SettingsGuiPalette palette) {
        if (!scrollbarVisible) return;
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }
        LayoutRender2D.roundedQuad(
                scrollbarX,
                scrollbarY,
                scrollbarW,
                scrollbarH,
                2f * scale,
                palette.moduleScrollTrackA(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackB(),
                palette.moduleScrollTrackA()
        );
        LayoutRender2D.roundedQuad(
                scrollbarX,
                scrollbarThumbY,
                scrollbarW,
                scrollbarThumbH,
                2f * scale,
                palette.moduleScrollHandleA(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleB(),
                palette.moduleScrollHandleA()
        );
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

    private void drawPanel(float x, float y, float w, float h, float scale, SettingsGuiPalette palette) {
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
        LayoutRender2D.roundedStroke(x, y, w, h, 5f * scale, 0.5f * scale, palette.menuWindowStroke());
    }

    private void ensureSettings() {
        if (!settingsByCategory.isEmpty()) return;
        rebuildSettings();
    }

    private void rebuildSettings() {
        settingsByCategory.clear();
        MainConfig config = MainConfig.get();
        settingsByCategory.put(Category.IMAGE, buildSettings(config.getImageSettingDefs(), config));
        settingsByCategory.put(Category.MISCELLANIOUS, buildSettings(config.getMiscellaneousSettingDefs(), config));
        settingsByCategory.put(Category.SECURITY, buildSettings(config.getSecuritySettingDefs(), config));
        settingsByCategory.put(Category.UTILITY, buildSettings(config.getUtilitySettingDefs(), config));
    }

    private enum Category {
        IMAGE("Image"),
        SECURITY("Security"),
        UTILITY("Utility"),
        MISCELLANIOUS("Miscellanious");

        private final String title;

        Category(String title) {
            this.title = title;
        }
    }

    private record SettingRow(Setting setting, float anim, float height, float gap) {
    }

    private record SettingHit(Setting setting, float x, float y, float w, float h) {
    }

    private record CategoryHit(Category category, float x, float y, float w, float h) {
    }

    private record Parallax(float yawDeg, float pitchDeg, float scale, float shiftX, float shiftY, boolean active) {
        private static final Parallax NONE = new Parallax(0f, 0f, 1f, 0f, 0f, false);
    }
}
