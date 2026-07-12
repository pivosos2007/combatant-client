/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import combatant.client.render.engine.color.ColorUtils;

/**
 * Keeps color as hex-string and provides access as ARGB int.
 * Supported formats: #RRGGBB, #AARRGGBB, 0xRRGGBB, 0xAARRGGBB.
 */
public class RGBAColorValue extends ConfigValue<String> implements ColorValue {

    private static final int RAINBOW_SPEED = 18;

    public RGBAColorValue(String name, String defHex) {
        super(name, normalize(defHex));
    }

    public static boolean isRainbowValue(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        return t.startsWith("RNB") || t.startsWith("RAINBOW");
    }

    public static boolean isRgbaValue(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        return t.startsWith("RGBA");
    }

    public static String toRainbow(String hex) {
        int argb = parseToArgb(hex);
        return "RNB#" + String.format("%08X", argb);
    }

    public static String rainbowFallbackHex(String s) {
        int argb = rainbowFallbackArgb(s);
        return String.format("#%02X%02X%02X%02X",
                (argb >>> 24) & 0xFF,
                (argb >>> 16) & 0xFF,
                (argb >>> 8) & 0xFF,
                argb & 0xFF);
    }

    private static String normalize(String s) {
        if (s == null || s.isEmpty()) return "#FFFFFFFF"; // white
        s = s.trim();
        if (isRainbowValue(s)) return normalizeRainbow(s);
        boolean rgba = isRgbaValue(s);
        if (rgba) {
            s = stripRgbaPrefix(s);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (!s.startsWith("#")) s = "#" + s;
        s = s.toUpperCase();
        return rgba ? "RGBA" + s : s;
    }

    private static String normalizeRainbow(String s) {
        if (s == null) return "RNB";
        String upper = s.trim().toUpperCase();
        String tail = upper.startsWith("RAINBOW") ? upper.substring(7) : upper.substring(3);
        tail = tail.trim();
        if (!tail.isEmpty() && (tail.charAt(0) == ':' || tail.charAt(0) == '=')) {
            tail = tail.substring(1).trim();
        }
        if (tail.startsWith("#")) tail = tail.substring(1);
        if (tail.startsWith("0X")) tail = tail.substring(2);
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char c = tail.charAt(i);
            boolean digit = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F');
            if (digit) hex.append(c);
        }
        if (hex.length() == 6) hex.insert(0, "FF");
        if (hex.length() == 8) {
            return "RNB#" + hex;
        }
        return "RNB";
    }

    private static int rainbowFallbackArgb(String s) {
        if (s == null) return 0xFFFFFFFF;
        int hash = s.indexOf('#');
        if (hash >= 0 && hash + 1 < s.length()) {
            return parseToArgb(s.substring(hash));
        }
        return 0xFFFFFFFF;
    }

    private static int parseToArgb(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFFFFFFFF;
        boolean rgba = isRgbaValue(hex);
        String cleaned = rgba ? stripRgbaPrefix(hex) : hex;
        String s = cleaned.startsWith("#") ? cleaned.substring(1) : cleaned;
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        long v;
        try {
            v = Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }

        if (s.length() == 6) {
            return (int) (0xFF000000L | v); // add full alpha
        } else if (s.length() == 8) {
            int value = (int) v;
            if (!rgba) {
                return value; // AARRGGBB
            }
            int r = (value >>> 24) & 0xFF;
            int g = (value >>> 16) & 0xFF;
            int b = (value >>> 8) & 0xFF;
            int a = value & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        return 0xFFFFFFFF;
    }

    private static String stripRgbaPrefix(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 4 && t.substring(0, 4).equalsIgnoreCase("RGBA")) {
            t = t.substring(4).trim();
            if (!t.isEmpty()) {
                char c = t.charAt(0);
                if (c == ':' || c == '=') {
                    t = t.substring(1).trim();
                }
            }
        }
        return t;
    }

    @Override
    public Object toJson() {
        return value;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s) value = normalize(s);
    }

    public int getArgb() {
        if (isRainbowValue(value)) {
            int fallback = rainbowFallbackArgb(value);
            int alpha = (fallback >>> 24) & 0xFF;
            float opacity = alpha / 255.0f;
            return ColorUtils
                    .rainbow(RAINBOW_SPEED, 0, 1f, 1f, opacity)
                    .getRGB();
        }
        return parseToArgb(value);
    }

    @Override
    public boolean supportsAlpha() {
        return true;
    }

    @Override
    public boolean isRainbow() {
        return isRainbowValue(value);
    }

    @Override
    public String toRainbowValue() {
        return toRainbow(value);
    }

    @Override
    public String rainbowFallbackHex() {
        return rainbowFallbackHex(value);
    }
}
