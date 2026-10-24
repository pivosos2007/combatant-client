/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudGlobalConfig;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
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

import static combatant.client.features.theme.Theme.theme;

/** WASD / jump / mouse-button overlay with an optional clicks-per-second readout. */
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

    private static final float KEY = 18f;
    private static final float GAP = 2f;
    private static final float RADIUS = 3f;
    private static final long CPS_WINDOW_MS = 1000L;

    private final Minecraft mc = Minecraft.getInstance();
    private final HudGlobalConfig hud = HudGlobalConfig.get();

    private final NumberValue<Double> scale = num("scale", 1.0, 0.5, 5.0);
    private final BooleanValue showMouse = bool("show_mouse", true);
    private final BooleanValue showCps = visibleWhen(bool("show_cps", true), showMouse::get);
    private final BooleanValue showSpace = bool("show_space", true);
    private final BooleanValue blur = bool("blur", false);
    private final NumberValue<Integer> blurAlpha = visibleWhen(num("blur_alpha", 140, 0, 255), blur::get);
    private final NumberValue<Integer> bgAlpha = num("bg_alpha", 150, 0, 255);

    private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private boolean leftWas;
    private boolean rightWas;

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        this.x = 16f;
        this.y = 120f;
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
        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();
        if (left && !leftWas) leftClicks.addLast(now);
        if (right && !rightWas) rightClicks.addLast(now);
        leftWas = left;
        rightWas = right;
        trim(leftClicks, now);
        trim(rightClicks, now);

        float s = HudScale.scale(screenW, screenH) * TextSizing.scaleForSize(hud.getFontSize()) * scale.get().floatValue();
        float key = KEY * s;
        float gap = GAP * s;
        float radius = RADIUS * s;
        float step = key + gap;

        // Row layout: W / A S D / mouse / space; the grid is three keys wide.
        cell(renderer, textRenderer, mc.options.keyUp, x + step, y, key, key, s, radius, null);
        cell(renderer, textRenderer, mc.options.keyLeft, x, y + step, key, key, s, radius, null);
        cell(renderer, textRenderer, mc.options.keyDown, x + step, y + step, key, key, s, radius, null);
        cell(renderer, textRenderer, mc.options.keyRight, x + 2 * step, y + step, key, key, s, radius, null);

        float bottom = y + 2 * step + key;
        float rowY = y + 2 * step;
        if (showMouse.get()) {
            float half = (3 * key + 2 * gap - gap) * 0.5f;
            float mouseH = key * (showCps.get() ? 1.35f : 1.0f);
            cell(renderer, textRenderer, mc.options.keyAttack, x, rowY, half, mouseH, s, radius,
                    showCps.get() ? leftClicks.size() + " CPS" : null, "LMB");
            cell(renderer, textRenderer, mc.options.keyUse, x + half + gap, rowY, half, mouseH, s, radius,
                    showCps.get() ? rightClicks.size() + " CPS" : null, "RMB");
            bottom = rowY + mouseH;
            rowY += mouseH + gap;
        }
        if (showSpace.get()) {
            float spaceH = key * 0.7f;
            cell(renderer, textRenderer, mc.options.keyJump, x, rowY, 3 * key + 2 * gap, spaceH, s, radius, null, "SPACE");
            bottom = rowY + spaceH;
        }

        width = 3 * key + 2 * gap;
        height = bottom - y;
    }

    private void cell(Renderer2D renderer, TextRenderer textRenderer, KeyMapping binding,
                      float cx, float cy, float w, float h, float s, float radius, String sub) {
        cell(renderer, textRenderer, binding, cx, cy, w, h, s, radius, sub, null);
    }

    private void cell(Renderer2D renderer, TextRenderer textRenderer, KeyMapping binding,
                      float cx, float cy, float w, float h, float s, float radius, String sub, String labelOverride) {
        boolean down = binding.isDown();
        int base = down ? theme().accent() : theme().windowBg();
        int bg = HudRenderUtil.setAlpha(base, bgAlpha.get());
        if (blur.get()) {
            Renderer2D.COLOR.blurRect(cx, cy, w, h, radius, hud.getBlurRadius(), 1.0f, blurAlpha.get() / 255f, 0xFFFFFF);
        }
        HudRenderUtil.drawHudBackground(renderer, cx, cy, w, h, radius, 1.0f, bg, false);

        String label = labelOverride != null ? labelOverride : shortLabel(binding);
        TextRenderer fallback = textRenderer != null ? textRenderer : TextRenderer.get();
        TextRenderer font = BuiltinFontCatalog.IOSEVKA_BOLD.renderer(fallback);
        RenderColor color = new RenderColor(theme().textPrimary());

        float labelScale = s * 0.8f;
        font.begin(labelScale, false, false);
        float lw = (float) font.getWidth(label, false);
        float lh = (float) font.getHeight(false);
        float ly = sub != null ? cy + h * 0.5f - lh : cy + (h - lh) * 0.5f;
        font.render(label, cx + (w - lw) * 0.5f, ly, color, false);
        font.end();

        if (sub != null) {
            float subScale = s * 0.6f;
            font.begin(subScale, false, false);
            float sw = (float) font.getWidth(sub, false);
            font.render(sub, cx + (w - sw) * 0.5f, cy + h * 0.5f + 1f * s, color, false);
            font.end();
        }
    }

    private static String shortLabel(KeyMapping binding) {
        String text = binding.getTranslatedKeyMessage().getString();
        if (text.length() > 3) text = text.substring(0, 3);
        return text.toUpperCase(java.util.Locale.ROOT);
    }

    private static void trim(ArrayDeque<Long> clicks, long now) {
        while (!clicks.isEmpty() && now - clicks.peekFirst() > CPS_WINDOW_MS) clicks.pollFirst();
    }
}
