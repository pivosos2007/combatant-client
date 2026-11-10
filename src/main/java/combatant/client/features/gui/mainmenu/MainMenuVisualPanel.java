/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.config.subsystem.VisualConfig;
import combatant.client.features.gui.clickgui.settings.BooleanSetting;
import combatant.client.features.gui.clickgui.settings.ColorSettingNoAlpha;
import combatant.client.features.gui.clickgui.settings.ModeSetting;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingOverlayHost;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.settings.SliderSetting;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;

final class MainMenuVisualPanel {
    private static final float TIP = 13f;
    private static final float PADDING = 17f;
    private final VisualConfig config = VisualConfig.get();
    private final List<Setting> settings = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final SettingOverlayHost overlays = new SettingOverlayHost();
    private boolean open;
    private float reveal;
    private float hover;
    private float scroll;
    private float targetScroll;
    private float settingScale = 1f;
    private float maxScroll;
    private float buttonX;
    private float buttonY;
    private float buttonRadius;
    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float contentX;
    private float contentY;
    private float contentW;
    private float contentH;

    MainMenuVisualPanel() {
        add(new ModeSetting("menuHoneycombPalette", config.menuHoneycombPaletteValue()));
        add(new BooleanSetting("menuHoneycombGrid", config.menuHoneycombGridValue()));
        add(new BooleanSetting("menuHoneycombCursorGlow", config.menuHoneycombCursorGlowValue()));
        add(new BooleanSetting("menuHoneycombAmbient", config.menuHoneycombAmbientValue()));
        add(new SliderSetting<>("menuGridIntensity", config.menuGridIntensityValue()));
        add(new SliderSetting<>("menuCursorIntensity", config.menuCursorIntensityValue()));
        add(new SliderSetting<>("menuAmbientIntensity", config.menuAmbientIntensityValue()));
        add(new SliderSetting<>("menuHoneycombBloom", config.menuHoneycombBloomValue()));
        add(new BooleanSetting("menuCrystalBackground", config.menuCrystalBackgroundValue()));
        add(new SliderSetting<>("menuCrystalIntensity", config.menuCrystalIntensityValue())
                .visibleWhen(config::menuCrystalBackgroundEnabled));
        add(new SliderSetting<>("menuCrystalGloss", config.menuCrystalGlossValue())
                .visibleWhen(config::menuCrystalBackgroundEnabled));
        add(new ColorSettingNoAlpha("menuGridColor", config.menuGridColorValue())
                .visibleWhen(config::menuCustomColors));
        add(new ColorSettingNoAlpha("menuGlowColor", config.menuGlowColorValue())
                .visibleWhen(config::menuCustomColors));
        add(new BooleanSetting("menuFluidEnabled", config.menuFluidEnabledValue()));
        add(new BooleanSetting("menuFluidRainbow", config.menuFluidRainbowValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new ModeSetting("menuFluidQuality", config.menuFluidQualityValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new SliderSetting<>("menuFluidIntensity", config.menuFluidIntensityValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new SliderSetting<>("menuFluidSize", config.menuFluidSizeValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new SliderSetting<>("menuFluidVorticity", config.menuFluidVorticityValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new SliderSetting<>("menuFluidBloom", config.menuFluidBloomValue())
                .visibleWhen(config::menuFluidEnabled));
        add(new ColorSettingNoAlpha("menuFluidColor", config.menuFluidColorValue())
                .visibleWhen(() -> config.menuCustomColors() && config.menuFluidEnabled() && !config.menuFluidRainbow()));
    }

    private void add(Setting setting) {
        setting.setParent(config);
        settings.add(setting);
    }

    private void layout(float width, float height, MainMenuBackdrop.GridLayout grid) {
        float radius = grid.radius();
        int row = Math.max(1, Math.round((height - radius * 1.62f - grid.originY()) / grid.stepY()));
        int col = Math.round((radius * 2.2f - grid.originX()) / grid.stepX() - row * 0.5f);
        buttonX = grid.originX() + grid.stepX() * (col + row * 0.5f);
        buttonY = grid.originY() + grid.stepY() * row;
        while (buttonX < radius * 1.05f) buttonX += grid.stepX();
        while (buttonX > width - radius * 1.05f) buttonX -= grid.stepX();
        buttonY = Math.max(radius * 1.05f, Math.min(height - radius * 1.05f, buttonY));
        buttonRadius = radius;
        settingScale = Math.max(0.98f, Math.min(1.14f,
                Math.min(width / 1450f, height / 820f) * 1.12f));
        panelW = Math.min(468f, Math.max(300f, width - buttonX - radius - 22f));
        panelH = Math.min(665f, Math.max(225f, height - 28f));
        panelX = Math.max(9f, Math.min(width - panelW - 9f, buttonX + radius * 0.83f));
        panelY = Math.max(9f, Math.min(height - panelH - 9f, buttonY - panelH * 0.83f));
        contentX = panelX + TIP + PADDING + 2f;
        contentW = Math.max(100f, panelW - TIP - PADDING * 2f - 5f);
        contentY = panelY + 69f + (MainMenuBackdrop.computeSupported() ? 0f : 47f);
        contentH = Math.max(45f, panelH - (contentY - panelY) - 20f);
        overlays.setViewport(panelX + TIP + 6f, panelY + 7f,
                panelW - TIP - 13f, panelH - 14f);
    }

    boolean open() { return open; }

    void close() {
        open = false;
        overlays.closeImmediately();
        hits.clear();
        config.saveConfig();
    }

    void render(float width, float height, float opacity, float mx, float my,
                MainMenuBackdrop.GridLayout grid, float dt) {
        if (grid == null) return;
        layout(width, height, grid);
        float s = Math.min(1f, dt * 13f);
        reveal += ((open ? 1f : 0f) - reveal) * s;
        hover += ((buttonContains(mx, my) ? 1f : 0f) - hover) * Math.min(1f, dt * 14f);
        drawButton(opacity);
        if (reveal >= 0.008f) drawPanel(opacity, mx, my, dt);
    }

    private void glass(UiPrimitive primitive, int tint, float alpha) {
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.withLiquidGlassSceneSource(UiBackdropRequest.SceneSource.CURRENT_TARGET,
                () -> renderer.withLiquidGlassBlurProfile(Renderer2D.BlurQuality.LIQUID_GLASS, 0.68f,
                        () -> renderer.withLiquidGlassMaterial(UiLiquidGlassMaterial.DEFAULT,
                                () -> renderer.liquidGlassPrimitive(primitive, tint,
                                        Math.min(1f, alpha), Math.min(1f, alpha),
                                        Renderer2D.LiquidGlassPreset.HUD_SURFACE_LARGE))));
    }

    private void drawButton(float opacity) {
        UiPrimitive hex = UiPrimitive.builder(buttonX - buttonRadius * 0.8660254f,
                        buttonY - buttonRadius, buttonRadius * 1.7320508f, buttonRadius * 2f)
                .customConvex(0.5, 0.0, 1.0, 0.25, 1.0, 0.75,
                        0.5, 1.0, 0.0, 0.75, 0.0, 0.25)
                .rounding(2.4f).build();
        int base = Theme.theme().windowBg();
        int tint = ((Math.round((87f + hover * 26f + reveal * 18f) * opacity) & 255) << 24)
                | (base & 0x00FFFFFF);
        glass(hex, tint, opacity);
        float icon = buttonRadius * 0.55f;
        int color = Theme.theme().textPrimary();
        Renderer2D.COLOR.svg("paintbrush", buttonX - icon * 0.5f, buttonY - icon * 0.5f,
                icon, icon, SvgRenderOptions.overrideColor(
                        (color & 0x00FFFFFF) | (Math.round(opacity * 255f) << 24)));
        if (hover > 0.5f) SystemCursor.set(SystemCursor.CursorType.HAND);
    }

    private void drawPanel(float opacity, float mx, float my, float dt) {
        float k = 1f - (1f - reveal) * (1f - reveal);
        float x = panelX + (1f - k) * -7f;
        float w = panelW;
        float h = panelH;
        float anchor = Math.max(0.13f, Math.min(0.87f, (buttonY - panelY) / h));
        UiPrimitive primitive = UiPrimitive.builder(x, panelY, w, h)
                .customConvex(0.0, anchor, TIP / w, 0.0,
                        1.0, 0.0, 1.0, 1.0, TIP / w, 1.0)
                .rounding(8.0f).build();
        int tint = Theme.theme().windowBg() & 0x00FFFFFF;
        tint |= Math.round(135f * opacity * k) << 24;
        glass(primitive, tint, opacity * k);
        double originalAlpha = Renderer2D.COLOR.getAlpha();
        float originalTextAlpha = LayoutRender2D.getRenderAlphaMultiplier();
        float contentAlpha = Math.max(0f, Math.min(1f, opacity * k));
        Renderer2D.COLOR.setAlpha(originalAlpha * contentAlpha);
        LayoutRender2D.setRenderAlphaMultiplier(originalTextAlpha * contentAlpha);
        try {
        int title = Theme.theme().textPrimary();
        int muted = Theme.theme().textMuted();
        float titleY = panelY + 19f;
        Renderer2D.COLOR.svg("paintbrush", contentX, titleY, 19f, 19f,
                SvgRenderOptions.overrideColor(title));
        LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_BOLD.renderer(),
                I18n.get("screen.combatant.menu_visual.title"), contentX + 27f,
                titleY + 1f, 15f, title, false);
        Renderer2D.COLOR.quad(contentX, panelY + 54f, contentW, 1f,
                (muted & 0x00FFFFFF) | 0x43000000);
        if (!MainMenuBackdrop.computeSupported()) {
            Renderer2D.COLOR.svg("triangle-alert", contentX, panelY + 68f, 17f, 17f,
                    SvgRenderOptions.overrideColor(0xFFF4C65A));
            LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(),
                    I18n.get("screen.combatant.menu_visual.compute_warning"), contentX + 23f,
                    panelY + 67f, 12.5f, 0xFFF4C65A, false);
            LayoutRender2D.drawText(BuiltinFontCatalog.ONEST_MEDIUM.renderer(),
                    I18n.get("screen.combatant.menu_visual.compute_hint"), contentX + 23f,
                    panelY + 86f, 11f, 0xFFD9AC51, false);
        }
        hits.clear();
        targetScroll = clamp(targetScroll, -maxScroll, 0f);
        float response = 1f - (float)Math.exp(-Math.max(0f, Math.min(dt, 0.08f)) * 15f);
        scroll += (targetScroll - scroll) * response;
        if (Math.abs(targetScroll - scroll) < 0.05f) scroll = targetScroll;
        float y = contentY + scroll;
        float total = 0f;
        boolean clip = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(
                SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            for (Setting setting : settings) {
                float visibility = clamp(setting.updateVisibilitySafely(), 0f, 1f);
                boolean visible = setting.isVisibilityTargetVisibleSafely();
                if (!visible && visibility < 0.01f) continue;
                float fullHeight = setting.getHeightSafely();
                if (fullHeight < 0.5f) continue;
                float animatedHeight = fullHeight * visibility;
                float gap = 11f * smoothstep(visibility);
                if (animatedHeight > 0.5f && y + animatedHeight >= contentY && y <= contentY + contentH) {
                    renderRow(setting, contentX, y, contentW, fullHeight, animatedHeight,
                            visibility, mx, my);
                    if (visible && visibility > 0.8f) {
                        hits.add(new Hit(setting, contentX, y, contentW, animatedHeight));
                    }
                }
                y += animatedHeight + gap;
                total += animatedHeight + gap;
            }
        } finally {
            if (clip) ScissorFunction.pop();
        }
        maxScroll = Math.max(0f, total - contentH);
        targetScroll = clamp(targetScroll, -maxScroll, 0f);
        scroll = clamp(scroll, -maxScroll, 0f);
        if (maxScroll > 0.2f) {
            float barH = Math.max(28f, contentH * contentH / (contentH + maxScroll));
            float by = contentY + (-scroll / maxScroll) * (contentH - barH);
            Renderer2D.COLOR.roundedRect(panelX + panelW - 10f, by, 3f, barH, 1.5f, 1f,
                    (Theme.theme().accent() & 0x00FFFFFF) | 0xAA000000);
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(
                SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            overlays.render(mx, my);
        }
        } finally {
            LayoutRender2D.restoreRenderAlphaMultiplier(originalTextAlpha);
            Renderer2D.COLOR.setAlpha(originalAlpha);
        }
    }

    private void renderRow(Setting setting, float x, float y, float width,
                           float fullHeight, float animatedHeight, float visibility,
                           float mx, float my) {
        float fade = smoothstep(visibility);
        double originalAlpha = Renderer2D.COLOR.getAlpha();
        float originalTextAlpha = LayoutRender2D.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(originalAlpha * fade);
        LayoutRender2D.setRenderAlphaMultiplier(originalTextAlpha * fade);
        boolean clipped = visibility < 0.985f && animatedHeight < fullHeight - 0.75f
                && ScissorFunction.pushRaw(x - 2f, y - 2f, width + 4f, animatedHeight + 4f);
        try {
            setting.renderSafely(x, y + (1f - fade) * 6f, width, mx, my);
        } finally {
            if (clipped) ScissorFunction.pop();
            LayoutRender2D.restoreRenderAlphaMultiplier(originalTextAlpha);
            Renderer2D.COLOR.setAlpha(originalAlpha);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothstep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private boolean buttonContains(float x, float y) {
        float dx = Math.abs(x - buttonX);
        float dy = Math.abs(y - buttonY);
        return dx < 0.8660254f * buttonRadius && dx / 1.7320508f + dy < buttonRadius;
    }

    private boolean panelContains(float x, float y) {
        return inside(x, y, panelX, panelY, panelW, panelH);
    }

    boolean click(float mx, float my, int button) {
        if (button != combatant.client.input.InputKeys.MOUSE_LEFT) return false;
        if (buttonContains(mx, my)) {
            if (open) close();
            else { open = true; scroll = 0f; targetScroll = 0f; }
            return true;
        }
        if (!open) return false;
        if (overlays.hasActiveOverlay()) {
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
                if (overlays.mouseClicked(mx, my, button)) return true;
            }
        }
        if (!panelContains(mx, my)) { close(); return true; }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            for (Hit hit : hits) {
                if (inside(mx, my, hit.x, hit.y, hit.w, hit.h)
                        && inside(mx, my, contentX, contentY, contentW, contentH)) {
                    hit.setting.mouseClickedSafely(mx, my, button, hit.x, hit.y, hit.w);
                } else {
                    hit.setting.mouseClickedOutsideSafely(mx, my, button);
                }
            }
        }
        config.saveConfig();
        return true;
    }

    boolean release(float mx, float my, int button) {
        if (!open) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            if (overlays.hasActiveOverlay()) overlays.mouseReleased(mx, my, button);
            for (Setting setting : settings) setting.mouseReleasedSafely(mx, my, button);
        }
        config.saveConfig();
        return true;
    }

    boolean scroll(float mx, float my, double amount) {
        if (!open || !panelContains(mx, my)) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            if (overlays.hasActiveOverlay() && overlays.mouseScrolled(mx, my, amount)) return true;
            for (Hit hit : hits) {
                if (inside(mx, my, hit.x, hit.y, hit.w, hit.h)
                        && hit.setting.mouseScrolledSafely(mx, my, amount)) return true;
            }
        }
        targetScroll = clamp(targetScroll + (float)amount * 54f, -maxScroll, 0f);
        return true;
    }

    boolean key(int key, int scan, int modifiers) {
        if (!open) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            if (overlays.hasActiveOverlay() && overlays.keyPressed(key, scan, modifiers)) return true;
            for (Setting setting : settings) if (setting.keyPressedSafely(key, scan, modifiers)) return true;
        }
        if (key == combatant.client.input.InputKeys.ESCAPE) { close(); return true; }
        return false;
    }

    boolean character(char c, int modifiers) {
        if (!open) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MAP_SETTINGS, settingScale, overlays)) {
            if (overlays.hasActiveOverlay() && overlays.charTyped(c, modifiers)) return true;
            for (Setting setting : settings) if (setting.charTypedSafely(c, modifiers)) return true;
        }
        return false;
    }

    private static boolean inside(float x, float y, float bx, float by, float w, float h) {
        return x >= bx && y >= by && x <= bx + w && y <= by + h;
    }

    private record Hit(Setting setting, float x, float y, float w, float h) {}
}
