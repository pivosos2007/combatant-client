/* Analytic shape-clip contract. Included only by COMBATANT_ANALYTIC_CLIP variants. */
const int COMBATANT_MAX_ANALYTIC_CLIPS = 4;
const float COMBATANT_CLIP_RECT = 0.0;
const float COMBATANT_CLIP_ROUNDED = 1.0;
const float COMBATANT_CLIP_CHAMFER = 2.0;
const float COMBATANT_CLIP_CIRCLE = 3.0;

layout (std140) uniform UIClip {
    vec4 uClipHeader; // x = primitive count
    vec4 uClipKinds;
    vec4 uClipBounds[COMBATANT_MAX_ANALYTIC_CLIPS];
    vec4 uClipParams0[COMBATANT_MAX_ANALYTIC_CLIPS];
    vec4 uClipParams1[COMBATANT_MAX_ANALYTIC_CLIPS];
};

vec2 combatantLogicalFragCoord() {
    vec2 framebuffer = max(uScreen.xy, vec2(1.0));
    vec2 logical = max(uScreen.zw, vec2(1.0));
    return vec2(
        gl_FragCoord.x * logical.x / framebuffer.x,
        (framebuffer.y - gl_FragCoord.y) * logical.y / framebuffer.y
    );
}

float combatantRectSdf(vec2 p, vec2 halfSize) {
    vec2 q = abs(p) - halfSize;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0));
}

float combatantClipPrimitiveDistance(int index, vec2 logicalPosition) {
    vec4 bounds = uClipBounds[index];
    vec2 size = max(bounds.zw, vec2(0.0001));
    vec2 halfSize = size * 0.5;
    vec2 p = logicalPosition - (bounds.xy + halfSize);
    float kind = uClipKinds[index];

    if (kind < 0.5) return combatantRectSdf(p, halfSize);
    if (kind < 1.5) return roundedCornersSdf(p, halfSize, normalizeRadii(uClipParams0[index], size));
    if (kind < 2.5) {
        vec4 cutX = clamp(uClipParams0[index], 0.0, size.x);
        vec4 cutY = clamp(uClipParams1[index], 0.0, size.y);
        return chamferSdf(p, halfSize, cutX, cutY);
    }
    return length(p) - min(halfSize.x, halfSize.y);
}

float combatantClipDistance(vec2 logicalPosition) {
    int count = clamp(int(uClipHeader.x + 0.5), 0, COMBATANT_MAX_ANALYTIC_CLIPS);
    float distanceToIntersection = -1e20;
    for (int i = 0; i < COMBATANT_MAX_ANALYTIC_CLIPS; i++) {
        if (i >= count) break;
        distanceToIntersection = max(distanceToIntersection,
                combatantClipPrimitiveDistance(i, logicalPosition));
    }
    return distanceToIntersection;
}

float combatantClipCoverage(float clipDistance, vec2 logicalScale) {
    float aa = max(max(logicalScale.x, logicalScale.y), max(fwidth(clipDistance) * 0.75, 0.0001));
    return clamp(0.5 - clipDistance / aa, 0.0, 1.0);
}
