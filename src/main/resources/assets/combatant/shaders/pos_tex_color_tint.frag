#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;

layout (std140) uniform TextureTint {
    vec4 u_Tint;   // rgb = tint, a = strength
    vec4 u_Params; // x = mode
};

in vec2 v_TexCoord;
in vec4 v_Color;

vec3 screen_blend(vec3 a, vec3 b) {
    return 1.0 - (1.0 - a) * (1.0 - b);
}

vec3 overlay_blend(vec3 a, vec3 b) {
    vec3 lo = 2.0 * a * b;
    vec3 hi = 1.0 - 2.0 * (1.0 - a) * (1.0 - b);
    return mix(lo, hi, step(vec3(0.5), a));
}

vec3 rotate_rgb(vec3 c, float selector) {
    if (selector < 0.333) {
        return c.gbr;
    }
    if (selector < 0.666) {
        return c.brg;
    }
    return c;
}

void main() {
    vec4 tex = texture(u_Texture, v_TexCoord);
    float alpha = tex.a * v_Color.a;
    if (alpha <= 0.001) {
        discard;
    }

    vec3 base = tex.rgb * v_Color.rgb;
    vec3 tint = clamp(u_Tint.rgb, 0.0, 1.0);
    float strength = clamp(u_Tint.a, 0.0, 1.0);
    int mode = int(u_Params.x + 0.5);

    vec3 outColor = base;
    if (mode == 1) {
        vec3 layerTint = mix(v_Color.rgb, tint, 0.72);
        outColor = mix(base, tex.rgb * layerTint, strength * 0.72);
    } else if (mode == 2) {
        vec3 atmospheric = screen_blend(base, tint * tex.a);
        outColor = mix(base, atmospheric, strength * 0.64);
    } else if (mode == 3) {
        float selector = fract(dot(v_TexCoord, vec2(12.37, 41.91)));
        vec3 chroma = rotate_rgb(tint, selector);
        vec3 colorized = mix(tex.rgb * chroma, screen_blend(base, chroma), 0.42);
        outColor = mix(base, colorized, strength * 0.72);
    } else if (mode == 4) {
        vec3 warm = mix(tint, tint.gbr, 0.34);
        vec3 rich = mix(overlay_blend(base, warm), screen_blend(base, warm), 0.36);
        outColor = mix(base, rich, strength * 0.82);
    }

    color = vec4(outColor, alpha);
}
