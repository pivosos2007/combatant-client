#version 330 core

in vec4 v_Local;
in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Rect;
in vec4 v_Params; // radius, softness, mask mode, reserved

out vec4 fragColor;

uniform sampler2D u_Texture;

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
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    vec2 frag = warpedLocal(v_Local);
    vec2 halfSize = v_Rect.zw * 0.5;
    float d = roundedBoxSdf(frag - (v_Rect.xy + halfSize), halfSize, v_Params.x);
    float aa = max(max(logicalScale.x, logicalScale.y), max(fwidth(d) * 0.75, 0.0001))
            + max(v_Params.y, 0.0);
    float shapeAlpha = clamp(0.5 - d / max(aa, 0.0001), 0.0, 1.0);
    vec4 texel = texture(u_Texture, v_TexCoord);
    if (v_Params.z > 0.5) {
        fragColor = vec4(v_Color.rgb, v_Color.a * texel.r * shapeAlpha);
    } else {
        fragColor = vec4(texel.rgb * v_Color.rgb, texel.a * v_Color.a * shapeAlpha);
    }
}
