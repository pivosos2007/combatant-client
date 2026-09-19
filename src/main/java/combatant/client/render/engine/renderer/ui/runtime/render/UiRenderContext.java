/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.text.TextRenderer;

public record UiRenderContext(Renderer2D renderer,
                              TextRenderer textRenderer,
                              GuiGraphicsExtractor drawContext,
                              float tickDelta,
                              UiProjectionMode projectionMode,
                              float alpha,
                              UiRenderTransform transform,
                              UiVectorSpace vectorSpace) {
    public UiRenderContext {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        transform = transform != null ? transform : UiRenderTransform.IDENTITY;
    }

    public UiRenderContext(Renderer2D renderer,
                           TextRenderer textRenderer,
                           GuiGraphicsExtractor drawContext,
                           float tickDelta) {
        this(renderer, textRenderer, drawContext, tickDelta, UiProjectionMode.CURRENT, 1.0f, UiRenderTransform.IDENTITY, null);
    }

    public UiRenderContext(Renderer2D renderer,
                           TextRenderer textRenderer,
                           GuiGraphicsExtractor drawContext,
                           float tickDelta,
                           UiProjectionMode projectionMode) {
        this(renderer, textRenderer, drawContext, tickDelta, projectionMode, 1.0f, UiRenderTransform.IDENTITY, null);
    }

    public UiRenderContext(Renderer2D renderer,
                           TextRenderer textRenderer,
                           GuiGraphicsExtractor drawContext,
                           float tickDelta,
                           UiProjectionMode projectionMode,
                           float alpha) {
        this(renderer, textRenderer, drawContext, tickDelta, projectionMode, alpha, UiRenderTransform.IDENTITY, null);
    }

    public UiRenderContext(Renderer2D renderer,
                           TextRenderer textRenderer,
                           GuiGraphicsExtractor drawContext,
                           float tickDelta,
                           UiProjectionMode projectionMode,
                           float alpha,
                           UiRenderTransform transform) {
        this(renderer, textRenderer, drawContext, tickDelta, projectionMode, alpha, transform, null);
    }

    public UiRenderContext withTransform(UiRenderTransform nextTransform) {
        UiRenderTransform resolved = nextTransform != null ? nextTransform : UiRenderTransform.IDENTITY;
        if (resolved.equals(transform)) return this;
        return new UiRenderContext(renderer, textRenderer, drawContext, tickDelta, projectionMode, alpha, resolved, vectorSpace);
    }

    public UiRenderContext withVectorSpace(UiVectorSpace nextVectorSpace) {
        if (nextVectorSpace == vectorSpace) return this;
        return new UiRenderContext(renderer, textRenderer, drawContext, tickDelta, projectionMode, alpha, transform, nextVectorSpace);
    }

    public UiRenderContext at(float x, float y, float scale) {
        return withTransform(UiRenderTransform.scaleAt(x, y, scale));
    }

    public UiBounds renderBounds(UiBounds logicalBounds) {
        return transform.bounds(logicalBounds);
    }

    public float renderX(float logicalX) {
        return transform.x(logicalX);
    }

    public float renderY(float logicalY) {
        return transform.y(logicalY);
    }

    public float renderLength(float logicalLength) {
        return transform.length(logicalLength);
    }

    public double renderVectorX(double x) {
        return vectorSpace != null ? vectorSpace.renderX(x, transform) : transform.x(x);
    }

    public double renderVectorY(double y) {
        return vectorSpace != null ? vectorSpace.renderY(y, transform) : transform.y(y);
    }

    public UiRenderContext multiplyAlpha(float multiplier) {
        float resolved = Math.max(0.0f, Math.min(1.0f, multiplier));
        if (resolved >= 0.9999f) return this;
        return new UiRenderContext(renderer, textRenderer, drawContext, tickDelta, projectionMode, alpha * resolved, transform, vectorSpace);
    }
}
