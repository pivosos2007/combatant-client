/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.module;

import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.config.MainConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.util.ClickGuiHintOverlay;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UiScriptAsset("combatant:modules/clickgui/settings_panel")
public final class SettingsPanelComponent {
private static final float DROPDOWN_PANEL_W = 115.0f;
    private static final float DROPDOWN_PANEL_H = 240.0f;
    private static final float DROPDOWN_HEADER_H = 22.0f;
    private static final float DROPDOWN_SEPARATOR_H = 0.0f;
    private final List<Setting> settings = new ArrayList<>();
    private final List<SettingRow> rows = new ArrayList<>();
    private final List<SettingHit> hits = new ArrayList<>();
    private final SettingRenderSurface renderSurface;
    private final Minecraft mc = Minecraft.getInstance();
    private final UiScriptModuleHandle panelModuleHandle = HudScriptLayouts.handle(SettingsPanelComponent.class);
    private final CachedUiScriptRuntime panelRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private String targetId = "";
    private String title = "Settings";
    private boolean hudContext = false;
    private boolean editorContext = false;
    private boolean openTarget = false;
    private float openAnim = 0f;
    private float panelHeight = 120f;
    private float scroll = 0f;
    private float smoothedScroll = 0f;
    private float closeHoverAnim = 0f;
    private boolean dragging = false;
    private float dragOffsetX = 0f;
    private float dragOffsetY = 0f;
    private boolean manualPos = false;
    private float manualX = 0f;
    private float manualY = 0f;
    private boolean limitedHeight = false;
    private float hudExtraTop = 0f;
    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float closeX;
    private float closeY;
    private float closeW;
    private float closeH;
    private float contentX;
    private float contentY;
    private float contentW;
    private float contentH;
    private float maxScroll;
    private float contentPadLeft;
    private float contentPadRight;
    private float pillX;
    private float pillY;
    private float pillW;
    private float pillH;
    private float pillActiveAnim = 1f;
    private boolean hintsVisible = true;
    private HudPreviewMode hudPreviewMode = HudPreviewMode.ONLY_CURRENT;
    private float lastSettingScale = 1.0f;
    public SettingsPanelComponent() {
        this(SettingRenderSurface.MODULES);
    }
    public SettingsPanelComponent(SettingRenderSurface renderSurface) {
        this.renderSurface = renderSurface == null ? SettingRenderSurface.SETTINGS : renderSurface;
    }

    public void setHintsVisible(boolean visible) {
        this.hintsVisible = visible;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean isHintToggle(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_H && (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public void open(String id, String title, List<Setting> source, boolean hudContext) {
        if (id == null) return;
        resetHudPreview();
        this.targetId = id;
        this.title = title == null || title.isBlank() ? id : title;
        this.settings.clear();
        if (source != null) this.settings.addAll(source);
        this.hudContext = hudContext;
        this.editorContext = false;
        this.openTarget = true;
        this.scroll = 0f;
        this.smoothedScroll = 0f;
        this.closeHoverAnim = 0f;
        this.dragging = false;
        this.rows.clear();
        this.hits.clear();
        if (hudContext) {
            this.manualPos = false;
        }
        if (hudContext) {
            this.hudPreviewMode = HudPreviewMode.ONLY_CURRENT;
            this.pillActiveAnim = 1f;
            applyHudPreviewMode();
        }
    }

    public void openEditor(String id, String title, List<Setting> source) {
        open(id, title, source, false);
        this.editorContext = true;
        this.manualPos = false;
    }

    public void close() {
        openTarget = false;
        dragging = false;
    }

    public boolean isActive() {
        return openTarget || openAnim > 0.001f;
    }

    public boolean shouldMaskMenu() {
        return (hudContext || editorContext) && isActive();
    }

    public boolean blocksMenuInput() {
        return (hudContext || editorContext) && (openTarget || openAnim > 0.35f);
    }

    public void render(float menuX, float menuY, float menuW, float menuH, float mx, float my, float scale) {
        float dt = AnimationUtility.deltaTime(AnimationUtility.Mode.MILLIS);
        float openTargetValue = openTarget ? 1f : 0f;
        openAnim = AnimationUtility.approach(openAnim, openTargetValue, dt, openTarget ? 12f : 13f);
        openAnim = AnimationUtility.snap(openAnim, openTargetValue, 0.01f);
        float pillTarget = hudPreviewMode == HudPreviewMode.ALL_ENABLED ? 0f : 1f;
        pillActiveAnim = AnimationUtility.approach(pillActiveAnim, pillTarget, dt, 14f);
        pillActiveAnim = AnimationUtility.snap(pillActiveAnim, pillTarget, 0.002f);

        if (!openTarget && openAnim <= 0.001f) {
            openAnim = 0f;
            if (hudContext) {
                resetHudPreview();
                hudContext = false;
            }
            editorContext = false;
            return;
        }

        float panelScale = panelScale(scale);
        lastSettingScale = settingScale(panelScale);
        buildRows(panelScale);

        float baseW = DROPDOWN_PANEL_W * panelScale;
        float baseX = menuX + menuW + 24f * panelScale;
        float baseY = menuY + 4f * panelScale;
        if (manualPos) {
            baseX = manualX;
            baseY = manualY;
        }
        hudExtraTop = hudContext ? 20f * panelScale : 0f;
        float targetH = AnimationUtility.clamp(
                totalRowsHeight() + (DROPDOWN_HEADER_H + DROPDOWN_SEPARATOR_H) * panelScale + 5f * panelScale + hudExtraTop,
                0f,
                DROPDOWN_PANEL_H * panelScale
        );
        panelHeight = AnimationUtility.approach(panelHeight, targetH, dt, 14f);
        panelHeight = AnimationUtility.snap(panelHeight, targetH, 0.25f);

        float distanceProgress = AnimationUtility.easeInOutCubic(openAnim);
        float fadeProgress = AnimationUtility.easeInOutCubic(openAnim);
        if (dragging) {
            manualPos = true;
            manualX = mx + dragOffsetX;
            manualY = my + dragOffsetY;
            panelX = manualX;
            panelY = manualY;
        } else {
            panelX = baseX;
            panelY = baseY;
        }
        panelW = baseW;
        panelH = panelHeight;
        limitedHeight = panelH >= (DROPDOWN_PANEL_H * panelScale - 0.25f * panelScale);
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        try (RenderWarpStack.Scope lifecycleScope = pushLifecycleWarp(distanceProgress)) {
            closeW = 14f * panelScale;
            closeH = 12f * panelScale;
            closeX = panelX + panelW - closeW - 5.5f * panelScale;
            closeY = panelY + (DROPDOWN_HEADER_H * panelScale - closeH) * 0.5f;
            boolean closeHover = inside(mx, my, closeX, closeY, closeW, closeH);
            closeHoverAnim = AnimationUtility.approach(closeHoverAnim, closeHover ? 1f : 0f, dt, 10f);
            if (closeHoverAnim > 0.08f) SystemCursor.set(SystemCursor.CursorType.HAND);

            if (hudContext) updateHudModePillBounds(panelScale);

            float contentStartY = panelY + (DROPDOWN_HEADER_H + DROPDOWN_SEPARATOR_H) * panelScale;
            if (hudContext) {
                contentStartY += hudExtraTop;
            }

            contentPadLeft = 7f * panelScale;
            contentPadRight = 11f * panelScale;
            float contentClipInset = 3.0f * panelScale;
            contentX = panelX + contentPadLeft;
            contentY = contentStartY + contentClipInset;
            contentW = Math.max(1f, panelW - contentPadLeft - contentPadRight);
            contentH = Math.max(0f, panelY + panelH - contentY - 7.0f * panelScale);

            maxScroll = Math.max(0f, totalRowsHeight() - contentH);
            scroll = AnimationUtility.clamp(scroll, -maxScroll, 0f);
            smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, dt, 14f);
            smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, 0.1f);

            renderScriptedPanel(panelScale, fadeProgress, mx, my, palette);

            hits.clear();
            float rowsReveal = fadeProgress;
            boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
            float fadeBottom = contentY + contentH;
            float baseFadeDepth = Math.min(contentH, 14f * panelScale);
            // The fade is an affordance for content that still exists below the viewport,
            // not a permanent readability penalty on the final setting. As the scroll
            // approaches its lower bound, collapse the ramp with the remaining distance.
            // At the exact bottom start == end, so pushBottomAlphaFade disables itself.
            float remainingScroll = Math.max(0f, maxScroll + smoothedScroll);
            float activeFadeDepth = Math.min(baseFadeDepth, remainingScroll);
            float fadeStart = fadeBottom - activeFadeDepth;

            try (ClickGuiRenderer.VerticalAlphaFadeScope ignoredFade =
                         ClickGuiRenderer.pushBottomAlphaFade(fadeStart, fadeBottom)) {
                float y = contentY + smoothedScroll;
                for (SettingRow row : rows) {
                    float sh = row.height() * rowsReveal;
                    float gap = row.gap() * rowsReveal;
                    if (sh > 0.5f) {
                        hits.add(new SettingHit(row.setting(), contentX, y, contentW, sh, row.scale()));
                        if (y + sh >= contentY - 1.0f * panelScale && y <= contentY + contentH + 1.0f * panelScale) {
                            float slide = (1f - row.anim()) * 6f * panelScale;
                            boolean itemClip = ScissorFunction.pushRaw(contentX, y, contentW, sh);
                            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, row.scale())) {
                                row.setting().render(contentX, y + slide, contentW, mx, my);
                            }
                            ClickGuiRenderer.flushRenderer();
                            if (itemClip) {
                                ScissorFunction.pop();
                            }
                        }
                    }
                    y += sh + gap;
                }
            }

            ClickGuiRenderer.flushRenderer();
            if (clipped) {
                ScissorFunction.pop();
            }

        }
        if (hintsVisible) {
            renderHudEditorHints(menuX, menuY, menuW, menuH, panelScale, fadeProgress);
            renderSettingsPanelHints(menuX, menuY, menuW, menuH, panelScale, fadeProgress);
        }
    }

    public boolean mouseClicked(float mx, float my, int button, float scale) {
        if (!isActive()) return false;
        if (openAnim <= 0.01f) return false;
        float panelScale = panelScale(scale);

        if (button == 0 && inside(mx, my, closeX, closeY, closeW, closeH)) {
            close();
            return true;
        }

        if (!inside(mx, my, panelX, panelY, panelW, panelH)) {
            return hudContext || editorContext;
        }

        if (button == 0 && inside(mx, my, panelX, panelY, panelW, DROPDOWN_HEADER_H * panelScale)) {
            dragging = true;
            dragOffsetX = panelX - mx;
            dragOffsetY = panelY - my;
            return true;
        }

        if (hudContext && button == 0 && inside(mx, my, pillX, pillY, pillW, pillH)) {
            float half = pillW * 0.5f;
            hudPreviewMode = (mx < pillX + half) ? HudPreviewMode.ALL_ENABLED : HudPreviewMode.ONLY_CURRENT;
            applyHudPreviewMode();
            return true;
        }

        if (inside(mx, my, contentX, contentY, contentW, contentH)) {
            SettingHit target = null;
            for (SettingHit hit : hits) {
                if (inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    target = hit;
                    break;
                }
            }
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, target == null ? settingScale(panelScale) : target.scale())) {
                for (Setting setting : settings) {
                    if (target != null && target.setting() == setting) continue;
                    setting.mouseClickedOutside(mx, my, button);
                }
                if (target != null) {
                    if (!target.setting().isAvailable()) {
                        return true;
                    }
                    target.setting().mouseClicked(mx, my, button);
                    return true;
                }
            }
        } else {
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, settingScale(panelScale))) {
                for (Setting setting : settings) setting.mouseClickedOutside(mx, my, button);
            }
        }
        return true;
    }

    public boolean mouseReleased(float mx, float my, int button) {
        if (!isActive()) return false;
        if (button == 0) dragging = false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, lastSettingScale)) {
            for (Setting setting : settings) {
                if (!setting.isAvailable()) continue;
                setting.mouseReleased(mx, my, button);
            }
        }
        return inside(mx, my, panelX, panelY, panelW, panelH);
    }

    public boolean mouseScrolled(float mx, float my, double amount) {
        if (!isActive()) return false;
        if (!limitedHeight) {
            return inside(mx, my, panelX, panelY, panelW, panelH);
        }
        if (!inside(mx, my, panelX, panelY, panelW, panelH)) {
            return hudContext || editorContext;
        }
        for (SettingHit hit : hits) {
            if (!inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (!hit.setting().isAvailable()) return true;
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, hit.scale())) {
                if (hit.setting().mouseScrolled(mx, my, amount)) return true;
            }
            break;
        }
        scroll += (float) (amount * 20f * Math.max(0.25f, lastSettingScale));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isActive()) return false;
        if (isHintToggle(keyCode, modifiers)) {
            MainConfig config = MainConfig.get();
            if (hudContext) {
                config.setClickGuiHudEditorHintsEnabled(!config.isClickGuiHudEditorHintsEnabled());
            } else {
                config.setClickGuiHintsEnabled(!config.isClickGuiHintsEnabled());
            }
            return true;
        }

        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, lastSettingScale)) {
            for (Setting setting : settings) {
                if (!setting.isAvailable()) continue;
                if (setting.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!isActive()) return false;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, lastSettingScale)) {
            for (Setting setting : settings) {
                if (!setting.isAvailable()) continue;
                if (setting.charTyped(chr, modifiers)) return true;
            }
        }
        return false;
    }

    private void updateHudModePillBounds(float scale) {
        pillX = panelX + 7f * scale;
        pillY = panelY + DROPDOWN_HEADER_H * scale + 4f * scale;
        pillW = panelW - 14f * scale;
        pillH = 15f * scale;
    }

    private void renderHudEditorHints(float fallbackX, float fallbackY, float fallbackW, float fallbackH, float scale, float alpha) {
        MainConfig config = MainConfig.get();
        if (!hudContext || !config.isClickGuiHudEditorHintsEnabled()) return;

        float areaX = 0f;
        float areaY = 0f;
        float areaW = Math.max(1f, fallbackX + fallbackW);
        float areaH = Math.max(1f, fallbackY + fallbackH);
        if (mc != null && mc.getWindow() != null) {
            int screenW = mc.getWindow().getWidth();
            int screenH = mc.getWindow().getHeight();
            areaW = Math.max(1f, HudScale.virtualWidth(screenW, screenH));
            areaH = Math.max(1f, HudScale.virtualHeight(screenW, screenH));
        }

        ClickGuiHintOverlay.renderBottomLeft(
                areaX,
                areaY,
                areaW,
                areaH,
                scale,
                alpha,
                ClickGuiI18n.tr("clickgui.hints.hud.alt", "Alt - drag without widget linking"),
                ClickGuiI18n.tr("clickgui.hints.hud.close", "Esc - close editor"),
                ClickGuiI18n.tr("clickgui.hints.hud.hide", "Alt+H - hide hints")
        );
    }


    private void renderSettingsPanelHints(float fallbackX, float fallbackY, float fallbackW, float fallbackH, float scale, float alpha) {
        MainConfig config = MainConfig.get();
        if (hudContext || !isActive() || !config.isClickGuiHintsEnabled()) return;

        float areaX = 0f;
        float areaY = 0f;
        float areaW = Math.max(1f, fallbackX + fallbackW);
        float areaH = Math.max(1f, fallbackY + fallbackH);
        if (mc != null && mc.getWindow() != null) {
            int screenW = mc.getWindow().getWidth();
            int screenH = mc.getWindow().getHeight();
            areaW = Math.max(1f, HudScale.virtualWidth(screenW, screenH));
            areaH = Math.max(1f, HudScale.virtualHeight(screenW, screenH));
        }

        ClickGuiHintOverlay.renderBottomLeft(
                areaX,
                areaY,
                areaW,
                areaH,
                scale,
                alpha,
                ClickGuiI18n.tr("clickgui.hints.settings_panel.change", "LMB - change focused setting"),
                ClickGuiI18n.tr("clickgui.hints.settings_panel.drag", "Drag header - move this panel"),
                ClickGuiI18n.tr("clickgui.hints.settings_panel.scroll", "Wheel - scroll settings"),
                ClickGuiI18n.tr("clickgui.hints.settings_panel.back", "Esc - back to category"),
                ClickGuiI18n.tr("clickgui.hints.settings_panel.hide", "Alt+H - hide hints")
        );
    }

    private void renderScriptedPanel(float scale,
                                     float alpha,
                                     float mouseX,
                                     float mouseY,
                                     SettingsGuiPalette palette) {
        if (panelW <= 0.5f || panelH <= 0.5f || alpha <= 0.001f) return;
        if (panelModuleHandle.isRuntimeBlocked() || mc == null || mc.getResourceManager() == null) return;

        HudScriptLayouts.pollReloadCombo(mc);
        if (panelModuleHandle.consumeChanged()) panelRuntime.reset();
        UiScriptModule module = ensurePanelModule();
        if (module == null) return;

        float segmentW = hudContext ? pillW * 0.5f : 0f;
        boolean enabledHover = hudContext && inside(mouseX, mouseY, pillX, pillY, segmentW, pillH);
        boolean onlyHover = hudContext && inside(mouseX, mouseY, pillX + segmentW, pillY, segmentW, pillH);
        Map<String, Object> props = panelTemplateProps(scale, alpha, enabledHover, onlyHover, palette);
        long treeSignature = CachedUiScriptRuntime.signature(props);
        long layoutSignature = 0xcbf29ce484222325L;
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, panelX);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, panelY);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, panelW);
        layoutSignature = CachedUiScriptRuntime.mix(layoutSignature, panelH);

        UiRuntime runtime = panelRuntime.updatePersistent(
                panelModuleHandle,
                module,
                "settings-panel",
                treeSignature,
                layoutSignature,
                panelW,
                panelH,
                ClickGuiRenderer.getInterRegular(),
                panelX,
                panelY,
                panelW,
                panelH,
                () -> props,
                () -> Collections.<String, Map<String, ?>>emptyMap()
        );
        if (runtime == null) return;

        ClickGuiRenderer.flushRenderer();

        // Inventory/Potions-style material: soft outer shadow + an explicit square blur
        // pass below the chrome. Radius stays zero: this is not a rounded glass card.
        HudRenderUtil.drawHudShadow(
                Renderer2D.COLOR,
                panelX, panelY, panelW, panelH,
                0.0f, Math.max(0.85f, scale * 0.72f),
                false, 46, alpha
        );
        // Use the normal ClickGUI blur path so this panel gets the same Kawase blur
        // as the rest of ClickGUI instead of the weaker local pass.
        ClickGuiRenderer.drawBlur(
                panelX, panelY, panelW, panelH,
                0.0f,
                0xFFFFFFFF,
                0.92f * alpha
        );
        ClickGuiRenderer.flushRenderer();

        runtime.render(new UiRenderContext(
                Renderer2D.COLOR,
                ClickGuiRenderer.getInterRegular(),
                null,
                0f,
                UiProjectionMode.CURRENT,
                1f
        ));
        ClickGuiRenderer.flushRenderer();
    }

    private UiScriptModule ensurePanelModule() {
        if (!panelModuleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(panelModuleHandle);
            return null;
        }
        panelModuleHandle.consumeChanged();
        return panelModuleHandle.module();
    }

    private Map<String, Object> panelTemplateProps(float scale,
                                                   float alpha,
                                                   boolean enabledHover,
                                                   boolean onlyHover,
                                                   SettingsGuiPalette palette) {
        Themes.Theme theme = Theme.theme();
        Themes.ThemeEntry entry = Theme.currentEntry();
        Themes.GradientSpec strokeGradient = entry == null ? null : entry.strokeGradient();
        boolean strokeGradientEnabled = strokeGradient != null && strokeGradient.enabled();

        // Same neutral chrome recipe as Inventory/Potions: window/header/surface/deep.
        // Do not spread the accent/card gradient across the whole panel material.
        int chromeAlpha = 184;
        int window = SettingsGuiPalette.withAlpha(theme.windowBg(), chromeAlpha);
        int header = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.windowHeader(), theme.surface(), 0.18f),
                Math.min(255, chromeAlpha + 14)
        );
        int surface = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surface(), theme.windowBg(), 0.22f),
                Math.min(255, chromeAlpha + 6)
        );
        int deep = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surface(), theme.windowHeader(), 0.42f),
                Math.min(255, chromeAlpha + 18)
        );

        int headerStart = SettingsGuiPalette.mix(header, window, 0.22f);
        int headerEnd = SettingsGuiPalette.mix(surface, header, 0.52f);
        int bodyStart = SettingsGuiPalette.mix(window, surface, 0.24f);
        int bodyEnd = SettingsGuiPalette.mix(deep, surface, 0.18f);
        float chromeAngle = 90.0f;

        int headerGlintStart = SettingsGuiPalette.withAlpha(theme.textPrimary(), 14);
        int headerGlintEnd = SettingsGuiPalette.withAlpha(theme.textPrimary(), 0);
        int bodyGlintStart = SettingsGuiPalette.withAlpha(theme.textPrimary(), 8);
        int bodyGlintEnd = SettingsGuiPalette.withAlpha(theme.textPrimary(), 0);

        int neutralStroke = SettingsGuiPalette.mix(theme.windowStroke(), theme.strokeSoft(), 0.18f);
        int rawStrokeStart = strokeGradientEnabled ? strokeGradient.start() : neutralStroke;
        int rawStrokeEnd = strokeGradientEnabled ? strokeGradient.end() : neutralStroke;
        float strokeAngle = strokeGradientEnabled ? strokeGradient.angleDeg() : chromeAngle;

        // Stroke can inherit the theme gradient, but most of its chroma is mixed back
        // into neutral chrome so the frame does not become a saturated accent cage.
        int strokeStart = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(rawStrokeStart, neutralStroke, 0.78f), 70);
        int strokeEnd = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(rawStrokeEnd, neutralStroke, 0.80f), 64);
        int dividerStart = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(rawStrokeStart, neutralStroke, 0.84f), 52);
        int dividerEnd = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(rawStrokeEnd, neutralStroke, 0.86f), 46);

        int selectorSurfaceStart = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surface(), theme.windowBg(), 0.30f), 118);
        int selectorSurfaceEnd = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surfaceHover(), theme.windowHeader(), 0.34f), 132);
        int selectorActiveStart = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surfaceHover(), theme.accent(), 0.10f), 166);
        int selectorActiveEnd = SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surface(), theme.accentSoft(), 0.12f), 154);

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("width", panelW);
        props.put("height", panelH);
        props.put("scale", scale);
        props.put("open", alpha);
        props.put("title", title);
        props.put("headerH", DROPDOWN_HEADER_H * scale);
        props.put("hudContext", hudContext);
        props.put("closeHover", closeHoverAnim);
        props.put("closeTint", hex(SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.textMuted(), theme.textPrimary(), closeHoverAnim * 0.62f), 222)));
        props.put("closeHoverBg", hex(SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.surfaceHover(), theme.windowHeader(), 0.18f), 34)));
        props.put("hudMode", hudPreviewMode == HudPreviewMode.ONLY_CURRENT ? "only" : "enabled");
        props.put("pillProgress", AnimationUtility.easeInOutCubic(pillActiveAnim));
        props.put("enabledHover", enabledHover);
        props.put("onlyHover", onlyHover);

        // Keep the scripted blur node too; the explicit Java pass above makes the
        // square framebuffer blur reliable even through persistent UI batching.
        props.put("blurAlpha", 0.22f * alpha);
        props.put("blurBrightness", 0.96f);
        props.put("blurQuality", ClickGuiRenderer.clickGuiBlurQuality());

        props.put("bodyA", hex(bodyStart));
        props.put("bodyB", hex(bodyEnd));
        props.put("bodyAngle", chromeAngle);
        props.put("bodyGlintA", hex(bodyGlintStart));
        props.put("bodyGlintB", hex(bodyGlintEnd));

        props.put("headerA", hex(headerStart));
        props.put("headerB", hex(headerEnd));
        props.put("headerAngle", chromeAngle);
        props.put("headerGlintA", hex(headerGlintStart));
        props.put("headerGlintB", hex(headerGlintEnd));

        props.put("strokeA", hex(strokeStart));
        props.put("strokeB", hex(strokeEnd));
        props.put("strokeAngle", strokeAngle);
        props.put("dividerA", hex(dividerStart));
        props.put("dividerB", hex(dividerEnd));

        props.put("text", hex(SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.textPrimary(), theme.accent(), 0.04f), 240)));
        props.put("muted", hex(SettingsGuiPalette.withAlpha(
                SettingsGuiPalette.mix(theme.textMuted(), theme.textPrimary(), 0.16f), 184)));

        props.put("surfaceA", hex(selectorSurfaceStart));
        props.put("surfaceB", hex(selectorSurfaceEnd));
        props.put("surfaceAngle", chromeAngle);
        props.put("activeA", hex(selectorActiveStart));
        props.put("activeB", hex(selectorActiveEnd));
        props.put("activeAngle", chromeAngle);

        boolean scrollbarVisible = limitedHeight && maxScroll > 0f;
        float trackW = 1.5f * scale;
        float trackX = panelW - 4.5f * scale;
        float trackY = contentY - panelY + 5.0f * scale;
        float viewableH = Math.max(1f, contentH);
        float trackH = Math.max(8f * scale, viewableH - 10f * scale);
        float rowsH = Math.max(1f, totalRowsHeight());
        float handleH = Math.min(trackH, Math.max(18f * scale, (viewableH / rowsH) * trackH));
        float ratio = maxScroll <= 0f ? 0f : (-smoothedScroll / maxScroll);
        float handleY = trackY + (trackH - handleH) * ratio;
        props.put("scrollbarVisible", scrollbarVisible);
        props.put("scrollbarX", trackX);
        props.put("scrollbarY", trackY);
        props.put("scrollbarW", trackW);
        props.put("scrollbarH", trackH);
        props.put("scrollbarThumbY", handleY);
        props.put("scrollbarThumbH", handleH);
        props.put("scrollbarTrack", hex(SettingsGuiPalette.withAlpha(palette.panelScrollTrackA(), 54)));
        props.put("scrollbarThumbA", hex(SettingsGuiPalette.withAlpha(strokeStart, 112)));
        props.put("scrollbarThumbB", hex(SettingsGuiPalette.withAlpha(strokeEnd, 104)));
        return props;
    }

    private static String hex(int argb) {
        return "#" + String.format("%08X", argb);
    }

    private void buildRows(float menuScale) {
        rows.clear();
        for (Setting setting : settings) {
            float rowScale = settingScale(setting, menuScale);
            try (SettingRenderContext.Scope ignored = SettingRenderContext.push(renderSurface, rowScale)) {
                float anim = setting.updateVisibilityAnim();
                boolean targetVisible = setting.isVisibilityTargetVisible();
                if (!targetVisible && anim <= 0.01f) continue;
                float h = setting.getHeight() * anim;
                float gap = 0f;
                rows.add(new SettingRow(setting, anim, h, gap, rowScale));
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

    private void applyHudPreviewMode() {
        if (!hudContext) return;
        DraggableHudElement target = DraggableHudElementRegistry.getById(targetId);
        if (hudPreviewMode == HudPreviewMode.ONLY_CURRENT) {
            if (target != null) {
                target.setPreviewEnabled(true);
            }
            DraggableHudElementRegistry.setEditorWidgetId(targetId);
            DraggableHudElementRegistry.setForceVisible(true);
        } else {
            if (target != null) {
                target.setPreviewEnabled(false);
            }
            DraggableHudElementRegistry.setEditorWidgetId(null);
            DraggableHudElementRegistry.setForceVisible(true);
        }
    }

    private void resetHudPreview() {
        DraggableHudElement target = DraggableHudElementRegistry.getById(targetId);
        if (target != null) {
            target.setPreviewEnabled(false);
        }
        DraggableHudElementRegistry.setEditorWidgetId(null);
        DraggableHudElementRegistry.setForceVisible(false);
    }

    private float panelScale(float menuScale) {
        float base = Math.max(0.25f, menuScale);
        if (renderSurface != SettingRenderSurface.MODULES) return base;
        return AnimationUtility.clamp(base * 1.5f, 2.55f, 3.0f);
    }

    private float settingScale(float menuScale) {
        return Math.max(0.25f, menuScale);
    }

    private float settingScale(Setting setting, float menuScale) {
        return settingScale(menuScale);
    }

    private RenderWarpStack.Scope pushLifecycleWarp(float progress) {
        if (progress >= 0.999f || dragging) {
            return Renderer2D.pushWarp(null);
        }
        return Renderer2D.pushPerspectiveWarp(
                panelX,
                panelY,
                panelW,
                panelH,
                0.0f,
                0.0f,
                0.0f,
                3.6f,
                0.80f,
                0.92f + 0.08f * progress
        );
    }

    private enum HudPreviewMode {
        ALL_ENABLED,
        ONLY_CURRENT
    }

    private record SettingRow(Setting setting, float anim, float height, float gap, float scale) {
    }

    private record SettingHit(Setting setting, float x, float y, float w, float h, float scale) {
    }
}
