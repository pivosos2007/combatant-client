/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import combatant.client.render.engine.renderer.ui.runtime.render.UiBlendSpec;
import combatant.client.render.engine.text.FontInfo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sparse inline-style declaration used by script-authored UI nodes.
 *
 * <p>This is intentionally not another resolved style object. Only keys that were explicitly
 * authored are stored, so an inline declaration can be layered over utility classes without
 * resetting unrelated class-derived values to {@link UiStyle} defaults.</p>
 */
public final class UiInlineStyle {
    public static final UiInlineStyle EMPTY = new UiInlineStyle(Map.of());

    private static final Set<String> KNOWN_KEYS = Set.of(
            "width", "height", "minwidth", "minheight", "maxwidth", "maxheight",
            "padding", "paddingx", "paddinghorizontal", "paddingy", "paddingvertical",
            "paddingleft", "paddingtop", "paddingright", "paddingbottom",
            "margin", "marginx", "marginhorizontal", "marginy", "marginvertical",
            "marginleft", "margintop", "marginright", "marginbottom",
            "gap", "grow", "flexgrow", "display", "flexdirection",
            "position", "absolute", "left", "top", "right", "bottom", "x", "y",
            "align", "alignitems", "justify", "justifycontent", "overflow",
            "radius", "borderradius",
            "background", "backgroundcolor", "bordercolor", "strokecolor", "borderwidth", "strokewidth",
            "shadow", "boxshadow", "shadowcolor", "shadowblur", "shadowinneralpha",
            "blur", "blurquality", "blurbrightness", "bluralpha", "liquidglass", "clip", "marquee",
            "color", "textcolor", "fontfamily", "fontweight", "fontstyle", "fontsize", "lineheight", "fontscale", "textscale",
            "textshadow", "texteffect", "texteffectspeed", "textbackend", "maxtextwidth", "ellipsis",
            "textalign", "cursor", "blend", "opacity"
    );

    private final Map<String, Object> values;

    public UiInlineStyle(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            this.values = Map.of();
            return;
        }
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) continue;
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        this.values = normalized.isEmpty() ? Map.of() : Collections.unmodifiableMap(normalized);
        validateKnownKeys();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, Object> values() {
        return values;
    }

    /** Applies this sparse declaration over an already resolved base style. */
    public UiStyle apply(UiStyle base) {
        if (values.isEmpty()) return base != null ? base : UiStyle.DEFAULT;
        UiStyle resolvedBase = base != null ? base : UiStyle.DEFAULT;
        UiStyle.Builder builder = UiStyle.builder(resolvedBase);

        applyNumber(builder::width, "width");
        applyNumber(builder::height, "height");
        applyNumber(builder::minWidth, "minwidth");
        applyNumber(builder::minHeight, "minheight");
        applyNumber(builder::maxWidth, "maxwidth");
        applyNumber(builder::maxHeight, "maxheight");

        Float padding = number("padding");
        if (padding != null) builder.padding(padding);
        applyAxisInsets(builder, true, true);

        Float margin = number("margin");
        if (margin != null) builder.margin(margin);
        applyAxisInsets(builder, false, true);

        applyNumber(builder::gap, "gap");
        Float grow = firstNumber("flexgrow", "grow");
        if (grow != null) builder.grow(grow);

        String display = text("display");
        if (display != null) {
            UiDisplay parsed = UiDisplay.parse(display, null);
            if (parsed != null) builder.display(parsed);
            else invalid("UI inline style 'display' currently supports flex or block, got '" + display + "'.");
        }
        String flexDirection = text("flexdirection");
        if (flexDirection != null) {
            UiFlexDirection parsed = UiFlexDirection.parse(flexDirection, null);
            if (parsed != null) builder.flexDirection(parsed);
            else invalid("UI inline style 'flexDirection' has unknown value '" + flexDirection + "'.");
        }

        String position = text("position");
        if (position != null) {
            switch (position.trim().toLowerCase(Locale.ROOT)) {
                case "absolute" -> builder.absolute(true);
                case "relative", "static", "flow" -> builder.absolute(false);
                default -> invalid("UI inline style 'position' must be absolute or relative, got '" + position + "'.");
            }
        }
        Boolean absolute = bool("absolute");
        if (absolute != null) builder.absolute(absolute);
        Float left = firstNumber("left", "x");
        Float top = firstNumber("top", "y");
        Float right = number("right");
        Float bottom = number("bottom");
        if (left != null) builder.offsetX(left);
        if (top != null) builder.offsetY(top);
        if (right != null) builder.offsetRight(right);
        if (bottom != null) builder.offsetBottom(bottom);

        String align = firstText("alignitems", "align");
        if (align != null) {
            UiAlign parsed = UiAlign.parse(align, null);
            if (parsed != null) builder.align(parsed);
            else invalid("UI inline style 'alignItems' has unknown value '" + align + "'.");
        }
        String justify = firstText("justifycontent", "justify");
        if (justify != null) {
            UiJustify parsed = UiJustify.parse(justify, null);
            if (parsed != null) builder.justify(parsed);
            else invalid("UI inline style 'justifyContent' has unknown value '" + justify + "'.");
        }
        String overflow = text("overflow");
        if (overflow != null) {
            UiOverflow parsed = UiOverflow.parse(overflow, null);
            if (parsed != null) builder.overflow(parsed);
            else invalid("UI inline style 'overflow' has unknown value '" + overflow + "'.");
        }

        Float radius = firstNumber("borderradius", "radius");
        if (radius != null) builder.radius(radius);
        Integer background = firstColor("backgroundcolor", "background");
        if (background != null) builder.backgroundColor(background);
        Integer strokeColor = firstColor("bordercolor", "strokecolor");
        if (strokeColor != null) builder.strokeColor(strokeColor);
        Float strokeWidth = firstNumber("borderwidth", "strokewidth");
        if (strokeWidth != null) builder.strokeWidth(strokeWidth);

        applyShadow(builder, resolvedBase);

        Boolean blur = bool("blur");
        if (blur != null) builder.blur(blur);
        Float blurQuality = number("blurquality");
        Float blurBrightness = number("blurbrightness");
        Float blurAlpha = number("bluralpha");
        if (blurQuality != null || blurBrightness != null || blurAlpha != null) {
            builder.blur(
                    blurQuality != null ? blurQuality : resolvedBase.blurQuality(),
                    blurBrightness != null ? blurBrightness : resolvedBase.blurBrightness(),
                    blurAlpha != null ? blurAlpha : resolvedBase.blurAlpha()
            );
        }
        Boolean liquidGlass = bool("liquidglass");
        if (liquidGlass != null) builder.liquidGlass(liquidGlass);
        Boolean clip = bool("clip");
        if (clip != null) builder.clip(clip);
        Boolean marquee = bool("marquee");
        if (marquee != null) builder.marquee(marquee);

        Integer textColor = firstColor("color", "textcolor");
        if (textColor != null) builder.textColor(textColor);
        applyFont(builder, resolvedBase);
        // Legacy multiplicative scale is accepted, but authored fontSize wins when both are present.
        Float textScale = firstNumber("fontscale", "textscale");
        if (textScale != null) builder.textScale(textScale);
        Float fontSize = number("fontsize");
        if (fontSize != null) builder.fontSize(fontSize);
        Float lineHeight = number("lineheight");
        if (lineHeight != null) builder.lineHeight(lineHeight);
        Boolean textShadow = bool("textshadow");
        if (textShadow != null) builder.textShadow(textShadow);
        String textEffect = text("texteffect");
        Integer textEffectSpeed = integer("texteffectspeed");
        if (textEffect != null || textEffectSpeed != null) {
            builder.textEffect(
                    textEffect != null ? textEffect : resolvedBase.textEffect(),
                    textEffectSpeed != null ? textEffectSpeed : resolvedBase.textEffectSpeed()
            );
        }
        String textBackend = text("textbackend");
        if (textBackend != null) builder.textBackend(textBackend);
        Float maxTextWidth = number("maxtextwidth");
        if (maxTextWidth != null) builder.maxTextWidth(maxTextWidth);
        Boolean ellipsis = bool("ellipsis");
        if (ellipsis != null) builder.ellipsis(ellipsis);
        String textAlign = text("textalign");
        if (textAlign != null) builder.textAlign(textAlign);
        String cursor = text("cursor");
        if (cursor != null) builder.cursor(cursor);
        String blend = text("blend");
        if (blend != null) builder.blend(UiBlendSpec.parse(blend));
        Float opacity = number("opacity");
        if (opacity != null) builder.opacity(opacity);

        return builder.build();
    }

    private void applyAxisInsets(UiStyle.Builder builder, boolean padding, boolean includeSides) {
        Float horizontal = firstNumber(padding ? "paddingx" : "marginx", padding ? "paddinghorizontal" : "marginhorizontal");
        Float vertical = firstNumber(padding ? "paddingy" : "marginy", padding ? "paddingvertical" : "marginvertical");
        if (padding) {
            if (horizontal != null) {
                builder.paddingLeft(horizontal);
                builder.paddingRight(horizontal);
            }
            if (vertical != null) {
                builder.paddingTop(vertical);
                builder.paddingBottom(vertical);
            }
            if (includeSides) {
                applyNumber(builder::paddingLeft, "paddingleft");
                applyNumber(builder::paddingTop, "paddingtop");
                applyNumber(builder::paddingRight, "paddingright");
                applyNumber(builder::paddingBottom, "paddingbottom");
            }
        } else {
            if (horizontal != null) {
                builder.marginLeft(horizontal);
                builder.marginRight(horizontal);
            }
            if (vertical != null) {
                builder.marginTop(vertical);
                builder.marginBottom(vertical);
            }
            if (includeSides) {
                applyNumber(builder::marginLeft, "marginleft");
                applyNumber(builder::marginTop, "margintop");
                applyNumber(builder::marginRight, "marginright");
                applyNumber(builder::marginBottom, "marginbottom");
            }
        }
    }

    private void applyShadow(UiStyle.Builder builder, UiStyle base) {
        Object raw = first("boxshadow", "shadow");
        if (raw instanceof Boolean enabled && !enabled) {
            builder.clearShadow();
            return;
        }

        Integer color = base.shadowColor();
        float blur = base.shadowBlur();
        float innerAlpha = base.shadowInnerAlpha();
        boolean touched = false;

        if (raw instanceof Map<?, ?> map) {
            Object rawColor = map.get("color");
            if (rawColor != null) {
                color = color(rawColor, color != null ? color : 0x66000000, "boxShadow.color");
                touched = true;
            }
            Object rawBlur = map.get("blur");
            if (rawBlur != null) {
                Float parsed = numeric(rawBlur, null, "boxShadow.blur");
                if (parsed != null) {
                    blur = parsed;
                    touched = true;
                }
            }
            Object rawInnerAlpha = map.get("innerAlpha");
            if (rawInnerAlpha != null) {
                Float parsed = numeric(rawInnerAlpha, null, "boxShadow.innerAlpha");
                if (parsed != null) {
                    innerAlpha = parsed;
                    touched = true;
                }
            }
        } else if (raw != null && !(raw instanceof Boolean)) {
            invalid("UI inline style 'boxShadow' must be an object or false.");
        }

        Integer directColor = color("shadowcolor");
        if (directColor != null) {
            color = directColor;
            touched = true;
        }
        Float directBlur = number("shadowblur");
        if (directBlur != null) {
            blur = directBlur;
            touched = true;
        }
        Float directInnerAlpha = number("shadowinneralpha");
        if (directInnerAlpha != null) {
            innerAlpha = directInnerAlpha;
            touched = true;
        }
        if (touched) {
            builder.shadow(color != null ? color : 0x66000000, blur, innerAlpha);
        }
    }

    private void applyFont(UiStyle.Builder builder, UiStyle base) {
        String family = text("fontfamily");
        Object weightRaw = values.get("fontweight");
        String styleRaw = text("fontstyle");
        if (family == null && weightRaw == null && styleRaw == null) return;

        boolean bold = base.fontType() == FontInfo.Type.Bold || base.fontType() == FontInfo.Type.BoldItalic;
        boolean italic = base.fontType() == FontInfo.Type.Italic || base.fontType() == FontInfo.Type.BoldItalic;
        if (weightRaw instanceof Number number) {
            bold = number.intValue() >= 600;
        } else if (weightRaw instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            bold = "bold".equals(normalized) || "semibold".equals(normalized) || "600".equals(normalized)
                    || "700".equals(normalized) || "800".equals(normalized) || "900".equals(normalized);
        } else if (weightRaw != null) {
            invalid("UI inline style 'fontWeight' must be a number or string.");
        }
        if (styleRaw != null) italic = "italic".equalsIgnoreCase(styleRaw.trim());

        FontInfo.Type type = bold
                ? (italic ? FontInfo.Type.BoldItalic : FontInfo.Type.Bold)
                : (italic ? FontInfo.Type.Italic : FontInfo.Type.Regular);
        builder.font(family != null ? family : base.fontFamily(), type);
    }

    private void validateKnownKeys() {
        if (!UiRuntimeValidation.enabled()) return;
        for (String key : values.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                throw UiRuntimeValidation.invalid("Unknown UI inline style property '" + key + "'.");
            }
        }
    }

    private Object first(String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) return values.get(key);
        }
        return null;
    }

    private Float firstNumber(String... keys) {
        for (String key : keys) {
            Float value = number(key);
            if (value != null || values.containsKey(key)) return value;
        }
        return null;
    }

    private String firstText(String... keys) {
        for (String key : keys) {
            String value = text(key);
            if (value != null || values.containsKey(key)) return value;
        }
        return null;
    }

    private Integer firstColor(String... keys) {
        for (String key : keys) {
            Integer value = color(key);
            if (value != null || values.containsKey(key)) return value;
        }
        return null;
    }

    private Float number(String key) {
        if (!values.containsKey(key)) return null;
        Object raw = values.get(key);
        if (raw == null) return null;
        return numeric(raw, null, key);
    }

    private Integer integer(String key) {
        Float value = number(key);
        return value != null ? Math.round(value) : null;
    }

    private Boolean bool(String key) {
        if (!values.containsKey(key)) return null;
        Object raw = values.get(key);
        if (raw == null) return null;
        if (raw instanceof Boolean value) return value;
        invalid("UI inline style '" + key + "' must be boolean.");
        return null;
    }

    private String text(String key) {
        if (!values.containsKey(key)) return null;
        Object raw = values.get(key);
        if (raw == null) return null;
        if (raw instanceof String value) return value;
        invalid("UI inline style '" + key + "' must be string.");
        return null;
    }

    private Integer color(String key) {
        if (!values.containsKey(key)) return null;
        Object raw = values.get(key);
        if (raw == null) return null;
        return color(raw, 0x00000000, key);
    }

    private static Float numeric(Object raw, Float fallback, String key) {
        if (raw instanceof Number value) return value.floatValue();
        if (raw instanceof String value) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException ignored) {
            }
        }
        invalid("UI inline style '" + key + "' must be numeric.");
        return fallback;
    }

    private static int color(Object raw, int fallback, String key) {
        if (raw instanceof Number value) return value.intValue();
        if (raw instanceof String value) {
            String text = value.trim();
            if (text.equalsIgnoreCase("transparent")) return 0x00000000;
            String name = text;
            float alpha = -1.0f;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                name = text.substring(0, slash);
                try {
                    alpha = Float.parseFloat(text.substring(slash + 1)) / 100.0f;
                } catch (NumberFormatException ignored) {
                    invalid("UI inline style '" + key + "' has invalid color alpha '" + text + "'.");
                }
            }
            int color = name.startsWith("#")
                    ? UiColor.parse(name, fallback)
                    : UiThemeRegistry.current().color(name, fallback);
            return alpha >= 0.0f ? UiColor.multiplyAlpha(color, alpha) : color;
        }
        invalid("UI inline style '" + key + "' must be a color string or integer.");
        return fallback;
    }

    private void applyNumber(FloatConsumer consumer, String key) {
        Float value = number(key);
        if (value != null) consumer.accept(value);
    }

    private static String normalizeKey(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '-' || c == '_' || Character.isWhitespace(c)) continue;
            out.append(Character.toLowerCase(c));
        }
        return out.toString();
    }

    private static void invalid(String message) {
        if (UiRuntimeValidation.enabled()) throw UiRuntimeValidation.invalid(message);
    }

    @FunctionalInterface
    private interface FloatConsumer {
        void accept(float value);
    }
}
