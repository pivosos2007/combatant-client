/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.svg;

import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

enum SvgStyleUtil {
    ;
    private static final Map<String, Integer> NAMED_COLORS = namedColors();

    static void applyElementStyle(SvgStyle style, Element element) {
        applyProperty(style, "fill", element.getAttribute("fill"));
        applyProperty(style, "stroke", element.getAttribute("stroke"));
        applyProperty(style, "stroke-width", element.getAttribute("stroke-width"));
        applyProperty(style, "stroke-linecap", element.getAttribute("stroke-linecap"));
        applyProperty(style, "stroke-linejoin", element.getAttribute("stroke-linejoin"));
        applyProperty(style, "stroke-miterlimit", element.getAttribute("stroke-miterlimit"));
        applyProperty(style, "fill-opacity", element.getAttribute("fill-opacity"));
        applyProperty(style, "stroke-opacity", element.getAttribute("stroke-opacity"));
        applyProperty(style, "opacity", element.getAttribute("opacity"));
        applyProperty(style, "fill-rule", element.getAttribute("fill-rule"));
        applyProperty(style, "color", element.getAttribute("color"));
        applyProperty(style, "display", element.getAttribute("display"));
        applyProperty(style, "visibility", element.getAttribute("visibility"));

        String inline = element.getAttribute("style");
        if (inline != null && !inline.isBlank()) {
            String[] parts = inline.split(";");
            for (String part : parts) {
                int i = part.indexOf(':');
                if (i <= 0) continue;
                String key = part.substring(0, i).trim().toLowerCase(Locale.ROOT);
                String value = part.substring(i + 1).trim();
                applyProperty(style, key, value);
            }
        }
    }

    private static void applyProperty(SvgStyle style, String key, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return;
        String value = rawValue.trim();
        int currentColor = style.currentColor;
        switch (key) {
            case "fill" -> style.fill = parsePaint(value, currentColor);
            case "stroke" -> style.stroke = parsePaint(value, currentColor);
            case "stroke-width" -> style.strokeWidth = Math.max(0.0f, (float) parseLength(value, style.strokeWidth));
            case "stroke-linecap" -> style.lineCap = switch (value.toLowerCase(Locale.ROOT)) {
                case "round" -> BasicStroke.CAP_ROUND;
                case "square" -> BasicStroke.CAP_SQUARE;
                default -> BasicStroke.CAP_BUTT;
            };
            case "stroke-linejoin" -> style.lineJoin = switch (value.toLowerCase(Locale.ROOT)) {
                case "round" -> BasicStroke.JOIN_ROUND;
                case "bevel" -> BasicStroke.JOIN_BEVEL;
                default -> BasicStroke.JOIN_MITER;
            };
            case "stroke-miterlimit" -> style.miterLimit = Math.max(1.0f, (float) parseLength(value, style.miterLimit));
            case "fill-opacity" -> style.fillOpacity = clamp01(parseOpacity(value, style.fillOpacity));
            case "stroke-opacity" -> style.strokeOpacity = clamp01(parseOpacity(value, style.strokeOpacity));
            case "opacity" -> style.groupOpacity = clamp01(style.groupOpacity * parseOpacity(value, 1.0f));
            case "fill-rule" -> style.fillRule = "evenodd".equalsIgnoreCase(value) ? "evenodd" : "nonzero";
            case "color" -> {
                Integer p = parsePaint(value, currentColor);
                if (p != null) style.currentColor = p;
            }
            case "display" -> {
                if ("none".equalsIgnoreCase(value)) style.visible = false;
            }
            case "visibility" -> {
                if ("hidden".equalsIgnoreCase(value) || "collapse".equalsIgnoreCase(value)) style.visible = false;
            }
            default -> {
            }
        }
    }

    static int resolvePaint(Integer paint, float channelOpacity, float groupOpacity, SvgRenderOptions options) {
        if (paint == null) return 0;
        int base = paint;
        int baseA = (base >>> 24) & 0xFF;
        float alphaMul = clamp01(channelOpacity) * clamp01(groupOpacity) * clamp01(options.alpha());

        if (options.colorMode() == SvgColorMode.OVERRIDE) {
            int o = options.overrideArgb();
            int oa = (o >>> 24) & 0xFF;
            int a = clamp255(Math.round(baseA * (oa / 255.0f) * alphaMul));
            return (a << 24) | (o & 0x00FFFFFF);
        }
        if (options.colorMode() == SvgColorMode.GRADIENT_LINEAR) {
            int start = options.gradientStartArgb();
            int sa = (start >>> 24) & 0xFF;
            int a = clamp255(Math.round(baseA * (sa / 255.0f) * alphaMul));
            return (a << 24) | (start & 0x00FFFFFF);
        }

        int a = clamp255(Math.round(baseA * alphaMul));
        return (a << 24) | (base & 0x00FFFFFF);
    }

    static AffineTransform parseTransform(String raw) {
        AffineTransform out = new AffineTransform();
        if (raw == null || raw.isBlank()) return out;

        int i = 0;
        int n = raw.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(raw.charAt(i))) i++;
            if (i >= n) break;
            int nameStart = i;
            while (i < n && Character.isLetter(raw.charAt(i))) i++;
            if (i >= n) break;
            String name = raw.substring(nameStart, i).toLowerCase(Locale.ROOT);
            while (i < n && Character.isWhitespace(raw.charAt(i))) i++;
            if (i >= n || raw.charAt(i) != '(') break;
            i++;
            int argsStart = i;
            int depth = 1;
            while (i < n && depth > 0) {
                char c = raw.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                i++;
            }
            int argsEnd = Math.max(argsStart, i - 1);
            double[] args = parseNumberList(raw.substring(argsStart, argsEnd));
            switch (name) {
                case "matrix" -> {
                    if (args.length >= 6)
                        out.concatenate(new AffineTransform(args[0], args[1], args[2], args[3], args[4], args[5]));
                }
                case "translate" -> {
                    if (args.length == 1) out.translate(args[0], 0.0);
                    else if (args.length >= 2) out.translate(args[0], args[1]);
                }
                case "scale" -> {
                    if (args.length == 1) out.scale(args[0], args[0]);
                    else if (args.length >= 2) out.scale(args[0], args[1]);
                }
                case "rotate" -> {
                    if (args.length == 1) out.rotate(Math.toRadians(args[0]));
                    else if (args.length >= 3) out.rotate(Math.toRadians(args[0]), args[1], args[2]);
                }
                case "skewx" -> {
                    if (args.length >= 1) out.shear(Math.tan(Math.toRadians(args[0])), 0.0);
                }
                case "skewy" -> {
                    if (args.length >= 1) out.shear(0.0, Math.tan(Math.toRadians(args[0])));
                }
                default -> {
                }
            }
        }
        return out;
    }

    static double parseLength(String raw, double fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith("px") || s.endsWith("pt") || s.endsWith("pc")
                || s.endsWith("cm") || s.endsWith("mm") || s.endsWith("in")
                || s.endsWith("em") || s.endsWith("ex")) {
            s = s.substring(0, s.length() - 2).trim();
        } else if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.isEmpty()) return fallback;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static double[] parseNumberList(String raw) {
        if (raw == null || raw.isBlank()) return new double[0];
        ArrayList<Double> out = new ArrayList<>(8);
        int i = 0;
        int n = raw.length();
        while (i < n) {
            while (i < n) {
                char c = raw.charAt(i);
                if (Character.isWhitespace(c) || c == ',') i++;
                else break;
            }
            if (i >= n) break;
            int start = i;
            if (raw.charAt(i) == '+' || raw.charAt(i) == '-') i++;
            boolean hasDot = false;
            while (i < n) {
                char c = raw.charAt(i);
                if (Character.isDigit(c)) {
                    i++;
                    continue;
                }
                if (c == '.' && !hasDot) {
                    hasDot = true;
                    i++;
                    continue;
                }
                break;
            }
            if (i < n && (raw.charAt(i) == 'e' || raw.charAt(i) == 'E')) {
                int e = i + 1;
                if (e < n && (raw.charAt(e) == '+' || raw.charAt(e) == '-')) e++;
                boolean expDigits = false;
                while (e < n && Character.isDigit(raw.charAt(e))) {
                    expDigits = true;
                    e++;
                }
                if (expDigits) i = e;
            }
            if (start == i) {
                i++;
                continue;
            }
            try {
                out.add(Double.parseDouble(raw.substring(start, i)));
            } catch (NumberFormatException ignored) {
            }
        }
        double[] vals = new double[out.size()];
        for (int j = 0; j < out.size(); j++) vals[j] = out.get(j);
        return vals;
    }

    private static Integer parsePaint(String raw, int currentColor) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty() || "none".equals(v) || v.startsWith("url(")) return null;
        if ("currentcolor".equals(v)) return currentColor;
        if ("transparent".equals(v)) return 0x00000000;

        if (v.startsWith("#")) {
            String hex = v.substring(1).trim();
            try {
                return switch (hex.length()) {
                    case 3 -> {
                        int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                        int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                        int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                        yield (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                    case 4 -> {
                        int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                        int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                        int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                        int a = Integer.parseInt(hex.substring(3, 4) + hex.substring(3, 4), 16);
                        yield (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    case 6 -> (0xFF << 24) | Integer.parseInt(hex, 16);
                    case 8 -> {
                        int rgba = (int) Long.parseLong(hex, 16);
                        int r = (rgba >>> 24) & 0xFF;
                        int g = (rgba >>> 16) & 0xFF;
                        int b = (rgba >>> 8) & 0xFF;
                        int a = rgba & 0xFF;
                        yield (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    default -> null;
                };
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (v.startsWith("rgb(") || v.startsWith("rgba(")) {
            int open = v.indexOf('(');
            int close = v.lastIndexOf(')');
            if (open < 0 || close <= open) return null;
            String[] chunks = v.substring(open + 1, close).split("[,\\s/]+");
            if (chunks.length < 3) return null;
            int r = parseColorComponent(chunks[0]);
            int g = parseColorComponent(chunks[1]);
            int b = parseColorComponent(chunks[2]);
            int a = 255;
            if (chunks.length >= 4) {
                a = clamp255((int) Math.round(255.0 * parseOpacity(chunks[3], 1.0f)));
            }
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        return NAMED_COLORS.get(v);
    }

    private static int parseColorComponent(String s) {
        String v = s.trim();
        if (v.endsWith("%")) {
            double p = parseLength(v.substring(0, v.length() - 1), 0.0);
            return clamp255((int) Math.round(2.55 * p));
        }
        return clamp255((int) Math.round(parseLength(v, 0.0)));
    }

    private static float parseOpacity(String raw, float fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String s = raw.trim();
        try {
            if (s.endsWith("%")) {
                double p = Double.parseDouble(s.substring(0, s.length() - 1).trim());
                return clamp01((float) (p / 100.0));
            }
            return clamp01(Float.parseFloat(s));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        return Math.min(v, 1.0f);
    }

    private static Map<String, Integer> namedColors() {
        Map<String, Integer> m = new HashMap<>();
        m.put("black", 0xFF000000);
        m.put("white", 0xFFFFFFFF);
        m.put("gray", 0xFF808080);
        m.put("silver", 0xFFC0C0C0);
        m.put("red", 0xFFFF0000);
        m.put("green", 0xFF008000);
        m.put("lime", 0xFF00FF00);
        m.put("blue", 0xFF0000FF);
        m.put("yellow", 0xFFFFFF00);
        m.put("cyan", 0xFF00FFFF);
        m.put("aqua", 0xFF00FFFF);
        m.put("magenta", 0xFFFF00FF);
        m.put("fuchsia", 0xFFFF00FF);
        m.put("orange", 0xFFFFA500);
        m.put("purple", 0xFF800080);
        m.put("teal", 0xFF008080);
        m.put("navy", 0xFF000080);
        return m;
    }
}
