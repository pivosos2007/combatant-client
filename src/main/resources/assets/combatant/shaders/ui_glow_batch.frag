#version 330 core

in vec4 v_Local;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params;  // kind, radius, softness, glow radius
in vec4 v_Params2; // glow center xy

out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
};

vec2 warpedLocal(vec4 local) {
    float invW = abs(local.z) > 0.000001 ? local.z : 1.0;
    return local.xy / invW;
}

float roundedBoxSdf(vec2 p, vec2 halfSize, float radius) {
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 frag = warpedLocal(v_Local);
    vec2 halfSize = v_Rect.zw * 0.5;
    vec2 center = v_Rect.xy + halfSize;
    float kind = v_Params.x;
    float radius = v_Params.y;
    float softness = max(v_Params.z, 0.0001);
    float glowRadius = max(v_Params.w, 0.0001);
    float d = roundedBoxSdf(frag - center, halfSize, radius);

    float alpha;
    if (kind < 1.5) {
        float outside = step(0.0, d);
        alpha = clamp(1.0 - max(d, 0.0) / glowRadius, 0.0, 1.0) * outside;
    } else {
        float mask = 1.0 - smoothstep(0.0, softness, d);
        float radial = 1.0 - smoothstep(0.0, glowRadius, length(frag - v_Params2.xy));
        alpha = radial * radial * mask;
    }

    fragColor = vec4(v_Color.rgb, v_Color.a * alpha);
}
