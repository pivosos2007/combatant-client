#version 330 core

#moj_import <combatant:ui_aa.glsl>
#moj_import <combatant:ui_geometry.glsl>
#moj_import <combatant:ui_stroke.glsl>

/*
 * Data-driven UI geometry family. Shape identity is vertex data, not pipeline state.
 */

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;
in vec4 v_Params2;
in vec4 v_Params3;

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#ifdef COMBATANT_ANALYTIC_CLIP
#moj_import <combatant:ui_clip.glsl>
#endif

const float KIND_RECT = 0.0;
const float KIND_ROUNDED = 1.0;
const float KIND_SQUIRCLE = 2.0;
const float KIND_ROUNDED_CORNERS = 3.0;
const float KIND_CHAMFER = 4.0;
const float KIND_CIRCLE = 5.0;
const float KIND_ARC = 6.0;
const float KIND_SHADOW = 7.0;
const float KIND_SOFT_SHADOW = 8.0;

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float normalizeAngle(float angleDeg) {
    float angle = mod(angleDeg, 360.0);
    return angle < 0.0 ? angle + 360.0 : angle;
}

vec2 arcPoint(vec2 center, float radius, float angleDeg) {
    float angle = radians(angleDeg);
    return center + vec2(sin(angle), -cos(angle)) * radius;
}

float arcCoverage(vec2 frag, vec2 center, vec2 logicalScale) {
    float radius = max(0.0, v_Params.y);
    float softness = max(0.0, v_Params.z);
    float thickness = max(0.0, v_Params.w);
    float startDeg = normalizeAngle(v_Params2.x);
    float endDeg = normalizeAngle(v_Params2.y);
    bool caps = v_Params2.z > 0.5;
    vec2 local = frag - center;
    float radialAlpha = centeredStrokeCoverage(length(local) - radius, thickness, logicalScale, softness);

    if (v_Params3.x > 0.5) {
        float hashTime = max(v_Params3.y, 0.0);
        float completedLayers = floor(hashTime);
        float progress = fract(hashTime);
        if (completedLayers >= 1.0) return radialAlpha;
        if (progress <= 0.000001) return 0.0;

        float sweep = 360.0 * progress;
        float endUnwrapped = startDeg + sweep;
        float angleDeg = normalizeAngle(degrees(atan(local.x, -local.y)));
        if (angleDeg < startDeg) angleDeg += 360.0;
        float angularSoft = max(fwidth(angleDeg), degrees(max(softness, pixelAa(logicalScale)) / max(radius, 0.0001)));
        float alpha = radialAlpha
                * smoothstep(startDeg - angularSoft, startDeg, angleDeg)
                * (1.0 - smoothstep(endUnwrapped, endUnwrapped + angularSoft, angleDeg));
        if (caps) {
            float capRadius = thickness * 0.5;
            float startD = length(frag - arcPoint(center, radius, startDeg)) - capRadius;
            float endD = length(frag - arcPoint(center, radius, endUnwrapped)) - capRadius;
            alpha = max(alpha, max(
                    coverage(startD, analyticAa(startD, logicalScale, softness)),
                    coverage(endD, analyticAa(endD, logicalScale, softness))));
        }
        return alpha;
    }

    float sweep = endDeg - startDeg;
    if (sweep <= 0.0) sweep += 360.0;
    if (sweep >= 359.99) return radialAlpha;

    // Compare in one unwrapped angular domain. A start at 270° and an end at 0°
    // is a 90° sweep ending at 360°, not a sweep ending numerically at zero.
    float endUnwrapped = startDeg + sweep;
    float angleDeg = normalizeAngle(degrees(atan(local.x, -local.y)));
    if (angleDeg < startDeg) angleDeg += 360.0;
    float angularSoft = max(fwidth(angleDeg), degrees(max(softness, pixelAa(logicalScale)) / max(radius, 0.0001)));
    float alpha = radialAlpha
            * smoothstep(startDeg - angularSoft, startDeg, angleDeg)
            * (1.0 - smoothstep(endUnwrapped, endUnwrapped + angularSoft, angleDeg));
    if (caps) {
        float capRadius = thickness * 0.5;
        float startD = length(frag - arcPoint(center, radius, startDeg)) - capRadius;
        float endD = length(frag - arcPoint(center, radius, endUnwrapped)) - capRadius;
        alpha = max(alpha, max(
                coverage(startD, analyticAa(startD, logicalScale, softness)),
                coverage(endD, analyticAa(endD, logicalScale, softness))));
    }
    return alpha;
}


float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

vec3 overlayColor(vec3 base, vec3 layer) {
    vec3 lo = 2.0 * base * layer;
    vec3 hi = 1.0 - 2.0 * (1.0 - base) * (1.0 - layer);
    return mix(lo, hi, step(vec3(0.5), base));
}

vec3 rotateRgb(vec3 c, float selector) {
    if (selector < 0.333333) return c;
    if (selector < 0.666666) return c.gbr;
    return c.brg;
}

vec3 arcHistoryColor(vec3 base, vec2 frag, vec2 center) {
    if (v_Params3.x < 0.5) return base;

    float startDeg = normalizeAngle(v_Params2.x);
    vec2 local = frag - center;
    float angleDeg = normalizeAngle(degrees(atan(local.x, -local.y)));
    if (angleDeg < startDeg) angleDeg += 360.0;
    float ringU = clamp((angleDeg - startDeg) / 360.0, 0.0, 1.0);

    // History is virtual: one quad is submitted regardless of play time. The four newest
    // completed hours are reconstructed from integer hashes. A new hour enters on top,
    // the fifth one drops out of the bottom, and no framebuffer/history draw is retained.
    float hashTime = max(v_Params3.y, 0.0);
    float completed = floor(hashTime);
    float progress = fract(hashTime);
    vec3 outColor = base;

    for (int i = 0; i < 4; i++) {
        float age = float(i);
        float seed = completed - 1.0 - age;
        float exists = step(0.0, seed);
        float h0 = hash11(seed * 17.173 + ringU * 23.711);
        float h1 = hash11(seed * 41.117 + ringU * 11.903 + 7.0);
        vec3 shifted = rotateRgb(base, h0);
        vec3 layer = base + (shifted - base) * (0.16 + 0.22 * h1);
        layer *= 0.92 + 0.16 * hash11(seed * 9.731 + ringU * 37.0);
        float weight = exists * (0.18 - age * 0.025);
        vec3 over = overlayColor(outColor, clamp(layer, 0.0, 1.0));
        outColor += (over - outColor) * weight;
    }

    // The current, not-yet-completed hour is the top virtual layer. It only affects the
    // already elapsed angular range, so it visibly overwrites the older composite as time moves.
    float edgeAa = max(fwidth(ringU) * 1.5, 0.0025);
    float currentMask = progress <= 0.000001
            ? 0.0
            : 1.0 - smoothstep(progress, progress + edgeAa, ringU);
    float currentSeed = completed;
    float ch0 = hash11(currentSeed * 19.913 + ringU * 29.417 + 3.0);
    float ch1 = hash11(currentSeed * 47.117 + ringU * 13.331 + 11.0);
    vec3 currentShifted = rotateRgb(base, ch0);
    vec3 currentLayer = base + (currentShifted - base) * (0.20 + 0.24 * ch1);
    currentLayer *= 0.94 + 0.14 * hash11(currentSeed * 7.331 + ringU * 31.0);
    vec3 currentOver = overlayColor(outColor, clamp(currentLayer, 0.0, 1.0));
    outColor += (currentOver - outColor) * (0.24 * currentMask);

    return clamp(outColor, 0.0, 1.0);
}


float bottomShadowCoverage(vec2 frag, vec2 center, vec2 halfSize) {
    float radius = min(max(v_Params.y, 0.0), min(halfSize.x, halfSize.y));
    float spread = max(v_Params.w, 0.0001);
    vec2 p = frag - center;
    float dBase = roundedBoxSdf(p, halfSize, radius);
    float outside = step(0.0, dBase);
    float falloff = 1.0 - smoothstep(0.0, max(fwidth(dBase), 0.0001), dBase);
    float rawDx = abs(p.x) - (halfSize.x - radius);
    float dx = max(rawDx, 0.0);
    float arc = sqrt(max(radius * radius - dx * dx, 0.0));
    float bottomEdge = mix(v_Rect.y + v_Rect.w, v_Rect.y + v_Rect.w - radius + arc, step(0.0, rawDx));
    float down = frag.y - bottomEdge;
    float bottomMask = step(0.0, down) * (1.0 - smoothstep(0.0, spread, down));
    return falloff * outside * bottomMask * step(abs(p.x), halfSize.x);
}

float softShadowCoverage(vec2 p, vec2 halfSize) {
    bool squircle = v_Params.y < 0.0;
    float radius = min(max(v_Params.y, 0.0), min(halfSize.x, halfSize.y));
    float blur = max(v_Params.z, 0.0001);
    float innerAlpha = clamp(v_Params.w, 0.0, 1.0);
    float d = squircle
            ? squircleSdf(p, halfSize, -v_Params.y)
            : roundedBoxSdf(p, halfSize, radius);
    float outside = exp(-pow(max(d, 0.0) / blur, 2.0) * 1.35);
    float inside = innerAlpha * (1.0 - smoothstep(-blur * 0.55, 0.0, d));
    return max(inside, outside * (1.0 - inside));
}

void emitShapeColor(vec4 value, vec2 logicalScale) {
#ifdef COMBATANT_ANALYTIC_CLIP
    float clipDistance = combatantClipDistance(combatantLogicalFragCoord());
    value.a *= combatantClipCoverage(clipDistance, logicalScale);
#endif
    if (value.a <= 0.001) discard;
    fragColor = value;
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(0.0001));
    vec2 halfSize = size * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec2 p = frag - center;
    float kind = v_Params.x;

    if (abs(kind - KIND_RECT) < 0.25) {
        emitShapeColor(v_Color, logicalScale);
        return;
    }
    if (abs(kind - KIND_ARC) < 0.25) {
        vec3 arcColor = arcHistoryColor(v_Color.rgb, frag, center);
        emitShapeColor(vec4(arcColor, v_Color.a * arcCoverage(frag, center, logicalScale)), logicalScale);
        return;
    }
    if (abs(kind - KIND_SHADOW) < 0.25) {
        emitShapeColor(vec4(v_Color.rgb, v_Color.a * bottomShadowCoverage(frag, center, halfSize)), logicalScale);
        return;
    }
    if (abs(kind - KIND_SOFT_SHADOW) < 0.25) {
        emitShapeColor(vec4(v_Color.rgb, v_Color.a * softShadowCoverage(p, halfSize)), logicalScale);
        return;
    }

    float d;
    float softness = 0.0;
    float strokeWidth = 0.0;
    bool fill = true;
    bool innerStroke = false;

    if (abs(kind - KIND_ROUNDED) < 0.25 || abs(kind - KIND_SQUIRCLE) < 0.25) {
        d = kind < 1.5
                ? roundedBoxSdf(p, halfSize, v_Params.y)
                : squircleSdf(p, halfSize, v_Params.y);
        strokeWidth = max(0.0, v_Params.z);
        int packedFlags = int(v_Params.w + 0.5);
        int flags = packedFlags & 3;
        softness = float(packedFlags >> 2) / 16.0;
        fill = (flags & 1) != 0;
        innerStroke = (flags & 2) != 0;
    } else if (abs(kind - KIND_ROUNDED_CORNERS) < 0.25) {
        vec4 radii = normalizeRadii(v_Params2, size);
        d = roundedCornersSdf(p, halfSize, radii);
        softness = max(0.0, v_Params.y);
        strokeWidth = max(0.0, v_Params.z);
        fill = v_Params.w > 0.5;
        innerStroke = true;
    } else if (abs(kind - KIND_CHAMFER) < 0.25) {
        vec4 cutX = clamp(v_Params2, 0.0, v_Rect.z);
        vec4 cutY = clamp(v_Params3, 0.0, v_Rect.w);
        d = chamferSdf(p, halfSize, cutX, cutY);
        strokeWidth = max(0.0, v_Params.y);
        fill = v_Params.z > 0.5;
        innerStroke = true;
    } else if (abs(kind - KIND_CIRCLE) < 0.25) {
        d = length(p) - max(0.0, v_Params.y);
        softness = max(0.0, v_Params.z);
        strokeWidth = max(0.0, v_Params.w);
        fill = strokeWidth <= 0.0;
    } else {
        discard;
    }

    float alpha;
    if (!fill && strokeWidth > 0.0) {
        alpha = innerStroke
                ? innerStrokeCoverage(d, strokeWidth, logicalScale, softness)
                : centeredStrokeCoverage(d, strokeWidth, logicalScale, softness);
    } else {
        alpha = coverage(d, analyticAa(d, logicalScale, softness));
    }
    emitShapeColor(vec4(v_Color.rgb, v_Color.a * alpha), logicalScale);
}
