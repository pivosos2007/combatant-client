#version 330 core

/*
 * Analytic media/progress waveform.
 * Geometry is a single quad.
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

#ifdef COMBATANT_ANALYTIC_CLIP
#moj_import <combatant:ui_geometry.glsl>
#moj_import <combatant:ui_clip.glsl>
#endif

const float TAU = 6.28318530717958647692;

vec2 sourceLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float edgeEnvelope(float x, float left, float right, float fadePx) {
    if (fadePx <= 0.0001) return 1.0;
    float edge = min(x - left, right - x);
    float t = clamp(edge / fadePx, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

float waveSignal(float x, float left, float wavelength, float phase, float harmonic) {
    // Water-surface profile, not a periodic spring.  The dominant component is a
    // long swell; two weaker components travel at different phase velocities and
    // use irrational-ish wavelength ratios so the silhouette does not repeat as a
    // chain of identical coils along the seek bar.
    float lambda = max(wavelength, 1.0);
    float d = x - left;
    float detail = clamp(harmonic, 0.0, 0.45);

    float longTheta = (d / (lambda * 1.72)) * TAU + phase * 0.58 + 0.35;
    float midTheta  = (d / (lambda * 1.07)) * TAU - phase * 0.37 + 2.05;
    float fineTheta = (d / (lambda * 0.61)) * TAU + phase * 0.83 - 0.75;

    // Slowly bend the horizontal phase to produce drifting/interfering water
    // rather than evenly spaced sinusoidal turns.
    float domainWarp = 0.16 * sin((d / (lambda * 2.43)) * TAU - phase * 0.21 + 1.10)
                     + 0.07 * sin((d / (lambda * 0.89)) * TAU + phase * 0.16 - 0.40);

    float swell = sin(longTheta + domainWarp);
    float crossing = sin(midTheta - domainWarp * 0.55);
    float ripple = sin(fineTheta + domainWarp * 0.32);

    float signal = swell * 0.70
                 + crossing * 0.23
                 + ripple * (0.07 + detail * 0.10);

    // Mild crest asymmetry: water crests are slightly tighter than troughs,
    // without turning the line into a sawtooth or a spring.
    signal += 0.055 * (signal * signal - 0.34);
    return clamp(signal, -1.0, 1.0);
}

float waveY(float x, float left, float right, float centerY,
            float amplitude, float wavelength, float phase, float harmonic, float fadePx) {
    float envelope = edgeEnvelope(x, left, right, fadePx);
    return centerY + waveSignal(x, left, wavelength, phase, harmonic) * amplitude * envelope;
}

void main() {
    vec2 p = sourceLocal(v_Local);

    // v_Rect: left, right, centerY, reserved
    float left = min(v_Rect.x, v_Rect.y);
    float right = max(v_Rect.x, v_Rect.y);
    float centerY = v_Rect.z;

    // v_Params: amplitude, thickness, wavelength, phase
    float amplitude = max(v_Params.x, 0.0);
    float thickness = max(v_Params.y, 0.0);
    float wavelength = max(v_Params.z, 1.0);
    float phase = v_Params.w;

    // v_Params2: harmonic, end-fade px, reserved, reserved
    float harmonic = clamp(v_Params2.x, 0.0, 0.45);
    float fadePx = max(v_Params2.y, 0.0);
    float halfWidth = thickness * 0.5;

    float clampedX = clamp(p.x, left, right);
    float center = waveY(clampedX, left, right, centerY,
                         amplitude, wavelength, phase, harmonic, fadePx);

    // Reconstruct the local tangent from the same continuous function. This gives
    // a perpendicular-distance stroke instead of measuring only vertical distance.
    float derivativeStep = max(0.20, min(0.75, wavelength * 0.035));
    float sx0 = clamp(clampedX - derivativeStep, left, right);
    float sx1 = clamp(clampedX + derivativeStep, left, right);
    float y0 = waveY(sx0, left, right, centerY,
                     amplitude, wavelength, phase, harmonic, fadePx);
    float y1 = waveY(sx1, left, right, centerY,
                     amplitude, wavelength, phase, harmonic, fadePx);
    float dx = max(sx1 - sx0, 0.0001);
    float slope = (y1 - y0) / dx;
    float lineDistance = abs(p.y - center) / sqrt(1.0 + slope * slope) - halfWidth;

    // Round analytic caps. Because the amplitude envelope settles to the centreline
    // at both ends, endpoint centres are stable and do not jump as phase changes.
    float startDistance = length(p - vec2(left, centerY)) - halfWidth;
    float endDistance = length(p - vec2(right, centerY)) - halfWidth;
    float d = p.x < left ? startDistance : (p.x > right ? endDistance : lineDistance);

    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);

    vec4 value = v_Color;
    value.a *= coverage;

#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 framebuffer = max(uScreen.xy, vec2(1.0));
    vec2 logical = max(uScreen.zw, vec2(1.0));
    vec2 logicalScale = logical / framebuffer;
    float clipDistance = combatantClipDistance(combatantLogicalFragCoord());
    value.a *= combatantClipCoverage(clipDistance, logicalScale);
#endif

    fragColor = value;
}
