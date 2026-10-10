/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.config.MainConfig;
import combatant.client.render.engine.renderer.RenderWarpStack;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiSearch;
import combatant.client.features.gui.clickgui.material.PrismaticGlassTransition;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.module.ModuleComponent;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingErrorView;
import combatant.client.runtime.error.ErrorHandler;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleSubcategoryDefinition;
import combatant.client.features.theme.Themes;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.util.ClickGuiHintOverlay;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiBoxShape;
import combatant.client.render.engine.renderer.ui.draw.UiCompoundSdf;
import combatant.client.render.engine.renderer.ui.draw.UiBlurQuality;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiStroke;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;

import java.util.ArrayList;
import java.util.List;

public final class ModulesMenuScreen {
    private static final float INPUT_READY_PROGRESS = 0.72f;
    private static final float PANEL_W = 115.0f;
    private static final float BASE_PANEL_H = 240.0f;
    private static final float PANEL_H = 255.0f;
    private static final float PANEL_GAP = 14.0f;
    private static final float BASE_HEADER_H = 24.0f;
    private static final float MODULE_HEADER_H = 39.0f;
    private static final float SUBCATEGORY_Y = 24.6f;
    private static final float SUBCATEGORY_H = 9.4f;
    private static final float SUBCATEGORY_SIDE_PAD = 5.5f;
    private static final float SUBCATEGORY_GAP = 2.0f;
    private static final float SUBCATEGORY_TEXT = 5.2f;
    private static final float SEPARATOR_H = 4.0f;
    private static final float MODULE_ROW_H = 20.0f;
    private static final float PANEL_RADIUS = 10.0f;
    private static final float TEXT_LEFT_PADDING = 10.0f;
    private static final float HOVER_DESCRIPTION_MAX_W = 290.0f;
    private static final float HOVER_DESCRIPTION_FONT = 11.0f;
    private static final float HOVER_DESCRIPTION_MARQUEE_SPEED = 24.0f;
    private static final float HOVER_DESCRIPTION_MARQUEE_GAP = 34.0f;
    private static final float HOVER_DESCRIPTION_MARQUEE_PAUSE_SEC = 0.85f;
    private static final float HOVER_DESCRIPTION_FADE_W = 13.0f;

    private static final float LIQUID_GLASS_LOGICAL_SCALE = 3.0f;
    private static float ACTIVE_PORT_SCALE = LIQUID_GLASS_LOGICAL_SCALE;

    private final List<ModulesMenuPanel> panels = new ArrayList<>();

    private float areaX;
    private float areaY;
    private float areaW;
    private float areaH;
    private float scale = 1.0f;
    private float screenAnim = 0.0f;
    private float prismProgress = 1.0f;
    private boolean openTarget = false;

    private float searchAnim = 0.0f;
    private boolean lastSearchMode;
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;

    private TextRenderer regular;
    private TextRenderer medium;
    private TextRenderer semibold;
    private TextRenderer comfortaa;
    private long fontGeneration = Long.MIN_VALUE;

    private String frameHoverDescriptionId;
    private String frameHoverDescription;
    private ModulesMenuCategory frameHoverDescriptionCategory;
    private String activeHoverDescriptionId;
    private String activeHoverDescription;
    private ModulesMenuCategory activeHoverDescriptionCategory;
    private float hoverDescriptionAnim;
    private long hoverDescriptionMarqueeStartNanos;

    public ModulesMenuScreen() {
        ModulesMenuCategory[] values = ModulesMenuCategory.values();
        for (int i = 0; i < values.length; i++) {
            panels.add(new ModulesMenuPanel(values[i], i));
        }
    }

    private static float easeOutCubic(float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    static float computePortScale() {
        return ACTIVE_PORT_SCALE;
    }

    private static float computePortScale(float areaW, float areaH) {
        float designW = PANEL_W * ModulesMenuCategory.values().length + PANEL_GAP * (ModulesMenuCategory.values().length - 1);
        float sideMargin = Math.max(28.0f, areaW * 0.03f);
        float verticalReserve = Math.max(56.0f, areaH * 0.10f);

        float fitX = Math.max(1.0f, (areaW - sideMargin * 2.0f) / designW);
        float fitY = Math.max(1.0f, (areaH - verticalReserve) / (PANEL_H + 30.0f));
        float fit = Math.min(fitX, fitY);

        return AnimationUtility.clamp(Math.min(LIQUID_GLASS_LOGICAL_SCALE, fit), 2.55f, LIQUID_GLASS_LOGICAL_SCALE);
    }

    static boolean isModuleListEditHeld() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getWindow() == null || ClickGuiSearch.isActive()) {
            return false;
        }
        return GLFW.glfwGetKey(client.getWindow().handle(), GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS;
    }

    private static float middle(float child, float parent) {
        return (parent - child) * 0.5f;
    }

    private static float textHeight(TextRenderer renderer, float size) {
        return ClickGuiRenderer.textHeight(renderer, size);
    }

    static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static int withAlpha(int color, float alpha) {
        float a = AnimationUtility.clamp(alpha, 0.0f, 1.0f);
        int ca = (color >>> 24) & 0xFF;
        int na = Math.round(ca * a);
        return (color & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int mix(int from, int to, float t) {
        t = AnimationUtility.clamp(t, 0.0f, 1.0f);

        int a = Math.round(((from >>> 24) & 0xFF) * (1.0f - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1.0f - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1.0f - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1.0f - t) + (to & 0xFF) * t);

        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public void open() {
        if (!openTarget) prismProgress = 0.0f;
        openTarget = true;
    }

    public void close() {
        openTarget = false;
        ClickGuiSearch.deactivate();
        if (!ClickGuiRenderer.isClosingForExit()) {
            screenAnim = 0.0f;
            resetTransientState();
        }
    }

    public boolean isVisible() {
        return openTarget || screenAnim > 0.001f;
    }

    public void layout(float x, float y, float w, float h) {
        this.areaX = x;
        this.areaY = y;
        this.areaW = Math.max(1.0f, w);
        this.areaH = Math.max(1.0f, h);
        this.scale = computePortScale(areaW, areaH);
        ACTIVE_PORT_SCALE = this.scale;

        float pw = PANEL_W * scale;
        float ph = PANEL_H * scale;
        float gap = PANEL_GAP * scale;
        float total = (pw + gap) * panels.size() - gap;

        float outerPadX = 18.0f * scale;
        float outerPadTop = 6.0f * scale;
        float outerPadBottom = 26.0f * scale;

        float layoutW = Math.max(total, areaW - outerPadX * 2.0f);
        float startX = areaX + (layoutW - total) * 0.5f + (areaW - layoutW) * 0.5f;
        startX = Math.max(areaX + outerPadX, startX);

        float basePh = BASE_PANEL_H * scale;
        float py = areaY + outerPadTop + Math.max(0.0f, (areaH - outerPadTop - outerPadBottom - basePh) * 0.5f);
        float maxPanelY = areaY + areaH - outerPadBottom - ph;
        if (py > maxPanelY) py = Math.max(areaY + outerPadTop, maxPanelY);
        for (int i = 0; i < panels.size(); i++) {
            ModulesMenuPanel panel = panels.get(i);
            float targetX = startX + i * (pw + gap);
            float panelT = panelProgress(i);
            float eased = easeOutCubic(panelT);
            float drop = -(38.0f + i * 3.5f) * scale * (1.0f - eased);
            float settle = (float) Math.sin(panelT * Math.PI) * 4.5f * scale;

            panel.x = targetX;
            panel.y = py + drop + settle;
            panel.w = pw;
            panel.h = ph;
            panel.anim = panelT;
        }

        searchW = 100.0f * scale;
        searchH = 20.0f * scale;
        searchX = areaX + areaW * 0.5f - searchW * 0.5f;

        float panelBottom = py + ph;
        float visibleSearchY = panelBottom + 6.0f * scale;
        float maxVisibleSearchY = areaY + areaH - searchH - 8.0f * scale;
        if (visibleSearchY > maxVisibleSearchY) {
            visibleSearchY = maxVisibleSearchY;
        }

        float hiddenSearchY = visibleSearchY + 12.0f * scale;
        searchY = AnimationUtility.lerp(hiddenSearchY, visibleSearchY, searchAnim);
    }

    public void render(float mouseX, float mouseY) {
        ensureFonts();
        ModulesMenuStyle.syncTheme();

        float dt = AnimationUtility.deltaTime();
        float target = openTarget ? 1.0f : 0.0f;

        if (openTarget && prismProgress < 1.0f) {
            prismProgress = Math.min(1.0f, prismProgress + dt / 0.85f);
        }

        screenAnim = AnimationUtility.approach(screenAnim, target, dt, openTarget ? 4.8f : 7.5f);
        screenAnim = AnimationUtility.snap(screenAnim, target, 0.01f);

        if (screenAnim <= 0.001f && !openTarget) {
            resetTransientState();
            return;
        }

        boolean searchMode = ClickGuiSearch.isActive() || ClickGuiSearch.hasQuery();
        if (searchMode != lastSearchMode) {
            for (ModulesMenuPanel panel : panels) {
                panel.modulesScroll = 0.0f;
                panel.modulesSmoothScroll = 0.0f;
                panel.maxModulesScroll = 0.0f;
                panel.subcategoryContentAnim = 0.08f;
            }
            lastSearchMode = searchMode;
        }
        searchAnim = AnimationUtility.approach(searchAnim, searchMode ? 1.0f : 0.0f, dt, 10.0f);
        searchAnim = AnimationUtility.snap(searchAnim, searchMode ? 1.0f : 0.0f, 0.01f);

        layout(areaX, areaY, areaW, areaH);

        frameHoverDescriptionId = null;
        frameHoverDescription = null;
        frameHoverDescriptionCategory = null;

        for (ModulesMenuPanel panel : panels) {
            if (panel.isDraggingScrollbar()) panel.updateScrollbarDrag(mouseX, mouseY);
            float alpha = panelAlpha(panel);
            if (alpha <= 0.001f) continue;
            try (RenderWarpStack.Scope ignored = pushPanelDropWarp(panel)) {
                renderPanelGlass(panel, alpha);
            }
        }

        for (ModulesMenuPanel panel : panels) {
            float alpha = panelAlpha(panel);
            if (alpha <= 0.001f) continue;
            try (RenderWarpStack.Scope ignored = pushPanelDropWarp(panel)) {
                renderPanel(panel, mouseX, mouseY, alpha);
            }
        }

        updateHoverDescription(dt);
        renderHoverDescription();
        renderSearch(mouseX, mouseY);
        renderHints();

        ClickGuiRenderer.flushRenderer();
    }

    public void renderGlassPass(float alphaFactor) {
        if (screenAnim <= 0.001f) return;

        float passAlpha = AnimationUtility.clamp(alphaFactor, 0.0f, 1.0f);
        for (ModulesMenuPanel panel : panels) {
            float alpha = panelAlpha(panel) * passAlpha;
            if (alpha <= 0.001f) continue;
            try (RenderWarpStack.Scope ignored = pushPanelDropWarp(panel)) {
                renderPanelGlass(panel, alpha);
            }
        }
    }

    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (!isInteractive()) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
            ClickGuiSearch.setActive(true);
            ClickGuiSearch.editor().layout(searchX + 8f * scale, searchW - 16f * scale, regular, 8f * scale);
            ClickGuiSearch.editor().beginDrag(mouseX, false);
            return true;
        }

        for (ModulesMenuPanel panel : panels) {
            if (!inside(mouseX, mouseY, panel.x, panel.y, panel.w, panel.h)) continue;
            return panel.mousePressed(mouseX, mouseY, button);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && ClickGuiSearch.isActive()) {
            ClickGuiSearch.unfocus();
            return true;
        }

        return false;
    }

    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (!isInteractive()) return;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) combatant.client.util.text.SingleLineTextInput.mouseReleased();
        for (ModulesMenuPanel panel : panels) {
            panel.mouseReleased(mouseX, mouseY, button);
        }
    }

    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!isInteractive()) return false;
        for (ModulesMenuPanel panel : panels) {
            if (!inside(mouseX, mouseY, panel.x, panel.y, panel.w, panel.h)) continue;
            panel.scroll(mouseX, mouseY, amount);
            return true;
        }

        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isInteractive()) return false;
        if (isHintToggle(keyCode, modifiers)) {
            MainConfig config = MainConfig.get();
            config.setClickGuiHintsEnabled(!config.isClickGuiHintsEnabled());
            return true;
        }

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            ClickGuiSearch.setActive(!ClickGuiSearch.isActive());
            return true;
        }

        if (ClickGuiSearch.isActive()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ClickGuiSearch.setActive(false);
                return true;
            }

            if (ClickGuiSearch.keyPressed(keyCode, modifiers)) return true;
            return true;
        }

        for (ModulesMenuPanel panel : panels) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        return false;
    }

    private static boolean isHintToggle(int keyCode, int modifiers) {
        return keyCode == GLFW.GLFW_KEY_H && (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!isInteractive()) return false;
        if (ClickGuiSearch.isActive()) {
            ClickGuiSearch.append(chr);
            return true;
        }

        for (ModulesMenuPanel panel : panels) {
            if (panel.charTyped(chr, modifiers)) return true;
        }

        return false;
    }

    private float panelProgress(int index) {
        if (panels.isEmpty()) return screenAnim;
        float stagger = 0.055f;
        float span = Math.max(0.20f, 1.0f - stagger * Math.max(0, panels.size() - 1));
        return AnimationUtility.clamp((screenAnim - index * stagger) / span, 0.0f, 1.0f);
    }

    private float panelAlpha(ModulesMenuPanel panel) {
        return easeOutCubic(panel.anim);
    }

    private boolean isInteractive() {
        return openTarget && screenAnim >= INPUT_READY_PROGRESS;
    }

    private RenderWarpStack.Scope pushPanelDropWarp(ModulesMenuPanel panel) {
        float t = easeOutCubic(panel.anim);
        if (t >= 0.999f) return Renderer2D.pushWarp(null);
        float inv = 1.0f - t;
        return Renderer2D.pushPerspectiveWarp(
                panel.x,
                panel.y,
                panel.w,
                panel.h,
                0.0f,
                -9.0f * inv,
                0.0f,
                3.4f,
                0.82f,
                0.94f + 0.06f * t
        );
    }

    private void resetTransientState() {
        frameHoverDescriptionId = null;
        frameHoverDescription = null;
        frameHoverDescriptionCategory = null;
        activeHoverDescriptionId = null;
        activeHoverDescription = null;
        hoverDescriptionAnim = 0.0f;
        lastSearchMode = false;
        for (ModulesMenuPanel panel : panels) {
            panel.selected = null;
            panel.selectedTitle = null;
            panel.selectedSettings.clear();
            panel.settingsScroll = 0.0f;
            panel.settingsSmoothScroll = 0.0f;
            panel.modulesScroll = 0.0f;
            panel.modulesSmoothScroll = 0.0f;
            panel.swap = 0.0f;
            panel.subcategoryIndicatorReady = false;
            panel.subcategoryContentAnim = 1.0f;
            panel.subcategoryDirection = 1;
            panel.hits.clear();
            panel.settingHits.clear();
            panel.subcategoryHits.clear();
        }
    }

    private void renderPanelGlass(ModulesMenuPanel panel, float alpha) {
        if (alpha <= 0.001f) return;

        LayoutRender2D.roundedSoftShadow(
                panel.x,
                panel.y,
                panel.w,
                panel.h,
                PANEL_RADIUS * scale,
                18.0f * scale,
                0.018f,
                withAlpha(ModulesMenuStyle.shadow(), alpha)
        );

        float materialAlpha = alpha >= 0.999f ? 1.0f : alpha;
        drawLiquidGlass(panel.x, panel.y, panel.w, panel.h, PANEL_RADIUS * scale, true, materialAlpha);
    }

    private void renderPanel(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        panel.update();

        int text = withAlpha(ModulesMenuStyle.text(), alpha);
        int muted = withAlpha(ModulesMenuStyle.textMuted(), alpha);
        int split = withAlpha(ModulesMenuStyle.split(), alpha);
        int veil = withAlpha(ModulesMenuStyle.panelBgGlassDark(), alpha * 0.30f);
        LayoutRender2D.roundedQuad(panel.x, panel.y, panel.w, panel.h, PANEL_RADIUS * scale, veil, veil, veil, veil);

        panel.hits.clear();
        panel.settingHits.clear();
        panel.subcategoryHits.clear();

        // Keep the original category-title row intact. Subcategories live in an added row below it.
        float titleY = panel.y + middle(textHeight(semibold, 9.0f * scale), BASE_HEADER_H * scale) + 0.5f * scale;
        ClickGuiRenderer.drawText(semibold, panel.category.title(), panel.x + TEXT_LEFT_PADDING * scale, titleY, 9.0f * scale, text, false);
        drawCategoryIcon(panel, muted);

        if (panel.swap < 0.999f) {
            float modulePageAlpha = alpha * (1.0f - panel.swap);
            float subcategoryVisibility = 1.0f - AnimationUtility.easeOutCubic(searchAnim);
            renderSubcategoryBar(panel, mouseX, mouseY, modulePageAlpha * subcategoryVisibility);
            renderModulePage(panel, mouseX, mouseY, modulePageAlpha);
        }

        if (panel.swap > 0.001f) {
            renderSettingsPage(panel, mouseX, mouseY, alpha * panel.swap);
        }

        float searchCollapse = AnimationUtility.easeOutCubic(searchAnim);
        float moduleHeaderH = AnimationUtility.lerp(MODULE_HEADER_H, BASE_HEADER_H, searchCollapse);
        float headerH = moduleHeaderH + (BASE_HEADER_H - moduleHeaderH) * panel.swap;
        LayoutRender2D.rect(
                panel.x + 8.0f * scale,
                panel.y + headerH * scale,
                panel.w - 16.0f * scale,
                0.5f * scale,
                split
        );
    }

    private void renderSubcategoryBar(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.001f) {
            panel.subcategoryViewportX = panel.subcategoryViewportY = 0.0f;
            panel.subcategoryViewportW = panel.subcategoryViewportH = 0.0f;
            return;
        }
        List<ModuleSubcategoryDefinition> subcategories = panel.category.subcategories();
        if (subcategories.isEmpty()) return;

        float rowX = panel.x + SUBCATEGORY_SIDE_PAD * scale;
        float rowY = panel.y + SUBCATEGORY_Y * scale;
        float rowW = panel.w - SUBCATEGORY_SIDE_PAD * 2.0f * scale;
        float rowH = SUBCATEGORY_H * scale;
        float gap = SUBCATEGORY_GAP * scale;
        float fontSize = SUBCATEGORY_TEXT * scale;
        float radius = rowH * 0.5f;

        float[] itemWidths = new float[subcategories.size()];
        float contentW = gap * Math.max(0, subcategories.size() - 1);
        float minItemW = 26.0f * scale;
        float horizontalTextPad = 8.0f * scale;
        for (int i = 0; i < subcategories.size(); i++) {
            float preferred = ClickGuiRenderer.textWidth(medium, subcategories.get(i).displayName(), fontSize)
                    + horizontalTextPad * 2.0f;
            itemWidths[i] = Math.max(minItemW, preferred);
            contentW += itemWidths[i];
        }

        // Fill the row while it fits. Addon-defined groups switch to horizontal scrolling instead
        // of shrinking labels below their readable width.
        if (contentW < rowW) {
            float extra = (rowW - contentW) / subcategories.size();
            for (int i = 0; i < itemWidths.length; i++) itemWidths[i] += extra;
            contentW = rowW;
        }

        panel.maxSubcategoryScroll = Math.max(0.0f, contentW - rowW);
        panel.subcategoryScroll = AnimationUtility.clamp(
                panel.subcategoryScroll, 0.0f, panel.maxSubcategoryScroll);
        panel.subcategorySmoothScroll = AnimationUtility.clamp(
                panel.subcategorySmoothScroll, 0.0f, panel.maxSubcategoryScroll);
        boolean searchMode = ClickGuiSearch.isActive() || ClickGuiSearch.hasQuery();
        if (searchMode) {
            panel.subcategoryViewportX = panel.subcategoryViewportY = 0.0f;
            panel.subcategoryViewportW = panel.subcategoryViewportH = 0.0f;
        } else {
            panel.subcategoryViewportX = rowX;
            panel.subcategoryViewportY = rowY - 1.6f * scale;
            panel.subcategoryViewportW = rowW;
            panel.subcategoryViewportH = rowH + 3.2f * scale;
        }

        ModuleSubcategoryDefinition active = panel.activeSubcategory();
        float selectedTargetX = rowX;
        float selectedTargetW = itemWidths[0];
        float[] contentXs = new float[subcategories.size()];
        float[] hoverValues = new float[subcategories.size()];
        float cursor = rowX;
        for (int i = 0; i < subcategories.size(); i++) {
            contentXs[i] = cursor;
            ModuleSubcategoryDefinition subcategory = subcategories.get(i);
            if (active != null && active.key().equals(subcategory.key())) {
                selectedTargetX = cursor;
                selectedTargetW = itemWidths[i];
            }
            cursor += itemWidths[i] + gap;
        }
        panel.animateSubcategoryIndicator(selectedTargetX, selectedTargetW);

        Renderer2D renderer = Renderer2D.COLOR;
        Themes.GradientSpec buttonGradient = Themes.hudSelectionGradient();
        float gradientAngle = buttonGradient.angleDeg();
        float scroll = panel.subcategorySmoothScroll;
        boolean viewportHover = inside(mouseX, mouseY, rowX, rowY, rowW, rowH);

        // Edge affordances are intentionally derived from the smooth position, not the target
        // scroll. That keeps the chevrons visually coupled to the strip while wheel inertia is
        // still settling. Near either end the corresponding hint fades away instead of popping.
        float hintFadeDistance = 7.5f * scale;
        float leftHintTarget = panel.maxSubcategoryScroll > 0.5f
                ? AnimationUtility.clamp01(scroll / Math.max(0.001f, hintFadeDistance))
                : 0.0f;
        float rightHintTarget = panel.maxSubcategoryScroll > 0.5f
                ? AnimationUtility.clamp01((panel.maxSubcategoryScroll - scroll) / Math.max(0.001f, hintFadeDistance))
                : 0.0f;
        panel.animateSubcategoryScrollHints(leftHintTarget, rightHintTarget);

        // The optical/stroke footprint is wider than the logical hit area. Keep the scroll viewport
        // strict for input, but give the renderer enough guard band so the first/last pill is not
        // visibly cut by scissor.
        float clipPadX = 2.4f * scale;
        float clipPadY = 2.2f * scale;
        boolean clipped = ScissorFunction.pushRaw(
                rowX - clipPadX, rowY - clipPadY,
                rowW + clipPadX * 2.0f, rowH + clipPadY * 2.0f);
        try {
            for (int i = 0; i < subcategories.size(); i++) {
                ModuleSubcategoryDefinition subcategory = subcategories.get(i);
                float x = contentXs[i] - scroll;
                float itemW = itemWidths[i];
                boolean hovered = !searchMode && viewportHover && inside(mouseX, mouseY, x, rowY, itemW, rowH);
                float hover = panel.subcategoryHoverAnim(subcategory, hovered);
                hoverValues[i] = hover;
                if (hovered) SystemCursor.set(SystemCursor.CursorType.HAND);
                if (!searchMode) {
                    panel.subcategoryHits.add(new ModulesMenuPanel.SubcategoryHit(subcategory, x, rowY, itemW, rowH));
                }

                UiBoxShape shape = UiBoxShape.rounded(x, rowY, itemW, rowH, radius);

                // Tint the optical glass itself. The old version put an opaque-ish theme gradient
                // over the result, which flattened the refraction and made these read as matte pills.
                // Keep only a whisper of authored gradient below for the theme's two-colour direction.
                int themeGlass = mix(buttonGradient.start(), buttonGradient.end(), 0.50f);
                int glassRgb = mix(ModulesMenuStyle.panelBgGlassDark(), themeGlass,
                        0.72f + 0.08f * hover);
                int glassTint = withAlpha(glassRgb, Math.round(alpha * (108.0f + 18.0f * hover)));
                UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT.withInnerGlow(
                        0.006f + hover * 0.006f,
                        2.15f + hover * 0.20f,
                        withAlpha(themeGlass, Math.round(alpha * (4.0f + 4.0f * hover)))
                );
                renderer.withLiquidGlassMaterial(material, () -> renderer.liquidGlassRect(
                        x, rowY, itemW, rowH, radius,
                        glassTint,
                        alpha * (0.92f + hover * 0.04f),
                        alpha * (0.82f + hover * 0.06f),
                        Renderer2D.LiquidGlassPreset.HUD_SMALL
                ));

                int idleStart = withAlpha(buttonGradient.start(), Math.round(alpha * (7.0f + 5.0f * hover)));
                int idleEnd = withAlpha(buttonGradient.end(), Math.round(alpha * (5.0f + 5.0f * hover)));
                renderer.box(shape, UiPaint.linear(idleStart, idleEnd, gradientAngle, 0.0f));

                int strokeStart = withAlpha(buttonGradient.start(), Math.round(alpha * (17.0f + 10.0f * hover)));
                int strokeEnd = withAlpha(buttonGradient.end(), Math.round(alpha * (14.0f + 9.0f * hover)));
                renderer.boxStroke(
                        shape,
                        UiPaint.linear(strokeStart, strokeEnd, gradientAngle, 0.0f),
                        UiStroke.of(0.22f * scale)
                );
            }

            // Faster lead/trail movement. There is still no threshold swap: both lobes converge into
            // the target continuously, but the whole transition now finishes in ~150 ms.
            float progress = AnimationUtility.clamp01(panel.subcategoryIndicatorProgress);
            float leadProgress = AnimationUtility.easeOutQuint(AnimationUtility.clamp01(progress * 1.10f));
            float trailProgress = AnimationUtility.easeOutCubic(
                    AnimationUtility.clamp01((progress - 0.035f) / 0.965f)
            );
            float motion = (float) Math.sin(Math.PI * progress);

            float leadX = AnimationUtility.lerp(
                    panel.subcategoryIndicatorStartX, panel.subcategoryIndicatorTargetX, leadProgress) - scroll;
            float leadW = AnimationUtility.lerp(
                    panel.subcategoryIndicatorStartW, panel.subcategoryIndicatorTargetW, leadProgress);
            float trailX = AnimationUtility.lerp(
                    panel.subcategoryIndicatorStartX, panel.subcategoryIndicatorTargetX, trailProgress) - scroll;
            float trailW = AnimationUtility.lerp(
                    panel.subcategoryIndicatorStartW, panel.subcategoryIndicatorTargetW, trailProgress);

            float smoothing = (2.15f + 0.80f * motion) * scale;
            UiCompoundSdf activeShape = UiCompoundSdf.smoothBoxUnion(
                    UiRect.of(trailX, rowY, trailW, rowH), radius,
                    UiRect.of(leadX, rowY, leadW, rowH), radius,
                    smoothing
            );

            int activeThemeGlass = mix(buttonGradient.start(), buttonGradient.end(), 0.50f);
            int activeGlassRgb = mix(ModulesMenuStyle.panelBgGlassDark(), activeThemeGlass, 0.86f);
            int activeGlassTint = withAlpha(activeGlassRgb, Math.round(alpha * 148.0f));
            UiLiquidGlassMaterial activeMaterial = UiLiquidGlassMaterial.DEFAULT.withInnerGlow(
                    0.012f + 0.004f * motion,
                    2.45f + 0.18f * motion,
                    withAlpha(activeThemeGlass, Math.round(alpha * 8.0f))
            );
            renderer.withLiquidGlassMaterial(activeMaterial, () -> renderer.liquidGlassCompound(
                    activeShape,
                    activeGlassTint,
                    alpha * 0.98f,
                    alpha * 0.90f,
                    Renderer2D.LiquidGlassPreset.HUD_SMALL
            ));

            // Preserve the configured theme gradient, but at surface-coating strength only. The
            // scene/refraction remains the dominant body of the active control.
            int activeStart = withAlpha(buttonGradient.start(), Math.round(alpha * 20.0f));
            int activeEnd = withAlpha(buttonGradient.end(), Math.round(alpha * 15.0f));
            renderer.compoundSdf(activeShape, UiPaint.linear(activeStart, activeEnd, gradientAngle, 0.0f));

            int activeStrokeStart = withAlpha(buttonGradient.start(), Math.round(alpha * 48.0f));
            int activeStrokeEnd = withAlpha(buttonGradient.end(), Math.round(alpha * 40.0f));
            renderer.compoundSdfStroke(
                    activeShape,
                    UiPaint.linear(activeStrokeStart, activeStrokeEnd, gradientAngle, 0.0f),
                    UiStroke.of(0.30f * scale)
            );

            for (int i = 0; i < subcategories.size(); i++) {
                ModuleSubcategoryDefinition subcategory = subcategories.get(i);
                float x = contentXs[i] - scroll;
                float itemW = itemWidths[i];
                float hover = hoverValues[i];
                boolean selected = active != null && active.key().equals(subcategory.key());
                String label = subcategory.displayName();
                float textW = ClickGuiRenderer.textWidth(medium, label, fontSize);
                float textH = textHeight(medium, fontSize);
                float textX = x + (itemW - textW) * 0.5f;
                float textY = rowY + (rowH - textH) * 0.5f - 0.10f * scale;
                int color = selected
                        ? withAlpha(ModulesMenuStyle.text(), alpha)
                        : mix(
                                withAlpha(ModulesMenuStyle.textMuted(), alpha),
                                withAlpha(ModulesMenuStyle.text(), alpha),
                                0.52f + hover * 0.40f
                        );
                ClickGuiRenderer.drawText(medium, label, textX, textY, fontSize, color, false);
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }

        renderSubcategoryScrollHints(panel, rowX, rowY, rowW, rowH, alpha);
    }

    private void renderSubcategoryScrollHints(ModulesMenuPanel panel,
                                              float rowX,
                                              float rowY,
                                              float rowW,
                                              float rowH,
                                              float alpha) {
        float left = AnimationUtility.easeOutCubic(panel.subcategoryLeftHintAnim);
        float right = AnimationUtility.easeOutCubic(panel.subcategoryRightHintAnim);
        if (Math.max(left, right) <= 0.002f || alpha <= 0.002f) return;

        // A short edge scrim keeps the SVG readable over both selected and idle glass pills while
        // still letting the underlying strip remain visible. It also makes clipped labels read as
        // continuation rather than accidental scissor damage.
        float fadeW = 10.5f * scale;
        int edgeRgb = ModulesMenuStyle.panelBgGlassDark();
        if (left > 0.002f) {
            int edge = withAlpha(edgeRgb, Math.round(alpha * left * 176.0f));
            int clear = withAlpha(edgeRgb, 0);
            LayoutRender2D.rectQuad(rowX - 0.15f * scale, rowY - 0.55f * scale,
                    fadeW, rowH + 1.10f * scale, edge, clear, clear, edge);
            drawSubcategoryScrollChevron("chevron-left",
                    rowX + 1.25f * scale, rowY, rowH, alpha * left, -1.0f);
        }
        if (right > 0.002f) {
            int edge = withAlpha(edgeRgb, Math.round(alpha * right * 176.0f));
            int clear = withAlpha(edgeRgb, 0);
            LayoutRender2D.rectQuad(rowX + rowW - fadeW + 0.15f * scale, rowY - 0.55f * scale,
                    fadeW, rowH + 1.10f * scale, clear, edge, edge, clear);
            drawSubcategoryScrollChevron("chevron-right",
                    rowX + rowW - 6.45f * scale, rowY, rowH, alpha * right, 1.0f);
        }
    }

    private void drawSubcategoryScrollChevron(String icon,
                                               float x,
                                               float rowY,
                                               float rowH,
                                               float alpha,
                                               float direction) {
        float iconSize = 5.15f * scale;
        float iconY = rowY + (rowH - iconSize) * 0.5f;
        // Tiny motion keeps the hint perceptible without turning it into a pulsing CTA.
        float pulse = (float) Math.sin(AnimationUtility.time(0.0045f, AnimationUtility.Mode.NANOS));
        float travel = pulse * 0.28f * scale * direction * AnimationUtility.clamp01(alpha);
        int iconColor = withAlpha(ModulesMenuStyle.text(), Math.round(alpha * 216.0f));
        Renderer2D.COLOR.svg(icon, x + travel, iconY, iconSize, iconSize,
                SvgRenderOptions.overrideColor(iconColor));
    }

    private void renderModulePage(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.001f) return;

        float categorySwitch = AnimationUtility.easeOutQuint(panel.subcategoryContentAnim);
        float switchAlpha = AnimationUtility.clamp(categorySwitch, 0.0f, 1.0f);
        alpha *= switchAlpha;
        if (alpha <= 0.001f) return;

        float switchOffset = (1.0f - categorySwitch) * 4.0f * scale * panel.subcategoryDirection;
        float pageX = panel.x - panel.w * panel.swap + switchOffset;
        float searchCollapse = AnimationUtility.easeOutCubic(searchAnim);
        float moduleHeaderH = AnimationUtility.lerp(MODULE_HEADER_H, BASE_HEADER_H, searchCollapse);
        float listY = panel.y + (moduleHeaderH + SEPARATOR_H - 1.0f) * scale;
        float clipY = panel.y + (moduleHeaderH + SEPARATOR_H) * scale;
        float clipH = panel.h - (moduleHeaderH + SEPARATOR_H) * scale - 0.5f * scale;

        boolean clipped = ScissorFunction.pushRaw(panel.x, clipY, panel.w, clipH);
        ClickGuiRenderer.flushRenderer();
        float listW = Math.max(1.0f, panel.w - 7.0f * scale);

        float y = listY - panel.modulesSmoothScroll;
        float total = 0.0f;

        boolean globalSearch = ClickGuiSearch.isActive() || ClickGuiSearch.hasQuery();
        ModuleSubcategoryDefinition subcategoryFilter = globalSearch ? null : panel.activeSubcategory();
        List<ModuleComponent.CardEntry> entries = ModulesMenuResolver.buildCards(panel.category, subcategoryFilter);
        boolean panelShapeClip = false;
        if (ClipFunction.usesMsaaStencilByDefault()) {
            // The selected fallback owns the complete panel subtree, including procedural hovers.
            // This is one local MSAA layer per panel, never one layer per hovered module.
            panelShapeClip = ClipFunction.pushRoundedRect(
                    panel.x, panel.y, panel.w, panel.h, PANEL_RADIUS * scale);
        }
        try {
            for (ModuleComponent.CardEntry entry : entries) {
                if (!ClickGuiSearch.matches(entry.title(), entry.searchAliases())) continue;

                if (y + MODULE_ROW_H * scale >= clipY && y <= clipY + clipH) {
                    float rowH = MODULE_ROW_H * scale;
                    boolean hover = inside(mouseX, mouseY, pageX, y, listW, rowH);
                    float hoverAnim = panel.hoverAnim(entry.getId(), hover);
                    if (hover && panel.selected == null) {
                        captureHoverDescription(entry.getId(), entry.description(), panel.category);
                    }
                    renderModuleRowHover(panel, entry.getId(), pageX, y, listW, rowH,
                            mouseX, mouseY, alpha, hoverAnim);
                }

                y += MODULE_ROW_H * scale;
                total += MODULE_ROW_H * scale;
            }

            ClickGuiRenderer.flushRenderer();
            if (!panelShapeClip) {
                // In the normal analytic mode the material has no analytic permutation, so only
                // text/shapes enter the analytic scope. MSAA mode above keeps everything together.
                panelShapeClip = ClipFunction.pushRoundedRect(
                        panel.x, panel.y, panel.w, panel.h, PANEL_RADIUS * scale);
            }
            // Every standalone TextRenderer.end() is an execution boundary. Without this scope,
            // each visible module label splits otherwise compatible shape/text work into another
            // tiny mesh upload, render pass and pipeline round-trip. The complete row list shares
            // one scissor and one shape clip, so batching its foreground text preserves ordering.
            ClickGuiRenderer.beginTextBatch();
            try {
                y = listY - panel.modulesSmoothScroll;
                for (ModuleComponent.CardEntry entry : entries) {
                    if (!ClickGuiSearch.matches(entry.title(), entry.searchAliases())) continue;
                    if (y + MODULE_ROW_H * scale >= clipY && y <= clipY + clipH) {
                        renderModuleRow(panel, entry, pageX, y, listW, alpha,
                                panel.hoverAnimValue(entry.getId()));
                    }
                    y += MODULE_ROW_H * scale;
                }

                // Enabled checkmarks do not overlap the row dividers. Emitting them after the
                // geometry layer keeps exact visible ordering while preventing every SVG from
                // splitting one compatible analytic geometry batch into another render pass.
                y = listY - panel.modulesSmoothScroll;
                for (ModuleComponent.CardEntry entry : entries) {
                    if (!ClickGuiSearch.matches(entry.title(), entry.searchAliases())) continue;
                    if (y + MODULE_ROW_H * scale >= clipY && y <= clipY + clipH) {
                        renderModuleRowCheck(panel, entry, pageX, y, listW, alpha);
                    }
                    y += MODULE_ROW_H * scale;
                }
            } finally {
                ClickGuiRenderer.endTextBatch();
            }
        } finally {
            ClickGuiRenderer.flushRenderer();
            if (panelShapeClip) ClipFunction.pop();
        }

        panel.maxModulesScroll = Math.max(0.0f, total - clipH);
        panel.modulesScroll = AnimationUtility.clamp(panel.modulesScroll, 0.0f, panel.maxModulesScroll);
        panel.modulesSmoothScroll = AnimationUtility.approach(panel.modulesSmoothScroll, panel.modulesScroll, 0.22f);
        panel.modulesSmoothScroll = AnimationUtility.snap(panel.modulesSmoothScroll, panel.modulesScroll, 0.05f);

        if (clipped) ScissorFunction.pop();

        renderScrollbar(panel, panel.modulesSmoothScroll, panel.maxModulesScroll, clipY, clipH, alpha, mouseX, mouseY);
    }

    private void renderModuleRow(
            ModulesMenuPanel panel,
            ModuleComponent.CardEntry entry,
            float x,
            float y,
            float rowW,
            float alpha,
            float hoverAnim
    ) {
        float h = MODULE_ROW_H * scale;

        float enabled = panel.enabledAnim(entry.getId(), entry.enabled());

        String label = panel.bindingId != null && panel.bindingId.equals(entry.getId())
                ? bindingLabel(entry)
                : entry.title();

        boolean moduleListEdit = isModuleListEditHeld();
        String moduleListLabel = entry.shownInModuleList() ? "[ON]" : "[OFF]";
        float moduleListTextSize = 7.2f * scale;
        float moduleListTextW = moduleListEdit
                ? ClickGuiRenderer.textWidth(semibold, moduleListLabel, moduleListTextSize)
                : 0.0f;
        float moduleListW = moduleListEdit ? moduleListTextW + 3.0f * scale : 0.0f;
        float checkReserve = entry.enabled() ? 18.0f * scale : 6.0f * scale;
        float moduleListX = x + rowW - checkReserve - moduleListW - 3.0f * scale;
        float moduleListY = y + middle(textHeight(semibold, moduleListTextSize), h) - 0.5f * scale;

        Module faultModule = ModulesMenuResolver.moduleById(entry.getId());
        boolean failed = entry.failed() || SettingErrorView.hasFailure(faultModule);
        int nameColor = failed ? withAlpha(0xFFFF7777,alpha) : mix(withAlpha(ModulesMenuStyle.textMuted(), alpha), withAlpha(ModulesMenuStyle.text(), alpha), 0.25f + 0.75f * enabled + 0.20f * hoverAnim);

        float nameX = x + (TEXT_LEFT_PADDING + 2.0f * enabled) * scale;
        float nameSize = 8.0f * scale;
        float nameY = y + middle(textHeight(regular, nameSize), h) - 0.5f * scale;
        float nameMaxW = moduleListEdit
                ? Math.max(12.0f * scale, moduleListX - nameX - 5.0f * scale)
                : Math.max(12.0f * scale, rowW - (nameX - x) - 18.0f * scale);

        String matchedAlias = panel.bindingId == null || !panel.bindingId.equals(entry.getId())
                ? ClickGuiSearch.matchingAlias(entry.searchAliases())
                : null;
        String aliasLabel = matchedAlias == null || matchedAlias.isBlank()
                ? null
                : "(aka " + matchedAlias + ")";

        if (failed) {
            SettingErrorView.warning(x+rowW-15f*scale,y+5f*scale,9f*scale,alpha);
        }
        renderModuleSearchTitle(label, aliasLabel, nameX, nameY, nameMaxW, nameSize, nameColor, alpha, hoverAnim);

        if (moduleListEdit) {
            int listColor = entry.shownInModuleList() ? ModulesMenuStyle.MODULE_LIST_ON : ModulesMenuStyle.MODULE_LIST_OFF;
            ClickGuiRenderer.drawText(semibold, moduleListLabel, moduleListX, moduleListY, moduleListTextSize, withAlpha(listColor, alpha), false);
        }

        int divider = withAlpha(ModulesMenuStyle.text(), alpha * 0.02f);
        LayoutRender2D.rect(x + 3.0f * scale, y + h, Math.max(1f, rowW - 6.0f * scale), 0.5f * scale, divider);

        panel.hits.add(new ModulesMenuPanel.ModuleHit(entry.getId(), x, y, rowW, h, entry.hasSettings(), entry.toggleable()));
    }

    private void renderModuleSearchTitle(
            String label,
            String aliasLabel,
            float x,
            float y,
            float maxW,
            float nameSize,
            int nameColor,
            float alpha,
            float hoverAnim
    ) {
        if (label == null) label = "";
        if (aliasLabel == null || aliasLabel.isBlank() || !ClickGuiSearch.hasQuery()) {
            String visible = ClickGuiRenderer.fitText(regular, label, nameSize, maxW);
            ClickGuiRenderer.drawText(regular, visible, x, y, nameSize, nameColor, false);
            return;
        }

        float gap = 3.0f * scale;
        float aliasSize = 6.15f * scale;
        float fullNameW = ClickGuiRenderer.textWidth(regular, label, nameSize);
        float fullAliasW = ClickGuiRenderer.textWidth(medium, aliasLabel, aliasSize);

        if (fullNameW + gap + fullAliasW <= maxW) {
            ClickGuiRenderer.drawText(regular, label, x, y, nameSize, nameColor, false);
            float aliasY = y + middle(textHeight(medium, aliasSize), textHeight(regular, nameSize)) + 0.15f * scale;
            int aliasColor = mix(
                    withAlpha(ModulesMenuStyle.textFaint(), alpha),
                    withAlpha(ModulesMenuStyle.textMuted(), alpha),
                    0.28f + 0.30f * hoverAnim
            );
            ClickGuiRenderer.drawText(medium, aliasLabel, x + fullNameW + gap, aliasY, aliasSize, aliasColor, false);
            return;
        }

        // Preserve the module's primary name first. Alias metadata is search-only and
        // receives a bounded share of the row so it cannot crowd the normal row affordances.
        float minNameBudget = Math.min(fullNameW, Math.max(25.0f * scale, maxW * 0.56f));
        float aliasBudget = Math.min(fullAliasW, Math.min(48.0f * scale, maxW - minNameBudget - gap));
        float aliasMinUseful = ClickGuiRenderer.textWidth(medium, "(aka x)", aliasSize);
        if (aliasBudget < aliasMinUseful) {
            String visible = ClickGuiRenderer.fitText(regular, label, nameSize, maxW);
            ClickGuiRenderer.drawText(regular, visible, x, y, nameSize, nameColor, false);
            return;
        }

        float nameBudget = Math.max(12.0f * scale, maxW - gap - aliasBudget);
        String visibleName = ClickGuiRenderer.fitText(regular, label, nameSize, nameBudget);
        String visibleAlias = ClickGuiRenderer.fitText(medium, aliasLabel, aliasSize, aliasBudget);
        float visibleNameW = ClickGuiRenderer.textWidth(regular, visibleName, nameSize);
        float aliasY = y + middle(textHeight(medium, aliasSize), textHeight(regular, nameSize)) + 0.15f * scale;
        int aliasColor = mix(
                withAlpha(ModulesMenuStyle.textFaint(), alpha),
                withAlpha(ModulesMenuStyle.textMuted(), alpha),
                0.28f + 0.30f * hoverAnim
        );

        ClickGuiRenderer.drawText(regular, visibleName, x, y, nameSize, nameColor, false);
        ClickGuiRenderer.drawText(medium, visibleAlias, x + visibleNameW + gap, aliasY, aliasSize, aliasColor, false);
    }

    private void renderModuleRowCheck(ModulesMenuPanel panel,
                                      ModuleComponent.CardEntry entry,
                                      float x,
                                      float y,
                                      float rowW,
                                      float alpha) {
        float enabled = panel.enabledAnimValue(entry.getId());
        if (SettingErrorView.hasFailure(ModulesMenuResolver.moduleById(entry.getId()))) return;
        if (enabled <= 0.001f) return;

        float check = 6.0f * scale;
        float checkX = x + rowW - 15.0f * scale - 2.0f * scale * enabled;
        float checkY = y + 7.0f * scale;
        Renderer2D.COLOR.svg(
                "check",
                checkX,
                checkY,
                check,
                check,
                SvgRenderOptions.overrideColor(withAlpha(
                        ModulesMenuStyle.text(),
                        alpha * (0.10f + 0.90f * enabled)
                ))
        );
    }

    private void renderModuleRowHover(ModulesMenuPanel panel,
                                      String moduleId,
                                      float x,
                                      float y,
                                      float w,
                                      float h,
                                      float mouseX,
                                      float mouseY,
                                      float alpha,
                                      float hoverAnim) {
        if (hoverAnim <= 0.001f || alpha <= 0.001f) return;

        float rx = x + 3.0f * scale;
        float ry = y + 2.0f * scale;
        float rw = w - 6.0f * scale;
        float rh = h - 4.0f * scale;
        if (rw <= 0.5f || rh <= 0.5f) return;

        float clipTop = panel.y + (MODULE_HEADER_H + SEPARATOR_H) * scale;
        float clipBottom = panel.y + panel.h - 0.5f * scale;
        if (ry + rh <= clipTop || ry >= clipBottom) return;

        Renderer2D.COLOR.moduleCategorySurface(
                rx,
                ry,
                rw,
                rh,
                5.0f * scale,
                panel.x,
                panel.y,
                panel.w,
                panel.h,
                ClipFunction.usesMsaaStencilByDefault() ? 0.0f : PANEL_RADIUS * scale,
                categoryEffectMode(panel.category),
                hoverAnim,
                categoryEffectTime(),
                mouseX,
                mouseY,
                moduleEffectSeed(moduleId),
                ModulesMenuStyle.categoryFxPrimary(panel.category, alpha),
                ModulesMenuStyle.categoryFxSecondary(panel.category, alpha),
                ModulesMenuStyle.categoryFxHighlight(panel.category, alpha),
                1.0f
        );
    }


    private static int categoryEffectMode(ModulesMenuCategory category) {
        return switch (category) {
            case COMBAT -> 0;   // fire
            case MOVEMENT -> 1; // procedural ice wind / blizzard
            case VISUALS -> 2;  // rapidly growing faceted crystals
            case OTHER -> 3;    // procedural water
            case PLAYER -> 4;   // restrained pearlescent fallback for the remaining category
        };
    }

    private static float categoryEffectTime() {
        long nanos = System.nanoTime() % 120_000_000_000L;
        return nanos / 1_000_000_000.0f;
    }

    private static float moduleEffectSeed(String moduleId) {
        int hash = moduleId != null ? moduleId.hashCode() : 0x6D2B79F5;
        hash ^= hash >>> 16;
        return (hash & 0x00FFFFFF) / 16777215.0f;
    }

    private void captureHoverDescription(String id, String description, ModulesMenuCategory category) {
        if (id == null || id.isBlank() || description == null || description.isBlank()) return;
        frameHoverDescriptionId = id;
        frameHoverDescription = description;
        frameHoverDescriptionCategory = category;
    }

    private void updateHoverDescription(float dt) {
        boolean hasTarget = frameHoverDescription != null && !frameHoverDescription.isBlank();
        if (hasTarget && !frameHoverDescriptionId.equals(activeHoverDescriptionId)) {
            activeHoverDescriptionId = frameHoverDescriptionId;
            activeHoverDescription = frameHoverDescription;
            activeHoverDescriptionCategory = frameHoverDescriptionCategory;
            hoverDescriptionAnim = Math.min(hoverDescriptionAnim, 0.32f);
            hoverDescriptionMarqueeStartNanos = System.nanoTime();
        } else if (hasTarget) {
            activeHoverDescription = frameHoverDescription;
            activeHoverDescriptionCategory = frameHoverDescriptionCategory;
        }

        float target = hasTarget ? 1.0f : 0.0f;
        hoverDescriptionAnim = AnimationUtility.approach(
                hoverDescriptionAnim,
                target,
                dt,
                hasTarget ? 9.5f : 11.5f
        );
        hoverDescriptionAnim = AnimationUtility.snap(hoverDescriptionAnim, target, 0.008f);

        if (!hasTarget && hoverDescriptionAnim <= 0.001f) {
            activeHoverDescriptionId = null;
            activeHoverDescription = null;
            activeHoverDescriptionCategory = null;
            hoverDescriptionMarqueeStartNanos = 0L;
        }
    }

    private void renderHoverDescription() {
        if (comfortaa == null || activeHoverDescription == null || activeHoverDescription.isBlank()) return;
        float raw = AnimationUtility.clamp01(hoverDescriptionAnim);
        if (raw <= 0.001f) return;

        float alphaProgress = AnimationUtility.easeOutQuint(raw);
        float motionProgress = AnimationUtility.easeOutBack(raw, 0.72f);
        float alpha = alphaProgress * easeOutCubic(screenAnim);
        if (alpha <= 0.001f) return;

        float sizeScale = 0.965f + 0.035f * Math.max(0.0f, motionProgress);
        float fontSize = HOVER_DESCRIPTION_FONT * scale * sizeScale;
        float maxWidth = HOVER_DESCRIPTION_MAX_W * scale;
        String text = activeHoverDescription.trim();
        if (text.isBlank()) return;

        float fullTextW = ClickGuiRenderer.textWidth(comfortaa, text, fontSize);
        float textH = textHeight(comfortaa, fontSize);
        float viewW = Math.min(maxWidth, fullTextW);
        boolean marquee = fullTextW > maxWidth + 0.5f * scale;
        float minPanelY = Float.MAX_VALUE;
        for (ModulesMenuPanel panel : panels) minPanelY = Math.min(minPanelY, panel.y);
        if (!Float.isFinite(minPanelY)) return;

        float x = areaX + areaW * 0.5f - viewW * 0.5f;
        float y = minPanelY - textH - 10.0f * scale
                + (1.0f - motionProgress) * 5.5f * scale;
        y = Math.max(areaY + 2.0f * scale, y);

        int negativeGlassColor = withAlpha(0xFFFFFFFF, Math.min(1.0f, alpha * 0.84f));
        float blendBoundsX = x - 3.0f * scale;
        float blendBoundsY = y - 3.0f * scale;
        float blendBoundsW = viewW + 6.0f * scale;
        float blendBoundsH = textH + 6.0f * scale;
        UiRect blendBounds = UiRect.of(blendBoundsX, blendBoundsY, blendBoundsW, blendBoundsH);
        UiBackdropBlendSpec blend = UiBackdropBlendSpec.negative(1.0f);
        UiBackdropRequest backdrop = UiBackdropRequest.currentTargetGlass(
                blendBounds,
                UiBlurQuality.LIQUID_GLASS,
                Renderer2D.LIQUID_GLASS_KAWASE_OFFSET_PX
        );

        if (!marquee) {
            renderHoverDescriptionGlassText(
                    text, x, y, fontSize, negativeGlassColor,
                    blendBoundsX, blendBoundsY, blendBoundsW, blendBoundsH, blend, backdrop,
                    Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 0.0f
            );
            return;
        }

        if (hoverDescriptionMarqueeStartNanos == 0L) {
            hoverDescriptionMarqueeStartNanos = System.nanoTime();
        }
        float speed = HOVER_DESCRIPTION_MARQUEE_SPEED * scale;
        float gap = HOVER_DESCRIPTION_MARQUEE_GAP * scale;
        float cycleDistance = fullTextW + gap;
        float moveDuration = speed <= 0.0f ? 0.0f : cycleDistance / speed;
        float cycleDuration = HOVER_DESCRIPTION_MARQUEE_PAUSE_SEC + moveDuration;
        float elapsed = (System.nanoTime() - hoverDescriptionMarqueeStartNanos) / 1_000_000_000.0f;
        float t = cycleDuration > 0.0f ? elapsed % cycleDuration : 0.0f;
        float offset = t <= HOVER_DESCRIPTION_MARQUEE_PAUSE_SEC
                ? 0.0f
                : -(t - HOVER_DESCRIPTION_MARQUEE_PAUSE_SEC) * speed;
        float fade = Math.min(viewW * 0.22f, HOVER_DESCRIPTION_FADE_W * scale);
        float clipLeft = x;
        float clipRight = x + viewW;

        boolean clipped = ScissorFunction.pushRaw(clipLeft, y, viewW, textH);
        try {
            renderHoverDescriptionGlassText(
                    text, x + offset, y, fontSize, negativeGlassColor,
                    blendBoundsX, blendBoundsY, blendBoundsW, blendBoundsH, blend, backdrop,
                    clipLeft, clipRight, fade
            );
            if (cycleDistance > viewW * 0.5f) {
                renderHoverDescriptionGlassText(
                        text, x + offset + cycleDistance, y, fontSize, negativeGlassColor,
                        blendBoundsX, blendBoundsY, blendBoundsW, blendBoundsH, blend, backdrop,
                        clipLeft, clipRight, fade
                );
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void renderHoverDescriptionGlassText(
            String text,
            float x,
            float y,
            float fontSize,
            int color,
            float blendBoundsX,
            float blendBoundsY,
            float blendBoundsW,
            float blendBoundsH,
            UiBackdropBlendSpec blend,
            UiBackdropRequest backdrop,
            float clipLeft,
            float clipRight,
            float fadeWidth
    ) {
        comfortaa.beginSize(fontSize, false, false);
        try {
            comfortaa.renderLiquidGlassBlendQuadGradient(
                    text,
                    x,
                    y,
                    (index, codePoint, x0, y0, x1, y1, out) -> {
                        if (fadeWidth <= 0.0f || !Float.isFinite(clipLeft) || !Float.isFinite(clipRight)) {
                            out[0] = color;
                            out[1] = color;
                            out[2] = color;
                            out[3] = color;
                            return;
                        }

                        int leftColor = withAlpha(color, horizontalMarqueeFade((float) x0, clipLeft, clipRight, fadeWidth));
                        int rightColor = withAlpha(color, horizontalMarqueeFade((float) x1, clipLeft, clipRight, fadeWidth));
                        out[0] = leftColor;
                        out[1] = leftColor;
                        out[2] = rightColor;
                        out[3] = rightColor;
                    },
                    blendBoundsX,
                    blendBoundsY,
                    blendBoundsW,
                    blendBoundsH,
                    blend,
                    backdrop
            );
        } finally {
            comfortaa.end();
        }
    }

    private static float horizontalMarqueeFade(float px, float left, float right, float fadeWidth) {
        if (fadeWidth <= 0.0f) return 1.0f;
        float leftAlpha = AnimationUtility.clamp01((px - left) / fadeWidth);
        float rightAlpha = AnimationUtility.clamp01((right - px) / fadeWidth);
        return Math.min(leftAlpha, rightAlpha);
    }

    private void renderSettingsPage(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.001f) return;

        float pageX = panel.x + panel.w * (1.0f - panel.swap);
        float settingsPadLeft = 6.0f * scale;
        float settingsPadRight = 13.0f * scale;
        float settingsX = pageX + settingsPadLeft;
        float settingsW = Math.max(1.0f, panel.w - settingsPadLeft - settingsPadRight);
        float settingsTop = panel.y + (BASE_HEADER_H + SEPARATOR_H) * scale;
        float backRowY = panel.y + 28.0f * scale;
        float backRowH = 20.0f * scale;
        boolean pageClip = ScissorFunction.pushRaw(panel.x, panel.y, panel.w, panel.h);
        try {

        boolean headerClip = ScissorFunction.pushRaw(panel.x, panel.y, panel.w, panel.h);
        try {

        if (inside(mouseX, mouseY, pageX, backRowY, panel.w, backRowH)) {
            int hoverBg = withAlpha(ModulesMenuStyle.rowHover(), alpha);
            LayoutRender2D.roundedQuad(
                    pageX + 3.0f * scale,
                    backRowY + scale,
                    panel.w - 6.0f * scale,
                    backRowH - 2.0f * scale,
                    5.0f * scale,
                    hoverBg,
                    hoverBg,
                    hoverBg,
                    hoverBg
            );
        }

        String title = panel.selectedTitle == null ? "Settings" : panel.selectedTitle;
        title = ClickGuiRenderer.fitText(regular, title, 8.0f * scale, panel.w - 18.0f * scale);

        ClickGuiRenderer.drawText(
                regular,
                title,
                pageX + TEXT_LEFT_PADDING * scale,
                settingsTop + middle(textHeight(regular, 8.0f * scale), BASE_HEADER_H * scale) - scale,
                8.0f * scale,
                withAlpha(ModulesMenuStyle.text(), alpha),
                false
        );

        } finally { if (headerClip) ScissorFunction.pop(); }

        float clipY = settingsTop + BASE_HEADER_H * scale;
        float clipH = panel.h - BASE_HEADER_H * 2.0f * scale - SEPARATOR_H * scale - 0.5f * scale - 5.0f * scale;

        boolean clipped = ScissorFunction.pushRaw(settingsX, clipY, settingsW, clipH);
        try {

        float y = clipY - panel.settingsSmoothScroll;
        float total = 0.0f;

        panel.previewX = panel.previewY = panel.previewW = panel.previewH = 0.0f;
        if (ModulesMenuResolver.supportsPreview(panel.selected)) {
            float actionH = 20.0f * scale;
            float actionGap = 5.0f * scale;
            panel.previewX = settingsX;
            panel.previewY = y;
            panel.previewW = settingsW;
            panel.previewH = actionH;
            if (y + actionH >= clipY && y <= clipY + clipH) {
                boolean hovered = inside(mouseX, mouseY, settingsX, y, settingsW, actionH);
                int left = withAlpha(hovered ? ModulesMenuStyle.rowHover() : ModulesMenuStyle.panelBgGlassDark(), alpha * (hovered ? 0.92f : 0.72f));
                int right = withAlpha(hovered ? ModulesMenuStyle.categoryFxPrimary(panel.category, alpha) : ModulesMenuStyle.panelStroke(), alpha * (hovered ? 0.54f : 0.46f));
                LayoutRender2D.roundedQuad(settingsX, y, settingsW, actionH, 6.0f * scale, left, right, right, left);
                LayoutRender2D.roundedStrokeQuad(
                        settingsX, y, settingsW, actionH, 6.0f * scale, 0.45f * scale,
                        withAlpha(ModulesMenuStyle.textMuted(), alpha * 0.34f),
                        withAlpha(ModulesMenuStyle.text(), alpha * 0.18f),
                        withAlpha(ModulesMenuStyle.text(), alpha * 0.14f),
                        withAlpha(ModulesMenuStyle.textMuted(), alpha * 0.28f)
                );
                float icon = 7.0f * scale;
                Renderer2D.COLOR.svg("combatant:svg/eye", settingsX + 8.0f * scale, y + (actionH - icon) * 0.5f, icon, icon,
                        SvgRenderOptions.overrideColor(withAlpha(ModulesMenuStyle.text(), alpha)));
                ClickGuiRenderer.drawText(
                        medium,
                        ClickGuiI18n.tr("clickgui.modules.visual_preview", "Tune visually"),
                        settingsX + 19.0f * scale,
                        y + middle(textHeight(medium, 7.2f * scale), actionH),
                        7.2f * scale,
                        withAlpha(ModulesMenuStyle.text(), alpha),
                        false
                );
                if (hovered) SystemCursor.set(SystemCursor.CursorType.HAND);
            }
            y += actionH + actionGap;
            total += actionH + actionGap;
        }

        Module selectedModule = ModulesMenuResolver.moduleById(panel.selected);
        if (selectedModule != null) {
            List<Setting> desired = SettingErrorView.withDiagnostics(selectedModule, selectedModule.getSettings());
            if (!panel.selectedSettings.equals(desired)) {
                panel.selectedSettings.clear();
                panel.selectedSettings.addAll(desired);
            }
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.MODULES, scale)) {
            for (Setting setting : panel.selectedSettings) {
                float vis = setting.updateVisibilitySafely();
                if (!setting.isVisibilityTargetVisibleSafely() && vis <= 0.01f) continue;

                float baseH = setting.getHeightSafely();
                float h = baseH * vis;

                if (h > 0.5f) {
                    panel.settingHits.add(new ModulesMenuPanel.SettingHit(setting, settingsX, y, settingsW, h));

                    if (y + h >= clipY && y <= clipY + clipH) {
                        float slide = (-baseH + baseH * vis) * 0.5f;

                        boolean settingClipped = ScissorFunction.pushRaw(settingsX, y, settingsW, h);

                        try {
                            setting.renderSafely(settingsX, y + slide, settingsW, mouseX, mouseY);
                        } finally {
                            if (settingClipped) ScissorFunction.pop();
                        }
                    }
                }

                y += h;
                total += h;
            }
        }

        panel.maxSettingsScroll = Math.max(0.0f, total - clipH);
        panel.settingsScroll = AnimationUtility.clamp(panel.settingsScroll, 0.0f, panel.maxSettingsScroll);
        panel.settingsSmoothScroll = AnimationUtility.approach(panel.settingsSmoothScroll, panel.settingsScroll, 0.20f);
        panel.settingsSmoothScroll = AnimationUtility.snap(panel.settingsSmoothScroll, panel.settingsScroll, 0.05f);

        } finally { if (clipped) ScissorFunction.pop(); }

        renderScrollbar(panel, panel.settingsSmoothScroll, panel.maxSettingsScroll, clipY, clipH, alpha, mouseX, mouseY);
        } finally { if (pageClip) ScissorFunction.pop(); }
    }

    private void renderSearch(float mouseX, float mouseY) {
        if (searchAnim <= 0.001f && !ClickGuiSearch.isActive()) return;

        float alpha = easeOutCubic(screenAnim) * searchAnim;
        if (alpha <= 0.001f) return;

        float materialAlpha = alpha >= 0.999f ? 1.0f : alpha;
        float radius = 6.0f * scale;

        drawLiquidGlass(searchX, searchY, searchW, searchH, radius, false, materialAlpha);

        int veil = withAlpha(ModulesMenuStyle.panelBgGlassDark(), alpha * 0.25f);
        LayoutRender2D.roundedQuad(searchX, searchY, searchW, searchH, radius, veil, veil, veil, veil);

        String text = ClickGuiSearch.getText();
        String draw = text == null || text.isBlank() ? "Search..." : text;
        int color = text == null || text.isBlank() ? withAlpha(ModulesMenuStyle.textFaint(), alpha) : withAlpha(ModulesMenuStyle.text(), alpha);

        float tx = searchX + 8.0f * scale;
        float ty = searchY + middle(textHeight(regular, 8.0f * scale), searchH);
        boolean clip = ScissorFunction.pushRaw(tx, searchY, searchW - 16f * scale, searchH);
        if (ClickGuiSearch.getText().isEmpty() && !ClickGuiSearch.isActive()) {
            ClickGuiRenderer.drawText(regular, draw, tx, ty, 8f * scale, color, false);
        } else {
            ClickGuiSearch.editor().render(tx, ty, searchW - 16f * scale, searchH, regular, 8f * scale,
                    color, withAlpha(0xFF588CEF, alpha * 0.55f), ClickGuiSearch.isActive());
        }
        if (clip) ScissorFunction.pop();
    }

    private void renderHints() {
        MainConfig config = MainConfig.get();
        if (!config.isClickGuiHintsEnabled()) return;

        float alpha = easeOutCubic(screenAnim);
        ClickGuiHintOverlay.renderBottomLeft(
                areaX,
                areaY,
                areaW,
                areaH,
                scale,
                alpha,
                ClickGuiI18n.tr("clickgui.hints.modules.toggle", "LMB - toggle module"),
                ClickGuiI18n.tr("clickgui.hints.modules.settings", "RMB - settings"),
                ClickGuiI18n.tr("clickgui.hints.modules.module_list", "E - edit ModuleList"),
                ClickGuiI18n.tr("clickgui.hints.modules.search", "Ctrl+F - search"),
                ClickGuiI18n.tr("clickgui.hints.modules.close", "Esc - close ClickGui"),
                ClickGuiI18n.tr("clickgui.hints.modules.hide", "Alt+H - hide hints")
        );
    }

    private void drawLiquidGlass(float x, float y, float w, float h, float radius, boolean panel, float alpha) {
        if (alpha <= 0.001f) return;

        float materialAlpha = AnimationUtility.clamp(alpha, 0.0f, 1.0f);
        float blurAlpha = AnimationUtility.clamp(materialAlpha * (panel ? 1.20f : 1.04f), 0.0f, 1.0f);
        float thickness = (panel ? 15.5f : 12.5f) * scale;
        float fresnelPower = panel ? -18.0f : -16.0f;
        float fresnelAlpha = 1.0f;
        float baseAlpha = panel ? 0.78f : 0.84f;
        float fresnelMix = panel ? 0.52f : 0.48f;
        PrismaticGlassTransition prism = panel
                ? PrismaticGlassTransition.fromProgress(prismProgress)
                : PrismaticGlassTransition.CALM;
        float distortion = (panel ? 0.190f : 0.155f) * materialAlpha * (1f + prism.strength() * 0.55f);

        // liquidGlassRect already composites its prepared blur as an independent alpha layer.
        // A blurRect immediately before it prepares and draws a second blur chain for every panel,
        // while also evicting the reusable captured-world blur from the single-frame cache.
        Renderer2D.COLOR.withLiquidGlassBlurProfile(
                Renderer2D.BlurQuality.LIQUID_GLASS,
                1.38f,
                () -> Renderer2D.COLOR.liquidGlassRect(
                        x,
                        y,
                        w,
                        h,
                        radius,
                        thickness,
                        0xFFFFFFFF,
                        materialAlpha,
                        blurAlpha,
                        fresnelPower,
                        fresnelAlpha,
                        baseAlpha,
                        fresnelMix,
                        distortion,
                        0.0f,
                        prism.strength(),
                        prism.phase()
                )
        );
    }

    private void drawCategoryIcon(ModulesMenuPanel panel, int color) {
        float icon = 8.0f * scale;
        float ix = panel.x + panel.w - 10.0f * scale - icon;
        float iy = panel.y + middle(icon, BASE_HEADER_H * scale) + 0.5f * scale;

        Renderer2D.COLOR.svg(panel.category.icon(), ix, iy, icon, icon, SvgRenderOptions.overrideColor(color));
    }

    private void renderScrollbar(ModulesMenuPanel panel, float scroll, float maxScroll, float clipY, float clipH, float alpha, float mouseX, float mouseY) {
        boolean settingsPage = panel.selected != null;
        if (maxScroll <= 0.5f) {
            if (settingsPage) {
                panel.settingsScrollbarX = panel.settingsScrollbarY = panel.settingsScrollbarW = panel.settingsScrollbarH = 0f;
            } else {
                panel.modulesScrollbarX = panel.modulesScrollbarY = panel.modulesScrollbarW = panel.modulesScrollbarH = 0f;
            }
            return;
        }

        float trackW = 2.5f * scale;
        float trackX = panel.x + panel.w - 5.0f * scale;
        float trackY = clipY + 5.0f * scale;
        float trackH = Math.max(1.0f, clipH - 10.0f * scale);

        int trackA = ModulesMenuStyle.scrollTrackA(alpha);
        int trackB = ModulesMenuStyle.scrollTrackB(alpha);
        int handleA = ModulesMenuStyle.scrollHandleA(alpha);
        int handleB = ModulesMenuStyle.scrollHandleB(alpha);

        boolean scrollbarHovered = inside(mouseX, mouseY, trackX - 3.0f * scale, trackY, trackW + 6.0f * scale, trackH);
        boolean scrollbarDragging = settingsPage ? panel.settingsScrollbarDragging : panel.modulesScrollbarDragging;
        if (scrollbarHovered || scrollbarDragging) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }

        LayoutRender2D.roundedQuad(
                trackX,
                trackY,
                trackW,
                trackH,
                trackW * 0.5f,
                trackA,
                trackB,
                trackB,
                trackA
        );

        float handleH = Math.max(18.0f * scale, trackH * (trackH / (trackH + maxScroll)));
        float handleY = trackY + (trackH - handleH) * (scroll / Math.max(1.0f, maxScroll));
        if (settingsPage) {
            panel.settingsScrollbarX = trackX;
            panel.settingsScrollbarY = trackY;
            panel.settingsScrollbarW = trackW;
            panel.settingsScrollbarH = trackH;
            panel.settingsScrollbarHandleY = handleY;
            panel.settingsScrollbarHandleH = handleH;
        } else {
            panel.modulesScrollbarX = trackX;
            panel.modulesScrollbarY = trackY;
            panel.modulesScrollbarW = trackW;
            panel.modulesScrollbarH = trackH;
            panel.modulesScrollbarHandleY = handleY;
            panel.modulesScrollbarHandleH = handleH;
        }

        LayoutRender2D.roundedQuad(
                trackX,
                handleY,
                trackW,
                handleH,
                trackW * 0.5f,
                handleA,
                handleB,
                handleB,
                handleA
        );
    }

    private void ensureFonts() {
        long generation = Fonts.generation();
        if (fontGeneration != generation) {
            regular = null;
            medium = null;
            semibold = null;
            comfortaa = null;
            fontGeneration = generation;
        }
        if (regular == null)
            regular = BuiltinFontCatalog.ONEST_REGULAR.renderer(ClickGuiRenderer.getInterRegular());
        if (medium == null) medium = BuiltinFontCatalog.ONEST_MEDIUM.renderer(regular);
        if (semibold == null) semibold = BuiltinFontCatalog.ONEST_BOLD.renderer(medium);
        if (comfortaa == null) comfortaa = BuiltinFontCatalog.COMFORTAA.renderer(regular);
    }

    private String bindingLabel(ModuleComponent.CardEntry entry) {
        String pending = ClickGuiRenderer.getPendingBindDisplay();
        if (pending != null) return pending;

        String bind = entry.bindLabel();
        return bind == null || bind.isBlank() ? "Binding..." : "Key: " + bind;
    }
}
