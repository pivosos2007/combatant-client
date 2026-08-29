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
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNodeType;
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

        /*
         * JS/UI runtime must not fall back to one auto-flushed Renderer2D batch per node.
         * Keep a single active Renderer2D batch across the whole runtime tree and flush only
         * at state boundaries such as scissor/clip changes. This is the first production
         * UI batch compiler layer: Renderer2D primitive methods still generate the actual
         * meshes, but they now compile into phase-sized batches instead of tiny immediate
         * submissions.
         */
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
                    boolean textFade = node.props().bool("textFade", false);
                    float fadeLeft = node.props().number("fadeLeft", 0.0f);
                    float fadeRight = node.props().number("fadeRight", textFade ? Math.min(16.0f, bounds.width() * 0.25f) : 0.0f);
                    int color = UiColor.parse(node.props().string("color", ""), style.textColor() != null ? style.textColor() : 0xFFFFFFFF);
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
            } finally {
                if (clipped) {
                    clipStack.pop();
                }
            }
        }
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
            );
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
