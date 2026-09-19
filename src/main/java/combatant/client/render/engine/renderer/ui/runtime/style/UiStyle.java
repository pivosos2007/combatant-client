/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.renderer.ui.runtime.render.UiBlendSpec;
import combatant.client.render.engine.text.FontInfo;

import java.util.Locale;

public final class UiStyle {
    public static final UiStyle DEFAULT = builder().build();

    private final Float width;
    private final Float height;
    private final Float minWidth;
    private final Float minHeight;
    private final Float maxWidth;
    private final Float maxHeight;
    private final float paddingLeft;
    private final float paddingTop;
    private final float paddingRight;
    private final float paddingBottom;
    private final float marginLeft;
    private final float marginTop;
    private final float marginRight;
    private final float marginBottom;
    private final float gap;
    private final float grow;
    private final float shrink;
    private final UiDisplay display;
    private final UiFlexDirection flexDirection;
    private final boolean absolute;
    private final Float offsetX;
    private final Float offsetY;
    private final Float offsetRight;
    private final Float offsetBottom;
    private final UiAlign align;
    private final UiJustify justify;
    private final UiOverflow overflow;
    private final float radius;
    private final Integer backgroundColor;
    private final Integer strokeColor;
    private final float strokeWidth;
    private final Integer shadowColor;
    private final float shadowBlur;
    private final float shadowInnerAlpha;
    private final boolean blur;
    private final boolean liquidGlass;
    private final boolean clip;
    private final boolean marquee;
    private final float blurQuality;
    private final float blurBrightness;
    private final float blurAlpha;
    private final Integer textColor;
    private final String fontFamily;
    private final FontInfo.Type fontType;
    private final float fontSize;
    private final Float lineHeight;
    private final boolean textShadow;
    private final String textEffect;
    private final int textEffectSpeed;
    private final String textBackend;
    private final float maxTextWidth;
    private final boolean ellipsis;
    private final String whiteSpace;
    private final String overflowWrap;
    private final int maxLines;
    private final String textOverflow;
    private final String textAlign;
    private final String cursor;
    private final UiBlendSpec blend;
    private final float opacity;

    private UiStyle(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;
        this.minWidth = builder.minWidth;
        this.minHeight = builder.minHeight;
        this.maxWidth = builder.maxWidth;
        this.maxHeight = builder.maxHeight;
        this.paddingLeft = builder.paddingLeft;
        this.paddingTop = builder.paddingTop;
        this.paddingRight = builder.paddingRight;
        this.paddingBottom = builder.paddingBottom;
        this.marginLeft = builder.marginLeft;
        this.marginTop = builder.marginTop;
        this.marginRight = builder.marginRight;
        this.marginBottom = builder.marginBottom;
        this.gap = builder.gap;
        this.grow = builder.grow;
        this.shrink = builder.shrink;
        this.display = builder.display;
        this.flexDirection = builder.flexDirection;
        this.absolute = builder.absolute;
        this.offsetX = builder.offsetX;
        this.offsetY = builder.offsetY;
        this.offsetRight = builder.offsetRight;
        this.offsetBottom = builder.offsetBottom;
        this.align = builder.align;
        this.justify = builder.justify;
        this.overflow = builder.overflow;
        this.radius = builder.radius;
        this.backgroundColor = builder.backgroundColor;
        this.strokeColor = builder.strokeColor;
        this.strokeWidth = builder.strokeWidth;
        this.shadowColor = builder.shadowColor;
        this.shadowBlur = builder.shadowBlur;
        this.shadowInnerAlpha = builder.shadowInnerAlpha;
        this.blur = builder.blur;
        this.liquidGlass = builder.liquidGlass;
        this.clip = builder.clip;
        this.marquee = builder.marquee;
        this.blurQuality = builder.blurQuality;
        this.blurBrightness = builder.blurBrightness;
        this.blurAlpha = builder.blurAlpha;
        this.textColor = builder.textColor;
        this.fontFamily = builder.fontFamily;
        this.fontType = builder.fontType;
        this.fontSize = builder.fontSize;
        this.lineHeight = builder.lineHeight;
        this.textShadow = builder.textShadow;
        this.textEffect = builder.textEffect;
        this.textEffectSpeed = builder.textEffectSpeed;
        this.textBackend = builder.textBackend;
        this.maxTextWidth = builder.maxTextWidth;
        this.ellipsis = builder.ellipsis;
        this.whiteSpace = builder.whiteSpace;
        this.overflowWrap = builder.overflowWrap;
        this.maxLines = builder.maxLines;
        this.textOverflow = builder.textOverflow;
        this.textAlign = builder.textAlign;
        this.cursor = builder.cursor;
        this.blend = builder.blend;
        this.opacity = builder.opacity;
    }

    private static float clamp(float value, Float min, Float max) {
        float out = value;
        if (min != null) out = Math.max(out, min);
        if (max != null) out = Math.min(out, max);
        return out;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Creates a mutable builder initialized from an existing resolved style. */
    public static Builder builder(UiStyle base) {
        return new Builder(base != null ? base : DEFAULT);
    }

    public Float width() {
        return width;
    }

    public Float height() {
        return height;
    }

    public Float minWidth() {
        return minWidth;
    }

    public Float minHeight() {
        return minHeight;
    }

    public Float maxWidth() {
        return maxWidth;
    }

    public Float maxHeight() {
        return maxHeight;
    }

    public float paddingLeft() {
        return paddingLeft;
    }

    public float paddingTop() {
        return paddingTop;
    }

    public float paddingRight() {
        return paddingRight;
    }

    public float paddingBottom() {
        return paddingBottom;
    }

    public float paddingX() {
        return paddingLeft + paddingRight;
    }

    public float paddingY() {
        return paddingTop + paddingBottom;
    }

    public float marginLeft() {
        return marginLeft;
    }

    public float marginTop() {
        return marginTop;
    }

    public float marginRight() {
        return marginRight;
    }

    public float marginBottom() {
        return marginBottom;
    }

    public float marginX() {
        return marginLeft + marginRight;
    }

    public float marginY() {
        return marginTop + marginBottom;
    }

    public float gap() {
        return gap;
    }

    public float grow() {
        return grow;
    }

    /** Explicit main-axis shrink weight. Default 0 preserves legacy fixed-size behavior. */
    public float shrink() {
        return shrink;
    }

    /** Explicit display mode; null keeps the node type's legacy layout semantics. */
    public UiDisplay display() {
        return display;
    }

    /** Explicit flex direction; null uses row for display:flex or the node type's legacy flow. */
    public UiFlexDirection flexDirection() {
        return flexDirection;
    }

    public boolean absolute() {
        return absolute;
    }

    public Float offsetX() {
        return offsetX;
    }

    public Float offsetY() {
        return offsetY;
    }

    public Float offsetRight() {
        return offsetRight;
    }

    public Float offsetBottom() {
        return offsetBottom;
    }

    public UiAlign align() {
        return align;
    }

    public UiJustify justify() {
        return justify;
    }

    public UiOverflow overflow() {
        return overflow;
    }

    public float radius() {
        return radius;
    }

    public Integer backgroundColor() {
        return backgroundColor;
    }

    public Integer strokeColor() {
        return strokeColor;
    }

    public float strokeWidth() {
        return strokeWidth;
    }

    public Integer shadowColor() {
        return shadowColor;
    }

    public float shadowBlur() {
        return shadowBlur;
    }

    public float shadowInnerAlpha() {
        return shadowInnerAlpha;
    }

    public boolean blur() {
        return blur;
    }

    public boolean liquidGlass() {
        return liquidGlass;
    }

    public boolean clip() {
        return clip;
    }

    public boolean marquee() {
        return marquee;
    }

    public float blurQuality() {
        return blurQuality;
    }

    public float blurBrightness() {
        return blurBrightness;
    }

    public float blurAlpha() {
        return blurAlpha;
    }

    public Integer textColor() {
        return textColor;
    }

    public String fontFamily() {
        return fontFamily;
    }

    public FontInfo.Type fontType() {
        return fontType;
    }

    /** Authored font size in logical UI units, matching width/height/padding units. */
    public float fontSize() {
        return fontSize;
    }

    /**
     * @deprecated Backend compatibility only. UI code should author {@link #fontSize()} instead.
     */
    @Deprecated(forRemoval = false)
    public float textScale() {
        return UiUnits.fontScale(fontSize);
    }

    /** Optional authored line-box height in logical UI units. */
    public Float lineHeight() {
        return lineHeight;
    }

    public boolean textShadow() {
        return textShadow;
    }

    public String textEffect() {
        return textEffect;
    }

    public int textEffectSpeed() {
        return textEffectSpeed;
    }

    public String textBackend() {
        return textBackend;
    }

    public float maxTextWidth() {
        return maxTextWidth;
    }

    public boolean ellipsis() {
        return ellipsis;
    }

    public String whiteSpace() {
        return whiteSpace;
    }

    public String overflowWrap() {
        return overflowWrap;
    }

    public int maxLines() {
        return maxLines;
    }

    public String textOverflow() {
        return textOverflow;
    }

    public boolean wrapsText() {
        return "normal".equals(whiteSpace) || "pre-wrap".equals(whiteSpace);
    }

    public boolean ellipsizesText() {
        return ellipsis || "ellipsis".equals(textOverflow);
    }

    public String textAlign() {
        return textAlign;
    }

    public String cursor() {
        return cursor;
    }

    public UiBlendSpec blend() {
        return blend;
    }

    public float opacity() {
        return opacity;
    }

    public float resolveWidth(float fallback) {
        return clamp(width != null ? width : fallback, minWidth, maxWidth);
    }

    public float resolveHeight(float fallback) {
        return clamp(height != null ? height : fallback, minHeight, maxHeight);
    }

    public static final class Builder {
        private Float width;
        private Float height;
        private Float minWidth;
        private Float minHeight;
        private Float maxWidth;
        private Float maxHeight;
        private float paddingLeft;
        private float paddingTop;
        private float paddingRight;
        private float paddingBottom;
        private float marginLeft;
        private float marginTop;
        private float marginRight;
        private float marginBottom;
        private float gap;
        private float grow;
        private float shrink;
        private UiDisplay display;
        private UiFlexDirection flexDirection;
        private boolean absolute;
        private Float offsetX;
        private Float offsetY;
        private Float offsetRight;
        private Float offsetBottom;
        private UiAlign align = UiAlign.START;
        private UiJustify justify = UiJustify.START;
        private UiOverflow overflow = UiOverflow.VISIBLE;
        private float radius;
        private Integer backgroundColor;
        private Integer strokeColor;
        private float strokeWidth = 1.0f;
        private Integer shadowColor;
        private float shadowBlur = 10.0f;
        private float shadowInnerAlpha = 0.18f;
        private boolean blur;
        private boolean liquidGlass;
        private boolean clip;
        private boolean marquee;
        private float blurQuality = 8.0f;
        private float blurBrightness = 1.0f;
        private float blurAlpha = 0.35f;
        private Integer textColor = 0xFFFFFFFF;
        private String fontFamily;
        private FontInfo.Type fontType = FontInfo.Type.Regular;
        private float fontSize = UiUnits.FONT_REFERENCE_SIZE;
        private Float lineHeight;
        private boolean textShadow;
        private String textEffect = "";
        private int textEffectSpeed = 18;
        private String textBackend = "auto";
        private float maxTextWidth;
        private boolean ellipsis;
        private String whiteSpace = "nowrap";
        private String overflowWrap = "normal";
        private int maxLines;
        private String textOverflow = "clip";
        private String textAlign = "left";
        private String cursor = "";
        private UiBlendSpec blend = UiBlendSpec.TRANSLUCENT;
        private float opacity = 1.0f;

        private Builder() {
        }

        private Builder(UiStyle base) {
            this.width = base.width;
            this.height = base.height;
            this.minWidth = base.minWidth;
            this.minHeight = base.minHeight;
            this.maxWidth = base.maxWidth;
            this.maxHeight = base.maxHeight;
            this.paddingLeft = base.paddingLeft;
            this.paddingTop = base.paddingTop;
            this.paddingRight = base.paddingRight;
            this.paddingBottom = base.paddingBottom;
            this.marginLeft = base.marginLeft;
            this.marginTop = base.marginTop;
            this.marginRight = base.marginRight;
            this.marginBottom = base.marginBottom;
            this.gap = base.gap;
            this.grow = base.grow;
            this.shrink = base.shrink;
            this.display = base.display;
            this.flexDirection = base.flexDirection;
            this.absolute = base.absolute;
            this.offsetX = base.offsetX;
            this.offsetY = base.offsetY;
            this.offsetRight = base.offsetRight;
            this.offsetBottom = base.offsetBottom;
            this.align = base.align;
            this.justify = base.justify;
            this.overflow = base.overflow;
            this.radius = base.radius;
            this.backgroundColor = base.backgroundColor;
            this.strokeColor = base.strokeColor;
            this.strokeWidth = base.strokeWidth;
            this.shadowColor = base.shadowColor;
            this.shadowBlur = base.shadowBlur;
            this.shadowInnerAlpha = base.shadowInnerAlpha;
            this.blur = base.blur;
            this.liquidGlass = base.liquidGlass;
            this.clip = base.clip;
            this.marquee = base.marquee;
            this.blurQuality = base.blurQuality;
            this.blurBrightness = base.blurBrightness;
            this.blurAlpha = base.blurAlpha;
            this.textColor = base.textColor;
            this.fontFamily = base.fontFamily;
            this.fontType = base.fontType;
            this.fontSize = base.fontSize;
            this.lineHeight = base.lineHeight;
            this.textShadow = base.textShadow;
            this.textEffect = base.textEffect;
            this.textEffectSpeed = base.textEffectSpeed;
            this.textBackend = base.textBackend;
            this.maxTextWidth = base.maxTextWidth;
            this.ellipsis = base.ellipsis;
            this.whiteSpace = base.whiteSpace;
            this.overflowWrap = base.overflowWrap;
            this.maxLines = base.maxLines;
            this.textOverflow = base.textOverflow;
            this.textAlign = base.textAlign;
            this.cursor = base.cursor;
            this.blend = base.blend;
            this.opacity = base.opacity;
        }

        public Builder width(float width) {
            this.width = width;
            return this;
        }

        public Builder height(float height) {
            this.height = height;
            return this;
        }

        public Builder minWidth(float minWidth) {
            this.minWidth = minWidth;
            return this;
        }

        public Builder minHeight(float minHeight) {
            this.minHeight = minHeight;
            return this;
        }

        public Builder maxWidth(float maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder maxHeight(float maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }

        public Builder padding(float padding) {
            return padding(padding, padding, padding, padding);
        }

        public Builder padding(float horizontal, float vertical) {
            return padding(horizontal, vertical, horizontal, vertical);
        }

        public Builder padding(float left, float top, float right, float bottom) {
            this.paddingLeft = left;
            this.paddingTop = top;
            this.paddingRight = right;
            this.paddingBottom = bottom;
            return this;
        }

        public Builder paddingLeft(float paddingLeft) {
            this.paddingLeft = paddingLeft;
            return this;
        }

        public Builder paddingTop(float paddingTop) {
            this.paddingTop = paddingTop;
            return this;
        }

        public Builder paddingRight(float paddingRight) {
            this.paddingRight = paddingRight;
            return this;
        }

        public Builder paddingBottom(float paddingBottom) {
            this.paddingBottom = paddingBottom;
            return this;
        }

        public Builder margin(float margin) {
            return margin(margin, margin, margin, margin);
        }

        public Builder margin(float horizontal, float vertical) {
            return margin(horizontal, vertical, horizontal, vertical);
        }

        public Builder margin(float left, float top, float right, float bottom) {
            this.marginLeft = left;
            this.marginTop = top;
            this.marginRight = right;
            this.marginBottom = bottom;
            return this;
        }

        public Builder marginLeft(float marginLeft) {
            this.marginLeft = marginLeft;
            return this;
        }

        public Builder marginTop(float marginTop) {
            this.marginTop = marginTop;
            return this;
        }

        public Builder marginRight(float marginRight) {
            this.marginRight = marginRight;
            return this;
        }

        public Builder marginBottom(float marginBottom) {
            this.marginBottom = marginBottom;
            return this;
        }

        public Builder gap(float gap) {
            this.gap = gap;
            return this;
        }

        public Builder grow(float grow) {
            this.grow = Math.max(0.0f, grow);
            return this;
        }

        public Builder shrink(float shrink) {
            this.shrink = Math.max(0.0f, shrink);
            return this;
        }

        public Builder display(UiDisplay display) {
            this.display = display;
            return this;
        }

        public Builder flexDirection(UiFlexDirection flexDirection) {
            this.flexDirection = flexDirection;
            return this;
        }

        public Builder absolute(boolean absolute) {
            this.absolute = absolute;
            return this;
        }

        public Builder offsetX(float offsetX) {
            this.offsetX = offsetX;
            return this;
        }

        public Builder offsetY(float offsetY) {
            this.offsetY = offsetY;
            return this;
        }

        public Builder offsetRight(float offsetRight) {
            this.offsetRight = offsetRight;
            return this;
        }

        public Builder offsetBottom(float offsetBottom) {
            this.offsetBottom = offsetBottom;
            return this;
        }

        public Builder align(UiAlign align) {
            this.align = align != null ? align : UiAlign.START;
            return this;
        }

        public Builder justify(UiJustify justify) {
            this.justify = justify != null ? justify : UiJustify.START;
            return this;
        }

        public Builder overflow(UiOverflow overflow) {
            this.overflow = overflow != null ? overflow : UiOverflow.VISIBLE;
            this.clip = this.overflow.clips();
            return this;
        }

        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }

        public Builder backgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder strokeColor(int strokeColor) {
            this.strokeColor = strokeColor;
            return this;
        }

        public Builder strokeWidth(float strokeWidth) {
            this.strokeWidth = strokeWidth;
            return this;
        }

        public Builder shadow(int color, float blur, float innerAlpha) {
            this.shadowColor = color;
            this.shadowBlur = Math.max(0.0f, blur);
            this.shadowInnerAlpha = Math.max(0.0f, Math.min(1.0f, innerAlpha));
            return this;
        }

        public Builder clearShadow() {
            this.shadowColor = null;
            return this;
        }

        public Builder blur(boolean blur) {
            this.blur = blur;
            return this;
        }

        public Builder liquidGlass(boolean liquidGlass) {
            this.liquidGlass = liquidGlass;
            return this;
        }

        public Builder clip(boolean clip) {
            this.clip = clip;
            return this;
        }

        public Builder marquee(boolean marquee) {
            this.marquee = marquee;
            this.clip = marquee || this.clip;
            return this;
        }

        public Builder blur(float quality, float brightness, float alpha) {
            this.blur = true;
            this.blurQuality = quality;
            this.blurBrightness = brightness;
            this.blurAlpha = alpha;
            return this;
        }

        public Builder textColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder font(String family, FontInfo.Type type) {
            this.fontFamily = family;
            this.fontType = type != null ? type : FontInfo.Type.Regular;
            return this;
        }

        /** Sets the preferred script-facing text size in logical UI units. */
        public Builder fontSize(float fontSize) {
            this.fontSize = Float.isFinite(fontSize)
                    ? Math.max(UiUnits.MIN_FONT_SIZE, fontSize)
                    : UiUnits.FONT_REFERENCE_SIZE;
            return this;
        }

        /**
         * @deprecated Backend-scale authoring retained only for compatibility. Prefer {@link #fontSize(float)}.
         */
        @Deprecated(forRemoval = false)
        public Builder textScale(float textScale) {
            return fontSize(UiUnits.fontSize(textScale));
        }

        public Builder lineHeight(float lineHeight) {
            this.lineHeight = Float.isFinite(lineHeight) ? Math.max(0.0f, lineHeight) : null;
            return this;
        }

        public Builder textShadow(boolean textShadow) {
            this.textShadow = textShadow;
            return this;
        }

        public Builder textEffect(String textEffect, int speed) {
            this.textEffect = textEffect != null ? textEffect : "";
            this.textEffectSpeed = Math.max(1, speed);
            return this;
        }

        public Builder textBackend(String textBackend) {
            this.textBackend = textBackend != null && !textBackend.isBlank() ? textBackend : "auto";
            return this;
        }

        public Builder maxTextWidth(float maxTextWidth) {
            this.maxTextWidth = Math.max(0.0f, maxTextWidth);
            return this;
        }

        public Builder ellipsis(boolean ellipsis) {
            this.ellipsis = ellipsis;
            this.textOverflow = ellipsis ? "ellipsis" : "clip";
            return this;
        }

        public Builder whiteSpace(String whiteSpace) {
            String value = whiteSpace == null ? "nowrap" : whiteSpace.trim().toLowerCase(Locale.ROOT);
            this.whiteSpace = switch (value) {
                case "normal", "pre-wrap", "nowrap" -> value;
                default -> "nowrap";
            };
            return this;
        }

        public Builder overflowWrap(String overflowWrap) {
            String value = overflowWrap == null ? "normal" : overflowWrap.trim().toLowerCase(Locale.ROOT);
            this.overflowWrap = switch (value) {
                case "break-word", "anywhere", "normal" -> value;
                default -> "normal";
            };
            return this;
        }

        public Builder maxLines(int maxLines) {
            this.maxLines = Math.max(0, maxLines);
            return this;
        }

        public Builder textOverflow(String textOverflow) {
            String value = textOverflow == null ? "clip" : textOverflow.trim().toLowerCase(Locale.ROOT);
            this.textOverflow = "ellipsis".equals(value) ? "ellipsis" : "clip";
            this.ellipsis = "ellipsis".equals(this.textOverflow);
            return this;
        }

        public Builder textAlign(String textAlign) {
            this.textAlign = textAlign != null ? textAlign : "left";
            return this;
        }

        public Builder cursor(String cursor) {
            this.cursor = cursor != null ? cursor : "";
            return this;
        }

        public Builder blend(UiBlendSpec blend) {
            this.blend = blend != null ? blend : UiBlendSpec.TRANSLUCENT;
            return this;
        }

        public Builder opacity(float opacity) {
            this.opacity = Math.max(0.0f, Math.min(1.0f, opacity));
            return this;
        }

        public UiStyle build() {
            return new UiStyle(this);
        }
    }
}
