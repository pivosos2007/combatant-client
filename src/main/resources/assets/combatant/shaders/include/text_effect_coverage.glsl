/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */

// Shared UI/world glyph coverage and effect composition. Glyph bounds prevent
// neighbouring atlas cells from bleeding into padded outline/shadow quads.
float textEffectMedian(vec3 rgb) {
    return max(min(rgb.r, rgb.g), min(max(rgb.r, rgb.g), rgb.b));
}

vec3 textEffectAtlas(vec2 uv, vec4 bounds, vec2 texel, vec2 uvDx, vec2 uvDy) {
    if (any(lessThan(uv, bounds.xy)) || any(greaterThan(uv, bounds.zw))) return vec3(0.0);
    return textureGrad(u_Texture, clamp(uv, bounds.xy + texel * 0.5,
                                      bounds.zw - texel * 0.5), uvDx, uvDy).rgb;
}

float textEffectCoverage(vec2 uv, vec4 bounds, vec2 texel,
                         vec2 uvDx, vec2 uvDy, float mode, float pxRange) {
    if (mode < 0.0) {
        // A GlyphMaskFont atlas stores four subpixel coverage samples in RGBA;
        // its ordinary glyph shader averages these four samples as well.
        // Subpixel offsets follow the actual projected pixel footprint; fixed
        // texel offsets break on fractional UI scales and produce stray dots.
        vec2 qx = uvDx * 0.25;
        vec2 qy = uvDy * 0.25;
        return 0.25 * (textEffectAtlas(uv - qx - qy, bounds, texel, uvDx, uvDy).r
                     + textEffectAtlas(uv + qx - qy, bounds, texel, uvDx, uvDy).r
                     + textEffectAtlas(uv - qx + qy, bounds, texel, uvDx, uvDy).r
                     + textEffectAtlas(uv + qx + qy, bounds, texel, uvDx, uvDy).r);
    }
    vec3 sampled = textEffectAtlas(uv, bounds, texel, uvDx, uvDy);
    if (mode == 0.0) return sampled.r;
    // Out-of-bounds MSDF must have zero coverage, not a negative distance
    // interpreted as an inner contour at the atlas cell boundary.
    float sd = textEffectMedian(sampled) - 0.5;
    return clamp(sd * pxRange + 0.5, 0.0, 1.0);
}

vec4 textEffectColor(vec2 uv, vec4 bounds, vec4 baseColor, vec4 effect,
                     vec2 uvDx, vec2 uvDy) {
    // Padded fragments may lie outside the glyph cell: the face samples zero
    // there, while a shifted shadow may still legitimately overlap the cell.
    vec2 texel = 1.0 / max(vec2(textureSize(u_Texture, 0)), vec2(1.0));
    float pxRange = effect.w > 0.0
            ? max(0.5 * dot(effect.w * texel, 1.0 / max(abs(uvDx) + abs(uvDy), vec2(1e-8))), 1.0)
            : 1.0;
    float face = textEffectCoverage(uv, bounds, texel, uvDx, uvDy, effect.w, pxRange);
    float effectCover = 0.0;
    vec3 effectRgb = vec3(0.0);

    if (effect.z < 0.0) {
        // Directional drop shadow. Only a single shifted glyph sample is needed;
        // no second quad and no duplicated glyph geometry.
        effectCover = textEffectCoverage(uv - effect.xy, bounds, texel,
                          uvDx, uvDy, effect.w, pxRange) * (-effect.z);
        effectRgb = vec3(60.0 / 255.0);
    } else if (effect.z > 0.0) {
        if (effect.w > 0.0) {
            // True MSDF dilation: expand the *same* distance field instead of
            // sampling unrelated neighbours, which caused detached bright dots.
            vec2 cellUv = clamp(uv, bounds.xy, bounds.zw);
            vec3 sampled = textEffectAtlas(cellUv, bounds, texel, uvDx, uvDy);
            vec2 footprint = max(abs(uvDx) + abs(uvDy), vec2(1e-8));
            float distanceOutsidePx = length((uv - cellUv) / footprint);
            float distancePx = (textEffectMedian(sampled) - 0.5) * pxRange - distanceOutsidePx;
            float radiusPx = 0.5 * dot(abs(effect.xy),
                    1.0 / max(abs(uvDx) + abs(uvDy), vec2(1e-8)));
            effectCover = max(0.0, clamp(distancePx + radiusPx + 0.5, 0.0, 1.0) - face)
                    * effect.z;
        } else {
            // Bitmap fallback: soft axial neighbourhood dilation. A mean,
            // rather than a max/threshold, avoids isolated single-pixel halos.
            float around = textEffectCoverage(uv - vec2(effect.x, 0.0), bounds, texel, uvDx, uvDy, effect.w, pxRange)
                         + textEffectCoverage(uv + vec2(effect.x, 0.0), bounds, texel, uvDx, uvDy, effect.w, pxRange)
                         + textEffectCoverage(uv - vec2(0.0, effect.y), bounds, texel, uvDx, uvDy, effect.w, pxRange)
                         + textEffectCoverage(uv + vec2(0.0, effect.y), bounds, texel, uvDx, uvDy, effect.w, pxRange);
            effectCover = max(0.0, around * 0.25 - face) * effect.z;
        }
    }
    float frontAlpha = face * baseColor.a;
    float backAlpha = effectCover * baseColor.a * (1.0 - face);
    float alpha = clamp(frontAlpha + backAlpha, 0.0, 1.0);
    vec3 premultiplied = baseColor.rgb * frontAlpha + effectRgb * backAlpha;
    return vec4(premultiplied / max(alpha, 1e-7), alpha);
}
