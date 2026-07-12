/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.config;

import combatant.client.config.profile.ConfigProfileDiff;
import combatant.client.config.profile.ConfigProfileMeta;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.clickgui.util.ClickGuiMath;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

public final class ConfigDiffPanelComponent {
    private float scroll;
    private float smoothedScroll;
    private float openAnim;
    private boolean openTarget;
    private float saveHoverAnim;
    private float closeHoverAnim;
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
    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float saveX;
    private float saveY;
    private float saveW;
    private float saveH;
    private float closeX;
    private float closeY;
    private float closeW;
    private float closeH;

    private static String stringify(Object value) {
        if (value == null) return "null";
        String text = String.valueOf(value);
        if (text.length() > 34) text = text.substring(0, 31) + "...";
        return text;
    }

    public void resetScroll() {
        scroll = 0f;
        smoothedScroll = 0f;
        draggingScrollbar = false;
    }

    public void open() {
        openTarget = true;
    }

    public void close() {
        openTarget = false;
        draggingScrollbar = false;
    }

    public boolean isActive() {
        return openTarget || openAnim > 0.001f;
    }

    public boolean isOpen() {
        return openTarget;
    }

    public void render(float x,
                       float y,
                       float w,
                       float h,
                       ConfigProfileMeta selected,
                       ConfigProfileDiff diff,
                       String error,
                       float scale,
                       SettingsGuiPalette palette) {
        float dt = AnimationUtility.deltaTime();
        float target = openTarget ? 1f : 0f;
        openAnim = AnimationUtility.approach(openAnim, target, dt, openTarget ? 12f : 13f);
        openAnim = AnimationUtility.snap(openAnim, target, 0.01f);
        if (!openTarget && openAnim <= 0.001f) {
            openAnim = 0f;
            return;
        }

        float reveal = AnimationUtility.easeOutBack(openAnim, 0.85f);
        x += (1f - reveal) * 10f * scale;
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;

        int shadow = LayoutRender2D.alpha(palette.panelShadow(), openAnim);
        int blurTint = LayoutRender2D.alpha(palette.panelBlurTint(), openAnim);
        int bgLeft = LayoutRender2D.alpha(palette.panelBgLeft(), openAnim);
        int bgRight = LayoutRender2D.alpha(palette.panelBgRight(), openAnim);
        int stroke = LayoutRender2D.alpha(palette.panelStroke(), openAnim);
        int divider = LayoutRender2D.alpha(palette.panelDivider(), openAnim);
        int text = LayoutRender2D.alpha(palette.panelText(), openAnim);

        LayoutRender2D.roundedSoftShadow(x, y, w, h, 8f * scale, 8f * scale, 0.018f, shadow);
        ClickGuiRenderer.drawBlur(x, y, w, h, 8f * scale, blurTint, (200f / 255f) * openAnim);
        LayoutRender2D.roundedQuad(
                x, y, w, h, 8f * scale,
                bgLeft,
                bgRight,
                bgRight,
                bgLeft
        );
        LayoutRender2D.roundedStroke(x, y, w, h, 8f * scale, 0.1f * scale, stroke);
        LayoutRender2D.rect(x, y + 22f * scale, w, 0.5f * scale, divider);

        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterRegular(),
                ClickGuiI18n.tr("clickgui.settings.config.diff.title", "Diff Settings"),
                x + 10f * scale,
                y + 9f * scale,
                7.5f * scale,
                text,
                false
        );
        renderHeaderActions(x, y, w, mx(), my(), scale, palette);

        float bodyX = x + 8f * scale;
        float bodyY = y + 27f * scale;
        float bodyW = w - 16f * scale;
        float bodyH = h - 31f * scale;
        float contentH = contentHeight(selected, diff, error, scale);
        float maxScroll = Math.max(0f, contentH - bodyH);
        updateScrollbarMetrics(bodyX, bodyY, bodyW, bodyH, maxScroll, scale);
        if (draggingScrollbar) scrollToMouse(ClickGuiRenderer.getMouseY());

        scroll = ClickGuiMath.clamp(scroll, -maxScroll, 0f);
        smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, draggingScrollbar ? 0.55f : 0.2f);
        smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, draggingScrollbar ? 0.01f : 0.05f);
        boolean clipped = ScissorFunction.pushRaw(bodyX, bodyY, bodyW, bodyH);
        renderBody(bodyX, bodyY + smoothedScroll, bodyW - (scrollbarVisible ? 7f * scale : 0f), bodyY, bodyH, selected, diff, error, scale, palette);
        if (clipped) ScissorFunction.pop();

        renderScrollbar(scale, palette);
    }

    public HeaderAction headerAction(float mx, float my, int button) {
        if (!openTarget || openAnim <= 0.01f) return HeaderAction.NONE;
        if (button != 0) return HeaderAction.NONE;
        if (ClickGuiMath.insideRect(mx, my, closeX, closeY, closeW, closeH)) return HeaderAction.CLOSE;
        if (ClickGuiMath.insideRect(mx, my, saveX, saveY, saveW, saveH)) return HeaderAction.SAVE;
        return HeaderAction.NONE;
    }

    public boolean mousePressedScrollbar(float mx, float my, int button) {
        if (!openTarget || openAnim <= 0.01f) return false;
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
        if (button == 0) draggingScrollbar = false;
    }

    public boolean scroll(float mx, float my, double amount) {
        if (!openTarget || openAnim <= 0.01f) return false;
        if (!ClickGuiMath.insideRect(mx, my, panelX, panelY, panelW, panelH)) return false;
        if (!scrollbarVisible && scrollbarMaxScroll <= 0.5f) return true;
        scroll += (float) (amount * 20f);
        return true;
    }

    private void renderHeaderActions(float x,
                                     float y,
                                     float w,
                                     float mx,
                                     float my,
                                     float scale,
                                     SettingsGuiPalette palette) {
        float btn = 16f * scale;
        closeX = x + w - btn - 4f * scale;
        closeY = y + 3f * scale;
        closeW = btn;
        closeH = btn;
        saveX = closeX - btn - scale;
        saveY = closeY;
        saveW = btn;
        saveH = btn;

        boolean saveHover = ClickGuiMath.insideRect(mx, my, saveX, saveY, saveW, saveH);
        boolean closeHover = ClickGuiMath.insideRect(mx, my, closeX, closeY, closeW, closeH);
        saveHoverAnim = AnimationUtility.approach(saveHoverAnim, saveHover ? 1f : 0f, AnimationUtility.deltaTime(), 10f);
        closeHoverAnim = AnimationUtility.approach(closeHoverAnim, closeHover ? 1f : 0f, AnimationUtility.deltaTime(), 10f);

        drawHeaderIcon(saveX, saveY, btn, "save", saveHoverAnim, scale, palette);
        LayoutRender2D.rect(closeX - scale, y + 6f * scale, 0.5f * scale, 10f * scale, LayoutRender2D.alpha(palette.panelDivider(), openAnim));
        drawHeaderIcon(closeX, closeY, btn, "x", closeHoverAnim, scale, palette);
        if (saveHover || closeHover) SystemCursor.set(SystemCursor.CursorType.MOVE);
    }

    private void drawHeaderIcon(float x,
                                float y,
                                float size,
                                String icon,
                                float hover,
                                float scale,
                                SettingsGuiPalette palette) {
        boolean save = "save".equals(icon);
        int bgA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.moduleCardTop(), save ? palette.menuCategorySelectedLeft() : palette.menuCategoryHoverLeft(), 0.14f + hover * 0.22f), openAnim);
        int bgB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.moduleCardTopStrong(), save ? palette.menuCategorySelectedRight() : palette.menuCategoryHoverRight(), 0.12f + hover * 0.18f), openAnim);
        int bgC = LayoutRender2D.alpha(SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.moduleCardTopStrong(), 0.12f), save ? palette.menuCategorySelectedRight() : palette.menuCategoryHoverRight(), 0.08f + hover * 0.15f), openAnim);
        LayoutRender2D.roundedSoftShadow(
                x,
                y + 0.4f * scale,
                size,
                size,
                3.5f * scale,
                4f * scale,
                0.015f + hover * 0.02f,
                LayoutRender2D.alpha(palette.menuShadow(), openAnim * (0.50f + hover * 0.45f))
        );
        LayoutRender2D.roundedQuad(x, y, size, size, 3.5f * scale, bgA, bgB, bgC, bgA);
        LayoutRender2D.roundedStrokeQuad(
                x,
                y,
                size,
                size,
                3.5f * scale,
                (0.35f + hover * 0.18f) * scale,
                LayoutRender2D.alpha(palette.moduleDividerStart(), openAnim * (0.54f + hover * 0.24f)),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), openAnim * (0.62f + hover * 0.24f)),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), openAnim * (0.48f + hover * 0.20f)),
                LayoutRender2D.alpha(palette.moduleDividerStart(), openAnim * (0.54f + hover * 0.24f))
        );

        int color = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelText(), palette.menuCategoryText(), 0.16f + hover * 0.38f), openAnim);
        Renderer2D.COLOR.svg(
                icon,
                x + 4.6f * scale,
                y + 4.6f * scale,
                size - 9.2f * scale,
                size - 9.2f * scale,
                SvgRenderOptions.overrideColor(color)
        );
    }

    private float mx() {
        return ClickGuiRenderer.getMouseX();
    }

    private float my() {
        return ClickGuiRenderer.getMouseY();
    }

    private void renderBody(float x,
                            float y,
                            float w,
                            float clipY,
                            float clipH,
                            ConfigProfileMeta selected,
                            ConfigProfileDiff diff,
                            String error,
                            float scale,
                            SettingsGuiPalette palette) {
        if (error != null && !error.isBlank()) {
            drawLine(ClickGuiI18n.tr("clickgui.settings.config.diff.error", "Diff error: %s", error), x, y, 7.2f * scale, palette.panelMuted());
            return;
        }
        if (selected == null) {
            drawLine(ClickGuiI18n.tr("clickgui.settings.config.diff.select_config", "Select a config and press diff."), x, y, 7.2f * scale, palette.panelMuted());
            return;
        }
        if (diff == null) {
            drawLine(selected.name(), x, y, 7.2f * scale, palette.panelText());
            drawLine(ClickGuiI18n.tr("clickgui.settings.config.diff.not_loaded", "Diff is not loaded."), x, y + 10f * scale, 7.2f * scale, palette.panelMuted());
            return;
        }
        if (diff.isEmpty()) {
            drawLine(selected.name(), x, y, 7.2f * scale, palette.panelText());
            drawLine(ClickGuiI18n.tr("clickgui.settings.config.diff.no_differences", "No differences."), x, y + 11f * scale, 7.2f * scale, palette.panelMuted());
            return;
        }

        float cy = y;
        drawVisibleLine(ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), selected.name(), 7.4f * scale, w), x, cy, 7.4f * scale, palette.panelText(), clipY, clipH, scale);
        cy += 11f * scale;
        drawVisibleLine(ClickGuiI18n.tr("clickgui.settings.config.diff.changed", "Changes: %s", diff.valueChangeCount()), x, cy, 7f * scale, palette.panelMuted(), clipY, clipH, scale);
        cy += 13f * scale;

        for (ConfigProfileDiff.Entry entry : diff.entries()) {
            String owner = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterMedium(), entry.displayName(), 7.4f * scale, w);
            drawVisibleLine(owner, x, cy, 7.4f * scale, palette.panelText(), clipY, clipH, scale);
            cy += 9.5f * scale;

            for (ConfigProfileDiff.Value value : entry.values()) {
                String text = value.key() + ": " + stringify(value.currentValue()) + " -> " + stringify(value.selectedValue());
                text = ClickGuiRenderer.fitText(ClickGuiRenderer.getInterRegular(), text, 6.7f * scale, w - 5f * scale);
                drawVisibleLine(text, x + 5f * scale, cy, 6.7f * scale, palette.panelMuted(), clipY, clipH, scale);
                cy += 8.5f * scale;
            }
            cy += 4f * scale;
        }
    }

    private float contentHeight(ConfigProfileMeta selected, ConfigProfileDiff diff, String error, float scale) {
        if (error != null && !error.isBlank()) return 11f * scale;
        if (selected == null) return 11f * scale;
        if (diff == null || diff.isEmpty()) return 23f * scale;

        float h = 24f * scale;
        for (ConfigProfileDiff.Entry entry : diff.entries()) {
            h += 9.5f * scale;
            h += entry.values().size() * 8.5f * scale;
            h += 4f * scale;
        }
        return h;
    }

    private void renderScrollbar(float scale, SettingsGuiPalette palette) {
        if (!scrollbarVisible) return;
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        if (isScrollbarHovered(mx, my) || draggingScrollbar) SystemCursor.set(SystemCursor.CursorType.SCROLL);

        LayoutRender2D.roundedQuad(scrollbarX, scrollbarY, scrollbarW, scrollbarH, 2f * scale,
                LayoutRender2D.alpha(palette.moduleScrollTrackA(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollTrackA(), openAnim));
        LayoutRender2D.roundedQuad(scrollbarX, scrollbarThumbY, scrollbarW, scrollbarThumbH, 2f * scale,
                LayoutRender2D.alpha(palette.moduleScrollHandleA(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleB(), openAnim),
                LayoutRender2D.alpha(palette.moduleScrollHandleA(), openAnim));
    }

    private void updateScrollbarMetrics(float x, float y, float w, float h, float maxScroll, float scale) {
        scrollbarVisible = maxScroll > 0.5f;
        scrollbarMaxScroll = Math.max(0f, maxScroll);
        if (!scrollbarVisible) {
            draggingScrollbar = false;
            scrollbarX = scrollbarY = scrollbarW = scrollbarH = scrollbarThumbY = scrollbarThumbH = 0f;
            return;
        }
        scrollbarW = 3f * scale;
        scrollbarX = x + w - scrollbarW - 1.5f * scale;
        scrollbarY = y + 2f * scale;
        scrollbarH = Math.max(1f, h - 4f * scale);
        scrollbarThumbH = Math.max(16f * scale, scrollbarH * (scrollbarH / (scrollbarMaxScroll + scrollbarH)));
        float scrollRatio = scrollbarMaxScroll <= 0f ? 0f : (-smoothedScroll / scrollbarMaxScroll);
        scrollbarThumbY = scrollbarY + (scrollbarH - scrollbarThumbH) * AnimationUtility.clamp(scrollRatio, 0f, 1f);
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

    private void drawVisibleLine(String text, float x, float y, float size, int color, float clipY, float clipH, float scale) {
        if (y + 9f * scale < clipY || y > clipY + clipH) return;
        drawLine(text, x, y, size, color);
    }

    private void drawLine(String text, float x, float y, float size, int color) {
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterRegular(), text, x, y, size, LayoutRender2D.alpha(color, openAnim), false);
    }

    public enum HeaderAction {
        NONE,
        SAVE,
        CLOSE
    }
}
