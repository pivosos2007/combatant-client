/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import combatant.client.render.engine.renderer.ui.runtime.render.UiBlendSpec;
import combatant.client.render.engine.text.FontInfo;

import java.util.List;
import java.util.Locale;

public final class UiStyleParser {
    private static float number(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public ParsedStyle parse(String classString) {
        if (classString == null || classString.isBlank()) {
            return new ParsedStyle(List.of(), UiStyle.DEFAULT);
        }

        UiStyle.Builder builder = UiStyle.builder();
        ObjectArrayList<UiStyleToken> tokens = new ObjectArrayList<>();
        for (String part : classString.trim().split("\\s+")) {
            if (part.isBlank()) continue;
            UiStyleToken token = UiStyleToken.parse(part);
            tokens.add(token);
            apply(builder, token);
        }
        tokens.trim();
        return new ParsedStyle(tokens, builder.build());
    }

    private void apply(UiStyle.Builder builder, UiStyleToken token) {
        if (!token.variant().isBlank()) {
            return;
        }
        String raw = token.raw().toLowerCase(Locale.ROOT);
        if (raw.startsWith("w-")) {
            float value = number(token.value(), -1.0f);
            if (value >= 0.0f) builder.width(value);
            return;
        }
        if (raw.startsWith("h-")) {
            float value = number(token.value(), -1.0f);
            if (value >= 0.0f) builder.height(value);
            return;
        }
        if (raw.startsWith("min-w-")) {
            builder.minWidth(number(raw.substring("min-w-".length()), 0.0f));
            return;
        }
        if (raw.startsWith("min-h-")) {
            builder.minHeight(number(raw.substring("min-h-".length()), 0.0f));
            return;
        }
        if (raw.startsWith("max-w-")) {
            builder.maxWidth(number(raw.substring("max-w-".length()), Float.MAX_VALUE));
            return;
        }
        if (raw.startsWith("max-h-")) {
            builder.maxHeight(number(raw.substring("max-h-".length()), Float.MAX_VALUE));
            return;
        }
        if (raw.startsWith("p-")) {
            builder.padding(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("px-")) {
            builder.padding(number(token.value(), 0.0f), 0.0f);
            return;
        }
        if (raw.startsWith("py-")) {
            builder.padding(0.0f, number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("m-")) {
            builder.margin(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("mx-")) {
            builder.margin(number(token.value(), 0.0f), 0.0f);
            return;
        }
        if (raw.startsWith("my-")) {
            builder.margin(0.0f, number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("ml-")) {
            builder.marginLeft(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("mt-")) {
            builder.marginTop(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("mr-")) {
            builder.marginRight(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("mb-")) {
            builder.marginBottom(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("gap-")) {
            builder.gap(number(token.value(), 0.0f));
            return;
        }
        if ("grow".equals(raw)) {
            builder.grow(1.0f);
            return;
        }
        if (raw.startsWith("grow-")) {
            builder.grow(number(token.value(), 1.0f));
            return;
        }
        if ("absolute".equals(raw)) {
            builder.absolute(true);
            return;
        }
        if (raw.startsWith("x-")) {
            builder.offsetX(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("y-")) {
            builder.offsetY(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("align-")) {
            builder.align(UiAlign.parse(raw.substring("align-".length()), UiAlign.START));
            return;
        }
        if (raw.startsWith("justify-")) {
            builder.justify(UiJustify.parse(raw.substring("justify-".length()), UiJustify.START));
            return;
        }
        if (raw.startsWith("overflow-")) {
            builder.overflow(UiOverflow.parse(raw.substring("overflow-".length()), UiOverflow.VISIBLE));
            return;
        }
        if ("scroll".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL);
            return;
        }
        if ("scroll-x".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL_X);
            return;
        }
        if ("scroll-y".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL_Y);
            return;
        }
        if (raw.startsWith("rounded-")) {
            builder.radius(number(token.value(), 0.0f));
            return;
        }
        if (raw.startsWith("bg-")) {
            builder.backgroundColor(resolveColor(token.value(), 0x00000000));
            return;
        }
        if (raw.startsWith("text-backend-")) {
            builder.textBackend(token.value());
            return;
        }
        if (raw.startsWith("text-effect-")) {
            builder.textEffect(token.value(), 18);
            return;
        }
        if (raw.startsWith("text-max-")) {
            builder.maxTextWidth(number(raw.substring("text-max-".length()), 0.0f));
            return;
        }
        if (raw.startsWith("text-align-")) {
            builder.textAlign(raw.substring("text-align-".length()));
            return;
        }
        if (raw.startsWith("text-")) {
            builder.textColor(resolveColor(token.value(), 0xFFFFFFFF));
            return;
        }
        if (raw.startsWith("border-")) {
            builder.strokeColor(resolveColor(token.value(), 0x665A5A5A));
            return;
        }
        if (raw.startsWith("font-")) {
            applyFont(builder, token.value());
            return;
        }
        if (raw.startsWith("cursor-")) {
            builder.cursor(token.value());
            return;
        }
        if (raw.startsWith("blend-")) {
            builder.blend(UiBlendSpec.parse(token.value()));
            return;
        }
        if (raw.startsWith("blur-alpha-")) {
            builder.blur(8.0f, 1.0f, number(raw.substring("blur-alpha-".length()), 0.35f));
            return;
        }
        if (raw.startsWith("blur-quality-")) {
            builder.blur(number(raw.substring("blur-quality-".length()), 8.0f), 1.0f, 0.35f);
            return;
        }
        if ("blur".equals(raw)) {
            builder.blur(true);
            return;
        }
        if ("glass".equals(raw) || "liquid-glass".equals(raw)) {
            builder.liquidGlass(true);
            return;
        }
        if ("clip".equals(raw)) {
            builder.clip(true);
            return;
        }
        if ("marquee".equals(raw)) {
            builder.marquee(true);
            return;
        }
        if ("shadow-compact".equals(raw)) {
            builder.shadow(0x30000000, 4.5f, 0.10f);
            return;
        }
        if ("shadow-panel".equals(raw)) {
            builder.shadow(0x26000000, 6.0f, 0.0f);
            return;
        }
        if ("shadow-soft".equals(raw) || "shadow".equals(raw)) {
            builder.shadow(0x66000000, 10.0f, 0.18f);
            return;
        }
        if ("shadow-text".equals(raw) || "text-shadow".equals(raw)) {
            builder.textShadow(true);
            return;
        }
        if ("ellipsis".equals(raw)) {
            builder.ellipsis(true);
        }
    }

    private int resolveColor(String value, int fallback) {
        String name = value;
        float alpha = -1.0f;
        int slash = value.indexOf('/');
        if (slash >= 0) {
            name = value.substring(0, slash);
            alpha = number(value.substring(slash + 1), 100.0f) / 100.0f;
        }
        int color = name.startsWith("#")
                ? UiColor.parse(name, fallback)
                : UiThemeRegistry.current().color(name, fallback);
        return alpha >= 0.0f ? UiColor.multiplyAlpha(color, alpha) : color;
    }

    private void applyFont(UiStyle.Builder builder, String value) {
        String[] parts = value.split("-");
        if (parts.length == 0) return;
        int end = parts.length;
        float scale = 1.0f;
        FontInfo.Type type = FontInfo.Type.Regular;

        float tailScale = number(parts[end - 1], Float.NaN);
        if (!Float.isNaN(tailScale)) {
            scale = tailScale;
            end--;
        }

        if (end <= 0) return;

        String tail = parts[end - 1].toLowerCase(Locale.ROOT);
        if ("bolditalic".equals(tail) || "bold_italic".equals(tail)) {
            type = FontInfo.Type.BoldItalic;
            end--;
        } else if ("italic".equals(tail)) {
            type = FontInfo.Type.Italic;
            end--;
            if (end > 0 && "bold".equals(parts[end - 1].toLowerCase(Locale.ROOT))) {
                type = FontInfo.Type.BoldItalic;
                end--;
            }
        } else if ("bold".equals(tail)) {
            type = FontInfo.Type.Bold;
            end--;
        } else if ("regular".equals(tail)) {
            end--;
        }

        if (end <= 0) return;
        StringBuilder family = new StringBuilder(parts[0]);
        for (int i = 1; i < end; i++) {
            family.append('-').append(parts[i]);
        }
        builder.font(family.toString(), type).textScale(scale);
    }

    public record ParsedStyle(List<UiStyleToken> tokens, UiStyle style) {
    }
}
