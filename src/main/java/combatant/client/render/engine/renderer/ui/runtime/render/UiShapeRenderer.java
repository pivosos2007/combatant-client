/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.Locale;

public final class UiShapeRenderer {
    private final int[] gradientCornersTmp = new int[4];

    private static Renderer2D.LiquidGlassPreset glassPreset(UiProps props) {
        String value = props != null ? props.string("glassPreset", "balanced") : "balanced";
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "light" -> Renderer2D.LiquidGlassPreset.LIGHT;
            case "heavy" -> Renderer2D.LiquidGlassPreset.HEAVY;
            case "hud-small", "hud_small" -> Renderer2D.LiquidGlassPreset.HUD_SMALL;
            case "hud-large", "hud_large" -> Renderer2D.LiquidGlassPreset.HUD_LARGE;
            case "health", "health-bar", "health_bar" -> Renderer2D.LiquidGlassPreset.HEALTH_BAR;
            default -> Renderer2D.LiquidGlassPreset.BALANCED;
        };
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

    private static UiVectorSpace vectorSpace(UiProps props, UiVectorSpace vector) {
        if (vector == null || props == null) return null;
        String coordinates = props.string("coordinateSpace", "viewBox").trim().toLowerCase(Locale.ROOT);
        return coordinates.equals("local") || coordinates.equals("bounds") ? null : vector;
    }

    private static UiBounds vectorPrimitiveBounds(UiProps props, UiVectorSpace vector, UiBounds fallback) {
        if (vector == null || props == null) return fallback;
        Object rawX = props.get("vectorX");
        Object rawY = props.get("vectorY");
        Object rawW = props.get("vectorWidth");
        Object rawH = props.get("vectorHeight");
        if (rawX == null || rawY == null || rawW == null || rawH == null) return fallback;
        return vector.logicalRect(
                number(rawX, 0.0f),
                number(rawY, 0.0f),
                Math.max(0.0f, number(rawW, 0.0f)),
                Math.max(0.0f, number(rawH, 0.0f))
        );
    }

    private static float clamp01(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }

    public void render(UiNode node, UiBounds logicalBounds, UiRenderContext context) {
        if (node == null || logicalBounds == null || context == null || context.renderer() == null) return;
        if (logicalBounds.width() <= 0.0f || logicalBounds.height() <= 0.0f) return;
        float alpha = context.alpha();
        if (alpha <= 0.001f) return;
        renderShapeInternal(node, context, logicalBounds, alpha);
    }

    private void renderShapeInternal(UiNode node, UiRenderContext context, UiBounds logicalBounds, float alpha) {
        UiProps props = node.props();
        UiStyle style = node.style();
        Renderer2D renderer = context.renderer();
        String shape = props.string("shape", "chamfered").toLowerCase(Locale.ROOT);
        UiVectorSpace vector = vectorSpace(props, context.vectorSpace());
        UiBounds primitiveLogicalBounds = vectorPrimitiveBounds(props, vector, logicalBounds);
        UiBounds bounds = context.renderBounds(primitiveLogicalBounds);
        int fill = UiRenderColors.applyAlpha(UiReactiveVisual.color(node, "fill", style.backgroundColor() != null ? style.backgroundColor() : 0x00000000), alpha);
        int stroke = UiRenderColors.applyAlpha(UiReactiveVisual.color(node, "stroke", style.strokeColor() != null ? style.strokeColor() : 0x00000000), alpha);
        float strokeWidth = context.renderLength(props.number("strokeWidth", style.strokeWidth()));
        double x = bounds.x();
        double y = bounds.y();
        double w = Math.max(0.0, context.renderLength(props.number("renderWidth", primitiveLogicalBounds.width())));
        double h = Math.max(0.0, context.renderLength(props.number("renderHeight", primitiveLogicalBounds.height())));
        float logicalCut = props.number("cut", props.number("chamfer", style.radius()));
        double cut = context.renderLength(logicalCut);
        double cutTL = context.renderLength(chamferCorner(props, "TL", "TopLeft", logicalCut));
        double cutTR = context.renderLength(chamferCorner(props, "TR", "TopRight", logicalCut));
        double cutBR = context.renderLength(chamferCorner(props, "BR", "BottomRight", logicalCut));
        double cutBL = context.renderLength(chamferCorner(props, "BL", "BottomLeft", logicalCut));
        boolean linearGradient = hasLinearGradient(props);
        int gradientStart = resolveColor(props.get("startColor"), fill, alpha);
        int gradientEnd = resolveColor(props.get("endColor"), fill, alpha);
        float gradientAngle = props.number("angle", 90.0f);
        float gradientOffset = context.renderLength(props.number("offset", 0.0f));

        boolean primitiveShape = UiShapeGeometry.isPrimitiveShape(shape);
        boolean compoundShape = UiShapeGeometry.isCompoundShape(shape);
        // Blur only shapes supported by the blur mask family.
        if (!primitiveShape && !compoundShape) renderShapeBlur(renderer, props, style, shape, x, y, w, h, cut, alpha, context);

        if (compoundShape) {
            UiCompoundSdf compound = UiShapeGeometry.buildCompoundSdf(props, shape, x, y, w, h, context.transform().scale());
            if (props.bool("liquidGlass", style.liquidGlass())) {
                UiBackdropRuntime.drawLiquidGlass(renderer, props, () ->
                        renderer.liquidGlassCompound(
                                compound,
                                resolveColor(props.get("glassTint"), 0xFFFFFFFF, alpha),
                                props.number("glassAlpha", 1.0f) * alpha,
                                props.number("blurAlpha", style.blurAlpha()) * alpha,
                                glassPreset(props)
                        ));
            }
            UiPaint fillPaint = buildPaint(props, fill, linearGradient, gradientStart, gradientEnd, gradientAngle, gradientOffset, alpha);
            if ((fillPaint.solidColor() >>> 24) > 0 || linearGradient || hasFillCornerColors(props)) {
                renderer.compoundSdf(compound, fillPaint);
            }
            if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                renderer.compoundSdfStroke(compound, buildStrokePaint(props, stroke, gradientAngle, gradientOffset, alpha, context),
                        UiStroke.of(strokeWidth));
            }
            return;
        }

        if (primitiveShape) {
            UiPrimitive primitive = UiShapeGeometry.buildPrimitive(props, style, shape, x, y, w, h, context.transform().scale());
            if (props.bool("liquidGlass", style.liquidGlass())) {
                UiBackdropRuntime.drawLiquidGlass(renderer, props, () -> {
                    int glassTint = resolveColor(props.get("glassTint"), 0xFFFFFFFF, alpha);
                    float glassAlpha = props.number("glassAlpha", 1.0f) * alpha;
                    float blurAlpha = props.number("blurAlpha", style.blurAlpha()) * alpha;
                    Renderer2D.LiquidGlassPreset preset = glassPreset(props);
                    if (primitive.shaderEligible()) {
                        renderer.liquidGlassPrimitive(primitive, glassTint, glassAlpha, blurAlpha, preset);
                    } else if (UiShapeGeometry.isRectPrimitivePreset(shape, props)) {
                        // Rounded/corner-authored RECT primitives can lower to >8 polygon points.
                        // Do not let scripted UI take down the whole surface: preserve the
                        // intended rectangular glass through the dedicated analytic rect path.
                        float radius = Math.max(0.0f, context.renderLength(props.number("radius", style.radius())));
                        float rounding = Math.max(radius, primitive.rounding());
                        renderer.liquidGlassRect(x, y, w, h, rounding,
                                glassTint, glassAlpha, blurAlpha, preset);
                    }
                });
            }
            UiPaint fillPaint = buildPaint(props, fill, linearGradient, gradientStart, gradientEnd, gradientAngle, gradientOffset, alpha);
            if ((fillPaint.solidColor() >>> 24) > 0 || linearGradient || hasFillCornerColors(props)) {
                renderer.primitive(primitive, fillPaint);
            }
            if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                renderer.primitiveStroke(primitive, buildStrokePaint(props, stroke, gradientAngle, gradientOffset, alpha, context),
                        UiStroke.of(strokeWidth));
            }
            return;
        }

        if (UiShapeGeometry.isBoxShape(shape, props)) {
            UiBoxShape boxShape = UiShapeGeometry.buildBoxShape(props, style, shape, x, y, w, h, context.transform().scale());
            UiPaint fillPaint = buildPaint(props, fill, linearGradient, gradientStart, gradientEnd, gradientAngle, gradientOffset, alpha);
            if ((fillPaint.solidColor() >>> 24) > 0 || linearGradient || hasFillCornerColors(props)) {
                renderer.box(boxShape, fillPaint);
            }
            if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                UiPaint strokePaint = buildStrokePaint(props, stroke, gradientAngle, gradientOffset, alpha, context);
                renderer.boxStroke(boxShape, strokePaint, UiStroke.of(strokeWidth));
            }
            return;
        }

        switch (shape) {
            case "rounded-soft-shadow", "rounded_soft_shadow", "soft-shadow", "soft_shadow" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float blur = context.renderLength(props.number("blur", props.number("shadowBlur", 8.0f)));
                float innerAlpha = props.number("innerAlpha", props.number("shadowInnerAlpha", 0.18f));
                int color = resolveColor(props.get("color"), fill, alpha);
                if ((color >>> 24) > 0 && blur > 0.0f) {
                    renderer.roundedRectSoftShadow(x, y, w, h, radius, blur, innerAlpha, color);
                }
                return;
            }
            case "rounded-shadow", "rounded_shadow", "shadow" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float softness = context.renderLength(props.number("softness", 8.0f));
                float spread = context.renderLength(props.number("spread", 12.0f));
                int color = resolveColor(props.get("color"), fill, alpha);
                if ((color >>> 24) > 0) {
                    renderer.roundedRectShadow(x, y, w, h, radius, softness, spread, color);
                }
                return;
            }
            case "rounded-glow", "rounded_glow", "glow" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float softness = context.renderLength(props.number("softness", 0.0f));
                float glow = context.renderLength(props.number("glow", props.number("spread", 8.0f)));
                int color = resolveColor(props.get("color"), fill, alpha);
                if ((color >>> 24) > 0 && glow > 0.0f) {
                    renderer.roundedRectGlow(x, y, w, h, radius, softness, glow, color);
                }
                return;
            }
            case "radial-glow-masked", "radial_glow_masked", "radial-glow", "radial_glow" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float softness = context.renderLength(props.number("softness", 0.0f));
                float glowRadius = context.renderLength(props.number("glowRadius", props.number("glow", Math.max(logicalBounds.width(), logicalBounds.height()) * 0.5f)));
                float cx = context.renderLength(props.number("cx", logicalBounds.width() * 0.5f));
                float cy = context.renderLength(props.number("cy", logicalBounds.height() * 0.5f));
                int color = resolveColor(props.get("color"), fill, alpha);
                if ((color >>> 24) > 0 && glowRadius > 0.0f) {
                    renderer.radialGlowMasked(x, y, w, h, radius, softness, glowRadius, (float) x + cx, (float) y + cy, color);
                }
                return;
            }
            case "rounded-gradient-quad", "rounded_gradient_quad", "rounded-quad-gradient", "rounded_quad_gradient" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float softness = context.renderLength(props.number("softness", 0.0f));
                renderer.roundedRectGradientQuad(x, y, w, h, radius, softness,
                        resolveColor(first(props, "topLeftColor", "cTopLeft"), gradientStart, alpha),
                        resolveColor(first(props, "topRightColor", "cTopRight"), gradientEnd, alpha),
                        resolveColor(first(props, "bottomRightColor", "cBottomRight"), gradientEnd, alpha),
                        resolveColor(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart, alpha));
                return;
            }
            case "rounded-stroke-gradient", "rounded_stroke_gradient" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float softness = context.renderLength(props.number("softness", 0.0f));
                float thickness = context.renderLength(props.number("thickness", Math.max(1.0f, style.strokeWidth())));
                int start = resolveColor(props.get("startColor"), resolveColor(props.get("strokeStartColor"), stroke, alpha), alpha);
                int end = resolveColor(props.get("endColor"), resolveColor(props.get("strokeEndColor"), stroke, alpha), alpha);
                if (((start | end) >>> 24) > 0 && thickness > 0.0f) {
                    renderer.roundedRectStrokeGradient(x, y, w, h, radius, softness, thickness,
                            start, end, props.number("angle", props.number("strokeAngle", 90.0f)),
                            props.get("offset") != null ? context.renderLength(props.number("offset", 0.0f)) : context.renderLength(props.number("strokeOffset", 0.0f)));
                }
                return;
            }
            case "circle-soft-shadow", "circle_soft_shadow" -> {
                double radius = context.renderLength(props.number("radius",
                        vector != null ? 2.0f : Math.min(logicalBounds.width(), logicalBounds.height()) * 0.5f));
                double cx = vector != null
                        ? context.renderVectorX(props.number("cx", (float) (vector.minX() + vector.width() * 0.5)))
                        : x + context.renderLength(props.number("cx", logicalBounds.width() * 0.5f));
                double cy = vector != null
                        ? context.renderVectorY(props.number("cy", (float) (vector.minY() + vector.height() * 0.5)))
                        : y + context.renderLength(props.number("cy", logicalBounds.height() * 0.5f));
                float blur = context.renderLength(props.number("blur", props.number("shadowBlur", 7.0f)));
                float innerAlpha = props.number("innerAlpha", props.number("shadowInnerAlpha", 0.34f));
                int color = resolveColor(props.get("color"), fill, alpha);
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
                int start = resolveColor(props.get("startColor"), fill, alpha);
                int end = resolveColor(props.get("endColor"), fill, alpha);
                if (hasFillCornerColors(props)) {
                    renderer.quadGradient(x, y, w, h,
                            resolveColor(first(props, "topLeftColor", "cTopLeft"), start, alpha),
                            resolveColor(first(props, "topRightColor", "cTopRight"), end, alpha),
                            resolveColor(first(props, "bottomRightColor", "cBottomRight"), end, alpha),
                            resolveColor(first(props, "bottomLeftColor", "cBottomLeft"), start, alpha));
                } else if (((start | end) >>> 24) > 0) {
                    renderer.quadGradientLinear(x, y, w, h, start, end,
                            props.number("angle", 0.0f),
                            context.renderLength(props.number("offset", 0.0f)));
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    if (hasStrokeCornerColors(props)) {
                        renderer.roundedRectStrokeGradientQuad(x, y, w, h, 0.0f, 0.0f, strokeWidth,
                                resolveColor(props.get("strokeTopLeftColor"), stroke, alpha),
                                resolveColor(props.get("strokeTopRightColor"), stroke, alpha),
                                resolveColor(props.get("strokeBottomRightColor"), stroke, alpha),
                                resolveColor(props.get("strokeBottomLeftColor"), stroke, alpha));
                    } else {
                        renderer.quadStrokeGradientLinear(x, y, w, h, strokeWidth,
                                resolveColor(props.get("strokeStartColor"), stroke, alpha),
                                resolveColor(props.get("strokeEndColor"), stroke, alpha),
                                props.number("strokeAngle", props.number("angle", 0.0f)),
                                props.get("strokeOffset") != null ? context.renderLength(props.number("strokeOffset", 0.0f)) : context.renderLength(props.number("offset", 0.0f)));
                    }
                }
                return;
            }
            case "rounded", "rounded-rect", "rounded_rect" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                if ((fill >>> 24) > 0) {
                    renderer.roundedRect(x, y, w, h, radius, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.roundedRectStroke(x, y, w, h, radius, strokeWidth, stroke);
                }
                return;
            }
            case "rounded-gradient", "rounded_gradient", "rounded-rect-gradient", "rounded_rect_gradient" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                int start = resolveColor(props.get("startColor"), fill, alpha);
                int end = resolveColor(props.get("endColor"), fill, alpha);
                if (hasFillCornerColors(props)) {
                    renderer.roundedRectGradientQuad(x, y, w, h, radius,
                            resolveColor(first(props, "topLeftColor", "cTopLeft"), start, alpha),
                            resolveColor(first(props, "topRightColor", "cTopRight"), end, alpha),
                            resolveColor(first(props, "bottomRightColor", "cBottomRight"), end, alpha),
                            resolveColor(first(props, "bottomLeftColor", "cBottomLeft"), start, alpha));
                } else if (((start | end) >>> 24) > 0) {
                    renderer.roundedRectGradient(x, y, w, h, radius, start, end,
                            props.number("angle", 90.0f),
                            context.renderLength(props.number("offset", 0.0f)));
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    if (hasStrokeCornerColors(props)) {
                        renderer.roundedRectStrokeGradientQuad(x, y, w, h, radius, strokeWidth,
                                resolveColor(props.get("strokeTopLeftColor"), stroke, alpha),
                                resolveColor(props.get("strokeTopRightColor"), stroke, alpha),
                                resolveColor(props.get("strokeBottomRightColor"), stroke, alpha),
                                resolveColor(props.get("strokeBottomLeftColor"), stroke, alpha));
                    } else {
                        renderer.roundedRectStrokeGradient(x, y, w, h, radius, strokeWidth,
                                resolveColor(props.get("strokeStartColor"), stroke, alpha),
                                resolveColor(props.get("strokeEndColor"), stroke, alpha),
                                props.number("strokeAngle", props.number("angle", 90.0f)),
                                props.get("strokeOffset") != null ? context.renderLength(props.number("strokeOffset", 0.0f)) : context.renderLength(props.number("offset", 0.0f)));
                    }
                }
                return;
            }
            case "rounded-progress-gradient", "rounded_progress_gradient", "progress-rounded-gradient",
                "progress_rounded_gradient" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float progress = props.number("progress", 1.0f);
                boolean fromRight = props.bool("fromRight", false) || "right".equals(props.string("direction", ""));
                int start = resolveColor(props.get("startColor"), fill, alpha);
                int end = resolveColor(props.get("endColor"), start, alpha);
                if (((start | end) >>> 24) > 0) {
                    renderer.roundedProgressRectGradient(x, y, w, h, radius, progress, fromRight,
                            start,
                            end,
                            props.number("angle", 0.0f),
                            context.renderLength(props.number("offset", 0.0f)));
                }
                return;
            }
            case "rounded-smoke-fill", "rounded_smoke_fill", "smoke-fill", "smoke_fill" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float fillRatio = props.number("fillRatio", props.number("progress", 1.0f));
                boolean fromRight = props.bool("fromRight", false) || "right".equals(props.string("direction", ""));
                int first = resolveColor(first(props, "firstColor", "startColor"), fill, alpha);
                int second = resolveColor(first(props, "secondColor", "endColor"), first, alpha);
                int third = resolveColor(props.get("thirdColor"), second, alpha);
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
                float radius = context.renderLength(props.number("radius", style.radius()));
                float radiusTL = props.get("radiusTL") != null ? context.renderLength(props.number("radiusTL", 0.0f)) : radius;
                float radiusTR = props.get("radiusTR") != null ? context.renderLength(props.number("radiusTR", 0.0f)) : radius;
                float radiusBR = props.get("radiusBR") != null ? context.renderLength(props.number("radiusBR", 0.0f)) : radius;
                float radiusBL = props.get("radiusBL") != null ? context.renderLength(props.number("radiusBL", 0.0f)) : radius;
                if (hasFillCornerColors(props)) {
                    renderer.roundedRectCornersQuad(x, y, w, h, radiusTL, radiusTR, radiusBR, radiusBL,
                            resolveColor(first(props, "topLeftColor", "cTopLeft"), gradientStart, alpha),
                            resolveColor(first(props, "topRightColor", "cTopRight"), gradientEnd, alpha),
                            resolveColor(first(props, "bottomRightColor", "cBottomRight"), gradientEnd, alpha),
                            resolveColor(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart, alpha));
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
                double radius = context.renderLength(props.number("radius",
                        vector != null ? 2.0f : Math.min(logicalBounds.width(), logicalBounds.height()) * 0.5f));
                double cx = vector != null
                        ? context.renderVectorX(props.number("cx", (float) (vector.minX() + vector.width() * 0.5)))
                        : x + context.renderLength(props.number("cx", logicalBounds.width() * 0.5f));
                double cy = vector != null
                        ? context.renderVectorY(props.number("cy", (float) (vector.minY() + vector.height() * 0.5)))
                        : y + context.renderLength(props.number("cy", logicalBounds.height() * 0.5f));
                if ((fill >>> 24) > 0) {
                    renderer.circle(cx, cy, radius, fill);
                }
                if ((stroke >>> 24) > 0 && strokeWidth > 0.0f) {
                    renderer.circleStroke(cx, cy, radius, strokeWidth, stroke);
                }
                return;
            }
            case "circle-stroke", "circle_stroke", "ring" -> {
                double radius = context.renderLength(props.number("radius",
                        vector != null ? 2.0f : Math.min(logicalBounds.width(), logicalBounds.height()) * 0.5f));
                double cx = vector != null
                        ? context.renderVectorX(props.number("cx", (float) (vector.minX() + vector.width() * 0.5)))
                        : x + context.renderLength(props.number("cx", logicalBounds.width() * 0.5f));
                double cy = vector != null
                        ? context.renderVectorY(props.number("cy", (float) (vector.minY() + vector.height() * 0.5)))
                        : y + context.renderLength(props.number("cy", logicalBounds.height() * 0.5f));
                float thickness = context.renderLength(props.number("thickness", Math.max(1.0f, style.strokeWidth())));
                int color = (stroke >>> 24) > 0 ? stroke : fill;
                if ((color >>> 24) > 0 && thickness > 0.0f) {
                    renderer.circleStroke(cx, cy, radius, thickness, color);
                }
                return;
            }
            case "arc", "arc-stroke", "arc_stroke", "arc-flat", "arc_flat", "arc-gradient", "arc_gradient", "arc-hash", "arc_hash" -> {
                float authoredThickness = props.number("thickness", Math.max(1.0f, style.strokeWidth()));
                float thickness = context.renderLength(authoredThickness);
                float defaultRadius = vector != null
                        ? 2.0f
                        : Math.max(0.0f, Math.min(logicalBounds.width(), logicalBounds.height()) * 0.5f - authoredThickness * 0.5f);
                double radius = context.renderLength(props.number("radius", defaultRadius));
                double cx = vector != null
                        ? context.renderVectorX(props.number("cx", (float) (vector.minX() + vector.width() * 0.5)))
                        : x + context.renderLength(props.number("cx", logicalBounds.width() * 0.5f));
                double cy = vector != null
                        ? context.renderVectorY(props.number("cy", (float) (vector.minY() + vector.height() * 0.5)))
                        : y + context.renderLength(props.number("cy", logicalBounds.height() * 0.5f));
                float startAngle = props.number("startAngle", 0.0f);
                float endAngle = props.number("endAngle", 360.0f);
                int color = (stroke >>> 24) > 0 ? stroke : fill;
                if (thickness <= 0.0f) return;
                if (shape.equals("arc-gradient") || shape.equals("arc_gradient")
                        || shape.equals("arc-hash") || shape.equals("arc_hash")) {
                    int start = resolveColor(props.get("startColor"), color, alpha);
                    int end = resolveColor(props.get("endColor"), color, alpha);
                    if (((start | end) >>> 24) > 0) {
                        if (shape.equals("arc-hash") || shape.equals("arc_hash")) {
                            renderer.arcStrokeHashedGradient(cx, cy, radius, thickness, startAngle, endAngle,
                                    context.renderLength(props.number("softness", 0.0f)),
                                    start, end, props.number("angle", 0.0f), context.renderLength(props.number("offset", 0.0f)),
                                    props.number("hashTime", 0.0f));
                        } else {
                            renderer.arcStrokeGradient(cx, cy, radius, thickness, startAngle, endAngle,
                                    context.renderLength(props.number("softness", 0.0f)),
                                    start, end, props.number("angle", 0.0f), context.renderLength(props.number("offset", 0.0f)));
                        }
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
                                props.get("bevel") != null ? context.renderLength(props.number("bevel", 0.0f)) : (float) cut,
                                gradientStart,
                                gradientEnd,
                                resolveColor(props.get("highlight"), UiRenderColors.lighten(gradientStart, 0.20f), alpha),
                                resolveColor(props.get("shadow"), UiRenderColors.darken(gradientEnd, 0.24f), alpha),
                                gradientAngle,
                                gradientOffset
                        );
                    } else {
                        renderer.beveledRect(
                                x, y, w, h,
                                props.get("bevel") != null ? context.renderLength(props.number("bevel", 0.0f)) : (float) cut,
                                fill,
                                resolveColor(props.get("highlight"), UiRenderColors.lighten(fill, 0.20f), alpha),
                                resolveColor(props.get("shadow"), UiRenderColors.darken(fill, 0.24f), alpha)
                        );
                    }
                }
                case "notched", "notch" -> {
                    double notchWidth = context.renderLength(props.number("notchWidth", Math.min(logicalBounds.width() * 0.18f, 18.0f)));
                    double notchDepth = context.renderLength(props.number("notchDepth", Math.min(logicalBounds.height() * 0.28f, 8.0f)));
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
            int strokeStart = resolveColor(props.get("strokeStartColor"), stroke, alpha);
            int strokeEnd = resolveColor(props.get("strokeEndColor"), stroke, alpha);
            float strokeAngle = props.number("strokeAngle", gradientAngle);
            float strokeOffset = props.get("strokeOffset") != null ? context.renderLength(props.number("strokeOffset", 0.0f)) : gradientOffset;
            boolean strokeGradient = hasStrokeLinearGradient(props) || linearGradient;
            switch (shape) {
                case "beveled", "bevel" -> {
                    if (strokeGradient) {
                        renderer.beveledRectStrokeGradient(x, y, w, h, props.get("bevel") != null ? context.renderLength(props.number("bevel", 0.0f)) : (float) cut, strokeWidth,
                                strokeStart, strokeEnd, strokeAngle, strokeOffset);
                    } else {
                        renderer.beveledRectStroke(x, y, w, h, props.get("bevel") != null ? context.renderLength(props.number("bevel", 0.0f)) : (float) cut, strokeWidth, stroke);
                    }
                }
                case "notched", "notch" -> {
                    double notchWidth = context.renderLength(props.number("notchWidth", Math.min(logicalBounds.width() * 0.18f, 18.0f)));
                    double notchDepth = context.renderLength(props.number("notchDepth", Math.min(logicalBounds.height() * 0.28f, 8.0f)));
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

    private UiPaint buildPaint(UiProps props, int fill, boolean linearGradient,
                               int gradientStart, int gradientEnd, float gradientAngle, float gradientOffset,
                               float alpha) {
        if (hasFillCornerColors(props)) {
            return UiPaint.corners(
                    resolveColor(first(props, "topLeftColor", "cTopLeft"), gradientStart, alpha),
                    resolveColor(first(props, "topRightColor", "cTopRight"), gradientEnd, alpha),
                    resolveColor(first(props, "bottomRightColor", "cBottomRight"), gradientEnd, alpha),
                    resolveColor(first(props, "bottomLeftColor", "cBottomLeft"), gradientStart, alpha)
            );
        }
        if (linearGradient) {
            return UiPaint.linear(gradientStart, gradientEnd, gradientAngle, gradientOffset);
        }
        return UiPaint.solid(fill);
    }

    private UiPaint buildStrokePaint(UiProps props, int stroke, float gradientAngle, float gradientOffset,
                                     float alpha, UiRenderContext context) {
        if (hasStrokeCornerColors(props)) {
            return UiPaint.corners(
                    resolveColor(props.get("strokeTopLeftColor"), stroke, alpha),
                    resolveColor(props.get("strokeTopRightColor"), stroke, alpha),
                    resolveColor(props.get("strokeBottomRightColor"), stroke, alpha),
                    resolveColor(props.get("strokeBottomLeftColor"), stroke, alpha)
            );
        }
        if (hasStrokeLinearGradient(props) || hasLinearGradient(props)) {
            return UiPaint.linear(
                    resolveColor(props.get("strokeStartColor"), resolveColor(props.get("startColor"), stroke, alpha), alpha),
                    resolveColor(props.get("strokeEndColor"), resolveColor(props.get("endColor"), stroke, alpha), alpha),
                    props.number("strokeAngle", props.number("angle", gradientAngle)),
                    props.get("strokeOffset") != null
                            ? context.renderLength(props.number("strokeOffset", 0.0f))
                            : (props.get("offset") != null ? context.renderLength(props.number("offset", 0.0f)) : gradientOffset)
            );
        }
        return UiPaint.solid(stroke);
    }

    private static int resolveColor(Object value, int fallback, float alpha) {
        return value == null ? fallback : UiRenderColors.resolve(value, fallback, alpha);
    }

    private void renderShapeBlur(Renderer2D renderer,
                                 UiProps props,
                                 UiStyle style,
                                 String shape,
                                 double x,
                                 double y,
                                 double w,
                                 double h,
                                 double cut,
                                 float alpha,
                                 UiRenderContext context) {
        if (!props.bool("blur", false)) return;
        float quality = props.number("blurQuality", style.blurQuality());
        float brightness = props.number("blurBrightness", style.blurBrightness());
        float blurAlpha = props.number("blurAlpha", style.blurAlpha()) * alpha;
        if (blurAlpha <= 0.001f) return;

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
                        quality, brightness, blurAlpha, 0xFFFFFF);
            }
            case "rounded", "round" -> renderer.blurRect(
                    x, y, w, h,
                    context.renderLength(props.number("radius", style.radius())),
                    quality,
                    brightness,
                    blurAlpha,
                    0xFFFFFF
            );
            case "rounded-corners", "rounded_corners" -> {
                float radius = context.renderLength(props.number("radius", style.radius()));
                float radiusTL = props.get("radiusTL") != null ? context.renderLength(props.number("radiusTL", 0.0f)) : radius;
                float radiusTR = props.get("radiusTR") != null ? context.renderLength(props.number("radiusTR", 0.0f)) : radius;
                float radiusBR = props.get("radiusBR") != null ? context.renderLength(props.number("radiusBR", 0.0f)) : radius;
                float radiusBL = props.get("radiusBL") != null ? context.renderLength(props.number("radiusBL", 0.0f)) : radius;
                renderer.blurComposite(blur -> blur.roundedRectCorners(
                        x, y, w, h,
                        radiusTL, radiusTR, radiusBR, radiusBL,
                        quality,
                        brightness,
                        blurAlpha,
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
                    blurAlpha,
                    0xFFFFFF
            );
        }
    }

}
