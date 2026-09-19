/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

/** Paints the style-owned box layer of a runtime node. Tree traversal stays in {@link UiRenderer}. */
final class UiBoxRenderer {
    public void render(UiNode node, UiStyle style, UiBounds logicalBounds, UiRenderContext context) {
        UiBounds bounds = context.renderBounds(logicalBounds);
        if (bounds.width() <= 0.0f || bounds.height() <= 0.0f) return;

        float lifecycleAlpha = context.alpha();
        if (lifecycleAlpha <= 0.001f) return;

        float radius = context.renderLength(node.props().number("renderRadius", style.radius()));
        float blurAlpha = node.props().number("renderBlurAlpha", style.blurAlpha()) * lifecycleAlpha;

        if (style.shadowColor() != null && style.shadowBlur() > 0.0f) {
            context.renderer().roundedRectSoftShadow(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    radius,
                    context.renderLength(style.shadowBlur()),
                    style.shadowInnerAlpha() * lifecycleAlpha,
                    UiColor.multiplyAlpha(style.shadowColor(), lifecycleAlpha)
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
                            lifecycleAlpha,
                            blurAlpha,
                            Renderer2D.LiquidGlassPreset.BALANCED
                    ));
        }

        if (style.backgroundColor() != null) {
            int background = UiColor.multiplyAlpha(style.backgroundColor(), lifecycleAlpha);
            if (radius > 0.0f) {
                context.renderer().roundedRect(
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        radius,
                        background
                );
            } else {
                context.renderer().quad(
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        background
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
                    context.renderLength(style.strokeWidth()),
                    UiColor.multiplyAlpha(style.strokeColor(), lifecycleAlpha)
            );
        }
    }
}
