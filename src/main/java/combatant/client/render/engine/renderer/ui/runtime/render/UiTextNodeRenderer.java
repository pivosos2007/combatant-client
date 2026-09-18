/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.text.TextEffectSpec;

/** Owns text-node paint behavior; tree traversal and clipping remain in {@link UiRenderer}. */
final class UiTextNodeRenderer {
    private final UiTextRenderer textRenderer;

    UiTextNodeRenderer(UiTextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    void render(UiNode node, UiBounds bounds, UiStyle style, UiRenderContext context) {
        String text = node.props().string("text", "");
        float textX = textX(node, context, text, bounds, style);
        float textY = bounds.y() + style.paddingTop()
                + textRenderer.lineOffsetY(context.textRenderer(), style)
                + node.props().number("textOffsetY", 0.0f);
        float textOffsetX = node.props().number("textOffsetX", 0.0f);
        boolean runtimeMarquee = node.props().bool("runtimeMarquee", false);
        float runtimeMarqueeOffset = runtimeMarquee
                ? marqueeOffset(node, context, text, bounds, style)
                : 0.0f;
        textOffsetX -= runtimeMarqueeOffset;
        boolean textFade = node.props().bool("textFade", false) || runtimeMarquee;
        float fadeWidth = node.props().number("marqueeFadeWidth", Math.min(16.0f, bounds.width() * 0.25f));
        float fadeLeft = node.props().number("fadeLeft", runtimeMarqueeOffset > 0.5f ? fadeWidth : 0.0f);
        float fadeRight = node.props().number("fadeRight", textFade ? fadeWidth : 0.0f);
        int rawColor = UiReactiveVisual.color(
                node, "color", style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
        int color = UiColor.multiplyAlpha(rawColor, context.alpha());
        String textBackend = node.props().string(
                "textBackend", node.props().string("backend", style.textBackend()));
        float glowWidth = node.props().number("textGlowWidth", 0.0f);
        float glowStrength = node.props().number("textGlowStrength", 0.0f);
        if (glowWidth > 0.0f && glowStrength > 0.0f) {
            int rawGlowColor = UiReactiveVisual.color(node, "textGlowColor", rawColor);
            textRenderer.renderGlow(
                    context.textRenderer(),
                    text,
                    textX + textOffsetX,
                    textY,
                    style,
                    UiColor.multiplyAlpha(rawGlowColor, context.alpha()),
                    glowWidth,
                    glowStrength,
                    textBackend
            );
        }
        if (textFade || fadeLeft > 0.0f || fadeRight > 0.0f || textOffsetX != 0.0f) {
            textRenderer.renderHorizontalFadeClipped(
                    context.textRenderer(),
                    text,
                    textX + textOffsetX,
                    textY,
                    style,
                    bounds.x(),
                    bounds.x() + bounds.width(),
                    fadeLeft,
                    fadeRight,
                    color
            );
            return;
        }

        boolean textGradient = node.props().bool("textGradient", false);
        if (textGradient) {
            int gradientStart = UiColor.multiplyAlpha(
                    UiColor.parse(node.props().string("gradientStartColor", ""), rawColor),
                    context.alpha());
            int gradientEnd = UiColor.multiplyAlpha(
                    UiColor.parse(node.props().string("gradientEndColor", ""), rawColor),
                    context.alpha());
            float gradientAngle = node.props().number("gradientAngle", 90.0f);
            textRenderer.renderLinearGradient(
                    context.textRenderer(),
                    text,
                    textX,
                    textY,
                    style,
                    gradientStart,
                    gradientEnd,
                    gradientAngle,
                    textBackend
            );
            return;
        }

        String effect = node.props().string("textEffect", style.textEffect());
        int effectSpeed = Math.max(1, (int) node.props().number("textEffectSpeed", style.textEffectSpeed()));
        float effectPhase = node.props().number("textEffectPhase", 0.0f);
        TextEffectSpec effectSpec = TextEffectSpec.of(effect, Math.max(0.1f, effectSpeed * 0.08f)).withPhase(effectPhase);
        textRenderer.render(
                context.textRenderer(),
                text,
                textX,
                textY,
                style,
                color,
                effectSpec,
                textBackend
        );
    }

    private float marqueeOffset(UiNode node, UiRenderContext context, String text, UiBounds bounds, UiStyle style) {
        float available = Math.max(0.0f, bounds.width() - style.paddingX());
        float measured = textRenderer.measureWidth(context.textRenderer(), text, style);
        float overflow = Math.max(0.0f, measured - available);
        var state = node.state();
        long now = System.nanoTime();
        long last = state.marqueeLastNanos();
        state.setMarqueeLastNanos(now);
        if (overflow <= 0.5f) {
            state.setMarqueeOffset(0.0f);
            state.setMarqueeForward(true);
            state.setMarqueeHoldUntilNanos(0L);
            return 0.0f;
        }

        float dtSeconds = last == 0L ? 0.0f : Math.min(0.1f, (now - last) / 1_000_000_000.0f);
        float speed = Math.max(0.0f, node.props().number("marqueeSpeed", 35.0f));
        long holdNanos = Math.max(0L, Math.round(node.props().number("marqueeHoldMs", 600.0f))) * 1_000_000L;
        UiNode source = UiReactiveVisual.stateSource(node);
        boolean hovered = source != null && source.state().hovered();
        float offset = Math.min(overflow, state.marqueeOffset());

        if (!hovered) {
            offset = Math.max(0.0f, offset - speed * dtSeconds);
            if (offset <= 0.001f) {
                offset = 0.0f;
                state.setMarqueeForward(true);
                state.setMarqueeHoldUntilNanos(0L);
            }
        } else if (now >= state.marqueeHoldUntilNanos()) {
            if (state.marqueeForward()) {
                offset += speed * dtSeconds;
                if (offset >= overflow) {
                    offset = overflow;
                    state.setMarqueeForward(false);
                    state.setMarqueeHoldUntilNanos(now + holdNanos);
                }
            } else {
                offset -= speed * dtSeconds;
                if (offset <= 0.0f) {
                    offset = 0.0f;
                    state.setMarqueeForward(true);
                    state.setMarqueeHoldUntilNanos(now + holdNanos);
                }
            }
        }
        state.setMarqueeOffset(offset);
        return offset;
    }

    private float textX(UiNode node, UiRenderContext context, String text, UiBounds bounds, UiStyle style) {
        float x = bounds.x() + style.paddingLeft();
        if ("left".equals(style.textAlign()) || text == null || text.isEmpty()) return x;
        float available = Math.max(0.0f, bounds.width() - style.paddingX());
        float textWidth = Math.min(available, textRenderer.measureWidth(context.textRenderer(), text, style));
        return switch (style.textAlign()) {
            case "center" -> x + Math.max(0.0f, available - textWidth) * 0.5f;
            case "right", "end" -> x + Math.max(0.0f, available - textWidth);
            default -> x;
        };
    }
}
