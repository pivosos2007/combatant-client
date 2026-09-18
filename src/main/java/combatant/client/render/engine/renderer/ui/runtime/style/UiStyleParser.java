/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
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
            if (!apply(builder, token) && UiRuntimeValidation.enabled()) {
                throw UiRuntimeValidation.invalid("Unknown UI style token '" + token.raw() + "'.");
            }
        }
        tokens.trim();
        return new ParsedStyle(tokens, builder.build());
    }

    private boolean apply(UiStyle.Builder builder, UiStyleToken token) {
        if (!token.variant().isBlank()) {
            return true;
        }
        String raw = token.raw().toLowerCase(Locale.ROOT);
        if (raw.startsWith("w-")) {
            float value = number(token.value(), -1.0f);
            if (value >= 0.0f) builder.width(value);
            return true;
        }
        if (raw.startsWith("h-")) {
            float value = number(token.value(), -1.0f);
            if (value >= 0.0f) builder.height(value);
            return true;
        }
        if (raw.startsWith("min-w-")) {
            builder.minWidth(number(raw.substring("min-w-".length()), 0.0f));
            return true;
        }
        if (raw.startsWith("min-h-")) {
            builder.minHeight(number(raw.substring("min-h-".length()), 0.0f));
            return true;
        }
        if (raw.startsWith("max-w-")) {
            builder.maxWidth(number(raw.substring("max-w-".length()), Float.MAX_VALUE));
            return true;
        }
        if (raw.startsWith("max-h-")) {
            builder.maxHeight(number(raw.substring("max-h-".length()), Float.MAX_VALUE));
            return true;
        }
        if (raw.startsWith("p-")) {
            builder.padding(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("px-")) {
            float value = number(token.value(), 0.0f);
            builder.paddingLeft(value).paddingRight(value);
            return true;
        }
        if (raw.startsWith("py-")) {
            float value = number(token.value(), 0.0f);
            builder.paddingTop(value).paddingBottom(value);
            return true;
        }
        if (raw.startsWith("pl-")) {
            builder.paddingLeft(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("pt-")) {
            builder.paddingTop(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("pr-")) {
            builder.paddingRight(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("pb-")) {
            builder.paddingBottom(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("m-")) {
            builder.margin(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("mx-")) {
            float value = number(token.value(), 0.0f);
            builder.marginLeft(value).marginRight(value);
            return true;
        }
        if (raw.startsWith("my-")) {
            float value = number(token.value(), 0.0f);
            builder.marginTop(value).marginBottom(value);
            return true;
        }
        if (raw.startsWith("ml-")) {
            builder.marginLeft(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("mt-")) {
            builder.marginTop(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("mr-")) {
            builder.marginRight(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("mb-")) {
            builder.marginBottom(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("gap-")) {
            builder.gap(number(token.value(), 0.0f));
            return true;
        }
        if ("flex".equals(raw)) {
            builder.display(UiDisplay.FLEX);
            return true;
        }
        if ("flex-row".equals(raw)) {
            builder.display(UiDisplay.FLEX).flexDirection(UiFlexDirection.ROW);
            return true;
        }
        if ("block".equals(raw)) {
            builder.display(UiDisplay.BLOCK);
            return true;
        }
        if ("flex-col".equals(raw) || "flex-column".equals(raw)) {
            builder.display(UiDisplay.FLEX).flexDirection(UiFlexDirection.COLUMN);
            return true;
        }
        if ("grow".equals(raw)) {
            builder.grow(1.0f);
            return true;
        }
        if (raw.startsWith("grow-")) {
            builder.grow(number(token.value(), 1.0f));
            return true;
        }
        if ("absolute".equals(raw)) {
            builder.absolute(true);
            return true;
        }
        if (raw.startsWith("x-")) {
            builder.offsetX(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("y-")) {
            builder.offsetY(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("right-")) {
            builder.offsetRight(number(raw.substring("right-".length()), 0.0f));
            return true;
        }
        if (raw.startsWith("bottom-")) {
            builder.offsetBottom(number(raw.substring("bottom-".length()), 0.0f));
            return true;
        }
        if (raw.startsWith("align-")) {
            UiAlign align = UiAlign.parse(raw.substring("align-".length()), null);
            if (align == null) return false;
            builder.align(align);
            return true;
        }
        if (raw.startsWith("justify-")) {
            UiJustify justify = UiJustify.parse(raw.substring("justify-".length()), null);
            if (justify == null) return false;
            builder.justify(justify);
            return true;
        }
        if (raw.startsWith("overflow-")) {
            UiOverflow overflow = UiOverflow.parse(raw.substring("overflow-".length()), null);
            if (overflow == null) return false;
            builder.overflow(overflow);
            return true;
        }
        if ("scroll".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL);
            return true;
        }
        if ("scroll-x".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL_X);
            return true;
        }
        if ("scroll-y".equals(raw)) {
            builder.overflow(UiOverflow.SCROLL_Y);
            return true;
        }
        if (raw.startsWith("rounded-")) {
            builder.radius(number(token.value(), 0.0f));
            return true;
        }
        if (raw.startsWith("bg-")) {
            builder.backgroundColor(resolveColor(token.value(), 0x00000000));
            return true;
        }
        if (raw.startsWith("text-backend-")) {
            builder.textBackend(token.value());
            return true;
        }
        if (raw.startsWith("text-effect-")) {
            builder.textEffect(token.value(), 18);
            return true;
        }
        if (raw.startsWith("text-max-")) {
            builder.maxTextWidth(number(raw.substring("text-max-".length()), 0.0f));
            return true;
        }
        if (raw.startsWith("text-align-")) {
            builder.textAlign(raw.substring("text-align-".length()));
            return true;
        }
        if (raw.startsWith("text-")) {
            builder.textColor(resolveColor(token.value(), 0xFFFFFFFF));
            return true;
        }
        if (raw.startsWith("border-")) {
            builder.strokeColor(resolveColor(token.value(), 0x665A5A5A));
            return true;
        }
        if (raw.startsWith("font-size-")) {
            builder.fontSize(number(raw.substring("font-size-".length()), UiUnits.FONT_REFERENCE_SIZE));
            return true;
        }
        if (raw.startsWith("line-height-")) {
            builder.lineHeight(number(raw.substring("line-height-".length()), UiUnits.FONT_REFERENCE_SIZE));
            return true;
        }
        if (raw.startsWith("font-")) {
            applyFont(builder, token.value());
            return true;
        }
        if (raw.startsWith("cursor-")) {
            builder.cursor(token.value());
            return true;
        }
        if (raw.startsWith("blend-")) {
            builder.blend(UiBlendSpec.parse(token.value()));
            return true;
        }
        if (raw.startsWith("opacity-")) {
            builder.opacity(number(raw.substring("opacity-".length()), 1.0f));
            return true;
        }
        if (raw.startsWith("blur-alpha-")) {
            builder.blur(8.0f, 1.0f, number(raw.substring("blur-alpha-".length()), 0.35f));
            return true;
        }
        if (raw.startsWith("blur-quality-")) {
            builder.blur(number(raw.substring("blur-quality-".length()), 8.0f), 1.0f, 0.35f);
            return true;
        }
        if ("blur".equals(raw)) {
            builder.blur(true);
            return true;
        }
        if ("glass".equals(raw) || "liquid-glass".equals(raw)) {
            builder.liquidGlass(true);
            return true;
        }
        if ("clip".equals(raw)) {
            builder.clip(true);
            return true;
        }
        if ("marquee".equals(raw)) {
            builder.marquee(true);
            return true;
        }
        if ("shadow-compact".equals(raw)) {
            builder.shadow(0x30000000, 4.5f, 0.10f);
            return true;
        }
        if ("shadow-panel".equals(raw)) {
            builder.shadow(0x26000000, 6.0f, 0.0f);
            return true;
        }
        if ("shadow-soft".equals(raw) || "shadow".equals(raw)) {
            builder.shadow(0x66000000, 10.0f, 0.18f);
            return true;
        }
        if ("shadow-text".equals(raw) || "text-shadow".equals(raw)) {
            builder.textShadow(true);
            return true;
        }
        if ("ellipsis".equals(raw)) {
            builder.ellipsis(true);
            return true;
        }
        return false;
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
        float legacyScale = Float.NaN;
        FontInfo.Type type = FontInfo.Type.Regular;

        float tailScale = number(parts[end - 1], Float.NaN);
        if (!Float.isNaN(tailScale)) {
            legacyScale = tailScale;
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
        builder.font(family.toString(), type);
        if (!Float.isNaN(legacyScale)) {
            builder.fontSize(UiUnits.fontSize(legacyScale));
        }
    }

    public record ParsedStyle(List<UiStyleToken> tokens, UiStyle style) {
    }
}
