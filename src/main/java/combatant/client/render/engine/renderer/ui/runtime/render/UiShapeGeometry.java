/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.render;

import combatant.client.render.engine.renderer.ui.draw.*;
import combatant.client.render.engine.renderer.ui.runtime.core.UiProps;
import combatant.client.render.engine.renderer.ui.runtime.style.UiStyle;

import java.util.Locale;
import java.util.Map;

/** Decodes script-authored shape properties into typed renderer geometry. */
final class UiShapeGeometry {
    private UiShapeGeometry() {
    }

    static boolean isPrimitiveShape(String shape) {
        return switch (shape) {
            case "primitive", "procedural-panel", "procedural_panel", "panel-primitive", "panel_primitive",
                 "hexagon", "trapezoid-left", "trapezoid_left", "trapezoid-right", "trapezoid_right",
                 "parallelogram-left", "parallelogram_left", "parallelogram-right", "parallelogram_right",
                 "directional-left", "directional_left", "directional-right", "directional_right" -> true;
            default -> false;
        };
    }

    static boolean isCompoundShape(String shape) {
        return switch (shape) {
            case "island-blob", "island_blob", "metaball", "metaballs",
                 "smooth-box-union", "smooth_box_union",
                 "smooth-squircle-union", "smooth_squircle_union",
                 "compound-sdf", "compound_sdf" -> true;
            default -> false;
        };
    }

    static boolean isRectPrimitivePreset(String shape, UiProps props) {
        String raw = props != null ? props.string("preset", shape) : shape;
        String preset = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (preset) {
            case "", "primitive", "procedural-panel", "procedural_panel",
                 "panel-primitive", "panel_primitive", "rect", "rectangle" -> true;
            default -> false;
        };
    }

    static boolean isBoxShape(String shape, UiProps props) {
        // UiBoxShape is reserved for the flexible-box contract; scripted shape ids keep their dedicated paths.
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
                                       UiCornerSpec fallback, float radius, float cut, float renderScale) {
        Object corners = props.get("corners");
        Object explicit = cornerValue(corners, shortName, longName);
        if (explicit == null) explicit = first(props, "corner" + shortName, "corner" + longName);
        if (explicit != null) return cornerFromObject(explicit, fallback, radius, cut, renderScale);

        Object mode = first(props, "cornerMode" + shortName, "cornerMode" + longName);
        if (mode != null) return cornerFromObject(mode, fallback, radius, cut, renderScale);

        float r = props.get("radius" + shortName) != null
                ? props.number("radius" + shortName, 0.0f) * renderScale
                : props.get("radius" + longName) != null
                ? props.number("radius" + longName, 0.0f) * renderScale
                : fallback.radiusX();
        float c = props.get("cut" + shortName) != null
                ? props.number("cut" + shortName, 0.0f) * renderScale
                : props.get("cut" + longName) != null
                ? props.number("cut" + longName, 0.0f) * renderScale
                : props.get("chamfer" + shortName) != null
                ? props.number("chamfer" + shortName, 0.0f) * renderScale
                : props.get("chamfer" + longName) != null
                ? props.number("chamfer" + longName, 0.0f) * renderScale
                : fallback.cutX();
        if (r > 0.0f && (fallback.kind() == UiCornerKind.ROUNDED || fallback.kind() == UiCornerKind.SQUARE)) {
            return UiCornerSpec.rounded(r, r);
        }
        if (c > 0.0f && fallback.kind() != UiCornerKind.ROUNDED) {
            return UiCornerSpec.chamfered(c);
        }
        return fallback;
    }

    private static UiCornerSpec cornerFromObject(Object value, UiCornerSpec fallback, float radius, float cut, float renderScale) {
        if (value instanceof Map<?, ?> map) {
            Object rawKind = map.get("kind");
            if (rawKind == null) rawKind = map.get("type");
            String kind = String.valueOf(rawKind != null ? rawKind : "").toLowerCase(Locale.ROOT);
            float r = map.get("radius") != null ? number(map.get("radius"), 0.0f) * renderScale : radius;
            float rx = map.get("radiusX") != null ? number(map.get("radiusX"), 0.0f) * renderScale : r;
            float ry = map.get("radiusY") != null ? number(map.get("radiusY"), 0.0f) * renderScale : r;
            float c = map.get("cut") != null ? number(map.get("cut"), 0.0f) * renderScale
                    : map.get("chamfer") != null ? number(map.get("chamfer"), 0.0f) * renderScale : cut;
            float cx = map.get("cutX") != null ? number(map.get("cutX"), 0.0f) * renderScale : c;
            float cy = map.get("cutY") != null ? number(map.get("cutY"), 0.0f) * renderScale : c;
            return switch (kind) {
                case "round", "rounded", "radius" -> UiCornerSpec.rounded(rx, ry);
                case "chamfer", "chamfered", "cut", "bevel", "beveled" -> UiCornerSpec.chamfered(cx, cy);
                case "concave", "inverse", "inverse-round", "inverse_round" -> UiCornerSpec.concaveRounded(r);
                case "notch", "notched", "removed", "removed-corner", "removed_corner" -> UiCornerSpec.notched(cx, cy);
                case "square", "none" -> UiCornerSpec.square();
                default -> fallback;
            };
        }
        if (value instanceof Number n) return UiCornerSpec.rounded(n.floatValue() * renderScale, n.floatValue() * renderScale);
        if (value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("round")) return UiCornerSpec.rounded(radius, radius);
            if (normalized.startsWith("chamfer") || normalized.startsWith("cut") || normalized.startsWith("bevel"))
                return UiCornerSpec.chamfered(cut);
            if (normalized.startsWith("concave") || normalized.startsWith("inverse"))
                return UiCornerSpec.concaveRounded(radius);
            if (normalized.startsWith("notch") || normalized.startsWith("removed"))
                return UiCornerSpec.notched(cut, cut);
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

    private static UiEdgeSpec edgeFromObject(Object value, double length, float renderScale) {
        if (value == null) return UiEdgeSpec.straight();
        if (value instanceof Map<?, ?> map) {
            Object rawKind = map.get("kind");
            if (rawKind == null) rawKind = map.get("type");
            String kind = String.valueOf(rawKind != null ? rawKind : "").toLowerCase(Locale.ROOT);
            if (kind.equals("notch") || kind.equals("notched")) {
                float width = map.get("width") != null ? number(map.get("width"), 0.0f) * renderScale
                        : map.get("size") != null ? number(map.get("size"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.18, 18.0 * renderScale);
                float depth = map.get("depth") != null ? number(map.get("depth"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.10, 8.0 * renderScale);
                Object offset = map.get("offset");
                if (offset == null || "center".equals(String.valueOf(offset)))
                    return UiEdgeSpec.notchedCenter(width, depth);
                return UiEdgeSpec.notched(number(offset, 0.0f) * renderScale, width, depth);
            }
            if (kind.equals("inset")) return UiEdgeSpec.inset(number(map.get("depth"), 0.0f) * renderScale);
            if (kind.equals("cut") || kind.equals("diagonal-cut") || kind.equals("diagonal_cut")) {
                float width = map.get("width") != null ? number(map.get("width"), 0.0f) * renderScale
                        : map.get("size") != null ? number(map.get("size"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.18, 18.0 * renderScale);
                float depth = map.get("depth") != null ? number(map.get("depth"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.10, 8.0 * renderScale);
                Object offset = map.get("offset");
                if (offset == null || "center".equals(String.valueOf(offset))) return UiEdgeSpec.cutCenter(width, depth);
                return UiEdgeSpec.cut(number(offset, 0.0f) * renderScale, width, depth);
            }
            if (kind.equals("protrusion") || kind.equals("tab")) {
                float width = map.get("width") != null ? number(map.get("width"), 0.0f) * renderScale
                        : map.get("size") != null ? number(map.get("size"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.18, 18.0 * renderScale);
                float depth = map.get("depth") != null ? number(map.get("depth"), 0.0f) * renderScale
                        : (float) Math.min(length * 0.10, 8.0 * renderScale);
                Object offset = map.get("offset");
                if (offset == null || "center".equals(String.valueOf(offset))) return UiEdgeSpec.protrusionCenter(width, depth);
                return UiEdgeSpec.protrusion(number(offset, 0.0f) * renderScale, width, depth);
            }
            return UiEdgeSpec.straight();
        }
        if (value instanceof String s) {
            String normalized = s.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("notch") || normalized.equals("notched"))
                return UiEdgeSpec.notchedCenter(Math.min(length * 0.18, 18.0 * renderScale), Math.min(length * 0.10, 8.0 * renderScale));
        }
        return UiEdgeSpec.straight();
    }

    static UiBoxShape buildBoxShape(UiProps props, UiStyle style, String shape,
                                     double x, double y, double w, double h, float renderScale) {
        float radius = props.number("radius", style.radius()) * renderScale;
        float cut = props.number("cut", props.number("chamfer", style.radius())) * renderScale;

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

        UiCornerSpec tl = corner(props, "TL", "TopLeft", defaultCorner, radius, cut, renderScale);
        UiCornerSpec tr = corner(props, "TR", "TopRight", defaultCorner, radius, cut, renderScale);
        UiCornerSpec br = corner(props, "BR", "BottomRight", defaultCorner, radius, cut, renderScale);
        UiCornerSpec bl = corner(props, "BL", "BottomLeft", defaultCorner, radius, cut, renderScale);

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
        UiEdgeSpec top = edgeFromObject(edgeValue(edges, "top"), w, renderScale);
        UiEdgeSpec right = edgeFromObject(edgeValue(edges, "right"), h, renderScale);
        UiEdgeSpec bottom = edgeFromObject(edgeValue(edges, "bottom"), w, renderScale);
        UiEdgeSpec left = edgeFromObject(edgeValue(edges, "left"), h, renderScale);

        if (shape.equals("notched") || shape.equals("notch")) {
            top = UiEdgeSpec.notchedCenter(
                    props.get("notchWidth") != null ? props.number("notchWidth", 0.0f) * renderScale : (float) Math.min(w * 0.18, 18.0 * renderScale),
                    props.get("notchDepth") != null ? props.number("notchDepth", 0.0f) * renderScale : (float) Math.min(h * 0.28, 8.0 * renderScale));
        }
        if (props.get("edgeTop") != null || props.get("topEdge") != null)
            top = edgeFromObject(first(props, "edgeTop", "topEdge"), w, renderScale);
        if (props.get("edgeRight") != null || props.get("rightEdge") != null)
            right = edgeFromObject(first(props, "edgeRight", "rightEdge"), h, renderScale);
        if (props.get("edgeBottom") != null || props.get("bottomEdge") != null)
            bottom = edgeFromObject(first(props, "edgeBottom", "bottomEdge"), w, renderScale);
        if (props.get("edgeLeft") != null || props.get("leftEdge") != null)
            left = edgeFromObject(first(props, "edgeLeft", "leftEdge"), h, renderScale);

        return builder.edges(top, right, bottom, left).build();
    }

    static UiCompoundSdf buildCompoundSdf(UiProps props, String shape,
                                             double x, double y, double w, double h, float renderScale) {
        float smoothing = Math.max(0.0f, props.number("smoothing", props.number("smoothness", 10.0f)) * renderScale);
        if (shape.equals("smooth-squircle-union") || shape.equals("smooth_squircle_union")) {
            UiRect first = compoundRect(props.get("first"), x, y, renderScale,
                    UiRect.of(x, y + h * 0.16, w * 0.62, h * 0.68));
            UiRect second = compoundRect(props.get("second"), x, y, renderScale,
                    UiRect.of(x + w * 0.38, y + h * 0.16, w * 0.62, h * 0.68));
            float defaultExponent = props.number("exponent", 4.0f);
            return UiCompoundSdf.smoothSquircleUnion(
                    first, props.number("firstExponent", defaultExponent),
                    second, props.number("secondExponent", defaultExponent),
                    smoothing
            );
        }
        if (shape.equals("smooth-box-union") || shape.equals("smooth_box_union")
                || shape.equals("compound-sdf") || shape.equals("compound_sdf")) {
            UiRect first = compoundRect(props.get("first"), x, y, renderScale,
                    UiRect.of(x, y + h * 0.16, w * 0.62, h * 0.68));
            UiRect second = compoundRect(props.get("second"), x, y, renderScale,
                    UiRect.of(x + w * 0.38, y + h * 0.16, w * 0.62, h * 0.68));
            float defaultRadius = props.get("radius") != null ? props.number("radius", 0.0f) * renderScale : Math.min((float) w, (float) h) * 0.24f;
            return UiCompoundSdf.smoothBoxUnion(
                    first, props.get("firstRadius") != null ? props.number("firstRadius", 0.0f) * renderScale : defaultRadius,
                    second, props.get("secondRadius") != null ? props.number("secondRadius", 0.0f) * renderScale : defaultRadius,
                    smoothing
            );
        }

        UiCompoundSdf.Circle[] circles = readCompoundCircles(props.get("sources"), x, y, renderScale);
        if (circles.length == 0) {
            float defaultRadius = props.get("radius") != null ? props.number("radius", 0.0f) * renderScale : Math.min((float) w, (float) h) * 0.34f;
            float separation = props.get("separation") != null ? props.number("separation", 0.0f) * renderScale : Math.max(2.0f * renderScale, defaultRadius * 0.72f);
            double cx = x + (props.get("cx") != null ? props.number("cx", 0.0f) * renderScale : w * 0.5);
            double cy = y + (props.get("cy") != null ? props.number("cy", 0.0f) * renderScale : h * 0.5);
            circles = new UiCompoundSdf.Circle[]{
                    UiCompoundSdf.circle(cx - separation * 0.5, cy, defaultRadius),
                    UiCompoundSdf.circle(cx + separation * 0.5, cy, defaultRadius)
            };
        }
        return UiCompoundSdf.islandBlob(smoothing, circles);
    }

    private static UiCompoundSdf.Circle[] readCompoundCircles(Object value, double offsetX, double offsetY, float renderScale) {
        if (!(value instanceof Iterable<?> iterable)) return new UiCompoundSdf.Circle[0];
        UiCompoundSdf.Circle[] out = new UiCompoundSdf.Circle[UiCompoundSdf.MAX_CIRCLES];
        int count = 0;
        for (Object item : iterable) {
            if (count >= out.length) break;
            if (!(item instanceof Map<?, ?> map)) continue;
            double cx = offsetX + number(map.get("x"), 0.0f) * renderScale;
            double cy = offsetY + number(map.get("y"), 0.0f) * renderScale;
            double radius = Math.max(0.0, number(map.containsKey("radius") ? map.get("radius") : map.get("r"), 0.0f) * renderScale);
            if (radius <= 0.0) continue;
            out[count++] = UiCompoundSdf.circle(cx, cy, radius);
        }
        UiCompoundSdf.Circle[] result = new UiCompoundSdf.Circle[count];
        System.arraycopy(out, 0, result, 0, count);
        return result;
    }

    private static UiRect compoundRect(Object value, double offsetX, double offsetY, float renderScale, UiRect fallback) {
        if (!(value instanceof Map<?, ?> map)) return fallback;
        double x = offsetX + (map.get("x") != null ? number(map.get("x"), 0.0f) * renderScale : fallback.x() - offsetX);
        double y = offsetY + (map.get("y") != null ? number(map.get("y"), 0.0f) * renderScale : fallback.y() - offsetY);
        Object rawW = map.containsKey("width") ? map.get("width") : map.get("w");
        double w = Math.max(0.0, rawW != null ? number(rawW, 0.0f) * renderScale : fallback.width());
        Object rawH = map.containsKey("height") ? map.get("height") : map.get("h");
        double h = Math.max(0.0, rawH != null ? number(rawH, 0.0f) * renderScale : fallback.height());
        return UiRect.of(x, y, w, h);
    }

    static UiPrimitive buildPrimitive(UiProps props, UiStyle style, String shape,
                                       double x, double y, double w, double h, float renderScale) {
        String rawPreset = props.string("preset", shape).trim().toLowerCase(Locale.ROOT);
        UiPrimitive.Preset preset = switch (rawPreset) {
            case "chamfer", "chamfered", "bevel", "beveled" -> UiPrimitive.Preset.CHAMFERED;
            case "hex", "hexagon" -> UiPrimitive.Preset.HEXAGON;
            case "trapezoid-left", "trapezoid_left" -> UiPrimitive.Preset.TRAPEZOID_LEFT;
            case "trapezoid-right", "trapezoid_right" -> UiPrimitive.Preset.TRAPEZOID_RIGHT;
            case "parallelogram-left", "parallelogram_left" -> UiPrimitive.Preset.PARALLELOGRAM_LEFT;
            case "parallelogram-right", "parallelogram_right" -> UiPrimitive.Preset.PARALLELOGRAM_RIGHT;
            case "directional-left", "directional_left", "tech-left", "tech_left" -> UiPrimitive.Preset.DIRECTIONAL_LEFT;
            case "directional-right", "directional_right", "tech-right", "tech_right" -> UiPrimitive.Preset.DIRECTIONAL_RIGHT;
            case "notched-top", "notched_top" -> UiPrimitive.Preset.NOTCHED_TOP;
            case "stepped-left", "stepped_left" -> UiPrimitive.Preset.STEPPED_LEFT;
            case "stepped-right", "stepped_right" -> UiPrimitive.Preset.STEPPED_RIGHT;
            default -> UiPrimitive.Preset.RECT;
        };

        float radius = props.number("radius", style.radius()) * renderScale;
        float cut = props.number("cut", props.number("chamfer", Math.max(2.0f, style.radius()))) * renderScale;
        UiPrimitive.Builder builder = UiPrimitive.builder(x, y, w, h)
                .preset(preset)
                .cut(cut)
                .rounding(props.number("rounding", props.number("edgeRounding", 0.0f)) * renderScale);

        UiCornerSpec defaultCorner = preset == UiPrimitive.Preset.CHAMFERED
                ? UiCornerSpec.chamfered(cut)
                : UiCornerSpec.square();
        if (props.get("corners") != null
                || props.get("cornerTL") != null || props.get("cornerTopLeft") != null
                || props.get("cornerTR") != null || props.get("cornerTopRight") != null
                || props.get("cornerBR") != null || props.get("cornerBottomRight") != null
                || props.get("cornerBL") != null || props.get("cornerBottomLeft") != null) {
            builder.corner(UiPrimitive.Corner.TOP_LEFT,
                            corner(props, "TL", "TopLeft", defaultCorner, radius, cut, renderScale))
                    .corner(UiPrimitive.Corner.TOP_RIGHT,
                            corner(props, "TR", "TopRight", defaultCorner, radius, cut, renderScale))
                    .corner(UiPrimitive.Corner.BOTTOM_RIGHT,
                            corner(props, "BR", "BottomRight", defaultCorner, radius, cut, renderScale))
                    .corner(UiPrimitive.Corner.BOTTOM_LEFT,
                            corner(props, "BL", "BottomLeft", defaultCorner, radius, cut, renderScale));
        }

        Object edges = props.get("edges");
        applyPrimitiveEdge(builder, UiPrimitive.Side.TOP, edgeValue(edges, "top"), w, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.RIGHT, edgeValue(edges, "right"), h, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.BOTTOM, edgeValue(edges, "bottom"), w, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.LEFT, edgeValue(edges, "left"), h, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.TOP, first(props, "edgeTop", "topEdge"), w, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.RIGHT, first(props, "edgeRight", "rightEdge"), h, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.BOTTOM, first(props, "edgeBottom", "bottomEdge"), w, renderScale);
        applyPrimitiveEdge(builder, UiPrimitive.Side.LEFT, first(props, "edgeLeft", "leftEdge"), h, renderScale);

        applyPrimitiveCornerOffset(builder, props, UiPrimitive.Corner.TOP_LEFT, "TL", "TopLeft", renderScale);
        applyPrimitiveCornerOffset(builder, props, UiPrimitive.Corner.TOP_RIGHT, "TR", "TopRight", renderScale);
        applyPrimitiveCornerOffset(builder, props, UiPrimitive.Corner.BOTTOM_RIGHT, "BR", "BottomRight", renderScale);
        applyPrimitiveCornerOffset(builder, props, UiPrimitive.Corner.BOTTOM_LEFT, "BL", "BottomLeft", renderScale);
        return builder.build();
    }

    private static void applyPrimitiveEdge(UiPrimitive.Builder builder,
                                           UiPrimitive.Side side,
                                           Object value,
                                           double length,
                                           float renderScale) {
        if (value != null) builder.side(side, edgeFromObject(value, length, renderScale));
    }

    private static void applyPrimitiveCornerOffset(UiPrimitive.Builder builder,
                                                   UiProps props,
                                                   UiPrimitive.Corner corner,
                                                   String shortName,
                                                   String longName,
                                                   float renderScale) {
        Object offsets = props.get("cornerOffsets");
        Object value = cornerValue(offsets, shortName, longName);
        float dx = 0.0f;
        float dy = 0.0f;
        if (value instanceof Map<?, ?> map) {
            dx = number(map.get("x"), number(map.get("dx"), 0.0f));
            dy = number(map.get("y"), number(map.get("dy"), 0.0f));
        }
        dx = props.number("offset" + shortName + "X", props.number("offset" + longName + "X", dx));
        dy = props.number("offset" + shortName + "Y", props.number("offset" + longName + "Y", dy));
        dx *= renderScale;
        dy *= renderScale;
        if (Math.abs(dx) > 0.0001f || Math.abs(dy) > 0.0001f) builder.cornerOffset(corner, dx, dy);
    }

    private static Object first(UiProps props, String first, String second) {
        Object value = props.get(first);
        return value != null ? value : props.get(second);
    }

    private static float number(Object value, float fallback) {
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String text) {
            try {
                return Float.parseFloat(text);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

}
