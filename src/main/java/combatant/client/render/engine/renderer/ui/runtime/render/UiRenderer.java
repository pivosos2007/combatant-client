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
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

public final class UiRenderer {
    private final UiRuntimeScissorStack clipStack = new UiRuntimeScissorStack();
    private final UiAssetResolver assetResolver;
    private final UiImageRendererBridge imageRenderer = new UiImageRendererBridge();
    private final UiItemRendererBridge itemRenderer = new UiItemRendererBridge();
    private final UiShapeRenderer shapeRenderer = new UiShapeRenderer();
    private final UiConnectorRenderer connectorRenderer = new UiConnectorRenderer();
    private final UiPathNodeRenderer pathNodeRenderer = new UiPathNodeRenderer();
    private final UiBoxRenderer boxRenderer = new UiBoxRenderer();
    private final UiTextNodeRenderer textNodeRenderer;
    private final UiScrollbarRenderer scrollbarRenderer = new UiScrollbarRenderer();

    public UiRenderer(UiTextRenderer textRenderer) {
        this(textRenderer, null);
    }

    public UiRenderer(UiTextRenderer textRenderer, UiAssetRegistry assetRegistry) {
        this.textNodeRenderer = new UiTextNodeRenderer(textRenderer);
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
            UiRenderContext nodeContext = context.multiplyAlpha(style.opacity());
            if (nodeContext.alpha() <= 0.001f) return;
            UiBounds bounds = animatedBounds(node);
            boxRenderer.render(node, style, bounds, nodeContext);

            UiRenderContext childContext = nodeContext;
            if (node.type() == UiNodeType.VECTOR) {
                UiBounds viewport = new UiBounds(
                        bounds.x() + style.paddingLeft(),
                        bounds.y() + style.paddingTop(),
                        Math.max(0.0f, bounds.width() - style.paddingX()),
                        Math.max(0.0f, bounds.height() - style.paddingY())
                );
                childContext = nodeContext.withVectorSpace(UiVectorSpace.from(node.props(), viewport));
            }

            boolean clipped = style.clip() || style.marquee();
            if (clipped) {
                clipStack.push(bounds, nodeContext);
            }
            try {
                switch (node.type()) {
                    case TEXT -> textNodeRenderer.render(node, bounds, style, nodeContext);
                    case IMAGE, SVG -> {
                        UiAssetRef asset = assetResolver.resolve(node.props());
                        imageRenderer.render(node, asset, bounds, nodeContext);
                    }
                    case SHAPE -> shapeRenderer.render(node, bounds, nodeContext);
                    case CONNECTOR -> connectorRenderer.render(node, bounds, nodeContext);
                    case PATH -> pathNodeRenderer.render(node, bounds, nodeContext);
                    case ITEM -> itemRenderer.render(node, bounds, nodeContext);
                    default -> {
                    }
                }

                for (UiNode child : node.children()) {
                    renderNode(child, childContext);
                }
                if (node.type() == UiNodeType.SCROLL) {
                    scrollbarRenderer.render(node, nodeContext);
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
}
