#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 fragColor;

uniform sampler2D u_History;
uniform sampler2D u_Mask;

layout (std140) uniform HandGhosting {
    vec4 u_Screen; // xy = full framebuffer size, z = delta seconds, w = time seconds
    vec4 u_Color;
    vec4 u_Params; // decay, strength, blur px, current reject
    vec4 u_Noise0; // quality, octaves, speed, scale
    vec4 u_Noise1; // swirl, contrast, density, history scale
};

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.55;
    mat2 rotation = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 6; ++i) {
        if (i >= octaves) break;
        value += valueNoise(p) * amplitude;
        p = rotation * p * 2.03 + vec2(31.7, 17.3);
        amplitude *= 0.52;
    }
    return value;
}

float maskAt(vec2 uv) {
    vec4 m = texture(u_Mask, clamp(uv, vec2(0.0), vec2(1.0)));
    return max(m.a, max(m.r, max(m.g, m.b)));
}

vec3 historyAt(vec2 uv) {
    return texture(u_History, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
}

void main() {
    int quality = clamp(int(u_Noise0.x + 0.5), 1, 4);
    int requestedOctaves = clamp(int(u_Noise0.y + 0.5), 1, 6);
    int octaves = min(requestedOctaves, quality + 2);

    float dt = clamp(u_Screen.z, 0.0, 0.10);
    float time = u_Screen.w * max(u_Noise0.z, 0.0);
    float scale = max(u_Noise0.w, 0.05);
    float swirl = max(u_Noise1.x, 0.0);
    float contrast = max(u_Noise1.y, 0.05);
    float density = max(u_Noise1.z, 0.0);

    ivec2 historySizeI = max(textureSize(u_History, 0), ivec2(1));
    vec2 historyTexel = 1.0 / vec2(historySizeI);
    vec2 aspect = vec2(max(u_Screen.x, 1.0) / max(u_Screen.y, 1.0), 1.0);
    vec2 p = (v_TexCoord - 0.5) * aspect * scale;

    float current = maskAt(v_TexCoord);
    vec3 previousCenter = historyAt(v_TexCoord);
    float previousCurrent = previousCenter.g;

    float slowTime = time * 0.18;
    float nx = fbm(p * 1.18 + vec2(slowTime * 0.42, -slowTime), octaves);
    float ny = fbm(p * 1.18 + vec2(7.3 - slowTime, 3.1 + slowTime * 0.37), octaves);
    vec2 flow = vec2(nx - 0.5, ny - 0.5);

    // Advect trail history only; the live hand mask must not seed the flow field.
    float frameScale = min(dt * 60.0, 3.0);
    vec2 advectedUv = v_TexCoord - flow * historyTexel * swirl * (0.90 + quality * 0.32) * frameScale;
    float previousTrail = historyAt(advectedUv).r;

    float bodyNoise = fbm(p * 2.05 + flow * (0.65 + swirl * 0.35) + vec2(time * 0.035, -time * 0.07), octaves);
    float turbulence = pow(clamp(bodyNoise * (0.72 + density * 0.45), 0.0, 1.0), 1.0 / contrast);

    // Turbulence may erode a trail, but never amplify temporal energy. Make that erosion depend on
    // elapsed time rather than frame count so Duration behaves the same at 60/144/240 FPS. The
    // explicit extinction term also guarantees that sub-visible history eventually becomes zero.
    float decay = clamp(u_Params.x, 0.0, 0.99995);
    float turbulenceDecay = pow(mix(0.94, 1.0, turbulence), frameScale);
    float decayedTrail = previousTrail * decay * turbulenceDecay;
    decayedTrail = max(decayedTrail - (1.0 - decay) * 0.025, 0.0);

    // History is generated only on the disappearing edge of the hand mask.
    float newlyExposed = max(previousCurrent - current, 0.0);
    newlyExposed = smoothstep(0.015, 0.20, newlyExposed);

    float trail = max(decayedTrail, newlyExposed);
    if (trail < 0.0025) trail = 0.0;

    // R = ghost trail only, G = exact current silhouette, B = turbulence field.
    fragColor = vec4(clamp(trail, 0.0, 1.0), clamp(current, 0.0, 1.0), turbulence, 1.0);
}
