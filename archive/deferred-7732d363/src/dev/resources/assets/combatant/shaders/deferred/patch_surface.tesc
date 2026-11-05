#version 450 core

layout(vertices = 4) out;

layout(location = 0) in vec2 v_Uv[];
layout(location = 1) in vec4 v_Color[];
layout(location = 2) in vec4 v_Params[];
layout(location = 3) in vec4 v_Tess[];
layout(location = 4) in vec4 v_Params2[];
layout(location = 5) flat in uint v_MaterialId[];
layout(location = 6) flat in uint v_MapMask[];
layout(location = 7) flat in uint v_Surface[];

layout(location = 0) out vec2 tc_Uv[];
layout(location = 1) out vec4 tc_Color[];
layout(location = 2) out vec4 tc_Params[];
layout(location = 3) out vec4 tc_Tess[];
layout(location = 4) out vec4 tc_Params2[];
layout(location = 5) flat out uint tc_MaterialId[];
layout(location = 6) flat out uint tc_MapMask[];
layout(location = 7) flat out uint tc_Surface[];

void main() {
    gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
    tc_Uv[gl_InvocationID] = v_Uv[gl_InvocationID];
    tc_Color[gl_InvocationID] = v_Color[gl_InvocationID];
    tc_Params[gl_InvocationID] = v_Params[gl_InvocationID];
    tc_Tess[gl_InvocationID] = v_Tess[gl_InvocationID];
    tc_Params2[gl_InvocationID] = v_Params2[gl_InvocationID];
    tc_MaterialId[gl_InvocationID] = v_MaterialId[gl_InvocationID];
    tc_MapMask[gl_InvocationID] = v_MapMask[gl_InvocationID];
    tc_Surface[gl_InvocationID] = v_Surface[gl_InvocationID];

    barrier();
    if (gl_InvocationID == 0) {
        vec3 center = 0.25 * (gl_in[0].gl_Position.xyz + gl_in[1].gl_Position.xyz
                           + gl_in[2].gl_Position.xyz + gl_in[3].gl_Position.xyz);
        float distanceToCamera = length(center);
        float minFactor = max(1.0, v_Tess[0].y);
        float maxFactor = max(minFactor, v_Tess[0].z);
        float fadeStart = max(0.0, v_Tess[0].w);
        float fadeEnd = max(fadeStart + 0.001, v_Params2[0].x);
        float distanceFade = smoothstep(fadeStart, fadeEnd, distanceToCamera);
        float factor = clamp(mix(maxFactor, minFactor, distanceFade), minFactor, maxFactor);

        gl_TessLevelOuter[0] = factor;
        gl_TessLevelOuter[1] = factor;
        gl_TessLevelOuter[2] = factor;
        gl_TessLevelOuter[3] = factor;
        gl_TessLevelInner[0] = factor;
        gl_TessLevelInner[1] = factor;
    }
}
