/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.ClickGui;
import combatant.client.mixins.accessors.BossHealthOverlayAccessor;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiBoundsPatchSet;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptColor;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptPatchSet;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptProps;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.media.MediaSessionService;
import combatant.client.util.media.RepeatMode;
import combatant.client.util.pvp.PvpState;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 60)
@UiScriptAsset("combatant:modules/hud/static/dynamic_island")
public final class DynamicIsland extends AbstractHudElement {
    public static final DynamicIsland INSTANCE = new DynamicIsland();
    private static final float TOP_Y = 18f;
    private static final float CLOSED_HEIGHT = 35f;
    private static final float EXPANDED_HEIGHT = 98f;
    private static final float CLOSED_CHAMFER = 4.5f;
    private static final float EXPANDED_CHAMFER = 6.5f;

    private static final float TIME_WIDTH = 82f;
    private static final float PVP_MIN_WIDTH = 84f;
    private static final float PVP_MAX_WIDTH = 108f;
    private static final float MUSIC_EXPANDED_WIDTH = 338f;

    private static final float COMPACT_STATUS_W = 50f;
    private static final float EXPANDED_PAD_X = 13f;
    private static final float PROGRESS_H = 5f;
    private static final float CONTROL_SIZE = 27f;
    private static final float CONTROL_GAP = 7f;
    private static final float CONTROL_TIME_GAP = 10f;
    private static final float CONTROL_ROW_GAP_Y = -5f;

    private static final int ICON_PAUSE = 0xEA02;
    private static final int ICON_PLAY = 0xEA03;
    private static final int ICON_PREV = 0xEA04;
    private static final int ICON_NEXT = 0xEA05;
    private static final int ICON_SHUFFLE = 0xEA06;

    private static final int STATUS_PVP = 0xFFFF6368;

    private final Minecraft mc = Minecraft.getInstance();
    private final MediaSessionService mediaService = MediaSessionService.get();
    private final BooleanValue syncTheme = new BooleanValue("dynamic_island_sync_theme", true);
    private final ModeValue panelStyle = new ModeValue(
            "dynamic_island_panel_style", HudRenderUtil.PANEL_STYLE_DEFAULT,
            HudRenderUtil.PANEL_STYLE_DEFAULT, HudRenderUtil.PANEL_STYLE_ACCENT, HudRenderUtil.PANEL_STYLE_GRADIENT);
    private final NumberValue<Integer> themeMix = new NumberValue<>("dynamic_island_theme_mix", 100, 0, 100);
    private final NumberValue<Integer> themeGradientStrength =
            new NumberValue<>("dynamic_island_theme_gradient_strength", 72, 0, 100);
    private final NumberValue<Integer> themeAlpha = new NumberValue<>("dynamic_island_theme_alpha", 200, 0, 255);
    private final BooleanValue blur = new BooleanValue("dynamic_island_blur", true);
    private final NumberValue<Integer> blurAlpha = new NumberValue<>("dynamic_island_blur_alpha", 120, 0, 255);
    private final RGBAColorValue bgColor = new RGBAColorValue("dynamic_island_bg", "#E614171D");
    private final RGBColorValue accentColor = new RGBColorValue("dynamic_island_accent", "#6E8DFF");
    private final RGBColorValue textColor = new RGBColorValue("dynamic_island_text", "#F5F7FA");
    private final RGBColorValue mutedColor = new RGBColorValue("dynamic_island_muted", "#A9B1BC");

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(DynamicIsland.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    private float visibilityAnim;
    private float expandAnim;
    private float widthAnim = -1f;
    private float mainWidthAnim = -1f;
    private float heightAnim = -1f;
    private boolean expanded;
    private boolean clickGuiShellVisible;
    private String titleScrollKey = "";
    private long titleScrollStartMs;

    private IslandMode currentMode = IslandMode.TIME;
    private MediaSessionService.Snapshot currentSnapshot = MediaSessionService.Snapshot.empty(MediaSessionService.isMediaAvailable());

    private float shuffleX;
    private float shuffleY;
    private float prevX;
    private float prevY;
    private float playX;
    private float playY;
    private float nextX;
    private float nextY;
    private float repeatX;
    private float repeatY;
    private float controlSize;
    private float shuffleHover;
    private float prevHover;
    private float playHover;
    private float nextHover;
    private float repeatHover;
    private float shufflePress;
    private float prevPress;
    private float playPress;
    private float nextPress;
    private float repeatPress;
    private float bodyHover;
    private float bodyPress;
    private float progressHover;
    private float progressPress;
    private float mainBodyX;
    private float mainBodyY;
    private float mainBodyW;
    private float mainBodyH;
    private float progressX;
    private float progressY;
    private float progressW;

    private int uiDisplayBg;
    private int uiDisplayBgStart;
    private int uiDisplayBgEnd;
    private float uiDisplayBgAngle = 90.0f;
    private int uiPhosphor;
    private int uiPhosphorDim;
    private int uiMatrixOff;
    private int uiTextPrimary;
    private int uiTextSecondary;

    private DynamicIsland() {
        super("dynamic_island", "Dynamic Island", true);
    }

    public static boolean shouldRenderInScreenOverlay(Screen screen) {
        return INSTANCE != null && INSTANCE.isScreenOverlayAllowed(screen);
    }

    public static void renderScreenOverlay(GuiGraphicsExtractor ctx, float tickDelta) {
        INSTANCE.renderScreenOverlayInternal(ctx, tickDelta);
    }

    /** Renders the complete ClickGUI tab shell in the same stratum and batch as its section. */
    public static void renderClickGuiShell(Renderer2D renderer,
                                           TextRenderer textRenderer,
                                           GuiGraphicsExtractor ctx,
                                           float tickDelta,
                                           int screenW,
                                           int screenH) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.clickGuiEnabled()) return;
        if (!(ClientScreen.current() instanceof ClickGuiScreen)) return;
        INSTANCE.renderEngineInternal(renderer, textRenderer, ctx, tickDelta, screenW, screenH, true);
    }

    public static boolean shouldOwnClickGuiTabShell() {
        if (INSTANCE == null || !INSTANCE.isEnabled() || !INSTANCE.clickGuiEnabled()) return false;
        return ClientScreen.current() instanceof ClickGuiScreen;
    }

    private static void putControlBounds(UiBoundsPatchSet patches,
                                         String key,
                                         float x,
                                         float y,
                                         float size,
                                         float press) {
        float down = clamp01(press);
        float haloSize = size * (1.0f - 0.055f * down);
        float haloOffset = (size - haloSize) * 0.5f;
        float iconScale = 1.0f - 0.08f * down;
        float iconW = size * iconScale;
        float iconH = Math.max(0.0f, (size - 4.0f) * iconScale);
        putBounds(patches, key + ":hover", x - 1.0f + haloOffset, y - 1.0f + haloOffset, haloSize + 2.0f, haloSize + 2.0f);
        putBounds(patches, key, x + (size - iconW) * 0.5f, y + 4.0f + ((size - 4.0f) - iconH) * 0.5f, iconW, iconH);
        putBounds(patches, key + ":active", x + size * 0.43f, y + size - 2.5f, size * 0.14f, 1.5f);
    }

    private static void putSvgControlBounds(UiBoundsPatchSet patches,
                                            String key,
                                            float x,
                                            float y,
                                            float size,
                                            float press) {
        float down = clamp01(press);
        float haloSize = size * (1.0f - 0.055f * down);
        float haloOffset = (size - haloSize) * 0.5f;
        float iconSize = Math.max(0.0f, size * 0.74f * (1.0f - 0.08f * down));
        putBounds(patches, key + ":hover", x - 1.0f + haloOffset, y - 1.0f + haloOffset, haloSize + 2.0f, haloSize + 2.0f);
        putBounds(patches, key, x + (size - iconSize) * 0.5f, y + (size - iconSize) * 0.5f, iconSize, iconSize);
        putBounds(patches, key + ":active", x + size * 0.43f, y + size - 2.5f, size * 0.14f, 1.5f);
    }

    private static void putBounds(UiBoundsPatchSet patches,
                                  String key,
                                  float x,
                                  float y,
                                  float width,
                                  float height) {
        patches.put(key, x, y, width, height);
    }

    private static void patchControl(UiScriptPatchSet patches,
                                     String key,
                                     String icon,
                                     String iconColor,
                                     String phosphor,
                                     float expandedAlpha,
                                     float hover,
                                     float press,
                                     boolean active) {
        patchText(patches, key, icon, scaleHexAlpha(iconColor, expandedAlpha));
        float focus = Math.max(0.0f, hover) + Math.max(0.0f, press) * 0.65f;
        patchShape(patches, key,
                "textGlowWidth", 1.5f + Math.min(1.0f, focus) * 0.7f,
                "textGlowStrength", (0.12f + Math.min(1.0f, focus) * 0.24f) * expandedAlpha,
                "textGlowColor", scaleHexAlpha(phosphor, expandedAlpha));
        patchShape(patches, key + ":hover", "fill", scaleHexAlpha(phosphor, focus * 0.13f * expandedAlpha));
        patchShape(patches, key + ":active", "fill", scaleHexAlpha(phosphor, active ? 0.92f * expandedAlpha : 0.0f));
    }

    private static void patchSvgControl(UiScriptPatchSet patches,
                                        String key,
                                        String asset,
                                        String iconColor,
                                        String phosphor,
                                        float expandedAlpha,
                                        float hover,
                                        float press,
                                        boolean active) {
        patchImage(patches, key, asset, scaleHexAlpha(iconColor, expandedAlpha));
        float focus = Math.max(0.0f, hover) + Math.max(0.0f, press) * 0.65f;
        patchShape(patches, key + ":hover", "fill", scaleHexAlpha(phosphor, focus * 0.13f * expandedAlpha));
        patchShape(patches, key + ":active", "fill", scaleHexAlpha(phosphor, active ? 0.92f * expandedAlpha : 0.0f));
    }

    private static void patchText(UiScriptPatchSet patches,
                                  String key,
                                  String text,
                                  String color) {
        patches.text(key, text, color);
    }

    private static void patchPhosphorText(UiScriptPatchSet patches,
                                          String key,
                                          String text,
                                          String color,
                                          String glowColor,
                                          float glowWidth,
                                          float glowStrength) {
        patches.text(key, text, color);
        patches.props(key,
                "textGlowColor", glowColor,
                "textGlowWidth", glowWidth,
                "textGlowStrength", glowStrength);
    }

    private static void patchClippedText(UiScriptPatchSet patches,
                                         String key,
                                         float measuredWidth,
                                         float boxWidth,
                                         float scrollTime,
                                         float delay,
                                         float speed,
                                         float fadeWidth) {
        patches.clippedText(key, measuredWidth, boxWidth, scrollTime, delay, speed, fadeWidth);
    }

    private static void patchImage(UiScriptPatchSet patches,
                                   String key,
                                   String asset,
                                   String tint) {
        patches.image(key, asset, tint);
    }

    private static void patchShape(UiScriptPatchSet patches,
                                   String key,
                                   Object... pairs) {
        patches.props(key, pairs);
    }

    private static void patchShape(UiScriptPatchSet patches,
                                   String key,
                                   String prop,
                                   Object value) {
        patches.put(key, prop, value);
    }

    private static void patchShape(UiScriptPatchSet patches,
                                   String key,
                                   String propA,
                                   Object valueA,
                                   String propB,
                                   Object valueB) {
        patches.put(key, propA, valueA, propB, valueB);
    }

    private static String stringProp(UiScriptProps props, String key, String fallback) {
        return props.string(key, fallback);
    }

    private static float numberProp(UiScriptProps props, String key, float fallback) {
        return props.number(key, fallback);
    }

    private static boolean boolProp(UiScriptProps props, String key, boolean fallback) {
        return props.bool(key, fallback);
    }

    private static float mapNumber(Map<?, ?> map, String key, float fallback) {
        if (map == null || key == null) return fallback;
        Object value = map.get(key);
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String scaleHexAlpha(String hex, float amount) {
        return UiScriptColor.alpha(hex, amount);
    }

    private static String repeatSvg(MediaSessionService.Snapshot snapshot) {
        if (snapshot == null || !snapshot.supportsRepeat()) {
            return "repeat-off";
        }
        return snapshot.repeatMode() == RepeatMode.ONE ? "repeat-1" : "repeat";
    }

    private static boolean clickGuiEnabled() {
        ClickGui clickGui = Modules.get(ClickGui.class);
        return clickGui != null && clickGui.isEnabled();
    }

    private static boolean isSettingsLikeScreen(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        String simple = screen.getClass().getSimpleName();
        if (name.contains(".gui.screen.option.") || name.contains(".gui.screen.pack.")) return true;
        return simple.contains("Options")
                || simple.contains("Option")
                || simple.contains("Controls")
                || simple.contains("Keybind")
                || simple.contains("Video")
                || simple.contains("Sound")
                || simple.contains("Language")
                || simple.contains("Accessibility")
                || simple.contains("Skin")
                || simple.contains("Pack")
                || simple.contains("Datapack")
                || simple.contains("Customize")
                || simple.contains("Telemetry")
                || simple.contains("Credits");
    }

    private static String formatClock(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    private static String formatPvpTelemetry(float seconds) {
        long remaining = Math.max(0L, (long) Math.ceil(Math.max(0f, seconds)));
        return remaining < 100L ? String.format("PVP %02d", remaining) : "PVP " + remaining;
    }

    private static String formatShortTime(float seconds) {
        return formatShortTime((long) Math.ceil(Math.max(0f, seconds)));
    }

    private static String formatShortTime(long seconds) {
        long sec = Math.max(0L, seconds);
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format("%d:%02d", m, s);
    }

    private static String iconString(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private int clickGuiCategoryColor(int index) {
        int[] categoryTints = {
                0xFF55D6FF,
                0xFFA98CFF,
                0xFFFFB85C,
                0xFF66E3A4,
                0xFFFF779D,
                0xFF65A8FF
        };
        int tint = categoryTints[Math.floorMod(index, categoryTints.length)];
        return HudRenderUtil.mixColor(uiPhosphor, tint, 0.38f);
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bool(syncTheme));
        defs.add(SettingDef.mode(panelStyle).visibleWhen(syncTheme::get));
        defs.add(SettingDef.number(themeMix).visibleWhen(syncTheme::get));
        defs.add(SettingDef.number(themeGradientStrength)
                .visibleWhen(() -> syncTheme.get() && HudRenderUtil.PANEL_STYLE_GRADIENT.equals(panelStyle.get())));
        defs.add(SettingDef.number(themeAlpha).visibleWhen(syncTheme::get));
        defs.add(SettingDef.bool(blur));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(blur::get));
        defs.add(SettingDef.color(bgColor).visibleWhen(() -> !syncTheme.get()));
        defs.add(SettingDef.colorNoAlpha(accentColor).visibleWhen(() -> !syncTheme.get()));
        defs.add(SettingDef.colorNoAlpha(textColor).visibleWhen(() -> !syncTheme.get()));
        defs.add(SettingDef.colorNoAlpha(mutedColor).visibleWhen(() -> !syncTheme.get()));
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudRenderSpace getRenderSpace() {
        return HudRenderSpace.UNSCALED_LOGICAL;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.AFTER_SUBTITLES;
    }

    @Override
    public int getRenderOrder() {
        return 40;
    }

    @Override
    public boolean isMouseOverInteractive(float mx, float my) {
        return mc != null && isInteractiveScreen(ClientScreen.current()) && contains(mx, my);
    }

    @Override
    public boolean onMouseClicked(float mx, float my, int button) {
        if (mc == null || !isInteractiveScreen(ClientScreen.current())) return false;
        if (!contains(mx, my)) return false;
        if (currentMode != IslandMode.MUSIC || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        if (expandAnim > 0.01f) {
            if (progressW > 1.0f && currentSnapshot != null && currentSnapshot.supportsSeek()
                    && hit(mx, my, progressX - 2.0f, progressY - 5.0f, progressW + 4.0f, PROGRESS_H + 10.0f)) {
                float fraction = clamp01((mx - progressX) / progressW);
                long duration = Math.max(0L, currentSnapshot.durationSeconds());
                if (duration > 0L) {
                    mediaService.seekTo(Math.round(duration * fraction));
                    progressPress = 1.0f;
                }
                return true;
            }
            if (currentSnapshot != null && currentSnapshot.supportsShuffle()
                    && hit(mx, my, shuffleX, shuffleY, controlSize, controlSize)) {
                shufflePress = 1.0f;
                mediaService.toggleShuffle();
                return true;
            }
            if (hit(mx, my, prevX, prevY, controlSize, controlSize)) {
                prevPress = 1.0f;
                mediaService.previous();
                return true;
            }
            if (hit(mx, my, playX, playY, controlSize, controlSize)) {
                playPress = 1.0f;
                mediaService.playPause();
                return true;
            }
            if (hit(mx, my, nextX, nextY, controlSize, controlSize)) {
                nextPress = 1.0f;
                mediaService.next();
                return true;
            }
            if (currentSnapshot != null && currentSnapshot.supportsRepeat()
                    && hit(mx, my, repeatX, repeatY, controlSize, controlSize)) {
                repeatPress = 1.0f;
                mediaService.cycleRepeatMode();
                return true;
            }
        }

        // Context chips are part of the overall bounds but are not the toggle surface.
        if (hit(mx, my, mainBodyX, mainBodyY, mainBodyW, mainBodyH)) {
            bodyPress = 1.0f;
            expanded = !expanded;
            return true;
        }
        return false;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        renderEngineInternal(renderer, textRenderer, ctx, tickDelta, screenW, screenH, false);
    }

    private void renderScreenOverlayInternal(GuiGraphicsExtractor ctx, float tickDelta) {
        if (!RuntimeGate.canRunHud()) return;
        if (mc == null || mc.getWindow() == null || ctx == null) return;
        Screen currentScreen = ClientScreen.current();
        // ClickGUI owns a single screen-stratum render path. Its shell is emitted from
        // ClickGuiRenderer after the active section, never as a later top overlay.
        if (currentScreen instanceof ClickGuiScreen) return;
        boolean clickGuiOverlay = isEnabled() && clickGuiEnabled();
        if (!clickGuiOverlay && !isScreenOverlayAllowed(currentScreen)) return;

        FullScreenRenderer.ensureInit();
        int screenW = Math.max(1, Math.round(HudScale.virtualWidth(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        )));
        int screenH = Math.max(1, Math.round(HudScale.virtualHeight(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        )));

        CombatantRenderSystem.updateFrameTiming(tickDelta, tickDelta, tickDelta);
        ViewportContext.beginUnscaledLogical(ctx);
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "2d:screen:dynamic_island")) {
            Renderer2D.COLOR.begin();
            renderEngineInternal(Renderer2D.COLOR, TextRenderer.get(), ctx, tickDelta, screenW, screenH, true);
            Renderer2D.COLOR.render();
        } finally {
            ViewportContext.end(ctx);
        }
    }

    private void renderEngineInternal(Renderer2D renderer,
                                      TextRenderer textRenderer,
                                      GuiGraphicsExtractor ctx,
                                      float tickDelta,
                                      int screenW,
                                      int screenH,
                                      boolean screenOverlayPass) {
        if (mc == null) return;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            resetScriptRuntime();
        }

        Screen currentScreen = ClientScreen.current();
        boolean chatOpen = currentScreen instanceof ChatScreen;
        boolean clickGuiVisible = clickGuiEnabled();
        boolean clickGuiTabShellScreen = currentScreen instanceof ClickGuiScreen;
        ClickGuiRenderer.ClickGuiIslandState clickGuiState = ClickGuiRenderer.islandState();
        boolean clickGuiBridge = isEnabled()
                && screenOverlayPass
                && clickGuiVisible
                && clickGuiTabShellScreen
                && clickGuiState.lifecycle() > 0.001f
                && clickGuiState.tabBarW() > 1.0f;
        if (clickGuiVisible && clickGuiTabShellScreen && !clickGuiBridge) {
            clickGuiShellVisible = false;
            setBounds(0f, 0f, 0f, 0f);
            resetControls();
            return;
        }
        boolean shouldShow = clickGuiBridge
                || (isEnabled() && (screenOverlayPass ? isScreenOverlayAllowed(currentScreen) : currentScreen == null || chatOpen));
        currentSnapshot = mediaService.snapshot();
        currentMode = clickGuiBridge ? IslandMode.CLICKGUI : resolveMode(currentSnapshot);

        if (!isInteractiveScreen(currentScreen) || currentMode != IslandMode.MUSIC) {
            expanded = false;
        }

        float dt = AnimationUtility.deltaTime();
        visibilityAnim = AnimationUtility.approach(visibilityAnim, shouldShow ? 1f : 0f, dt, 10f);
        expandAnim = AnimationUtility.approach(expandAnim, expanded ? 1f : 0f, dt, 10f);
        visibilityAnim = AnimationUtility.snap(visibilityAnim, shouldShow ? 1f : 0f, 0.002f);
        expandAnim = AnimationUtility.snap(expandAnim, expanded ? 1f : 0f, 0.002f);
        clickGuiShellVisible = clickGuiBridge && visibilityAnim > 0.01f;

        if (!shouldShow && visibilityAnim <= 0.01f) {
            clickGuiShellVisible = false;
            setBounds(0f, 0f, 0f, 0f);
            resetControls();
            return;
        }

        updatePalette();
        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer titleRenderer = BuiltinFontCatalog.ONEST_MEDIUM.renderer(fallback);
        TextRenderer valueRenderer = BuiltinFontCatalog.MATRIX_SANS_PRINT.renderer(fallback);
        TextRenderer metaRenderer = BuiltinFontCatalog.ONEST_REGULAR.renderer(fallback);

        IslandMetrics metrics = measureMetrics(titleRenderer, valueRenderer);
        float targetMainWidth = switch (currentMode) {
            case TIME -> TIME_WIDTH;
            case PVP -> clamp(metrics.pvpWidth, PVP_MIN_WIDTH, PVP_MAX_WIDTH);
            case MUSIC -> AnimationUtility.lerp(metrics.musicCompactWidth, MUSIC_EXPANDED_WIDTH, expandAnim);
            case CLICKGUI -> clickGuiState.tabBarW();
        };
        float targetHeight = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarH()
                : AnimationUtility.lerp(CLOSED_HEIGHT, currentMode == IslandMode.MUSIC ? EXPANDED_HEIGHT : CLOSED_HEIGHT, expandAnim);

        if (currentMode == IslandMode.CLICKGUI) {
            // Tab geometry is authoritative and must remain 1:1.
            mainWidthAnim = targetMainWidth;
            heightAnim = targetHeight;
        } else {
            mainWidthAnim = mainWidthAnim < 0f ? targetMainWidth
                    : AnimationUtility.approach(mainWidthAnim, targetMainWidth, dt, 12f);
            heightAnim = heightAnim < 0f ? targetHeight
                    : AnimationUtility.approach(heightAnim, targetHeight, dt, 12f);
        }
        mainWidthAnim = AnimationUtility.snap(mainWidthAnim, targetMainWidth, 0.05f);
        heightAnim = AnimationUtility.snap(heightAnim, targetHeight, 0.05f);

        float drawMainWidth = mainWidthAnim;
        float drawHeight = heightAnim;
        float drawWidth = drawMainWidth;
        widthAnim = drawWidth;
        float drawX = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarX()
                : (screenW - drawMainWidth) * 0.5f;
        float drawY = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarY() - (1.0f - AnimationUtility.easeOutCubic(clickGuiState.lifecycle())) * 18.0f
                : TOP_Y + bossBarOffset(screenH);
        float mainX = 0.0f;
        float alpha = AnimationUtility.easeOutCubic(visibilityAnim);
        float bezelCut = currentMode == IslandMode.CLICKGUI
                ? CLOSED_CHAMFER
                : AnimationUtility.lerp(CLOSED_CHAMFER, EXPANDED_CHAMFER, expandAnim);
        int accent = accentFor(currentMode);

        setBounds(drawX, drawY, drawWidth, drawHeight);
        mainBodyX = drawX + mainX;
        mainBodyY = drawY;
        mainBodyW = drawMainWidth;
        mainBodyH = drawHeight;

        float expandedContentAlpha = currentMode == IslandMode.MUSIC ? AnimationUtility.easeOutCubic(expandAnim) : 0f;
        float compactContentAlpha = currentMode == IslandMode.MUSIC ? 1f - AnimationUtility.easeOutCubic(expandAnim) : 0f;
        if (expandedContentAlpha > 0.01f) {
            updateMusicControls(drawX + mainX, drawY, drawMainWidth, valueRenderer);
        } else {
            resetControls();
        }
        updateInteractionAnimation(isInteractiveScreen(currentScreen), dt);

        if (!renderScripted(renderer, fallback, titleRenderer, metaRenderer, ctx, tickDelta, screenW, screenH, drawWidth, drawHeight,
                drawMainWidth, mainX, alpha, compactContentAlpha, expandedContentAlpha, bezelCut, accent)) {
            drawFallback(renderer, drawX + mainX, drawY, drawMainWidth, drawHeight, bezelCut, alpha);
        }
    }

    private boolean renderScripted(Renderer2D renderer,
                                   TextRenderer textRenderer,
                                   TextRenderer titleRenderer,
                                   TextRenderer metaRenderer,
                                   GuiGraphicsExtractor ctx,
                                   float tickDelta,
                                   int screenW,
                                   int screenH,
                                   float width,
                                   float height,
                                   float mainWidth,
                                   float mainX,
                                   float alpha,
                                   float compactAlpha,
                                   float expandedAlpha,
                                   float bezelCut,
                                   int accent) {
        if (moduleHandle.isRuntimeBlocked()) return false;

        UiScriptModule loaded = ensureModule();
        if (loaded == null) return false;

        UiScriptProps props = buildProps(
                screenW, screenH, width, height, mainWidth, mainX,
                alpha, compactAlpha, expandedAlpha, bezelCut, accent,
                titleRenderer, metaRenderer
        );
        long treeSignature = islandStructuralSignature(props);
        UiRuntime baked = scriptRuntime.bake(
                moduleHandle,
                loaded,
                "dynamic_island",
                treeSignature,
                width,
                height,
                textRenderer,
                x,
                y,
                width,
                height,
                props::asMap,
                () -> islandRuntimePatches(props),
                () -> islandBoundsPatches(props)
        );
        if (baked == null) return false;
        baked.render(new UiRenderContext(renderer, textRenderer, ctx, tickDelta, UiProjectionMode.UNSCALED_LOGICAL));
        return true;
    }

    private long islandStructuralSignature(UiScriptProps props) {
        long h = 0xcbf29ce484222325L;
        h = CachedUiScriptRuntime.mix(h, stringProp(props, "mode", ""));
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "blur", false));
        h = CachedUiScriptRuntime.mix(h, !stringProp(props, "artworkTexture", "").isEmpty());
        h = CachedUiScriptRuntime.mix(h, "music".equals(stringProp(props, "mode", "")));
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "pvpActive", false));
        h = CachedUiScriptRuntime.mix(h, numberProp(props, "expand", 0.0f) > 0.5f);
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "showShuffle", false));
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "showRepeat", false));
        Object clickGuiTabs = props.get("clickGuiTabs");
        if (clickGuiTabs instanceof Iterable<?> tabs) {
            for (Object item : tabs) {
                if (!(item instanceof Map<?, ?> tab)) continue;
                Object label = tab.get("label");
                h = CachedUiScriptRuntime.mix(h, label == null ? "" : String.valueOf(label));
            }
        }
        return h;
    }

    private Map<String, ? extends Map<String, ?>> islandRuntimePatches(UiScriptProps props) {
        UiScriptPatchSet patches = UiScriptPatchSet.create(160);
        float compactAlpha = numberProp(props, "compactAlpha", 0.0f);
        float expandedAlpha = numberProp(props, "expandedAlpha", 0.0f);
        float rootAlpha = numberProp(props, "alpha", 0.0f);
        float width = numberProp(props, "width", 0.0f);
        float mainWidth = numberProp(props, "mainWidth", width);
        float progress = clamp01(numberProp(props, "progress", 0.0f));
        String mode = stringProp(props, "mode", "");
        boolean musicMode = "music".equals(mode);
        boolean clickGuiMode = "clickgui".equals(mode);
        boolean pvpMode = "pvp".equals(mode);
        boolean timeMode = "time".equals(mode);
        String primary = stringProp(props, "textPrimary", "#FFFFFFFF");
        String secondary = stringProp(props, "textSecondary", "#99FFFFFF");
        String phosphor = stringProp(props, "phosphor", "#FFFFFFFF");
        String phosphorDim = stringProp(props, "phosphorDim", "#66FFFFFF");
        String matrixOff = stringProp(props, "matrixOff", "#22FFFFFF");

        float surfaceBlurAlpha = (boolProp(props, "blur", false)
                ? numberProp(props, "blurAlpha", 0.45f)
                : 0.0f) * rootAlpha;
        patchShape(patches, "shell:surface",
                "chamfer", props.get("bezelCut"),
                "fill", scaleHexAlpha(stringProp(props, "displayBg", "#FF050608"), rootAlpha),
                "startColor", scaleHexAlpha(stringProp(props, "displayBgStart", "#FF050608"), rootAlpha),
                "endColor", scaleHexAlpha(stringProp(props, "displayBgEnd", "#FF050608"), rootAlpha),
                "angle", numberProp(props, "displayBgAngle", 90.0f),
                "blurAlpha", surfaceBlurAlpha,
                "blurBrightness", 1.0f);
        float shellInteraction = numberProp(props, "bodyHover", 0.0f) * 0.025f
                + numberProp(props, "bodyPress", 0.0f) * 0.045f;
        patchShape(patches, "shell:interaction", "fill", scaleHexAlpha(phosphor, shellInteraction * rootAlpha));

        if (timeMode) {
            patchPhosphorText(patches, "time", stringProp(props, "time", ""),
                    scaleHexAlpha(phosphor, rootAlpha), scaleHexAlpha(phosphor, rootAlpha), 2.0f, 0.24f);
        }

        if (pvpMode) {
            patchPhosphorText(patches, "pvp:timer", stringProp(props, "pvpTelemetry", "PVP 00"),
                    scaleHexAlpha(phosphor, rootAlpha), scaleHexAlpha(phosphor, rootAlpha), 2.0f, 0.30f);
        }

        if (clickGuiMode) {
            Object tabsValue = props.get("clickGuiTabs");
            if (tabsValue instanceof Iterable<?> tabs) {
                int index = 0;
                for (Object item : tabs) {
                    if (!(item instanceof Map<?, ?> tab)) continue;
                    String label = String.valueOf(tab.get("label"));
                    boolean active = Boolean.TRUE.equals(tab.get("active"));
                    boolean hovered = Boolean.TRUE.equals(tab.get("hovered"));
                    String categoryColor = tab.get("color") instanceof String color ? color : phosphor;
                    float textAlpha = active ? 1.0f : (hovered ? 0.86f : 0.58f);
                    patchShape(patches, "clickgui:wash:" + index,
                            "startColor", scaleHexAlpha(categoryColor,
                                    rootAlpha * (active ? 0.24f : (hovered ? 0.10f : 0.0f))),
                            "endColor", scaleHexAlpha(phosphorDim,
                                    rootAlpha * (active ? 0.10f : (hovered ? 0.035f : 0.0f))));
                    patchText(patches, "clickgui:tab:" + index, label,
                            scaleHexAlpha(active || hovered ? categoryColor : secondary, rootAlpha * textAlpha));
                    patchShape(patches, "clickgui:tab:" + index,
                            "textGlowColor", scaleHexAlpha(categoryColor, rootAlpha),
                            "textGlowWidth", active || hovered ? 1.7f : 0.0f,
                            "textGlowStrength", active ? 0.24f : (hovered ? 0.14f : 0.0f));
                    patchShape(patches, "clickgui:underline:" + index, "fill",
                            scaleHexAlpha(active || hovered ? categoryColor : matrixOff,
                                    rootAlpha * (active ? 1.0f : (hovered ? 0.62f : 0.34f))));
                    index++;
                }
            }
        }

        if (musicMode) {
            float compactVisible = rootAlpha * compactAlpha;
            patchPhosphorText(patches, "music:clock:compact", stringProp(props, "time", "00:00"),
                    scaleHexAlpha(phosphor, compactVisible), scaleHexAlpha(phosphor, compactVisible), 1.7f, 0.20f);
            patchText(patches, "music:title:compact", stringProp(props, "title", ""), scaleHexAlpha(primary, compactVisible));
            float compactTitleW = Math.max(0.0f, mainWidth - 82.0f - (boolProp(props, "pvpActive", false) ? 50.0f : 24.0f) - 7.0f);
            patchClippedText(patches, "music:title:compact", numberProp(props, "titleWidthCompact", 0.0f), compactTitleW,
                    numberProp(props, "titleScrollTime", 0.0f), 1.0f, 18.0f, 15.0f);
            patchShape(patches, "music:divider", "fill", scaleHexAlpha(phosphorDim, 0.46f * compactVisible));
            if (boolProp(props, "pvpActive", false)) {
                patchPhosphorText(patches, "music:pvp:compact", stringProp(props, "pvpTelemetry", "PVP 00"),
                        scaleHexAlpha(phosphor, compactVisible), scaleHexAlpha(phosphor, compactVisible), 1.8f, 0.28f);
            } else {
                patchShape(patches, "wave:0", "fill", scaleHexAlpha(phosphor, 0.64f * compactVisible));
                patchShape(patches, "wave:1", "fill", scaleHexAlpha(phosphor, 0.74f * compactVisible));
                patchShape(patches, "wave:2", "fill", scaleHexAlpha(phosphor, 0.84f * compactVisible));
            }
            patchImage(patches, "artwork:compact", stringProp(props, "artworkTexture", ""), scaleHexAlpha("#FFFFFFFF", compactVisible));
            patchShape(patches, "artwork:compact:fallback",
                    "fill", scaleHexAlpha(phosphorDim, 0.72f * compactVisible));
        }

        if (musicMode) {
            float expandedVisible = rootAlpha * expandedAlpha;
            patchPhosphorText(patches, "music:label:expanded", "NOW PLAYING",
                    scaleHexAlpha(phosphorDim, expandedVisible), scaleHexAlpha(phosphor, expandedVisible), 1.5f, 0.14f);
            patchPhosphorText(patches, "music:elapsed:header", stringProp(props, "elapsed", "0:00"),
                    scaleHexAlpha(phosphor, expandedVisible), scaleHexAlpha(phosphor, expandedVisible), 1.7f, 0.20f);
            patchText(patches, "music:title:expanded", stringProp(props, "title", ""), scaleHexAlpha(primary, expandedVisible));
            patchClippedText(patches, "music:title:expanded", numberProp(props, "titleWidthExpanded", 0.0f), Math.max(0.0f, mainWidth - 63.0f), numberProp(props, "titleScrollTime", 0.0f), 1.0f, 18.0f, 16.0f);
            patchText(patches, "music:artist:expanded", stringProp(props, "artist", ""), scaleHexAlpha(secondary, expandedVisible));
            patchClippedText(patches, "music:artist:expanded", numberProp(props, "artistWidthExpanded", 0.0f), Math.max(0.0f, mainWidth - 63.0f), numberProp(props, "titleScrollTime", 0.0f), 1.4f, 14.0f, 16.0f);
            float progressFocus = Math.max(numberProp(props, "progressHover", 0.0f), numberProp(props, "progressPress", 0.0f));
            int currentDot = Math.max(0, Math.min(31, Math.round(progress * 31.0f)));
            for (int i = 0; i < 32; i++) {
                String dotColor = i == currentDot ? primary : (i <= currentDot ? phosphor : matrixOff);
                patchShape(patches, "music:progress:dot:" + i, "fill", scaleHexAlpha(dotColor, expandedVisible));
            }
            patchShape(patches, "music:progress:focus", "fill",
                    scaleHexAlpha(phosphor, (0.10f + progressFocus * 0.12f) * expandedVisible));
            patchPhosphorText(patches, "music:elapsed:expanded", stringProp(props, "elapsed", "0:00"),
                    scaleHexAlpha(phosphor, expandedVisible), scaleHexAlpha(phosphor, expandedVisible), 1.5f, 0.14f);
            patchPhosphorText(patches, "music:total:expanded", stringProp(props, "total", "0:00"),
                    scaleHexAlpha(phosphor, expandedVisible), scaleHexAlpha(phosphor, expandedVisible), 1.5f, 0.14f);
            patchShape(patches, "artwork:expanded:frame",
                    "fill", scaleHexAlpha(phosphorDim, 0.16f * expandedVisible));
            patchImage(patches, "artwork:expanded", stringProp(props, "artworkTexture", ""), scaleHexAlpha("#FFFFFFFF", expandedVisible));
            patchShape(patches, "artwork:expanded:fallback",
                    "fill", scaleHexAlpha(phosphorDim, 0.72f * expandedVisible));
            boolean shuffleActive = boolProp(props, "shuffleActive", false);
            boolean repeatActive = boolProp(props, "repeatActive", false);
            if (boolProp(props, "showShuffle", false)) {
                patchControl(patches, "music:shuffle", stringProp(props, "iconShuffle", ""), stringProp(props, "shuffleColor", secondary), phosphor, expandedVisible, numberProp(props, "shuffleHover", 0.0f), numberProp(props, "shufflePress", 0.0f), shuffleActive);
            }
            patchControl(patches, "music:prev", stringProp(props, "iconPrev", ""), primary, phosphor, expandedVisible, numberProp(props, "prevHover", 0.0f), numberProp(props, "prevPress", 0.0f), false);
            patchControl(patches, "music:play", stringProp(props, "iconPlay", ""), phosphor, phosphor, expandedVisible, numberProp(props, "playHover", 0.0f), numberProp(props, "playPress", 0.0f), true);
            patchControl(patches, "music:next", stringProp(props, "iconNext", ""), primary, phosphor, expandedVisible, numberProp(props, "nextHover", 0.0f), numberProp(props, "nextPress", 0.0f), false);
            if (boolProp(props, "showRepeat", false)) {
                patchSvgControl(patches, "music:repeat", stringProp(props, "repeatAsset", "repeat-off"), stringProp(props, "repeatColor", secondary), phosphor, expandedVisible, numberProp(props, "repeatHover", 0.0f), numberProp(props, "repeatPress", 0.0f), repeatActive);
            }
        }

        return patches.asMap();
    }

    private Map<String, UiBounds> islandBoundsPatches(UiScriptProps props) {
        UiBoundsPatchSet patches = UiBoundsPatchSet.create(160);
        float rootX = x;
        float rootY = y;
        float width = numberProp(props, "width", 0.0f);
        float height = numberProp(props, "height", 0.0f);
        float mainX = numberProp(props, "mainX", 0.0f);
        float mainWidth = numberProp(props, "mainWidth", width);
        String mode = stringProp(props, "mode", "");
        boolean musicMode = "music".equals(mode);
        boolean clickGuiMode = "clickgui".equals(mode);

        putBounds(patches, "dynamic-island", rootX, rootY, width, height);
        putBounds(patches, "content", rootX, rootY, width, height);
        putBounds(patches, "shell:surface", rootX + mainX, rootY, mainWidth, height);
        putBounds(patches, "shell:interaction", rootX + mainX, rootY, mainWidth, height);

        if (clickGuiMode) {
            Object tabsValue = props.get("clickGuiTabs");
            if (tabsValue instanceof Iterable<?> tabs) {
                int index = 0;
                for (Object item : tabs) {
                    if (!(item instanceof Map<?, ?> tab)) continue;
                    float relativeX = mapNumber(tab, "x", 0.0f);
                    float tabWidth = mapNumber(tab, "width", 1.0f);
                    putBounds(patches, "clickgui:wash:" + index,
                            rootX + mainX + relativeX + 2.5f, rootY + 3.0f,
                            Math.max(1.0f, tabWidth - 5.0f), Math.max(1.0f, height - 6.0f));
                    putBounds(patches, "clickgui:tab:" + index,
                            rootX + mainX + relativeX, rootY + (height - 17.0f) * 0.5f - 1.0f,
                            Math.max(1.0f, tabWidth), 17.0f);
                    boolean active = Boolean.TRUE.equals(tab.get("active"));
                    putBounds(patches, "clickgui:underline:" + index,
                            rootX + mainX + relativeX + tabWidth * 0.28f, rootY + height - 6.0f,
                            Math.max(1.0f, tabWidth * 0.44f), active ? 1.6f : 1.0f);
                    index++;
                }
            }
        }

        if (musicMode) {
            putMusicCompactBounds(patches, rootX, rootY, props, mainX, mainWidth);
            putMusicExpandedBounds(patches, rootX, rootY, props, mainWidth);
        } else if ("pvp".equals(mode)) {
            putBounds(patches, "pvp:timer", rootX + mainX + 7.0f, rootY + 7.5f,
                    Math.max(1.0f, mainWidth - 14.0f), 20.0f);
        } else if (!clickGuiMode) {
            putBounds(patches, "time", rootX + mainX + 7.0f, rootY + 7.5f,
                    Math.max(1.0f, mainWidth - 14.0f), 20.0f);
        }

        return patches.asMap();
    }

    private void putMusicCompactBounds(UiBoundsPatchSet patches,
                                       float rootX,
                                       float rootY,
                                       UiScriptProps props,
                                       float mainX,
                                       float width) {
        putBounds(patches, "music:clock:compact", rootX + mainX + 9.0f, rootY + 10.3f, 40.0f, 14.0f);
        putBounds(patches, "music:divider", rootX + mainX + 52.0f, rootY + 8.0f, 0.75f, 19.0f);
        putBounds(patches, "artwork:compact", rootX + mainX + 58.0f, rootY + 8.5f, 18.0f, 18.0f);
        putBounds(patches, "artwork:compact:fallback", rootX + mainX + 58.0f, rootY + 8.5f, 18.0f, 18.0f);
        float statusW = boolProp(props, "pvpActive", false) ? 50.0f : 24.0f;
        float titleW = Math.max(0.0f, width - 82.0f - statusW - 7.0f);
        putBounds(patches, "music:title:compact:clip", rootX + mainX + 82.0f, rootY + 8.7f, titleW, 18.0f);
        putBounds(patches, "music:title:compact", rootX + mainX + 82.0f, rootY + 8.7f, titleW, 18.0f);

        if (boolProp(props, "pvpActive", false)) {
            putBounds(patches, "music:pvp:compact", rootX + mainX + width - 55.0f, rootY + 10.5f, 48.0f, 14.0f);
            return;
        }
        float waveX = mainX + width - 19.0f;
        float centerY = 17.5f;
        float t = Util.getMillis() / 1000.0f;
        boolean playing = boolProp(props, "playing", false);
        for (int i = 0; i < 3; i++) {
            float barH = playing
                    ? (float) (3.0f + Math.abs(Math.sin(t * 5.2f + i * 0.85f)) * 7.0f)
                    : 3.0f + i;
            putBounds(patches, "wave:" + i, rootX + waveX + i * 4.0f, rootY + centerY - barH * 0.5f, 1.7f, barH);
        }
    }

    private void putMusicExpandedBounds(UiBoundsPatchSet patches,
                                        float rootX,
                                        float rootY,
                                        UiScriptProps props,
                                        float width) {
        float mainX = numberProp(props, "mainX", 0.0f);
        float progressW = Math.max(1.0f, width - 26.0f);
        putBounds(patches, "music:label:expanded", rootX + mainX + 13.0f, rootY + 7.0f, 90.0f, 12.0f);
        putBounds(patches, "music:elapsed:header", rootX + mainX + width - 61.0f, rootY + 7.0f, 48.0f, 12.0f);
        putBounds(patches, "artwork:expanded:frame", rootX + mainX + 12.0f, rootY + 23.0f, 30.0f, 30.0f);
        putBounds(patches, "artwork:expanded", rootX + mainX + 14.0f, rootY + 25.0f, 26.0f, 26.0f);
        putBounds(patches, "artwork:expanded:fallback", rootX + mainX + 14.0f, rootY + 25.0f, 26.0f, 26.0f);
        float titleW = Math.max(0.0f, width - 63.0f);
        putBounds(patches, "music:title:expanded:clip", rootX + mainX + 50.0f, rootY + 22.5f, titleW, 16.0f);
        putBounds(patches, "music:title:expanded", rootX + mainX + 50.0f, rootY + 22.5f, titleW, 16.0f);
        putBounds(patches, "music:artist:expanded:clip", rootX + mainX + 50.0f, rootY + 38.0f, titleW, 14.0f);
        putBounds(patches, "music:artist:expanded", rootX + mainX + 50.0f, rootY + 38.0f, titleW, 14.0f);
        float progressFocus = Math.max(numberProp(props, "progressHover", 0.0f), numberProp(props, "progressPress", 0.0f));
        float progress = clamp01(numberProp(props, "progress", 0.0f));
        int currentDot = Math.max(0, Math.min(31, Math.round(progress * 31.0f)));
        for (int i = 0; i < 32; i++) {
            boolean current = i == currentDot;
            boolean played = i <= currentDot;
            float dotSize = current ? 3.2f + progressFocus * 0.8f : (played ? 2.05f : 1.45f);
            float cx = rootX + mainX + 13.0f + progressW * i / 31.0f;
            putBounds(patches, "music:progress:dot:" + i,
                    cx - dotSize * 0.5f, rootY + 57.0f - dotSize * 0.5f, dotSize, dotSize);
        }
        float focusSize = 7.0f + progressFocus * 2.0f;
        float focusX = rootX + mainX + 13.0f + progressW * progress;
        putBounds(patches, "music:progress:focus", focusX - focusSize * 0.5f,
                rootY + 57.0f - focusSize * 0.5f, focusSize, focusSize);
        putBounds(patches, "music:elapsed:expanded", rootX + mainX + 13.0f, rootY + 63.0f, 44.0f, 12.0f);
        putBounds(patches, "music:total:expanded", rootX + mainX + width - 57.0f, rootY + 63.0f, 44.0f, 12.0f);

        if (boolProp(props, "showShuffle", false)) {
            putControlBounds(patches, "music:shuffle", shuffleX, shuffleY, controlSize, numberProp(props, "shufflePress", 0.0f));
        }
        putControlBounds(patches, "music:prev", prevX, prevY, controlSize, numberProp(props, "prevPress", 0.0f));
        putControlBounds(patches, "music:play", playX, playY, controlSize, numberProp(props, "playPress", 0.0f));
        putControlBounds(patches, "music:next", nextX, nextY, controlSize, numberProp(props, "nextPress", 0.0f));
        if (boolProp(props, "showRepeat", false)) {
            putSvgControlBounds(patches, "music:repeat", repeatX, repeatY, controlSize, numberProp(props, "repeatPress", 0.0f));
        }
    }

    private UiScriptProps buildProps(int screenW,
                                     int screenH,
                                     float width,
                                     float height,
                                     float mainWidth,
                                     float mainX,
                                     float alpha,
                                     float compactAlpha,
                                     float expandedAlpha,
                                     float bezelCut,
                                     int accent,
                                     TextRenderer titleRenderer,
                                     TextRenderer metaRenderer) {
        UiScriptProps props = UiScriptProps.create(72);
        String title = currentSnapshot != null ? currentSnapshot.title() : "";
        String artist = currentSnapshot != null ? currentSnapshot.artist() : "";
        long duration = currentSnapshot != null ? currentSnapshot.durationSeconds() : 0L;
        props.put("screenW", screenW);
        props.put("screenH", screenH);
        props.put("width", width);
        props.put("height", height);
        props.put("mainWidth", mainWidth);
        props.put("mainX", mainX);
        boolean pvpActive = PvpState.isActive();
        props.put("pvpActive", pvpActive);
        props.put("mode", currentMode.name().toLowerCase());
        props.put("alpha", alpha);
        props.put("compactAlpha", compactAlpha);
        props.put("expandedAlpha", expandedAlpha);
        props.put("expand", expandAnim);
        props.put("expanded", expanded);
        props.put("blur", blur.get());
        props.put("blurAlpha", blurAlpha.get() / 255.0f);
        props.put("bezelCut", bezelCut);
        props.put("time", formatClock(LocalTime.now()));
        props.put("pvpTelemetry", pvpActive ? formatPvpTelemetry(predictedPvpSeconds()) : "");
        ClickGuiRenderer.ClickGuiIslandState clickGuiState = ClickGuiRenderer.islandState();
        ArrayList<LinkedHashMap<String, Object>> clickGuiTabs = new ArrayList<>(clickGuiState.tabs().size());
        int clickGuiTabIndex = 0;
        for (ClickGuiRenderer.ClickGuiIslandTab tab : clickGuiState.tabs()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("label", tab.label());
            item.put("x", tab.relativeX());
            item.put("width", tab.width());
            item.put("active", tab.active());
            item.put("hovered", tab.hovered());
            item.put("color", hex(clickGuiCategoryColor(clickGuiTabIndex++)));
            clickGuiTabs.add(item);
        }
        props.put("clickGuiTabs", clickGuiTabs);
        props.put("title", title);
        props.put("artist", artist);
        props.put("titleWidthCompact", measureWidth(titleRenderer, title, 0.82f));
        props.put("titleWidthExpanded", measureWidth(titleRenderer, title, 0.86f));
        props.put("artistWidthExpanded", measureWidth(metaRenderer, artist, 0.72f));
        props.put("titleScrollTime", titleScrollSeconds(title, artist, duration));
        props.put("elapsed", formatShortTime(currentSnapshot != null ? currentSnapshot.predictedPositionSeconds() : 0L));
        props.put("total", formatShortTime(currentSnapshot != null ? currentSnapshot.durationSeconds() : 0L));
        props.put("progress", currentSnapshot != null && currentSnapshot.durationSeconds() > 0
                ? AnimationUtility.clamp((float) currentSnapshot.predictedPositionSeconds() / (float) currentSnapshot.durationSeconds(), 0f, 1f)
                : 0f);
        boolean playing = currentSnapshot != null && currentSnapshot.isPlaying();
        props.put("playing", playing);
        float waveSeconds = Util.getMillis() / 1000.0f;
        props.put("wavePhase", waveSeconds * (playing ? 1.15f : 0.0f));
        props.put("bodyHover", bodyHover);
        props.put("bodyPress", bodyPress);
        props.put("progressHover", progressHover);
        props.put("progressPress", progressPress);
        props.put("supportsSeek", currentSnapshot != null && currentSnapshot.supportsSeek());
        Identifier artwork = currentSnapshot != null ? currentSnapshot.artworkTexture() : null;
        props.put("artworkTexture", artwork != null ? artwork.toString() : "");
        int modePhosphor = currentMode == IslandMode.PVP
                ? HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.12f)
                : uiPhosphor;
        int modePhosphorDim = currentMode == IslandMode.PVP
                ? HudRenderUtil.mixColor(modePhosphor, uiDisplayBg, 0.48f)
                : uiPhosphorDim;
        int modeMatrixOff = currentMode == IslandMode.PVP
                ? HudRenderUtil.mixColor(modePhosphor, uiDisplayBg, 0.86f)
                : uiMatrixOff;
        props.put("displayBg", hex(uiDisplayBg));
        props.put("displayBgStart", hex(uiDisplayBgStart));
        props.put("displayBgEnd", hex(uiDisplayBgEnd));
        props.put("displayBgAngle", uiDisplayBgAngle);
        props.put("phosphor", hex(modePhosphor));
        props.put("phosphorDim", hex(modePhosphorDim));
        props.put("matrixOff", hex(modeMatrixOff));
        props.put("textPrimary", hex(uiTextPrimary));
        props.put("textSecondary", hex(uiTextSecondary));
        props.put("shuffleX", shuffleX - x);
        props.put("shuffleY", shuffleY - y);
        props.put("prevX", prevX - x);
        props.put("prevY", prevY - y);
        props.put("playX", playX - x);
        props.put("playY", playY - y);
        props.put("nextX", nextX - x);
        props.put("nextY", nextY - y);
        props.put("repeatX", repeatX - x);
        props.put("repeatY", repeatY - y);
        props.put("controlSize", controlSize);
        props.put("showShuffle", currentSnapshot != null && currentSnapshot.supportsShuffle());
        props.put("showRepeat", currentSnapshot != null && currentSnapshot.supportsRepeat());
        props.put("shuffleHover", shuffleHover);
        props.put("prevHover", prevHover);
        props.put("playHover", playHover);
        props.put("nextHover", nextHover);
        props.put("repeatHover", repeatHover);
        props.put("shufflePress", shufflePress);
        props.put("prevPress", prevPress);
        props.put("playPress", playPress);
        props.put("nextPress", nextPress);
        props.put("repeatPress", repeatPress);
        props.put("iconShuffle", iconString(ICON_SHUFFLE));
        props.put("iconPrev", iconString(ICON_PREV));
        props.put("iconPlay", iconString(currentSnapshot != null && currentSnapshot.isPlaying() ? ICON_PAUSE : ICON_PLAY));
        props.put("iconNext", iconString(ICON_NEXT));
        boolean shuffleActive = currentSnapshot != null && currentSnapshot.supportsShuffle() && currentSnapshot.isShuffleActive();
        RepeatMode repeatMode = currentSnapshot != null ? currentSnapshot.repeatMode() : RepeatMode.OFF;
        boolean repeatActive = currentSnapshot != null && currentSnapshot.supportsRepeat()
                && (repeatMode == RepeatMode.ALL || repeatMode == RepeatMode.ONE);
        props.put("shuffleActive", shuffleActive);
        props.put("repeatActive", repeatActive);
        props.put("shuffleColor", hex(shuffleActive ? modePhosphor : uiTextSecondary));
        props.put("repeatColor", hex(repeatActive ? modePhosphor : uiTextSecondary));
        props.put("repeatAsset", repeatSvg(currentSnapshot));
        return props;
    }

    private float titleScrollSeconds(String title, String artist, long durationSeconds) {
        String key = (title != null ? title : "") + "\u0000" + (artist != null ? artist : "");
        long now = Util.getMillis();
        if (!key.equals(titleScrollKey)) {
            titleScrollKey = key;
            titleScrollStartMs = now;
            return 0.0f;
        }
        return Math.max(0.0f, (now - titleScrollStartMs) / 1000.0f);
    }

    private UiScriptModule ensureModule() {
        if (mc == null || mc.getResourceManager() == null) return null;
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private void resetScriptRuntime() {
        scriptRuntime.reset();
    }

    private IslandMode resolveMode(MediaSessionService.Snapshot snapshot) {
        if (snapshot != null && snapshot.hasSession()) return IslandMode.MUSIC;
        if (PvpState.isActive()) return IslandMode.PVP;
        return IslandMode.TIME;
    }

    private IslandMetrics measureMetrics(TextRenderer titleRenderer,
                                         TextRenderer valueRenderer) {
        float pvpTimerW = reservedTimeWidth(valueRenderer, formatPvpTelemetry(predictedPvpSeconds()), 0.94f);
        float pvpWidth = 20f + pvpTimerW;

        String title = currentSnapshot != null ? currentSnapshot.title() : "";
        float titleW = measureWidth(titleRenderer, title, 0.82f);
        float statusWidth = PvpState.isActive() ? COMPACT_STATUS_W : 24.0f;
        float musicWidth = 82.0f + titleW + statusWidth + 14.0f;
        return new IslandMetrics(pvpWidth, clamp(musicWidth, 226f, 308f));
    }

    private void updateMusicControls(float drawX, float drawY, float width, TextRenderer valueRenderer) {
        progressX = drawX + EXPANDED_PAD_X;
        progressW = Math.max(0f, width - EXPANDED_PAD_X * 2f);
        progressY = drawY + 52f;
        float timeH = measureHeight(valueRenderer, 0.80f);

        controlSize = CONTROL_SIZE;
        float controlGap = CONTROL_GAP;
        boolean showShuffle = currentSnapshot != null && currentSnapshot.supportsShuffle();
        boolean showRepeat = currentSnapshot != null && currentSnapshot.supportsRepeat();
        int activeControls = 3 + (showShuffle ? 1 : 0) + (showRepeat ? 1 : 0);
        float totalControlsW = controlSize * activeControls + controlGap * Math.max(0, activeControls - 1);
        float controlsX = drawX + (width - totalControlsW) * 0.5f;
        float controlsY = progressY + 5f + timeH + CONTROL_ROW_GAP_Y;
        float cursorX = controlsX;

        if (showShuffle) {
            shuffleX = cursorX;
            shuffleY = controlsY;
            cursorX += controlSize + controlGap;
        } else {
            shuffleX = shuffleY = 0f;
        }
        prevX = cursorX;
        prevY = controlsY;
        cursorX += controlSize + controlGap;
        playX = cursorX;
        playY = controlsY;
        cursorX += controlSize + controlGap;
        nextX = cursorX;
        nextY = controlsY;
        cursorX += controlSize + controlGap;
        if (showRepeat) {
            repeatX = cursorX;
            repeatY = controlsY;
        } else {
            repeatX = repeatY = 0f;
        }
    }

    private boolean isInteractiveScreen(Screen screen) {
        return screen instanceof ChatScreen || isScreenOverlayAllowed(screen);
    }

    private boolean isScreenOverlayAllowed(Screen screen) {
        if (screen == null || screen instanceof ChatScreen) return false;
        if (screen instanceof ClickGuiScreen) return false;
        return !isSettingsLikeScreen(screen);
    }

    private float bossBarOffset(int logicalScreenH) {
        if (mc == null || mc.gui == null || mc.getWindow() == null) return 0f;
        BossHealthOverlay bossBarHud = mc.gui.hud.getBossOverlay();
        if (bossBarHud == null) return 0f;
        Map<UUID, LerpingBossEvent> bars;
        try {
            bars = ((BossHealthOverlayAccessor) bossBarHud).getBars();
        } catch (Throwable ignored) {
            return 0f;
        }
        if (bars == null || bars.isEmpty()) return 0f;

        int scaledH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        int y = 12;
        int rows = 0;
        for (LerpingBossEvent ignored : bars.values()) {
            rows++;
            y += 19;
            if (y >= scaledH / 3) break;
        }
        if (rows <= 0) return 0f;

        float bossBottomScaled = 17f + 19f * (rows - 1);
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        float windowScale = (float) mc.getWindow().getGuiScale();
        float bossBottomLogical = (bossBottomScaled + 8f) * windowScale / Math.max(0.001f, hudScale);
        return Math.max(0f, Math.min(logicalScreenH * 0.30f, bossBottomLogical) - TOP_Y);
    }

    private void drawFallback(Renderer2D renderer,
                              float drawX,
                              float drawY,
                              float drawWidth,
                              float drawHeight,
                              float chamfer,
                              float alpha) {
        if (blur.get()) {
            renderer.blurChamferedRect(drawX, drawY, drawWidth, drawHeight, chamfer, 8.0f, 1.0f,
                    (blurAlpha.get() / 255.0f) * alpha, 0xFFFFFF);
        }
        int start = HudRenderUtil.scaleAlpha(uiDisplayBgStart, alpha);
        int end = HudRenderUtil.scaleAlpha(uiDisplayBgEnd, alpha);
        if (start == end) {
            renderer.chamferedRect(drawX, drawY, drawWidth, drawHeight, chamfer, start);
        } else {
            renderer.chamferedRectGradient(drawX, drawY, drawWidth, drawHeight, chamfer,
                    start, end, uiDisplayBgAngle, 0.0f);
        }
        if (currentMode == IslandMode.CLICKGUI) {
            ClickGuiRenderer.ClickGuiIslandState state = ClickGuiRenderer.islandState();
            TextRenderer tabFont = ClickGuiRenderer.getOnestBold();
            int index = 0;
            for (ClickGuiRenderer.ClickGuiIslandTab tab : state.tabs()) {
                float tabX = drawX + tab.relativeX();
                float tabW = tab.width();
                float textSize = 12.0f;
                float textWidth = ClickGuiRenderer.textWidth(tabFont, tab.label(), textSize);
                int categoryColor = clickGuiCategoryColor(index++);
                if (tab.active() || tab.hovered()) {
                    renderer.roundedRect(tabX + 2.5f, drawY + 3.0f,
                            Math.max(1.0f, tabW - 5.0f), Math.max(1.0f, drawHeight - 6.0f), 2.2f,
                            HudRenderUtil.scaleAlpha(categoryColor, alpha * (tab.active() ? 0.18f : 0.08f)));
                }
                int color = tab.active() || tab.hovered() ? categoryColor : uiTextSecondary;
                ClickGuiRenderer.drawText(tabFont, tab.label(),
                        tabX + (tabW - textWidth) * 0.5f,
                        drawY + (drawHeight - textSize) * 0.5f,
                        textSize, HudRenderUtil.scaleAlpha(color, alpha), false);
                renderer.roundedRect(tabX + tabW * 0.28f, drawY + drawHeight - 6.0f,
                        Math.max(1.0f, tabW * 0.44f), tab.active() ? 1.6f : 1.0f, 0.8f,
                        HudRenderUtil.scaleAlpha(tab.active() || tab.hovered() ? categoryColor : uiMatrixOff, alpha));
            }
        }
    }

    private void updateInteractionAnimation(boolean interactive, float dt) {
        float decaySpeed = 16.0f;
        bodyPress = AnimationUtility.approach(bodyPress, 0f, dt, decaySpeed);
        progressPress = AnimationUtility.approach(progressPress, 0f, dt, decaySpeed);
        shufflePress = AnimationUtility.approach(shufflePress, 0f, dt, decaySpeed);
        prevPress = AnimationUtility.approach(prevPress, 0f, dt, decaySpeed);
        playPress = AnimationUtility.approach(playPress, 0f, dt, decaySpeed);
        nextPress = AnimationUtility.approach(nextPress, 0f, dt, decaySpeed);
        repeatPress = AnimationUtility.approach(repeatPress, 0f, dt, decaySpeed);

        if (!interactive || mc == null || mc.getWindow() == null) {
            bodyHover = AnimationUtility.approach(bodyHover, 0f, dt, 12f);
            progressHover = AnimationUtility.approach(progressHover, 0f, dt, 14f);
            shuffleHover = AnimationUtility.approach(shuffleHover, 0f, dt, 14f);
            prevHover = AnimationUtility.approach(prevHover, 0f, dt, 14f);
            playHover = AnimationUtility.approach(playHover, 0f, dt, 14f);
            nextHover = AnimationUtility.approach(nextHover, 0f, dt, 14f);
            repeatHover = AnimationUtility.approach(repeatHover, 0f, dt, 14f);
            return;
        }

        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float uiScale = HudScale.scale(fbw, fbh);
        float mx = HudScale.toVirtual((float) mc.mouseHandler.xpos(), uiScale);
        float my = HudScale.toVirtual((float) mc.mouseHandler.ypos(), uiScale);
        boolean music = currentMode == IslandMode.MUSIC;
        boolean expandedInteractive = music && expandAnim > 0.01f && controlSize > 0f;
        boolean showShuffle = currentSnapshot != null && currentSnapshot.supportsShuffle();
        boolean showRepeat = currentSnapshot != null && currentSnapshot.supportsRepeat();

        bodyHover = AnimationUtility.approach(bodyHover,
                music && hit(mx, my, mainBodyX, mainBodyY, mainBodyW, mainBodyH) ? 1f : 0f, dt, 12f);
        progressHover = AnimationUtility.approach(progressHover,
                expandedInteractive && progressW > 1f
                        && hit(mx, my, progressX - 2f, progressY - 5f, progressW + 4f, PROGRESS_H + 10f) ? 1f : 0f,
                dt, 16f);
        shuffleHover = AnimationUtility.approach(shuffleHover,
                expandedInteractive && showShuffle && hit(mx, my, shuffleX, shuffleY, controlSize, controlSize) ? 1f : 0f, dt, 16f);
        prevHover = AnimationUtility.approach(prevHover,
                expandedInteractive && hit(mx, my, prevX, prevY, controlSize, controlSize) ? 1f : 0f, dt, 16f);
        playHover = AnimationUtility.approach(playHover,
                expandedInteractive && hit(mx, my, playX, playY, controlSize, controlSize) ? 1f : 0f, dt, 16f);
        nextHover = AnimationUtility.approach(nextHover,
                expandedInteractive && hit(mx, my, nextX, nextY, controlSize, controlSize) ? 1f : 0f, dt, 16f);
        repeatHover = AnimationUtility.approach(repeatHover,
                expandedInteractive && showRepeat && hit(mx, my, repeatX, repeatY, controlSize, controlSize) ? 1f : 0f, dt, 16f);
    }

    private void resetControls() {
        shuffleX = shuffleY = 0f;
        prevX = prevY = 0f;
        playX = playY = 0f;
        nextX = nextY = 0f;
        repeatX = repeatY = 0f;
        progressX = progressY = progressW = 0f;
        controlSize = 0f;
        shuffleHover = 0f;
        prevHover = 0f;
        playHover = 0f;
        nextHover = 0f;
        repeatHover = 0f;
        progressHover = 0f;
        shufflePress = 0f;
        prevPress = 0f;
        playPress = 0f;
        nextPress = 0f;
        repeatPress = 0f;
        progressPress = 0f;
    }

    private int accentFor(IslandMode mode) {
        int syncedAccent = syncTheme.get() ? theme().accent() : (accentColor.getArgb() | 0xFF000000);
        return switch (mode) {
            case TIME -> syncedAccent;
            case MUSIC -> syncedAccent;
            case PVP -> STATUS_PVP;
            case CLICKGUI -> syncedAccent;
        };
    }

    private void updatePalette() {
        if (syncTheme.get()) {
            int alpha = themeAlpha.get();
            float mix = themeMix.get() / 100.0f;
            int accent = theme().accent();
            int baseSurface = HudRenderUtil.setAlpha(
                    HudRenderUtil.mixColor(theme().windowBg(), theme().surface(), 0.38f), alpha);

            uiDisplayBgAngle = 90.0f;
            if (HudRenderUtil.PANEL_STYLE_GRADIENT.equals(panelStyle.get())) {
                float strength = (themeGradientStrength.get() / 100.0f) * mix;
                HudRenderUtil.ThemeGradient gradient = HudRenderUtil.themePanelGradient(alpha);
                uiDisplayBgStart = HudRenderUtil.gradientSurface(baseSurface, gradient.start(), strength);
                uiDisplayBgEnd = HudRenderUtil.gradientSurface(baseSurface, gradient.end(), strength);
                uiDisplayBgAngle = gradient.angleDeg();
                uiDisplayBg = HudRenderUtil.mixColor(uiDisplayBgStart, uiDisplayBgEnd, 0.5f);
            } else if (HudRenderUtil.PANEL_STYLE_ACCENT.equals(panelStyle.get())) {
                uiDisplayBg = HudRenderUtil.accentSurface(baseSurface, 0.42f * mix);
                uiDisplayBgStart = uiDisplayBg;
                uiDisplayBgEnd = uiDisplayBg;
            } else {
                uiDisplayBg = baseSurface;
                uiDisplayBgStart = baseSurface;
                uiDisplayBgEnd = baseSurface;
            }

            uiPhosphor = HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.22f);
            uiPhosphorDim = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(uiPhosphor, uiDisplayBg, 0.48f), 255);
            uiMatrixOff = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(uiPhosphor, uiDisplayBg, 0.86f), 255);
            uiTextPrimary = theme().textPrimary();
            uiTextSecondary = theme().textMuted();
            return;
        }

        int baseBg = bgColor.getArgb();
        int accent = accentColor.getArgb() | 0xFF000000;
        uiDisplayBg = HudRenderUtil.setAlpha(HudRenderUtil.mixRgb(baseBg, 0xFF000000, 0.60f), 132);
        uiDisplayBgStart = uiDisplayBg;
        uiDisplayBgEnd = uiDisplayBg;
        uiDisplayBgAngle = 90.0f;
        uiPhosphor = HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.22f);
        uiPhosphorDim = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(uiPhosphor, uiDisplayBg, 0.48f), 255);
        uiMatrixOff = HudRenderUtil.setAlpha(HudRenderUtil.mixColor(uiPhosphor, uiDisplayBg, 0.86f), 255);
        uiTextPrimary = textColor.getArgb() | 0xFF000000;
        uiTextSecondary = mutedColor.getArgb() | 0xFF000000;
    }

    private float predictedPvpSeconds() {
        Float secondsLeft = PvpState.getSecondsLeft();
        if (!PvpState.isActive() || secondsLeft == null) return 0f;
        long sinceUpdate = Math.max(0L, Util.getMillis() - PvpState.getLastUpdateMs());
        return Math.max(0f, secondsLeft - sinceUpdate / 1000f);
    }

    private float measureWidth(TextRenderer renderer, String text, float scale) {
        if (renderer == null || text == null || text.isEmpty()) return 0f;
        renderer.begin(scale, true, false);
        try {
            return (float) renderer.getWidth(text, false);
        } finally {
            renderer.end();
        }
    }

    private float measureHeight(TextRenderer renderer, float scale) {
        if (renderer == null) return 0f;
        renderer.begin(scale, true, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    private float reservedTimeWidth(TextRenderer renderer, String text, float scale) {
        return measureWidth(renderer, reserveTimeTemplate(text), scale);
    }

    private String reserveTimeTemplate(String text) {
        if (text == null || text.isEmpty()) return "0:00";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(Character.isDigit(c) ? '8' : c);
        }
        return out.toString();
    }

    private enum IslandMode {
        TIME,
        MUSIC,
        PVP,
        CLICKGUI
    }

    private record IslandMetrics(float pvpWidth, float musicCompactWidth) {
    }
}
