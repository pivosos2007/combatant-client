/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.TextSizing;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Locale;

import static combatant.client.features.theme.Theme.theme;

/**
 * Tenacity-style W / ASD / space layout with renderer-native, per-key kinetic effects.
 * All visual effects use the existing batched SDF shape pipeline, not per-key FBOs.
 */
@HudElementInfo(
        id = "key_strokes",
        displayName = "KeyStrokes",
        enabledByDefault = false,
        order = 200,
        description = "hud.draggable.key_strokes.description")
public final class KeyStrokes extends DraggableHudElement {

    {
        defaultLayout(16f, 120f);
    }

    private static final long CPS_WINDOW_MS = 1000L;
    private static final String PULSE = "Pulse";
    private static final String RIPPLE = "Ripple";
    private static final String ORBIT = "Orbit";
    private static final String MINIMAL = "Minimal";

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    // Keep existing config keys for backwards compatibility with saved HUD presets.
    private final NumberValue<Double> scale = num("scale", 1.0, 0.5, 5.0);
    private final NumberValue<Double> keySize = num("key_size", 25.0, 15.0, 35.0);
    private final NumberValue<Double> keyGap = num("key_gap", 3.0, 2.0, 10.0);
    private final NumberValue<Double> radius = num("radius", 4.5, 1.0, 17.5);
    private final BooleanValue showMouse = bool("show_mouse", true);
    private final BooleanValue showCps = visibleWhen(bool("show_cps", true), showMouse::get);
    private final BooleanValue showSpace = bool("show_space", true);
    private final ModeValue effect = mode("press_effect", PULSE,
            new String[]{PULSE, RIPPLE, ORBIT, MINIMAL});
    private final NumberValue<Double> motionSpeed = visibleWhen(num("motion_speed", 1.0, 0.5, 2.0),
            () -> !MINIMAL.equals(effect.get()));
    private final NumberValue<Double> effectStrength = num("effect_strength", 0.75, 0.0, 1.0);
    private final BooleanValue syncTheme = bool("sync_theme", true);
    private final BooleanValue gradient = visibleWhen(bool("gradient", true), syncTheme::get);
    private final RGBAColorValue customBackground = visibleWhen(
            color("background", "#CA11151C"), () -> !syncTheme.get());
    private final RGBColorValue customAccent = visibleWhen(
            colorNoAlpha("accent", "#75D9FA"), () -> !syncTheme.get());
    private final RGBColorValue customText = visibleWhen(
            colorNoAlpha("text_color", "#FFFFFF"), () -> !syncTheme.get());
    private final NumberValue<Integer> bgAlpha = num("bg_alpha", 180, 0, 255);
    private final BooleanValue blur = bool("blur", false);
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("blur_alpha", 140, 0, 255), blur::get);

    // Persistent state; no per-frame key objects, animation instances or mesh allocations.
    private final KeyVisual forward = new KeyVisual();
    private final KeyVisual left = new KeyVisual();
    private final KeyVisual back = new KeyVisual();
    private final KeyVisual right = new KeyVisual();
    private final KeyVisual jump = new KeyVisual();
    private final KeyVisual attack = new KeyVisual();
    private final KeyVisual use = new KeyVisual();
    private final KeyVisual[] cells = {forward, left, back, right, jump, attack, use};
    private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private final RenderColor glyphColor = new RenderColor(0xFFFFFFFF);

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        x = 16f;
        y = 120f;
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
        if (mc.options == null) return;

        long now = System.currentTimeMillis();
        float dt = AnimationUtility.clamp(AnimationUtility.deltaTime(AnimationUtility.Mode.NANOS), 0f, 0.05f);
        float speed = motionSpeed.get().floatValue();
        boolean newLeft = attack.advance(mc.options.keyAttack, dt, speed);
        boolean newRight = use.advance(mc.options.keyUse, dt, speed);
        if (newLeft) leftClicks.addLast(now);
        if (newRight) rightClicks.addLast(now);
        trim(leftClicks, now);
        trim(rightClicks, now);
        forward.advance(mc.options.keyUp, dt, speed);
        left.advance(mc.options.keyLeft, dt, speed);
        back.advance(mc.options.keyDown, dt, speed);
        right.advance(mc.options.keyRight, dt, speed);
        jump.advance(mc.options.keyJump, dt, speed);

        float s = HudScale.scale(screenW, screenH)
                * TextSizing.scaleForSize(hud.getFontSize()) * scale.get().floatValue();
        float key = keySize.get().floatValue() * s;
        float gap = keyGap.get().floatValue() * s;
        float step = key + gap;
        float gridWidth = 3f * key + 2f * gap;
        float bottom = y + step + key;
        float yRow = bottom + gap;

        forward.place(x + step, y, key, key, true);
        left.place(x, y + step, key, key, true);
        back.place(x + step, y + step, key, key, true);
        right.place(x + 2f * step, y + step, key, key, true);

        if (showSpace.get()) {
            jump.place(x, yRow, gridWidth, key * 0.77f, true);
            bottom = yRow + key * 0.77f;
            yRow = bottom + gap;
        } else {
            jump.visible = false;
        }

        if (showMouse.get()) {
            float half = (gridWidth - gap) * 0.5f;
            float mouseHeight = key * (showCps.get() ? 1.18f : 0.90f);
            attack.place(x, yRow, half, mouseHeight, true);
            use.place(x + half + gap, yRow, half, mouseHeight, true);
            bottom = yRow + mouseHeight;
        } else {
            attack.visible = false;
            use.visible = false;
        }

        width = gridWidth;
        height = bottom - y;

        boolean themed = syncTheme.get();
        boolean useGradient = themed && gradient.get();
        Themes.Theme palette = theme();
        Themes.GradientSpec bgGradient = themed && useGradient ? Themes.hudBackgroundGradient() : null;
        HudRenderUtil.ThemeGradient accent = themed
                ? HudRenderUtil.themeSelectionGradient(255)
                : new HudRenderUtil.ThemeGradient(customAccent.getArgb(), customAccent.getArgb(), 90f);
        int alpha = themed ? bgAlpha.get() :
                Math.round(((customBackground.getArgb() >>> 24) & 0xFF) * (bgAlpha.get() / 255f));
        int start = HudRenderUtil.setAlpha(
                bgGradient != null ? bgGradient.start() : themed ? palette.windowBg() : customBackground.getArgb(), alpha);
        int end = HudRenderUtil.setAlpha(
                bgGradient != null ? bgGradient.end() : themed ? palette.windowBg() : customBackground.getArgb(), alpha);
        int textColor = themed ? palette.textPrimary() : 0xFF000000 | (customText.getArgb() & 0xFFFFFF);
        float strength = effectStrength.get().floatValue();
        float corner = radius.get().floatValue() * s;
        float angle = bgGradient != null ? bgGradient.angleDeg() : 90f;
        float phase = AnimationUtility.time(0.001f) * 160f;
        String style = effect.get();

        // Shapes first, glyphs second: no text/shape alternation or extra flush per key.
        for (KeyVisual cell : cells) {
            if (cell.visible) {
                drawCell(renderer, cell, start, end, accent, corner, angle,
                        useGradient, strength, s, phase, style);
            }
        }

        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer font = BuiltinFontCatalog.ONEST_BOLD.renderer(fallback);
        font.begin(s * 1.02f, false, false);
        for (KeyVisual cell : cells) {
            if (!cell.visible) continue;
            String label = cell == attack ? "LMB" : cell == use ? "RMB" :
                    cell == jump ? cell.binding.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT)
                    : shortLabel(cell.binding);
            float labelY = cell.drawY + (cell.drawH - (float) font.getHeight(false)) * 0.5f;
            if (showCps.get() && (cell == attack || cell == use)) {
                labelY -= 3.5f * s;
            }
            glyphColor.a = (textColor >>> 24) & 255;
            glyphColor.r = (textColor >>> 16) & 255;
            glyphColor.g = (textColor >>> 8) & 255;
            glyphColor.b = textColor & 255;
            font.render(label, cell.drawX + (cell.drawW - (float) font.getWidth(label, false)) * 0.5f,
                    labelY, glyphColor, false);
        }
        font.end();

        if (showMouse.get() && showCps.get()) {
            font.begin(s * 0.68f, false, false);
            glyphColor.a = (textColor >>> 24) & 255;
            glyphColor.r = (textColor >>> 16) & 255;
            glyphColor.g = (textColor >>> 8) & 255;
            glyphColor.b = textColor & 255;
            String leftCps = leftClicks.size() + " CPS";
            String rightCps = rightClicks.size() + " CPS";
            font.render(leftCps, attack.drawX + (attack.drawW - (float) font.getWidth(leftCps, false)) * 0.5f,
                    attack.drawY + attack.drawH * 0.5f + 1.1f * s, glyphColor, false);
            font.render(rightCps, use.drawX + (use.drawW - (float) font.getWidth(rightCps, false)) * 0.5f,
                    use.drawY + use.drawH * 0.5f + 1.1f * s, glyphColor, false);
            font.end();
        }
    }

    private void drawCell(Renderer2D renderer, KeyVisual cell,
                          int backgroundStart, int backgroundEnd,
                          HudRenderUtil.ThemeGradient accent, float radius, float angle,
                          boolean gradientEnabled, float strength, float s, float phase, String style) {
        float p = AnimationUtility.clamp01(cell.press);
        float kick = cell.pressAge < 0.34f
                ? (float) Math.sin(Math.PI * AnimationUtility.clamp01(cell.pressAge / 0.34f))
                    * (float) Math.exp(-cell.pressAge * 9f) : 0f;
        float indent = (p * 0.036f + kick * 0.018f * strength) * Math.min(cell.w, cell.h);
        cell.drawX = cell.x + indent;
        cell.drawY = cell.y + indent + p * 0.45f * s;
        cell.drawW = cell.w - 2f * indent;
        cell.drawH = cell.h - 2f * indent;
        float r = Math.min(radius, Math.min(cell.drawW, cell.drawH) * 0.5f);
        float active = AnimationUtility.clamp01((p * 0.52f + kick * 0.38f) * strength);
        int start = HudRenderUtil.mixRgb(backgroundStart, accent.start(), active);
        int end = HudRenderUtil.mixRgb(backgroundEnd, accent.end(), active);

        // Shadow is a single soft-shadow SDF quad, only for an active Pulse key.
        if (PULSE.equals(style) && p > 0.035f && strength > 0.01f) {
            int glowAlpha = Math.round(96f * p * strength);
            renderer.roundedRectSoftShadowGradient(cell.drawX, cell.drawY, cell.drawW, cell.drawH,
                    r, 3.2f * s, 0.045f,
                    HudRenderUtil.setAlpha(accent.start(), glowAlpha),
                    HudRenderUtil.setAlpha(accent.end(), glowAlpha), accent.angleDeg());
        }
        if (blur.get()) {
            Renderer2D.COLOR.blurRect(cell.x, cell.y, cell.w, cell.h,
                    Math.min(radius, Math.min(cell.w, cell.h) * 0.5f),
                    hud.getBlurRadius(), 1f, blurAlpha.get() / 255f, 0xFFFFFF);
        }
        if (gradientEnabled || (p > 0.005f && accent.start() != accent.end())) {
            renderer.roundedRectGradient(cell.drawX, cell.drawY, cell.drawW, cell.drawH,
                    r, 0.7f * s, start, end, angle);
        } else {
            renderer.roundedRect(cell.drawX, cell.drawY, cell.drawW, cell.drawH,
                    r, 0.7f * s, start);
        }

        if (MINIMAL.equals(style) || strength <= 0.005f) return;
        float outline = Math.max(p, kick * 0.75f);
        if (RIPPLE.equals(style)) {
            if (outline > 0.025f) {
                drawStroke(renderer, cell, r, 1.05f * s,
                        accent, Math.round(130f * outline * strength), accent.angleDeg());
            }
            // A single inward-travelling echo on key-down, no mask or full-screen postprocess.
            float progress = AnimationUtility.clamp01(cell.pressAge / 0.43f);
            if (progress < 1f && strength > 0.01f) {
                float inset = (1f - AnimationUtility.easeOutCubic(progress)) * Math.min(cell.w, cell.h) * 0.28f;
                float rw = cell.drawW - inset * 2f;
                float rh = cell.drawH - inset * 2f;
                if (rw > 2f * s && rh > 2f * s) {
                    int a = Math.round(150f * (1f - progress) * (1f - progress) * strength);
                    renderer.roundedRectStrokeGradient(cell.drawX + inset, cell.drawY + inset, rw, rh,
                            Math.max(0f, r - inset * 0.7f), 1.1f * s,
                            HudRenderUtil.setAlpha(accent.start(), a),
                            HudRenderUtil.setAlpha(accent.end(), a), accent.angleDeg());
                }
            }
        } else if (ORBIT.equals(style)) {
            if (outline > 0.025f) {
                // Rotating gradient around an SDF contour, not several overlapping rectangles.
                drawStroke(renderer, cell, r, 1.3f * s, accent,
                        Math.round(210f * outline * strength), phase);
            }
        } else if (outline > 0.025f) { // Pulse: elastic rim follows the key's press/rebound.
            float extra = kick * 1.5f * s * strength;
            int a = Math.round((125f * p + 110f * kick) * strength);
            renderer.roundedRectStrokeGradient(cell.drawX - extra, cell.drawY - extra,
                    cell.drawW + extra * 2f, cell.drawH + extra * 2f,
                    r + extra, (0.9f + 0.35f * kick) * s,
                    HudRenderUtil.setAlpha(accent.start(), a),
                    HudRenderUtil.setAlpha(accent.end(), a), accent.angleDeg());
        }
    }

    private static void drawStroke(Renderer2D renderer, KeyVisual cell, float radius, float thickness,
                                   HudRenderUtil.ThemeGradient accent, int alpha, float angle) {
        renderer.roundedRectStrokeGradient(cell.drawX, cell.drawY, cell.drawW, cell.drawH,
                radius, thickness,
                HudRenderUtil.setAlpha(accent.start(), alpha),
                HudRenderUtil.setAlpha(accent.end(), alpha), angle);
    }

    private static String shortLabel(KeyMapping binding) {
        String text = binding.getTranslatedKeyMessage().getString();
        if (text.length() > 3) text = text.substring(0, 3);
        return text.toUpperCase(Locale.ROOT);
    }

    private static void trim(ArrayDeque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > CPS_WINDOW_MS) clicks.pollFirst();
    }

    private static final class KeyVisual {
        private KeyMapping binding;
        private boolean visible;
        private boolean initialized;
        private boolean down;
        private float press;
        private float velocity;
        private float pressAge = 1f;
        private float x, y, w, h;
        private float drawX, drawY, drawW, drawH;

        /** Advances a spring without allocating a timer/animation object; returns a click edge. */
        private boolean advance(KeyMapping key, float dt, float speed) {
            binding = key;
            boolean pressedNow = key.isDown();
            if (!initialized) {
                initialized = true;
                press = pressedNow ? 1f : 0f;
                down = pressedNow;
                return false;
            }
            boolean clicked = pressedNow && !down;
            if (clicked) pressAge = 0f;
            down = pressedNow;
            pressAge = Math.min(1f, pressAge + dt * speed);

            // Damped spring: a tactile press with a small, bounded overshoot and smooth release.
            float target = pressedNow ? 1f : 0f;
            float spring = 330f * speed * speed;
            velocity += (target - press) * spring * dt;
            velocity *= (float) Math.exp(-19f * speed * dt);
            press = AnimationUtility.clamp(press + velocity * dt, -0.06f, 1.12f);
            if (Math.abs(press - target) < 0.001f && Math.abs(velocity) < 0.015f) {
                press = target;
                velocity = 0f;
            }
            return clicked;
        }

        private void place(float x, float y, float w, float h, boolean visible) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.visible = visible;
        }
    }
}
