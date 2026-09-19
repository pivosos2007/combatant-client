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
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.Locale;
import java.util.Map;

/** Paints graph/line connector nodes. Shape decoding is intentionally owned by {@link UiShapeRenderer}. */
final class UiConnectorRenderer {
    /** Reused point storage for dense HUD chart paths. */
    private final double[] points = new double[512];

    void render(UiNode node, UiBounds logicalBounds, UiRenderContext context) {
        if (node == null || logicalBounds == null || context == null || context.renderer() == null) return;

        UiBounds bounds = context.renderBounds(logicalBounds);
        UiProps props = node.props();
        UiStyle style = node.style();
        Renderer2D renderer = context.renderer();
        float alpha = context.alpha();
        if (alpha <= 0.001f) return;

        String type = props.string("connector", "line").toLowerCase(Locale.ROOT);
        float thickness = context.renderLength(props.number("strokeWidth", Math.max(1.0f, style.strokeWidth())));
        boolean gradient = hasLinearGradient(props) || hasStrokeLinearGradient(props);
        int rawStroke = UiRenderColors.raw(
                props.get("stroke"), style.strokeColor() != null ? style.strokeColor() : 0xFFFFFFFF
        );
        int stroke = UiRenderColors.applyAlpha(rawStroke, alpha);
        int start = UiRenderColors.applyAlpha(
                UiRenderColors.raw(props.get("startColor"), UiRenderColors.raw(props.get("strokeStartColor"), rawStroke)),
                alpha
        );
        int end = UiRenderColors.applyAlpha(
                UiRenderColors.raw(props.get("endColor"), UiRenderColors.raw(props.get("strokeEndColor"), rawStroke)),
                alpha
        );
        double x1 = bounds.x() + context.renderLength(props.number("x1", 0.0f));
        double y1 = bounds.y() + context.renderLength(props.number("y1", logicalBounds.height() * 0.5f));
        double x2 = bounds.x() + context.renderLength(props.number("x2", logicalBounds.width()));
        double y2 = bounds.y() + context.renderLength(props.number("y2", logicalBounds.height() * 0.5f));

        switch (type) {
            case "rounded-edge", "rounded_edge", "rounded-line", "rounded_line" -> renderRoundedConnector(
                    renderer, logicalBounds, bounds, props, context, thickness, stroke, gradient, start, end, RoundedConnectorMode.LINE
            );
            case "rounded-node-edge", "rounded_node_edge", "rounded-edge-bezier", "rounded_edge_bezier" ->
                    renderRoundedConnector(
                            renderer, logicalBounds, bounds, props, context, thickness, stroke, gradient, start, end,
                            RoundedConnectorMode.NODE_EDGE
                    );
            case "rounded-orthogonal", "rounded_orthogonal", "rounded-orthogonal-connector",
                 "rounded_orthogonal_connector" -> renderRoundedConnector(
                    renderer, logicalBounds, bounds, props, context, thickness, stroke, gradient, start, end,
                    RoundedConnectorMode.ORTHOGONAL
            );
            case "cable" -> {
                if (gradient) {
                    renderer.cableGradient(
                            x1, y1, x2, y2,
                            thickness,
                            props.get("outerStartColor") != null
                                    ? UiRenderColors.resolve(props.get("outerStartColor"), 0, alpha)
                                    : UiRenderColors.darken(start, 0.35f),
                            props.get("outerEndColor") != null
                                    ? UiRenderColors.resolve(props.get("outerEndColor"), 0, alpha)
                                    : UiRenderColors.darken(end, 0.35f),
                            props.get("innerStartColor") != null
                                    ? UiRenderColors.resolve(props.get("innerStartColor"), 0, alpha)
                                    : start,
                            props.get("innerEndColor") != null
                                    ? UiRenderColors.resolve(props.get("innerEndColor"), 0, alpha)
                                    : end
                    );
                } else {
                    renderer.cable(
                            x1, y1, x2, y2,
                            thickness,
                            props.get("outer") != null
                                    ? UiRenderColors.resolve(props.get("outer"), 0, alpha)
                                    : UiRenderColors.darken(stroke, 0.35f),
                            props.get("inner") != null
                                    ? UiRenderColors.resolve(props.get("inner"), 0, alpha)
                                    : stroke
                    );
                }
            }
            case "bezier", "bezier-connector", "bezier_connector" -> {
                double cx1 = bounds.x() + context.renderLength(props.number("cx1", props.number("x1", 0.0f) + logicalBounds.width() * 0.33f));
                double cy1 = bounds.y() + context.renderLength(props.number("cy1", props.number("y1", logicalBounds.height() * 0.5f)));
                double cx2 = bounds.x() + context.renderLength(props.number("cx2", props.number("x2", logicalBounds.width()) - logicalBounds.width() * 0.33f));
                double cy2 = bounds.y() + context.renderLength(props.number("cy2", props.number("y2", logicalBounds.height() * 0.5f)));
                if (gradient) {
                    renderer.bezierConnectorGradient(x1, y1, cx1, cy1, cx2, cy2, x2, y2, thickness, start, end);
                } else {
                    renderer.bezierConnector(x1, y1, cx1, cy1, cx2, cy2, x2, y2, thickness, stroke);
                }
            }
            case "orthogonal", "orthogonal-connector", "orthogonal_connector" -> {
                double midX = bounds.x() + context.renderLength(props.number("midX", logicalBounds.width() * 0.5f));
                if (gradient) {
                    renderer.orthogonalConnectorGradient(x1, y1, x2, y2, midX, thickness, start, end);
                } else {
                    renderer.orthogonalConnector(x1, y1, x2, y2, midX, thickness, stroke);
                }
            }
            case "node-edge", "node_edge", "edge" -> {
                if (gradient) {
                    renderer.nodeGraphEdgeGradient(x1, y1, x2, y2, thickness, start, end);
                } else {
                    renderer.nodeGraphEdge(x1, y1, x2, y2, thickness, stroke);
                }
            }
            case "spline" -> {
                int count = readPoints(props.get("points"), bounds.x(), bounds.y(), context.transform().scale());
                if (count >= 2) {
                    if (gradient) {
                        renderer.splineGradient(points, count, thickness, props.bool("closed", false), start, end);
                    } else {
                        renderer.spline(points, count, thickness, props.bool("closed", false), stroke);
                    }
                }
            }
            case "spline-area", "spline_area", "area-spline", "area_spline" -> {
                int count = readPoints(props.get("points"), bounds.x(), bounds.y(), context.transform().scale());
                if (count >= 2) {
                    double baseline = bounds.y() + context.renderLength(props.number("baseline", logicalBounds.height()));
                    int fillStart = props.get("fillStartColor") != null
                            ? UiRenderColors.resolve(props.get("fillStartColor"), 0, alpha)
                            : UiColor.multiplyAlpha(start, 0.30f);
                    int fillEnd = props.get("fillEndColor") != null
                            ? UiRenderColors.resolve(props.get("fillEndColor"), 0, alpha)
                            : UiColor.multiplyAlpha(end, 0.24f);
                    int bottomStart = props.get("fillBottomStartColor") != null
                            ? UiRenderColors.resolve(props.get("fillBottomStartColor"), 0, alpha)
                            : (fillStart & 0x00FFFFFF);
                    int bottomEnd = props.get("fillBottomEndColor") != null
                            ? UiRenderColors.resolve(props.get("fillBottomEndColor"), 0, alpha)
                            : (fillEnd & 0x00FFFFFF);
                    renderer.splineAreaGradient(points, count, baseline, fillStart, fillEnd, bottomStart, bottomEnd);
                }
            }
            default -> {
                if (gradient) renderer.connectorGradient(x1, y1, x2, y2, thickness, start, end);
                else renderer.connector(x1, y1, x2, y2, thickness, stroke);
            }
        }
    }

    private static void renderRoundedConnector(Renderer2D renderer,
                                               UiBounds logicalBounds,
                                               UiBounds bounds,
                                               UiProps props,
                                               UiRenderContext context,
                                               double thickness,
                                               int stroke,
                                               boolean gradient,
                                               int start,
                                               int end,
                                               RoundedConnectorMode mode) {
        double sx = bounds.x() + context.renderLength(props.number("sourceX", props.number("x1", 0.0f)));
        double sy = bounds.y() + context.renderLength(props.number("sourceY", props.number("y1", 0.0f)));
        double sw = context.renderLength(props.number("sourceWidth", props.number("sourceW", 1.0f)));
        double sh = context.renderLength(props.number("sourceHeight", props.number("sourceH", 1.0f)));
        double sr = context.renderLength(props.number("sourceRadius", 0.0f));
        double tx = bounds.x() + context.renderLength(props.number("targetX", props.number("x2", logicalBounds.width())));
        double ty = bounds.y() + context.renderLength(props.number("targetY", props.number("y2", 0.0f)));
        double tw = context.renderLength(props.number("targetWidth", props.number("targetW", 1.0f)));
        double th = context.renderLength(props.number("targetHeight", props.number("targetH", 1.0f)));
        double tr = context.renderLength(props.number("targetRadius", 0.0f));

        switch (mode) {
            case NODE_EDGE -> {
                if (gradient)
                    renderer.roundedRectNodeGraphEdgeGradient(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, start, end);
                else renderer.roundedRectNodeGraphEdge(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, stroke);
            }
            case ORTHOGONAL -> {
                double midX = bounds.x() + context.renderLength(props.number("midX", logicalBounds.width() * 0.5f));
                if (gradient) {
                    renderer.roundedRectOrthogonalConnectorGradient(
                            sx, sy, sw, sh, sr,
                            tx, ty, tw, th, tr,
                            midX,
                            thickness,
                            start,
                            end
                    );
                } else {
                    renderer.roundedRectOrthogonalConnector(
                            sx, sy, sw, sh, sr,
                            tx, ty, tw, th, tr,
                            midX,
                            thickness,
                            stroke
                    );
                }
            }
            default -> {
                if (gradient)
                    renderer.roundedRectConnectorGradient(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, start, end);
                else renderer.roundedRectConnector(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, stroke);
            }
        }
    }

    private int readPoints(Object value, double offsetX, double offsetY, float renderScale) {
        int count = 0;
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (count >= points.length / 2) break;
                if (item instanceof Map<?, ?> map) {
                    points[count * 2] = offsetX + number(map.get("x"), 0.0f) * renderScale;
                    points[count * 2 + 1] = offsetY + number(map.get("y"), 0.0f) * renderScale;
                    count++;
                } else if (item instanceof Number) {
                    break;
                }
            }
            if (count == 0) {
                double pendingX = Double.NaN;
                for (Object item : iterable) {
                    if (!(item instanceof Number n)) continue;
                    if (Double.isNaN(pendingX)) {
                        pendingX = n.doubleValue();
                    } else {
                        if (count >= points.length / 2) break;
                        points[count * 2] = offsetX + pendingX * renderScale;
                        points[count * 2 + 1] = offsetY + n.doubleValue() * renderScale;
                        count++;
                        pendingX = Double.NaN;
                    }
                }
            }
        }
        return count;
    }

    private static boolean hasLinearGradient(UiProps props) {
        return props.get("startColor") != null || props.get("endColor") != null;
    }

    private static boolean hasStrokeLinearGradient(UiProps props) {
        return props.get("strokeStartColor") != null || props.get("strokeEndColor") != null;
    }

    private static float number(Object value, float fallback) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private enum RoundedConnectorMode {
        LINE,
        NODE_EDGE,
        ORTHOGONAL
    }
}
