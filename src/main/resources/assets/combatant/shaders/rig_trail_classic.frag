#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec3 v_Normal;
in vec4 v_DeformCoord;
out vec4 color;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    float u = sat(v_TexCoord.x);
    float side = abs(v_TexCoord.y * 2.0 - 1.0);

    // Old Trail semantics: one vertical translucent sheet plus a clearly readable top/bottom stroke.
    // The spline/rig owns the bending; the material deliberately stays simple instead of inventing
    // a new procedural effect language.
    float tailFade = smoothstep(0.0, 0.10, u);
    float headFade = 1.0 - smoothstep(0.985, 1.0, u);
    float path = tailFade * headFade;

    float edge = smoothstep(0.76, 0.91, side);
    float edgeCore = smoothstep(0.90, 0.985, side);
    float fill = 1.0 - smoothstep(0.92, 1.0, side);

    float alpha = v_Color.a * path * (fill * 0.58 + edge * 0.24 + edgeCore * 0.38);
    if (alpha <= 0.003) discard;

    vec3 base = max(v_Color.rgb, vec3(0.001));
    vec3 stroke = mix(base, vec3(1.0), 0.28);
    vec3 outColor = base * (0.76 + fill * 0.10);
    outColor = mix(outColor, stroke, edge * 0.32 + edgeCore * 0.42);
    color = vec4(outColor, sat(alpha));
}
