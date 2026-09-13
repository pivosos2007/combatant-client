/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.modules;

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
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.util.ClickGuiHintOverlay;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
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
    private static final float PANEL_H = 240.0f;
    private static final float PANEL_GAP = 14.0f;
    private static final float HEADER_H = 24.0f;
    private static final float SEPARATOR_H = 4.0f;
    private static final float MODULE_ROW_H = 20.0f;
    private static final float PANEL_RADIUS = 10.0f;
    private static final float TEXT_LEFT_PADDING = 10.0f;

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
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;

    private TextRenderer regular;
    private TextRenderer medium;
    private TextRenderer semibold;
    private long fontGeneration = Long.MIN_VALUE;

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

        float py = areaY + outerPadTop + Math.max(0.0f, (areaH - outerPadTop - outerPadBottom - ph) * 0.5f);
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

        searchAnim = AnimationUtility.approach(searchAnim, ClickGuiSearch.isActive() ? 1.0f : 0.0f, dt, 10.0f);
        searchAnim = AnimationUtility.snap(searchAnim, ClickGuiSearch.isActive() ? 1.0f : 0.0f, 0.01f);

        layout(areaX, areaY, areaW, areaH);

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
            return true;
        }

        for (ModulesMenuPanel panel : panels) {
            if (!inside(mouseX, mouseY, panel.x, panel.y, panel.w, panel.h)) continue;
            return panel.mousePressed(mouseX, mouseY, button);
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && ClickGuiSearch.isActive()) {
            ClickGuiSearch.setActive(false);
            return true;
        }

        return false;
    }

    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (!isInteractive()) return;

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

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                ClickGuiSearch.backspace();
                return true;
            }
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
        for (ModulesMenuPanel panel : panels) {
            panel.selected = null;
            panel.selectedTitle = null;
            panel.selectedSettings.clear();
            panel.settingsScroll = 0.0f;
            panel.settingsSmoothScroll = 0.0f;
            panel.modulesScroll = 0.0f;
            panel.modulesSmoothScroll = 0.0f;
            panel.swap = 0.0f;
            panel.hits.clear();
            panel.settingHits.clear();
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

        float titleY = panel.y + middle(textHeight(semibold, 9.0f * scale), HEADER_H * scale) + 0.5f * scale;
        ClickGuiRenderer.drawText(semibold, panel.category.title(), panel.x + TEXT_LEFT_PADDING * scale, titleY, 9.0f * scale, text, false);
        drawCategoryIcon(panel, muted);

        if (panel.swap < 0.999f) {
            renderModulePage(panel, mouseX, mouseY, alpha * (1.0f - panel.swap));
        }

        if (panel.swap > 0.001f) {
            renderSettingsPage(panel, mouseX, mouseY, alpha * panel.swap);
        }

        LayoutRender2D.rect(
                panel.x + 8.0f * scale,
                panel.y + HEADER_H * scale,
                panel.w - 16.0f * scale,
                0.5f * scale,
                split
        );
    }

    private void renderModulePage(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.001f) return;

        float pageX = panel.x - panel.w * panel.swap;
        float listY = panel.y + (HEADER_H + SEPARATOR_H - 1.0f) * scale;
        float clipY = panel.y + (HEADER_H + SEPARATOR_H) * scale;
        float clipH = panel.h - (HEADER_H + SEPARATOR_H) * scale - 0.5f * scale;

        boolean clipped = ScissorFunction.pushRaw(panel.x, clipY, panel.w, clipH);
        ClickGuiRenderer.flushRenderer();
        float listW = Math.max(1.0f, panel.w - 7.0f * scale);

        float y = listY - panel.modulesSmoothScroll;
        float total = 0.0f;

        List<ModuleComponent.CardEntry> entries = ModulesMenuResolver.buildCards(panel.category);
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
        float nameY = y + middle(textHeight(regular, 8.0f * scale), h) - 0.5f * scale;
        float nameMaxW = moduleListEdit
                ? Math.max(12.0f * scale, moduleListX - nameX - 5.0f * scale)
                : Math.max(12.0f * scale, rowW - (nameX - x) - 18.0f * scale);
        String visibleLabel = ClickGuiRenderer.fitText(regular, label, 8.0f * scale, nameMaxW);

        if (failed) {
            SettingErrorView.warning(x+rowW-15f*scale,y+5f*scale,9f*scale,alpha);
        }
        ClickGuiRenderer.drawText(regular, visibleLabel, nameX, nameY, 8.0f * scale, nameColor, false);

        if (moduleListEdit) {
            int listColor = entry.shownInModuleList() ? ModulesMenuStyle.MODULE_LIST_ON : ModulesMenuStyle.MODULE_LIST_OFF;
            ClickGuiRenderer.drawText(semibold, moduleListLabel, moduleListX, moduleListY, moduleListTextSize, withAlpha(listColor, alpha), false);
        }

        int divider = withAlpha(ModulesMenuStyle.text(), alpha * 0.02f);
        LayoutRender2D.rect(x + 3.0f * scale, y + h, Math.max(1f, rowW - 6.0f * scale), 0.5f * scale, divider);

        panel.hits.add(new ModulesMenuPanel.ModuleHit(entry.getId(), x, y, rowW, h, entry.hasSettings(), entry.toggleable()));
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

        float clipTop = panel.y + (HEADER_H + SEPARATOR_H) * scale;
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

    private void renderSettingsPage(ModulesMenuPanel panel, float mouseX, float mouseY, float alpha) {
        if (alpha <= 0.001f) return;

        float pageX = panel.x + panel.w * (1.0f - panel.swap);
        float settingsPadLeft = 6.0f * scale;
        float settingsPadRight = 13.0f * scale;
        float settingsX = pageX + settingsPadLeft;
        float settingsW = Math.max(1.0f, panel.w - settingsPadLeft - settingsPadRight);
        float settingsTop = panel.y + (HEADER_H + SEPARATOR_H) * scale;
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
                settingsTop + middle(textHeight(regular, 8.0f * scale), HEADER_H * scale) - scale,
                8.0f * scale,
                withAlpha(ModulesMenuStyle.text(), alpha),
                false
        );

        } finally { if (headerClip) ScissorFunction.pop(); }

        float clipY = settingsTop + HEADER_H * scale;
        float clipH = panel.h - HEADER_H * 2.0f * scale - SEPARATOR_H * scale - 0.5f * scale - 5.0f * scale;

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

        ClickGuiRenderer.drawText(
                regular,
                draw,
                searchX + 8.0f * scale,
                searchY + middle(textHeight(regular, 8.0f * scale), searchH),
                8.0f * scale,
                color,
                false
        );
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
        Renderer2D.COLOR.liquidGlassRect(
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
        );
    }

    private void drawCategoryIcon(ModulesMenuPanel panel, int color) {
        float icon = 8.0f * scale;
        float ix = panel.x + panel.w - 10.0f * scale - icon;
        float iy = panel.y + middle(icon, HEADER_H * scale) + 0.5f * scale;

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
            fontGeneration = generation;
        }
        if (regular == null)
            regular = Fonts.renderer("Onest", FontInfo.Type.Regular, ClickGuiRenderer.getInterRegular());
        if (medium == null) medium = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, regular);
        if (semibold == null) semibold = Fonts.renderer("OnestBold", FontInfo.Type.Regular, medium);
    }

    private String bindingLabel(ModuleComponent.CardEntry entry) {
        String pending = ClickGuiRenderer.getPendingBindDisplay();
        if (pending != null) return pending;

        String bind = entry.bindLabel();
        return bind == null || bind.isBlank() ? "Binding..." : "Key: " + bind;
    }
}
