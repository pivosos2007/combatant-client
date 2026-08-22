#version 330 core

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
};

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

float pixelAa(vec2 logicalScale) {
    return max(max(logicalScale.x, logicalScale.y), 0.0001);
}

float analyticAa(float d, vec2 logicalScale, float softness) {
    return max(pixelAa(logicalScale), max(fwidth(d) * 0.75, 0.0001)) + max(0.0, softness);
}

float coverage(float d, float aa) {
    return clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
}

float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec4 normalizeRadii(vec4 radii, vec2 size) {
    float maxR = 0.5 * min(size.x, size.y);
    vec4 r = clamp(radii, 0.0, maxR);
    float scale = 1.0;
    if (r.x + r.y > size.x) scale = min(scale, size.x / max(r.x + r.y, 0.0001));
    if (r.w + r.z > size.x) scale = min(scale, size.x / max(r.w + r.z, 0.0001));
    if (r.x + r.w > size.y) scale = min(scale, size.y / max(r.x + r.w, 0.0001));
    if (r.y + r.z > size.y) scale = min(scale, size.y / max(r.y + r.z, 0.0001));
    return r * scale;
}

float roundedCornersSdf(vec2 p, vec2 halfSize, vec4 radii) {
    vec2 corner = p.x < 0.0 ? vec2(radii.x, radii.w) : vec2(radii.y, radii.z);
    float radius = p.y < 0.0 ? corner.x : corner.y;
    vec2 q = abs(p) - halfSize + radius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
}

float squircleSdf(vec2 p, vec2 halfSize, float exponent) {
    vec2 h = max(halfSize, vec2(0.0001));
    float n = clamp(exponent, 2.0, 16.0);
    vec2 q = abs(p) / h;
    vec2 qn = pow(q, vec2(n));
    float implicit = qn.x + qn.y - 1.0;
    vec2 gradient = n * vec2(
        pow(max(q.x, 0.000001), n - 1.0) / h.x,
        pow(max(q.y, 0.000001), n - 1.0) / h.y
    );
    float radial = (pow(max(qn.x + qn.y, 0.000001), 1.0 / n) - 1.0) * min(h.x, h.y);
    return length(gradient) > 0.00001 ? implicit / length(gradient) : radial;
}

float cornerCut(float u, float v, float cutX, float cutY) {
    if (cutX <= 0.0001 || cutY <= 0.0001) return -1.0;
    return (1.0 - u / cutX - v / cutY) / length(vec2(1.0 / cutX, 1.0 / cutY));
}

float chamferSdf(vec2 p, vec2 halfSize, vec4 cutX, vec4 cutY) {
    vec2 q = abs(p);
    float d = max(q.x - halfSize.x, q.y - halfSize.y);
    float left = p.x + halfSize.x;
    float right = halfSize.x - p.x;
    float top = p.y + halfSize.y;
    float bottom = halfSize.y - p.y;
    float tl = cornerCut(left, top, cutX.x, cutY.x);
    float tr = cornerCut(right, top, cutX.y, cutY.y);
    float br = cornerCut(right, bottom, cutX.z, cutY.z);
    float bl = cornerCut(left, bottom, cutX.w, cutY.w);
    return max(d, max(max(tl, tr), max(br, bl)));
}

float strokeBand(float d, float thickness, bool innerStroke) {
    float t = max(0.0, thickness);
    return innerStroke ? abs(d + t * 0.5) - t * 0.5 : abs(d) - t * 0.5;
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
    float radialD = abs(length(local) - radius) - thickness * 0.5;
    float radialAlpha = coverage(radialD, analyticAa(radialD, logicalScale, softness));

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

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);
    vec2 size = max(v_Rect.zw, vec2(0.0001));
    vec2 halfSize = size * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    vec2 p = frag - center;
    float kind = v_Params.x;

    if (abs(kind - KIND_RECT) < 0.25) {
        fragColor = v_Color;
        return;
    }
    if (abs(kind - KIND_ARC) < 0.25) {
        vec3 arcColor = arcHistoryColor(v_Color.rgb, frag, center);
        fragColor = vec4(arcColor, v_Color.a * arcCoverage(frag, center, logicalScale));
        return;
    }
    if (abs(kind - KIND_SHADOW) < 0.25) {
        fragColor = vec4(v_Color.rgb, v_Color.a * bottomShadowCoverage(frag, center, halfSize));
        return;
    }
    if (abs(kind - KIND_SOFT_SHADOW) < 0.25) {
        fragColor = vec4(v_Color.rgb, v_Color.a * softShadowCoverage(p, halfSize));
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

    if (!fill && strokeWidth > 0.0) d = strokeBand(d, strokeWidth, innerStroke);
    float alpha = coverage(d, analyticAa(d, logicalScale, softness));
    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
