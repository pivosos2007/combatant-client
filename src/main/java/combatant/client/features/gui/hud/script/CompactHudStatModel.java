/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import combatant.client.render.engine.text.TextSizing;
import java.util.LinkedHashMap;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.renderer.ui.runtime.core.UiPatchWriter;
import combatant.client.render.engine.renderer.ui.runtime.core.UiDynamicPatchBuffer;

/**
 * Mutable frame snapshot for compact stat widgets such as FPS, TPS, ping, and game time.
 */
public final class CompactHudStatModel {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final String id;
    private final String rootNodeKey;
    private final Background background = new Background();
    private final Icon icon = new Icon();
    private final Divider divider = new Divider();
    private final TextRun value = new TextRun();
    private final TextRun unit = new TextRun();
    private final TextRun extra = new TextRun();
    private final Animation animation = new Animation();
    // Direct-mapped, preallocated ARGB -> String cache for stable HUD paint colors.
    private final ColorCache dynamicColors = new ColorCache();
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
        this.rootNodeKey = "compact-stat:" + this.id;
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

    private static void putTextPatch(UiPatchWriter writer, String key, String text, String color) {
        putTextPatch(writer, key, text, color, Float.NaN);
    }

    private static void putTextPatch(UiPatchWriter writer, String key, String text,
                                     String color, float textOffsetY) {
        writer.put(key, "text", text != null ? text : "");
        if (color != null) writer.put(key, "color", color);
        if (!Float.isNaN(textOffsetY)) writer.putFloat(key, "textOffsetY", textOffsetY);
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

    private static String withAlpha(String hex, float alpha) {
        int argb = parseColor(hex);
        int a = Math.round(255.0f * clamp01(alpha));
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

    private String dynamicColor(int argb) {
        return dynamicColors.get(argb);
    }

    private String dynamicAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(alpha));
        return dynamicColors.get((argb & 0x00FFFFFF) | (a << 24));
    }

    private String dynamicWithAlpha(int argb, float alpha) {
        int a = Math.round(255.0f * clamp01(alpha));
        return dynamicColors.get((argb & 0x00FFFFFF) | (a << 24));
    }

    private static final class ColorCache {
        private final int[] colors = new int[128];
        private final String[] strings = new String[128];

        String get(int argb) {
            int slot = (argb * 0x9E3779B9) >>> 25;
            String cached = strings[slot];
            if (cached != null && colors[slot] == argb) return cached;
            String encoded = colorString(argb);
            colors[slot] = argb;
            strings[slot] = encoded;
            return encoded;
        }
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
        // Radius is baked into root/clip utility classes and shell shape props.
        h = mix(h, radius);
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
     * Fast retained-UI path: both prop and bounds instructions use stable,
     * preallocated slots instead of nested per-frame HashMaps/UiBounds records.
     */
    public void writeDynamicPatches(UiDynamicPatchBuffer patches) {
        writeRuntimePropPatches(patches);
        writeRuntimeBoundsPatches(patches);
    }

    /** Compatibility for older HUD consumers and tests. */
    public LinkedHashMap<String, LinkedHashMap<String, Object>> runtimePatches() {
        LinkedHashMap<String, LinkedHashMap<String, Object>> patches = new LinkedHashMap<>(12);
        writeRuntimePropPatches(new LegacyPatchWriter(patches, null));
        return patches;
    }

    /** Compatibility for older HUD consumers and tests. */
    public LinkedHashMap<String, UiBounds> runtimeBoundsPatches() {
        LinkedHashMap<String, UiBounds> patches = new LinkedHashMap<>(24);
        writeRuntimeBoundsPatches(new LegacyPatchWriter(null, patches));
        return patches;
    }

    private void writeRuntimePropPatches(UiPatchWriter writer) {
        writer.putFloat(rootNodeKey, "renderBlurAlpha", background.blurAlpha);
        writer.put("fill", "materialMode", background.materialMode);
        writer.put("fill", "startColor", dynamicColor(background.primary));
        writer.put("fill", "endColor", dynamicColor(background.secondary));
        writer.putFloat("fill", "strokeWidth", Math.max(0.45f, background.strokeWidth));
        writer.putFloat("fill", "softness", background.softness);
        float paintAlpha = Math.max(alpha01(background.primary), alpha01(background.secondary));
        float resolvedStrokeAlpha = background.strokeControlled
                ? (background.strokeEnabled ? clamp01(background.strokeAlpha) : 0.0f)
                : Math.max(alpha01(background.stroke), paintAlpha);
        String solidStroke = background.strokeControlled
                ? dynamicWithAlpha(background.stroke, resolvedStrokeAlpha)
                : dynamicAlpha(background.stroke, resolvedStrokeAlpha);
        String strokeStart = background.strokeControlled
                ? dynamicWithAlpha(background.strokeGradient ? background.strokeStartColor : background.stroke, resolvedStrokeAlpha)
                : dynamicAlpha(0xFFFFFFFF, 0.16f * resolvedStrokeAlpha);
        String strokeEnd = background.strokeControlled
                ? dynamicWithAlpha(background.strokeGradient ? background.strokeEndColor : background.stroke, resolvedStrokeAlpha)
                : solidStroke;
        writer.put("fill", "stroke", solidStroke);
        writer.put("fill", "strokeStartColor", strokeStart);
        writer.put("fill", "strokeEndColor", strokeEnd);
        writer.put("top-glint", "startColor", dynamicAlpha(0xFFFFFFFF, 0.065f * paintAlpha));
        writer.put("top-glint", "endColor", "#00000000");
        if (icon.visible) {
            if ("glyph".equals(icon.kind)) {
                writer.put("icon:glyph", "color", dynamicColor(icon.color));
            } else {
                writer.put("icon:texture", "tint", dynamicColor(icon.color));
            }
        }
        if (divider.visible) {
            writer.put("divider", "fill", dynamicColor(divider.color));
        }

        if ("xyz".equals(id)) {
            String coordinateColor = stringData("valueColor", dynamicColor(value.color));
            putTextPatch(writer, "x:v", stringData("xText", "0"), coordinateColor);
            putTextPatch(writer, "y:v", stringData("yText", "0"), coordinateColor);
            putTextPatch(writer, "z:v", stringData("zText", "0"), coordinateColor);
            if (booleanData("showNether") && !stringData("netherText", "").isEmpty()) {
                putTextPatch(writer, "nether", stringData("netherText", ""), dynamicColor(extra.color));
            }
        } else if (animation.digitAnimation) {
            float progress = clamp01(animation.digitProgress);
            float offset = animation.digitOffset;
            putTextPatch(writer, "digit:current", value.text, dynamicAlpha(value.color, progress), -offset * (1.0f - progress) + 2.0f);
            putTextPatch(writer, "digit:prev", animation.previousValue, dynamicAlpha(value.color, 1.0f - progress), offset * progress + 2.0f);
            if (unit.visible) putTextPatch(writer, "unit", unit.text, dynamicColor(unit.color));
            if (extra.visible) putTextPatch(writer, "extra", extra.text, dynamicColor(extra.color));
        } else {
            if (value.visible) putTextPatch(writer, "value", value.text, dynamicColor(value.color));
            if (unit.visible) putTextPatch(writer, "unit", unit.text, dynamicColor(unit.color));
            if (extra.visible) putTextPatch(writer, "extra", extra.text, dynamicColor(extra.color));
        }
    }

    private void writeRuntimeBoundsPatches(UiPatchWriter writer) {
        float visualW = visualWidth();
        writer.putBounds(rootNodeKey, rootX, rootY, visualW, height);
        writer.putBounds("clip", rootX, rootY, visualW, height);
        writer.putBounds("fill", rootX, rootY, visualW, height);
        writer.putBounds("top-glint", rootX + 1.0f, rootY + 1.0f, Math.max(0.0f, visualW - 2.0f), Math.max(1.0f, height * 0.42f));
        if (icon.visible) {
            String iconKey = "glyph".equals(icon.kind) ? "icon:glyph" : "icon:texture";
            writer.putBounds(iconKey, rootX + icon.x, rootY + icon.y, icon.width, icon.height);
        }
        if (divider.visible) {
            writer.putBounds("divider", rootX + divider.x, rootY + divider.y, divider.width, divider.height);
        }

        if ("xyz".equals(id)) {
            putCoordinateBounds(writer, visualW);
        } else if (animation.digitAnimation) {
            writer.putBounds("digit:layer", rootX, rootY, width, height);
            writer.putBounds("digit:clip", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            writer.putBounds("digit:prev", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            writer.putBounds("digit:current", rootX + value.x, rootY + value.y - 2.0f, value.width, Math.max(8.0f, height));
            if (unit.visible) {
                writer.putBounds("unit", rootX + unit.x, rootY + unit.y, unit.width, Math.max(8.0f, height));
            }
            if (extra.visible) {
                writer.putBounds("extra", rootX + extra.x, rootY + extra.y, extra.width, Math.max(8.0f, height));
            }
        } else {
            writer.putBounds("text:layer", rootX, rootY, width, height);
            if (value.visible) {
                writer.putBounds("value", rootX + value.x, rootY + value.y, value.width, Math.max(8.0f, height));
            }
            if (unit.visible) {
                writer.putBounds("unit", rootX + unit.x, rootY + unit.y, unit.width, Math.max(8.0f, height));
            }
            if (extra.visible) {
                writer.putBounds("extra", rootX + extra.x, rootY + extra.y, extra.width, Math.max(8.0f, height));
            }
        }
    }

    private static final class LegacyPatchWriter implements UiPatchWriter {
        private final LinkedHashMap<String, LinkedHashMap<String, Object>> props;
        private final LinkedHashMap<String, UiBounds> bounds;

        private LegacyPatchWriter(LinkedHashMap<String, LinkedHashMap<String, Object>> props,
                                  LinkedHashMap<String, UiBounds> bounds) {
            this.props = props;
            this.bounds = bounds;
        }

        @Override
        public void put(String key, String property, Object value) {
            putPropPatch(props, key, property, value);
        }

        @Override
        public void putBounds(String key, float x, float y, float width, float height) {
            CompactHudStatModel.putBounds(bounds, key, x, y, width, height);
        }
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

    private void putCoordinateBounds(UiPatchWriter writer, float visualW) {
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
        writer.putBounds("coords:row", rowX, rowY, Math.max(0.0f, visualW - value.x - 4.0f), rowH);
        writer.putBounds("x:l", cursor, rowY, labelXW, rowH);
        cursor += labelXW + pairGap;
        writer.putBounds("x:v", cursor, rowY, valueXW, rowH);
        cursor += valueXW + gap;
        writer.putBounds("y:l", cursor, rowY, labelYW, rowH);
        cursor += labelYW + pairGap;
        writer.putBounds("y:v", cursor, rowY, valueYW, rowH);
        cursor += valueYW + gap;
        writer.putBounds("z:l", cursor, rowY, labelZW, rowH);
        cursor += labelZW + pairGap;
        writer.putBounds("z:v", cursor, rowY, valueZW, rowH);
        cursor += valueZW + gap;
        if (booleanData("showNether") && !stringData("netherText", "").isEmpty()) {
            writer.putBounds("nether", cursor, rowY, extraW, rowH);
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
        private String materialMode = "";
        private boolean theme;
        private float blurAlpha;
        private int primary;
        private int secondary;
        private int stroke;
        private float strokeWidth;
        private float softness;
        private boolean strokeControlled;
        private boolean strokeEnabled;
        private float strokeAlpha = 1.0f;
        private boolean strokeGradient;
        private int strokeStartColor;
        private int strokeEndColor;

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

        public Background setStrokeControls(boolean enabled,
                                            float alpha,
                                            boolean gradient,
                                            int startColor,
                                            int endColor) {
            this.strokeControlled = true;
            this.strokeEnabled = enabled;
            this.strokeAlpha = clamp01(alpha);
            this.strokeGradient = gradient;
            this.strokeStartColor = startColor;
            this.strokeEndColor = endColor;
            return this;
        }

        public Background setMaterial(String value) {
            this.materialMode = value != null ? value : "";
            return this;
        }

        private void writeTo(LinkedHashMap<String, Object> props) {
            props.put("materialMode", materialMode);
            props.put("backgroundEffect", effect);
            props.put("backgroundTheme", theme);
            props.put("blurAlpha", blurAlpha);
            props.put("bgPrimary", color(primary));
            props.put("bgSecondary", color(secondary));
            props.put("stroke", color(stroke));
            props.put("strokeWidth", strokeWidth);
            props.put("softness", softness);
            props.put("strokeControlled", strokeControlled);
            props.put("strokeEnabled", strokeEnabled);
            props.put("strokeAlpha", strokeAlpha);
            props.put("strokeGradient", strokeGradient);
            props.put("strokeStartColor", color(strokeStartColor));
            props.put("strokeEndColor", color(strokeEndColor));
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, effect);
            h = CompactHudStatModel.mix(h, materialMode);
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
        private float fontSize;
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
            this.fontSize = TextSizing.sizeForScale(scale);
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
            props.put("iconFontSize", fontSize);
            props.put("iconColor", color(color));
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, kind);
            h = CompactHudStatModel.mix(h, id);
            h = CompactHudStatModel.mix(h, glyph);
            h = CompactHudStatModel.mix(h, font);
            // Glyph font size is structural because it changes authored text geometry.
            h = CompactHudStatModel.mix(h, fontSize);
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

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            return h;
        }

    }

    public static final class TextRun {
        private boolean visible;
        private String text = "";
        private String font = "";
        private float fontSize;
        private float x;
        private float y;
        private float width;
        private int color;

        public TextRun set(String text, String font, float scale, float x, float y, float width, int color) {
            this.visible = text != null && !text.isEmpty();
            this.text = text != null ? text : "";
            this.font = font != null ? font : "";
            this.fontSize = TextSizing.sizeForScale(scale);
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
            this.fontSize = TextSizing.REFERENCE_SIZE;
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
            props.put(prefix + "FontSize", fontSize);
            props.put(prefix + "X", x);
            props.put(prefix + "Y", y);
            props.put(prefix + "W", width);
            props.put(prefix + "Color", color(color));
        }

        private long mixStructure(long h) {
            h = CompactHudStatModel.mix(h, visible);
            h = CompactHudStatModel.mix(h, font);
            h = CompactHudStatModel.mix(h, fontSize);
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

    }
}
