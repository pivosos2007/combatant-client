#version 330 core

/*
 * Texture-shaped world mask for local post refraction.
 * R/G encode signed screen flow, B local strength, A coverage.
 */

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;
out vec4 color;

float sourceShape(vec2 uv) {
    vec4 tex = texture(u_Texture, clamp(uv, vec2(0.0), vec2(1.0)));
    float luminance = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    return tex.a * smoothstep(0.015, 0.92, max(luminance, max(tex.r, max(tex.g, tex.b))));
}

void main() {
    vec4 tex = texture(u_Texture, v_TexCoord);
    float shape = sourceShape(v_TexCoord);
    float coverage = shape * v_Color.a;
    if (coverage <= 0.003) discard;

    vec2 texel = 1.0 / vec2(max(textureSize(u_Texture, 0), ivec2(1)));
    float sL = sourceShape(v_TexCoord - vec2(texel.x, 0.0));
    float sR = sourceShape(v_TexCoord + vec2(texel.x, 0.0));
    float sD = sourceShape(v_TexCoord - vec2(0.0, texel.y));
    float sU = sourceShape(v_TexCoord + vec2(0.0, texel.y));
    vec2 gradient = vec2(sR - sL, sU - sD);
    float gradientLength = length(gradient);

    vec2 p = v_TexCoord * 2.0 - 1.0;
    float len = max(length(p), 1.0e-4);
    vec2 radial = p / len;
    vec2 edgeNormal = gradientLength > 1.0e-5 ? gradient / gradientLength : radial;
    vec2 tangent = vec2(-edgeNormal.y, edgeNormal.x);

    float swirl = v_Color.g * 2.0 - 1.0;
    float textureFlow = (tex.r - tex.b) * 1.15 + (tex.g - 0.5) * 0.42;
    float edgeWeight = smoothstep(0.02, 0.32, gradientLength);

    // Follow the actual texture boundary first. Radial flow only stabilizes flat interior regions.
    vec2 flow = mix(radial, edgeNormal, 0.58 + edgeWeight * 0.34);
    flow += tangent * (swirl * (0.42 + edgeWeight * 0.58) + textureFlow * 0.26);
    float flowLen = length(flow);
    if (flowLen > 1.0e-5) flow /= flowLen;

    float localStrength = v_Color.r * (0.58 + edgeWeight * 0.42);
    vec3 encoded = vec3(flow * 0.5 + 0.5, localStrength);
    color = vec4(encoded * coverage, coverage);
}
