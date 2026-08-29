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
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.HudTextEffects;
import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleManager;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;

import java.util.LinkedHashMap;
import java.util.*;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 45)
public final class Keybinds extends DraggableHudElement {

    {
        defaultLinkedLayout(456.6228f, 248.0f, "potions", "RIGHT", 0.0f, 0.0f);
    }

    private static final float HEADER_HEIGHT = 15.5f;
    private static final float CONTENT_START_Y = 25.0f;
    private static final float ROW_STEP = 11.0f;
    private static final float MIN_WIDTH = 102.0f;
    private static final float HEADER_ICON_SCALE = 1.18f;
    private static final float TITLE_TEXT_X = 22.0f;
    private static final float COUNT_LABEL_OFFSET = 22.0f;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String MODE_PRESS = "Press";
    private static final String MODE_HOLD = "Hold";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final ScriptedListHudPanel scriptedPanel = new ScriptedListHudPanel();
    private final NumberValue<Double> scaleValue =
            new NumberValue<>("keybinds_scale", 1.0, HudPanelLayoutModes.SCALE_MIN, HudPanelLayoutModes.SCALE_MAX);
    private final ModeValue layoutMode =
            new ModeValue("keybinds_layout", "Unified Divider", HudPanelLayoutModes.SPLIT_HEADER, HudPanelLayoutModes.UNIFIED_DIVIDER);
    private final ModeValue colorMode =
            new ModeValue("keybinds_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final ModeValue panelStyle =
            new ModeValue("keybinds_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
                    HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT,
                    HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("keybinds_theme_gradient_strength", 72, 0, 100);
    private final ModeValue bgEffect =
            new ModeValue("keybinds_bg_effect", "None", EFFECT_NONE, EFFECT_BLUR);
    private final RGBAColorValue bg =
            new RGBAColorValue("keybinds_bg", "#F7343434");
    private final RGBAColorValue bg2 =
            new RGBAColorValue("keybinds_bg_secondary", "#F7161616");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("keybinds_bg_alpha", 225, 0, 255);
    private final BooleanValue strokeEnabled =
            new BooleanValue("keybinds_stroke_enabled", false);
    private final NumberValue<Integer> strokeAlpha =
            new NumberValue<>("keybinds_stroke_alpha", 160, 0, 255);
    private final BooleanValue strokeGradient =
            new BooleanValue("keybinds_stroke_gradient", true);
    private final BooleanValue shadowEnabled =
            new BooleanValue("keybinds_shadow_enabled", true);
    private final ModeValue shadowMode =
            new ModeValue("keybinds_shadow_mode", HudRenderUtil.SHADOW_MODE_BLACK,
                    HudRenderUtil.SHADOW_MODE_BLACK, HudRenderUtil.SHADOW_MODE_THEME);
    private final NumberValue<Integer> themeShadowStrength =
            new NumberValue<>("keybinds_theme_shadow_strength", 100, 0, 100);
    private final NumberValue<Integer> shadowAlpha =
            new NumberValue<>("keybinds_shadow_alpha", 38, 0, 255);
    private final RGBColorValue stroke =
            new RGBColorValue("keybinds_stroke", "#5A5A5A");
    private final RGBColorValue text =
            new RGBColorValue("keybinds_text", "#FFFFFF");
    private final RGBColorValue muted =
            new RGBColorValue("keybinds_muted", "#A5A5A5");
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("keybinds_blur_alpha", 140, 0, 255);
    private final BooleanValue localActions =
            new BooleanValue("keybinds_local_actions", true);
    private final BooleanMapValue localActionModes =
            new BooleanMapValue("keybinds_local_action_modes", new LinkedHashMap<>() {{
                put(MODE_PRESS, true);
                put(MODE_HOLD, true);
            }});
    private final BooleanValue headerIconPulse =
            new BooleanValue("keybinds_header_icon_pulse", true);
    private final NumberValue<Integer> headerIconPulseSpeed =
            new NumberValue<>("keybinds_header_icon_pulse_speed", 18, 1, 60);
    private final NumberValue<Integer> headerIconPulseIntensity =
            new NumberValue<>("keybinds_header_icon_pulse_intensity", 100, 0, 100);

    private final Map<String, Float> entryAnim = new LinkedHashMap<>();
    private final Map<String, Row> lastEntries = new LinkedHashMap<>();
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
    private int uiBlurTint;

    public Keybinds() {
        super("keybinds", "Keybinds", true);
    }

    private static String iconFor(ModuleCategory category) {
        if (category == null) return "ellipsis";
        return switch (category) {
            case COMBAT -> "swords";
            case MOVEMENT -> "accessibility";
            case PLAYER -> "user";
            case VISUALS -> "tree-pine";
            default -> "ellipsis";
        };
    }

    private static String cleanActionLabel(FunctionBindSetting setting) {
        if (setting == null) return "";
        String explicit = setting.getHudLabel();
        if (explicit != null && !explicit.isBlank()) return explicit;

        String actionId = setting.getActionId();
        String display = setting.getDisplayName();
        if (display == null || display.isBlank() || display.equals("Function: " + actionId)) {
            display = actionId;
        }
        display = display.replace("Function: ", "").replace('_', ' ').replace('-', ' ').trim();
        if (display.isEmpty()) return "";

        StringBuilder out = new StringBuilder(display.length());
        boolean cap = true;
        for (int i = 0; i < display.length(); i++) {
            char c = display.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') out.append(' ');
                cap = true;
                continue;
            }
            out.append(cap ? Character.toUpperCase(c) : c);
            cap = false;
        }
        return out.toString();
    }

    private static String formatBind(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String[] parts = raw.trim().toUpperCase(Locale.ROOT).split("\\+");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            String mapped = formatBindPart(part.trim());
            if (!mapped.isEmpty()) out.add(mapped);
        }
        return String.join("+", out);
    }

    private static String formatBindPart(String value) {
        return switch (value) {
            case "LEFT_CONTROL", "RIGHT_CONTROL", "LEFT_CTRL", "RIGHT_CTRL", "CONTROL", "CTRL" -> "CTRL";
            case "LEFT_SHIFT", "RIGHT_SHIFT", "SHIFT" -> "SHIFT";
            case "LEFT_ALT", "RIGHT_ALT", "ALT" -> "ALT";
            case "LEFT_SUPER", "RIGHT_SUPER", "SUPER" -> "WIN";
            case "GRAVE_ACCENT" -> "`";
            case "COMMA" -> ",";
            case "PERIOD" -> ".";
            case "SEMICOLON" -> ";";
            case "APOSTROPHE" -> "'";
            case "SLASH" -> "/";
            case "BACKSLASH" -> "\\";
            case "MINUS" -> "-";
            case "EQUAL" -> "=";
            case "SPACE" -> "SPC";
            case "ENTER" -> "ENT";
            case "ESCAPE" -> "ESC";
            case "DELETE" -> "DEL";
            case "INSERT" -> "INS";
            case "CAPS_LOCK" -> "CAPS";
            case "LEFT_BRACKET" -> "[";
            case "RIGHT_BRACKET" -> "]";
            default -> value.startsWith("MOUSE_BUTTON_") ? "M" + value.substring("MOUSE_BUTTON_".length()) : value;
        };
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scaleValue));
        defs.add(SettingDef.mode(layoutMode));
        defs.add(SettingDef.mode(colorMode));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.number(themeGradientStrength).visibleWhen(this::isGradientPanelStyle));
        defs.add(SettingDef.color(bg).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(bg2).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.bool(strokeEnabled));
        defs.add(SettingDef.colorNoAlpha(stroke).visibleWhen(() -> strokeEnabled.get() && isCustomMode()));
        defs.add(SettingDef.number(strokeAlpha).visibleWhen(strokeEnabled::get));
        defs.add(SettingDef.bool(strokeGradient).visibleWhen(() -> strokeEnabled.get() && isThemeMode()));
        defs.add(SettingDef.bool(shadowEnabled));
        defs.add(SettingDef.mode(shadowMode).visibleWhen(() -> shadowEnabled.get() && isThemeMode()));
        defs.add(SettingDef.number(themeShadowStrength).visibleWhen(this::isThemeShadow));
        defs.add(SettingDef.number(shadowAlpha).visibleWhen(shadowEnabled::get));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(this::isThemeMode));
        defs.add(SettingDef.colorNoAlpha(text).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.colorNoAlpha(muted).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.bool(localActions));
        defs.add(SettingDef.group("keybinds_local_action_modes", localActionModes).visibleWhen(localActions::get));
        defs.add(SettingDef.bool(headerIconPulse));
        defs.add(SettingDef.number(headerIconPulseSpeed).visibleWhen(headerIconPulse::get));
        defs.add(SettingDef.number(headerIconPulseIntensity).visibleWhen(headerIconPulse::get));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = screenW - 128.0f;
        this.y = 80.0f;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        boolean forceVisible = DraggableHudElementRegistry.isForceVisible();
        if (mc == null || (mc.player == null && !forceVisible)) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        boolean chatPreview = ClientScreen.current() instanceof ChatScreen;
        List<Row> rows = collectRows();
        List<AnimatedRow> animatedRows = animateRows(rows);
        boolean showExampleRow = rows.isEmpty() && (forceVisible || chatPreview);
        boolean showWidget = !rows.isEmpty() || showExampleRow;
        visibilityAnim = HudRenderUtil.animateVisibility(visibilityAnim, showWidget);
        float widgetScale = HudRenderUtil.visibilityScale(visibilityAnim);
        if (animatedRows.isEmpty() && !showExampleRow && visibilityAnim <= 0.0f) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

        updatePalette();

        TextRenderer headerIconRenderer = Fonts.renderer("Icons", FontInfo.Type.Regular, TextRenderer.get());
        TextRenderer headerTextRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        TextRenderer rowTextRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, TextRenderer.get());
        if (headerIconRenderer == null) headerIconRenderer = textRenderer;
        if (headerTextRenderer == null) headerTextRenderer = textRenderer;
        if (rowTextRenderer == null) rowTextRenderer = textRenderer;

        float baseScale = HudScale.scale(screenW, screenH) * 1.1f * HudPanelLayoutModes.effectiveScale(scaleValue);
        float rowStep = ROW_STEP * baseScale;
        float fontScale = 0.98f * (hud.getFontSize() / 18.0f);

        headerIconRenderer.begin(fontScale * HEADER_ICON_SCALE, true, false);
        float headerIconH = (float) headerIconRenderer.getHeight(false);
        headerIconRenderer.end();

        headerTextRenderer.begin(fontScale, true, false);
        float headerTextH = (float) headerTextRenderer.getHeight(false);
        float titleWidth = (float) headerTextRenderer.getWidth("Keybinds", false);
        headerTextRenderer.end();

        rowTextRenderer.begin(fontScale, true, false);
        float rowTextH = (float) rowTextRenderer.getHeight(false);
        float maxWidth = MIN_WIDTH * baseScale;
        if (showExampleRow) {
            float previewWidth = (float) rowTextRenderer.getWidth("KillAuraG", false) + (34.0f * baseScale);
            maxWidth = Math.max(maxWidth, previewWidth);
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                Row row = animatedRow.row();
                float widthCandidate = (float) rowTextRenderer.getWidth(row.name() + row.bindText(), false) + (34.0f * baseScale);
                maxWidth = Math.max(maxWidth, widthCandidate);
            }
        }
        rowTextRenderer.end();

        rowTextRenderer.begin(fontScale * 0.92f, false, false);
        String activeCountText = Integer.toString(rows.size());
        float activeLabelWidth = (float) rowTextRenderer.getWidth("Active:", false);
        float activeCountWidth = (float) rowTextRenderer.getWidth(activeCountText, false);
        rowTextRenderer.end();

        float headerWidth = (TITLE_TEXT_X * baseScale) + titleWidth
                + activeLabelWidth + activeCountWidth
                + ((COUNT_LABEL_OFFSET + 12.0f) * baseScale);
        maxWidth = Math.max(maxWidth, headerWidth);

        float contentHeight = showExampleRow ? rowStep : totalAnimatedHeight(animatedRows, rowStep);
        float targetHeight = (showExampleRow || !animatedRows.isEmpty())
                ? (CONTENT_START_Y * baseScale + contentHeight)
                : (HEADER_HEIGHT * baseScale);

        displayWidth = HudRenderUtil.animateDimension(displayWidth, maxWidth);
        displayHeight = HudRenderUtil.animateDimension(displayHeight, targetHeight);
        width = displayWidth;
        height = displayHeight;

        if (!showWidget && widgetScale <= 0.001f && animatedRows.isEmpty()) {
            width = 0.0f;
            height = 0.0f;
            return;
        }

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
        float drawRowTextH = rowTextH * drawScale;
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

        HudRenderUtil.ThemeGradient headerIconGradient = isThemeMode()
                ? HudRenderUtil.themeForegroundGradient(255)
                : new HudRenderUtil.ThemeGradient(resolvedHeaderIconColor, resolvedHeaderIconColor, 45.0f);
        if (isThemeMode() && headerIconPulse.get()) {
            headerIconGradient = HudTextEffects.animatedGradient(
                    headerIconGradient,
                    HudTextEffects.Effect.PULSE,
                    headerIconPulseSpeed.get(),
                    (float) (Util.getMillis() / 1000.0),
                    0.0f,
                    headerIconPulseIntensity.get() / 100.0f
            );
        }

        List<LinkedHashMap<String, Object>> panelRows = ScriptedListHudPanel.rows();
        if (showExampleRow) {
            LinkedHashMap<String, Object> row = ScriptedListHudPanel.row(
                    "preview",
                    "svg",
                    "swords",
                    List.of(ScriptedListHudPanel.textPart("KillAura", withAlpha(uiText & 0x00FFFFFF, 255), 0.0f)),
                    "G",
                    withAlpha(uiCounter & 0x00FFFFFF, 255),
                    withAlpha(uiMuted & 0x00FFFFFF, 130),
                    1.0f
            );
            row.put("iconTint", ScriptedListHudPanel.hex(withAlpha(uiCounter & 0x00FFFFFF, 255)));
            panelRows.add(row);
        } else {
            for (AnimatedRow animatedRow : animatedRows) {
                float anim = animatedRow.anim();
                if (anim <= 0.01f) continue;
                Row rowData = animatedRow.row();
                int rowAlpha = clamp255(Math.round(255.0f * anim));
                int rowNameColor = rowData.local()
                        ? withAlpha(uiCounter & 0x00FFFFFF, rowAlpha)
                        : withAlpha(uiText & 0x00FFFFFF, rowAlpha);
                int rowRightColor = withAlpha(uiCounter & 0x00FFFFFF, rowAlpha);
                int rowDividerColor = rowData.local()
                        ? withAlpha(uiCounter & 0x00FFFFFF, Math.max(42, rowAlpha - 94))
                        : withAlpha(uiMuted & 0x00FFFFFF, Math.max(26, rowAlpha - 120));
                LinkedHashMap<String, Object> row = ScriptedListHudPanel.row(
                        rowData.key(),
                        "svg",
                        rowData.icon(),
                        List.of(ScriptedListHudPanel.textPart(rowData.name(), rowNameColor, 0.0f)),
                        rowData.bindText(),
                        rowRightColor,
                        rowDividerColor,
                        anim
                );
                row.put("iconTint", ScriptedListHudPanel.hex(withAlpha(uiCounter & 0x00FFFFFF, rowAlpha)));
                panelRows.add(row);
            }
        }

        if (shadowEnabled.get()) {
            HudRenderUtil.drawHudShadow(
                    renderer, drawX, drawY, drawWidth, drawHeight,
                    ScriptedListHudPanel.PANEL_RADIUS * drawBaseScale, drawBaseScale,
                    isThemeShadow(), shadowAlpha.get(), 1.0f,
                    themeShadowStrength.get() / 100.0f
            );
        }

        scriptedPanel.render(
                renderer,
                textRenderer,
                ctx,
                tickDelta,
                new ScriptedListHudPanel.Panel(
                        ScriptedListHudPanel.KEYBINDS,
                        new ScriptedListHudPanel.Palette(uiHeaderLeft, uiHeaderRight, uiBodyLeft, uiBodyRight,
                                uiOutline, uiText, uiMuted, uiCounter, uiTitleText, uiDivider, uiBlurTint),
                        drawX,
                        drawY,
                        drawWidth,
                        drawHeight,
                        drawScale,
                        drawBaseScale,
                        drawFontScale,
                        drawHeaderIconH,
                        drawHeaderTextH,
                        drawRowTextH,
                        activeLabelWidth * drawScale,
                        activeCountWidth * drawScale,
                        rows.size(),
                        hasEffect(),
                        Math.min(1.0f, (blurAlpha.get() / 255.0f) * (isThemeMode() ? 1.15f : 1.0f)),
                        resolvedHeaderIconColor,
                        isThemeMode(),
                        headerIconGradient.start(),
                        headerIconGradient.end(),
                        headerIconGradient.angleDeg(),
                        HudPanelLayoutModes.current(layoutMode),
                        strokeEnabled.get(),
                        strokeAlpha.get() / 255.0f,
                        isThemeMode() && strokeGradient.get(),
                        resolveStrokeGradientStart(),
                        resolveStrokeGradientEnd(),
                        true,
                        panelRows
                )
        );
    }

    private List<Row> collectRows() {
        List<Row> rows = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module == null || !module.isEnabled()) continue;
            String name = module.getDisplayName();
            if (name == null || name.isBlank()) name = module.name();
            String moduleIcon = iconFor(module.getCategory());

            if (module.getKeyBindSetting() != null && module.getKeyBindSetting().getValue() != null) {
                String rawBind = module.getKeyBindSetting().getValue().get();
                if (rawBind != null && !rawBind.isBlank() && !"NONE".equalsIgnoreCase(rawBind)) {
                    rows.add(new Row(
                            module.name(),
                            name,
                            formatBind(rawBind),
                            moduleIcon,
                            false
                    ));
                }
            }

            for (FunctionBindSetting action : module.getActionSettings()) {
                if (action == null || !isLocalActionVisible(action)) continue;
                String rawBind = action.get();
                if (rawBind == null || rawBind.isBlank() || "NONE".equalsIgnoreCase(rawBind)) continue;

                String actionLabel = cleanActionLabel(action);
                if (actionLabel.isBlank()) actionLabel = action.getActionId();
                String actionIcon = action.getHudIcon();
                if (actionIcon == null || actionIcon.isBlank()) actionIcon = moduleIcon;

                rows.add(new Row(
                        module.name() + ":action:" + action.getActionId(),
                        name + ": " + actionLabel,
                        formatBind(rawBind),
                        actionIcon,
                        true
                ));
            }
        }
        rows.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        return rows;
    }

    private boolean isLocalActionVisible(FunctionBindSetting action) {
        if (!localActions.get()) return false;
        BindMode mode = action.getMode();
        if (mode == BindMode.HOLD) {
            return localActionModes.get(MODE_HOLD) && action.isHeldForHud();
        }
        return localActionModes.get(MODE_PRESS) && action.isHudToggle() && action.isHudToggleActive();
    }

    private List<AnimatedRow> animateRows(List<Row> rows) {
        Map<String, Row> current = new LinkedHashMap<>();
        for (Row row : rows) {
            current.put(row.key(), row);
        }

        Map<String, Row> merged = new LinkedHashMap<>();
        merged.putAll(current);
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        lastEntries.clear();
        lastEntries.putAll(merged);

        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            String key = entry.getKey();
            float target = current.containsKey(key) ? 1.0f : 0.0f;
            float anim = entryAnim.getOrDefault(key, 0.0f);
            anim = AnimationUtility.approach(anim, target, AnimationUtility.deltaTime(), 12.0f);
            anim = AnimationUtility.snap(anim, target, 0.02f);
            if (anim <= 0.0f && target <= 0.0f) {
                toRemove.add(key);
            } else {
                entryAnim.put(key, anim);
            }
        }
        for (String key : toRemove) {
            entryAnim.remove(key);
            lastEntries.remove(key);
        }

        List<AnimatedRow> out = new ArrayList<>();
        for (Map.Entry<String, Row> entry : lastEntries.entrySet()) {
            float anim = entryAnim.getOrDefault(entry.getKey(), 0.0f);
            if (anim > 0.01f) {
                out.add(new AnimatedRow(entry.getValue(), anim));
            }
        }
        return out;
    }

    private float totalAnimatedHeight(List<AnimatedRow> rows, float rowStep) {
        float out = 0.0f;
        for (AnimatedRow row : rows) {
            out += rowStep * row.anim();
        }
        return out;
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
                uiHeaderLeft = HudRenderUtil.accentSurface(uiHeaderLeft, 0.18f);
                uiHeaderRight = HudRenderUtil.accentSurface(uiHeaderRight, 0.26f);
                uiBodyLeft = HudRenderUtil.accentSurface(uiBodyLeft, 0.20f);
                uiBodyRight = HudRenderUtil.accentSurface(uiBodyRight, 0.30f);
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
        uiBlurTint = HudRenderUtil.mixColor(uiHeaderLeft, uiBodyRight, 0.5f);
    }

    private int withAlpha(int rgb, int alpha) {
        return (clamp255(alpha) << 24) | (rgb & 0x00FFFFFF);
    }

    private boolean isThemeMode() {
        return COLOR_THEME.equals(colorMode.get());
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
        if (!isThemeMode()) return stroke.getArgb();
        return HudRenderUtil.themeAccentGradient(255).start();
    }

    private int resolveStrokeGradientEnd() {
        if (!isThemeMode()) return stroke.getArgb();
        return HudRenderUtil.themeAccentGradient(255).end();
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private record Row(String key, String name, String bindText, String icon, boolean local) {
    }

    private record AnimatedRow(Row row, float anim) {
    }
}
