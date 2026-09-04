/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

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
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.media.MediaSessionService;
import combatant.client.util.media.RepeatMode;
import combatant.client.util.pvp.PvpState;

import java.time.LocalTime;
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
    private static final float CLOSED_RADIUS = 17f;
    private static final float EXPANDED_RADIUS = 20f;

    private static final float TIME_WIDTH = 74f;
    private static final float PVP_MIN_WIDTH = 68f;
    private static final float PVP_MAX_WIDTH = 96f;
    private static final float MUSIC_EXPANDED_WIDTH = 338f;

    private static final float CONTEXT_GAP = 7f;
    private static final float CONTEXT_CHIP_H = 20f;
    private static final float CONTEXT_CHIP_Y = 7.5f;
    private static final float TIME_CONTEXT_WIDTH = 57f;
    private static final float PVP_CONTEXT_WIDTH = 46f;

    private static final float COMPACT_PAD_X = 11f;
    private static final float COMPACT_GAP = 9f;
    private static final float COMPACT_ART = 22f;
    private static final float COMPACT_WAVE_W = 15f;
    private static final float COMPACT_FADE = 15f;
    private static final float EXPANDED_PAD_X = 13f;
    private static final float EXPANDED_PAD_Y = 11f;
    private static final float EXPANDED_ART = 36f;
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

    private static final int BASE_SHADOW = 0xFF000000;
    private static final int BASE_STROKE = 0xFFFFFFFF;
    private static final int STATUS_PVP = 0xFFFF6368;
    private static final int PROGRESS_BG = 0x2AFFFFFF;

    private final Minecraft mc = Minecraft.getInstance();
    private final MediaSessionService mediaService = MediaSessionService.get();
    private final BooleanValue syncTheme = new BooleanValue("dynamic_island_sync_theme", true);
    private final NumberValue<Integer> themeAlpha = new NumberValue<>("dynamic_island_theme_alpha", 200, 0, 255);
    private final BooleanValue blur = new BooleanValue("dynamic_island_blur", false);
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
    private float leftContextWidthAnim;
    private float rightContextWidthAnim;
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

    private int uiFillTop;
    private int uiFillBottom;
    private int uiTextPrimary;
    private int uiTextSecondary;
    private int uiTextMuted;
    private int uiAccent;
    private int uiAccentSoft;

    private DynamicIsland() {
        super("dynamic_island", "Dynamic Island", true);
    }

    public static boolean shouldRenderInScreenOverlay(Screen screen) {
        return INSTANCE != null && INSTANCE.isScreenOverlayAllowed(screen);
    }

    public static void renderScreenOverlay(GuiGraphicsExtractor ctx, float tickDelta) {
        INSTANCE.renderScreenOverlayInternal(ctx, tickDelta);
    }

    public static boolean shouldOwnClickGuiTabShell() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        return INSTANCE.clickGuiShellVisible;
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
        putBounds(patches, key + ":hover", x + haloOffset, y + haloOffset, haloSize, haloSize);
        putBounds(patches, key, x + (size - iconW) * 0.5f, y + 4.0f + ((size - 4.0f) - iconH) * 0.5f, iconW, iconH);
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
        putBounds(patches, key + ":hover", x + haloOffset, y + haloOffset, haloSize, haloSize);
        putBounds(patches, key, x + (size - iconSize) * 0.5f, y + (size - iconSize) * 0.5f, iconSize, iconSize);
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
                                     String accentSoft,
                                     float expandedAlpha,
                                     float hover,
                                     float press,
                                     boolean primary) {
        patchText(patches, key, icon, scaleHexAlpha(iconColor, expandedAlpha));
        float interaction = (primary ? 0.13f : 0.0f)
                + Math.max(0.0f, hover) * 0.23f
                + Math.max(0.0f, press) * 0.34f;
        patchShape(patches, key + ":hover", "fill", scaleHexAlpha(accentSoft, interaction * expandedAlpha));
    }

    private static void patchSvgControl(UiScriptPatchSet patches,
                                        String key,
                                        String asset,
                                        String iconColor,
                                        String accentSoft,
                                        float expandedAlpha,
                                        float hover,
                                        float press) {
        patchImage(patches, key, asset, scaleHexAlpha(iconColor, expandedAlpha));
        float interaction = Math.max(0.0f, hover) * 0.23f + Math.max(0.0f, press) * 0.34f;
        patchShape(patches, key + ":hover", "fill", scaleHexAlpha(accentSoft, interaction * expandedAlpha));
    }

    private static void patchText(UiScriptPatchSet patches,
                                  String key,
                                  String text,
                                  String color) {
        patches.text(key, text, color);
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

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String scaleHexAlpha(String hex, float amount) {
        return UiScriptColor.alpha(hex, amount);
    }

    private static int shuffleColor(MediaSessionService.Snapshot snapshot) {
        if (snapshot == null || !snapshot.supportsShuffle()) {
            return 0x99000000;
        }
        return snapshot.isShuffleActive() ? 0xFFFFFFFF : 0x7AB7BEC8;
    }

    private static int repeatColor(MediaSessionService.Snapshot snapshot) {
        if (snapshot == null || !snapshot.supportsRepeat()) {
            return 0x99000000;
        }
        RepeatMode mode = snapshot.repeatMode();
        return mode == RepeatMode.ALL || mode == RepeatMode.ONE ? 0xFFFFFFFF : 0x7AB7BEC8;
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

    private static String formatSecondsOnly(float seconds) {
        return Math.max(0L, (long) Math.ceil(Math.max(0f, seconds))) + "s";
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

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bool(syncTheme));
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
        TextRenderer titleRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, fallback);
        TextRenderer valueRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, fallback);
        TextRenderer metaRenderer = Fonts.renderer("Inter", FontInfo.Type.Regular, fallback);

        IslandMetrics metrics = measureMetrics(titleRenderer, valueRenderer, metaRenderer);
        float targetMainWidth = switch (currentMode) {
            case TIME -> TIME_WIDTH;
            case PVP -> clamp(metrics.pvpWidth, PVP_MIN_WIDTH, PVP_MAX_WIDTH);
            case MUSIC -> AnimationUtility.lerp(metrics.musicCompactWidth, MUSIC_EXPANDED_WIDTH, expandAnim);
            case CLICKGUI -> clickGuiState.tabBarW();
        };
        float targetHeight = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarH()
                : AnimationUtility.lerp(CLOSED_HEIGHT, currentMode == IslandMode.MUSIC ? EXPANDED_HEIGHT : CLOSED_HEIGHT, expandAnim);

        boolean musicContext = currentMode == IslandMode.MUSIC;
        boolean clickGuiContext = currentMode == IslandMode.CLICKGUI;
        float contextPresence = musicContext ? 1.0f - AnimationUtility.easeOutCubic(expandAnim) : (clickGuiContext ? 1.0f : 0.0f);
        float targetLeftContextWidth = TIME_CONTEXT_WIDTH * contextPresence;
        float targetRightContextWidth = PvpState.isActive() ? PVP_CONTEXT_WIDTH * contextPresence : 0.0f;

        mainWidthAnim = mainWidthAnim < 0f ? targetMainWidth : AnimationUtility.approach(mainWidthAnim, targetMainWidth, dt, 12f);
        heightAnim = heightAnim < 0f ? targetHeight : AnimationUtility.approach(heightAnim, targetHeight, dt, 12f);
        leftContextWidthAnim = AnimationUtility.approach(leftContextWidthAnim, targetLeftContextWidth, dt, 13f);
        rightContextWidthAnim = AnimationUtility.approach(rightContextWidthAnim, targetRightContextWidth, dt, 13f);
        mainWidthAnim = AnimationUtility.snap(mainWidthAnim, targetMainWidth, 0.05f);
        heightAnim = AnimationUtility.snap(heightAnim, targetHeight, 0.05f);
        leftContextWidthAnim = AnimationUtility.snap(leftContextWidthAnim, targetLeftContextWidth, 0.05f);
        rightContextWidthAnim = AnimationUtility.snap(rightContextWidthAnim, targetRightContextWidth, 0.05f);

        float drawMainWidth = mainWidthAnim;
        float drawHeight = heightAnim;
        float leftOccupied = leftContextWidthAnim > 0.5f ? leftContextWidthAnim + CONTEXT_GAP : 0.0f;
        float rightOccupied = rightContextWidthAnim > 0.5f ? CONTEXT_GAP + rightContextWidthAnim : 0.0f;
        float drawWidth = leftOccupied + drawMainWidth + rightOccupied;
        widthAnim = drawWidth;
        float drawX = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarX() - leftOccupied
                : (screenW - drawMainWidth) * 0.5f - leftOccupied;
        float drawY = currentMode == IslandMode.CLICKGUI
                ? clickGuiState.tabBarY() - (1.0f - AnimationUtility.easeOutCubic(clickGuiState.lifecycle())) * 18.0f
                : TOP_Y + bossBarOffset(screenH);
        float mainX = leftOccupied;
        float alpha = AnimationUtility.easeOutCubic(visibilityAnim);
        float radius = currentMode == IslandMode.CLICKGUI ? drawHeight * 0.5f : AnimationUtility.lerp(CLOSED_RADIUS, EXPANDED_RADIUS, expandAnim);
        int accent = accentFor(currentMode);

        setBounds(drawX, drawY, drawWidth, drawHeight);
        mainBodyX = drawX + mainX;
        mainBodyY = drawY;
        mainBodyW = drawMainWidth;
        mainBodyH = drawHeight;

        float expandedContentAlpha = currentMode == IslandMode.MUSIC ? AnimationUtility.easeOutCubic(expandAnim) * alpha : 0f;
        float compactContentAlpha = currentMode == IslandMode.MUSIC ? (1f - AnimationUtility.easeOutCubic(expandAnim)) * alpha : 0f;
        if (expandedContentAlpha > 0.01f) {
            updateMusicControls(drawX + mainX, drawY, drawMainWidth, valueRenderer);
        } else {
            resetControls();
        }
        updateInteractionAnimation(isInteractiveScreen(currentScreen), dt);

        if (!renderScripted(renderer, fallback, titleRenderer, metaRenderer, ctx, tickDelta, screenW, screenH, drawWidth, drawHeight,
                drawMainWidth, mainX, leftContextWidthAnim, rightContextWidthAnim,
                alpha, compactContentAlpha, expandedContentAlpha, radius, accent)) {
            drawFallback(renderer, drawX + mainX, drawY, drawMainWidth, drawHeight, radius, alpha, accent);
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
                                   float leftContextWidth,
                                   float rightContextWidth,
                                   float alpha,
                                   float compactAlpha,
                                   float expandedAlpha,
                                   float radius,
                                   int accent) {
        if (moduleHandle.isRuntimeBlocked()) return false;

        UiScriptModule loaded = ensureModule();
        if (loaded == null) return false;

        UiScriptProps props = buildProps(
                screenW, screenH, width, height, mainWidth, mainX, leftContextWidth, rightContextWidth,
                alpha, compactAlpha, expandedAlpha, radius, accent,
                titleRenderer, metaRenderer
        );
        long treeSignature = islandStructuralSignature(props);
        long dataSignature = CachedUiScriptRuntime.signature(props.asMap());
        long layoutSignature = treeSignature;
        UiRuntime baked = scriptRuntime.bake(
                moduleHandle,
                loaded,
                "dynamic_island",
                treeSignature,
                dataSignature,
                layoutSignature,
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
        h = CachedUiScriptRuntime.mix(h, numberProp(props, "blurAlpha", 0.0f));
        h = CachedUiScriptRuntime.mix(h, !stringProp(props, "artworkTexture", "").isEmpty());
        h = CachedUiScriptRuntime.mix(h, "music".equals(stringProp(props, "mode", "")));
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "showShuffle", false));
        h = CachedUiScriptRuntime.mix(h, boolProp(props, "showRepeat", false));
        return h;
    }

    private Map<String, ? extends Map<String, ?>> islandRuntimePatches(UiScriptProps props) {
        UiScriptPatchSet patches = UiScriptPatchSet.create(48);
        float compactAlpha = numberProp(props, "compactAlpha", 0.0f);
        float expandedAlpha = numberProp(props, "expandedAlpha", 0.0f);
        float contextAlpha = numberProp(props, "contextAlpha", compactAlpha);
        float width = numberProp(props, "width", 0.0f);
        float mainWidth = numberProp(props, "mainWidth", width);
        float progress = clamp01(numberProp(props, "progress", 0.0f));
        String mode = stringProp(props, "mode", "");
        boolean musicMode = "music".equals(mode);
        boolean clickGuiMode = "clickgui".equals(mode);
        boolean shellMode = musicMode || clickGuiMode;
        boolean pvpMode = "pvp".equals(mode);
        boolean timeMode = "time".equals(mode);
        String primary = stringProp(props, "textPrimary", "#FFFFFFFF");
        String secondary = stringProp(props, "textSecondary", "#99FFFFFF");
        String muted = stringProp(props, "textMuted", "#66FFFFFF");
        String accent = stringProp(props, "accent", "#FFFFFFFF");
        String accentSoft = stringProp(props, "accentSoft", "#33FFFFFF");

        if (shellMode) {
            float shellBlurAlpha = numberProp(props, "blurAlpha", 0.0f) * numberProp(props, "alpha", 0.0f);
            patchShape(patches, "dynamic-island", "renderRadius", props.get("radius"), "renderBlurAlpha", shellBlurAlpha);
            patchShape(patches, "shell:blur", "blurAlpha", shellBlurAlpha, "radius", props.get("radius"));
            patchShape(patches, "shell:shadow", "fill", props.get("shadow"), "radius", props.get("radius"));
            patchShape(patches, "shell:fill", "startColor", props.get("fillTop"), "endColor", props.get("fillBottom"), "radius", props.get("radius"));
            patchShape(patches, "shell:tint", "startColor", props.get("tintTop"), "endColor", props.get("tintBottom"), "radius", props.get("radius"));
            patchShape(patches, "shell:stroke", "stroke", props.get("stroke"), "radius", props.get("radius"));
            float shellInteraction = numberProp(props, "bodyHover", 0.0f) * 0.07f
                    + numberProp(props, "bodyPress", 0.0f) * 0.13f;
            patchShape(patches, "shell:interaction",
                    "fill", scaleHexAlpha(accentSoft, shellInteraction),
                    "radius", props.get("radius"));
        }

        if (timeMode) {
            patchShape(patches, "time:box", "fill", props.get("timeFill"), "stroke", props.get("timeStroke"));
            patchText(patches, "time", stringProp(props, "time", ""), primary);
        }

        if (pvpMode) {
            patchShape(patches, "pvp:chip", "fill", props.get("pvpFill"), "stroke", props.get("pvpStroke"));
            patchText(patches, "pvp:timer", stringProp(props, "pvpTimer", "0s"), primary);
        }

        if (musicMode || clickGuiMode) {
            float contextBlurAlpha = numberProp(props, "blurAlpha", 0.0f) * contextAlpha;
            patchShape(patches, "context:time:blur", "blurAlpha", contextBlurAlpha, "radius", 10.0f);
            patchShape(patches, "context:time:box", "fill", scaleHexAlpha(stringProp(props, "contextFill", "#00000000"), contextAlpha), "stroke", scaleHexAlpha(stringProp(props, "contextStroke", "#00000000"), contextAlpha));
            patchText(patches, "context:time", stringProp(props, "time", ""), scaleHexAlpha(primary, contextAlpha));
            float pvpContextAlpha = boolProp(props, "pvpContextVisible", false) ? contextAlpha : 0.0f;
            patchShape(patches, "context:pvp:blur", "blurAlpha", numberProp(props, "blurAlpha", 0.0f) * pvpContextAlpha, "radius", 10.0f);
            patchShape(patches, "context:pvp:box", "fill", scaleHexAlpha(stringProp(props, "pvpFill", "#00000000"), pvpContextAlpha), "stroke", scaleHexAlpha(stringProp(props, "pvpStroke", "#00000000"), pvpContextAlpha));
            patchText(patches, "context:pvp", stringProp(props, "pvpTimer", ""), scaleHexAlpha(primary, pvpContextAlpha));
        }

        if (musicMode) {
            patchText(patches, "music:elapsed:compact", stringProp(props, "elapsed", "0:00"), scaleHexAlpha(secondary, compactAlpha));
            patchText(patches, "music:title:compact", stringProp(props, "title", ""), scaleHexAlpha(primary, compactAlpha));
            patchClippedText(patches, "music:title:compact", numberProp(props, "titleWidthCompact", 0.0f), Math.max(0.0f, mainWidth - 124.0f), numberProp(props, "titleScrollTime", 0.0f), 1.0f, 18.0f, 15.0f);
            patchShape(patches, "music:divider", "fill", scaleHexAlpha(muted, 0.4f * compactAlpha));
            patchShape(patches, "wave:0", "fill", scaleHexAlpha(accent, 0.75f * compactAlpha));
            patchShape(patches, "wave:1", "fill", scaleHexAlpha(accent, 0.83f * compactAlpha));
            patchShape(patches, "wave:2", "fill", scaleHexAlpha(accent, 0.91f * compactAlpha));
            patchImage(patches, "artwork:compact", stringProp(props, "artworkTexture", ""), scaleHexAlpha("#FFFFFFFF", compactAlpha));
            patchShape(patches, "artwork:compact:fallback", "fill", scaleHexAlpha(accent, 0.92f * compactAlpha), "stroke", scaleHexAlpha("#33FFFFFF", compactAlpha));
        }

        if (musicMode) {
            patchText(patches, "music:title:expanded", stringProp(props, "title", ""), scaleHexAlpha(primary, expandedAlpha));
            patchClippedText(patches, "music:title:expanded", numberProp(props, "titleWidthExpanded", 0.0f), Math.max(0.0f, mainWidth - 72.0f), numberProp(props, "titleScrollTime", 0.0f), 1.0f, 18.0f, 16.0f);
            patchText(patches, "music:artist:expanded", stringProp(props, "artist", ""), scaleHexAlpha(secondary, expandedAlpha));
            patchClippedText(patches, "music:artist:expanded", numberProp(props, "artistWidthExpanded", 0.0f), Math.max(0.0f, mainWidth - 72.0f), numberProp(props, "titleScrollTime", 0.0f), 1.4f, 14.0f, 16.0f);
            float progressFocus = Math.max(numberProp(props, "progressHover", 0.0f), numberProp(props, "progressPress", 0.0f));
            patchShape(patches, "music:progress:bg",
                    "fill", scaleHexAlpha(stringProp(props, "progressBg", "#00FFFFFF"), expandedAlpha * (0.86f + 0.14f * progressFocus)));
            float progressWidth = Math.max(0.0f, mainWidth - 26.0f);
            float waveSpan = progressWidth * progress;
            float waveSpanFactor = clamp01(waveSpan / 24.0f);
            waveSpanFactor = waveSpanFactor * waveSpanFactor * (3.0f - 2.0f * waveSpanFactor);
            float waveAmplitude = boolProp(props, "playing", false)
                    ? (2.15f + 0.30f * progressFocus) * waveSpanFactor
                    : 0.0f;
            patchShape(patches, "music:progress:wave",
                    "x2", waveSpan,
                    "phase", props.get("wavePhase"),
                    "amplitude", waveAmplitude,
                    "strokeWidth", 4.6f + 0.65f * progressFocus,
                    "startColor", scaleHexAlpha(accent, expandedAlpha),
                    "endColor", scaleHexAlpha(accent, expandedAlpha));
            patchShape(patches, "music:progress:thumb",
                    "fill", scaleHexAlpha(primary, expandedAlpha * (0.94f + 0.06f * progressFocus)));
            patchText(patches, "music:elapsed:expanded", stringProp(props, "elapsed", "0:00"), scaleHexAlpha(secondary, expandedAlpha));
            patchText(patches, "music:total:expanded", stringProp(props, "total", "0:00"), scaleHexAlpha(secondary, expandedAlpha));
            patchImage(patches, "artwork:expanded", stringProp(props, "artworkTexture", ""), scaleHexAlpha("#FFFFFFFF", expandedAlpha));
            patchShape(patches, "artwork:expanded:fallback", "fill", scaleHexAlpha(accent, 0.92f * expandedAlpha), "stroke", scaleHexAlpha("#33FFFFFF", expandedAlpha));
            if (boolProp(props, "showShuffle", false)) {
                patchControl(patches, "music:shuffle", stringProp(props, "iconShuffle", ""), stringProp(props, "shuffleColor", primary), accentSoft, expandedAlpha, numberProp(props, "shuffleHover", 0.0f), numberProp(props, "shufflePress", 0.0f), false);
            }
            patchControl(patches, "music:prev", stringProp(props, "iconPrev", ""), primary, accentSoft, expandedAlpha, numberProp(props, "prevHover", 0.0f), numberProp(props, "prevPress", 0.0f), false);
            patchControl(patches, "music:play", stringProp(props, "iconPlay", ""), primary, accentSoft, expandedAlpha, numberProp(props, "playHover", 0.0f), numberProp(props, "playPress", 0.0f), true);
            patchControl(patches, "music:next", stringProp(props, "iconNext", ""), primary, accentSoft, expandedAlpha, numberProp(props, "nextHover", 0.0f), numberProp(props, "nextPress", 0.0f), false);
            if (boolProp(props, "showRepeat", false)) {
                patchSvgControl(patches, "music:repeat", stringProp(props, "repeatAsset", "repeat-off"), stringProp(props, "repeatColor", "#99000000"), accentSoft, expandedAlpha, numberProp(props, "repeatHover", 0.0f), numberProp(props, "repeatPress", 0.0f));
            }
        }

        return patches.asMap();
    }

    private Map<String, UiBounds> islandBoundsPatches(UiScriptProps props) {
        UiBoundsPatchSet patches = UiBoundsPatchSet.create(48);
        float rootX = x;
        float rootY = y;
        float width = numberProp(props, "width", 0.0f);
        float height = numberProp(props, "height", 0.0f);
        float mainX = numberProp(props, "mainX", 0.0f);
        float mainWidth = numberProp(props, "mainWidth", width);
        float leftContextWidth = numberProp(props, "leftContextWidth", 0.0f);
        float rightContextWidth = numberProp(props, "rightContextWidth", 0.0f);
        String mode = stringProp(props, "mode", "");
        boolean musicMode = "music".equals(mode);
        boolean clickGuiMode = "clickgui".equals(mode);

        putBounds(patches, "dynamic-island", rootX, rootY, width, height);
        putBounds(patches, "content", rootX, rootY, width, height);

        if (musicMode || clickGuiMode) {
            putBounds(patches, "shell:blur", rootX + mainX, rootY, mainWidth, height);
            putBounds(patches, "shell:shadow", rootX + mainX, rootY, mainWidth, height);
            putBounds(patches, "shell:fill", rootX + mainX, rootY, mainWidth, height);
            putBounds(patches, "shell:tint", rootX + mainX, rootY, mainWidth, height);
            putBounds(patches, "shell:stroke", rootX + mainX, rootY, mainWidth, height);
            putBounds(patches, "shell:interaction", rootX + mainX, rootY, mainWidth, height);
        }

        if (musicMode || clickGuiMode) {
            putBounds(patches, "context:time:blur", rootX, rootY + CONTEXT_CHIP_Y, leftContextWidth, CONTEXT_CHIP_H);
            putBounds(patches, "context:time:box", rootX, rootY + CONTEXT_CHIP_Y, leftContextWidth, CONTEXT_CHIP_H);
            putBounds(patches, "context:time", rootX, rootY + 10.1f, leftContextWidth, 14.0f);
            float rightX = mainX + mainWidth + CONTEXT_GAP;
            putBounds(patches, "context:pvp:blur", rootX + rightX, rootY + CONTEXT_CHIP_Y, rightContextWidth, CONTEXT_CHIP_H);
            putBounds(patches, "context:pvp:box", rootX + rightX, rootY + CONTEXT_CHIP_Y, rightContextWidth, CONTEXT_CHIP_H);
            putBounds(patches, "context:pvp", rootX + rightX, rootY + 10.1f, rightContextWidth, 14.0f);
        }

        if (musicMode) {
            putMusicCompactBounds(patches, rootX, rootY, props, mainX, mainWidth);
            putMusicExpandedBounds(patches, rootX, rootY, props, mainWidth);
        } else if ("pvp".equals(mode)) {
            float chipW = Math.min(mainWidth - 16.0f, Math.max(42.0f, PVP_CONTEXT_WIDTH + 8.0f));
            float chipX = mainX + (mainWidth - chipW) * 0.5f;
            putBounds(patches, "pvp:chip", rootX + chipX, rootY + CONTEXT_CHIP_Y, chipW, CONTEXT_CHIP_H);
            putBounds(patches, "pvp:timer", rootX + chipX, rootY + 10.1f, chipW, 14.0f);
        } else {
            putBounds(patches, "time:box", rootX + mainX, rootY + CONTEXT_CHIP_Y, mainWidth, CONTEXT_CHIP_H);
            putBounds(patches, "time", rootX + mainX, rootY + 7.8f, mainWidth, 20.0f);
        }

        return patches.asMap();
    }

    private void putMusicCompactBounds(UiBoundsPatchSet patches,
                                       float rootX,
                                       float rootY,
                                       UiScriptProps props,
                                       float mainX,
                                       float width) {
        putBounds(patches, "music:elapsed:compact", rootX + mainX + 11.0f, rootY + 10.5f, 38.0f, 13.0f);
        putBounds(patches, "music:divider", rootX + mainX + 49.0f, rootY + 7.0f, 1.0f, 18.0f);
        putBounds(patches, "artwork:compact", rootX + mainX + 58.0f, rootY + 6.5f, 22.0f, 22.0f);
        putBounds(patches, "artwork:compact:fallback", rootX + mainX + 58.0f, rootY + 6.5f, 22.0f, 22.0f);
        float titleW = Math.max(0.0f, width - 124.0f);
        putBounds(patches, "music:title:compact:clip", rootX + mainX + 89.0f, rootY + 8.5f, titleW, 18.0f);
        putBounds(patches, "music:title:compact", rootX + mainX + 89.0f, rootY + 8.5f, titleW, 18.0f);

        float waveX = mainX + width - 26.0f;
        float centerY = 17.5f;
        float t = Util.getMillis() / 1000.0f;
        boolean playing = boolProp(props, "playing", false);
        for (int i = 0; i < 3; i++) {
            float barH = playing
                    ? (float) (3.0f + Math.abs(Math.sin(t * 5.2f + i * 0.85f)) * 8.0f)
                    : 4.0f + i;
            putBounds(patches, "wave:" + i, rootX + waveX + i * 5.0f, rootY + centerY - barH * 0.5f, 2.5f, barH);
        }
    }

    private void putMusicExpandedBounds(UiBoundsPatchSet patches,
                                        float rootX,
                                        float rootY,
                                        UiScriptProps props,
                                        float width) {
        float mainX = numberProp(props, "mainX", 0.0f);
        float progressW = Math.max(0.0f, width - 26.0f);
        putBounds(patches, "artwork:expanded", rootX + mainX + 13.0f, rootY + 11.0f, 36.0f, 36.0f);
        putBounds(patches, "artwork:expanded:fallback", rootX + mainX + 13.0f, rootY + 11.0f, 36.0f, 36.0f);
        float titleW = Math.max(0.0f, width - 72.0f);
        putBounds(patches, "music:title:expanded:clip", rootX + mainX + 59.0f, rootY + 11.0f, titleW, 16.0f);
        putBounds(patches, "music:title:expanded", rootX + mainX + 59.0f, rootY + 11.0f, titleW, 16.0f);
        putBounds(patches, "music:artist:expanded:clip", rootX + mainX + 59.0f, rootY + 28.0f, titleW, 14.0f);
        putBounds(patches, "music:artist:expanded", rootX + mainX + 59.0f, rootY + 28.0f, titleW, 14.0f);
        float progressFocus = Math.max(numberProp(props, "progressHover", 0.0f), numberProp(props, "progressPress", 0.0f));
        float progress = clamp01(numberProp(props, "progress", 0.0f));
        float activeW = progressW * progress;
        float inactiveH = 3.0f + 0.75f * progressFocus;
        float inactiveY = 54.5f - inactiveH * 0.5f;
        putBounds(patches, "music:progress:bg",
                rootX + mainX + 13.0f + activeW,
                rootY + inactiveY,
                Math.max(0.0f, progressW - activeW),
                inactiveH);
        putBounds(patches, "music:progress:wave", rootX + mainX + 13.0f, rootY + 48.0f, progressW, 13.0f);
        float thumbSize = 7.5f + 1.5f * progressFocus;
        float thumbCenterX = rootX + mainX + 13.0f + activeW;
        float thumbCenterY = rootY + 54.5f;
        putBounds(patches, "music:progress:thumb", thumbCenterX - thumbSize * 0.5f, thumbCenterY - thumbSize * 0.5f, thumbSize, thumbSize);
        putBounds(patches, "music:elapsed:expanded", rootX + mainX + 13.0f, rootY + 65.0f, 44.0f, 14.0f);
        putBounds(patches, "music:total:expanded", rootX + mainX + width - 57.0f, rootY + 65.0f, 44.0f, 14.0f);

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
                                                                float leftContextWidth,
                                                                float rightContextWidth,
                                                                float alpha,
                                                                float compactAlpha,
                                                                float expandedAlpha,
                                                                float radius,
                                                                int accent,
                                                                TextRenderer titleRenderer,
                                                                TextRenderer metaRenderer) {
        UiScriptProps props = UiScriptProps.create(80);
        String title = currentSnapshot != null ? currentSnapshot.title() : "";
        String artist = currentSnapshot != null ? currentSnapshot.artist() : "";
        long duration = currentSnapshot != null ? currentSnapshot.durationSeconds() : 0L;
        props.put("screenW", screenW);
        props.put("screenH", screenH);
        props.put("width", width);
        props.put("height", height);
        props.put("mainWidth", mainWidth);
        props.put("mainX", mainX);
        props.put("leftContextWidth", leftContextWidth);
        props.put("rightContextWidth", rightContextWidth);
        props.put("contextGap", CONTEXT_GAP);
        boolean pvpContextVisible = (currentMode == IslandMode.MUSIC || currentMode == IslandMode.CLICKGUI) && PvpState.isActive() && rightContextWidth > 0.5f;
        props.put("pvpContextVisible", pvpContextVisible);
        props.put("contextAlpha", currentMode == IslandMode.MUSIC ? compactAlpha : (currentMode == IslandMode.CLICKGUI ? alpha : 0.0f));
        props.put("mode", currentMode.name().toLowerCase());
        props.put("alpha", alpha);
        props.put("compactAlpha", compactAlpha);
        props.put("expandedAlpha", expandedAlpha);
        props.put("expand", expandAnim);
        props.put("expanded", expanded);
        props.put("blur", blur.get());
        props.put("blurAlpha", blurAlpha.get() / 255.0f);
        props.put("radius", radius);
        props.put("time", formatClock(LocalTime.now()));
        props.put("pvpTimer", pvpContextVisible || currentMode == IslandMode.PVP ? formatSecondsOnly(predictedPvpSeconds()) : "");
        ClickGuiRenderer.ClickGuiIslandState clickGuiState = ClickGuiRenderer.islandState();
        props.put("clickGuiLabel", clickGuiState.activeLabel());
        props.put("clickGuiTabCount", clickGuiState.tabCount());
        props.put("clickGuiPicker", clickGuiState.pickerActive());
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
        props.put("fillTop", hex(HudRenderUtil.scaleAlpha(uiFillTop, 0.88f * alpha)));
        props.put("fillBottom", hex(HudRenderUtil.scaleAlpha(uiFillBottom, 0.88f * alpha)));
        props.put("tintTop", hex(HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.10f), 0.16f * alpha)));
        props.put("tintBottom", hex(HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, 0xFF000000, 0.25f), 0.112f * alpha)));
        props.put("shadow", hex(HudRenderUtil.scaleAlpha(BASE_SHADOW, 0.28f * alpha)));
        props.put("stroke", hex(HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(syncTheme.get() ? theme().windowStroke() : BASE_STROKE, accent, 0.18f), 0.18f * alpha)));
        props.put("highlight", hex(HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.08f * alpha)));
        props.put("contextFill", hex(HudRenderUtil.scaleAlpha(0xFF000000, 0.36f * alpha)));
        props.put("contextStroke", hex(HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.10f * alpha)));
        props.put("timeFill", hex(HudRenderUtil.scaleAlpha(0xFF000000, 0.48f * alpha)));
        props.put("timeStroke", hex(HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.10f * alpha)));
        props.put("pvpFill", hex(HudRenderUtil.scaleAlpha(STATUS_PVP, 0.86f * alpha)));
        props.put("pvpStroke", hex(HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(STATUS_PVP, 0xFFFFFFFF, 0.32f), 0.34f * alpha)));
        props.put("accent", hex(HudRenderUtil.scaleAlpha(accent, alpha)));
        props.put("accentSoft", hex(HudRenderUtil.scaleAlpha(uiAccentSoft, alpha)));
        props.put("textPrimary", hex(HudRenderUtil.scaleAlpha(uiTextPrimary, alpha)));
        props.put("textSecondary", hex(HudRenderUtil.scaleAlpha(uiTextSecondary, alpha)));
        props.put("textMuted", hex(HudRenderUtil.scaleAlpha(uiTextMuted, alpha)));
        props.put("progressBg", hex(HudRenderUtil.scaleAlpha(PROGRESS_BG, expandedAlpha)));
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
        props.put("shuffleColor", hex(shuffleColor(currentSnapshot)));
        props.put("repeatColor", hex(repeatColor(currentSnapshot)));
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
                                         TextRenderer valueRenderer,
                                         TextRenderer metaRenderer) {
        float pvpTimerW = reservedTimeWidth(valueRenderer, formatSecondsOnly(predictedPvpSeconds()), 0.84f);
        float pvpWidth = 24f + pvpTimerW;

        String title = currentSnapshot != null ? currentSnapshot.title() : "";
        float titleW = measureWidth(titleRenderer, title, 0.82f);
        String compactElapsed = formatShortTime(currentSnapshot != null ? currentSnapshot.predictedPositionSeconds() : 0L);
        float elapsedW = reservedTimeWidth(valueRenderer, compactElapsed, 0.72f);
        float musicWidth = 10f + elapsedW + 18f + COMPACT_ART + COMPACT_GAP + titleW + COMPACT_GAP + COMPACT_WAVE_W + 10f;
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
                              float radius,
                              float alpha,
                              int accent) {
        renderer.roundedRectShadow(drawX, drawY, drawWidth, drawHeight, radius, 8f, 14f,
                HudRenderUtil.scaleAlpha(BASE_SHADOW, 0.28f * alpha));
        renderer.roundedRect(drawX, drawY, drawWidth, drawHeight, radius, 1.1f,
                HudRenderUtil.scaleAlpha(uiFillTop, 0.88f * alpha));
        renderer.roundedRectStroke(drawX, drawY, drawWidth, drawHeight, radius, 1.1f, 1f,
                HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(BASE_STROKE, accent, 0.18f), 0.18f * alpha));
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
            int windowBg = HudRenderUtil.setAlpha(theme().windowBg(), themeAlpha.get());
            uiFillTop = HudRenderUtil.mixRgb(windowBg, 0xFFFFFFFF, 0.04f);
            uiFillBottom = HudRenderUtil.mixRgb(windowBg, 0xFF000000, 0.08f);
            uiTextPrimary = theme().textPrimary();
            uiTextSecondary = theme().textMuted();
            uiTextMuted = HudRenderUtil.mixColor(theme().textMuted(), windowBg, 0.18f);
            uiAccent = theme().accent();
            uiAccentSoft = theme().accentSoft();
            return;
        }

        int baseBg = bgColor.getArgb();
        uiFillTop = HudRenderUtil.mixRgb(baseBg, 0xFFFFFFFF, 0.04f);
        uiFillBottom = HudRenderUtil.mixRgb(baseBg, 0xFF000000, 0.08f);
        uiTextPrimary = textColor.getArgb() | 0xFF000000;
        uiTextSecondary = mutedColor.getArgb() | 0xFF000000;
        uiTextMuted = HudRenderUtil.mixColor(uiTextSecondary, baseBg, 0.16f);
        uiAccent = accentColor.getArgb() | 0xFF000000;
        uiAccentSoft = HudRenderUtil.mixColor(uiAccent, baseBg, 0.40f);
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
