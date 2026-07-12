/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import combatant.client.render.engine.color.ColorUtils;

/**
 * Цвет без альфы. Хранится как hex "#RRGGBB" и возвращается с полной альфой (0xFF) при запросе ARGB.
 * Допустимые входные форматы: #RRGGBB, #AARRGGBB, 0xRRGGBB, 0xAARRGGBB.
 */
public class RGBColorValue extends ConfigValue<String> implements ColorValue {

    private static final int RAINBOW_SPEED = 18;

    public RGBColorValue(String name, String defHex) {
        super(name, normalize(defHex));
    }

    public static boolean isRainbowValue(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        return t.startsWith("RNB") || t.startsWith("RAINBOW");
    }

    public static String toRainbow(String hex) {
        int rgb = parseRgb(hex);
        return "RNB#" + String.format("%06X", rgb & 0xFFFFFF);
    }

    public static String rainbowFallbackHex(String s) {
        int rgb = rainbowFallbackRgb(s);
        return String.format("#%02X%02X%02X",
                (rgb >>> 16) & 0xFF,
                (rgb >>> 8) & 0xFF,
                rgb & 0xFF);
    }

    private static String normalize(String s) {
        if (s == null || s.isEmpty()) return "#FFFFFF"; // white RGB
        s = s.trim();
        if (isRainbowValue(s)) return normalizeRainbow(s);
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.startsWith("#")) s = s.substring(1);

        // s is now hex without prefix; allow 6 or 8 digits, drop alpha if present
        if (s.length() == 8) {
            s = s.substring(2); // drop AA
        }
        if (s.length() != 6) {
            return "#FFFFFF";
        }
        return ("#" + s).toUpperCase();
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
        if (hex.length() >= 6) {
            String tail6 = hex.substring(hex.length() - 6);
            return "RNB#" + tail6;
        }
        return "RNB";
    }

    private static int rainbowFallbackRgb(String s) {
        if (s == null) return 0xFFFFFF;
        int hash = s.indexOf('#');
        if (hash >= 0 && hash + 1 < s.length()) {
            return parseRgb(s.substring(hash));
        }
        return 0xFFFFFF;
    }

    private static int parseRgb(String hex) {
        if (hex == null || hex.isEmpty()) return 0xFFFFFF;
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Integer.parseUnsignedInt(s, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    @Override
    public Object toJson() {
        return value;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s) value = normalize(s);
    }

    /**
     * Возвращает цвет как ARGB с полной альфой (0xFF).
     */
    public int getArgb() {
        if (isRainbowValue(value)) {
            return ColorUtils
                    .rainbow(RAINBOW_SPEED, 0, 1f, 1f, 1f)
                    .getRGB();
        }
        return 0xFF000000 | parseRgb(value);
    }

    @Override
    public boolean supportsAlpha() {
        return false;
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
