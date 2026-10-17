#version 330 core
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D u_BaseColor;
uniform sampler2D u_MetallicRoughness;
uniform sampler2D u_Normal;
uniform sampler2D u_Occlusion;
uniform sampler2D u_Emissive;

layout (std140) uniform AssetMaterial {
    vec4 u_BaseColorFactor;
    vec4 u_EmissiveNormalScale; // rgb=emissive factor, a=normal scale
    vec4 u_SurfaceAlpha;        // x=AO strength, y=roughness, z=metallic, w=alpha cutoff
    vec4 u_Flags;               // x=presence bits, y=UV1 bits, z=alpha mode, w=unlit
    vec4 u_UvTransformX[5];
    vec4 u_UvTransformY[5];
};

layout(location = 0) in vec2 v_UV0;
layout(location = 1) in vec2 v_UV1;
layout(location = 2) in vec4 v_Color;
layout(location = 4) in vec3 v_Normal;
layout(location = 5) in vec4 v_Tangent;

layout(location = 0) out vec4 FragColor;

const uint TEX_BASE = 1u << 0u;
const uint TEX_MR = 1u << 1u;
const uint TEX_NORMAL = 1u << 2u;
const uint TEX_AO = 1u << 3u;
const uint TEX_EMISSIVE = 1u << 4u;

vec2 uvFor(uint bit, uint uv1Mask, int transformIndex) {
    vec2 uv = (uv1Mask & bit) != 0u ? v_UV1 : v_UV0;
    vec4 rowX = u_UvTransformX[transformIndex];
    vec4 rowY = u_UvTransformY[transformIndex];
    return vec2(dot(rowX.xy, uv) + rowX.z, dot(rowY.xy, uv) + rowY.z);
}

vec3 srgbToLinear(vec3 value) {
    vec3 low = value / 12.92;
    vec3 high = pow((value + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), value));
}

void main() {
    uint presence = uint(max(u_Flags.x, 0.0) + 0.5);
    uint uv1Mask = uint(max(u_Flags.y, 0.0) + 0.5);

    vec4 base = u_BaseColorFactor * v_Color;
    if ((presence & TEX_BASE) != 0u) {
        vec4 sampledBase = texture(u_BaseColor, uvFor(TEX_BASE, uv1Mask, 0));
        base *= vec4(srgbToLinear(sampledBase.rgb), sampledBase.a);
    }

    if (u_Flags.z > 0.5 && u_Flags.z < 1.5 && base.a < u_SurfaceAlpha.w) discard;

    vec3 n = normalize(v_Normal);
    if ((presence & TEX_NORMAL) != 0u) {
        vec3 tangentNormal = texture(u_Normal, uvFor(TEX_NORMAL, uv1Mask, 2)).xyz * 2.0 - 1.0;
        tangentNormal.xy *= u_EmissiveNormalScale.a;
        tangentNormal = normalize(tangentNormal);
        vec3 t = normalize(v_Tangent.xyz);
        vec3 b = normalize(cross(n, t)) * (v_Tangent.w < 0.0 ? -1.0 : 1.0);
        n = normalize(mat3(t, b, n) * tangentNormal);
    }

    float roughness = clamp(u_SurfaceAlpha.y, 0.04, 1.0);
    float metallic = clamp(u_SurfaceAlpha.z, 0.0, 1.0);
    if ((presence & TEX_MR) != 0u) {
        vec4 mr = texture(u_MetallicRoughness, uvFor(TEX_MR, uv1Mask, 1));
        roughness *= mr.g;
        metallic *= mr.b;
    }

    float ao = 1.0;
    if ((presence & TEX_AO) != 0u) {
        float sampledAo = texture(u_Occlusion, uvFor(TEX_AO, uv1Mask, 3)).r;
        ao = mix(1.0, sampledAo, clamp(u_SurfaceAlpha.x, 0.0, 1.0));
    }

    vec3 emissive = u_EmissiveNormalScale.rgb;
    if ((presence & TEX_EMISSIVE) != 0u) {
        emissive *= srgbToLinear(texture(u_Emissive, uvFor(TEX_EMISSIVE, uv1Mask, 4)).rgb);
    }

    vec3 color;
    if (u_Flags.w > 0.5) {
        color = base.rgb + emissive;
    } else {
        vec3 l = normalize(vec3(0.35, 0.80, 0.45));
        vec3 v = vec3(0.0, 0.0, 1.0);
        vec3 h = normalize(l + v);
        float ndl = max(dot(n, l), 0.0);
        float ndh = max(dot(n, h), 0.0);
        vec3 f0 = mix(vec3(0.04), base.rgb, metallic);
        float specPower = mix(128.0, 4.0, roughness * roughness);
        vec3 specular = f0 * pow(ndh, specPower) * ndl;
        vec3 diffuse = base.rgb * (1.0 - metallic) * (0.24 + 0.76 * ndl);
        color = (diffuse + specular) * ao + emissive;
    }

    FragColor = vec4(max(color, vec3(0.0)), base.a);
}
