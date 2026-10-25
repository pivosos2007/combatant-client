/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.draw.UiRect;

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

        if (node.props().bool("materialRippleSurface", false)) {
            UiNode owner = UiReactiveVisual.stateSource(node);
            if (owner != null) {
                long start = owner.state().rippleStartNanos();
                if (start > 0L) {
                    long now = System.nanoTime();
                    float elapsedMs = Math.max(0f, (now - start) / 1_000_000f);
                    long release = owner.state().rippleReleaseNanos();
                    // Material Web: 450ms grow, at least 225ms before fade.
                    float growth = Math.min(1f, elapsedMs / 450f);
                    float eased = 1f - (float) Math.pow(1f - growth, 2.5);
                    float releaseAge = release > 0L
                            ? Math.max(0f, (now - Math.max(release, start + 225_000_000L)) / 1_000_000f)
                            : 0f;
                    float visible = release > 0L ? Math.max(0f, 1f - releaseAge / 180f) : 1f;
                    if (visible > 0.001f) {
                        float px = Math.max(0f, Math.min(bounds.width(),
                                context.renderLength(owner.state().rippleLocalX())));
                        float py = Math.max(0f, Math.min(bounds.height(),
                                context.renderLength(owner.state().rippleLocalY())));
                        float edgeX = Math.max(px, bounds.width() - px);
                        float edgeY = Math.max(py, bounds.height() - py);
                        float maxRadius = (float) Math.hypot(edgeX, edgeY) + context.renderLength(8f);
                        float rippleRadius = maxRadius * (.2f + .8f * eased);
                        int tint = UiReactiveVisual.color(node, "rippleColor", 0xFFFFFFFF);
                        context.renderer().rippleMasked(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                                context.renderLength(node.props().number("rippleCornerRadius", style.radius())),
                                Math.max(.8f, context.renderLength(.8f)), rippleRadius,
                                bounds.x() + px, bounds.y() + py,
                                UiColor.multiplyAlpha(tint, .13f * visible * lifecycleAlpha));
                    }
                }
            }
            return;
        }

        float radius = context.renderLength(node.props().number("renderRadius", style.radius()));
        float blurAlpha = node.props().number("renderBlurAlpha", style.blurAlpha()) * lifecycleAlpha;

        if (style.shadowColor() != null && style.shadowBlur() > 0.0f) {
            // Opt-in retained shadow tint/opacity for component elevation.
            // Default callers without shadowColorReactive render exactly as before.
            int shadow = UiReactiveVisual.color(node, "shadowColor", style.shadowColor());
            if ((shadow >>> 24) != 0) {
                context.renderer().roundedRectSoftShadow(
                        bounds.x(),
                        bounds.y(),
                        bounds.width(),
                        bounds.height(),
                        radius,
                        context.renderLength(style.shadowBlur()),
                        style.shadowInnerAlpha() * lifecycleAlpha,
                        UiColor.multiplyAlpha(shadow, lifecycleAlpha)
                );
            }
        }

        // Glass already prepares its own source/blur; do not issue a second blur pass.
        if (style.blur() && !style.liquidGlass()) {
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
            UiBackdropRuntime.drawLiquidGlass(context.renderer(), node,
                    UiRect.of(bounds.x(), bounds.y(), bounds.width(), bounds.height()),
                    node.props(), () ->
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
