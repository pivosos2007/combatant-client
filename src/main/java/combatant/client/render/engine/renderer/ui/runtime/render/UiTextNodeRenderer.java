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
        UiTextRenderer.TextLayout textLayout = textRenderer.layout(context.textRenderer(), text, style);
        if (textLayout.lines().isEmpty()) return;
        if (textLayout.multiline()) {
            renderMultiline(node, bounds, style, context, textLayout);
            return;
        }
        text = textLayout.lines().get(0);
        float renderScale = context.transform().scale();
        UiVectorSpace vector = context.vectorSpace();
        String coordinateSpace = node.props().string("coordinateSpace", "viewBox").trim().toLowerCase();
        if (coordinateSpace.equals("local") || coordinateSpace.equals("bounds")) vector = null;
        boolean vectorPosition = vector != null
                && node.props().get("vectorX") != null
                && node.props().get("vectorY") != null;
        float textX;
        float textY;
        if (vectorPosition) {
            textX = (float) vector.logicalX(node.props().number("vectorX", 0.0f));
            textY = (float) vector.logicalY(node.props().number("vectorY", 0.0f));
            float measuredWidth = textRenderer.measureWidth(context.textRenderer(), text, style);
            float measuredHeight = textRenderer.measureHeight(context.textRenderer(), style);
            String anchor = node.props().string("textAnchor", "start").trim().toLowerCase();
            if (anchor.equals("middle") || anchor.equals("center")) textX -= measuredWidth * 0.5f;
            else if (anchor.equals("end") || anchor.equals("right")) textX -= measuredWidth;
            String verticalAnchor = node.props().string("verticalAnchor", "top").trim().toLowerCase();
            if (verticalAnchor.equals("middle") || verticalAnchor.equals("center")) textY -= measuredHeight * 0.5f;
            else if (verticalAnchor.equals("bottom") || verticalAnchor.equals("end")) textY -= measuredHeight;
            textY += textRenderer.lineOffsetY(context.textRenderer(), style);
        } else {
            textX = textX(node, context, text, bounds, style);
            textY = bounds.y() + style.paddingTop()
                    + textRenderer.lineOffsetY(context.textRenderer(), style);
        }
        textY += node.props().number("textOffsetY", 0.0f);
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
                    context.renderX(textX + textOffsetX),
                    context.renderY(textY),
                    style,
                    UiColor.multiplyAlpha(rawGlowColor, context.alpha()),
                    glowWidth,
                    glowStrength,
                    textBackend,
                    renderScale
            );
        }
        if (textFade || fadeLeft > 0.0f || fadeRight > 0.0f || textOffsetX != 0.0f) {
            textRenderer.renderHorizontalFadeClipped(
                    context.textRenderer(),
                    text,
                    context.renderX(textX + textOffsetX),
                    context.renderY(textY),
                    style,
                    context.renderX(bounds.x()),
                    context.renderX(bounds.x() + bounds.width()),
                    context.renderLength(fadeLeft),
                    context.renderLength(fadeRight),
                    color,
                    renderScale
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
                    context.renderX(textX),
                    context.renderY(textY),
                    style,
                    gradientStart,
                    gradientEnd,
                    gradientAngle,
                    textBackend,
                    renderScale
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
                context.renderX(textX),
                context.renderY(textY),
                style,
                color,
                effectSpec,
                textBackend,
                renderScale
        );
    }

    private void renderMultiline(UiNode node,
                                 UiBounds bounds,
                                 UiStyle style,
                                 UiRenderContext context,
                                 UiTextRenderer.TextLayout layout) {
        float renderScale = context.transform().scale();
        UiVectorSpace vector = context.vectorSpace();
        String coordinateSpace = node.props().string("coordinateSpace", "viewBox").trim().toLowerCase();
        if (coordinateSpace.equals("local") || coordinateSpace.equals("bounds")) vector = null;
        boolean vectorPosition = vector != null
                && node.props().get("vectorX") != null
                && node.props().get("vectorY") != null;

        float blockX;
        float blockY;
        float availableWidth;
        if (vectorPosition) {
            blockX = (float) vector.logicalX(node.props().number("vectorX", 0.0f));
            blockY = (float) vector.logicalY(node.props().number("vectorY", 0.0f));
            String anchor = node.props().string("textAnchor", "start").trim().toLowerCase();
            if (anchor.equals("middle") || anchor.equals("center")) blockX -= layout.width() * 0.5f;
            else if (anchor.equals("end") || anchor.equals("right")) blockX -= layout.width();
            String verticalAnchor = node.props().string("verticalAnchor", "top").trim().toLowerCase();
            if (verticalAnchor.equals("middle") || verticalAnchor.equals("center")) blockY -= layout.height() * 0.5f;
            else if (verticalAnchor.equals("bottom") || verticalAnchor.equals("end")) blockY -= layout.height();
            availableWidth = layout.width();
        } else {
            blockX = bounds.x() + style.paddingLeft();
            blockY = bounds.y() + style.paddingTop();
            availableWidth = Math.max(0.0f, bounds.width() - style.paddingX());
        }
        blockY += textRenderer.lineOffsetY(context.textRenderer(), style)
                + node.props().number("textOffsetY", 0.0f);
        float textOffsetX = node.props().number("textOffsetX", 0.0f);

        int rawColor = UiReactiveVisual.color(
                node, "color", style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
        int color = UiColor.multiplyAlpha(rawColor, context.alpha());
        String textBackend = node.props().string(
                "textBackend", node.props().string("backend", style.textBackend()));
        float glowWidth = node.props().number("textGlowWidth", 0.0f);
        float glowStrength = node.props().number("textGlowStrength", 0.0f);
        boolean textGradient = node.props().bool("textGradient", false);
        int gradientStart = UiColor.multiplyAlpha(
                UiColor.parse(node.props().string("gradientStartColor", ""), rawColor), context.alpha());
        int gradientEnd = UiColor.multiplyAlpha(
                UiColor.parse(node.props().string("gradientEndColor", ""), rawColor), context.alpha());
        float gradientAngle = node.props().number("gradientAngle", 90.0f);
        String effect = node.props().string("textEffect", style.textEffect());
        int effectSpeed = Math.max(1, (int) node.props().number("textEffectSpeed", style.textEffectSpeed()));
        float effectPhase = node.props().number("textEffectPhase", 0.0f);
        TextEffectSpec effectSpec = TextEffectSpec.of(effect, Math.max(0.1f, effectSpeed * 0.08f)).withPhase(effectPhase);

        for (int i = 0; i < layout.lines().size(); i++) {
            String line = layout.lines().get(i);
            if (line.isEmpty()) continue;
            float lineWidth = textRenderer.measureWidth(context.textRenderer(), line, style);
            float lineX = alignedTextX(blockX, availableWidth, lineWidth, style.textAlign()) + textOffsetX;
            float lineY = blockY + layout.lineHeight() * i;

            if (glowWidth > 0.0f && glowStrength > 0.0f) {
                int rawGlowColor = UiReactiveVisual.color(node, "textGlowColor", rawColor);
                textRenderer.renderGlow(
                        context.textRenderer(), line, context.renderX(lineX), context.renderY(lineY), style,
                        UiColor.multiplyAlpha(rawGlowColor, context.alpha()), glowWidth, glowStrength, textBackend, renderScale
                );
            }
            if (textGradient) {
                textRenderer.renderLinearGradient(
                        context.textRenderer(), line, context.renderX(lineX), context.renderY(lineY), style,
                        gradientStart, gradientEnd, gradientAngle, textBackend, renderScale
                );
            } else {
                textRenderer.render(
                        context.textRenderer(), line, context.renderX(lineX), context.renderY(lineY), style,
                        color, effectSpec, textBackend, renderScale
                );
            }
        }
    }

    private static float alignedTextX(float x, float available, float textWidth, String align) {
        float free = Math.max(0.0f, available - Math.min(available, textWidth));
        return switch (align) {
            case "center" -> x + free * 0.5f;
            case "right", "end" -> x + free;
            default -> x;
        };
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
