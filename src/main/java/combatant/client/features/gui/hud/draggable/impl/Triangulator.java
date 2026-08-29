/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.SettingDef;
import combatant.client.config.values.*;
import combatant.client.features.gui.hud.HudElementInfo;
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
import combatant.client.util.text.ClipboardUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.*;

import static combatant.client.features.theme.Theme.theme;

@HudElementInfo(
        id = "triangulator",
        displayName = "Triangulator",
        enabledByDefault = false,
        order = 940
)
public final class Triangulator extends DraggableHudElement {

    // Baked HUD layout default from user cfg.
    {
        defaultLayout(1302.04f, 330.69208f);
    }


    private static final Identifier HEADER_ICON =
            Identifier.fromNamespaceAndPath("combatant", "textures/hud/elements/coords.png");

    private static final float HEADER_HEIGHT = 15.5f;
    private static final float BODY_Y_OFFSET = 16.5f;
    private static final float BODY_INSET_Y = 6.5f;
    private static final float CONTENT_START_Y = 23.0f;
    private static final float ROW_STEP = 11.0f;
    private static final float MIN_WIDTH = 126.0f;
    private static final float PANEL_RADIUS = 4.0f;
    private static final float PANEL_SOFTNESS = 1.0f;
    private static final float PANEL_STROKE = 0.55f;
    private static final float HEADER_DIVIDER_X = 18.0f;
    private static final float HEADER_DIVIDER_Y = 5.0f;
    private static final float HEADER_DIVIDER_H = 6.0f;
    private static final float TITLE_ICON_X = 4.5f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float CLEAR_BUTTON_SIZE = 9.0f;
    private static final float CLEAR_BUTTON_RIGHT = 8.0f;
    private static final float HEADER_BUTTON_GAP = 3.0f;
    private static final int COPY_COORD_Y = 64;
    private static final float COUNT_LABEL_OFFSET = 28.0f;
    private static final float COUNT_VALUE_OFFSET = 4.0f;
    private static final float SUMMARY_LINE_1_Y = 0.0f;
    private static final float SUMMARY_LINE_2_Y = 11.0f;
    private static final float SUMMARY_LINE_3_Y = 20.5f;
    private static final float SUMMARY_HEIGHT = 30.5f;
    private static final float SUMMARY_DIVIDER_Y = 34.5f;
    private static final float SUMMARY_DIVIDER_INSET_X = 8.0f;
    private static final float SUMMARY_DIVIDER_H = 0.55f;
    private static final float ROW_DIVIDER_X = 14.5f;
    private static final float ROW_DIVIDER_H = 6.5f;
    private static final float ROW_TEXT_X = 19.0f;
    private static final float ROW_VALUE_RIGHT = 8.0f;
    private static final float ROW_CONTENT_CENTER_OFFSET = 1.95f;
    private static final float ROW_MARKER_X = 4.0f;
    private static final float ROW_MARKER_SIZE = 7.5f;
    private static final float SEARCH_RANGE = 196.0f;

    private static final double MIN_HORIZONTAL_SPEED = 0.03;
    private static final double CAPTURE_DEVIATION_LIMIT = 0.35;
    private static final double CAPTURE_QUALITY_FLOOR = 0.55;
    private static final double MIN_SOLVABLE_ANGLE_DEG = 0.05;
    private static final long RESET_FLASH_MS = 3000L;

    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";

    private static final List<CachedThrow> PREVIEW_THROWS = List.of(
            new CachedThrow(1L, 110.0, 58.0, 0.9238795, -0.3826834, 0.91, 0.996, 0.08, 8, 0L),
            new CachedThrow(2L, 188.0, -24.0, 0.7071067, -0.7071067, 0.87, 0.992, 0.12, 7, 0L),
            new CachedThrow(3L, 254.0, -102.0, 0.4713967, -0.8819212, 0.82, 0.989, 0.19, 6, 0L)
    );
    private static final SolveResult PREVIEW_RESULT = solveStatic(PREVIEW_THROWS, 6.0);

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedTriangulatorHudPanel scriptedPanel = new ScriptedTriangulatorHudPanel();

    private final NumberValue<Double> scaleValue =
            new NumberValue<>("triangulator_scale", 1.68, 0.5, 5.0);
    private final ModeValue colorMode =
            new ModeValue("triangulator_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("triangulator_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("triangulator_theme_gradient_strength", 72, 0, 100);
    private final BooleanValue strokeEnabled =
            new BooleanValue("triangulator_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("triangulator_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("triangulator_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("triangulator_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("triangulator_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("triangulator_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("triangulator_shadow_alpha", 38, 0, 255);
    private final ModeValue bgEffect =
            new ModeValue("triangulator_bg_effect", "Blur", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("triangulator_bg", "#F7343434");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("triangulator_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("triangulator_bg_alpha", 225, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("triangulator_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("triangulator_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("triangulator_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("triangulator_blur_alpha", 140, 0, 255);
    private final EnumValue<HudTextEffects.Effect> iconEffect =
            new EnumValue<>("triangulator_icon_effect", HudTextEffects.Effect.NONE,
                    HudTextEffects.Effect.NONE, HudTextEffects.Effect.MIX, HudTextEffects.Effect.FLOW,
                    HudTextEffects.Effect.PULSE, HudTextEffects.Effect.STRIPE);
    private final NumberValue<Integer> iconEffectSpeed =
            new NumberValue<>("triangulator_icon_effect_speed", 18, 1, 60);
    private final NumberValue<Integer> minObservationTicks =
            new NumberValue<>("triangulator_min_observation_ticks", 6, 2, 20);
    private final NumberValue<Double> stabilityThreshold =
            new NumberValue<>("triangulator_stability_threshold", 0.99, 0.900, 0.999);
    private final NumberValue<Double> minPairAngleDeg =
            new NumberValue<>("triangulator_min_pair_angle", 6.0, 0.25, 35.0);
    private final NumberValue<Double> autoResetDistance =
            new NumberValue<>("triangulator_auto_reset_distance", 96.0, 16.0, 512.0);
    private final NumberValue<Integer> maxCachedThrows =
            new NumberValue<>("triangulator_max_cached_throws", 6, 2, 12);
    private final NumberValue<Integer> cacheLifetimeSec =
            new NumberValue<>("triangulator_cache_lifetime", 180, 15, 1800);
    private final BooleanValue autoReset =
            new BooleanValue("triangulator_auto_reset", true);

    private final Map<Integer, TrackedEye> activeEyes = new LinkedHashMap<>();
    private final List<CachedThrow> cachedThrows = new ArrayList<>();

    private float displayWidth = -1.0f;
    private float displayHeight = -1.0f;
    private float visibilityAnim = 0.0f;
    private float clearButtonX;
    private float clearButtonY;
    private float clearButtonSize;
    private float copyButtonX;
    private float copyButtonY;
    private float copyButtonSize;

    private long nextCaptureSerial = 1L;
    private long lastWorldTime = Long.MIN_VALUE;
    private long lastCaptureMs = 0L;
    private long lastResetMs = 0L;
    private String lastResetReason = "";
    private SolveResult currentResult;

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
    private int uiBlurTint;
    private int uiSuccess;
    private int uiWarn;
    private int uiDanger;

    public Triangulator() {
        super("triangulator", "Triangulator", false);
    }

    private static SolveResult solveStatic(List<CachedThrow> throwsToSolve, double minAngleThreshold) {
        if (throwsToSolve == null || throwsToSolve.isEmpty()) {
            return null;
        }
        if (throwsToSolve.size() == 1) {
            CachedThrow only = throwsToSolve.get(0);
            return new SolveResult(
                    false,
                    false,
                    only.pointX(),
                    only.pointZ(),
                    0.0,
                    0.0,
                    only.quality(),
                    Mth.floor(only.pointX() / 16.0),
                    Mth.floor(only.pointZ() / 16.0)
            );
        }

        double qualitySum = 0.0;
        double strongestAngle = 0.0;
        double pairX = 0.0;
        double pairZ = 0.0;
        double pairWeightSum = 0.0;
        double crossThreshold = Math.sin(Math.toRadians(MIN_SOLVABLE_ANGLE_DEG));

        for (int i = 0; i < throwsToSolve.size(); i++) {
            CachedThrow throwI = throwsToSolve.get(i);
            qualitySum += throwI.quality();

            for (int j = i + 1; j < throwsToSolve.size(); j++) {
                CachedThrow throwJ = throwsToSolve.get(j);
                double dot = Math.abs(throwI.dirX() * throwJ.dirX() + throwI.dirZ() * throwJ.dirZ());
                double angle = Math.toDegrees(Math.acos(Mth.clamp(dot, -1.0, 1.0)));
                strongestAngle = Math.max(strongestAngle, angle);

                double cross = throwI.dirX() * throwJ.dirZ() - throwI.dirZ() * throwJ.dirX();
                if (Math.abs(cross) <= crossThreshold) {
                    continue;
                }

                double deltaX = throwJ.pointX() - throwI.pointX();
                double deltaZ = throwJ.pointZ() - throwI.pointZ();
                double t = (deltaX * throwJ.dirZ() - deltaZ * throwJ.dirX()) / cross;
                double intersectionX = throwI.pointX() + throwI.dirX() * t;
                double intersectionZ = throwI.pointZ() + throwI.dirZ() * t;
                double pairWeight = (0.35 + throwI.quality() * 0.65)
                        * (0.35 + throwJ.quality() * 0.65)
                        * Mth.clamp(0.35 + angle / Math.max(0.35, minAngleThreshold), 0.35, 4.0);

                pairX += intersectionX * pairWeight;
                pairZ += intersectionZ * pairWeight;
                pairWeightSum += pairWeight;
            }
        }

        if (pairWeightSum <= 1.0e-8) {
            return new SolveResult(false, false, 0.0, 0.0, Double.POSITIVE_INFINITY, strongestAngle, 0.0, 0, 0);
        }

        double x = pairX / pairWeightSum;
        double z = pairZ / pairWeightSum;
        double errorSum = 0.0;
        for (CachedThrow cachedThrow : throwsToSolve) {
            double d = distanceToLine(x, z, cachedThrow);
            errorSum += d * d;
        }
        double rmse = Math.sqrt(errorSum / throwsToSolve.size());
        double quality = qualitySum / throwsToSolve.size();
        double effectiveAngleThreshold = Math.max(0.35, Math.min(minAngleThreshold, 2.0));
        double angleScore = Mth.clamp(
                (strongestAngle - effectiveAngleThreshold) / Math.max(0.5, 12.0 - effectiveAngleThreshold),
                0.0,
                1.0
        );
        double errorScore = Mth.clamp(1.0 - rmse / 64.0, 0.0, 1.0);
        double countBonus = Mth.clamp((throwsToSolve.size() - 1.0) / 3.0, 0.0, 1.0);
        double confidence = Mth.clamp(
                quality * 0.45 + angleScore * 0.25 + errorScore * 0.20 + countBonus * 0.10,
                0.0,
                1.0
        );
        boolean ready = strongestAngle >= effectiveAngleThreshold && rmse <= 96.0 && confidence >= 0.55;
        return new SolveResult(
                true,
                ready,
                x,
                z,
                rmse,
                strongestAngle,
                confidence,
                Mth.floor(x / 16.0),
                Mth.floor(z / 16.0)
        );
    }

    private static double distanceToLine(double x, double z, CachedThrow cachedThrow) {
        double nx = -cachedThrow.dirZ();
        double nz = cachedThrow.dirX();
        return Math.abs((x - cachedThrow.pointX()) * nx + (z - cachedThrow.pointZ()) * nz);
    }

    private static String formatPercent(double value) {
        return Math.round(Mth.clamp(value, 0.0, 1.0) * 100.0) + "%";
    }

    private static String formatThrowPoint(CachedThrow cachedThrow) {
        return String.format(Locale.ROOT, "%d %d",
                Math.round(cachedThrow.pointX()),
                Math.round(cachedThrow.pointZ()));
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scaleValue));
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
        defs.add(SettingDef.mode(iconEffect));
        defs.add(SettingDef.number(iconEffectSpeed)
                .visibleWhen(() -> iconEffect.get() != HudTextEffects.Effect.NONE));
        defs.add(SettingDef.number(minObservationTicks));
        defs.add(SettingDef.number(stabilityThreshold));
        defs.add(SettingDef.number(minPairAngleDeg));
        defs.add(SettingDef.bool(autoReset));
        defs.add(SettingDef.number(autoResetDistance).visibleWhen(autoReset::get));
        defs.add(SettingDef.number(maxCachedThrows));
        defs.add(SettingDef.number(cacheLifetimeSec));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 184.0f;
        this.y = 140.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public boolean isMouseOverInteractive(float mx, float my) {
        return (clearButtonSize > 0.0f
                && cachedThrows.size() > 0
                && hit(mx, my, clearButtonX, clearButtonY, clearButtonSize, clearButtonSize))
                || (copyButtonSize > 0.0f
                && currentCopyResult() != null
                && hit(mx, my, copyButtonX, copyButtonY, copyButtonSize, copyButtonSize));
    }

    @Override
    public boolean onMouseClicked(float mx, float my, int button) {
        if (button != 0) {
            return false;
        }
        SolveResult copyResult = currentCopyResult();
        if (copyButtonSize > 0.0f
                && copyResult != null
                && hit(mx, my, copyButtonX, copyButtonY, copyButtonSize, copyButtonSize)) {
            ClipboardUtil.copy(formatCopyCoordinates(copyResult));
            return true;
        }
        if (clearButtonSize > 0.0f
                && cachedThrows.size() > 0
                && hit(mx, my, clearButtonX, clearButtonY, clearButtonSize, clearButtonSize)) {
            clearAllState("Manual clear", true, System.currentTimeMillis());
            return true;
        }
        return false;
    }

    private SolveResult currentCopyResult() {
        SolveResult result = currentResult != null ? currentResult : solve(cachedThrows);
        return result != null && result.hasSolution() ? result : null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            activeEyes.clear();
            return;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            activeEyes.clear();
            currentResult = solve(cachedThrows);
            return;
        }

        long now = System.currentTimeMillis();
        long worldTime = mc.level.getGameTime();
        if (lastWorldTime != Long.MIN_VALUE && worldTime + 200L < lastWorldTime) {
            activeEyes.clear();
        }
        lastWorldTime = worldTime;

        if (cacheLifetimeSec.get() > 0
                && !cachedThrows.isEmpty()
                && activeEyes.isEmpty()
                && (currentResult == null || !currentResult.hasSolution())
                && lastCaptureMs > 0L
                && now - lastCaptureMs > cacheLifetimeSec.get() * 1000L) {
            clearAllState("Cache expired", true, now);
        }

        updateTrackedEyes(now);
        currentResult = solve(cachedThrows);
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (!preview && (!isEnabled() || mc == null || mc.player == null || mc.level == null)) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();

        List<CachedThrow> displayThrows = preview && cachedThrows.isEmpty() ? PREVIEW_THROWS : cachedThrows;
        SolveResult displayResult = preview && cachedThrows.isEmpty() ? PREVIEW_RESULT : currentResult;
        if (displayResult == null && !displayThrows.isEmpty()) {
            displayResult = solve(displayThrows);
        }
        DisplayState state = buildDisplayState(displayThrows, displayResult, preview);

        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, true);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);

        TextRenderer headerTextRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer rowTextRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        if (headerTextRenderer == null) headerTextRenderer = textRenderer;
        if (rowTextRenderer == null) rowTextRenderer = textRenderer;

        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * scaleValue.get().floatValue();
        float fontScale = 0.98f * (hud.getFontSize() / 18.0f);

        headerTextRenderer.begin(fontScale, true, false);
        float headerTextH = (float) headerTextRenderer.getHeight(false);
        float titleWidth = (float) headerTextRenderer.getWidth("Triangulator", false);
        headerTextRenderer.end();

        rowTextRenderer.begin(fontScale, true, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxWidth = MIN_WIDTH * baseScale;
        maxWidth = Math.max(maxWidth, (float) rowTextRenderer.getWidth(state.statusText(), false) + 52.0f * baseScale);
        maxWidth = Math.max(maxWidth, (float) rowTextRenderer.getWidth(state.primaryLine(), false) + 20.0f * baseScale);
        rowTextRenderer.end();

        rowTextRenderer.begin(fontScale * 0.92f, false, false);
        maxWidth = Math.max(maxWidth, (float) rowTextRenderer.getWidth(state.secondaryLine(), false) + 20.0f * baseScale);
        String activeCountText = Integer.toString(displayThrows.size());
        float activeLabelWidth = (float) rowTextRenderer.getWidth("Eyes:", false);
        float activeCountWidth = (float) rowTextRenderer.getWidth(activeCountText, false);
        rowTextRenderer.end();

        for (int i = 0; i < displayThrows.size(); i++) {
            CachedThrow cachedThrow = displayThrows.get(i);
            rowTextRenderer.begin(fontScale, true, false);
            float rowWidth = (float) rowTextRenderer.getWidth("Eye #" + (i + 1), false)
                    + (float) rowTextRenderer.getWidth(formatThrowPoint(cachedThrow), false)
                    + 36.0f * baseScale;
            rowTextRenderer.end();
            maxWidth = Math.max(maxWidth, rowWidth);
        }

        boolean showClearButton = !displayThrows.isEmpty();
        boolean showCopyButton = displayResult != null && displayResult.hasSolution();
        int headerButtons = (showClearButton ? 1 : 0) + (showCopyButton ? 1 : 0);
        float headerButtonsWidth = headerButtons == 0 ? 0.0f
                : ((CLEAR_BUTTON_SIZE * headerButtons) + (HEADER_BUTTON_GAP * Math.max(0, headerButtons - 1)) + 4.0f) * baseScale;
        float headerWidth = (TITLE_TEXT_X * baseScale) + titleWidth
                + activeLabelWidth + activeCountWidth
                + headerButtonsWidth
                + ((COUNT_LABEL_OFFSET + 16.0f) * baseScale);
        maxWidth = Math.max(maxWidth, headerWidth);

        float rowsHeight = displayThrows.isEmpty() ? 0.0f : displayThrows.size() * (ROW_STEP * baseScale);
        float dividerHeight = displayThrows.isEmpty() ? 0.0f : 5.0f * baseScale;
        float targetHeight = (CONTENT_START_Y * baseScale) + (SUMMARY_HEIGHT * baseScale) + dividerHeight + rowsHeight;

        displayWidth = HudRenderUtil.animateDimension(displayWidth, maxWidth);
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
        float drawHeaderTextH = headerTextH * drawScale;
        float drawRowTextH = rowTextH * drawScale;

        float buttonCursorRight = CLEAR_BUTTON_RIGHT * drawBaseScale;
        if (showClearButton) {
            clearButtonSize = CLEAR_BUTTON_SIZE * drawBaseScale;
            clearButtonX = drawX + drawWidth - buttonCursorRight - clearButtonSize;
            clearButtonY = drawY + (((HEADER_HEIGHT * drawBaseScale) - clearButtonSize) * 0.5f);
            buttonCursorRight += clearButtonSize + (HEADER_BUTTON_GAP * drawBaseScale);
        } else {
            clearButtonSize = 0.0f;
        }
        if (showCopyButton) {
            copyButtonSize = CLEAR_BUTTON_SIZE * drawBaseScale;
            copyButtonX = drawX + drawWidth - buttonCursorRight - copyButtonSize;
            copyButtonY = drawY + (((HEADER_HEIGHT * drawBaseScale) - copyButtonSize) * 0.5f);
        } else {
            copyButtonSize = 0.0f;
        }

        int headerIconColor = uiCounter;
        if (iconEffect.get() != HudTextEffects.Effect.NONE) {
            headerIconColor = HudTextEffects.animatedColor(
                    headerIconColor,
                    iconEffect.get(),
                    iconEffectSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f
            );
        }

        HudRenderUtil.ThemeGradient headerIconGradient = isThemeMode()
                ? HudRenderUtil.themeForegroundGradient(255)
                : new HudRenderUtil.ThemeGradient(headerIconColor, headerIconColor, 45.0f);
        if (isThemeMode() && iconEffect.get() != HudTextEffects.Effect.NONE) {
            headerIconGradient = HudTextEffects.animatedGradient(
                    headerIconGradient,
                    iconEffect.get(),
                    iconEffectSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f,
                    1.0f
            );
        }

        List<LinkedHashMap<String, Object>> panelRows = ScriptedTriangulatorHudPanel.rows();
        for (int i = 0; i < displayThrows.size(); i++) {
            CachedThrow cachedThrow = displayThrows.get(i);
            int markerColor = solveMarkerColor(cachedThrow, displayResult, i, displayThrows.size());
            panelRows.add(ScriptedTriangulatorHudPanel.row(
                    "eye:" + cachedThrow.serial(),
                    "Eye #" + (i + 1),
                    formatThrowPoint(cachedThrow),
                    uiText,
                    markerColor,
                    markerColor,
                    HudRenderUtil.scaleAlpha(uiMuted, 0.45f),
                    1.0f
            ));
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
                new ScriptedTriangulatorHudPanel.Panel(
                        new ScriptedTriangulatorHudPanel.Palette(
                                uiHeaderLeft,
                                uiHeaderRight,
                                uiBodyLeft,
                                uiBodyRight,
                                uiOutline,
                                uiText,
                                uiMuted,
                                uiCounter,
                                uiTitleText,
                                uiDivider,
                                uiBlurTint,
                                uiSuccess,
                                uiWarn,
                                uiDanger
                        ),
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        drawScale,
                        drawBaseScale,
                        drawFontScale,
                        drawHeaderTextH,
                        drawRowTextH,
                        activeLabelWidth * drawScale,
                        activeCountWidth * drawScale,
                        displayThrows.size(),
                        hasEffect(),
                        Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f)),
                        strokeEnabled.get(),
                        strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(),
                        resolveStrokeGradientEnd(),
                        ScriptedTriangulatorHudPanel.idString(HEADER_ICON),
                        headerIconColor,
                        isThemeMode(),
                        headerIconGradient.start(),
                        headerIconGradient.end(),
                        headerIconGradient.angleDeg(),
                        showClearButton,
                        uiDanger,
                        showCopyButton,
                        uiCounter,
                        state.statusText(),
                        state.primaryLine(),
                        state.secondaryLine(),
                        state.confidenceText(),
                        state.statusColor(),
                        panelRows
                )
        );
    }

    private static String formatCopyCoordinates(SolveResult result) {
        if (result == null || !result.hasSolution()) {
            return "";
        }
        return String.format(Locale.ROOT, "%d %d %d", Math.round(result.x()), COPY_COORD_Y, Math.round(result.z()));
    }

    private DisplayState buildDisplayState(List<CachedThrow> throwsToShow, SolveResult result, boolean preview) {
        long now = System.currentTimeMillis();
        if (throwsToShow.isEmpty()) {
            if (!lastResetReason.isEmpty() && now - lastResetMs <= RESET_FLASH_MS) {
                return new DisplayState(lastResetReason, "Cache cleared", "Waiting for a new eye", "", uiDanger);
            }
            if (!activeEyes.isEmpty()) {
                return new DisplayState("Tracking eye", "Stabilizing trajectory", "Need one clean capture", "", uiWarn);
            }
            if (preview) {
                return new DisplayState("Solved", formatCoordinateLine(1288.0, -412.0),
                        formatResultLine(1288.0, -412.0, 5.2), "88%", uiSuccess);
            }
            return new DisplayState("Waiting for eye", "Throw an eye of ender", "Second clean eye unlocks result", "", uiMuted);
        }

        if (throwsToShow.size() == 1) {
            CachedThrow first = throwsToShow.get(0);
            return new DisplayState(
                    activeEyes.isEmpty() ? "Need second eye" : "Tracking next eye",
                    String.format(Locale.ROOT, "Q %s  Stability %.3f", formatPercent(first.quality()), first.stability()),
                    "Need another angle for solve",
                    "",
                    uiWarn
            );
        }

        if (result == null || !result.hasSolution()) {
            return new DisplayState(
                    "Angles too close",
                    String.format(Locale.ROOT, "Best pair %.2f deg", result == null ? 0.0 : result.minPairAngleDeg()),
                    "Move farther, then throw again",
                    "",
                    uiWarn
            );
        }

        if (!result.ready()) {
            return new DisplayState(
                    "Low correlation",
                    formatCoordinateLine(result.x(), result.z()),
                    String.format(Locale.ROOT, "%s  Pair %.2f deg", formatResultLine(result.x(), result.z(), result.rmse()), result.minPairAngleDeg()),
                    formatPercent(result.confidence()),
                    uiWarn
            );
        }

        if (!result.ready()) {
            return new DisplayState(
                    "Low correlation",
                    String.format(Locale.ROOT, "X %d  Z %d", Math.round(result.x()), Math.round(result.z())),
                    String.format(Locale.ROOT, "Err %.1fb  Min angle %.1fВ°", result.rmse(), result.minPairAngleDeg()),
                    formatPercent(result.confidence()),
                    uiWarn
            );
        }

        return new DisplayState(
                "Solved",
                formatCoordinateLine(result.x(), result.z()),
                formatResultLine(result.x(), result.z(), result.rmse()),
                formatPercent(result.confidence()),
                uiSuccess
        );
    }

    private void updateTrackedEyes(long now) {
        if (mc == null || mc.player == null || mc.level == null) {
            activeEyes.clear();
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            cleanupMissingEyes(Set.of(), now);
            return;
        }

        AABB searchBox = mc.player.getBoundingBox().inflate(SEARCH_RANGE);
        List<EyeOfEnder> eyes = mc.level.getEntitiesOfClass(
                EyeOfEnder.class,
                searchBox,
                entity -> entity != null && entity.isAlive()
        );

        Set<Integer> seen = new HashSet<>();
        for (EyeOfEnder eye : eyes) {
            seen.add(eye.getId());
            TrackedEye trackedEye = activeEyes.computeIfAbsent(eye.getId(), TrackedEye::new);
            trackedEye.observe(eye, now);

            if (!trackedEye.captured()) {
                TrackedCapture capture = trackedEye.tryCapture(
                        minObservationTicks.get(),
                        stabilityThreshold.get(),
                        CAPTURE_DEVIATION_LIMIT
                );
                if (capture != null) {
                    trackedEye.markCaptured();
                    acceptCapture(capture, now);
                }
            }
        }

        cleanupMissingEyes(seen, now);
    }

    private String formatCoordinateLine(double overworldX, double overworldZ) {
        if (mc != null && mc.level != null && mc.level.dimension() == Level.NETHER) {
            return String.format(Locale.ROOT, "N %d  %d", Math.round(overworldX / 8.0), Math.round(overworldZ / 8.0));
        }
        return String.format(Locale.ROOT, "X %d  Z %d", Math.round(overworldX), Math.round(overworldZ));
    }

    private String formatResultLine(double overworldX, double overworldZ, double rmse) {
        if (mc != null && mc.level != null && mc.level.dimension() == Level.NETHER) {
            return String.format(Locale.ROOT, "OW %d %d  Err %.1fb",
                    Math.round(overworldX), Math.round(overworldZ), rmse);
        }
        return String.format(Locale.ROOT, "Err %.1fb  Chunk %d %d",
                rmse, Mth.floor(overworldX / 16.0), Mth.floor(overworldZ / 16.0));
    }

    private void cleanupMissingEyes(Set<Integer> seen, long now) {
        List<Integer> toRemove = new ArrayList<>();
        for (Map.Entry<Integer, TrackedEye> entry : activeEyes.entrySet()) {
            TrackedEye trackedEye = entry.getValue();
            if (seen.contains(entry.getKey())) {
                continue;
            }
            if (!trackedEye.captured()) {
                TrackedCapture capture = trackedEye.tryCapture(
                        minObservationTicks.get(),
                        stabilityThreshold.get() - 0.01,
                        CAPTURE_DEVIATION_LIMIT * 1.25
                );
                if (capture != null) {
                    trackedEye.markCaptured();
                    acceptCapture(capture, now);
                }
            }
            if (trackedEye.captured() || now - trackedEye.lastSeenMs() > 600L) {
                toRemove.add(entry.getKey());
            }
        }
        for (Integer id : toRemove) {
            activeEyes.remove(id);
        }
    }

    private void acceptCapture(TrackedCapture capture, long now) {
        if (capture.quality() < CAPTURE_QUALITY_FLOOR) {
            return;
        }

        CachedThrow cachedThrow = new CachedThrow(
                nextCaptureSerial++,
                capture.pointX(),
                capture.pointZ(),
                capture.dirX(),
                capture.dirZ(),
                capture.quality(),
                capture.stability(),
                capture.deviation(),
                capture.samples(),
                now
        );

        if (autoReset.get() && !cachedThrows.isEmpty()) {
            SolveResult existing = currentResult != null ? currentResult : solve(cachedThrows);
            if (existing != null && existing.ready()) {
                double mismatch = distanceToLine(existing.x(), existing.z(), cachedThrow);
                if (mismatch > autoResetDistance.get()) {
                    clearAllState("Different stronghold", true, now);
                }
            }
        }

        cachedThrows.add(cachedThrow);
        while (cachedThrows.size() > maxCachedThrows.get()) {
            cachedThrows.remove(0);
        }
        lastCaptureMs = now;
        lastResetReason = "";
        currentResult = solve(cachedThrows);
    }

    private void clearAllState(String reason, boolean rememberReason, long now) {
        activeEyes.clear();
        cachedThrows.clear();
        currentResult = null;
        nextCaptureSerial = 1L;
        lastCaptureMs = 0L;
        if (rememberReason) {
            lastResetReason = reason;
            lastResetMs = now;
        } else {
            lastResetReason = "";
            lastResetMs = 0L;
        }
    }

    private SolveResult solve(List<CachedThrow> throwsToSolve) {
        return solveStatic(throwsToSolve, minPairAngleDeg.get());
    }

    private int solveMarkerColor(CachedThrow cachedThrow, SolveResult result, int index, int total) {
        if (result == null || !result.hasSolution()) {
            return index == total - 1 ? uiWarn : uiCounter;
        }
        double error = distanceToLine(result.x(), result.z(), cachedThrow);
        if (error <= 8.0) {
            return uiSuccess;
        }
        if (error <= autoResetDistance.get() * 0.5) {
            return uiWarn;
        }
        return uiDanger;
    }

    private void drawWidgetBlur(float px,
                                float headerY,
                                float width,
                                float headerHeight,
                                float bodyY,
                                float bodyHeight,
                                float radius) {
        if (!hasEffect() || width <= 0.0f || headerHeight <= 0.0f) {
            return;
        }
        float blurStrength = Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f));
        int blurTint = uiBlurTint & 0x00FFFFFF;
        float blurQuality = hud.getBlurRadius();

        Renderer2D.COLOR.blurComposite(composite -> {
            composite.roundedRectCorners(
                    px, headerY, width, headerHeight,
                    radius, 0.0f, radius, 0.0f,
                    blurQuality, 1.0f, blurStrength, blurTint
            );
            if (bodyHeight > 0.0f) {
                composite.roundedRectCorners(
                        px, bodyY, width, bodyHeight,
                        0.0f, radius, 0.0f, radius,
                        blurQuality, 1.0f, blurStrength, blurTint
                );
            }
        });
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
            uiBlurTint = HudRenderUtil.mixColor(
                    HudRenderUtil.mixColor(uiHeaderRight, uiBodyRight, 0.5f),
                    theme().accent(),
                    0.14f
            );
        } else {
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
            uiBlurTint = HudRenderUtil.mixColor(uiHeaderLeft, uiBodyRight, 0.5f);
        }

        uiSuccess = HudRenderUtil.mixColor(uiCounter, 0xFF55FF93, 0.55f);
        uiWarn = HudRenderUtil.mixColor(uiCounter, 0xFFFFC85A, 0.55f);
        uiDanger = HudRenderUtil.mixColor(uiCounter, 0xFFFF6A6A, 0.62f);
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
        if (isThemeMode()) {
            return HudRenderUtil.themeAccentGradient(255).start();
        }
        return stroke.getArgb() | 0xFF000000;
    }

    private int resolveStrokeGradientEnd() {
        if (isThemeMode()) {
            return HudRenderUtil.themeAccentGradient(255).end();
        }
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

    public XaeroSnapshot getXaeroSnapshot() {
        SolveResult result = currentResult != null ? currentResult : solve(cachedThrows);
        if (result == null || !result.hasSolution()) {
            return null;
        }
        return new XaeroSnapshot(result.x(), result.z(), result.ready(), result.confidence(), cachedThrows.size());
    }

    public record XaeroSnapshot(double overworldX,
                                double overworldZ,
                                boolean ready,
                                double confidence,
                                int eyeCount) {
    }

    private record Observation(double x, double z, double dirX, double dirZ, double speed) {
    }

    private record TrackedCapture(double pointX,
                                  double pointZ,
                                  double dirX,
                                  double dirZ,
                                  double quality,
                                  double stability,
                                  double deviation,
                                  int samples) {
    }

    private record CachedThrow(long serial,
                               double pointX,
                               double pointZ,
                               double dirX,
                               double dirZ,
                               double quality,
                               double stability,
                               double deviation,
                               int samples,
                               long capturedAtMs) {
    }

    private record SolveResult(boolean hasSolution,
                               boolean ready,
                               double x,
                               double z,
                               double rmse,
                               double minPairAngleDeg,
                               double confidence,
                               int chunkX,
                               int chunkZ) {
    }

    private record DisplayState(String statusText,
                                String primaryLine,
                                String secondaryLine,
                                String confidenceText,
                                int statusColor) {
    }

    private static final class TrackedEye {
        private final List<Observation> observations = new ArrayList<>();
        private long lastSeenMs;
        private boolean captured;

        private TrackedEye(int entityId) {
        }

        private void observe(EyeOfEnder eye, long now) {
            if (eye == null) {
                return;
            }
            Vec3 velocity = eye.getDeltaMovement();
            double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (speed < MIN_HORIZONTAL_SPEED) {
                lastSeenMs = now;
                return;
            }
            observations.add(new Observation(eye.getX(), eye.getZ(), velocity.x / speed, velocity.z / speed, speed));
            if (observations.size() > 14) {
                observations.remove(0);
            }
            lastSeenMs = now;
        }

        private TrackedCapture tryCapture(int minTicks, double stabilityThreshold, double deviationLimit) {
            if (captured || observations.size() < Math.max(2, minTicks)) {
                return null;
            }

            double dirX = 0.0;
            double dirZ = 0.0;
            double avgSpeed = 0.0;
            for (Observation observation : observations) {
                dirX += observation.dirX();
                dirZ += observation.dirZ();
                avgSpeed += observation.speed();
            }
            avgSpeed /= observations.size();

            double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (dirLen <= 1.0e-6) {
                return null;
            }
            dirX /= dirLen;
            dirZ /= dirLen;

            double centroidX = 0.0;
            double centroidZ = 0.0;
            double avgCos = 0.0;
            double minCos = 1.0;
            for (Observation observation : observations) {
                centroidX += observation.x();
                centroidZ += observation.z();
                double dot = Mth.clamp(observation.dirX() * dirX + observation.dirZ() * dirZ, -1.0, 1.0);
                avgCos += dot;
                minCos = Math.min(minCos, dot);
            }
            centroidX /= observations.size();
            centroidZ /= observations.size();
            avgCos /= observations.size();

            double normalX = -dirZ;
            double normalZ = dirX;
            double deviation = 0.0;
            for (Observation observation : observations) {
                deviation += Math.abs((observation.x() - centroidX) * normalX + (observation.z() - centroidZ) * normalZ);
            }
            deviation /= observations.size();

            if (avgCos < stabilityThreshold || deviation > deviationLimit) {
                return null;
            }

            double stabilityScore = Mth.clamp(
                    (avgCos - stabilityThreshold) / Math.max(1.0e-5, 1.0 - stabilityThreshold),
                    0.0,
                    1.0
            );
            double minCosScore = Mth.clamp(
                    (minCos - (stabilityThreshold - 0.01)) / Math.max(1.0e-5, 1.01 - stabilityThreshold),
                    0.0,
                    1.0
            );
            double deviationScore = Math.exp(-deviation / 0.15);
            double speedScore = Mth.clamp(avgSpeed / 0.35, 0.0, 1.0);
            double quality = Mth.clamp(
                    stabilityScore * 0.40 + minCosScore * 0.25 + deviationScore * 0.20 + speedScore * 0.15,
                    0.0,
                    1.0
            );

            return new TrackedCapture(
                    centroidX,
                    centroidZ,
                    dirX,
                    dirZ,
                    quality,
                    avgCos,
                    deviation,
                    observations.size()
            );
        }

        private boolean captured() {
            return captured;
        }

        private void markCaptured() {
            captured = true;
        }

        private long lastSeenMs() {
            return lastSeenMs;
        }
    }
}
