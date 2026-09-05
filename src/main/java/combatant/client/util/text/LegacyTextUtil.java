/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

public enum LegacyTextUtil {
    ;

    public static String stripLegacy(String s) {
        if (s == null || s.isEmpty()) return "";
        return stripSectionCodes(ampersandToSection(s));
    }

    private static String stripSectionCodes(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                char code = s.charAt(i + 1);
                if ((code == 'x' || code == 'X') && i + 13 < s.length()) {
                    boolean ok = true;
                    for (int j = i + 2, k = 0; k < 6; k++, j += 2) {
                        if (j + 1 >= s.length() || s.charAt(j) != '§' || !isHex(s.charAt(j + 1))) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        i += 13;
                        continue;
                    }
                }
                if (isLegacyCode(code) || Character.toLowerCase(code) == 'g' || code == '/' || isPotentialLegacyCode(code)) {
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    /**
     * Конвертирует любые "§"-коды внутри сегментов текста в реальные стили Text/Style.
     */
    public static Component convertLegacyCodes(Component in) {
        if (in == null) return Component.empty();

        final MutableComponent out = Component.empty();
        final boolean[] changed = {false};

        in.visit((style, string) -> {
            if (string == null || string.isEmpty()) return Optional.empty();

            // Гибрид: конвертим &-коды в §-коды ПО СЕГМЕНТАМ, сохраняя Style (и hover/click).
            String s = string;
            if (s.indexOf('&') >= 0) {
                String conv = ampersandToSection(s);
                if (!conv.equals(s)) {
                    s = conv;
                    changed[0] = true;
                }
            }

            if (s.indexOf('§') < 0) {
                out.append(Component.literal(s).setStyle(style == null ? Style.EMPTY : style));
                return Optional.empty();
            }

            changed[0] = true;
            out.append(parseLegacySegment(s, style == null ? Style.EMPTY : style));
            return Optional.empty();
        }, Style.EMPTY);

        return changed[0] ? out : in;
    }

    /**
     * Also handles malformed server components that split a legacy marker and
     * its code (for example {@code "&"} and {@code "3Name"}) across siblings.
     * Native component styles are preserved unless such a leaked marker remains.
     */
    public static Component convertLegacyCodesRobust(Component in) {
        Component converted = convertLegacyCodes(in);
        String flattened = converted.getString();
        if (!containsLegacyMarker(flattened)) return converted;
        return convertLegacyCodes(Component.literal(flattened));
    }

    private static boolean containsLegacyMarker(String value) {
        if (value == null || value.length() < 2) return false;
        for (int i = 0; i + 1 < value.length(); i++) {
            char marker = value.charAt(i);
            if (marker != '&' && marker != '\u00A7') continue;
            char code = Character.toLowerCase(value.charAt(i + 1));
            if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'z') || code == '/') {
                return true;
            }
        }
        return false;
    }

    private static MutableComponent parseLegacySegment(String s, Style base) {
        MutableComponent out = Component.empty();
        Style cur = base;
        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (ch == '§' && i + 1 < s.length()) {
                char code = s.charAt(i + 1);

                // flush буфер до кода
                if (buf.length() > 0) {
                    out.append(Component.literal(buf.toString()).setStyle(cur));
                    buf.setLength(0);
                }

                // hex-цвет: §x§R§R§G§G§B§B
                if (code == 'x' || code == 'X') {
                    int j = i + 2;
                    StringBuilder hex = new StringBuilder(6);
                    boolean ok = true;
                    for (int k = 0; k < 6; k++) {
                        if (j + 1 >= s.length() || s.charAt(j) != '§') {
                            ok = false;
                            break;
                        }
                        char h = s.charAt(j + 1);
                        hex.append(h);
                        j += 2;
                    }
                    if (ok) {
                        try {
                            int rgb = Integer.parseInt(hex.toString(), 16);
                            cur = cur.withColor(TextColor.fromRgb(rgb));
                            i = j - 1; // перескочили hex-блок
                            continue;
                        } catch (NumberFormatException ignored) {
                            // падать не надо — просто обработаем как обычный текст ниже
                        }
                    }
                }

                ChatFormatting fmt = ChatFormatting.getByCode(code);
                if (fmt != null) {
                    if (fmt == ChatFormatting.RESET) {
                        cur = base;
                    } else if (isColor(fmt)) {
                        // цвет по vanilla сбрасывает предыдущие декорации => возвращаемся к base
                        cur = base.applyFormat(fmt);
                    } else {
                        cur = cur.applyFormat(fmt);
                    }
                    i++; // пропустить символ кода
                    continue;
                }

                char lowered = Character.toLowerCase(code);
                if (lowered == 'g') {
                    // Common Bedrock/plugin extension: Minecoin Gold.
                    cur = base.withColor(TextColor.fromRgb(0xDDD605));
                    i++;
                    continue;
                }
                if (lowered == '/') {
                    // Some scoreboards use &/ or §/ as an end/reset marker for custom color markup.
                    cur = base;
                    i++;
                    continue;
                }
                if (isPotentialLegacyCode(code)) {
                    // Do not leak unsupported plugin color tags like §h§/, §q§/, etc. into rendered text.
                    i++;
                    continue;
                }

                // неизвестный не-code символ — оставим section sign как обычный текст
                buf.append('§');
                continue;
            }

            buf.append(ch);
        }

        if (buf.length() > 0) {
            out.append(Component.literal(buf.toString()).setStyle(cur));
        }
        return out;
    }

    private static boolean isColor(ChatFormatting fmt) {
        return fmt.ordinal() <= ChatFormatting.WHITE.ordinal();
    }

    public static String ampersandToSection(String s) {
        if (s == null || s.isEmpty()) return s;

        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '&' && i + 1 < s.length()) {
                char code = s.charAt(i + 1);

                // Hex legacy: &#RRGGBB -> §x§R§R§G§G§B§B
                if (code == '#' && i + 7 < s.length()) {
                    String hex = s.substring(i + 2, i + 8);
                    boolean ok = true;
                    for (int k = 0; k < hex.length(); k++) {
                        if (!isHex(hex.charAt(k))) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        out.append('§').append('x');
                        for (int k = 0; k < hex.length(); k++) {
                            out.append('§').append(hex.charAt(k));
                        }
                        i += 7;
                        continue;
                    }
                }

                // Hex legacy: &x&R&R&G&G&B&B -> §x§R§R§G§G§B§B
                if ((code == 'x' || code == 'X') && i + 13 < s.length()) {
                    int j = i + 2;
                    StringBuilder hex = new StringBuilder(6);
                    boolean ok = true;
                    for (int k = 0; k < 6; k++) {
                        if (j + 1 >= s.length() || s.charAt(j) != '&' || !isHex(s.charAt(j + 1))) {
                            ok = false;
                            break;
                        }
                        hex.append(s.charAt(j + 1));
                        j += 2;
                    }
                    if (ok) {
                        out.append('§').append('x');
                        for (int k = 0; k < hex.length(); k++) {
                            out.append('§').append(hex.charAt(k));
                        }
                        i = j - 1;
                        continue;
                    }
                }

                if (isLegacyCode(code) || Character.toLowerCase(code) == 'g' || code == '/' || isPotentialLegacyCode(code)) {
                    out.append('§').append(code);
                    i++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static boolean isLegacyCode(char ch) {
        char c = Character.toLowerCase(ch);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o') || c == 'r';
    }

    private static boolean isPotentialLegacyCode(char ch) {
        char c = Character.toLowerCase(ch);
        return (c >= 'a' && c <= 'z') || c == '/';
    }

    private static boolean isHex(char ch) {
        char c = Character.toLowerCase(ch);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }
}
