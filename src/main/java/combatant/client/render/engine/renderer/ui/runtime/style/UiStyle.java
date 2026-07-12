/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.style;

import combatant.client.render.engine.renderer.ui.runtime.render.UiBlendSpec;
import combatant.client.render.engine.text.FontInfo;

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
    private final boolean absolute;
    private final Float offsetX;
    private final Float offsetY;
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
    private final float textScale;
    private final boolean textShadow;
    private final String textEffect;
    private final int textEffectSpeed;
    private final String textBackend;
    private final float maxTextWidth;
    private final boolean ellipsis;
    private final String textAlign;
    private final String cursor;
    private final UiBlendSpec blend;

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
        this.absolute = builder.absolute;
        this.offsetX = builder.offsetX;
        this.offsetY = builder.offsetY;
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
        this.textScale = builder.textScale;
        this.textShadow = builder.textShadow;
        this.textEffect = builder.textEffect;
        this.textEffectSpeed = builder.textEffectSpeed;
        this.textBackend = builder.textBackend;
        this.maxTextWidth = builder.maxTextWidth;
        this.ellipsis = builder.ellipsis;
        this.textAlign = builder.textAlign;
        this.cursor = builder.cursor;
        this.blend = builder.blend;
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

    public boolean absolute() {
        return absolute;
    }

    public Float offsetX() {
        return offsetX;
    }

    public Float offsetY() {
        return offsetY;
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

    public float textScale() {
        return textScale;
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

    public String textAlign() {
        return textAlign;
    }

    public String cursor() {
        return cursor;
    }

    public UiBlendSpec blend() {
        return blend;
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
        private boolean absolute;
        private Float offsetX;
        private Float offsetY;
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
        private float textScale = 1.0f;
        private boolean textShadow;
        private String textEffect = "";
        private int textEffectSpeed = 18;
        private String textBackend = "auto";
        private float maxTextWidth;
        private boolean ellipsis;
        private String textAlign = "left";
        private String cursor = "";
        private UiBlendSpec blend = UiBlendSpec.TRANSLUCENT;

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
            if (this.overflow.clips()) this.clip = true;
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

        public Builder textScale(float textScale) {
            this.textScale = textScale;
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

        public UiStyle build() {
            return new UiStyle(this);
        }
    }
}
