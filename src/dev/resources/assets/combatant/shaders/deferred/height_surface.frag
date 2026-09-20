#version 450 core

layout(binding = 0) uniform sampler2D u_BlockAtlas;
layout(binding = 1) uniform sampler2D u_AlbedoAtlas;
layout(binding = 2) uniform sampler2D u_NormalHeightAtlas;
layout(binding = 3) uniform sampler2D u_SurfaceAtlas;
layout(binding = 4) uniform sampler2D u_SpecularAtlas;

layout(location = 0) in vec2 te_Uv;
layout(location = 1) in vec4 te_Color;
layout(location = 2) in vec4 te_Params;
layout(location = 3) in vec3 te_ViewPosition;
layout(location = 4) flat in uint te_MaterialId;
layout(location = 5) flat in uint te_MapMask;
layout(location = 6) flat in uint te_Surface;
layout(location = 7) in float te_SkyLight;

layout(location = 0) out vec4 outScene;
layout(location = 1) out vec4 outSurface;
layout(location = 2) out vec4 outGeometry;
layout(location = 3) out vec4 outAuxiliary;
layout(location = 4) out vec4 outMaterial;
layout(location = 5) out uint outMaterialId;

float unpack8(uint packedValue, uint shift) {
    return float((packedValue >> shift) & 255u) / 255.0;
}
vec2 encodeOct(vec3 n) {
    n /= abs(n.x) + abs(n.y) + abs(n.z);
    vec2 e = n.xy;
    if (n.z < 0.0) e = (1.0 - abs(e.yx)) * vec2(e.x >= 0.0 ? 1.0 : -1.0, e.y >= 0.0 ? 1.0 : -1.0);
    return e * 0.5 + 0.5;
}
float encodeDistance(float d) {
    return clamp((log2(1.0 + max(d, 0.0)) + 1.0) / 17.0, 1.0 / 255.0, 1.0);
}

void main() {
    vec4 texel = texture(u_BlockAtlas, te_Uv);
    if ((te_MapMask & (1u << 7u)) != 0u) {
        vec4 overrideAlbedo = texture(u_AlbedoAtlas, te_Uv);
        texel.rgb = overrideAlbedo.rgb;
        texel.a *= overrideAlbedo.a;
    }
    vec4 base = texel * te_Color;
    if (base.a < 0.1) discard;

    vec3 geometricNormal = normalize(cross(dFdx(te_ViewPosition), dFdy(te_ViewPosition)));
    if (!gl_FrontFacing) geometricNormal = -geometricNormal;
    vec3 viewNormal = geometricNormal;
    if ((te_MapMask & 1u) != 0u) {
        vec3 tangentNormal = normalize(texture(u_NormalHeightAtlas, te_Uv).rgb * 2.0 - 1.0);
        vec3 dpdx = dFdx(te_ViewPosition);
        vec3 dpdy = dFdy(te_ViewPosition);
        vec2 duvdx = dFdx(te_Uv);
        vec2 duvdy = dFdy(te_Uv);
        float det = duvdx.x * duvdy.y - duvdx.y * duvdy.x;
        if (abs(det) > 1.0e-7) {
            vec3 tangent = normalize((dpdx * duvdy.y - dpdy * duvdx.y) / det);
            vec3 bitangent = normalize(cross(geometricNormal, tangent));
            viewNormal = normalize(mat3(tangent, bitangent, geometricNormal) * tangentNormal);
        }
    }

    float materialAo = 1.0;
    float roughness = unpack8(te_Surface, 0u);
    float metallic = unpack8(te_Surface, 8u);
    float dielectricF0 = unpack8(te_Surface, 16u);
    float emission = unpack8(te_Surface, 24u);
    if ((te_MapMask & 0x3Eu) != 0u) {
        vec4 s = texture(u_SurfaceAtlas, te_Uv);
        vec4 sp = texture(u_SpecularAtlas, te_Uv);
        if ((te_MapMask & (1u << 1u)) != 0u) materialAo = s.r;
        if ((te_MapMask & (1u << 2u)) != 0u) roughness = s.g;
        if ((te_MapMask & (1u << 3u)) != 0u) metallic = s.b;
        if ((te_MapMask & (1u << 4u)) != 0u) dielectricF0 = sp.r;
        if ((te_MapMask & (1u << 5u)) != 0u) emission = s.a;
    }

    float ao = clamp(te_Params.z * materialAo, 1.0 / 255.0, 1.0);
    float blockLight = clamp(te_Params.w, 0.0, 1.0);
    float skyLight = clamp(te_SkyLight, 0.0, 1.0);
    float dist = length(te_ViewPosition);

    outScene = vec4(base.rgb, 1.0);
    outSurface = vec4(base.rgb, ao);
    outGeometry = vec4(encodeOct(viewNormal), blockLight, skyLight);
    outAuxiliary = vec4(encodeDistance(dist), encodeDistance(dist), 1.0, 0.0);
    outMaterial = vec4(clamp(roughness, 0.0, 1.0), clamp(metallic, 0.0, 1.0),
                       clamp(dielectricF0, 0.0, 1.0), max(emission, 0.0));
    outMaterialId = te_MaterialId;
}
