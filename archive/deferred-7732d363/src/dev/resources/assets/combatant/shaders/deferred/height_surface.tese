#version 450 core

layout(quads, fractional_even_spacing, ccw) in;

layout(location = 0) in vec2 tc_Uv[];
layout(location = 1) in vec4 tc_Color[];
layout(location = 2) in vec4 tc_Params[];
layout(location = 3) in vec4 tc_Tess[];
layout(location = 4) in vec4 tc_Params2[];
layout(location = 5) flat in uint tc_MaterialId[];
layout(location = 6) flat in uint tc_MapMask[];
layout(location = 7) flat in uint tc_Surface[];

layout(binding = 2) uniform sampler2D u_NormalHeightAtlas;
layout(std430, binding = 5) readonly buffer PatchCamera {
    mat4 u_ViewRotation;
    mat4 u_Projection;
    vec4 u_CameraTime;
    vec4 u_Viewport;
};

layout(location = 0) out vec2 te_Uv;
layout(location = 1) out vec4 te_Color;
layout(location = 2) out vec4 te_Params;
layout(location = 3) out vec3 te_ViewPosition;
layout(location = 4) flat out uint te_MaterialId;
layout(location = 5) flat out uint te_MapMask;
layout(location = 6) flat out uint te_Surface;
layout(location = 7) out float te_SkyLight;

vec3 bilerp3(vec3 a, vec3 b, vec3 c, vec3 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}
vec2 bilerp2(vec2 a, vec2 b, vec2 c, vec2 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}
vec4 bilerp4(vec4 a, vec4 b, vec4 c, vec4 d, vec2 uv) {
    return mix(mix(a, b, uv.x), mix(d, c, uv.x), uv.y);
}

void main() {
    vec2 patchUv = gl_TessCoord.xy;
    vec3 p = bilerp3(gl_in[0].gl_Position.xyz, gl_in[1].gl_Position.xyz,
                     gl_in[2].gl_Position.xyz, gl_in[3].gl_Position.xyz, patchUv);
    vec2 uv = bilerp2(tc_Uv[0], tc_Uv[1], tc_Uv[2], tc_Uv[3], patchUv);
    vec4 color = bilerp4(tc_Color[0], tc_Color[1], tc_Color[2], tc_Color[3], patchUv);
    vec4 params = bilerp4(tc_Params[0], tc_Params[1], tc_Params[2], tc_Params[3], patchUv);

    vec3 edgeU = mix(gl_in[1].gl_Position.xyz - gl_in[0].gl_Position.xyz,
                     gl_in[2].gl_Position.xyz - gl_in[3].gl_Position.xyz, patchUv.y);
    vec3 edgeV = mix(gl_in[3].gl_Position.xyz - gl_in[0].gl_Position.xyz,
                     gl_in[2].gl_Position.xyz - gl_in[1].gl_Position.xyz, patchUv.x);
    vec3 localNormal = normalize(cross(edgeU, edgeV));

    if ((tc_MapMask[0] & (1u << 6u)) != 0u) {
        float height = texture(u_NormalHeightAtlas, uv).a;
        p += localNormal * ((height - 0.5) * tc_Tess[0].x);
    }

    vec4 viewPosition = u_ViewRotation * vec4(p, 1.0);
    gl_Position = u_Projection * viewPosition;
    te_Uv = uv;
    te_Color = color;
    te_Params = params;
    te_ViewPosition = viewPosition.xyz;
    te_MaterialId = tc_MaterialId[0];
    te_MapMask = tc_MapMask[0];
    te_Surface = tc_Surface[0];
    te_SkyLight = bilerp4(tc_Params2[0], tc_Params2[1], tc_Params2[2], tc_Params2[3], patchUv).y;
}
