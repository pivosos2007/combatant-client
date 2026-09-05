/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.nondraggable.impl;

import com.mojang.authlib.GameProfile;
import combatant.client.config.values.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import combatant.client.config.SettingDef;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.impl.HudNotifier;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.ColorMath;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.MatteHudStyle;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.input.KeyManager;
import combatant.client.util.map.maplink.runtime.MapLinkLocatorBridge;
import combatant.client.util.player.PlayerSkinResolver;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 40)
public final class CustomBar extends AbstractHudElement {

    public static final CustomBar INSTANCE = new CustomBar();
    private static final float XP_BAR_WIDTH = 182f;
    private static final int XP_BG_ALPHA = 0x88;
    private static final int XP_FILL_ALPHA = 0xCC;
    private static final double XP_TEXT_SCALE = 0.59;
    private static final int XP_BAR_OFFSET = 2;
    private static final int XP_TEXT_OFFSET = 0;
    private static final float XP_BAR_HEIGHT = 5.0f;
    private static final float XP_BAR_RADIUS = 2.5f;
    private static final float BAR_SOFTNESS = 1.0f;
    private static final float BAR_BLUR_QUALITY = 12.0f;
    private static final float BAR_BLUR_ALPHA = 0.72f;
    private static final float BAR_GLASS_ALPHA = 0.58f;
    private static final float BAR_TRACK_ALPHA = 0.54f;
    private static final float BAR_FILL_ALPHA = 0.82f;
    private static final float BAR_STROKE_ALPHA = 0.48f;
    private static final int XP_RAINBOW_SEGMENTS = 24;
    private static final Identifier XP_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "hud/experience_bar_background");
    private static final Identifier XP_PROGRESS = Identifier.fromNamespaceAndPath("minecraft", "hud/experience_bar_progress");
    private static final Identifier LOCATOR_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "hud/locator_bar_background");
    private static final Identifier JUMP_BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "hud/jump_bar_background");
    private static final Identifier JUMP_COOLDOWN = Identifier.fromNamespaceAndPath("minecraft", "hud/jump_bar_cooldown");
    private static final Identifier JUMP_PROGRESS = Identifier.fromNamespaceAndPath("minecraft", "hud/jump_bar_progress");
    private static final float LOCATOR_BAR_HEIGHT = XP_BAR_HEIGHT;
    private static final int LOCATOR_TICK_COUNT = 6;
    private static final float LOCATOR_HEAD_SIZE = 5.8f;
    private static final float LOCATOR_HEAD_RADIUS = 1.45f;
    private static final float LOCATOR_HEAD_GAP = 0f;
    private static final float LOCATOR_HEAD_RANGE = XP_BAR_WIDTH - LOCATOR_HEAD_SIZE;
    private static final float LOCATOR_ANGLE_RANGE = 60f;
    private static final float LOCATOR_HEAD_SCALE_START = 24f;
    private static final float LOCATOR_HEAD_SCALE_END = 96f;
    private static final float LOCATOR_HEAD_MIN_SCALE = 0.7f;
    private static final float LOCATOR_OUTLINE_DISTANCE = 64f;
    private static final float LOCATOR_OUTLINE_MIN = 0.6f;
    private static final float LOCATOR_OUTLINE_MAX = 1.6f;
    private static final Identifier LOCATOR_ARROW_UP = Identifier.fromNamespaceAndPath("minecraft", "hud/locator_bar_arrow_up");
    private static final Identifier LOCATOR_ARROW_DOWN = Identifier.fromNamespaceAndPath("minecraft", "hud/locator_bar_arrow_down");
    private static final int LOCATOR_ARROW_W = 6;
    private static final int LOCATOR_ARROW_H = 4;
    private static final int LOCATOR_ARROW_GAP = 1;
    private static final float LOCATOR_LABEL_SCALE = 0.27f;
    private static final float LOCATOR_LABEL_OFFSET = 2.4f;
    private static final float LOCATOR_LABEL_ANIM_SPEED = 9.0f;
    private static final float LOCATOR_LABEL_POSITION_SPEED = 10.0f;
    private static final float LOCATOR_LABEL_ROW_GAP = 0.8f;
    private static final float LOCATOR_LABEL_ROW_MIN_WIDTH = 24.0f;
    private static final float LOCATOR_LABEL_ROW_MAX_WIDTH = 68.0f;
    private static final float LOCATOR_LABEL_ROW_HEIGHT = 8.2f;
    private static final float LOCATOR_LABEL_CHIP_PAD_X = 3.0f;
    private static final float LOCATOR_LABEL_DISTANCE_GAP = 2.0f;
    private static final float LOCATOR_LABEL_DISTANCE_PAD_X = 2.2f;
    private static final float LOCATOR_LABEL_DISTANCE_HEIGHT = 6.2f;
    private static final float LOCATOR_LABEL_RADIUS = 2.2f;
    private static final float LOCATOR_LABEL_SCROLL_SPEED = 22.0f;
    private static final float LOCATOR_LABEL_SCROLL_GAP = 14.0f;
    private static final float LOCATOR_LABEL_SCROLL_PAUSE_SEC = 0.8f;
    private static final int LOCATOR_LABEL_MAX_ROWS = 3;
    private static final String COLOR_THEME = "Theme";
    private static final String COLOR_CUSTOM = "Custom";
    private static final Map<String, Float> LOCATOR_LABEL_ANIM = new HashMap<>();
    private static final Map<String, LocatorLabel> LOCATOR_LABEL_CACHE = new HashMap<>();
    private static final Map<String, Float> LOCATOR_LABEL_POSITIONS = new HashMap<>();
    private final NumberValue<Integer> overlayAlpha =
            new NumberValue<>("overlay_alpha", 255, 30, 255);
    private final BooleanValue xpBar =
            new BooleanValue("xp_bar", true);
    private final BooleanValue xpBarShowLevel =
            new BooleanValue("xp_bar_show_level", true);
    private final EnumValue<BarMode> barMode =
            new EnumValue<>("bar_mode", BarMode.LEVEL, BarMode.values());
    private final ModeValue colorMode =
            new ModeValue("bar_color_mode", "Theme", COLOR_THEME, COLOR_CUSTOM);
    private final RGBColorValue xpBarBgColor =
            new RGBColorValue("xp_bar_bg_color", "#151515");
    private final RGBColorValue xpBarFillStart =
            new RGBColorValue("xp_bar_fill_start", "#4CB2FF");
    private final RGBColorValue xpBarFillEnd =
            new RGBColorValue("xp_bar_fill_end", "#76F0FF");
    private final RGBColorValue xpBarTextColor =
            new RGBColorValue("xp_bar_text_color", "#A0E5FF");
    private final EnumValue<XpBarStyle> xpBarStyle =
            new EnumValue<>("xp_bar_style", XpBarStyle.GRADIENT, XpBarStyle.values());
    private final RGBAColorValue customBgColor =
            new RGBAColorValue("custom_bg_color", "#88151515");
    private final RGBAColorValue customFillColor =
            new RGBAColorValue("custom_fill_color", "#CC4CB2FF");
    private final BooleanValue customFillGradient =
            new BooleanValue("custom_fill_gradient", true);
    private final RGBAColorValue customFillGradientColor =
            new RGBAColorValue("custom_fill_gradient_color", "#CC76F0FF");
    private final NumberValue<Float> customFillGradientAngle =
            new NumberValue<>("custom_fill_gradient_angle", 0.0f, 0.0f, 360.0f);
    private final RGBAColorValue customTextColor =
            new RGBAColorValue("custom_text_color", "#FFA0E5FF");
    private final NumberValue<Float> customTextScale =
            new NumberValue<>("custom_text_scale", (float) XP_TEXT_SCALE, 0.3f, 1.2f);
    private final BooleanValue locatorLabels =
            new BooleanValue("locator_labels", true);
    private final EnumValue<LocatorLabelMode> locatorLabelMode =
            new EnumValue<>("locator_label_mode", LocatorLabelMode.CENTER, LocatorLabelMode.values());
    private final NumberValue<Float> locatorLabelAngle =
            new NumberValue<>("locator_label_angle", 6.0f, 4.0f, 45.0f);
    private final BooleanValue locatorLabelDistance =
            new BooleanValue("locator_label_distance", true);
    private final BooleanValue locatorSearchEnabled =
            new BooleanValue("locator_search_enabled", true);
    private final SetValue locatorSearchPlayers =
            new SetValue("locator_search_list", new java.util.LinkedHashSet<>(java.util.List.of("imhorny")));
    private final RGBColorValue locatorSearchColor =
            new RGBColorValue("locator_search_color", "#FFAA00");
    private final RGBColorValue locatorDimmedColor =
            new RGBColorValue("locator_dimmed_color", "#777777");
    private final BooleanValue locatorDimmedScaleOnly =
            new BooleanValue("locator_dimmed_scale_only", true);
    private final BooleanValue locatorMapFilterEnabled =
            new BooleanValue("locator_map_filter_enabled", false);
    private final StringValue locatorMapServer =
            new StringValue("locator_map_server", "");
    private final StringValue locatorMapUrl =
            new StringValue("locator_map_url", "example");
    private final KeyBindValue locatorTargetToggle =
            new KeyBindValue("locator_target_toggle", "NONE");
    private final KeyBindValue locatorBarToggle =
            new KeyBindValue("locator_bar_toggle", "U");

    private CustomBar() {
        super("vanilla_bar", "Bar", true);
    }

    public static CustomBar get() {
        return INSTANCE;
    }

    public static boolean shouldUseLocator(Minecraft mc, CustomBar bm) {
        if (mc == null || bm == null) return false;
        return switch (bm.getHotbarBarMode()) {
            case LOCATOR -> true;
            case LEVEL -> false;
            case AUTO -> mc.getConnection() != null
                    && mc.getConnection().getWaypointManager().hasWaypoints();
        };
    }

    public static boolean shouldUseJumpBar(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        var mount = mc.player.jumpableVehicle();
        return mount != null && (mc.player.getJumpRidingScale() > 0.0f || mount.getJumpCooldown() > 0);
    }

    public static void renderXpBarBackground(Renderer2D r2d, Minecraft mc) {
        if (mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;

        float progress = Mth.clamp(mc.player.experienceProgress, 0.0f, 1.0f);
        float scale = (float) mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        float baseY = screenH - 22f;

        float x = (screenW - XP_BAR_WIDTH) * 0.5f;
        float y = baseY - XP_BAR_OFFSET - XP_BAR_HEIGHT;
        drawVanillaGlassTrack(r2d, x, y, scale, progress, bm, false);
    }

    private static void drawXpBar(Renderer2D r2d, float barX, float barY, float scale, float progress, CustomBar bm) {
        if (r2d == null || bm == null) return;
        drawVanillaGlassTrack(r2d, barX, barY, scale, progress, bm, false);
    }

    public static void renderXpBarTexture(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (ctx == null || mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        int k = mc.player.getXpNeededForNextLevel();
        if (k <= 0) return;

        int x = Mth.floor((mc.getWindow().getGuiScaledWidth() - XP_BAR_WIDTH) * 0.5f);
        int y = Mth.floor(mc.getWindow().getGuiScaledHeight() - 22f - XP_BAR_OFFSET - XP_BAR_HEIGHT);
        BarColors colors = colors(bm);
        ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, XP_BACKGROUND, x, y, 182, 5,
                applyBarAlpha(colors.trackTexture(), bm));
        int progress = (int) (Mth.clamp(mc.player.experienceProgress, 0.0f, 1.0f) * 183.0f);
        if (progress > 0) {
            ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, XP_PROGRESS, 182, 5, 0, 0, x, y, progress, 5,
                    applyBarAlpha(colors.fillTexture(), bm));
        }
    }

    public static void renderJumpBarBackground(Renderer2D r2d, Minecraft mc) {
        if (mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        float progress = mc.player.jumpableVehicle() != null && mc.player.jumpableVehicle().getJumpCooldown() <= 0
                ? Mth.clamp(mc.player.getJumpRidingScale(), 0.0f, 1.0f)
                : 1.0f;
        float scale = (float) mc.getWindow().getGuiScale();
        float x = (mc.getWindow().getGuiScaledWidth() - XP_BAR_WIDTH) * 0.5f;
        float y = mc.getWindow().getGuiScaledHeight() - 22f - XP_BAR_OFFSET - XP_BAR_HEIGHT;
        drawVanillaGlassTrack(r2d, x, y, scale, progress, bm, false);
    }

    public static void renderJumpBarTexture(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (ctx == null || mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        var mount = mc.player.jumpableVehicle();
        if (mount == null) return;

        int x = Mth.floor((mc.getWindow().getGuiScaledWidth() - XP_BAR_WIDTH) * 0.5f);
        int y = Mth.floor(mc.getWindow().getGuiScaledHeight() - 22f - XP_BAR_OFFSET - XP_BAR_HEIGHT);
        BarColors colors = colors(bm);
        ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, JUMP_BACKGROUND, x, y, 182, 5,
                applyBarAlpha(colors.trackTexture(), bm));
        if (mount.getJumpCooldown() > 0) {
            ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, JUMP_COOLDOWN, x, y, 182, 5,
                    applyBarAlpha(colors.fillTexture(), bm));
            return;
        }
        int progress = Mth.lerpDiscrete(mc.player.getJumpRidingScale(), 0, 182);
        if (progress > 0) {
            ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, JUMP_PROGRESS, 182, 5, 0, 0, x, y, progress, 5,
                    applyBarAlpha(colors.fillTexture(), bm));
        }
    }

    private static void drawVanillaGlassTrack(Renderer2D r2d,
                                              float barX,
                                              float barY,
                                              float scale,
                                              float progress,
                                              CustomBar bm,
                                              boolean locator) {
        if (r2d == null || bm == null) return;

        float x = barX * scale;
        float y = barY * scale;
        float w = XP_BAR_WIDTH * scale;
        float h = XP_BAR_HEIGHT * scale;
        float radius = XP_BAR_RADIUS * scale;
        BarColors colors = colors(bm);
        float overlay = bm.getHudOverlayAlphaFactor();

        if (locator) {
            // Locator deliberately uses the same restrained matte language as TabList.
            // It must stay a compact navigation rail, not become another glass panel.
            int locatorTop = applyBarAlpha(HudRenderUtil.mixColor(theme().windowBg(), theme().surface(), 0.34f), bm);
            int locatorBottom = applyBarAlpha(HudRenderUtil.mixColor(theme().windowBg(), theme().surface(), 0.52f), bm);
            int locatorStroke = applyBarAlpha(HudRenderUtil.mixColor(theme().windowStroke(), theme().accent(), 0.12f), bm);
            r2d.roundedRectShadow(x, y, w, h, radius, Math.max(scale, 1.0f * scale), 3.5f * scale,
                    HudRenderUtil.scaleAlpha(0xFF000000, 0.16f * overlay));
            r2d.roundedRectGradient(x, y, w, h, radius, BAR_SOFTNESS * scale,
                    locatorTop, locatorBottom, 90.0f);
            r2d.roundedRectStroke(x, y, w, h, radius, BAR_SOFTNESS * scale,
                    Math.max(0.55f, 0.62f * scale), locatorStroke);
        } else {
            // The narrow inner pill needs an explicit scene blur just like CustomHotbar.
            // Liquid-glass refraction alone is not enough on a 5px-high element.
            float blurAlpha = Mth.clamp(BAR_BLUR_ALPHA * overlay, 0.0f, 1.0f);
            r2d.blurRect(x, y, w, h, radius, BAR_BLUR_QUALITY, 1.0f, blurAlpha, 0xFFFFFF);
            HudRenderUtil.drawLiquidGlass(x, y, w, h, radius, scale, false, blurAlpha, BAR_GLASS_ALPHA * overlay);

            int trackA = applyBarAlpha(colors.track(), bm);
            r2d.roundedRectGradient(x, y, w, h, radius, BAR_SOFTNESS * scale, trackA,
                    HudRenderUtil.scaleAlpha(trackA, 0.76f), 0.0f);
        }

        float fillW = XP_BAR_WIDTH * Mth.clamp(progress, 0.0f, 1.0f) * scale;
        if (fillW > 0.0f && !locator) {
            int fillStart = applyBarAlpha(colors.fillStart(), bm);
            int fillEnd = applyBarAlpha(colors.fillEnd(), bm);
            r2d.roundedRectMaskedQuad(
                    x, y, fillW, h,
                    x, y, w, h,
                    radius, BAR_SOFTNESS * scale,
                    fillStart, fillEnd, fillEnd, fillStart
            );

            // Highlight is clipped by the OUTER pill. Previously it was another rounded
            // capsule with its own left cap, which produced the stray blob on the XP fill.
            float highlightH = Math.max(scale, h * 0.42f);
            r2d.roundedRectMaskedQuad(
                    x, y, fillW, highlightH,
                    x, y, w, h,
                    radius, BAR_SOFTNESS * scale,
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.10f * overlay),
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.035f * overlay),
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.015f * overlay),
                    HudRenderUtil.scaleAlpha(0xFFFFFFFF, 0.045f * overlay)
            );
        }

        if (LOCATOR_TICK_COUNT > 0 && locator) {
            float lineW = Math.max(1.0f, 0.55f * scale);
            float step = w / (LOCATOR_TICK_COUNT + 1f);
            int tickColor = applyBarAlpha(HudRenderUtil.scaleAlpha(colors.locatorTexture(), 0.25f), bm);
            for (int i = 1; i <= LOCATOR_TICK_COUNT; i++) {
                float lx = x + step * i - lineW * 0.5f;
                r2d.quad(lx, y + 1.15f * scale, lineW, Math.max(scale, h - 2.3f * scale), tickColor);
            }
        }

        if (!locator) {
            int stroke = applyBarAlpha(colors.stroke(), bm);
            r2d.roundedRectStroke(x, y, w, h, radius, BAR_SOFTNESS * scale,
                    Math.max(0.65f, 0.72f * scale), stroke);
        }
    }

    public static void renderLocatorBackground(Renderer2D r2d, Minecraft mc) {
        if (mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        float scale = (float) mc.getWindow().getGuiScale();
        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        float baseY = screenH - 22f;
        float x = (screenW - XP_BAR_WIDTH) * 0.5f;
        float y = baseY - XP_BAR_OFFSET - LOCATOR_BAR_HEIGHT;
        drawVanillaGlassTrack(r2d, x, y, scale, 1.0f, bm, true);
    }

    public static void renderLocatorTexture(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (ctx == null || mc == null || mc.player == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        int x = Mth.floor((mc.getWindow().getGuiScaledWidth() - XP_BAR_WIDTH) * 0.5f);
        int y = Mth.floor(mc.getWindow().getGuiScaledHeight() - 22f - XP_BAR_OFFSET - LOCATOR_BAR_HEIGHT);
        ctx.blitSprite(CombatantRenderPipelines.GUI_TEXTURE_LOOKUP, LOCATOR_BACKGROUND, x, y, 182, 5,
                applyBarAlpha(colors(bm).locatorTexture(), bm));
    }

    private static BarColors colors(CustomBar bm) {
        if (bm != null && bm.isCustomMode()) {
            int track = HudRenderUtil.scaleAlpha(bm.customBgColor.getArgb(), BAR_TRACK_ALPHA);
            int fillStart = HudRenderUtil.scaleAlpha(bm.customFillColor.getArgb(), BAR_FILL_ALPHA);
            int fillEnd = HudRenderUtil.scaleAlpha(bm.customFillGradientColor.getArgb(), BAR_FILL_ALPHA);
            int stroke = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(fillEnd, track, 0.38f), BAR_STROKE_ALPHA);
            int textureTrack = forceOpaque(HudRenderUtil.mixColor(track, 0xFFFFFFFF, 0.18f));
            int textureFill = forceOpaque(HudRenderUtil.mixColor(fillStart, fillEnd, 0.48f));
            int locator = forceOpaque(HudRenderUtil.mixColor(fillEnd, track, 0.16f));
            return new BarColors(track, fillStart, fillEnd, stroke, textureTrack, textureFill, locator);
        }

        int window = theme().windowBg();
        int surface = theme().surface();
        int accent = theme().accent();
        int soft = theme().accentSoft();
        int track = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(window, surface, 0.44f), BAR_TRACK_ALPHA);
        int fillStart = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(soft, accent, 0.24f), BAR_FILL_ALPHA);
        int fillEnd = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(accent, theme().textPrimary(), 0.18f), BAR_FILL_ALPHA);
        int stroke = HudRenderUtil.scaleAlpha(HudRenderUtil.mixColor(theme().windowStroke(), accent, 0.18f), BAR_STROKE_ALPHA);
        int textureTrack = forceOpaque(HudRenderUtil.mixColor(track, theme().textMuted(), 0.22f));
        int textureFill = forceOpaque(HudRenderUtil.mixColor(fillStart, fillEnd, 0.50f));
        int locator = forceOpaque(HudRenderUtil.mixColor(accent, 0xFF1E7A4C, 0.42f));
        return new BarColors(track, fillStart, fillEnd, stroke, textureTrack, textureFill, locator);
    }

    private static int forceOpaque(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    public static void renderLocatorArrows(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, Minecraft mc) {
        renderLocatorMarkers(ctx, tickCounter, mc, true, false);
    }

    public static void renderLocatorHeads(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, Minecraft mc) {
        renderLocatorMarkers(ctx, tickCounter, mc, false, true);
    }

    public static void renderLocatorMarkers(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, Minecraft mc) {
        renderLocatorMarkers(ctx, tickCounter, mc, true, true);
    }

    private static void renderLocatorMarkers(GuiGraphicsExtractor ctx,
                                             DeltaTracker tickCounter,
                                             Minecraft mc,
                                             boolean drawArrows,
                                             boolean drawHeads) {
        if (ctx == null || mc == null || mc.player == null || mc.level == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled()) return;
        if (mc.getConnection() == null) return;
        ClientWaypointManager handler = mc.getConnection().getWaypointManager();
        if (handler == null || !handler.hasWaypoints()) return;

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();
        float baseY = screenH - 22f;
        float barX = (screenW - XP_BAR_WIDTH) * 0.5f;
        float barY = baseY - XP_BAR_OFFSET - LOCATOR_BAR_HEIGHT;

        int startX = Mth.floor(barX + LOCATOR_HEAD_RANGE * 0.5f);
        PartialTickSupplier tickProgress = entity -> tickCounter.getGameTimeDeltaPartialTick(false);
        var camera = mc.gameRenderer.mainCamera();

        TrackedWaypoint.Camera yawProvider = new TrackedWaypoint.Camera() {
            @Override
            public float yaw() {
                return camera.yRot();
            }

            @Override
            public Vec3 position() {
                return camera.position();
            }
        };

        Set<UUID> mapPlayers = bm.isLocatorMapFilterEnabled()
                ? MapLinkLocatorBridge.exactPlayerIds(mc, bm.getLocatorMapServer(), bm.getLocatorMapUrl())
                : Collections.emptySet();
        boolean labelsEnabled = drawHeads && bm.isLocatorLabelsEnabled();
        LocatorLabelMode labelMode = labelsEnabled
                ? bm.getLocatorLabelMode()
                : LocatorLabelMode.ALWAYS;
        float labelAngle = labelsEnabled ? bm.getLocatorLabelAngle() : 0f;
        float dt = drawHeads ? AnimationUtility.deltaTime() : 0.0f;

        List<LocatorCandidate> candidates = new ArrayList<>();
        List<LocatorHead> heads = new ArrayList<>();
        Set<String> seenLabels = labelsEnabled ? new HashSet<>() : null;
        List<LocatorLabel> labels = labelsEnabled ? new ArrayList<>() : null;

        handler.forEachWaypoint(mc.player, waypoint -> {
            if (isSourcePlayer(mc.player, waypoint)) return;
            UUID wpUuid = waypoint.id().left().orElse(null);
            if (wpUuid != null && !mapPlayers.isEmpty() && mapPlayers.contains(wpUuid)) return;

            double yaw = waypoint.yawAngleToCamera(mc.level, yawProvider, tickProgress);
            if (yaw <= -LOCATOR_ANGLE_RANGE || yaw >= LOCATOR_ANGLE_RANGE) return;

            float dist = Mth.sqrt((float) waypoint.distanceSquared(mc.player));
            String name = getWaypointName(mc, waypoint, wpUuid);
            String key = locatorLabelKey(waypoint, wpUuid);

            candidates.add(new LocatorCandidate(
                    waypoint,
                    wpUuid,
                    (float) yaw,
                    dist,
                    name,
                    key
            ));
        });

        if (candidates.isEmpty() && !drawHeads) return;

        candidates.sort((a, b) -> {
            int cmp = Float.compare(a.dist, b.dist);
            if (cmp != 0) return cmp;
            cmp = a.nameSort.compareTo(b.nameSort);
            if (cmp != 0) return cmp;
            return a.keySort.compareTo(b.keySort);
        });

        LocatorCandidate focusedLabelCandidate = null;
        if (labelsEnabled && (labelMode == LocatorLabelMode.CENTER || labelMode == LocatorLabelMode.CENTER_TARGETS)) {
            focusedLabelCandidate = candidates.stream()
                    .filter(candidate -> Math.abs(candidate.yaw) <= labelAngle)
                    .min(Comparator
                            .comparingDouble((LocatorCandidate candidate) -> Math.abs(candidate.yaw))
                            .thenComparingDouble(candidate -> candidate.dist)
                            .thenComparing(candidate -> candidate.nameSort)
                            .thenComparing(candidate -> candidate.keySort))
                    .orElse(null);
        }

        for (LocatorCandidate candidate : candidates) {
            float yaw = candidate.yaw;
            int offset = Mth.floor(yaw * (LOCATOR_HEAD_RANGE / 2f) / LOCATOR_ANGLE_RANGE);
            float drawX = startX + offset;

            float dist = candidate.dist;
            UUID wpUuid = candidate.uuid;

            boolean isTarget = isLocatorTarget(wpUuid, bm);
            boolean isDimmed = isLocatorDimmed(wpUuid, bm);
            float headScale = locatorHeadScale(dist, isTarget, isDimmed, bm);
            float headSize = LOCATOR_HEAD_SIZE * headScale;
            float headRadius = LOCATOR_HEAD_RADIUS * headScale;
            float headY = barY + (LOCATOR_BAR_HEIGHT - headSize) * 0.5f + LOCATOR_HEAD_GAP;

            int baseColor = resolveWaypointColor(candidate.waypoint);
            RenderColor headColor = new RenderColor(applyBarAlpha(0xFFFFFFFF, bm));
            RenderColor outlineColor = isTarget
                    ? new RenderColor(applyBarAlpha(0xFF000000 | bm.getLocatorSearchColorRgb(), bm))
                    : locatorOutlineColor(baseColor, dist, bm);
            float outlineThickness = locatorOutlineThickness(dist) * headScale;
            if (isTarget) {
                outlineThickness = Math.max(outlineThickness, 1.85f * headScale);
            }

            AbstractClientPlayer player = resolveWaypointPlayer(mc, wpUuid);
            Identifier skin = (player == null)
                    ? PlayerHeadRenderer.resolveCachedSkin(
                            wpUuid, candidate.name, resolveWaypointSkin(mc, wpUuid, candidate.waypoint))
                    : null;

            if (drawHeads) {
                heads.add(new LocatorHead(
                        drawX, headY, headSize, headRadius,
                        player, skin, headColor, outlineColor, outlineThickness, isTarget
                ));
            }

            TrackedWaypoint.PitchDirection pitch = candidate.waypoint.pitchDirectionToCamera(mc.level, mc.gameRenderer, tickProgress);
            if (drawArrows && (pitch == TrackedWaypoint.PitchDirection.UP || pitch == TrackedWaypoint.PitchDirection.DOWN)) {
                Identifier arrow = (pitch == TrackedWaypoint.PitchDirection.DOWN) ? LOCATOR_ARROW_DOWN : LOCATOR_ARROW_UP;
                int xOffset = Mth.floor(drawX + (headSize - LOCATOR_ARROW_W) * 0.5f);
                int yOffset = (pitch == TrackedWaypoint.PitchDirection.DOWN)
                        ? Mth.floor(headY + headSize + LOCATOR_ARROW_GAP)
                        : Mth.floor(headY - LOCATOR_ARROW_H - LOCATOR_ARROW_GAP);
                ctx.blitSprite(
                        CombatantRenderPipelines.GUI_TEXTURE_LOOKUP,
                        arrow,
                        xOffset,
                        yOffset,
                        LOCATOR_ARROW_W,
                        LOCATOR_ARROW_H,
                        applyBarAlpha(colors(bm).locatorTexture(), bm)
                );
            }

            if (!labelsEnabled || labels == null || seenLabels == null) continue;

            String labelKey = candidate.key != null ? candidate.key : candidate.nameSort;
            boolean focused = candidate == focusedLabelCandidate;
            boolean showLabel = shouldShowLocatorLabel(labelMode, isTarget, focused);
            if (showLabel && labelKey != null && !labelKey.isBlank()) {
                seenLabels.add(labelKey);
            }
            float anim = labelKey != null && !labelKey.isBlank()
                    ? updateLocatorLabelAnim(labelKey, showLabel ? 1f : 0f, dt)
                    : (showLabel ? 1f : 0f);
            if (anim <= 0.01f) continue;

            String name = candidate.name;
            if (name == null || name.isBlank()) continue;

            String distanceText = "";
            if (bm.isLocatorLabelDistanceEnabled()) {
                distanceText = formatDistance(dist);
            }

            LocatorLabel label = new LocatorLabel(labelKey, name, distanceText, anim, isTarget, focused, showLabel, candidate.nameSort, yaw);
            labels.add(label);
            if (labelKey != null && !labelKey.isBlank()) {
                LOCATOR_LABEL_CACHE.put(labelKey, label);
            }
        }

        if (!drawHeads) return;

        if (labelsEnabled && labels != null && seenLabels != null) {
            decayLocatorLabels(seenLabels, dt);
            labels.clear();
            appendCachedLocatorLabels(labels);
        }

        if (heads.isEmpty() && (labels == null || labels.isEmpty())) return;

        float scale = (float) mc.getWindow().getGuiScale();
        ViewportContext.beginUnscaled(ctx);
        try {
            if (!heads.isEmpty()) {
                boolean startedHeadsBatch = beginOwnedColorBatch();
                try {
                    for (int i = heads.size() - 1; i >= 0; i--) {
                        LocatorHead head = heads.get(i);
                        float headX = head.x * scale;
                        float headYScaled = head.y * scale;
                        float headSize = head.size * scale;
                        float headRadius = head.radius * scale;
                        float headOutline = head.outlineThickness * scale;
                        if (head.target) {
                            int glow = applyBarAlpha(HudRenderUtil.scaleAlpha(0xFF000000 | bm.getLocatorSearchColorRgb(), 0.28f), bm);
                            Renderer2D.COLOR.roundedRect(
                                    headX - 1.9f * scale,
                                    headYScaled - 1.9f * scale,
                                    headSize + 3.8f * scale,
                                    headSize + 3.8f * scale,
                                    headRadius + 2.4f * scale,
                                    scale,
                                    glow
                            );
                        }
                        if (head.player != null) {
                            PlayerHeadRenderer.drawRounded(
                                    ctx,
                                    headX, headYScaled, headSize,
                                    headRadius,
                                    head.player,
                                    head.color,
                                    true,
                                    head.outlineColor,
                                    headOutline,
                                    false
                            );
                        } else if (head.skin != null) {
                            PlayerHeadRenderer.drawRounded(
                                    ctx,
                                    headX, headYScaled, headSize,
                                    headRadius,
                                    head.skin,
                                    head.color,
                                    true,
                                    head.outlineColor,
                                    headOutline,
                                    false
                            );
                        }
                    }
                } finally {
                    renderOwnedColorBatch(startedHeadsBatch);
                }
            }

            if (labelsEnabled && labels != null && !labels.isEmpty()) {
                TextRenderer tr = Fonts.renderer("Iosevka");
                Renderer2D r2d = Renderer2D.COLOR;
                float labelScale = LOCATOR_LABEL_SCALE * scale;
                float rowH = LOCATOR_LABEL_ROW_HEIGHT * scale;
                float rowGap = LOCATOR_LABEL_ROW_GAP * scale;
                float rowStep = rowH + rowGap;
                float rowBottom = barY * scale - LOCATOR_LABEL_OFFSET * scale;
                float screenCenterX = screenW * scale * 0.5f;

                List<LocatorLabel> slotted = new ArrayList<>(labels);
                slotted.removeIf(label -> label == null || label.alpha() <= 0.01f);
                slotted.sort(Comparator
                        .comparing((LocatorLabel label) -> !label.active())
                        .thenComparing(label -> !label.focused())
                        .thenComparing(label -> !label.target())
                        .thenComparingDouble(label -> Math.abs(label.yaw()))
                        .thenComparing(label -> label.sort() == null ? "" : label.sort())
                        .thenComparing(LocatorLabel::name));
                if (slotted.size() > LOCATOR_LABEL_MAX_ROWS) {
                    slotted = new ArrayList<>(slotted.subList(0, LOCATOR_LABEL_MAX_ROWS));
                }

                Map<String, Integer> targetRows = new HashMap<>(slotted.size());
                boolean centerLane = labelMode == LocatorLabelMode.CENTER || labelMode == LocatorLabelMode.CENTER_TARGETS;
                int nextRow = centerLane ? 1 : 0;
                if (centerLane) {
                    for (LocatorLabel label : slotted) {
                        if (label.focused()) {
                            targetRows.put(label.key(), 0);
                        }
                    }
                }
                for (LocatorLabel label : slotted) {
                    if (centerLane && label.focused()) continue;
                    targetRows.put(label.key(), nextRow++);
                }

                if (!slotted.isEmpty()) {
                    Map<String, Float> rowWidths = new HashMap<>(slotted.size());
                    Map<String, Float> distanceWidths = new HashMap<>(slotted.size());
                    Map<String, Float> nameWidths = new HashMap<>(slotted.size());
                    tr.begin(labelScale, false, false);
                    float textH = (float) tr.getHeight(true);
                    for (LocatorLabel label : slotted) {
                        float nameTextW = label.name() == null || label.name().isEmpty() ? 0.0f : (float) tr.getWidth(label.name(), true);
                        float distanceTextW = label.distance().isEmpty() ? 0.0f : (float) tr.getWidth(label.distance(), true);
                        nameWidths.put(label.key(), nameTextW);
                        distanceWidths.put(label.key(), distanceTextW);
                        rowWidths.put(label.key(), locatorLabelRowWidth(tr, label, distanceTextW, scale));
                    }
                    tr.end();

                    boolean startedLabelBatch = beginOwnedColorBatch();
                    try {
                        for (int listIndex = 0; listIndex < slotted.size(); listIndex++) {
                            LocatorLabel label = slotted.get(listIndex);
                            int rowIndex = targetRows.getOrDefault(label.key(), listIndex);
                            float anim = Mth.clamp(label.alpha(), 0f, 1f);
                            float ease = locatorLabelEase(anim);
                            float rowW = rowWidths.getOrDefault(label.key(), LOCATOR_LABEL_ROW_MIN_WIDTH * scale);
                            float rowX = screenCenterX - rowW * 0.5f;
                            float rowPosition = updateLocatorLabelPosition(label.key(), rowIndex, dt);
                            float rowY = rowBottom - rowH - rowPosition * rowStep;
                            float drawY = rowY + (1.0f - ease) * 0.75f * scale;
                            float distanceTextW = distanceWidths.getOrDefault(label.key(), 0.0f);
                            drawLocatorLabelRow(r2d, bm, label, rowX, drawY, rowW, rowH,
                                    LOCATOR_LABEL_RADIUS * scale, scale, ease, distanceTextW);
                        }

                        for (int listIndex = 0; listIndex < slotted.size(); listIndex++) {
                            LocatorLabel label = slotted.get(listIndex);
                            int rowIndex = targetRows.getOrDefault(label.key(), listIndex);
                            float anim = Mth.clamp(label.alpha(), 0f, 1f);
                            float ease = locatorLabelEase(anim);
                            float rowW = rowWidths.getOrDefault(label.key(), LOCATOR_LABEL_ROW_MIN_WIDTH * scale);
                            float rowX = screenCenterX - rowW * 0.5f;
                            float rowPosition = LOCATOR_LABEL_POSITIONS.getOrDefault(label.key(), (float) rowIndex);
                            float rowY = rowBottom - rowH - rowPosition * rowStep + (1.0f - ease) * 0.75f * scale;
                            float padX = LOCATOR_LABEL_CHIP_PAD_X * scale;
                            float distanceTextW = distanceWidths.getOrDefault(label.key(), 0.0f);
                            float distancePillW = label.distance().isEmpty()
                                    ? 0.0f
                                    : distanceTextW + LOCATOR_LABEL_DISTANCE_PAD_X * 2.0f * scale;
                            float distanceGap = distancePillW > 0.0f ? LOCATOR_LABEL_DISTANCE_GAP * scale : 0.0f;
                            float nameMaxW = Math.max(8.0f * scale, rowW - padX * 2.0f - distanceGap - distancePillW);
                            float nameTextW = nameWidths.getOrDefault(label.key(), 0.0f);
                            float textY = rowY + (rowH - textH) * 0.5f;
                            float textX = rowX + padX;
                            int nameArgb = applyBarAlpha(ColorMath.scaleAlpha(theme().textPrimary(), ease * (label.target() ? 1.0f : 0.94f)), bm);
                            if (nameTextW > nameMaxW * 1.01f) {
                                drawLocatorMarqueeName(tr, label.name(), textX, textY, nameTextW, nameMaxW,
                                        labelScale, scale, new RenderColor(nameArgb));
                            } else {
                                tr.begin(labelScale, false, false);
                                try {
                                    tr.render(label.name(), textX, textY, new RenderColor(nameArgb), true);
                                } finally {
                                    tr.end();
                                }
                            }
                            if (distancePillW > 0.0f) {
                                float distanceX = rowX + rowW - padX - distancePillW * 0.5f - distanceTextW * 0.5f;
                                int distanceArgb = applyBarAlpha(ColorMath.scaleAlpha(theme().textMuted(), ease * 0.82f), bm);
                                tr.begin(labelScale, false, false);
                                try {
                                    tr.render(label.distance(), distanceX, textY, new RenderColor(distanceArgb), true);
                                } finally {
                                    tr.end();
                                }
                            }
                        }
                    } finally {
                        renderOwnedColorBatch(startedLabelBatch);
                    }
                }
            }
        } finally {
            ViewportContext.end(ctx);
        }
    }


    private static float locatorLabelRowWidth(TextRenderer tr, LocatorLabel label, float distanceTextWidth, float scale) {
        if (tr == null || label == null) return LOCATOR_LABEL_ROW_MIN_WIDTH * scale;

        float padX = LOCATOR_LABEL_CHIP_PAD_X * scale;
        float nameW = label.name() == null || label.name().isEmpty()
                ? 0.0f
                : (float) tr.getWidth(label.name(), true);
        float distancePillW = label.distance().isEmpty()
                ? 0.0f
                : distanceTextWidth + LOCATOR_LABEL_DISTANCE_PAD_X * 2.0f * scale;
        float distanceGap = distancePillW > 0.0f ? LOCATOR_LABEL_DISTANCE_GAP * scale : 0.0f;

        float wanted = padX * 2.0f + nameW + distanceGap + distancePillW;
        return Mth.clamp(wanted,
                LOCATOR_LABEL_ROW_MIN_WIDTH * scale,
                LOCATOR_LABEL_ROW_MAX_WIDTH * scale);
    }

    private static void drawLocatorLabelRow(Renderer2D r2d,
                                            CustomBar bm,
                                            LocatorLabel label,
                                            float x,
                                            float y,
                                            float w,
                                            float h,
                                            float radius,
                                            float scale,
                                            float alpha,
                                            float distanceTextWidth) {
        if (r2d == null || label == null || alpha <= 0.001f || w <= 0f || h <= 0f) return;

        float materialAlpha = alpha * (bm != null ? bm.getHudOverlayAlphaFactor() : 1.0f);
        MatteHudStyle.drawEspMattePlate(r2d, x, y, w, h, radius, materialAlpha);

        if (!label.distance().isEmpty()) {
            float padX = LOCATOR_LABEL_CHIP_PAD_X * scale;
            float pillW = distanceTextWidth + LOCATOR_LABEL_DISTANCE_PAD_X * 2.0f * scale;
            float pillH = Math.min(h - 1.2f * scale, LOCATOR_LABEL_DISTANCE_HEIGHT * scale);
            float pillX = x + w - padX - pillW;
            float pillY = y + (h - pillH) * 0.5f;
            float pillRadius = pillH * 0.5f;

            int pillFill = applyBarAlpha(ColorMath.scaleAlpha(
                    label.target()
                            ? HudRenderUtil.mixColor(theme().windowBg(), theme().accentSoft(), 0.12f)
                            : theme().windowBg(),
                    alpha * 0.62f), bm);
            int pillStroke = applyBarAlpha(ColorMath.scaleAlpha(
                    label.target() ? theme().accentSoft() : theme().strokeSoft(),
                    alpha * (label.target() ? 0.34f : 0.24f)), bm);
            r2d.roundedRect(pillX, pillY, pillW, pillH, pillRadius, 0.0f, pillFill);
            r2d.roundedRectStroke(pillX, pillY, pillW, pillH, pillRadius, 0.0f,
                    Math.max(0.45f, 0.50f * scale), pillStroke);
        }
    }

    private static boolean beginOwnedColorBatch() {
        if (Renderer2D.UI_BATCHER.isActive()) {
            return false;
        }
        Renderer2D.COLOR.begin();
        return true;
    }

    private static void renderOwnedColorBatch(boolean owned) {
        if (owned) {
            Renderer2D.COLOR.render();
        }
    }

    public static void renderXpBarText(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (mc == null || mc.player == null || ctx == null) return;
        CustomBar bm = CustomBar.get();
        if (bm == null || !bm.isHudBarEnabled() || !bm.isHotbarXpBarEnabled() || !bm.isHotbarXpBarShowLevel()) return;
        if (mc.player.experienceLevel <= 0) return;

        Component text = Component.translatable("gui.experience.level", mc.player.experienceLevel);
        int x = (ctx.guiWidth() - mc.font.width(text)) / 2 + XP_TEXT_OFFSET;
        int y = ctx.guiHeight() - 24 - 9 - 2 + XP_TEXT_OFFSET;

        int color = applyBarAlpha(bm.getXpTextArgb(), bm);
        ctx.text(mc.font, text, x + 1, y, 0xFF000000, false);
        ctx.text(mc.font, text, x - 1, y, 0xFF000000, false);
        ctx.text(mc.font, text, x, y + 1, 0xFF000000, false);
        ctx.text(mc.font, text, x, y - 1, 0xFF000000, false);
        ctx.text(mc.font, text, x, y, color, false);
    }

    public static void renderPreview(Renderer2D r2d, float x, float y, float scale, float progress) {
        CustomBar bm = CustomBar.get();
        if (bm == null) return;
        drawXpBar(r2d, x, y, scale, progress, bm);
    }

    public static XpTextInfo buildPreviewText(float barX, float barY, float scale, int level) {
        CustomBar bm = CustomBar.get();
        if (bm == null) return new XpTextInfo("", 0, 0, 0, (float) XP_TEXT_SCALE * scale);
        if (level <= 0) return new XpTextInfo("", 0, 0, 0, bm.getXpTextScale() * scale);

        String text = String.valueOf(level);
        TextRenderer tr = TextRenderer.get();
        float textScale = bm.getXpTextScale() * scale;
        tr.begin(textScale, false, false);
        int textW = (int) Math.ceil(tr.getWidth(text, true));
        int textH = (int) Math.ceil(tr.getHeight(true));
        tr.end();

        float barW = XP_BAR_WIDTH * scale;
        int tx = Math.round(barX * scale + (barW - textW) * 0.5f) + Math.round(XP_TEXT_OFFSET * scale);
        int ty = Math.round(barY * scale - textH - XP_TEXT_OFFSET * scale) + Math.round(XP_TEXT_OFFSET * scale);

        int color = applyBarAlpha(bm.getXpTextArgb(), bm);
        return new XpTextInfo(text, tx, ty, color, textScale);
    }

    private static void drawRainbowFill(Renderer2D r2d, float x, float y, float w, float h, int segments, CustomBar bm) {
        if (segments <= 0 || w <= 0f || h <= 0f) return;
        float segW = w / segments;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / (float) segments;
            float t1 = (float) (i + 1) / (float) segments;
            int c0 = rainbowColor(t0, bm);
            int c1 = rainbowColor(t1, bm);
            r2d.quad(x + segW * i, y, segW, h, c0, c1, c1, c0);
        }
    }

    private static void drawRoundedRainbowFill(Renderer2D r2d, float x, float y, float w, float h,
                                               float maskX, float maskY, float maskW, float maskH,
                                               float radius, float softness, int segments, CustomBar bm) {
        if (r2d == null || w <= 0f || h <= 0f) return;
        int segCount = Math.max(1, segments);
        float segW = w / segCount;

        for (int i = 0; i < segCount; i++) {
            float t0 = (float) i / (float) segCount;
            float t1 = (float) (i + 1) / (float) segCount;
            float x0 = x + segW * i;
            float x1 = (i == segCount - 1) ? (x + w) : (x0 + segW);

            int left = rainbowColor(t0, bm);
            int right = rainbowColor(t1, bm);

            r2d.roundedRectMaskedQuad(
                    x0, y, x1 - x0, h,
                    maskX, maskY, maskW, maskH,
                    radius, softness,
                    left, right, right, left
            );
        }
    }

    private static int rainbowColor(float t, CustomBar bm) {
        float hue = Mth.clamp(t, 0f, 1f);
        int rgb = Color.HSBtoRGB(hue, 0.65f, 1.0f) & 0x00FFFFFF;
        return applyBarAlpha(ColorMath.colorWithAlpha(rgb, XP_FILL_ALPHA), bm);
    }

    private static int applyBarAlpha(int argb, CustomBar bm) {
        if (bm == null) return argb;
        int a = (argb >>> 24) & 0xFF;
        int na = (int) (a * bm.getHudOverlayAlphaFactor());
        return (argb & 0x00FFFFFF) | ((na & 0xFF) << 24);
    }

    private static boolean isSourcePlayer(LocalPlayer player, TrackedWaypoint waypoint) {
        if (player == null || waypoint == null) return false;
        return waypoint.id().left()
                .map(uuid -> uuid.equals(player.getUUID()))
                .orElse(false);
    }

    private static int resolveWaypointColor(TrackedWaypoint waypoint) {
        CustomBar bm = CustomBar.get();

        UUID wpUuid = waypoint.id().left().orElse(null);
        boolean isPlayerWaypoint = wpUuid != null;

        if (bm != null && bm.isLocatorSearchEnabled() && isPlayerWaypoint) {
            Set<String> targets = bm.getLocatorSearchPlayers();
            if (targets != null && !targets.isEmpty()) {

                if (isPlayerInTargets(wpUuid, targets)) {
                    return 0xFF000000 | bm.getLocatorSearchColorRgb();
                }

                return 0xFF000000 | bm.getLocatorDimmedColorRgb();
            }
        }

        var cfg = waypoint.icon();
        if (cfg != null && cfg.color.isPresent()) {
            return 0xFF000000 | (cfg.color.get() & 0x00FFFFFF);
        }

        int rgb = waypoint.id()
                .map(CustomBar::colorFromUuid, CustomBar::colorFromName);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static RenderColor locatorOutlineColor(int baseArgb, float dist, CustomBar bm) {
        float t = Mth.clamp(dist / LOCATOR_OUTLINE_DISTANCE, 0f, 1f);
        int baseRgb = baseArgb & 0x00FFFFFF;
        int rgb = ColorMath.mixRgb(baseRgb, 0x000000, 0.25f + 0.35f * t);
        int alpha = Math.round(Mth.lerp(230f, 120f, t));
        int argb = applyBarAlpha((alpha << 24) | rgb, bm);
        return new RenderColor(argb);
    }

    private static float locatorOutlineThickness(float dist) {
        float t = Mth.clamp(dist / LOCATOR_OUTLINE_DISTANCE, 0f, 1f);
        return Mth.lerp(LOCATOR_OUTLINE_MAX, LOCATOR_OUTLINE_MIN, t);
    }

    private static AbstractClientPlayer resolveWaypointPlayer(Minecraft mc, UUID uuid) {
        if (mc == null || mc.level == null || uuid == null) return null;
        var player = mc.level.getPlayerByUUID(uuid);
        return player instanceof AbstractClientPlayer clientPlayer ? clientPlayer : null;
    }

    private static Identifier resolveWaypointSkin(Minecraft mc, UUID uuid, TrackedWaypoint waypoint) {
        GameProfile profile = null;
        if (mc != null && mc.getConnection() != null && uuid != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(uuid);
            if (entry != null && entry.getProfile() != null) {
                profile = entry.getProfile();
            }
        }

        if (profile == null && uuid != null) {
            profile = new GameProfile(uuid, uuid.toString());
        }

        if (profile == null) {
            String name = waypoint.id().right().orElse(null);
            if (name != null && !name.isBlank()) {
                profile = new GameProfile(offlineUuid(name), name);
            }
        }

        return PlayerSkinResolver.resolveProfileSkin(profile);
    }

    private static String getWaypointName(Minecraft mc, TrackedWaypoint waypoint, UUID uuid) {
        if (mc != null && mc.getConnection() != null && uuid != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(uuid);
            if (entry != null && entry.getProfile() != null) {
                String name = entry.getProfile().name();
                if (name != null && !name.isBlank()) return name;
            }
        }

        String fallback = waypoint.id().right().orElse(null);
        if (fallback != null && !fallback.isBlank()) return fallback;
        return uuid != null ? uuid.toString() : null;
    }

    private static String locatorLabelKey(TrackedWaypoint waypoint, UUID uuid) {
        if (uuid != null) return uuid.toString();
        return waypoint.id().right().orElse(null);
    }

    private static boolean shouldShowLocatorLabel(LocatorLabelMode mode, boolean isTarget, boolean focused) {
        return switch (mode) {
            case ALWAYS -> true;
            case CENTER -> focused;
            case TARGETS -> isTarget;
            case CENTER_TARGETS -> isTarget || focused;
        };
    }

    private static boolean isLocatorTarget(UUID wpUuid, CustomBar bm) {
        if (bm == null || wpUuid == null || !bm.isLocatorSearchEnabled()) return false;
        Set<String> targets = bm.getLocatorSearchPlayers();
        if (targets == null || targets.isEmpty()) return false;
        return isPlayerInTargets(wpUuid, targets);
    }

    private static boolean isLocatorDimmed(UUID wpUuid, CustomBar bm) {
        if (bm == null || wpUuid == null || !bm.isLocatorSearchEnabled()) return false;
        Set<String> targets = bm.getLocatorSearchPlayers();
        if (targets == null || targets.isEmpty()) return false;
        return !isPlayerInTargets(wpUuid, targets);
    }

    private static float locatorHeadScale(float dist, boolean isTarget, boolean isDimmed, CustomBar bm) {
        if (isTarget) return 1.0f;
        boolean dimmedOnly = bm != null && bm.isLocatorDimmedScaleOnly();
        if (dimmedOnly && !isDimmed) return 1.0f;

        float range = LOCATOR_HEAD_SCALE_END - LOCATOR_HEAD_SCALE_START;
        if (range <= 0.0f) return 1.0f;
        float t = Mth.clamp((dist - LOCATOR_HEAD_SCALE_START) / range, 0f, 1f);
        return Mth.lerp(t, 1.0f, LOCATOR_HEAD_MIN_SCALE);
    }

    private static float updateLocatorLabelPosition(String key, int rowIndex, float dt) {
        if (key == null || key.isBlank()) return rowIndex;
        float target = Math.max(0, rowIndex);
        Float currentValue = LOCATOR_LABEL_POSITIONS.get(key);
        if (currentValue == null || !Float.isFinite(currentValue)) {
            LOCATOR_LABEL_POSITIONS.put(key, target);
            return target;
        }
        float next = AnimationUtility.approach(currentValue, target, dt, LOCATOR_LABEL_POSITION_SPEED);
        next = AnimationUtility.snap(next, target, 0.005f);
        LOCATOR_LABEL_POSITIONS.put(key, next);
        return next;
    }

    private static void drawLocatorMarqueeName(TextRenderer tr,
                                               String text,
                                               float x,
                                               float y,
                                               float fullWidth,
                                               float viewWidth,
                                               float textScale,
                                               float visualScale,
                                               RenderColor color) {
        if (tr == null || text == null || text.isEmpty() || color == null || viewWidth <= 0.0f) return;

        float speed = LOCATOR_LABEL_SCROLL_SPEED * visualScale;
        float gap = LOCATOR_LABEL_SCROLL_GAP * visualScale;
        if (speed <= 0.0f) return;

        float cycleDistance = fullWidth + gap;
        float cycleTime = LOCATOR_LABEL_SCROLL_PAUSE_SEC + cycleDistance / speed;
        if (cycleTime <= 0.0f) return;

        float t = (Util.getMillis() / 1000.0f) % cycleTime;
        float offset = t > LOCATOR_LABEL_SCROLL_PAUSE_SEC
                ? -(t - LOCATOR_LABEL_SCROLL_PAUSE_SEC) * speed
                : 0.0f;
        float fade = Math.min(viewWidth * 0.24f, 5.0f * visualScale);
        tr.begin(textScale, false, false);
        try {
            tr.renderHorizontalFadeClipped(text, x + offset, y, color,
                    x, x + viewWidth, fade, fade, true);
            if (cycleDistance > viewWidth * 0.5f) {
                tr.renderHorizontalFadeClipped(text, x + offset + cycleDistance, y, color,
                        x, x + viewWidth, fade, fade, true);
            }
        } finally {
            tr.end();
        }
    }

    private static void appendCachedLocatorLabels(List<LocatorLabel> labels) {
        if (labels == null || LOCATOR_LABEL_CACHE.isEmpty()) return;
        for (Map.Entry<String, LocatorLabel> entry : LOCATOR_LABEL_CACHE.entrySet()) {
            float alpha = LOCATOR_LABEL_ANIM.getOrDefault(entry.getKey(), 0f);
            if (alpha <= 0.01f) continue;
            LocatorLabel cached = entry.getValue();
            labels.add(new LocatorLabel(entry.getKey(), cached.name(), cached.distance(), alpha, cached.target(), cached.focused(), cached.active(), cached.sort(), cached.yaw()));
        }
    }

    private static float locatorLabelEase(float alpha) {
        return AnimationUtility.easeOutCubic(alpha);
    }

    private static float updateLocatorLabelAnim(String key, float target, float dt) {
        float current = LOCATOR_LABEL_ANIM.getOrDefault(key, 0f);
        float next = AnimationUtility.approach(current, target, dt, LOCATOR_LABEL_ANIM_SPEED);
        next = AnimationUtility.snap(next, target, 0.01f);
        LOCATOR_LABEL_ANIM.put(key, next);
        return next;
    }

    private static void decayLocatorLabels(Set<String> seen, float dt) {
        if (LOCATOR_LABEL_ANIM.isEmpty()) return;
        var it = LOCATOR_LABEL_ANIM.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (seen.contains(entry.getKey())) continue;
            float next = AnimationUtility.approach(entry.getValue(), 0f, dt, LOCATOR_LABEL_ANIM_SPEED);
            next = AnimationUtility.snap(next, 0f, 0.01f);
            if (next <= 0.0f) {
                LOCATOR_LABEL_CACHE.remove(entry.getKey());
                LOCATOR_LABEL_POSITIONS.remove(entry.getKey());
                it.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private static String formatDistance(float dist) {
        if (!Float.isFinite(dist)) return "";
        return String.format(Locale.ROOT, "%.0fm", dist);
    }

    private static UUID offlineUuid(String name) {
        if (name == null || name.isBlank()) return new UUID(0L, 0L);
        String key = "OfflinePlayer:" + name;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isPlayerInTargets(UUID wpUuid, Set<String> targets) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return false;

        for (String raw : targets) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.isEmpty()) continue;

            if (t.equalsIgnoreCase(wpUuid.toString())) {
                return true;
            }

            PlayerInfo entry = findEntryByName(mc, t);
            if (entry != null && entry.getProfile() != null) {
                if (wpUuid.equals(entry.getProfile().id())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PlayerInfo findEntryByName(Minecraft mc, String name) {
        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            if (e.getProfile() != null
                    && e.getProfile().name().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    private static int colorFromUuid(UUID uuid) {
        if (uuid == null) return 0xFFFFFFFF;
        return colorFromHash(uuid.hashCode());
    }

    private static int colorFromName(String name) {
        if (name == null) return 0xFFFFFFFF;
        return colorFromHash(name.hashCode());
    }

    private static int colorFromHash(int hash) {
        float hue = (hash & 0xFFFF) / 65535.0f;
        int rgb = Mth.hsvToRgb(hue, 0.9f, 0.9f);
        return rgb & 0x00FFFFFF;
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.bool(xpBar));
        defs.add(SettingDef.bool(xpBarShowLevel).visibleWhen(xpBar::get));
        defs.add(SettingDef.mode(barMode).visibleWhen(xpBar::get));
        defs.add(SettingDef.mode(colorMode).visibleWhen(xpBar::get));
        defs.add(SettingDef.color(customBgColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(customFillColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(customFillGradientColor).visibleWhen(this::isCustomMode));
        defs.add(SettingDef.color(customTextColor).visibleWhen(this::isCustomMode));

        defs.add(SettingDef.bool(locatorLabels).visibleWhen(this::locatorModeEnabled));
        defs.add(SettingDef.mode(locatorLabelMode)
                .visibleWhen(() -> locatorModeEnabled() && locatorLabels.get()));
        defs.add(SettingDef.number(locatorLabelAngle)
                .visibleWhen(() -> locatorModeEnabled()
                        && locatorLabels.get()
                        && locatorLabelModeUsesCenter()));
        defs.add(SettingDef.bool(locatorLabelDistance)
                .visibleWhen(() -> locatorModeEnabled() && locatorLabels.get()));

        defs.add(SettingDef.bool(locatorSearchEnabled).visibleWhen(this::locatorModeEnabled));
        defs.add(SettingDef.textList(locatorSearchPlayers)
                .visibleWhen(() -> locatorModeEnabled() && locatorSearchEnabled.get()));
        defs.add(SettingDef.colorNoAlpha(locatorDimmedColor)
                .visibleWhen(() -> locatorModeEnabled()
                        && locatorSearchEnabled.get()
                        && !locatorSearchPlayers.get().isEmpty()));
        defs.add(SettingDef.colorNoAlpha(locatorSearchColor)
                .visibleWhen(() -> locatorModeEnabled()
                        && locatorSearchEnabled.get()
                        && !locatorSearchPlayers.get().isEmpty()));
        defs.add(SettingDef.bool(locatorDimmedScaleOnly).visibleWhen(this::locatorModeEnabled));
        defs.add(SettingDef.bool(locatorMapFilterEnabled).visibleWhen(this::locatorModeEnabled));
        defs.add(SettingDef.text(locatorMapServer)
                .visibleWhen(() -> locatorModeEnabled() && locatorMapFilterEnabled.get()));
        defs.add(SettingDef.text(locatorMapUrl)
                .visibleWhen(() -> locatorModeEnabled() && locatorMapFilterEnabled.get()));

        defs.add(SettingDef.bind(locatorTargetToggle, BindMode.PRESS).visibleWhen(this::locatorModeEnabled));
        defs.add(SettingDef.bind(locatorBarToggle, BindMode.PRESS).visibleWhen(this::locatorModeEnabled));
    }

    @Override
    protected void onLoaded() {
        registerBind(locatorTargetToggle);
        registerBind(locatorBarToggle);
    }

    @Override
    public void onTick() {
        if (KeyManager.wasPressed(bindingName(locatorTargetToggle))) {
            boolean next = !locatorSearchEnabled.get();
            locatorSearchEnabled.set(next);
            HudNotifier.pushState("Locator target", next);
            saveConfig();
        }

        if (KeyManager.wasPressed(bindingName(locatorBarToggle))) {
            BarMode mode = barMode.get();
            if (mode != BarMode.AUTO) {
                barMode.set(mode == BarMode.LOCATOR ? BarMode.LEVEL : BarMode.LOCATOR);
                saveConfig();
            }
        }
    }

    public boolean isHudBarEnabled() {
        return !RuntimeGate.isPanic() && isEnabled();
    }

    public float getHudOverlayAlphaFactor() {
        return getHudOverlayAlpha() / 255f;
    }

    public int getHudOverlayAlpha() {
        int v = overlayAlpha.get();
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public boolean isHotbarXpBarEnabled() {
        return !RuntimeGate.isPanic() && xpBar.get();
    }

    public boolean isHotbarXpBarShowLevel() {
        return !RuntimeGate.isPanic() && xpBarShowLevel.get();
    }

    public BarMode getHotbarBarMode() {
        return barMode.get();
    }

    public int getHotbarXpBarBgRgb() {
        return xpBarBgColor.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarXpBarFillStartRgb() {
        return xpBarFillStart.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarXpBarFillEndRgb() {
        return xpBarFillEnd.getArgb() & 0x00FFFFFF;
    }

    public int getHotbarXpBarTextRgb() {
        return xpBarTextColor.getArgb() & 0x00FFFFFF;
    }

    public XpBarStyle getHotbarXpBarStyle() {
        return xpBarStyle.get();
    }

    public float getBarWidth() {
        return XP_BAR_WIDTH;
    }

    public float getBarHeight() {
        return XP_BAR_HEIGHT;
    }

    private boolean isCustomXpStyleEnabled() {
        return xpBar.get() && isCustomMode();
    }

    private boolean isDefaultXpStyleEnabled() {
        return xpBar.get() && !isCustomMode();
    }

    private boolean isGradientXpStyleEnabled() {
        return xpBar.get();
    }

    private boolean isCustomXpGradientEnabled() {
        return isCustomXpStyleEnabled();
    }

    private float getXpBarRadius() {
        return XP_BAR_RADIUS;
    }

    private float getXpTextScale() {
        return (float) XP_TEXT_SCALE;
    }

    private int getXpTextArgb() {
        if (isCustomXpStyleEnabled()) {
            return customTextColor.getArgb();
        }
        return HudRenderUtil.mixColor(theme().textPrimary(), theme().accent(), 0.32f);
    }

    private boolean isCustomMode() {
        return COLOR_CUSTOM.equals(colorMode.get());
    }

    private boolean isThemeMode() {
        return !isCustomMode();
    }

    public boolean isLocatorSearchEnabled() {
        return !RuntimeGate.isPanic() && locatorModeEnabled() && locatorSearchEnabled.get();
    }

    public Set<String> getLocatorSearchPlayers() {
        return locatorSearchPlayers.get();
    }

    public int getLocatorDimmedColorRgb() {
        return locatorDimmedColor.getArgb() & 0x00FFFFFF;
    }

    public int getLocatorSearchColorRgb() {
        return locatorSearchColor.getArgb() & 0x00FFFFFF;
    }

    public boolean isLocatorDimmedScaleOnly() {
        return locatorDimmedScaleOnly.get();
    }

    public boolean isLocatorMapFilterEnabled() {
        return locatorMapFilterEnabled.get();
    }

    public String getLocatorMapServer() {
        return locatorMapServer.get();
    }

    public String getLocatorMapUrl() {
        return locatorMapUrl.get();
    }

    public boolean isLocatorLabelsEnabled() {
        return !RuntimeGate.isPanic() && locatorModeEnabled() && locatorLabels.get();
    }

    public boolean isLocatorLabelDistanceEnabled() {
        return isLocatorLabelsEnabled() && locatorLabelDistance.get();
    }

    public float getLocatorLabelAngle() {
        return locatorLabelAngle.get();
    }

    public LocatorLabelMode getLocatorLabelMode() {
        return locatorLabelMode.get();
    }

    private boolean locatorModeEnabled() {
        if (!xpBar.get()) return false;
        return barMode.get() != BarMode.LEVEL;
    }

    private boolean locatorLabelModeUsesCenter() {
        LocatorLabelMode mode = locatorLabelMode.get();
        return mode == LocatorLabelMode.CENTER || mode == LocatorLabelMode.CENTER_TARGETS;
    }

    private String bindingName(KeyBindValue value) {
        return name() + ":" + value.getName();
    }

    private void registerBind(KeyBindValue value) {
        String name = bindingName(value);
        KeyManager.unregisterAll(name);
        if (!value.isNone()) {
            KeyManager.registerCombo(name, value.get());
        }
    }

    public enum BarMode implements EnumValue.IdProvider {
        LEVEL("level"),
        LOCATOR("locator"),
        AUTO("auto");

        private final String id;

        BarMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum XpBarStyle implements EnumValue.IdProvider {
        GRADIENT("gradient"),
        RAINBOW("rainbow"),
        CUSTOM("custom");

        private final String id;

        XpBarStyle(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum LocatorLabelMode implements EnumValue.IdProvider {
        ALWAYS("always"),
        CENTER("center"),
        TARGETS("targets"),
        CENTER_TARGETS("center_targets");

        private final String id;

        LocatorLabelMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public static final class XpTextInfo {
        public final String text;
        public final int x;
        public final int y;
        public final int color;
        public final float scale;

        XpTextInfo(String text, int x, int y, int color, float scale) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.scale = scale;
        }
    }

    private record LocatorLabel(String key, String name, String distance, float alpha, boolean target, boolean focused, boolean active, String sort, float yaw) {
    }

    private record BarColors(int track,
                             int fillStart,
                             int fillEnd,
                             int stroke,
                             int trackTexture,
                             int fillTexture,
                             int locatorTexture) {
    }

    private static final class LocatorCandidate {
        final TrackedWaypoint waypoint;
        final UUID uuid;
        final float yaw;
        final float dist;
        final String name;
        final String nameSort;
        final String key;
        final String keySort;

        private LocatorCandidate(TrackedWaypoint waypoint, UUID uuid, float yaw, float dist, String name, String key) {
            this.waypoint = waypoint;
            this.uuid = uuid;
            this.yaw = yaw;
            this.dist = dist;
            this.name = name != null ? name : "";
            this.nameSort = this.name.toLowerCase(Locale.ROOT);
            this.key = key;
            this.keySort = key != null ? key : "";
        }
    }

    private record LocatorHead(float x, float y, float size, float radius, AbstractClientPlayer player,
                               Identifier skin, RenderColor color, RenderColor outlineColor, float outlineThickness,
                               boolean target) {
    }
}
