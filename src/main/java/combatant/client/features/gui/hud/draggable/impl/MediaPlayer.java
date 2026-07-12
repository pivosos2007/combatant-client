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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import combatant.client.config.SettingDef;
import combatant.client.events.impl.RenderPrewarmCollectEvent;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.media.MediaInfo;
import combatant.client.util.media.MediaSessionService;
import combatant.client.util.media.RepeatMode;

import java.util.List;

import static combatant.client.features.theme.Theme.theme;

@HudElementRegister(order = 70)
public final class MediaPlayer extends DraggableHudElement {

    {
        defaultLayout(12.0f, 82.30222f);
    }


    private static final float BASE_PADDING = 6f;
    private static final float BASE_GAP = 6f;
    private static final float BASE_ART_SIZE = 30f;
    private static final float BASE_ART_BOOST = 3f;
    private static final float BASE_ART_OVERHANG = 4f;
    private static final float BASE_RADIUS = 6f;
    private static final float BASE_BAR_H = 2.4f;
    private static final float BASE_BUTTON_SIZE = 16f;
    private static final float BASE_BUTTON_GAP = 8f;
    private static final float BASE_TEXT_GAP = 2f;
    private static final float BASE_BAR_GAP = 4f;
    private static final float BASE_BOTTOM_GAP = 4f;
    private static final float BASE_TITLE_SCROLL_SPEED = 22f;
    private static final float BASE_TITLE_SCROLL_GAP = 14f;
    private static final float BASE_TITLE_FADE = 12f;
    private static final float TITLE_SCROLL_PAUSE_SEC = 0.8f;
    private static final String EFFECT_NONE = "None";
    private static final String EFFECT_BLUR = "Blur";
    private static final String EFFECT_GLASS = "Glass";

    private static final float BASE_CONTENT_W = 170f;

    private static final int ICON_PAUSE = 0xEA02;
    private static final int ICON_PLAY = 0xEA03;
    private static final int ICON_PREV = 0xEA04;
    private static final int ICON_NEXT = 0xEA05;
    private static final int ICON_SHUFFLE = 0xEA06;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();
    private final MediaSessionService mediaService = MediaSessionService.get();
    private final NumberValue<Double> scale =
            new NumberValue<>("media_player_scale", 1.51, 0.5, 5.0);
    private final BooleanValue syncTheme =
            new BooleanValue("media_player_sync_theme", true);
    private final RGBAColorValue bg =
            new RGBAColorValue("media_player_bg", "#60000000");
    private final NumberValue<Integer> bgAlpha =
            new NumberValue<>("media_player_bg_alpha", 124, 0, 255);
    private final RGBColorValue accent =
            new RGBColorValue("media_player_accent", "#FF2F2F");
    private final RGBColorValue text =
            new RGBColorValue("media_player_text", "#FFFFFF");
    private final ModeValue bgEffect =
            new ModeValue("media_player_bg_effect", "None", EFFECT_NONE, EFFECT_BLUR, EFFECT_GLASS);
    private final NumberValue<Integer> blurAlpha =
            new NumberValue<>("media_player_blur_alpha", 255, 0, 255);
    private final BooleanValue gradient =
            new BooleanValue("media_player_gradient", true);
    private float anim = 0f;
    private MediaSessionService.Snapshot currentSnapshot = MediaSessionService.Snapshot.empty(MediaSessionService.isMediaAvailable());

    private float playHover;
    private float shuffleHover;
    private float prevHover;
    private float nextHover;
    private float repeatHover;

    private float shuffleX;
    private float shuffleY;
    private float playX;
    private float playY;
    private float prevX;
    private float prevY;
    private float nextX;
    private float nextY;
    private float repeatX;
    private float repeatY;
    private float buttonSize;
    private int uiAccent;
    private int uiAccentSoft;
    private int uiText;
    private int uiTextMuted;

    public MediaPlayer() {
        super("media_player", "Media Player", false);
    }

    private static String iconString(int codepoint) {
        return new String(Character.toChars(codepoint));
    }

    private static TextRenderer getMediaIcons(TextRenderer fallback) {
        return Fonts.renderer("MediaPlayer", FontInfo.Type.Regular, fallback);
    }

    public void onResourceReload() {
        mediaService.onResourceReload();
        currentSnapshot = MediaSessionService.Snapshot.empty(MediaSessionService.isMediaAvailable());
    }

    @Override
    protected void defineSettings(List<SettingDef> defs) {
        defs.add(SettingDef.number(scale));
        defs.add(SettingDef.bool(syncTheme));
        defs.add(SettingDef.color(bg).visibleWhen(() -> !syncTheme.get() && !isGlassEffect()));
        defs.add(SettingDef.number(bgAlpha).visibleWhen(() -> syncTheme.get() && !isGlassEffect()));
        defs.add(SettingDef.colorNoAlpha(accent).visibleWhen(() -> !syncTheme.get()));
        defs.add(SettingDef.colorNoAlpha(text).visibleWhen(() -> !syncTheme.get()));
        defs.add(SettingDef.mode(bgEffect));
        defs.add(SettingDef.number(blurAlpha).visibleWhen(this::hasEffect));
        defs.add(SettingDef.bool(gradient).visibleWhen(() -> syncTheme.get() && !isGlassEffect()));
    }

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float scale = HudScale.scale(screenW, screenH) * this.scale.get().floatValue();
        this.x = 20f * scale;
        this.y = 20f * scale;
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public boolean isMouseOverInteractive(float mx, float my) {
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;
        if (buttonSize <= 0f) return false;
        boolean showShuffle = currentSnapshot != null && currentSnapshot.supportsShuffle();
        boolean showRepeat = currentSnapshot != null && currentSnapshot.supportsRepeat();
        return hit(mx, my, playX, playY, buttonSize, buttonSize)
                || (showShuffle && hit(mx, my, shuffleX, shuffleY, buttonSize, buttonSize))
                || hit(mx, my, prevX, prevY, buttonSize, buttonSize)
                || hit(mx, my, nextX, nextY, buttonSize, buttonSize)
                || (showRepeat && hit(mx, my, repeatX, repeatY, buttonSize, buttonSize));
    }

    @Override
    public boolean onMouseClicked(float mx, float my, int button) {
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;
        if (currentSnapshot == null || !currentSnapshot.hasSession()) return false;
        if (button != 0) return false;

        boolean consumed = false;
        try {
            if (currentSnapshot.supportsShuffle() && hit(mx, my, shuffleX, shuffleY, buttonSize, buttonSize)) {
                mediaService.toggleShuffle();
                consumed = true;
            } else if (hit(mx, my, playX, playY, buttonSize, buttonSize)) {
                mediaService.playPause();
                consumed = true;
            } else if (hit(mx, my, prevX, prevY, buttonSize, buttonSize)) {
                mediaService.previous();
                consumed = true;
            } else if (hit(mx, my, nextX, nextY, buttonSize, buttonSize)) {
                mediaService.next();
                consumed = true;
            } else if (currentSnapshot.supportsRepeat() && hit(mx, my, repeatX, repeatY, buttonSize, buttonSize)) {
                mediaService.cycleRepeatMode();
                consumed = true;
            }
        } catch (Throwable ignored) {
        }
        return consumed;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        if (mc == null) return;
        boolean chatOpen = ClientScreen.current() instanceof ChatScreen;
        boolean preview = DraggableHudElementRegistry.isForceVisible();

        MediaSessionService.Snapshot snapshot = mediaService.snapshot();
        currentSnapshot = snapshot;
        MediaInfo media = snapshot.media();
        boolean hasSession = snapshot.hasSession();
        boolean mediaAvailable = snapshot.mediaAvailable();
        boolean shouldShow = hasSession || chatOpen || preview;

        float target = shouldShow ? 1.0f : 0.0f;
        anim = lerp(anim, target, 0.25f);
        if (!shouldShow && anim <= 0.02f) {
            width = 0f;
            height = 0f;
            updateButtons();
            return;
        }

        TextRenderer titleRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, textRenderer);
        TextRenderer artistRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, textRenderer);
        TextRenderer metaRenderer = Fonts.renderer("Inter", FontInfo.Type.Regular, textRenderer);
        TextRenderer iconRenderer = getMediaIcons(textRenderer);

        float mediaScale = scale.get().floatValue();
        float baseScale = HudScale.scale(screenW, screenH) * mediaScale;
        float fontScale = (hud.getFontSize() / 18f) * mediaScale;
        float padding = BASE_PADDING * baseScale;
        float gap = BASE_GAP * baseScale;
        float artBase = BASE_ART_SIZE * baseScale;
        float artBoost = BASE_ART_BOOST * baseScale;
        float artOverhang = BASE_ART_OVERHANG * baseScale;
        float radius = BASE_RADIUS * baseScale;
        float barH = BASE_BAR_H * baseScale;
        float textGap = BASE_TEXT_GAP * baseScale;
        float barGap = BASE_BAR_GAP * baseScale;
        float bottomGap = BASE_BOTTOM_GAP * baseScale;
        float buttonGap = BASE_BUTTON_GAP * baseScale;
        float contentW = BASE_CONTENT_W * baseScale;

        float titleScale = 0.95f * fontScale;
        float artistScale = 0.78f * fontScale;
        float metaScale = 0.70f * fontScale;
        float iconScale = 0.9f * fontScale;

        String title = hasSession ? safe(media.title()) : "";
        String artist = hasSession ? safe(media.artist()) : "";
        String owner = hasSession && snapshot.session() != null ? safe(snapshot.session().getOwner()) : "";
        long predictedPos = hasSession ? snapshot.predictedPositionSeconds() : 0L;
        long predictedDur = hasSession ? snapshot.durationSeconds() : 0L;
        String timeText = hasSession ? formatTime(predictedPos) : "";

        metaRenderer.begin(metaScale, false, false);
        float timeW = hasSession ? (float) metaRenderer.getWidth(timeText, false) : 0f;
        metaRenderer.end();

        final boolean showShuffle = hasSession && snapshot.supportsShuffle();
        final boolean showRepeat = hasSession && snapshot.supportsRepeat();
        final int activeControls = 3 + (showShuffle ? 1 : 0) + (showRepeat ? 1 : 0);
        final float btnSizeBase = BASE_BUTTON_SIZE * baseScale;

        float maxOwnerW = Math.max(0f, contentW - timeW - buttonGap);
        String ownerText = hasSession ? trimText(metaRenderer, owner, maxOwnerW, metaScale) : "";


        String titleText = hasSession ? title : "";
        String artistText = hasSession ? trimText(artistRenderer, artist, contentW, artistScale) : "";

        titleRenderer.begin(titleScale, false, false);
        float titleW = hasSession ? (float) titleRenderer.getWidth(titleText, false) : 0f;
        float titleH = (float) titleRenderer.getHeight(false);
        titleRenderer.end();

        artistRenderer.begin(artistScale, false, false);
        if (hasSession) {
            artistRenderer.getWidth(artistText, false);
        }
        float artistH = (float) artistRenderer.getHeight(false);
        artistRenderer.end();

        metaRenderer.begin(metaScale, false, false);
        float metaH = (float) metaRenderer.getHeight(false);
        float ownerW = hasSession ? (float) metaRenderer.getWidth(ownerText, false) : 0f;
        metaRenderer.end();

        float contentH = titleH + textGap + artistH + barGap + barH + bottomGap + metaH;
        float artSize = Math.max(artBase, contentH + artBoost);
        float leftPad = padding + artOverhang;

        float bodyH = Math.max(contentH, artSize);

        float boxW = leftPad + padding + artSize + gap + contentW;
        float boxH = padding * 2f + bodyH;

        width = boxW;
        height = boxH;

        float scale = Math.max(0f, Math.min(1f, anim));
        float drawX = x + (boxW - boxW * scale) * 0.5f;
        float drawY = y + (boxH - boxH * scale) * 0.5f;

        boolean useSyncTheme = syncTheme.get();
        boolean blurEnabled = isBlurEffect();
        boolean glassEnabled = isGlassEffect();
        int baseRgb = glassEnabled ? HudRenderUtil.glassBackgroundRgb()
                : (useSyncTheme ? (theme().windowBg() & 0x00FFFFFF) : (bg.getArgb() & 0x00FFFFFF));
        int bgAlpha = getBgAlpha();
        int bg = (bgAlpha << 24) | baseRgb;
        int accent = useSyncTheme ? theme().accent() : (this.accent.getArgb() | 0xFF000000);
        int accentSoft = useSyncTheme
                ? theme().accentSoft()
                : HudRenderUtil.mixColor(accent, bg, 0.55f);
        int stroke = HudRenderUtil.scaleAlpha(accent, 0.75f);
        int strokeSoft = HudRenderUtil.scaleAlpha(accentSoft, 0.45f);
        int borderColor = HudRenderUtil.mixColor(stroke, strokeSoft, 0.5f);
        uiAccent = accent;
        uiAccentSoft = accentSoft;
        uiText = useSyncTheme ? theme().textPrimary() : (text.getArgb() | 0xFF000000);
        uiTextMuted = useSyncTheme ? theme().textMuted() : HudRenderUtil.mixColor(uiText, bg, 0.6f);

        if (glassEnabled) {
            drawGlass(drawX, drawY, boxW * scale, boxH * scale, radius * scale, baseScale * scale, uiAccentSoft);
        } else if (blurEnabled) {
            drawBlur(drawX, drawY, boxW * scale, boxH * scale, radius * scale, bg);
        }

        int bgScaled = glassEnabled
                ? HudRenderUtil.glassPanelBackground(scale)
                : HudRenderUtil.scaleAlpha(bg, scale);
        boolean useGradient = !glassEnabled && useSyncTheme && gradient.get();
        HudRenderUtil.drawHudBackground(renderer, drawX, drawY, boxW * scale, boxH * scale, radius * scale, 1.0f, bgScaled, useGradient);
        if (!glassEnabled) {
            renderer.roundedRectStroke(drawX, drawY, boxW * scale, boxH * scale, radius * scale, 1.0f, 0.9f * baseScale * scale,
                    HudRenderUtil.scaleAlpha(borderColor, scale));
        }

        float contentY = padding + (bodyH - contentH) * 0.5f;

        if (!hasSession) {
            String statusKey = mediaAvailable
                    ? "hud.media_player.available"
                    : "hud.media_player.unavailable";
            String status = I18n.get(statusKey);

            metaRenderer.begin(metaScale * scale, false, false);
            float statusW = (float) metaRenderer.getWidth(status, false);
            float statusH = (float) metaRenderer.getHeight(false);
            metaRenderer.end();

            float sx = drawX + (boxW * scale - statusW) * 0.5f;
            float sy = drawY + (boxH * scale - statusH) * 0.5f;

            metaRenderer.begin(metaScale * scale, false, false);
            metaRenderer.render(status, sx, sy, new RenderColor(HudRenderUtil.scaleAlpha(uiTextMuted, scale)), false);
            metaRenderer.end();
            updateButtons();
            return;
        }

        float artX = leftPad - (2f * baseScale);
        float artY = padding + (bodyH - artSize) * 0.5f;
        float textX = leftPad + artSize + gap;
        float artistY = contentY + titleH + textGap;
        float barY = artistY + artistH + barGap;
        float bottomY = barY + barH + bottomGap;

        float drawArtX = drawX + artX * scale;
        float drawArtY = drawY + artY * scale;
        float drawArtSize = artSize * scale;
        float artRadius = Math.min(radius * 1.2f, artSize * 0.22f) * scale;

        Identifier artworkTexture = snapshot.artworkTexture();
        if (artworkTexture != null) {
            Renderer2D.TEXTURE.roundedTexRect(drawArtX, drawArtY, drawArtSize, drawArtSize, artRadius,
                    1.0f, HudRenderUtil.scaleAlpha(0xFFFFFFFF, scale), artworkTexture);
        } else {
            renderer.roundedRect(drawArtX, drawArtY, drawArtSize, drawArtSize, artRadius,
                    1.0f, HudRenderUtil.scaleAlpha(0x44111111, scale));
            renderer.roundedRectStroke(drawArtX, drawArtY, drawArtSize, drawArtSize, artRadius,
                    1.0f, 0.9f * baseScale * scale, HudRenderUtil.scaleAlpha(uiAccentSoft, scale * 0.9f));
        }

        float titleRenderX = drawX + textX * scale;
        float titleRenderY = drawY + contentY * scale;
        float titleRenderW = contentW * scale;
        float titleRenderH = titleH * scale;
        boolean titleScroll = titleW > contentW * 1.01f;

        if (titleScroll) {
            drawScrollingTitle(titleRenderer, titleText, titleRenderX, titleRenderY, titleW * scale, titleRenderW,
                    titleScale * scale, baseScale, scale);
        } else {
            titleRenderer.begin(titleScale * scale, false, false);
            titleRenderer.render(titleText, titleRenderX, titleRenderY, new RenderColor(HudRenderUtil.scaleAlpha(uiText, scale)), false);
            titleRenderer.end();
        }

        artistRenderer.begin(artistScale * scale, false, false);
        artistRenderer.render(artistText, drawX + textX * scale, drawY + artistY * scale,
                new RenderColor(HudRenderUtil.scaleAlpha(uiTextMuted, scale)), false);
        artistRenderer.end();

        float barX = drawX + textX * scale;
        float barW = contentW * scale;
        float barRadius = Math.max(1f, barH * 0.5f) * scale;
        int barBg = HudRenderUtil.scaleAlpha(0x55000000, scale);
        renderer.roundedRect(barX, drawY + barY * scale, barW, barH * scale, barRadius, 1.0f, barBg);

        float progress = predictedDur > 0 ? Math.min(1.0f, Math.max(0.0f,
                (float) predictedPos / (float) predictedDur)) : 0f;
        int barFill = HudRenderUtil.scaleAlpha(uiAccent, scale);
        renderer.roundedRect(barX, drawY + barY * scale, barW * progress, barH * scale, barRadius, 1.0f, barFill);

        metaRenderer.begin(metaScale * scale, false, false);
        metaRenderer.render(timeText, drawX + textX * scale, drawY + bottomY * scale,
                new RenderColor(HudRenderUtil.scaleAlpha(uiTextMuted, scale)), false);

        float ownerX = drawX + (textX + contentW - ownerW) * scale;
        metaRenderer.render(ownerText, ownerX, drawY + bottomY * scale,
                new RenderColor(HudRenderUtil.scaleAlpha(uiTextMuted, scale)), false);
        metaRenderer.end();

        buttonSize = btnSizeBase * scale;

        float controlsReservedW = btnSizeBase * activeControls + buttonGap * Math.max(0, activeControls - 1);
        float controlsX = textX + (contentW - controlsReservedW) * 0.5f;
        float controlsY = bottomY + (metaH - btnSizeBase) * 0.5f;
        float cursorX = controlsX;

        if (showShuffle) {
            shuffleX = drawX + cursorX * scale;
            shuffleY = drawY + controlsY * scale;
            cursorX += btnSizeBase + buttonGap;
        } else {
            shuffleX = shuffleY = 0f;
        }

        prevX = drawX + cursorX * scale;
        prevY = drawY + controlsY * scale;
        cursorX += btnSizeBase + buttonGap;

        playX = drawX + cursorX * scale;
        playY = prevY;
        cursorX += btnSizeBase + buttonGap;

        nextX = drawX + cursorX * scale;
        nextY = prevY;
        cursorX += btnSizeBase + buttonGap;

        if (showRepeat) {
            repeatX = drawX + cursorX * scale;
            repeatY = prevY;
        } else {
            repeatX = repeatY = 0f;
        }

        float controlIconScale = iconScale * 1.05f;

        updateHover(chatOpen, showShuffle, showRepeat);
        if (showShuffle) {
            drawControl(renderer, iconRenderer, iconString(ICON_SHUFFLE),
                    shuffleX, shuffleY, buttonSize, shuffleHover, scale, controlIconScale, shuffleColor(snapshot, scale));
        }

        drawControl(renderer, iconRenderer, iconString(ICON_PREV),
                prevX, prevY, buttonSize, prevHover, scale, controlIconScale, HudRenderUtil.scaleAlpha(uiText, scale));

        drawControl(renderer, iconRenderer,
                iconString(snapshot.isPlaying() ? ICON_PAUSE : ICON_PLAY),
                playX, playY, buttonSize, playHover, scale, controlIconScale, HudRenderUtil.scaleAlpha(uiText, scale));

        drawControl(renderer, iconRenderer, iconString(ICON_NEXT),
                nextX, nextY, buttonSize, nextHover, scale, controlIconScale, HudRenderUtil.scaleAlpha(uiText, scale));

        if (showRepeat) {
            drawRepeatControl(renderer, snapshot, repeatX, repeatY, buttonSize, repeatHover, scale);
        }
    }

    private void drawControl(Renderer2D renderer,
                             TextRenderer iconRenderer,
                             String icon,
                             float x,
                             float y,
                             float size,
                             float hover,
                             float alpha,
                             float iconScale,
                             int iconColor) {
        if (size <= 0f) return;
        float radius = size * 0.5f;
        float hoverEase = clamp01(hover);
        int bg = HudRenderUtil.scaleAlpha(uiAccentSoft, hoverEase * 0.45f * alpha);
        if (hoverEase > 0.01f) {
            renderer.roundedRect(x, y, size, size, radius, 1.0f, bg);
        }

        iconRenderer.begin(iconScale * alpha, false, false);
        float iw = (float) iconRenderer.getWidth(icon, false);
        float ih = (float) iconRenderer.getHeight(false);
        float ix = x + (size - iw) * 0.5f;
        float iy = y + (size - ih) * 0.5f;
        iconRenderer.render(icon, ix, iy, new RenderColor(iconColor), false);
        iconRenderer.end();
    }

    private void drawRepeatControl(Renderer2D renderer,
                                   MediaSessionService.Snapshot snapshot,
                                   float x,
                                   float y,
                                   float size,
                                   float hover,
                                   float alpha) {
        if (size <= 0f) return;
        float radius = size * 0.5f;
        float hoverEase = clamp01(hover);
        int bg = HudRenderUtil.scaleAlpha(uiAccentSoft, hoverEase * 0.45f * alpha);
        if (hoverEase > 0.01f) {
            renderer.roundedRect(x, y, size, size, radius, 1.0f, bg);
        }

        String icon = repeatSvg(snapshot);
        int color = repeatColor(snapshot, alpha);
        float iconSize = size * 0.78f;
        float ix = x + (size - iconSize) * 0.5f;
        float iy = y + (size - iconSize) * 0.5f;
        renderer.svg(icon, ix, iy, iconSize, iconSize, SvgRenderOptions.overrideColor(color));
    }

    private void updateHover(boolean chatOpen, boolean showShuffle, boolean showRepeat) {
        if (!chatOpen) {
            playHover = lerp(playHover, 0f, 0.2f);
            shuffleHover = lerp(shuffleHover, 0f, 0.2f);
            prevHover = lerp(prevHover, 0f, 0.2f);
            nextHover = lerp(nextHover, 0f, 0.2f);
            repeatHover = lerp(repeatHover, 0f, 0.2f);
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        float uiScale = HudScale.scale(fbw, fbh);
        float mx = HudScale.toVirtual((float) mc.mouseHandler.xpos(), uiScale);
        float my = HudScale.toVirtual((float) mc.mouseHandler.ypos(), uiScale);
        shuffleHover = lerp(shuffleHover, showShuffle && hit(mx, my, shuffleX, shuffleY, buttonSize, buttonSize) ? 1f : 0f, 0.25f);
        playHover = lerp(playHover, hit(mx, my, playX, playY, buttonSize, buttonSize) ? 1f : 0f, 0.25f);
        prevHover = lerp(prevHover, hit(mx, my, prevX, prevY, buttonSize, buttonSize) ? 1f : 0f, 0.25f);
        nextHover = lerp(nextHover, hit(mx, my, nextX, nextY, buttonSize, buttonSize) ? 1f : 0f, 0.25f);
        repeatHover = lerp(repeatHover, showRepeat && hit(mx, my, repeatX, repeatY, buttonSize, buttonSize) ? 1f : 0f, 0.25f);
    }

    private void updateButtons() {
        shuffleX = (float) 0.0;
        shuffleY = (float) 0.0;
        playX = (float) 0.0;
        playY = (float) 0.0;
        prevX = (float) 0.0;
        prevY = (float) 0.0;
        nextX = (float) 0.0;
        nextY = (float) 0.0;
        repeatX = (float) 0.0;
        repeatY = (float) 0.0;
        buttonSize = (float) 0.0;
        playHover = 0f;
        shuffleHover = 0f;
        prevHover = 0f;
        nextHover = 0f;
        repeatHover = 0f;
    }

    private int shuffleColor(MediaSessionService.Snapshot snapshot, float alpha) {
        if (snapshot == null || !snapshot.supportsShuffle()) {
            return HudRenderUtil.scaleAlpha(0xFF000000, 0.36f * alpha);
        }
        return snapshot.isShuffleActive()
                ? HudRenderUtil.scaleAlpha(0xFFFFFFFF, alpha)
                : HudRenderUtil.scaleAlpha(0xFFB7BEC8, 0.48f * alpha);
    }

    private int repeatColor(MediaSessionService.Snapshot snapshot, float alpha) {
        if (snapshot == null || !snapshot.supportsRepeat()) {
            return HudRenderUtil.scaleAlpha(0xFF000000, 0.36f * alpha);
        }
        RepeatMode mode = snapshot.repeatMode();
        return mode == RepeatMode.ALL || mode == RepeatMode.ONE
                ? HudRenderUtil.scaleAlpha(0xFFFFFFFF, alpha)
                : HudRenderUtil.scaleAlpha(0xFFB7BEC8, 0.48f * alpha);
    }

    private String repeatSvg(MediaSessionService.Snapshot snapshot) {
        if (snapshot == null || !snapshot.supportsRepeat()) {
            return "repeat-off";
        }
        return snapshot.repeatMode() == RepeatMode.ONE ? "repeat-1" : "repeat";
    }

    private String formatTime(long seconds) {
        long sec = Math.max(0L, seconds);
        long m = sec / 60L;
        long s = sec % 60L;
        return String.format("%d:%02d", m, s);
    }

    private String safe(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.isEmpty() ? "" : v;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void drawScrollingTitle(TextRenderer renderer,
                                    String text,
                                    float x,
                                    float y,
                                    float fullWidth,
                                    float viewWidth,
                                    float scale,
                                    float baseScale,
                                    float alpha) {
        float speed = BASE_TITLE_SCROLL_SPEED * baseScale;
        float gap = BASE_TITLE_SCROLL_GAP * baseScale;
        if (speed <= 0f) return;
        float cycleDistance = fullWidth + gap;
        float cycleTime = TITLE_SCROLL_PAUSE_SEC + (cycleDistance / speed);
        if (cycleTime <= 0f) return;

        float now = Util.getMillis() / 1000f;
        float t = now % cycleTime;
        float offset = 0f;
        if (t > TITLE_SCROLL_PAUSE_SEC) {
            offset = -(t - TITLE_SCROLL_PAUSE_SEC) * speed;
        }

        int color = HudRenderUtil.scaleAlpha(uiText, alpha);
        RenderColor renderColor = new RenderColor(color);
        float fade = Math.min(viewWidth * 0.25f, BASE_TITLE_FADE * baseScale);
        renderer.begin(scale, false, false);
        try {
            renderer.renderHorizontalFadeClipped(text, x + offset, y, renderColor, x, x + viewWidth, fade, fade, false);
            if (cycleDistance > viewWidth * 0.5f) {
                renderer.renderHorizontalFadeClipped(text, x + offset + cycleDistance, y, renderColor,
                        x, x + viewWidth, fade, fade, false);
            }
        } finally {
            renderer.end();
        }
    }

    private float getTextHeight(TextRenderer renderer, float scale) {
        renderer.begin(scale, false, false);
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    private String trimText(TextRenderer tr, String text, float maxWidth, float scale) {
        if (text == null || text.isEmpty()) return "";
        tr.begin(scale, false, false);
        try {
            if (tr.getWidth(text, false) <= maxWidth) return text;
            String value = text;
            while (!value.isEmpty()) {
                String candidate = value + "...";
                if (tr.getWidth(candidate, false) <= maxWidth) return candidate;
                value = value.substring(0, value.length() - 1);
            }
            return "";
        } finally {
            tr.end();
        }
    }

    private void drawBlur(float x, float y, float w, float h, float radius, int tintRgb) {
        float quality = hud.getBlurRadius();
        float brightness = 1.0f;
        float alpha = blurAlpha.get() / 255f;
        Renderer2D.COLOR.blurRect(x, y, w, h, radius, quality, brightness, alpha, 0xFFFFFF);
    }

    private void drawGlass(float x, float y, float w, float h, float radius, float scale, int accentSoft) {
        float alpha = blurAlpha.get() / 255f;
        HudRenderUtil.drawLiquidGlass(x, y, w, h, radius, scale, true, alpha);
    }

    private boolean hasEffect() {
        return !EFFECT_NONE.equals(bgEffect.get());
    }

    private boolean isBlurEffect() {
        return EFFECT_BLUR.equals(bgEffect.get());
    }

    private boolean isGlassEffect() {
        return EFFECT_GLASS.equals(bgEffect.get());
    }

    private int getBgAlpha() {
        if (syncTheme.get()) {
            return bgAlpha.get();
        }
        return (bg.getArgb() >>> 24) & 0xFF;
    }
    @Override
    public void collectRenderPrewarm(RenderPrewarmCollectEvent event) {
        super.collectRenderPrewarm(event);
        event.font("MediaPlayer", FontInfo.Type.Regular)
                .font("Inter", FontInfo.Type.Bold)
                .font("InterMedium", FontInfo.Type.Regular)
                .svg("repeat")
                .svg("repeat-1")
                .svg("repeat-off");
    }

}

