/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiColor;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;
import combatant.client.render.engine.renderer.ui.runtime.style.UiThemeRegistry;

import java.util.Locale;
import java.util.Map;

public final class UiPrimitiveRenderer {
    private final double[] points = new double[64];
    private final int[] gradientCornersTmp = new int[4];
    private float renderAlpha = 1.0f;

    private static boolean isBoxShape(String shape, UiProps props) {
        // Only the new flexible-box contract should be routed through UiBoxShape.
        // Do not hijack legacy JS shapes such as "rounded-gradient", "chamfered",
        // "notched" or plain "rect": those already have tuned Renderer2D paths and
        // are heavily used by the JS HUD runtime. Routing them through the CPU
        // flexible-box fallback changed winding/shader behavior and made old
        // backgrounds disappear while blur still rendered.
        if (props.get("corners") != null || props.get("edges") != null
                || props.get("cornerTL") != null || props.get("cornerTopLeft") != null
                || props.get("cornerTR") != null || props.get("cornerTopRight") != null
                || props.get("cornerBR") != null || props.get("cornerBottomRight") != null
                || props.get("cornerBL") != null || props.get("cornerBottomLeft") != null
                || props.get("edgeTop") != null || props.get("topEdge") != null
                || props.get("edgeRight") != null || props.get("rightEdge") != null
                || props.get("edgeBottom") != null || props.get("bottomEdge") != null
                || props.get("edgeLeft") != null || props.get("leftEdge") != null) {
            return true;
        }
        return switch (shape) {
            case "box", "mixed", "flex", "flex-box", "flex_box", "squircle", "superellipse" -> true;
            default -> false;
        };
    }

    private static UiCornerSpec corner(UiProps props, String shortName, String longName,
                                       UiCornerSpec fallback, float radius, float cut) {
        Object corners = props.get("corners");
        Object explicit = cornerValue(corners, shortName, longName);
        if (explicit == null) explicit = first(props, "corner" + shortName, "corner" + longName);
        if (explicit != null) return cornerFromObject(explicit, fallback, radius, cut);

        Object mode = first(props, "cornerMode" + shortName, "cornerMode" + longName);
        if (mode != null) return cornerFromObject(mode, fallback, radius, cut);

        float r = props.number("radius" + shortName, props.number("radius" + longName, fallback.radiusX()));
        float c = props.number("cut" + shortName, props.number("cut" + longName,
                props.number("chamfer" + shortName, props.number("chamfer" + longName, fallback.cutX()))));
        if (r > 0.0f && (fallback.kind() == UiCornerKind.ROUNDED || fallback.kind() == UiCornerKind.SQUARE)) {
            return UiCornerSpec.rounded(r, r);
        }
        if (c > 0.0f && fallback.kind() != UiCornerKind.ROUNDED) {
            return UiCornerSpec.chamfered(c);
        }
        return fallback;
    }

    private static UiCornerSpec cornerFromObject(Object value, UiCornerSpec fallback, float radius, float cut) {
        if (value instanceof Map<?, ?> map) {
            Object rawKind = map.get("kind");
            if (rawKind == null) rawKind = map.get("type");
            String kind = String.valueOf(rawKind != null ? rawKind : "").toLowerCase(Locale.ROOT);
            float r = number(map.get("radius"), radius);
            float rx = number(map.get("radiusX"), r);
            float ry = number(map.get("radiusY"), r);
            float c = number(map.get("cut"), number(map.get("chamfer"), cut));
            float cx = number(map.get("cutX"), c);
            float cy = number(map.get("cutY"), c);
            return switch (kind) {
                case "round", "rounded", "radius" -> UiCornerSpec.rounded(rx, ry);
                case "chamfer", "chamfered", "cut", "bevel", "beveled" -> UiCornerSpec.chamfered(cx, cy);
                case "concave", "inverse", "inverse-round", "inverse_round" -> UiCornerSpec.concaveRounded(r);
                case "square", "none" -> UiCornerSpec.square();
                default -> fallback;
            };
        }
        if (value instanceof Number n) return UiCornerSpec.rounded(n.floatValue(), n.floatValue());
        if (value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("round")) return UiCornerSpec.rounded(radius, radius);
            if (normalized.startsWith("chamfer") || normalized.startsWith("cut") || normalized.startsWith("bevel"))
                return UiCornerSpec.chamfered(cut);
            if (normalized.startsWith("concave") || normalized.startsWith("inverse"))
                return UiCornerSpec.concaveRounded(radius);
            if (normalized.startsWith("square") || normalized.startsWith("none")) return UiCornerSpec.square();
        }
        return fallback;
    }

    private static Object cornerValue(Object corners, String shortName, String longName) {
        if (!(corners instanceof Map<?, ?> map)) return null;
        Object value = map.get(shortName);
        if (value == null) value = map.get(shortName.toLowerCase(Locale.ROOT));
        if (value == null) value = map.get(longName);
        if (value == null) value = map.get(Character.toLowerCase(longName.charAt(0)) + longName.substring(1));
        return value;
    }

    private static Object edgeValue(Object edges, String name) {
        if (!(edges instanceof Map<?, ?> map)) return null;
        Object value = map.get(name);
        if (value == null) value = map.get(name.toUpperCase(Locale.ROOT));
        return value;
    }

    private static UiEdgeSpec edgeFromObject(Object value, double length, boolean horizontal) {
        if (value == null) return UiEdgeSpec.straight();
        if (value instanceof Map<?, ?> map) {
            Object rawKind = map.get("kind");
            if (rawKind == null) rawKind = map.get("type");
            String kind = String.valueOf(rawKind != null ? rawKind : "").toLowerCase(Locale.ROOT);
            if (kind.equals("notch") || kind.equals("notched")) {
                float width = number(map.get("width"), number(map.get("size"), (float) Math.min(length * 0.18, 18.0)));
                float depth = number(map.get("depth"), (float) Math.min(length * 0.10, 8.0));
                Object offset = map.get("offset");
                if (offset == null || "center".equals(String.valueOf(offset)))
                    return UiEdgeSpec.notchedCenter(width, depth);
                return UiEdgeSpec.notched(number(offset, 0.0f), width, depth);
            }
            if (kind.equals("inset")) return UiEdgeSpec.inset(number(map.get("depth"), 0.0f));
            return UiEdgeSpec.straight();
        }
        if (value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("notch") || normalized.equals("notched"))
                return UiEdgeSpec.notchedCenter(Math.min(length * 0.18, 18.0), Math.min(length * 0.10, 8.0));
        }
        return UiEdgeSpec.straight();
    }

    private static void renderRoundedConnector(Renderer2D renderer,
                                               UiBounds bounds,
                                               UiProps props,
                                               double thickness,
                                               int stroke,
                                               boolean gradient,
                                               int start,
                                               int end,
                                               RoundedConnectorMode mode) {
        double sx = bounds.x() + props.number("sourceX", props.number("x1", 0.0f));
        double sy = bounds.y() + props.number("sourceY", props.number("y1", 0.0f));
        double sw = props.number("sourceWidth", props.number("sourceW", 1.0f));
        double sh = props.number("sourceHeight", props.number("sourceH", 1.0f));
        double sr = props.number("sourceRadius", 0.0f);
        double tx = bounds.x() + props.number("targetX", props.number("x2", bounds.width()));
        double ty = bounds.y() + props.number("targetY", props.number("y2", 0.0f));
        double tw = props.number("targetWidth", props.number("targetW", 1.0f));
        double th = props.number("targetHeight", props.number("targetH", 1.0f));
        double tr = props.number("targetRadius", 0.0f);

        switch (mode) {
            case NODE_EDGE -> {
                if (gradient)
                    renderer.roundedRectNodeGraphEdgeGradient(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, start, end);
                else renderer.roundedRectNodeGraphEdge(sx, sy, sw, sh, sr, tx, ty, tw, th, tr, thickness, stroke);
            }
            case ORTHOGONAL -> {
                double midX = bounds.x() + props.number("midX", bounds.width() * 0.5f);
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

    private static boolean hasFillCornerColors(UiProps props) {
        return first(props, "topLeftColor", "cTopLeft") != null
                || first(props, "topRightColor", "cTopRight") != null
                || first(props, "bottomRightColor", "cBottomRight") != null
                || first(props, "bottomLeftColor", "cBottomLeft") != null;
    }

    private static boolean hasStrokeCornerColors(UiProps props) {
        return props.get("strokeTopLeftColor") != null
                || props.get("strokeTopRightColor") != null
                || props.get("strokeBottomRightColor") != null
                || props.get("strokeBottomLeftColor") != null;
    }

    private static boolean hasLinearGradient(UiProps props) {
        return props.get("startColor") != null || props.get("endColor") != null;
    }

    private static boolean hasStrokeLinearGradient(UiProps props) {
        return props.get("strokeStartColor") != null || props.get("strokeEndColor") != null;
    }

    private static Object first(UiProps props, String first, String second) {
        Object value = props.get(first);
        return value != null ? value : props.get(second);
    }

    private static float chamferCorner(UiProps props, String shortName, String longName, float fallback) {
        Object value = props.get("cut" + shortName);
        if (value == null) value = props.get("chamfer" + shortName);
        if (value == null) value = props.get("cut" + longName);
        if (value == null) value = props.get("chamfer" + longName);
        return number(value, fallback);
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

    private static int lighten(int argb, float amount) {
        return adjust(argb, Math.abs(amount));
    }

    private static int darken(int argb, float amount) {
        return adjust(argb, -Math.abs(amount));
    }

    private static int adjust(int argb, float delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = channel(r, delta);
        g = channel(g, delta);
        b = channel(b, delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int value, float delta) {
        int out = delta >= 0.0f
                ? value + Math.round((255 - value) * delta)
                : value - Math.round(value * -delta);
        return Math.max(0, Math.min(255, out));
    }

    private static void computeLinearGradientCornerColors(float width,
                                                          float height,
                                                          int startArgb,
                                                          int endArgb,
                                                          float angleDeg,
                                                          float offsetPx,
                                                          int[] out) {
        float angle = (float) Math.toRadians(angleDeg);
        float dirX = (float) Math.cos(angle);
        float dirY = (float) Math.sin(angle);

        float p0 = 0.0f;
        float p1 = width * dirX;
        float p2 = height * dirY;
        float p3 = width * dirX + height * dirY;
        float minProj = Math.min(Math.min(p0, p1), Math.min(p2, p3));
        float maxProj = Math.max(Math.max(p0, p1), Math.max(p2, p3));
        float range = Math.max(0.0001f, maxProj - minProj);

        out[0] = mixArgb(startArgb, endArgb, clamp01((p0 + offsetPx - minProj) / range));
        out[1] = mixArgb(startArgb, endArgb, clamp01((p1 + offsetPx - minProj) / range));
        out[2] = mixArgb(startArgb, endArgb, clamp01((p3 + offsetPx - minProj) / range));
        out[3] = mixArgb(startArgb, endArgb, clamp01((p2 + offsetPx - minProj) / range));
    }

    private static int mixArgb(int a, int b, float t) {
        float u = 1.0f - t;
        int aa = Math.round(((a >>> 24) & 0xFF) * u + ((b >>> 24) & 0xFF) * t);
        int rr = Math.round(((a >>> 16) & 0xFF) * u + ((b >>> 16) & 0xFF) * t);
        int gg = Math.round(((a >>> 8) & 0xFF) * u + ((b >>> 8) & 0xFF) * t);
        int bb = Math.round((a & 0xFF) * u + (b & 0xFF) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    public void renderShape(UiNode node, UiRenderContext context) {
        if (node == null || context == null || context.renderer() == null) return;
        UiBounds bounds = node.bounds();
        if (bounds.width() <= 0.0f || bounds.height() <= 0.0f) return;
        float previousAlpha = renderAlpha;
        renderAlpha = context.alpha();
        try {
            renderShapeInternal(node, context, bounds);
        } finally {
            renderAlpha = previousAlpha;
        }
    }

    private void renderShapeInternal(UiNode node, UiRenderContext context, UiBounds bounds) {
        UiProps props = node.props();
        UiStyle style = node.style();
        Renderer2D renderer = context.renderer();
        String shape = props.string("shape", "chamfered").toLowerCase(Locale.ROOT);
        int fill = color(props.get("fill"), style.backgroundColor() != null ? style.backgroundColor() : 0x00000000);
        int stroke = color(props.get("stroke"), style.strokeColor() != null ? style.strokeColor() : 0x00000000);
        float strokeWidth = props.number("strokeWidth", style.strokeWidth());
        double x = bounds.x();
        double y = bounds.y();
        double w = Math.max(0.0, props.number("renderWidth", bounds.width()));
        double h = Math.max(0.0, props.number("renderHeight", bounds.height()));
        double cut = props.number("cut", props.number("chamfer", style.radius()));
        double cutTL = chamferCorner(props, "TL", "TopLeft", (float) cut);
        double cutTR = chamferCorner(props, "TR", "TopRight", (float) cut);
        double cutBR = chamferCorner(props, "BR", "BottomRight", (float) cut);
        double cutBL = chamferCorner(props, "BL", "BottomLeft", (float) cut);
        boolean linearGradient = hasLinearGradient(props);
        int gradientStart = color(props.get("startColor"), fill);
        int gradientEnd = color(props.get("endColor"), fill);
        float gradientAngle = props.number("angle", 90.0f);
        float gradientOffset = props.number("offset", 0.0f);

        renderShapeBlur(renderer, props, style, shape, x, y, w, h, cut);

        if (isBoxShape(shape, props)) {
            UiBoxShape boxShape = buildBoxShape(props, style, shape, x, y, w, h);
            UiPaint fillPaint = buildPaint(props, fill, linearGradient, gradientStart, gradientEnd, gradientAngle, gradientOffset);
            if ((fillPaint.solidColor() >>> 24) > 0 || linearGradient || hasFillCornerColors(props)) {
                renderer.box(boxShape, fillPaint);
            }
            if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                UiPaint strokePaint = buildStrokePaint(props, stroke, gradientAngle, gradientOffset);
                renderer.boxStroke(boxShape, strokePaint, UiStroke.of(strokeWidth));
            }
            return;
        }

        switch (shape) {
            case "rounded-soft-shadow", "rounded_soft_shadow", "soft-shadow", "soft_shadow" -> {
                float radius = props.number("radius", style.radius());
                float blur = props.number("blur", props.number("shadowBlur", 8.0f));
                float innerAlpha = props.number("innerAlpha", props.number("shadowInnerAlpha", 0.18f));
                int color = color(props.get("color"), fill);
                if ((color >>> 24) > 0 && blur > 0.0f) {
                    renderer.roundedRectSoftShadow(x, y, w, h, radius, blur, innerAlpha, color);
                }
                return;
            }
            case "rounded-shadow", "rounded_shadow", "shadow" -> {
                float radius = props.number("radius", style.radius());
                float softness = props.number("softness", 8.0f);
                float spread = props.number("spread", 12.0f);
                int color = color(props.get("color"), fill);
                if ((color >>> 24) > 0) {
                    renderer.roundedRectShadow(x, y, w, h, radius, softness, spread, color);
                }
                return;
            }
            case "rounded-glow", "rounded_glow", "glow" -> {
                float radius = props.number("radius", style.radius());
                float softness = props.number("softness", 0.0f);
                float glow = props.number("glow", props.number("spread", 8.0f));
                int color = color(props.get("color"), fill);
                if ((color >>> 24) > 0 && glow > 0.0f) {
                    renderer.roundedRectGlow(x, y, w, h, radius, softness, glow, color);
                }
                return;
            }
            case "radial-glow-masked", "radial_glow_masked", "radial-glow", "radial_glow" -> {
                float radius = props.number("radius", style.radius());
                float softness = props.number("softness", 0.0f);
                float glowRadius = props.number("glowRadius", props.number("glow", (float) Math.max(w, h) * 0.5f));
                float cx = props.number("cx", (float) (w * 0.5));
                float cy = props.number("cy", (float) (h * 0.5));
                int color = color(props.get("color"), fill);
                if ((color >>> 24) > 0 && glowRadius > 0.0f) {
                    renderer.radialGlowMasked(x, y, w, h, radius, softness, glowRadius, (float) x + cx, (float) y + cy, color);
                }
                return;
            }
            case "rounded-gradient-quad", "rounded_gradient_quad", "rounded-quad-gradient", "rounded_quad_gradient" -> {
                float radius = props.number("radius", style.radius());
                float softness = props.number("softness", 0.0f);
                renderer.roundedRectGradientQuad(x, y, w, h, radius, softness,
                        color(first(props, "topLeftColor", "cTopLeft"), gradientStart),
                        color(first(props, "topRightColor", "cTopRight"), gradientEnd),
                        color(first(props, "bottomRightColor", "cBottomRight"), gradientEnd),
                        color(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart));
                return;
            }
            case "rounded-stroke-gradient", "rounded_stroke_gradient" -> {
                float radius = props.number("radius", style.radius());
                float softness = props.number("softness", 0.0f);
                float thickness = props.number("thickness", Math.max(1.0f, strokeWidth));
                int start = color(props.get("startColor"), color(props.get("strokeStartColor"), stroke));
                int end = color(props.get("endColor"), color(props.get("strokeEndColor"), stroke));
                if (((start | end) >>> 24) > 0 && thickness > 0.0f) {
                    renderer.roundedRectStrokeGradient(x, y, w, h, radius, softness, thickness,
                            start, end, props.number("angle", props.number("strokeAngle", 90.0f)),
                            props.number("offset", props.number("strokeOffset", 0.0f)));
                }
                return;
            }
            case "circle-soft-shadow", "circle_soft_shadow" -> {
                double radius = props.number("radius", (float) (Math.min(w, h) * 0.5));
                double cx = x + props.number("cx", (float) (w * 0.5));
                double cy = y + props.number("cy", (float) (h * 0.5));
                float blur = props.number("blur", props.number("shadowBlur", 7.0f));
                float innerAlpha = props.number("innerAlpha", props.number("shadowInnerAlpha", 0.34f));
                int color = color(props.get("color"), fill);
                if ((color >>> 24) > 0 && radius > 0.0 && blur > 0.0f) {
                    renderer.circleSoftShadow(cx, cy, radius, blur, innerAlpha, color);
                }
                return;
            }
            case "rect", "quad" -> {
                if ((fill >>> 24) > 0) {
                    renderer.quad(x, y, w, h, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.roundedRectStroke(x, y, w, h, 0.0f, 0.0f, strokeWidth, stroke);
                }
                return;
            }
            case "gradient", "rect-gradient", "rect_gradient", "quad-gradient", "quad_gradient" -> {
                int start = color(props.get("startColor"), fill);
                int end = color(props.get("endColor"), fill);
                if (hasFillCornerColors(props)) {
                    renderer.quadGradient(x, y, w, h,
                            color(first(props, "topLeftColor", "cTopLeft"), start),
                            color(first(props, "topRightColor", "cTopRight"), end),
                            color(first(props, "bottomRightColor", "cBottomRight"), end),
                            color(first(props, "bottomLeftColor", "cBottomLeft"), start));
                } else if (((start | end) >>> 24) > 0) {
                    renderer.quadGradientLinear(x, y, w, h, start, end,
                            props.number("angle", 0.0f),
                            props.number("offset", 0.0f));
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    if (hasStrokeCornerColors(props)) {
                        renderer.roundedRectStrokeGradientQuad(x, y, w, h, 0.0f, 0.0f, strokeWidth,
                                color(props.get("strokeTopLeftColor"), stroke),
                                color(props.get("strokeTopRightColor"), stroke),
                                color(props.get("strokeBottomRightColor"), stroke),
                                color(props.get("strokeBottomLeftColor"), stroke));
                    } else {
                        renderer.quadStrokeGradientLinear(x, y, w, h, strokeWidth,
                                color(props.get("strokeStartColor"), stroke),
                                color(props.get("strokeEndColor"), stroke),
                                props.number("strokeAngle", props.number("angle", 0.0f)),
                                props.number("strokeOffset", props.number("offset", 0.0f)));
                    }
                }
                return;
            }
            case "rounded", "rounded-rect", "rounded_rect" -> {
                float radius = props.number("radius", style.radius());
                if ((fill >>> 24) > 0) {
                    renderer.roundedRect(x, y, w, h, radius, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.roundedRectStroke(x, y, w, h, radius, strokeWidth, stroke);
                }
                return;
            }
            case "rounded-gradient", "rounded_gradient", "rounded-rect-gradient", "rounded_rect_gradient" -> {
                float radius = props.number("radius", style.radius());
                int start = color(props.get("startColor"), fill);
                int end = color(props.get("endColor"), fill);
                if (hasFillCornerColors(props)) {
                    renderer.roundedRectGradientQuad(x, y, w, h, radius,
                            color(first(props, "topLeftColor", "cTopLeft"), start),
                            color(first(props, "topRightColor", "cTopRight"), end),
                            color(first(props, "bottomRightColor", "cBottomRight"), end),
                            color(first(props, "bottomLeftColor", "cBottomLeft"), start));
                } else if (((start | end) >>> 24) > 0) {
                    renderer.roundedRectGradient(x, y, w, h, radius, start, end,
                            props.number("angle", 90.0f),
                            props.number("offset", 0.0f));
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    if (hasStrokeCornerColors(props)) {
                        renderer.roundedRectStrokeGradientQuad(x, y, w, h, radius, strokeWidth,
                                color(props.get("strokeTopLeftColor"), stroke),
                                color(props.get("strokeTopRightColor"), stroke),
                                color(props.get("strokeBottomRightColor"), stroke),
                                color(props.get("strokeBottomLeftColor"), stroke));
                    } else {
                        renderer.roundedRectStrokeGradient(x, y, w, h, radius, strokeWidth,
                                color(props.get("strokeStartColor"), stroke),
                                color(props.get("strokeEndColor"), stroke),
                                props.number("strokeAngle", props.number("angle", 90.0f)),
                                props.number("strokeOffset", props.number("offset", 0.0f)));
                    }
                }
                return;
            }
            case "rounded-progress-gradient", "rounded_progress_gradient", "progress-rounded-gradient",
                "progress_rounded_gradient" -> {
                float radius = props.number("radius", style.radius());
                float progress = props.number("progress", 1.0f);
                boolean fromRight = props.bool("fromRight", false) || "right".equals(props.string("direction", ""));
                int start = color(props.get("startColor"), fill);
                int end = color(props.get("endColor"), start);
                if (((start | end) >>> 24) > 0) {
                    renderer.roundedProgressRectGradient(x, y, w, h, radius, progress, fromRight,
                            start,
                            end,
                            props.number("angle", 0.0f),
                            props.number("offset", 0.0f));
                }
                return;
            }
            case "rounded-smoke-fill", "rounded_smoke_fill", "smoke-fill", "smoke_fill" -> {
                float radius = props.number("radius", style.radius());
                float fillRatio = props.number("fillRatio", props.number("progress", 1.0f));
                boolean fromRight = props.bool("fromRight", false) || "right".equals(props.string("direction", ""));
                int first = color(first(props, "firstColor", "startColor"), fill);
                int second = color(first(props, "secondColor", "endColor"), first);
                int third = color(props.get("thirdColor"), second);
                if (((first | second | third) >>> 24) > 0) {
                    renderer.roundedSmokeFill(
                            x, y, w, h,
                            radius,
                            fillRatio,
                            fromRight,
                            first,
                            second,
                            third,
                            props.number("time", 0.0f),
                            props.number("smokeScale", 3.0f),
                            props.number("smokeMix", 0.72f),
                            Math.round(props.number("octaves", 4.0f)),
                            props.number("flowX", 0.08f),
                            props.number("flowY", -0.05f),
                            props.number("intensity", 1.45f)
                    );
                }
                return;
            }
            case "rounded-corners", "rounded_corners", "rounded-rect-corners", "rounded_rect_corners" -> {
                float radius = props.number("radius", style.radius());
                float radiusTL = props.number("radiusTL", radius);
                float radiusTR = props.number("radiusTR", radius);
                float radiusBR = props.number("radiusBR", radius);
                float radiusBL = props.number("radiusBL", radius);
                if (hasFillCornerColors(props)) {
                    renderer.roundedRectCornersQuad(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL,
                            color(first(props, "topLeftColor", "cTopLeft"), gradientStart),
                            color(first(props, "topRightColor", "cTopRight"), gradientEnd),
                            color(first(props, "bottomRightColor", "cBottomRight"), gradientEnd),
                            color(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart));
                } else if (linearGradient) {
                    computeLinearGradientCornerColors((float) w, (float) h, gradientStart, gradientEnd,
                            gradientAngle, gradientOffset, gradientCornersTmp);
                    renderer.roundedRectCornersQuad(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL,
                            gradientCornersTmp[0],
                            gradientCornersTmp[1],
                            gradientCornersTmp[2],
                            gradientCornersTmp[3]);
                } else if ((fill >>> 24) > 0) {
                    renderer.roundedRectCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.roundedRectStrokeCorners(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL,
                            strokeWidth, stroke);
                }
                return;
            }
            case "circle" -> {
                double radius = props.number("radius", (float) (Math.min(w, h) * 0.5));
                double cx = x + props.number("cx", (float) (w * 0.5));
                double cy = y + props.number("cy", (float) (h * 0.5));
                if ((fill >>> 24) > 0) {
                    renderer.circle(cx, cy, radius, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.circleStroke(cx, cy, radius, strokeWidth, stroke);
                }
                return;
            }
            case "circle-stroke", "circle_stroke", "ring" -> {
                double radius = props.number("radius", (float) (Math.min(w, h) * 0.5));
                double cx = x + props.number("cx", (float) (w * 0.5));
                double cy = y + props.number("cy", (float) (h * 0.5));
                float thickness = props.number("thickness", Math.max(1.0f, strokeWidth));
                int color = (stroke >>> 24) > 0 ? stroke : fill;
                if ((color >>> 24) > 0 && thickness > 0.0f) {
                    renderer.circleStroke(cx, cy, radius, thickness, color);
                }
                return;
            }
            case "arc", "arc-stroke", "arc_stroke", "arc-flat", "arc_flat", "arc-gradient", "arc_gradient" -> {
                float thickness = props.number("thickness", Math.max(1.0f, strokeWidth));
                double radius = props.number("radius", (float) Math.max(0.0, Math.min(w, h) * 0.5 - thickness * 0.5));
                double cx = x + props.number("cx", (float) (w * 0.5));
                double cy = y + props.number("cy", (float) (h * 0.5));
                float startAngle = props.number("startAngle", 0.0f);
                float endAngle = props.number("endAngle", 360.0f);
                int color = (stroke >>> 24) > 0 ? stroke : fill;
                if (thickness <= 0.0f) return;
                if (shape.equals("arc-gradient") || shape.equals("arc_gradient")) {
                    int start = color(props.get("startColor"), color);
                    int end = color(props.get("endColor"), color);
                    if (((start | end) >>> 24) > 0) {
                        renderer.arcStrokeGradient(cx, cy, radius, thickness, startAngle, endAngle,
                                props.number("softness", 0.0f),
                                start, end, props.number("angle", 0.0f), props.number("offset", 0.0f));
                    }
                } else if ((color >>> 24) > 0) {
                    if (shape.equals("arc-flat") || shape.equals("arc_flat")) {
                        renderer.arcStrokeFlat(cx, cy, radius, thickness, startAngle, endAngle, color);
                    } else {
                        renderer.arcStroke(cx, cy, radius, thickness, startAngle, endAngle, color);
                    }
                }
                return;
            }
        }

        if ((fill >>> 24) > 0 || (linearGradient && ((gradientStart | gradientEnd) >>> 24) > 0)) {
            switch (shape) {
                case "beveled", "bevel" -> {
                    if (linearGradient) {
                        renderer.beveledRectGradient(
                                x, y, w, h,
                                props.number("bevel", (float) cut),
                                gradientStart,
                                gradientEnd,
                                color(props.get("highlight"), lighten(gradientStart, 0.20f)),
                                color(props.get("shadow"), darken(gradientEnd, 0.24f)),
                                gradientAngle,
                                gradientOffset
                        );
                    } else {
                        renderer.beveledRect(
                                x, y, w, h,
                                props.number("bevel", (float) cut),
                                fill,
                                color(props.get("highlight"), lighten(fill, 0.20f)),
                                color(props.get("shadow"), darken(fill, 0.24f))
                        );
                    }
                }
                case "notched", "notch" -> {
                    double notchWidth = props.number("notchWidth", (float) Math.min(w * 0.18, 18.0));
                    double notchDepth = props.number("notchDepth", (float) Math.min(h * 0.28, 8.0));
                    if (linearGradient) {
                        renderer.notchedRectGradient(x, y, w, h, notchWidth, notchDepth,
                                gradientStart, gradientEnd, gradientAngle, gradientOffset);
                    } else {
                        renderer.notchedRect(x, y, w, h, notchWidth, notchDepth, fill);
                    }
                }
                case "cut", "cut-corner", "cut_corner" -> {
                    if (linearGradient) {
                        renderer.chamferedRectGradient(x, y, w, h,
                                cutTL, cutTR, cutBR, cutBL,
                                gradientStart, gradientEnd, gradientAngle, gradientOffset);
                    } else {
                        renderer.chamferedRect(x, y, w, h, cutTL, cutTR, cutBR, cutBL, fill);
                    }
                }
                default -> {
                    if (linearGradient) {
                        renderer.chamferedRectGradient(x, y, w, h,
                                cutTL, cutTR, cutBR, cutBL,
                                gradientStart, gradientEnd, gradientAngle, gradientOffset);
                    } else {
                        renderer.chamferedRect(x, y, w, h, cutTL, cutTR, cutBR, cutBL, fill);
                    }
                }
            }
        }

        if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
            int strokeStart = color(props.get("strokeStartColor"), stroke);
            int strokeEnd = color(props.get("strokeEndColor"), stroke);
            float strokeAngle = props.number("strokeAngle", gradientAngle);
            float strokeOffset = props.number("strokeOffset", gradientOffset);
            boolean strokeGradient = hasStrokeLinearGradient(props) || linearGradient;
            switch (shape) {
                case "beveled", "bevel" -> {
                    if (strokeGradient) {
                        renderer.beveledRectStrokeGradient(x, y, w, h, props.number("bevel", (float) cut), strokeWidth,
                                strokeStart, strokeEnd, strokeAngle, strokeOffset);
                    } else {
                        renderer.beveledRectStroke(x, y, w, h, props.number("bevel", (float) cut), strokeWidth, stroke);
                    }
                }
                case "notched", "notch" -> {
                    double notchWidth = props.number("notchWidth", (float) Math.min(w * 0.18, 18.0));
                    double notchDepth = props.number("notchDepth", (float) Math.min(h * 0.28, 8.0));
                    if (strokeGradient) {
                        renderer.notchedRectStrokeGradient(x, y, w, h, notchWidth, notchDepth, strokeWidth,
                                strokeStart, strokeEnd, strokeAngle, strokeOffset);
                    } else {
                        renderer.notchedRectStroke(x, y, w, h, notchWidth, notchDepth, strokeWidth, stroke);
                    }
                }
                case "cut", "cut-corner", "cut_corner" -> {
                    if (strokeGradient) {
                        renderer.chamferedRectStrokeGradient(x, y, w, h,
                                cutTL, cutTR, cutBR, cutBL,
                                strokeWidth, strokeStart, strokeEnd, strokeAngle, strokeOffset);
                    } else {
                        renderer.chamferedRectStroke(x, y, w, h, cutTL, cutTR, cutBR, cutBL, strokeWidth, stroke);
                    }
                }
                default -> {
                    if (strokeGradient) {
                        renderer.chamferedRectStrokeGradient(x, y, w, h,
                                cutTL, cutTR, cutBR, cutBL,
                                strokeWidth, strokeStart, strokeEnd, strokeAngle, strokeOffset);
                    } else {
                        renderer.chamferedRectStroke(x, y, w, h, cutTL, cutTR, cutBR, cutBL, strokeWidth, stroke);
                    }
                }
            }
        }
    }

    private UiBoxShape buildBoxShape(UiProps props, UiStyle style, String shape,
                                     double x, double y, double w, double h) {
        float radius = props.number("radius", style.radius());
        float cut = props.number("cut", props.number("chamfer", style.radius()));

        UiCornerSpec defaultCorner = switch (shape) {
            case "rounded", "rounded-rect", "rounded_rect", "rounded-gradient", "rounded_gradient",
                 "rounded-rect-gradient", "rounded_rect_gradient" -> UiCornerSpec.rounded(radius, radius);
            case "chamfered", "beveled", "bevel", "cut", "cut-corner", "cut_corner" -> UiCornerSpec.chamfered(cut);
            default -> UiCornerSpec.square();
        };

        if (shape.equals("rounded-corners") || shape.equals("rounded_corners")
                || shape.equals("rounded-rect-corners") || shape.equals("rounded_rect_corners")) {
            defaultCorner = UiCornerSpec.rounded(radius, radius);
        }

        UiCornerSpec tl = corner(props, "TL", "TopLeft", defaultCorner, radius, cut);
        UiCornerSpec tr = corner(props, "TR", "TopRight", defaultCorner, radius, cut);
        UiCornerSpec br = corner(props, "BR", "BottomRight", defaultCorner, radius, cut);
        UiCornerSpec bl = corner(props, "BL", "BottomLeft", defaultCorner, radius, cut);

        UiBoxShape.Builder builder = UiBoxShape.rect(x, y, w, h)
                .corners(tl, tr, br, bl);

        if (shape.equals("squircle") || shape.equals("superellipse")) {
            String profile = props.string("profile", "standard").toLowerCase(Locale.ROOT);
            float fallbackPower = switch (profile) {
                case "soft" -> UiSquircleProfile.SOFT.exponent();
                case "tight" -> UiSquircleProfile.TIGHT.exponent();
                default -> UiSquircleProfile.STANDARD.exponent();
            };
            builder.squircle(props.number("power", props.number("exponent", fallbackPower)));
        }

        Object edges = props.get("edges");
        UiEdgeSpec top = edgeFromObject(edgeValue(edges, "top"), w, true);
        UiEdgeSpec right = edgeFromObject(edgeValue(edges, "right"), h, false);
        UiEdgeSpec bottom = edgeFromObject(edgeValue(edges, "bottom"), w, true);
        UiEdgeSpec left = edgeFromObject(edgeValue(edges, "left"), h, false);

        if (shape.equals("notched") || shape.equals("notch")) {
            top = UiEdgeSpec.notchedCenter(props.number("notchWidth", (float) Math.min(w * 0.18, 18.0)),
                    props.number("notchDepth", (float) Math.min(h * 0.28, 8.0)));
        }
        if (props.get("edgeTop") != null || props.get("topEdge") != null)
            top = edgeFromObject(first(props, "edgeTop", "topEdge"), w, true);
        if (props.get("edgeRight") != null || props.get("rightEdge") != null)
            right = edgeFromObject(first(props, "edgeRight", "rightEdge"), h, false);
        if (props.get("edgeBottom") != null || props.get("bottomEdge") != null)
            bottom = edgeFromObject(first(props, "edgeBottom", "bottomEdge"), w, true);
        if (props.get("edgeLeft") != null || props.get("leftEdge") != null)
            left = edgeFromObject(first(props, "edgeLeft", "leftEdge"), h, false);

        return builder.edges(top, right, bottom, left).build();
    }

    private UiPaint buildPaint(UiProps props, int fill, boolean linearGradient,
                               int gradientStart, int gradientEnd, float gradientAngle, float gradientOffset) {
        if (hasFillCornerColors(props)) {
            return UiPaint.corners(
                    color(first(props, "topLeftColor", "cTopLeft"), gradientStart),
                    color(first(props, "topRightColor", "cTopRight"), gradientEnd),
                    color(first(props, "bottomRightColor", "cBottomRight"), gradientEnd),
                    color(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart)
            );
        }
        if (linearGradient) {
            return UiPaint.linear(gradientStart, gradientEnd, gradientAngle, gradientOffset);
        }
        return UiPaint.solid(fill);
    }

    private UiPaint buildStrokePaint(UiProps props, int stroke, float gradientAngle, float gradientOffset) {
        if (hasStrokeCornerColors(props)) {
            return UiPaint.corners(
                    color(props.get("strokeTopLeftColor"), stroke),
                    color(props.get("strokeTopRightColor"), stroke),
                    color(props.get("strokeBottomRightColor"), stroke),
                    color(props.get("strokeBottomLeftColor"), stroke)
            );
        }
        if (hasStrokeLinearGradient(props) || hasLinearGradient(props)) {
            return UiPaint.linear(
                    color(props.get("strokeStartColor"), color(props.get("startColor"), stroke)),
                    color(props.get("strokeEndColor"), color(props.get("endColor"), stroke)),
                    props.number("strokeAngle", props.number("angle", gradientAngle)),
                    props.number("strokeOffset", props.number("offset", gradientOffset))
            );
        }
        return UiPaint.solid(stroke);
    }

    public void renderConnector(UiNode node, UiRenderContext context) {
        if (node == null || context == null || context.renderer() == null) return;
        float previousAlpha = renderAlpha;
        renderAlpha = context.alpha();
        try {
            renderConnectorInternal(node, context);
        } finally {
            renderAlpha = previousAlpha;
        }
    }

    private void renderConnectorInternal(UiNode node, UiRenderContext context) {
        UiBounds bounds = node.bounds();
        UiProps props = node.props();
        UiStyle style = node.style();
        Renderer2D renderer = context.renderer();
        String type = props.string("connector", "line").toLowerCase(Locale.ROOT);
        int stroke = color(props.get("stroke"), style.strokeColor() != null ? style.strokeColor() : 0xFFFFFFFF);
        float thickness = props.number("strokeWidth", Math.max(1.0f, style.strokeWidth()));
        boolean gradient = hasLinearGradient(props) || hasStrokeLinearGradient(props);
        int start = color(props.get("startColor"), color(props.get("strokeStartColor"), stroke));
        int end = color(props.get("endColor"), color(props.get("strokeEndColor"), stroke));
        double x1 = bounds.x() + props.number("x1", 0.0f);
        double y1 = bounds.y() + props.number("y1", bounds.height() * 0.5f);
        double x2 = bounds.x() + props.number("x2", bounds.width());
        double y2 = bounds.y() + props.number("y2", bounds.height() * 0.5f);

        switch (type) {
            case "rounded-edge", "rounded_edge", "rounded-line", "rounded_line" -> renderRoundedConnector(
                    renderer, bounds, props, thickness, stroke, gradient, start, end, RoundedConnectorMode.LINE
            );
            case "rounded-node-edge", "rounded_node_edge", "rounded-edge-bezier", "rounded_edge_bezier" ->
                    renderRoundedConnector(
                            renderer, bounds, props, thickness, stroke, gradient, start, end, RoundedConnectorMode.NODE_EDGE
                    );
            case "rounded-orthogonal", "rounded_orthogonal", "rounded-orthogonal-connector",
                 "rounded_orthogonal_connector" -> renderRoundedConnector(
                    renderer, bounds, props, thickness, stroke, gradient, start, end, RoundedConnectorMode.ORTHOGONAL
            );
            case "cable" -> {
                if (gradient) {
                    renderer.cableGradient(
                            x1, y1, x2, y2,
                            thickness,
                            color(props.get("outerStartColor"), darken(start, 0.35f)),
                            color(props.get("outerEndColor"), darken(end, 0.35f)),
                            color(props.get("innerStartColor"), start),
                            color(props.get("innerEndColor"), end)
                    );
                } else {
                    renderer.cable(
                            x1, y1, x2, y2,
                            thickness,
                            color(props.get("outer"), darken(stroke, 0.35f)),
                            color(props.get("inner"), stroke)
                    );
                }
            }
            case "bezier", "bezier-connector", "bezier_connector" -> {
                double cx1 = bounds.x() + props.number("cx1", props.number("x1", 0.0f) + bounds.width() * 0.33f);
                double cy1 = bounds.y() + props.number("cy1", props.number("y1", bounds.height() * 0.5f));
                double cx2 = bounds.x() + props.number("cx2", props.number("x2", bounds.width()) - bounds.width() * 0.33f);
                double cy2 = bounds.y() + props.number("cy2", props.number("y2", bounds.height() * 0.5f));
                int segments = (int) props.number("segments", 20.0f);
                if (gradient) {
                    renderer.bezierConnectorGradient(x1, y1, cx1, cy1, cx2, cy2, x2, y2, segments, thickness, start, end);
                } else {
                    renderer.bezierConnector(x1, y1, cx1, cy1, cx2, cy2, x2, y2, segments, thickness, stroke);
                }
            }
            case "orthogonal", "orthogonal-connector", "orthogonal_connector" -> {
                double midX = bounds.x() + props.number("midX", bounds.width() * 0.5f);
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
                int count = readPoints(props.get("points"), bounds.x(), bounds.y());
                if (count >= 2) {
                    if (gradient) {
                        renderer.splineGradient(points, count, thickness, props.bool("closed", false), start, end);
                    } else {
                        renderer.spline(points, count, thickness, props.bool("closed", false), stroke);
                    }
                }
            }
            case "wire" -> {
                if (gradient) renderer.wireGradient(x1, y1, x2, y2, thickness, start, end);
                else renderer.wire(x1, y1, x2, y2, thickness, stroke);
            }
            default -> {
                if (gradient) renderer.connectorGradient(x1, y1, x2, y2, thickness, start, end);
                else renderer.connector(x1, y1, x2, y2, thickness, stroke);
            }
        }
    }

    private int readPoints(Object value, double offsetX, double offsetY) {
        int count = 0;
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (count >= points.length / 2) break;
                if (item instanceof Map<?, ?> map) {
                    points[count * 2] = offsetX + number(map.get("x"), 0.0f);
                    points[count * 2 + 1] = offsetY + number(map.get("y"), 0.0f);
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
                        if (0 >= points.length / 2) break;
                        points[0 * 2] = offsetX + pendingX;
                        points[count * 2 + 1] = offsetY + n.doubleValue();
                        count++;
                        pendingX = Double.NaN;
                    }
                }
            }
        }
        return count;
    }

    private int color(Object value, int fallback) {
        int resolved;
        if (value instanceof Number n) {
            resolved = n.intValue();
        } else if (value instanceof String s) {
            int themed = UiThemeRegistry.current().color(s, Integer.MIN_VALUE);
            resolved = themed != Integer.MIN_VALUE ? themed : UiColor.parse(s, fallback);
        } else {
            resolved = fallback;
        }
        return renderAlpha >= 0.999f ? resolved : UiColor.multiplyAlpha(resolved, renderAlpha);
    }

    private void renderShapeBlur(Renderer2D renderer,
                                 UiProps props,
                                 UiStyle style,
                                 String shape,
                                 double x,
                                 double y,
                                 double w,
                                 double h,
                                 double cut) {
        if (!props.bool("blur", false)) return;
        float quality = props.number("blurQuality", style.blurQuality());
        float brightness = props.number("blurBrightness", style.blurBrightness());
        float alpha = props.number("blurAlpha", style.blurAlpha()) * renderAlpha;
        if (alpha <= 0.001f) return;

        switch (shape) {
            case "squircle", "superellipse" -> {
                String profile = props.string("profile", "standard").toLowerCase(Locale.ROOT);
                float fallbackPower = switch (profile) {
                    case "soft" -> UiSquircleProfile.SOFT.exponent();
                    case "tight" -> UiSquircleProfile.TIGHT.exponent();
                    default -> UiSquircleProfile.STANDARD.exponent();
                };
                renderer.blurSquircle(x, y, w, h,
                        props.number("power", props.number("exponent", fallbackPower)),
                        quality, brightness, alpha, 0xFFFFFF);
            }
            case "rounded", "round" -> renderer.blurRect(
                    x, y, w, h,
                    props.number("radius", style.radius()),
                    quality,
                    brightness,
                    alpha,
                    0xFFFFFF
            );
            case "rounded-corners", "rounded_corners" -> {
                float radius = props.number("radius", style.radius());
                float radiusTL = props.number("radiusTL", radius);
                float radiusTR = props.number("radiusTR", radius);
                float radiusBR = props.number("radiusBR", radius);
                float radiusBL = props.number("radiusBL", radius);
                renderer.blurComposite(blur -> blur.roundedRectCorners(
                        x, y, w, h,
                        radiusTL, radiusTR, radiusBR, radiusBL,
                        quality,
                        brightness,
                        alpha,
                        0xFFFFFF
                ));
            }
            default -> renderer.blurChamferedRect(
                    x,
                    y,
                    w,
                    h,
                    cut,
                    quality,
                    brightness,
                    alpha,
                    0xFFFFFF
            );
        }
    }

    private enum RoundedConnectorMode {
        LINE,
        NODE_EDGE,
        ORTHOGONAL
    }
}
