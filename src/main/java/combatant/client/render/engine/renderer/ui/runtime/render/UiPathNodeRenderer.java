/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiPathAreaFill;
import combatant.client.render.engine.renderer.ui.draw.UiPathCap;
import combatant.client.render.engine.renderer.ui.draw.UiPathCurve;
import combatant.client.render.engine.renderer.ui.draw.UiPathJoin;
import combatant.client.render.engine.renderer.ui.draw.UiPathStrokeLayer;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.Locale;

/** Paints generic vector path nodes and value-series plots through one typed path backend. */
final class UiPathNodeRenderer {
    private final UiPathGeometry geometry = new UiPathGeometry();

    void render(UiNode node, UiBounds bounds, UiRenderContext context) {
        if (node == null || bounds == null || context == null || context.renderer() == null) return;
        UiProps props = node.props();
        UiStyle style = node.style();
        float alpha = context.alpha();
        if (alpha <= 0.001f || bounds.width() <= 0.0f || bounds.height() <= 0.0f) return;

        int count = geometry.read(props, bounds, context.transform(), context.vectorSpace());
        if (count < 2) return;
        double[] points = geometry.points();

        UiPathCurve curve = pathCurve(props.string("curve", "linear"));
        boolean closed = props.bool("closed", false);

        boolean authoredStroke = props.get("stroke") != null
                || props.get("startColor") != null || props.get("endColor") != null
                || props.get("strokeStartColor") != null || props.get("strokeEndColor") != null;
        boolean styleStroke = style.strokeColor() != null;
        int rawStroke = UiRenderColors.raw(
                props.get("stroke"), styleStroke ? style.strokeColor() : 0xFFFFFFFF
        );
        int strokeStart = UiRenderColors.applyAlpha(
                UiRenderColors.raw(props.get("strokeStartColor"), UiRenderColors.raw(props.get("startColor"), rawStroke)),
                alpha
        );
        int strokeEnd = UiRenderColors.applyAlpha(
                UiRenderColors.raw(props.get("strokeEndColor"), UiRenderColors.raw(props.get("endColor"), rawStroke)),
                alpha
        );
        double defaultStrokeWidth = styleStroke ? Math.max(0.0f, style.strokeWidth()) : (authoredStroke ? 1.0 : 0.0);
        double strokeWidth = Math.max(0.0, context.renderLength(props.number("strokeWidth", (float) defaultStrokeWidth)));
        UiPathCap cap = pathCap(props.string("strokeLinecap", props.string("lineCap", "round")));
        UiPathJoin join = pathJoin(props.string("strokeLinejoin", props.string("lineJoin", "round")));
        UiPathStrokeLayer stroke = strokeWidth > 0.0
                ? new UiPathStrokeLayer(strokeWidth, strokeStart, strokeEnd, cap, join)
                : null;

        double glowWidth = Math.max(0.0, context.renderLength(props.number("glowWidth", 0.0f)));
        UiPathStrokeLayer glow = null;
        if (glowWidth > 0.0) {
            int defaultGlowStart = UiColor.multiplyAlpha(strokeStart, 0.18f);
            int defaultGlowEnd = UiColor.multiplyAlpha(strokeEnd, 0.14f);
            int glowStart = props.get("glowStartColor") != null
                    ? UiRenderColors.resolve(props.get("glowStartColor"), 0, alpha)
                    : props.get("glow") != null
                    ? UiRenderColors.resolve(props.get("glow"), defaultGlowStart, alpha)
                    : defaultGlowStart;
            int glowEnd = props.get("glowEndColor") != null
                    ? UiRenderColors.resolve(props.get("glowEndColor"), 0, alpha)
                    : props.get("glow") != null
                    ? UiRenderColors.resolve(props.get("glow"), defaultGlowEnd, alpha)
                    : defaultGlowEnd;
            glow = new UiPathStrokeLayer(glowWidth, glowStart, glowEnd, cap, join);
        }

        boolean areaEnabled = !closed && (props.bool("area", false) || props.get("fill") != null
                || props.get("fillStartColor") != null || props.get("fillEndColor") != null
                || props.get("fillBottomColor") != null
                || props.get("fillBottomStartColor") != null || props.get("fillBottomEndColor") != null);
        UiPathAreaFill area = null;
        if (areaEnabled) {
            double baseline = geometry.baseline(props, bounds, context.transform(), context.vectorSpace());
            Object fill = props.get("fill");
            int fillBase = fill != null ? UiRenderColors.resolve(fill, 0, alpha) : 0;
            int fillStart = props.get("fillStartColor") != null
                    ? UiRenderColors.resolve(props.get("fillStartColor"), 0, alpha)
                    : fill != null ? fillBase : UiColor.multiplyAlpha(strokeStart, 0.30f);
            int fillEnd = props.get("fillEndColor") != null
                    ? UiRenderColors.resolve(props.get("fillEndColor"), 0, alpha)
                    : fill != null ? fillBase : UiColor.multiplyAlpha(strokeEnd, 0.24f);
            Object bottomFill = props.get("fillBottomColor");
            int bottomBase = bottomFill != null ? UiRenderColors.resolve(bottomFill, 0, alpha) : 0;
            int bottomStart = props.get("fillBottomStartColor") != null
                    ? UiRenderColors.resolve(props.get("fillBottomStartColor"), 0, alpha)
                    : bottomFill != null ? bottomBase : (fillStart & 0x00FFFFFF);
            int bottomEnd = props.get("fillBottomEndColor") != null
                    ? UiRenderColors.resolve(props.get("fillBottomEndColor"), 0, alpha)
                    : bottomFill != null ? bottomBase : (fillEnd & 0x00FFFFFF);
            area = new UiPathAreaFill(baseline, fillStart, fillEnd, bottomStart, bottomEnd);
        }

        Renderer2D renderer = context.renderer();
        renderer.path(points, count, curve, closed, area, glow, stroke);
    }

    private static UiPathCurve pathCurve(String value) {
        String normalized = value == null ? "linear" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "linear", "line", "polyline" -> UiPathCurve.LINEAR;
            case "spline", "smooth", "catmull-rom", "catmull_rom" -> UiPathCurve.SPLINE;
            default -> invalidEnum("curve", value, UiPathCurve.LINEAR);
        };
    }

    private static UiPathCap pathCap(String value) {
        String normalized = value == null ? "round" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "butt", "flat" -> UiPathCap.BUTT;
            case "round" -> UiPathCap.ROUND;
            case "square" -> UiPathCap.SQUARE;
            default -> invalidEnum("strokeLinecap", value, UiPathCap.ROUND);
        };
    }

    private static UiPathJoin pathJoin(String value) {
        String normalized = value == null ? "round" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "miter", "mitre" -> UiPathJoin.MITER;
            case "round" -> UiPathJoin.ROUND;
            case "bevel" -> UiPathJoin.BEVEL;
            default -> invalidEnum("strokeLinejoin", value, UiPathJoin.ROUND);
        };
    }

    private static <T> T invalidEnum(String prop, String value, T fallback) {
        if (UiRuntimeValidation.enabled()) {
            throw UiRuntimeValidation.invalid("Unknown UI path " + prop + " value '" + value + "'.");
        }
        return fallback;
    }
}
