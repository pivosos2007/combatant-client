#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform ShaderEspSmoke {
    vec4 u_Rect;    // xy = location, zw = resolution
    vec4 u_Params0; // x = time, y = scale, z = speed, w = alpha
    vec4 u_Params1; // x = octaves, y = contrast, z = override color, w = intensity
    vec4 u_Color0;  // bright smoke color when override is enabled
    vec4 u_Color1;  // mid smoke color when override is enabled
    vec4 u_Color2;  // dark smoke color when override is enabled
};

in vec2 v_TexCoord;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int octaves) {
    float v = 0.0;
    float a = 0.52;
    mat2 r = mat2(0.86, 0.50, -0.50, 0.86);
    for (int i = 0; i < 6; i++) {
        if (i >= octaves) break;
        v += a * noise(p);
        p = r * p * 2.03 + vec2(17.13, 9.27);
        a *= 0.50;
    }
    return v;
}

vec3 deriveBright(vec3 base) {
    return mix(base, vec3(1.0), 0.20);
}

vec3 deriveDark(vec3 base) {
    return mix(base, vec3(0.02, 0.025, 0.04), 0.50);
}

void main() {
    vec4 mask = texture(u_Texture, v_TexCoord);
    if (mask.a <= 0.001) {
        discard;
    }

    vec2 resolution = max(u_Rect.zw, vec2(1.0));
    float time = u_Params0.x * u_Params0.z;
    float scale = max(u_Params0.y, 0.05);
    float passAlpha = clamp(u_Params0.w, 0.0, 1.0);
    int octaves = int(clamp(u_Params1.x, 1.0, 6.0) + 0.5);
    float contrast = max(u_Params1.y, 0.05);
    float overrideColor = step(0.5, u_Params1.z);
    float intensity = clamp(u_Params1.w, 0.0, 4.0);

    vec2 uv = gl_FragCoord.xy / resolution;
    vec2 aspectUv = vec2(uv.x * resolution.x / max(resolution.y, 1.0), uv.y);
    vec2 domain = aspectUv * scale;

    vec2 q = vec2(
        fbm(domain + vec2(0.0, time * 0.16), octaves),
        fbm(domain + vec2(4.1, 2.7) - vec2(time * 0.11, time * 0.08), octaves)
    );
    vec2 r = vec2(
        fbm(domain + q * 1.55 + vec2(1.7, 9.2) + time * 0.22, octaves),
        fbm(domain + q * 1.25 + vec2(8.3, 2.8) - time * 0.18, octaves)
    );
    float f = fbm(domain + r * 1.85 + vec2(time * 0.06, -time * 0.04), octaves);
    float filament = smoothstep(0.18, 0.95, pow(clamp(f * 1.22, 0.0, 1.0), contrast));
    float soft = clamp(f * f * f + 0.60 * f * f + 0.50 * f, 0.0, 1.35);

    vec3 entityBase = max(mask.rgb, vec3(0.001));
    vec3 c0 = mix(deriveBright(entityBase), u_Color0.rgb, overrideColor);
    vec3 c1 = mix(entityBase, u_Color1.rgb, overrideColor);
    vec3 c2 = mix(deriveDark(entityBase), u_Color2.rgb, overrideColor);

    vec3 smoke = mix(c2, c1, clamp(length(q), 0.0, 1.0));
    smoke = mix(smoke, c0, clamp(length(r) * 0.72 + filament * 0.22, 0.0, 1.0));
    smoke *= (0.58 + soft * 0.72) * intensity;

    float alphaShape = mix(0.52, 1.0, filament);
    color = vec4(smoke, mask.a * passAlpha * alphaShape);
}
