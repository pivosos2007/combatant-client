/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRegistry;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetResolver;
import combatant.client.render.engine.renderer.ui.runtime.animation.UiEasing;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
import combatant.client.render.engine.renderer.ui.runtime.input.UiScrollbarMetrics;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.text.TextEffectSpec;

public final class UiRenderer {
    private final UiTextRenderer textRenderer;
    private final UiClipStack clipStack = new UiClipStack();
    private final UiAssetResolver assetResolver;
    private final UiImageRendererBridge imageRenderer = new UiImageRendererBridge();
    private final UiItemRendererBridge itemRenderer = new UiItemRendererBridge();
    private final UiPrimitiveRenderer primitiveRenderer = new UiPrimitiveRenderer();

    public UiRenderer(UiTextRenderer textRenderer) {
        this(textRenderer, null);
    }

    public UiRenderer(UiTextRenderer textRenderer, UiAssetRegistry assetRegistry) {
        this.textRenderer = textRenderer;
        this.assetResolver = new UiAssetResolver(assetRegistry);
    }

    private static String nodeProfilerLabel(UiNode node) {
        if (node == null) return "unknown";
        String debug = node.props() != null ? node.props().string("debugName", "") : "";
        if (debug == null || debug.isBlank()) {
            debug = node.props() != null ? node.props().string("name", "") : "";
        }
        String key = node.key();
        String cls = node.styleClass();
        String id = debug != null && !debug.isBlank() ? debug : (!key.isBlank() ? key : (cls != null && !cls.isBlank() ? cls : "#" + node.runtimeId()));
        return node.type() + ":" + id;
    }

    public void render(UiNode root, UiRenderContext context) {
        if (root == null || context == null || context.renderer() == null) return;

        /* Keep one Renderer2D batch across the runtime tree; flush only at render-state boundaries. */
        boolean ownBatch = !Renderer2D.isBatching();
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("ui-batch-tree")) {
            if (ownBatch) context.renderer().begin();
            try {
                renderNode(root, context);
            } finally {
                if (ownBatch) context.renderer().render();
            }
        }
    }

    private void renderNode(UiNode node, UiRenderContext context) {
        try (RenderCostProfiler.Scope ignoredNode = RenderCostProfiler.uiNode(nodeProfilerLabel(node))) {
            UiStyle style = node.style();
            UiBounds bounds = animatedBounds(node);
            renderBox(node, style, bounds, context);

            boolean clipped = style.clip() || style.marquee();
            if (clipped) {
                clipStack.push(bounds, context);
            }
            try {
                if (node.type() == UiNodeType.TEXT) {
                    String text = node.props().string("text", "");
                    float textX = textX(node, context, text, bounds, style);
                    float textY = bounds.y() + style.paddingTop() + node.props().number("textOffsetY", 0.0f);
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
                    int color = UiReactiveVisual.color(node, "color", style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
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
                    } else {
                        String textBackend = node.props().string("textBackend", node.props().string("backend", style.textBackend()));
                        boolean textGradient = node.props().bool("textGradient", false);
                        if (textGradient) {
                            int gradientStart = UiColor.parse(node.props().string("gradientStartColor", ""), color);
                            int gradientEnd = UiColor.parse(node.props().string("gradientEndColor", ""), color);
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
                        } else {
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
                    }
                } else if (node.type() == UiNodeType.IMAGE || node.type() == UiNodeType.SVG) {
                    UiAssetRef asset = assetResolver.resolve(node.props());
                    imageRenderer.render(node, asset, context);
                } else if (node.type() == UiNodeType.SHAPE) {
                    primitiveRenderer.renderShape(node, context);
                } else if (node.type() == UiNodeType.CONNECTOR) {
                    primitiveRenderer.renderConnector(node, context);
                } else if (node.type() == UiNodeType.ITEM) {
                    itemRenderer.render(node, context);
                }

                for (UiNode child : node.children()) {
                    renderNode(child, context);
                }
                if (node.type() == UiNodeType.SCROLL) {
                    renderScrollbar(node, context);
                }
            } finally {
                if (clipped) {
                    clipStack.pop();
                }
            }
        }
    }

    private void renderScrollbar(UiNode node, UiRenderContext context) {
        UiScrollbarMetrics metrics = UiScrollbarMetrics.resolve(node);
        if (metrics == null) return;
        long now = System.nanoTime();
        long timeoutMs = Math.max(0L, Math.round(node.props().number("scrollbarVisibilityMs", 1100.0f)));
        boolean recent = node.state().lastScrollInteractionNanos() > 0L
                && now - node.state().lastScrollInteractionNanos() <= timeoutMs * 1_000_000L;
        float visibleTarget = (recent || node.state().scrollbarHovered() || node.state().scrollbarDragging()) ? 1.0f : 0.0f;
        float visible = node.state().motion("scrollbar:visible", visibleTarget, 220L, UiEasing.EASE_OUT_CUBIC, now);
        if (visible <= 0.001f) return;
        float hover = node.state().motion("scrollbar:hover", node.state().scrollbarHovered() ? 1.0f : 0.0f, 160L, UiEasing.EASE_OUT_CUBIC, now);
        float drag = node.state().motion("scrollbar:drag", node.state().scrollbarDragging() ? 1.0f : 0.0f, 160L, UiEasing.EASE_OUT_CUBIC, now);
        float alpha = node.props().number("scrollbarBaseAlpha", 0.28f)
                + node.props().number("scrollbarHoverAlpha", 0.24f) * hover
                + node.props().number("scrollbarDragAlpha", 0.28f) * drag;
        alpha = Math.max(0.0f, Math.min(1.0f, alpha * visible * context.alpha()));
        int base = UiReactiveVisual.rawColor(node.props().get("scrollbarColor"), 0xFFFFFFFF);
        int color = UiColor.withAlpha(base, alpha);
        float radius = Math.max(0.0f, node.props().number("scrollbarRadius", 1.0f));
        context.renderer().roundedRect(metrics.x(), metrics.thumbY(), metrics.width(), metrics.thumbHeight(), radius, color);
    }

    private UiBounds animatedBounds(UiNode node) {
        UiBounds bounds = node.bounds();
        var animations = node.state().animations();
        float x = animations.containsKey("x") ? animations.get("x").value() : bounds.x();
        float y = animations.containsKey("y") ? animations.get("y").value() : bounds.y();
        float w = animations.containsKey("width") ? animations.get("width").value() : bounds.width();
        float h = animations.containsKey("height") ? animations.get("height").value() : bounds.height();
        return new UiBounds(x, y, Math.max(0.0f, w), Math.max(0.0f, h));
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

    private void renderBox(UiNode node, UiStyle style, UiBounds bounds, UiRenderContext context) {
        if (bounds.width() <= 0.0f || bounds.height() <= 0.0f) return;

        float radius = node.props().number("renderRadius", style.radius());
        float blurAlpha = node.props().number("renderBlurAlpha", style.blurAlpha());

        if (style.shadowColor() != null && style.shadowBlur() > 0.0f) {
            context.renderer().roundedRectSoftShadow(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    radius,
                    style.shadowBlur(),
                    style.shadowInnerAlpha(),
                    style.shadowColor()
            );
        }

        if (style.blur()) {
            context.renderer().blurRect(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    radius,
                    style.blurQuality(),
                    style.blurBrightness(),
                    blurAlpha,
                    0xFFFFFF
            );
        }

        if (style.liquidGlass()) {
            UiBackdropRuntime.drawLiquidGlass(context.renderer(), node.props(), () ->
                    context.renderer().liquidGlassRect(
                            bounds.x(),
                            bounds.y(),
                            bounds.width(),
                            bounds.height(),
                            radius,
                            0xFFFFFFFF,
                            1.0f,
                            blurAlpha,
                            Renderer2D.LiquidGlassPreset.BALANCED
                    ));
        }

        if (style.backgroundColor() != null) {
            if (radius > 0.0f) {
                context.renderer().roundedRect(
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        radius,
                        style.backgroundColor()
                );
            } else {
                context.renderer().quad(
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        style.backgroundColor()
                );
            }
        }

        if (style.strokeColor() != null && style.strokeWidth() > 0.0f) {
            context.renderer().roundedRectStroke(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    radius,
                    style.strokeWidth(),
                    style.strokeColor()
            );
        }
    }
}
