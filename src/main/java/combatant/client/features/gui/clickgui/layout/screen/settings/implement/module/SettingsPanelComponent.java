/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.module;

import combatant.client.config.MainConfig;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.svg.SvgRenderOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsGlassMaterial;
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
import java.util.List;

public final class SettingsPanelComponent {
    private static final float DROPDOWN_PANEL_W = 115.0f;
    private static final float DROPDOWN_PANEL_H = 240.0f;
    private static final float DROPDOWN_HEADER_H = 24.0f;
    private static final float DROPDOWN_SEPARATOR_H = 4.0f;
    private static final float DROPDOWN_RADIUS = 10.0f;
    private final List<Setting> settings = new ArrayList<>();
    private final List<SettingRow> rows = new ArrayList<>();
    private final List<SettingHit> hits = new ArrayList<>();
    private final SettingRenderSurface renderSurface;
    private final Minecraft mc = Minecraft.getInstance();
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
    private HudPreviewMode hudPreviewMode = HudPreviewMode.ONLY_CURRENT;
    private float lastSettingScale = 1.0f;
    public SettingsPanelComponent() {
        this(SettingRenderSurface.MODULES);
    }
    public SettingsPanelComponent(SettingRenderSurface renderSurface) {
        this.renderSurface = renderSurface == null ? SettingRenderSurface.SETTINGS : renderSurface;
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static boolean isHintToggle(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_H && (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    private static int mixColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) (((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = (int) (((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = (int) (((from) & 0xFF) * (1 - t) + ((to) & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
        float dt = AnimationUtility.deltaTime();
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
            int bgTl = LayoutRender2D.alpha(palette.panelBgLeft(), fadeProgress);
            int bgTr = LayoutRender2D.alpha(palette.panelBgRight(), fadeProgress);
            int bgBr = LayoutRender2D.alpha(SettingsGuiPalette.darken(palette.panelBgRight(), 0.08f), fadeProgress);
            int bgBl = LayoutRender2D.alpha(SettingsGuiPalette.darken(palette.panelBgLeft(), 0.05f), fadeProgress);
            int stroke = LayoutRender2D.alpha(palette.panelStroke(), fadeProgress);
            int line = LayoutRender2D.alpha(palette.panelDivider(), 0.52f * fadeProgress);
            int text = LayoutRender2D.alpha(palette.panelText(), fadeProgress);
            int mutedBase = LayoutRender2D.alpha(palette.panelMuted(), fadeProgress);

            String header = title;
            closeW = 16f * panelScale;
            closeH = 14f * panelScale;
            closeX = panelX + panelW - closeW - 6f * panelScale;
            closeY = panelY + (DROPDOWN_HEADER_H * panelScale - closeH) * 0.5f;
            boolean closeHover = inside(mx, my, closeX, closeY, closeW, closeH);
            closeHoverAnim = AnimationUtility.approach(closeHoverAnim, closeHover ? 1f : 0f, dt, 10f);
            int muted = mixColor(mutedBase, text, closeHoverAnim * 0.7f);

            renderMatteChrome(header, panelScale, bgTl, bgTr, bgBr, bgBl, stroke, line, text, muted, palette);

            float contentStartY = panelY + (DROPDOWN_HEADER_H + DROPDOWN_SEPARATOR_H) * panelScale;
            if (hudContext) {
                renderHudModePill(panelScale, text, muted, palette);
                contentStartY += hudExtraTop;
            }

            contentPadLeft = 6f * panelScale;
            contentPadRight = 13f * panelScale;
            float contentClipInset = 2.2f * panelScale;
            contentX = panelX + contentPadLeft;
            contentY = contentStartY + contentClipInset;
            contentW = Math.max(1f, panelW - contentPadLeft - contentPadRight);
            contentH = Math.max(0f, panelY + panelH - contentY - 7.0f * panelScale);

            maxScroll = Math.max(0f, totalRowsHeight() - contentH);
            scroll = AnimationUtility.clamp(scroll, -maxScroll, 0f);
            smoothedScroll = AnimationUtility.approach(smoothedScroll, scroll, dt, 14f);
            smoothedScroll = AnimationUtility.snap(smoothedScroll, scroll, 0.1f);

            hits.clear();
            float rowsReveal = fadeProgress;
            boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);

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

            ClickGuiRenderer.flushRenderer();
            if (clipped) {
                ScissorFunction.pop();
            }

            renderScrollbar(panelScale, palette);
        }
        renderHudEditorHints(menuX, menuY, menuW, menuH, panelScale, fadeProgress);
        renderSettingsPanelHints(menuX, menuY, menuW, menuH, panelScale, fadeProgress);
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

    private void renderHudModePill(float scale, int text, int muted, SettingsGuiPalette palette) {
        updateHudModePillBounds(scale);

        float dt = AnimationUtility.deltaTime();
        float mx = ClickGuiRenderer.getMouseX();
        float my = ClickGuiRenderer.getMouseY();
        float segmentW = pillW * 0.5f;
        boolean enabledHover = inside(mx, my, pillX, pillY, segmentW, pillH);
        boolean onlyHover = inside(mx, my, pillX + segmentW, pillY, segmentW, pillH);
        int baseA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgLeft(), 0.32f), openAnim);
        int baseB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillBase(), palette.panelBgRight(), 0.40f), openAnim);
        int strokeA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.32f), openAnim);
        int strokeB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.moduleDividerEnd(), palette.menuCategorySelectedRight(), 0.20f), openAnim);

        ClickGuiRenderer.drawBlur(pillX, pillY, pillW, pillH, 5.5f * scale, palette.panelBlurTint(), (105f / 255f) * openAnim);
        LayoutRender2D.roundedQuad(pillX, pillY, pillW, pillH, 5.5f * scale, baseA, baseB, SettingsGuiPalette.darken(baseB, 0.05f), baseA);
        LayoutRender2D.roundedStrokeQuad(pillX, pillY, pillW, pillH, 5.5f * scale, 0.45f * scale, strokeA, strokeB, LayoutRender2D.alpha(strokeB, 0.82f), LayoutRender2D.alpha(strokeA, 0.88f));

        float innerPad = 1.3f * scale;
        float activeW = segmentW - innerPad * 2f;
        float activeH = pillH - innerPad * 2f;
        float pillT = AnimationUtility.easeInOutCubic(pillActiveAnim);
        float activeX = pillX + innerPad + (segmentW * pillT);
        int activeA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedLeft(), 0.18f), openAnim);
        int activeB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), 0.24f), openAnim);
        int activeC = LayoutRender2D.alpha(SettingsGuiPalette.darken(activeB, 0.10f), openAnim);
        LayoutRender2D.roundedSoftShadow(activeX, pillY + innerPad, activeW, activeH, 4.4f * scale, 4.0f * scale, 0.018f, LayoutRender2D.alpha(palette.menuShadow(), openAnim * 0.55f));
        LayoutRender2D.roundedQuad(activeX, pillY + innerPad, activeW, activeH, 4.4f * scale, activeA, activeB, activeC, activeA);
        LayoutRender2D.roundedStrokeQuad(activeX, pillY + innerPad, activeW, activeH, 4.4f * scale, 0.28f * scale, LayoutRender2D.alpha(palette.moduleDividerStart(), openAnim * 0.86f), LayoutRender2D.alpha(palette.moduleDividerEnd(), openAnim * 0.92f), LayoutRender2D.alpha(palette.moduleDividerEnd(), openAnim * 0.72f), LayoutRender2D.alpha(palette.moduleDividerStart(), openAnim * 0.78f));

        float sepX = pillX + segmentW;
        LayoutRender2D.roundedQuad(sepX - 0.42f * scale, pillY + 4.0f * scale, 0.84f * scale, pillH - 8.0f * scale, 0.42f * scale,
                LayoutRender2D.alpha(palette.moduleDividerStart(), 0.35f * openAnim),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.50f * openAnim),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), 0.42f * openAnim),
                LayoutRender2D.alpha(palette.moduleDividerStart(), 0.28f * openAnim));

        float ty = pillY + (pillH - ClickGuiRenderer.textHeight(ClickGuiRenderer.getInterMedium(), 6.2f * scale)) * 0.5f;
        int enabledColor = mixColor(mixColor(text, muted, pillT), text, enabledHover ? 0.10f : 0f);
        int onlyColor = mixColor(mixColor(muted, text, pillT), text, onlyHover ? 0.10f : 0f);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), "Enabled", pillX + 8.0f * scale, ty, 6.2f * scale, enabledColor, false);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), "Only this", pillX + segmentW + 8.0f * scale, ty, 6.2f * scale, onlyColor, false);
    }

    private void updateHudModePillBounds(float scale) {
        pillX = panelX + 7f * scale;
        pillY = panelY + DROPDOWN_HEADER_H * scale + 4f * scale;
        pillW = panelW - 14f * scale;
        pillH = 16f * scale;
    }

    private void renderScrollbar(float scale, SettingsGuiPalette palette) {
        if (!limitedHeight || maxScroll <= 0f) return;

        float trackW = 2.5f * scale;
        float trackX = panelX + panelW - 5f * scale;
        float trackY = contentY + 5f * scale;
        float viewableH = Math.max(1f, contentH);
        float trackH = Math.max(8f * scale, viewableH - 10f * scale);

        float rowsH = Math.max(1f, totalRowsHeight());
        float handleH = Math.min(trackH, Math.max(20f * scale, (viewableH / rowsH) * trackH));
        float ratio = maxScroll <= 0f ? 0f : (-smoothedScroll / maxScroll);
        float handleY = trackY + (trackH - handleH) * ratio;

        int trTl = LayoutRender2D.alpha(palette.panelScrollTrackA(), openAnim);
        int trTr = LayoutRender2D.alpha(palette.panelScrollTrackB(), openAnim);
        int hdTl = LayoutRender2D.alpha(palette.panelScrollHandleA(), openAnim);
        int hdTr = LayoutRender2D.alpha(palette.panelScrollHandleB(), openAnim);

        LayoutRender2D.roundedQuad(trackX, trackY, trackW, trackH, 1.25f * scale, trTl, trTr, trTr, trTl);
        LayoutRender2D.roundedQuad(trackX, handleY, trackW, handleH, 1.25f * scale, hdTl, hdTr, hdTr, hdTl);
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

    private void renderMatteChrome(String header,
                                   float scale,
                                   int bgTl,
                                   int bgTr,
                                   int bgBr,
                                   int bgBl,
                                   int stroke,
                                   int line,
                                   int text,
                                   int muted,
                                   SettingsGuiPalette palette) {
        if (panelW <= 0.5f || panelH <= 0.5f || openAnim <= 0.001f) return;

        float radius = DROPDOWN_RADIUS * scale;
        float headerBottom = panelY + DROPDOWN_HEADER_H * scale;
        int shadow = LayoutRender2D.alpha(palette.panelShadow(), 0.72f * openAnim);
        int topWashA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelBgLeft(), palette.menuCategorySelectedLeft(), 0.16f), openAnim);
        int topWashB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelBgRight(), palette.menuCategorySelectedRight(), 0.18f), openAnim);
        int accent = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillActive(), palette.menuCategorySelectedRight(), 0.18f), 0.55f * openAnim);
        int accentSoft = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelPillActive(), palette.panelBgRight(), 0.42f), 0.22f * openAnim);
        int strokeTop = LayoutRender2D.alpha(SettingsGuiPalette.mix(stroke, palette.moduleDividerEnd(), 0.22f), openAnim);
        int strokeBottom = LayoutRender2D.alpha(SettingsGuiPalette.mix(stroke, palette.panelBgRight(), 0.22f), 0.82f * openAnim);

        LayoutRender2D.roundedSoftShadow(panelX - 1.4f * scale, panelY + 1.2f * scale, panelW + 2.8f * scale, panelH + 2.4f * scale, radius + 1.6f * scale, 9.5f * scale, 0.028f, shadow);
        SettingsGlassMaterial.elevated(panelX, panelY, panelW, panelH, radius, scale, palette, openAnim);

        LayoutRender2D.roundedQuad(panelX + 1.0f * scale, panelY + 1.0f * scale, panelW - 2.0f * scale, DROPDOWN_HEADER_H * scale - 1.0f * scale, Math.max(0f, radius - 1.0f * scale), topWashA, topWashB, LayoutRender2D.alpha(SettingsGuiPalette.darken(topWashB, 0.18f), 0.64f), LayoutRender2D.alpha(SettingsGuiPalette.darken(topWashA, 0.14f), 0.64f));
        LayoutRender2D.rectQuad(panelX + 8.0f * scale, headerBottom, panelW - 16.0f * scale, Math.max(0.45f, 0.55f * scale), LayoutRender2D.alpha(line, 0.55f), line, LayoutRender2D.alpha(line, 0.28f), LayoutRender2D.alpha(line, 0.28f));
        LayoutRender2D.rectQuad(panelX + 8.0f * scale, headerBottom + 0.6f * scale, panelW - 16.0f * scale, Math.max(0.45f, 0.65f * scale), accent, accentSoft, 0x00000000, 0x00000000);

        float markW = 2.4f * scale;
        float markH = 9.2f * scale;
        LayoutRender2D.roundedQuad(panelX + 8.0f * scale, panelY + 7.2f * scale, markW, markH, markW * 0.5f, accent, accentSoft, accentSoft, accent);
        ClickGuiRenderer.drawText(ClickGuiRenderer.getInterMedium(), header, panelX + 14.0f * scale, panelY + 7.0f * scale, 8.0f * scale, text, false);

        renderCloseButton(scale, text, muted, palette);
    }

    private void renderCloseButton(float scale, int text, int muted, SettingsGuiPalette palette) {
        int bgA = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverLeft(), 0.16f + closeHoverAnim * 0.26f);
        int bgB = SettingsGuiPalette.mix(palette.panelPillBase(), palette.menuCategoryHoverRight(), 0.18f + closeHoverAnim * 0.28f);
        int bgC = SettingsGuiPalette.mix(SettingsGuiPalette.darken(palette.panelBgRight(), 0.06f), palette.menuCategoryHoverRight(), 0.12f + closeHoverAnim * 0.20f);
        int strokeA = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.panelStroke(), palette.moduleDividerStart(), 0.26f), openAnim * (0.72f + closeHoverAnim * 0.22f));
        int strokeB = LayoutRender2D.alpha(SettingsGuiPalette.mix(palette.moduleDividerEnd(), palette.menuCategorySelectedRight(), 0.22f), openAnim * (0.78f + closeHoverAnim * 0.20f));
        float radius = closeH * 0.50f;

        ClickGuiRenderer.drawBlur(closeX, closeY, closeW, closeH, radius, palette.panelBlurTint(), (72f / 255f) * openAnim);
        LayoutRender2D.roundedSoftShadow(closeX, closeY + 0.4f * scale, closeW, closeH, radius, 4.0f * scale, 0.018f + closeHoverAnim * 0.018f, LayoutRender2D.alpha(palette.menuShadow(), openAnim * (0.38f + closeHoverAnim * 0.36f)));
        LayoutRender2D.roundedQuad(closeX, closeY, closeW, closeH, radius, LayoutRender2D.alpha(bgA, openAnim), LayoutRender2D.alpha(bgB, openAnim), LayoutRender2D.alpha(bgC, openAnim), LayoutRender2D.alpha(bgA, openAnim));
        LayoutRender2D.roundedStrokeQuad(closeX, closeY, closeW, closeH, radius, 0.42f * scale, strokeA, strokeB, LayoutRender2D.alpha(strokeB, 0.74f), LayoutRender2D.alpha(strokeA, 0.78f));

        float icon = 6.8f * scale;
        int iconColor = LayoutRender2D.alpha(SettingsGuiPalette.mix(muted, text, 0.42f + closeHoverAnim * 0.48f), openAnim);
        Renderer2D.COLOR.svg("x", closeX + (closeW - icon) * 0.5f, closeY + (closeH - icon) * 0.5f, icon, icon, SvgRenderOptions.overrideColor(iconColor));
        if (closeHoverAnim > 0.08f) SystemCursor.set(SystemCursor.CursorType.HAND);
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
