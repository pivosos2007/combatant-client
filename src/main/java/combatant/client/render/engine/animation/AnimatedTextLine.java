/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.animation;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ScissorFunction;

import java.util.ArrayList;
import java.util.List;

public final class AnimatedTextLine {
    private final List<AnimatedGlyphSlot> slots = new ArrayList<>();
    private String text = "";

    public void setText(String nextText, long nowMs, AnimatedTextStyle style) {
        String next = nextText != null ? nextText : "";
        if (next.equals(text) && slots.size() == next.length()) return;

        while (slots.size() < next.length()) {
            slots.add(new AnimatedGlyphSlot(next.charAt(slots.size())));
        }
        while (slots.size() > next.length()) {
            slots.remove(slots.size() - 1);
        }

        for (int i = 0; i < next.length(); i++) {
            slots.get(i).set(next.charAt(i), nowMs, style);
        }
        text = next;
    }

    public String text() {
        return text;
    }

    public float width(TextRenderer renderer, float size, AnimatedTextStyle style) {
        if (renderer == null || text.isEmpty()) return 0.0f;
        Metrics metrics = measure(renderer, size, style);
        return metrics.totalWidth;
    }

    public float height(TextRenderer renderer, float size, AnimatedTextStyle style) {
        if (renderer == null) return 0.0f;
        renderer.begin(size, true, style != null && style.bigGlyphs());
        try {
            return (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
    }

    public void renderLiquidGlassCentered(TextRenderer renderer,
                                          float centerX,
                                          float y,
                                          float size,
                                          int argb,
                                          long nowMs,
                                          AnimatedTextStyle style) {
        float w = width(renderer, size, style);
        renderLiquidGlass(renderer, centerX - w * 0.5f, y, size, argb, nowMs, style);
    }

    public void renderLiquidGlass(TextRenderer renderer,
                                  float x,
                                  float y,
                                  float size,
                                  int argb,
                                  long nowMs,
                                  AnimatedTextStyle style) {
        if (renderer == null || text.isEmpty()) return;
        AnimatedTextStyle resolved = style != null ? style : AnimatedTextStyle.clockLiquidGlass();
        Metrics metrics = measure(renderer, size, resolved);
        if (metrics.totalWidth <= 0.0f || metrics.height <= 0.0f) return;

        float cursorX = x;
        for (int i = 0; i < slots.size(); i++) {
            AnimatedGlyphSlot slot = slots.get(i);
            char current = slot.current();
            float slotW = metrics.slotWidths[i];
            float currentW = metrics.glyphWidths[i];
            float previousW = metrics.previousGlyphWidths[i];
            float glyphX = cursorX + (slotW - currentW) * 0.5f;
            float previousGlyphX = cursorX + (slotW - previousW) * 0.5f;

            if (slot.isAnimating(nowMs, resolved)) {
                renderAnimatedSlot(renderer, slot, previousGlyphX, glyphX, cursorX, y, slotW, metrics.height, size, argb, nowMs, resolved);
            } else {
                renderGlyph(renderer, Character.toString(current), glyphX, y, size, argb, resolved);
            }
            cursorX += slotW;
        }
    }

    private void renderAnimatedSlot(TextRenderer renderer,
                                    AnimatedGlyphSlot slot,
                                    float previousGlyphX,
                                    float glyphX,
                                    float slotX,
                                    float y,
                                    float slotW,
                                    float height,
                                    float size,
                                    int argb,
                                    long nowMs,
                                    AnimatedTextStyle style) {
        float raw = slot.progress(nowMs, style);
        float move = AnimationUtility.easeInOutCubic(raw);
        float fadeIn = phase(raw, 0.08f, 0.86f);
        float fadeOut = 1.0f - phase(raw, 0.14f, 0.78f);
        float settle = AnimationUtility.easeOutBack(raw, 0.34f);
        float offset = Math.max(1.0f, height * style.rollOffsetScale());
        int oldColor = scaleAlpha(argb, fadeOut);
        int newColor = scaleAlpha(argb, fadeIn);

        float oldY = y;
        float newY = y;
        switch (style.transition()) {
            case FADE -> {
                oldY = y;
                newY = y;
            }
            case ROLL_DOWN -> {
                oldY = y + move * offset;
                newY = y - (1.0f - settle) * offset;
            }
            case ROLL_UP -> {
                oldY = y - move * offset;
                newY = y + (1.0f - settle) * offset;
            }
            case NONE -> {
                renderGlyph(renderer, Character.toString(slot.current()), glyphX, y, size, argb, style);
                return;
            }
        }

        float padY = height * style.clipPaddingScale();
        boolean clipped = ScissorFunction.pushRaw(
                slotX - 1.0f,
                y - padY,
                Math.max(1.0f, slotW + 2.0f),
                Math.max(1.0f, height + padY * 2.0f)
        );
        try {
            if (((oldColor >>> 24) & 0xFF) > 1) {
                renderGlyph(renderer, Character.toString(slot.previous()), previousGlyphX, oldY, size, oldColor, style);
            }
            if (((newColor >>> 24) & 0xFF) > 1) {
                renderGlyph(renderer, Character.toString(slot.current()), glyphX, newY, size, newColor, style);
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void renderGlyph(TextRenderer renderer,
                             String glyph,
                             float x,
                             float y,
                             float size,
                             int argb,
                             AnimatedTextStyle style) {
        if (glyph == null || glyph.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, style != null && style.bigGlyphs());
        try {
            renderer.renderLiquidGlass(glyph, x, y, new RenderColor(argb), false);
        } finally {
            renderer.end();
        }
    }

    private Metrics measure(TextRenderer renderer, float size, AnimatedTextStyle style) {
        int count = slots.size();
        float[] slotWidths = new float[count];
        float[] glyphWidths = new float[count];
        float[] previousGlyphWidths = new float[count];
        float total = 0.0f;
        float height;

        renderer.begin(size, true, style != null && style.bigGlyphs());
        try {
            float tabularDigitW = 0.0f;
            if (style != null && style.tabularDigits()) {
                for (char c = '0'; c <= '9'; c++) {
                    tabularDigitW = Math.max(tabularDigitW, (float) renderer.getWidth(Character.toString(c), false));
                }
            }
            for (int i = 0; i < count; i++) {
                AnimatedGlyphSlot slot = slots.get(i);
                char glyph = slot.current();
                char previous = slot.previous();
                float glyphW = (float) renderer.getWidth(Character.toString(glyph), false);
                float previousW = (float) renderer.getWidth(Character.toString(previous), false);
                float slotW = Math.max(glyphW, previousW);
                if (style != null && style.tabularDigits() && (Character.isDigit(glyph) || Character.isDigit(previous))) {
                    slotW = Math.max(slotW, tabularDigitW);
                }
                glyphWidths[i] = glyphW;
                previousGlyphWidths[i] = previousW;
                slotWidths[i] = slotW;
                total += slotW;
            }
            height = (float) renderer.getHeight(false);
        } finally {
            renderer.end();
        }
        return new Metrics(slotWidths, glyphWidths, previousGlyphWidths, total, height);
    }

    private static float phase(float value, float from, float to) {
        if (to <= from) return value >= to ? 1.0f : 0.0f;
        return AnimationUtility.smoothstep((value - from) / (to - from));
    }

    private static int scaleAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = Math.round(a * AnimationUtility.clamp01(factor));
        return (argb & 0x00FFFFFF) | ((scaled & 0xFF) << 24);
    }

    private record Metrics(float[] slotWidths, float[] glyphWidths, float[] previousGlyphWidths, float totalWidth, float height) {
    }
}
