/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.*;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.input.KeyManager;

import java.util.ArrayList;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 220)
public final class Inventory extends DraggableHudElement {

    {
        defaultLayout(1255.352f, 658.36005f);
    }


    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int FIRST_INV_SLOT = 9;
    private static final int LAST_INV_SLOT = 36;

    private static final float BASE_WIDTH = 123.0f;
    private static final float BASE_HEIGHT = 62.0f;
    private static final float HEADER_HEIGHT = 15.5f;
    private static final float BODY_Y_OFFSET = 18.4f;
    private static final float BODY_HEIGHT = 45.0f;
    private static final float PANEL_RADIUS = 4.0f;
    private static final float PANEL_SOFTNESS = 1.0f;
    private static final float PANEL_STROKE = 0.55f;
    private static final float HEADER_DIVIDER_X = 18.0f;
    private static final float HEADER_DIVIDER_Y = 5.0f;
    private static final float HEADER_DIVIDER_H = 6.0f;
    private static final float TITLE_ICON_X = 4.5f;
    private static final float TITLE_ICON_Y = 6.0f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float TITLE_TEXT_Y = 6.5f;
    private static final float COUNT_LABEL_OFFSET = 21.0f;
    private static final float COUNT_VALUE_OFFSET = 2.0f;
    private static final float GRID_START_X = 4.0f;
    private static final float GRID_START_Y = 22.0f;
    private static final float GRID_STEP = 13.0f;
    private static final float GRID_LINE_OFFSET_X = 11.0f;
    private static final float GRID_LINE_OFFSET_Y = 10.0f;
    private static final float GRID_LINE_LENGTH = 9.0f;
    private static final float GRID_LINE_THICKNESS = 0.5f;
    private static final float ITEM_RENDER_SCALE = 0.5f;

    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedInventoryHudPanel scriptedPanel = new ScriptedInventoryHudPanel();

    private final KeyBindValue toggleBind =
            new KeyBindValue("toggle_bind", "I");
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("inventory_scale", 1.32, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("inventory_layout", "Unified Divider", HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final ModeValue colorMode =
            new ModeValue("inventory_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("inventory_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("inventory_theme_gradient_strength", 72, 0, 100);
    private final BooleanValue strokeEnabled =
            new BooleanValue("inventory_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("inventory_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("inventory_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("inventory_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("inventory_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("inventory_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("inventory_shadow_alpha", 38, 0, 255);
    private final ModeValue bgEffect =
            new ModeValue("inventory_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("inventory_bg", "#F7343434");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("inventory_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("inventory_bg_alpha", 194, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("inventory_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("inventory_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("inventory_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("inventory_blur_alpha", 255, 0, 255);
    private final BooleanValue headerIconPulse =
            new BooleanValue("inventory_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("inventory_header_icon_pulse_speed", 15, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("inventory_header_icon_pulse_intensity", 100, 0, 100);

    private final List<QueuedItemIcon> pendingItemTasks = new ArrayList<>(COLS * ROWS);

    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private float visibilityAnim = 0.0f;
    private int uiHeaderLeft;
    private int uiHeaderRight;
    private int uiBodyLeft;
    private int uiBodyRight;
    private int uiOutline;
    private int uiText;
    private int uiMuted;
    private int uiCounter;
    private int uiTitleText;
    private int uiDivider;
    private int uiGridDivider;
    private int uiBlurTint;
    private boolean layoutReady;
    private boolean hitboxActive;
    private boolean displayVisible = true;
    private boolean toggleArmed = true;
    private boolean toggleInit = false;

    public Inventory() {
        super("inventory", "Inventory", true);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bind(toggleBind, BindMode.PRESS));
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.mode(layoutMode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.number(themeGradientStrength).visibleWhen(this::isGradientPanelStyle));
        defs.add(SettingDef.color(bg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bg2).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(() -> shadowEnabled.get() && isThemeMode()));
        defs.add(SettingDef.number(themeShadowStrength).visibleWhen(this::isThemeShadow));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.colorNoAlpha(text).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(muted).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.bool(headerIconPulse));
        defs.add(SettingDef.number(headerIconPulseSpeed).visibleWhen(headerIconPulse::get));
        defs.add(SettingDef.number(headerIconPulseIntensity).visibleWhen(headerIconPulse::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 385.0f;
        this.y = 40.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void onTick() {
        if (!toggleInit) {
            if (mc == null || mc.getWindow() == null) {
                return;
            }
            initToggleState();
            toggleInit = true;
        }

        String combo = toggleBind.get();
        if (combo == null || combo.isBlank() || "NONE".equalsIgnoreCase(combo)) {
            return;
        }
        if (mc == null || mc.getWindow() == null) {
            return;
        }

        boolean pressed = ClientScreen.current() == null && KeyManager.isComboHeldAllowScreen(combo);
        if (!pressed) {
            toggleArmed = true;
            return;
        }
        if (toggleArmed) {
            displayVisible = !displayVisible;
            toggleArmed = false;
        }
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        layoutReady = false;
        hitboxActive = false;
        pendingItemTasks.clear();

        boolean forceVisible = DraggableHudElementRegistry.isForceVisible();
        if (mc == null || (mc.player == null && !forceVisible)) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        if (!isEnabled() && !forceVisible) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        boolean chatPreview = ClientScreen.current() instanceof ChatScreen;
        List<ItemStack> stacks = collectStacks();
        boolean hasAnyItems = stacks.stream().anyMatch(stack -> stack != null && !stack.isEmpty());
        boolean displayTargetVisible = displayVisible;
        boolean showWidget = forceVisible || (displayTargetVisible && (chatPreview || hasAnyItems));
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);
        if (!showWidget && visibilityAnim <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();

        TextRenderer headerIconRenderer = Fonts.renderer("IconsNur", FontInfo.Type.Regular, TextRenderer.get());
        TextRenderer headerTextRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer countRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        if (headerIconRenderer == null) headerIconRenderer = textRenderer;
        if (headerTextRenderer == null) headerTextRenderer = textRenderer;
        if (countRenderer == null) countRenderer = textRenderer;

        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * HudPanelLayoutModes.effectiveScale(scaleValue);
        float fontScale = 0.98f * (hud.getFontSize() / 18.0f);
        long itemCount = 0L;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                itemCount += stack.getCount();
            }
        }

        countRenderer.begin(fontScale * 0.92f, false, false);
        String itemCountText = Long.toString(Math.max(0L, itemCount));
        float countLabelWidth = (float) countRenderer.getWidth("Items:", false);
        float countValueWidth = (float) countRenderer.getWidth(itemCountText, false);
        float countTextHeight = (float) countRenderer.getHeight(false);
        countRenderer.end();

        headerIconRenderer.begin(fontScale, false, false);
        headerTextRenderer.begin(fontScale, false, false);
        float headerIconH = (float) headerIconRenderer.getHeight(false);
        float headerTextH = (float) headerTextRenderer.getHeight(false);
        float titleWidth = (float) headerTextRenderer.getWidth("Inventory", false);
        headerTextRenderer.end();
        headerIconRenderer.end();

        float targetWidth = BASE_WIDTH * baseScale;
        float headerWidth = (TITLE_TEXT_X * baseScale) + titleWidth
                + countLabelWidth + countValueWidth
                + ((COUNT_LABEL_OFFSET + 12.0f) * baseScale);
        targetWidth = Math.max(targetWidth, headerWidth);
        float targetHeight = BASE_HEIGHT * baseScale;

        displayWidth = HudRenderUtil.animateDimension(displayWidth, targetWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        float drawScale = Math.max(0.0f, widgetScale);
        float drawWidth = displayWidth * drawScale;
        float drawHeight = displayHeight * drawScale;
        if (drawWidth <= 0.0f || drawHeight <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        float drawX = x + ((displayWidth - drawWidth) * 0.5f);
        float drawY = y + ((displayHeight - drawHeight) * 0.5f);
        float drawBaseScale = baseScale * drawScale;
        float drawFontScale = fontScale * drawScale;
        float drawHeaderIconH = headerIconH * drawScale;
        float drawHeaderTextH = headerTextH * drawScale;
        float drawCountTextHeight = countTextHeight * drawScale;
        float drawCountLabelWidth = countLabelWidth * drawScale;
        float drawCountValueWidth = countValueWidth * drawScale;
        hitboxActive = forceVisible || showWidget;

        int resolvedHeaderIconColor = uiCounter;
        if (headerIconPulse.get()) {
            int pulsedHeaderIconColor = HudTextEffects.animatedColor(
                    uiCounter,
                    HudTextEffects.Effect.PULSE,
                    headerIconPulseSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f
            );
            resolvedHeaderIconColor = HudRenderUtil.mixColor(
                    uiCounter,
                    pulsedHeaderIconColor,
                    headerIconPulseIntensity.get() / 100.0f
            );
        }

        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, drawX, drawY, drawWidth, drawHeight, PANEL_RADIUS * drawBaseScale, drawBaseScale,
                    isThemeShadow(),
                    shadowAlpha.get(), drawScale,
                    themeShadowStrength.get() / 100.0f
            );
        }

        scriptedPanel.render(
                renderer,
                textRenderer,
                ctx,
                tickDelta,
                new ScriptedInventoryHudPanel.Panel(
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        drawScale,
                        drawBaseScale,
                        drawFontScale,
                        drawHeaderIconH,
                        drawHeaderTextH,
                        drawCountTextHeight,
                        drawCountLabelWidth,
                        drawCountValueWidth,
                        itemCount,
                        hasEffect(),
                        Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f)),
                        strokeEnabled.get(),
                        strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(),
                        resolveStrokeGradientEnd(),
                        resolvedHeaderIconColor,
                        uiGridDivider,
                        HudPanelLayoutModes.current(layoutMode),
                        new ScriptedListHudPanel.Palette(uiHeaderLeft, uiHeaderRight, uiBodyLeft, uiBodyRight,
                                uiOutline, uiText, uiMuted, uiCounter, uiTitleText, uiDivider, uiBlurTint),
                        ScriptedInventoryHudPanel.cells(stacks)
                )
        );
    }

    @Override
    public void renderEngineForeground(Renderer2D renderer,
                                       TextRenderer textRenderer,
                                       GuiGraphicsExtractor ctx,
                                       float tickDelta,
                                       int screenW,
                                       int screenH) {
        if (!layoutReady || ctx == null || pendingItemTasks.isEmpty()) {
            pendingItemTasks.clear();
            return;
        }
        for (QueuedItemIcon task : pendingItemTasks) {
            // Inventory is rendered in UNSCALED_LOGICAL space, so item() must receive
            // logical coordinates directly. itemUnscaled() would shift icons out of slots.
            renderer.item(task.stack(), task.x(), task.y(), task.scale(), task.seed(), Renderer2D.ITEM_OVERLAY_ALL, null);
        }
        pendingItemTasks.clear();
    }

    @Override
    public boolean contains(float mx, float my) {
        if (!hitboxActive && !DraggableHudElementRegistry.isForceVisible()) {
            return false;
        }
        return super.contains(mx, my);
    }

    private List<ItemStack> collectStacks() {
        List<ItemStack> stacks = new ArrayList<>(LAST_INV_SLOT - FIRST_INV_SLOT);
        if (mc == null || mc.player == null) {
            return stacks;
        }
        for (int slot = FIRST_INV_SLOT; slot < LAST_INV_SLOT; slot++) {
            stacks.add(mc.player.getInventory().getItem(slot));
        }
        return stacks;
    }

    private void updatePalette() {
        if (isThemeMode()) {
            int alpha = bgAlpha.get();
            int window = HudRenderUtil.setAlpha(theme().windowBg(), alpha);
            int header = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowHeader(), theme().surface(), 0.18f),
                    Math.min(255, alpha + 14)
            );
            int surface = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowBg(), 0.22f),
                    Math.min(255, alpha + 6)
            );
            int deep = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().surface(), theme().windowHeader(), 0.42f),
                    Math.min(255, alpha + 18)
            );

            uiHeaderLeft = HudRenderUtil.mixColor(header, window, 0.22f);
            uiHeaderRight = HudRenderUtil.mixColor(surface, header, 0.52f);
            uiBodyLeft = HudRenderUtil.mixColor(window, surface, 0.24f);
            uiBodyRight = HudRenderUtil.mixColor(deep, surface, 0.18f);
            if (isAccentPanelStyle()) {
                uiHeaderLeft = HudRenderUtil.accentSurface(uiHeaderLeft, 0.20f);
                uiHeaderRight = HudRenderUtil.accentSurface(uiHeaderRight, 0.27f);
                uiBodyLeft = HudRenderUtil.accentSurface(uiBodyLeft, 0.16f);
                uiBodyRight = HudRenderUtil.accentSurface(uiBodyRight, 0.24f);
            } else if (isGradientPanelStyle()) {
                float strength = themeGradientStrength.get() / 100.0f;
                HudRenderUtil.ThemeGradient gradient = HudRenderUtil.themePanelGradient(255);
                uiHeaderLeft = HudRenderUtil.gradientSurface(uiHeaderLeft, gradient.start(), strength);
                uiHeaderRight = HudRenderUtil.gradientSurface(uiHeaderRight, gradient.end(), strength);
                uiBodyLeft = HudRenderUtil.gradientSurface(uiBodyLeft, gradient.start(), strength * 0.92f);
                uiBodyRight = HudRenderUtil.gradientSurface(uiBodyRight, gradient.end(), strength);
            }
            uiOutline = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowStroke(), theme().strokeSoft(), 0.18f),
                    Math.min(255, Math.max(182, alpha + 36))
            );
            uiText = theme().textPrimary();
            uiTitleText = HudRenderUtil.mixColor(theme().textPrimary(), theme().accent(), 0.14f);
            uiMuted = HudRenderUtil.mixColor(theme().textMuted(), theme().textPrimary(), 0.16f);
            uiCounter = HudRenderUtil.mixColor(theme().accent(), theme().textPrimary(), 0.38f);
            uiDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.68f);
            uiGridDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.28f);
            uiBlurTint = HudRenderUtil.mixColor(
                    HudRenderUtil.mixColor(uiHeaderRight, uiBodyRight, 0.5f),
                    theme().accent(),
                    0.14f
            );
            return;
        }

        uiHeaderLeft = bg.getArgb();
        uiHeaderRight = bg2.getArgb();
        uiBodyLeft = HudRenderUtil.mixColor(bg.getArgb(), bg2.getArgb(), 0.15f);
        uiBodyRight = bg2.getArgb();
        uiOutline = stroke.getArgb();
        uiText = text.getArgb();
        uiTitleText = uiText;
        uiMuted = muted.getArgb();
        uiCounter = HudRenderUtil.mixColor(text.getArgb(), 0xFFE1E1FF, 0.20f);
        uiDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.55f);
        uiGridDivider = HudRenderUtil.scaleAlpha(uiMuted, 0.20f);
        uiBlurTint = HudRenderUtil.mixColor(uiHeaderLeft, uiBodyRight, 0.5f);
    }

    private boolean isAccentPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get());
    }

    private boolean isGradientPanelStyle() {
        return isThemeMode() && HudRenderUtil.PANEL_STYLE_GRADIENT.equals(panelStyle.get());
    }

    private boolean isThemeShadow() {
        return shadowEnabled.get() && isThemeMode()
                && HudRenderUtil.SHADOW_MODE_THEME.equals(shadowMode.get());
    }

    private int resolveStrokeGradientStart() {
        if (isThemeMode()) return HudRenderUtil.themeAccentGradient(255).start();
        return stroke.getArgb() | 0xFF000000;
    }

    private int resolveStrokeGradientEnd() {
        if (isThemeMode()) return HudRenderUtil.themeAccentGradient(255).end();
        return stroke.getArgb() | 0xFF000000;
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private void initToggleState() {
        toggleArmed = true;
        String combo = toggleBind.get();
        if (combo != null && !combo.isBlank() && !"NONE".equalsIgnoreCase(combo)
                && mc != null && ClientScreen.current() == null
                && KeyManager.isComboHeldAllowScreen(combo)) {
            toggleArmed = false;
        }
    }

    private record QueuedItemIcon(ItemStack stack, float x, float y, float scale, int seed) {
    }
}
