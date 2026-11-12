/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.animation.UiAnimationState;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRef;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetRegistry;
import combatant.client.render.engine.renderer.ui.runtime.asset.UiAssetResolver;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
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
    private UiRenderProgram program;

    public UiRenderer(UiTextRenderer textRenderer) {
        this(textRenderer, null);
    }

    public UiRenderer(UiTextRenderer textRenderer, UiAssetRegistry assetRegistry) {
        this.textNodeRenderer = new UiTextNodeRenderer(textRenderer);
        this.assetResolver = new UiAssetResolver(assetRegistry);
    }

    /** Pre-bake the instruction tape after reconciliation (never during a frame). */
    public void compile(UiNode root) {
        program = UiRenderProgram.compile(root);
    }

    public void render(UiNode root, UiRenderContext context) {
        if (root == null || context == null || context.renderer() == null) return;
        if (program == null || program.root != root) compile(root);

        // Preserve the existing single-batch draw path. This changes only CPU traversal.
        boolean ownBatch = !Renderer2D.isBatching();
        try (RenderCostProfiler.Scope ignored = RenderCostProfiler.uiRuntime("ui-batch-tree")) {
            if (ownBatch) context.renderer().begin();
            try {
                execute(program, context);
            } finally {
                if (ownBatch) context.renderer().render();
            }
        }
    }

    private void execute(UiRenderProgram tape, UiRenderContext context) {
        int depth = 0;
        try {
            for (int pc = 0; pc < tape.length; pc++) {
                UiNode node = tape.nodes[pc];
                if (tape.operation[pc] == UiRenderProgram.LEAVE) {
                    --depth;
                    try {
                        if ((tape.flags[pc] & UiRenderProgram.SCROLL) != 0) {
                            scrollbarRenderer.render(node, tape.nodeContexts[depth]);
                        }
                    } finally {
                        if (tape.pushedClips[depth]) {
                            clipStack.pop();
                            tape.pushedClips[depth] = false;
                        }
                    }
                    continue;
                }

                if (!node.state().visible()) {
                    pc = tape.jump[pc] - 1;
                    continue;
                }
                UiRenderContext parentContext = depth == 0 ? context : tape.childContexts[depth - 1];
                UiStyle style = node.style();
                UiRenderContext nodeContext = parentContext.multiplyAlpha(
                        style.opacity() * node.props().number("runtimeOpacity", 1f));
                if (nodeContext.alpha() <= 0.001f) {
                    pc = tape.jump[pc] - 1;
                    continue;
                }
                UiBounds bounds = animatedBounds(node);
                boolean clipped = (tape.flags[pc] & UiRenderProgram.CLIP) != 0;
                if (clipped && (bounds.width() <= 0.0f || bounds.height() <= 0.0f)) {
                    pc = tape.jump[pc] - 1;
                    continue;
                }
                boxRenderer.render(node, style, bounds, nodeContext);

                UiRenderContext childContext = nodeContext;
                if ((tape.flags[pc] & UiRenderProgram.VECTOR) != 0) {
                    UiBounds viewport = new UiBounds(
                            bounds.x() + style.paddingLeft(),
                            bounds.y() + style.paddingTop(),
                            Math.max(0.0f, bounds.width() - style.paddingX()),
                            Math.max(0.0f, bounds.height() - style.paddingY())
                    );
                    childContext = nodeContext.withVectorSpace(UiVectorSpace.from(node.props(), viewport));
                }

                tape.nodeContexts[depth] = nodeContext;
                tape.childContexts[depth] = childContext;
                tape.pushedClips[depth] = clipped;
                if (clipped) {
                    clipStack.push(bounds, nodeContext);
                }
                depth++;

                switch (tape.drawCode[pc]) {
                    case UiRenderProgram.DRAW_TEXT -> textNodeRenderer.render(node, bounds, style, nodeContext);
                    case UiRenderProgram.DRAW_ASSET -> {
                        UiAssetRef asset = assetResolver.resolve(node.props());
                        imageRenderer.render(node, asset, bounds, nodeContext);
                    }
                    case UiRenderProgram.DRAW_SHAPE -> shapeRenderer.render(node, bounds, nodeContext);
                    case UiRenderProgram.DRAW_CONNECTOR -> connectorRenderer.render(node, bounds, nodeContext);
                    case UiRenderProgram.DRAW_PATH -> pathNodeRenderer.render(node, bounds, nodeContext);
                    case UiRenderProgram.DRAW_ITEM -> itemRenderer.render(node, bounds, nodeContext);
                    default -> { }
                }
            }
        } finally {
            // Unwind only active nested clips when drawing aborts unexpectedly.
            // Normal frames finish with depth == 0 and perform no cleanup walk.
            while (depth > 0) {
                --depth;
                if (tape.pushedClips[depth]) {
                    clipStack.pop();
                    tape.pushedClips[depth] = false;
                }
            }
        }
    }

    private UiBounds animatedBounds(UiNode node) {
        UiBounds bounds = node.bounds();
        var animations = node.state().animations();
        if (animations.isEmpty() && bounds.width() >= 0.0f && bounds.height() >= 0.0f) {
            return bounds;
        }
        UiAnimationState ax = animations.get("x");
        UiAnimationState ay = animations.get("y");
        UiAnimationState aw = animations.get("width");
        UiAnimationState ah = animations.get("height");
        float x = ax != null ? ax.value() : bounds.x();
        float y = ay != null ? ay.value() : bounds.y();
        float w = aw != null ? aw.value() : bounds.width();
        float h = ah != null ? ah.value() : bounds.height();
        return new UiBounds(x, y, Math.max(0.0f, w), Math.max(0.0f, h));
    }
}
