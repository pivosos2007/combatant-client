/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import java.util.LinkedHashMap;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;

/**
 * Mutable frame snapshot for compact stat widgets such as FPS, TPS, ping, and game time.
 */
public final class CompactHudStatModel {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final String id;
    private final Background background = new Background();
    private final Icon icon = new Icon();
    private final Divider divider = new Divider();
    private final TextRun value = new TextRun();
    private final TextRun unit = new TextRun();
    private final TextRun extra = new TextRun();
    private final Animation animation = new Animation();
    private final LinkedHashMap<String, Object> data = new LinkedHashMap<>();
    private boolean visible;
    private float rootX;
    private float rootY;
    private float width;
    private float height;
    private float radius;
    private float scale;

    public CompactHudStatModel(String id) {
        this.id = id != null ? id : "";
    }

    private static void putBounds(LinkedHashMap<String, UiBounds> patches,
                                  String key,
                                  float x,
                                  float y,
                                  float width,
                                  float height) {
        patches.put(key, new UiBounds(x, y, Math.max(0.0f, width), Math.max(0.0f, height)));
    }

    private static void putPropPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                     String key,
                                     String prop,
                                     Object value) {
        LinkedHashMap<String, Object> props = patches.get(key);
        if (props == null) {
            props = new LinkedHashMap<>(2);
            patches.put(key, props);
        }
        props.put(prop, value);
    }

    private static void putTextPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                     String key,
                                     String text,
                                     String color) {
        putTextPatch(patches, key, text, color, Float.NaN);
    }

    private static void putTextPatch(LinkedHashMap<String, LinkedHashMap<String, Object>> patches,
                                     String key,
                                     String text,
                                     String color,
                                     float textOffsetY) {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>(3);
        props.put("text", text != null ? text : "");
        if (color != null) props.put("color", color);
        if (!Float.isNaN(textOffsetY)) props.put("textOffsetY", textOffsetY);
        patches.put(key, props);
    }

    private static long mix(long hash, boolean value) {
        return mix(hash, value ? 1 : 0);
    }

    private static long mix(long hash, int value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    private static long mix(long hash, float value) {
        return mix(hash, Float.floatToIntBits(value));
    }

    private static long mix(long hash, String value) {
        return mix(hash, value != null ? value.hashCode() : 0);
    }

    private static long mixObject(long hash, Object value) {
        if (value instanceof Boolean b) return mix(hash, b);
        if (value instanceof Integer i) return mix(hash, i);
        if (value instanceof Float f) return mix(hash, f);
        if (value instanceof Number n) return mix(hash, Float.floatToIntBits(n.floatValue()));
        return mix(hash, value != null ? value.toString() : "");
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String alpha(String hex, float alpha) {
        int argb = parseColor(hex);
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(alpha));
        return color((argb & 0x00FFFFFF) | (a << 24));
    }

    private static float alpha01(int argb) {
        return ((argb >>> 24) & 0xFF) / 255.0f;
    }

    private static int parseColor(String hex) {
        if (hex == null || hex.isBlank()) return 0xFFFFFFFF;
        String text = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (text.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(text, 16);
            if (text.length() == 8) return (int) Long.parseLong(text, 16);
        } catch (NumberFormatException ignored) {
        }
        return 0xFFFFFFFF;
    }

    private static String color(int argb) {
        return colorString(argb);
    }

    public static String colorString(int argb) {
        char[] out = new char[9];
        out[0] = '#';
        for (int i = 7; i >= 0; i--) {
            out[8 - i] = HEX[(argb >>> (i * 4)) & 0xF];
        }
        return new String(out);
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id;
    }

    public boolean visible() {
        return visible;
    }

    public float rootX() {
        return rootX;
    }

    public float rootY() {
        return rootY;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float radius() {
        return radius;
    }

    public float scale() {
        return scale;
    }

    public CompactHudStatModel setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public CompactHudStatModel setRoot(float x, float y, float width, float height, float radius, float scale) {
        this.rootX = x;
        this.rootY = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.scale = scale;
        return this;
    }

    public Background background() {
        return background;
    }

    public String backgroundEffect() {
        return background.effect;
    }

    public float blurAlpha() {
        return background.blurAlpha;
    }

    public Icon icon() {
        return icon;
    }

    public Divider divider() {
        return divider;
    }

    public TextRun value() {
        return value;
    }

    public TextRun unit() {
        return unit;
    }

    public TextRun extra() {
        return extra;
    }

    public Animation animation() {
        return animation;
    }

    public CompactHudStatModel data(String key, Object value) {
        if (key != null && !key.isEmpty()) {
            data.put(key, value);
        }
        return this;
    }

    public LinkedHashMap<String, Object> toProps() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>(72);
        props.put("id", id);
        props.put("visible", visible);
        props.put("rootX", rootX);
        props.put("rootY", rootY);
        props.put("width", width);
        props.put("height", height);
        props.put("radius", radius);
        props.put("scale", scale);
        background.writeTo(props);
        icon.writeTo(props);
        divider.writeTo(props);
        value.writeTo(props, "value");
        unit.writeTo(props, "unit");
        extra.writeTo(props, "extra");
        animation.writeTo(props);
        props.putAll(data);
        return props;
    }

    public LinkedHashMap<String, Object> toTemplateProps() {
        LinkedHashMap<String, Object> props = toProps();
        float reservedRootWidth = reservedRootWidth();
        float reservedValueWidth = reservedValueWidth();
        float reservedUnitWidth = reservedUnitWidth();
        float reservedExtraWidth = reservedExtraWidth();
        props.put("width", width);
        props.put("valueW", reservedValueWidth);
        props.put("unitW", reservedUnitWidth);
        props.put("extraW", reservedExtraWidth);
        props.put("reservedValueWidth", reservedValueWidth);
        props.put("reservedUnitWidth", reservedUnitWidth);
        props.put("reservedExtraWidth", reservedExtraWidth);
        props.put("valueText", value.text);
        props.put("previousValue", animation.previousValue);
        props.put("digitProgress", animation.digitProgress);
        props.put("digitOffset", animation.digitOffset);
        props.put("effectTime", 0.0f);
        props.put("xText", stringData("xText", "0"));
        props.put("yText", stringData("yText", "0"));
        props.put("zText", stringData("zText", "0"));
        props.put("netherText", stringData("netherText", ""));
        props.remove("fps");
        props.remove("ping");
        props.remove("tps");
        props.remove("usedMiB");
        props.remove("maxMiB");
        props.remove("percent");
        props.remove("bps");
        props.remove("displayedBps");
        props.remove("x");
        props.remove("y");
        props.remove("z");
        props.remove("netherX");
        props.remove("netherZ");
        return props;
    }

    /**
     * Structure/template signature. Excludes volatile text/data values so HUD counters
     * do not force JS execution and reconciliation every frame.
     */
    public long structuralSignature() {
        long h = 0xcbf29ce484222325L;
        h = mix(h, id);
        h = mix(h, visible);
        h = mix(h, scale);
        h = background.mixStructure(h);
        h = icon.mixStructure(h);
        h = divider.mixStructure(h);
        h = value.mixStructure(h);
        h = unit.mixStructure(h);
        h = extra.mixStructure(h);
        h = animation.mixStructure(h);
        h = mixDataShape(h);
        return h;
    }

    /**
     * Dynamic signature for cheap runtime prop patching.
     */
    public long dynamicSignature() {
        long h = 0xcbf29ce484222325L;
        h = mix(h, icon.color);
        h = mix(h, divider.color);
        h = value.mixDynamic(h);
        h = unit.mixDynamic(h);
        h = extra.mixDynamic(h);
        h = animation.mixDynamic(h);
        for (var entry : data.entrySet()) {
            h = mix(h, entry.getKey());
            h = mixObject(h, entry.getValue());
        }
        return h;
    }

    public long treeSignature() {
        return structuralSignature();
    }

    public long layoutSignature(long structuralSignature) {
        long h = structuralSignature;
        h = mix(h, rootX);
        h = mix(h, rootY);
        h = mix(h, reservedRootWidth());
        h = mix(h, height);
        h = mix(h, radius);
        h = background.mixLayout(h);
        h = icon.mixLayout(h);
        h = divider.mixLayout(h);
        h = value.mixLayout(h, reservedValueWidth());
        h = unit.mixLayout(h, reservedUnitWidth());
        h = extra.mixLayout(h, reservedExtraWidth());
        return h;
    }

    public long layoutSignature(long structuralSignature, long ignoredDynamicSignature) {
        return layoutSignature(structuralSignature);
    }

    public long layoutSignature() {
        return layoutSignature(structuralSignature());
    }

    public LinkedHashMap<String, LinkedHashMap<String, Object>> runtimePatches() {
        LinkedHashMap<String, LinkedHashMap<String, Object>> patches = new LinkedHashMap<>(12);
        putPropPatch(patches, "compact-stat:" + id, "renderBlurAlpha", background.blurAlpha);
        putPropPatch(patches, "fill", "startColor", color(background.primary));
        putPropPatch(patches, "fill", "endColor", color(background.secondary));
        float paintAlpha = Math.max(alpha01(background.primary), alpha01(background.secondary));
        float strokeAlpha = Math.max(alpha01(background.stroke), paintAlpha);
        putPropPatch(patches, "fill", "stroke", alpha(color(background.stroke), strokeAlpha));
        putPropPatch(patches, "fill", "strokeStartColor", alpha("#FFFFFFFF", 0.16f * strokeAlpha));
        putPropPatch(patches, "fill", "strokeEndColor", alpha(color(background.stroke), strokeAlpha));
        putPropPatch(patches, "top-glint", "startColor", alpha("#FFFFFFFF", 0.065f * paintAlpha));
        putPropPatch(patches, "top-glint", "endColor", "#00000000");
        putPropPatch(patches, "icon:texture", "tint", color(icon.color));
        putPropPatch(patches, "icon:glyph", "color", color(icon.color));
        putPropPatch(patches, "divider", "fill", color(divider.color));
        putTextPatch(patches, "value", value.text, color(value.color));
        putTextPatch(patches, "unit", unit.text, color(unit.color));
        putTextPatch(patches, "extra", extra.text, color(extra.color));
        float progress = clamp01(animation.digitProgress);
        float offset = animation.digitOffset;
        putTextPatch(patches, "digit:current", value.text, alpha(color(value.color), progress), -offset * (1.0f - progress) + 2.0f);
        putTextPatch(patches, "digit:prev", animation.previousValue, alpha(color(value.color), 1.0f - progress), offset * progress + 2.0f);
        putTextPatch(patches, "x:v", stringData("xText", "0"), stringData("valueColor", color(value.color)));
        putTextPatch(patches, "y:v", stringData("yText", "0"), stringData("valueColor", color(value.color)));
        putTextPatch(patches, "z:v", stringData("zText", "0"), stringData("valueColor", color(value.color)));
        putTextPatch(patches, "nether", stringData("netherText", ""), color(extra.color));
        return patches;
    }

    public LinkedHashMap<String, UiBounds> runtimeBoundsPatches() {
        LinkedHashMap<String, UiBounds> patches = new LinkedHashMap<>(24);
        float visualW = visualWidth();
        putBounds(patches, "compact-stat:" + id, rootX, rootY, visualW, height);
        putBounds(patches, "clip", rootX, rootY, visualW, height);
        putBounds(patches, "fill", rootX, rootY, visualW, height);
        putBounds(patches, "top-glint", rootX + 1.0f, rootY + 1.0f, Math.max(0.0f, visualW - 2.0f), Math.max(1.0f, height * 0.42f));
        if (icon.visible) {
            putBounds(patches, "icon:texture", rootX + icon.x, rootY + icon.y, icon.width, icon.height);
            putBounds(patches, "icon:glyph", rootX + icon.x, rootY + icon.y, icon.width, icon.height);
        }
        if (divider.visible) {
            putBounds(patches, "divider", rootX + divider.x, rootY + divider.y, divider.width, divider.height);
        }

        if ("xyz".equals(id)) {
            putCoordinateBounds(patches, visualW);
        } else if (animation.digitAnimation) {
            putBounds(patches, "digit:layer", rootX, rootY, width, height);
            putBounds(patches, "digit:clip", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            putBounds(patches, "digit:prev", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            putBounds(patches, "digit:current", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            if (unit.visible)
                putBounds(patches, "unit", rootX + unit.x, rootY + unit.y, unit.width, Math.max(8.0f, height));
            else putBounds(patches, "unit", rootX, rootY, 0.0f, 0.0f);
            if (extra.visible)
                putBounds(patches, "extra", rootX + extra.x, rootY + extra.y, extra.width, Math.max(8.0f, height));
            else putBounds(patches, "extra", rootX, rootY, 0.0f, 0.0f);
        } else {
            putBounds(patches, "text:layer", rootX, rootY, width, height);
            if (value.visible)
                putBounds(patches, "value", rootX + value.x, rootY + value.y, value.width, Math.max(8.0f, height));
            else putBounds(patches, "value", rootX, rootY, 0.0f, 0.0f);
            if (unit.visible)
                putBounds(patches, "unit", rootX + unit.x, rootY + unit.y, unit.width, Math.max(8.0f, height));
            else putBounds(patches, "unit", rootX, rootY, 0.0f, 0.0f);
            if (extra.visible)
                putBounds(patches, "extra", rootX + extra.x, rootY + extra.y, extra.width, Math.max(8.0f, height));
            else putBounds(patches, "extra", rootX, rootY, 0.0f, 0.0f);
        }
        return patches;
    }

    private long mixDataShape(long h) {
        for (var entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            h = mix(h, key);
            if (value instanceof Boolean b) {
                h = mix(h, b);
            } else if ("labelColor".equals(key) || "valueColor".equals(key)) {
                h = mixObject(h, value);
            } else if ("netherText".equals(key)) {
                h = mix(h, value != null && !value.toString().isEmpty());
            } else if (value != null) {
                h = mix(h, value.getClass().getName());
            } else {
                h = mix(h, 0);
            }
        }
        return h;
    }

    private float reservedRootWidth() {
        float s = scale != 0.0f ? scale : 1.0f;
        return switch (id) {
            case "fps" -> (26.0f + 36.0f + 2.0f + 17.0f + 5.0f) * s;
            case "ping" -> (26.0f + 40.0f + 2.0f + 16.0f + 5.0f) * s;
            case "tps" -> (26.0f + 39.0f + 2.0f + 15.0f + 3.5f) * s;
            case "memory" -> (26.0f + 74.0f + 2.0f + 17.0f + 5.0f + 34.0f + 5.0f) * s;
            case "speed_bps" -> (26.0f + 36.0f + 2.0f + 20.0f + 3.5f) * s;
            case "game_time" -> (28.5f + 45.0f + 2.0f + 18.0f + 5.0f) * s;
            case "system_time" -> (28.5f + 66.0f + 2.0f + 18.0f + 5.0f) * s;
            case "xyz" -> (booleanData("showNether") ? 150.0f : 112.0f) * s;
            default -> Math.max(width, (26.0f + 42.0f + 2.0f + 18.0f + 5.0f) * s);
        };
    }

    private float reservedValueWidth() {
        float s = scale != 0.0f ? scale : 1.0f;
        float slot = switch (id) {
            case "fps" -> 36.0f;
            case "ping" -> 40.0f;
            case "tps" -> 39.0f;
            case "memory" -> 74.0f;
            case "speed_bps" -> 36.0f;
            case "game_time" -> 45.0f;
            case "system_time" -> 66.0f;
            case "xyz" -> 28.0f;
            default -> 42.0f;
        };
        return Math.max(value.width, slot * s);
    }

    private float reservedUnitWidth() {
        float s = scale != 0.0f ? scale : 1.0f;
        float slot = switch (id) {
            case "fps", "memory" -> 17.0f;
            case "ping" -> 16.0f;
            case "tps" -> 15.0f;
            case "speed_bps" -> 20.0f;
            default -> 18.0f;
        };
        return Math.max(unit.width, slot * s);
    }

    private float reservedExtraWidth() {
        float s = scale != 0.0f ? scale : 1.0f;
        float slot = switch (id) {
            case "memory" -> 34.0f;
            case "xyz" -> booleanData("showNether") ? 34.0f : 0.0f;
            default -> 0.0f;
        };
        return Math.max(extra.width, slot * s);
    }

    private boolean booleanData(String key) {
        Object value = data.get(key);
        return value instanceof Boolean b && b;
    }

    private void putCoordinateBounds(LinkedHashMap<String, UiBounds> patches, float visualW) {
        float rowX = rootX + value.x;
        float rowY = rootY + value.y;
        float rowH = Math.max(8.0f, height - value.y);
        float pairGap = Math.max(1.0f, scale * 1.6f);
        float gap = Math.max(2.0f, scale * 3.2f);
        float labelXW = numberData("labelXW", 6.0f * scale);
        float labelYW = numberData("labelYW", labelXW);
        float labelZW = numberData("labelZW", labelXW);
        float valueXW = numberData("valueXW", 28.0f * scale);
        float valueYW = numberData("valueYW", valueXW);
        float valueZW = numberData("valueZW", valueXW);
        float extraW = numberData("xyzExtraW", extra.width);
        float cursor = rowX;
        putBounds(patches, "coords:row", rowX, rowY, Math.max(0.0f, visualW - value.x - 4.0f), rowH);
        putBounds(patches, "x:l", cursor, rowY, labelXW, rowH);
        cursor += labelXW + pairGap;
        putBounds(patches, "x:v", cursor, rowY, valueXW, rowH);
        cursor += valueXW + gap;
        putBounds(patches, "y:l", cursor, rowY, labelYW, rowH);
        cursor += labelYW + pairGap;
        putBounds(patches, "y:v", cursor, rowY, valueYW, rowH);
        cursor += valueYW + gap;
        putBounds(patches, "z:l", cursor, rowY, labelZW, rowH);
        cursor += labelZW + pairGap;
        putBounds(patches, "z:v", cursor, rowY, valueZW, rowH);
        cursor += valueZW + gap;
        if (booleanData("showNether")) {
            putBounds(patches, "nether", cursor, rowY, extraW, rowH);
        }
    }

    private float visualWidth() {
        if ("xyz".equals(id) && booleanData("showNether")) {
            return Math.max(1.0f, width - 4.0f * scale);
        }
        return width;
    }

    private float numberData(String key, float fallback) {
        Object value = data.get(key);
        if (value instanceof Number n) return n.floatValue();
        if (value instanceof String s) {
            try {
                return Float.parseFloat(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String stringData(String key, String fallback) {
        Object value = data.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    public static final class Background {
        private String effect = "None";
        private boolean theme;
        private float blurAlpha;
        private int primary;
        private int secondary;
        private int stroke;
        private float strokeWidth;
        private float softness;

        public Background set(String effect,
                              boolean theme,
                              float blurAlpha,
                              int primary,
                              int secondary,
                              int stroke,
                              float strokeWidth,
                              float softness) {
            this.effect = effect != null ? effect : "None";
            this.theme = theme;
            this.blurAlpha = blurAlpha;
            this.primary = primary;
            this.secondary = secondary;
            this.stroke = stroke;
            this.strokeWidth = strokeWidth;
            this.softness = softness;
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props) {
            props.put("backgroundEffect", effect);
            props.put("backgroundTheme", theme);
            props.put("blurAlpha", blurAlpha);
            props.put("bgPrimary", color(primary));
            props.put("bgSecondary", color(secondary));
            props.put("stroke", color(stroke));
            props.put("strokeWidth", strokeWidth);
            props.put("softness", softness);
        }

        private long mix(long h) {
            h = CompactHudStatModel.mix(h, effect);
            h = CompactHudStatModel.mix(h, theme);
            h = CompactHudStatModel.mix(h, blurAlpha);
            h = CompactHudStatModel.mix(h, primary);
            h = CompactHudStatModel.mix(h, secondary);
            h = CompactHudStatModel.mix(h, stroke);
            h = CompactHudStatModel.mix(h, strokeWidth);
            h = CompactHudStatModel.mix(h, softness);
            return h;
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, effect);
            return h;
        }

        private long mixLayout(long h) {
            h = CompactHudStatModel.mix(h, strokeWidth);
            h = CompactHudStatModel.mix(h, softness);
            return h;
        }
    }

    public static final class Icon {
        private boolean visible;
        private String kind = "";
        private String id = "";
        private String glyph = "";
        private String font = "";
        private float x;
        private float y;
        private float width;
        private float height;
        private float scale;
        private int color;

        public Icon texture(String id, float x, float y, float width, float height, int color) {
            return set(true, "texture", id, "", "", x, y, width, height, 1.0f, color);
        }

        public Icon glyph(String glyph, String font, float x, float y, float width, float height, float scale, int color) {
            return set(true, "glyph", "", glyph, font, x, y, width, height, scale, color);
        }

        public Icon hidden() {
            return set(false, "", "", "", "", 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0);
        }

        private Icon set(boolean visible,
                         String kind,
                         String id,
                         String glyph,
                         String font,
                         float x,
                         float y,
                         float width,
                         float height,
                         float scale,
                         int color) {
            this.visible = visible;
            this.kind = kind != null ? kind : "";
            this.id = id != null ? id : "";
            this.glyph = glyph != null ? glyph : "";
            this.font = font != null ? font : "";
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.color = color;
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props) {
            props.put("iconVisible", visible);
            props.put("iconKind", kind);
            props.put("iconId", id);
            props.put("iconGlyph", glyph);
            props.put("iconFont", font);
            props.put("iconX", x);
            props.put("iconY", y);
            props.put("iconW", width);
            props.put("iconH", height);
            props.put("iconScale", scale);
            props.put("iconColor", color(color));
        }

        private long mix(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, kind);
            h = CompactHudStatModel.mix(h, id);
            h = CompactHudStatModel.mix(h, glyph);
            h = CompactHudStatModel.mix(h, font);
            h = CompactHudStatModel.mix(h, x);
            h = CompactHudStatModel.mix(h, y);
            h = CompactHudStatModel.mix(h, width);
            h = CompactHudStatModel.mix(h, height);
            h = CompactHudStatModel.mix(h, scale);
            return h;
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, kind);
            h = CompactHudStatModel.mix(h, id);
            h = CompactHudStatModel.mix(h, glyph);
            h = CompactHudStatModel.mix(h, font);
            return h;
        }

        private long mixLayout(long h) {
            h = CompactHudStatModel.mix(h, x);
            h = CompactHudStatModel.mix(h, y);
            h = CompactHudStatModel.mix(h, width);
            h = CompactHudStatModel.mix(h, height);
            h = CompactHudStatModel.mix(h, scale);
            return h;
        }
    }

    public static final class Divider {
        private boolean visible;
        private float x;
        private float y;
        private float width;
        private float height;
        private int color;

        public Divider set(float x, float y, float width, float height, int color) {
            this.visible = true;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props) {
            props.put("dividerVisible", visible);
            props.put("dividerX", x);
            props.put("dividerY", y);
            props.put("dividerW", width);
            props.put("dividerH", height);
            props.put("dividerColor", color(color));
        }

        private long mix(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, x);
            h = CompactHudStatModel.mix(h, y);
            h = CompactHudStatModel.mix(h, width);
            h = CompactHudStatModel.mix(h, height);
            return h;
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            return h;
        }

        private long mixLayout(long h) {
            h = CompactHudStatModel.mix(h, x);
            h = CompactHudStatModel.mix(h, y);
            h = CompactHudStatModel.mix(h, width);
            h = CompactHudStatModel.mix(h, height);
            return h;
        }
    }

    public static final class TextRun {
        private boolean visible;
        private String text = "";
        private String font = "";
        private float scale;
        private float x;
        private float y;
        private float width;
        private int color;

        public TextRun set(String text, String font, float scale, float x, float y, float width, int color) {
            this.visible = text != null && !text.isEmpty();
            this.text = text != null ? text : "";
            this.font = font != null ? font : "";
            this.scale = scale;
            this.x = x;
            this.y = y;
            this.width = width;
            this.color = color;
            return this;
        }

        public TextRun hidden() {
            this.visible = false;
            this.text = "";
            this.font = "";
            this.scale = 1.0f;
            this.x = 0.0f;
            this.y = 0.0f;
            this.width = 0.0f;
            this.color = 0;
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props, String prefix) {
            props.put(prefix + "Visible", visible);
            props.put(prefix + "Text", text);
            props.put(prefix + "Font", font);
            props.put(prefix + "Scale", scale);
            props.put(prefix + "X", x);
            props.put(prefix + "Y", y);
            props.put(prefix + "W", width);
            props.put(prefix + "Color", color(color));
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, font);
            h = CompactHudStatModel.mix(h, scale);
            return h;
        }

        private long mixLayout(long h, float reservedWidth) {
            h = CompactHudStatModel.mix(h, reservedWidth);
            return h;
        }

        private long mixDynamic(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, text);
            h = CompactHudStatModel.mix(h, color);
            return h;
        }
    }

    public static final class Animation {
        private String labelEffect = "NONE";
        private int labelEffectSpeed;
        private float effectTime;
        private boolean digitAnimation;
        private String previousValue = "";
        private float digitProgress = 1.0f;
        private float digitOffset;

        public Animation setLabelEffect(String effect, int speed, float time) {
            this.labelEffect = effect != null ? effect : "NONE";
            this.labelEffectSpeed = speed;
            this.effectTime = time;
            return this;
        }

        public Animation setDigitAnimation(boolean enabled, String previousValue, float progress, float offset) {
            this.digitAnimation = enabled;
            this.previousValue = previousValue != null ? previousValue : "";
            this.digitProgress = progress;
            this.digitOffset = offset;
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props) {
            props.put("labelEffect", labelEffect);
            props.put("labelEffectSpeed", labelEffectSpeed);
            props.put("effectTime", effectTime);
            props.put("digitAnimation", digitAnimation);
            props.put("previousValue", previousValue);
            props.put("digitProgress", digitProgress);
            props.put("digitOffset", digitOffset);
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, labelEffect);
            h = CompactHudStatModel.mix(h, labelEffectSpeed);
            h = CompactHudStatModel.mix(h, digitAnimation);
            return h;
        }

        private long mixDynamic(long h) {
            h = CompactHudStatModel.mix(h, effectTime);
            h = CompactHudStatModel.mix(h, previousValue);
            h = CompactHudStatModel.mix(h, digitProgress);
            h = CompactHudStatModel.mix(h, digitOffset);
            return h;
        }
    }
}
