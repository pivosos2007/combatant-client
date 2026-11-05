#version 450 core

layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec2 a_Uv;
layout(location = 2) in vec4 a_Color;
layout(location = 3) in vec4 a_Params;
layout(location = 4) in vec4 a_Tess;
layout(location = 5) in vec4 a_Params2;
layout(location = 6) in uint a_MaterialId;
layout(location = 7) in uint a_MapMask;
layout(location = 8) in uint a_Surface;

layout(location = 0) out vec2 v_Uv;
layout(location = 1) out vec4 v_Color;
layout(location = 2) out vec4 v_Params;
layout(location = 3) out vec4 v_Tess;
layout(location = 4) out vec4 v_Params2;
layout(location = 5) flat out uint v_MaterialId;
layout(location = 6) flat out uint v_MapMask;
layout(location = 7) flat out uint v_Surface;

void main() {
    gl_Position = vec4(a_Position, 1.0);
    v_Uv = a_Uv;
    v_Color = a_Color;
    v_Params = a_Params;
    v_Tess = a_Tess;
    v_Params2 = a_Params2;
    v_MaterialId = a_MaterialId;
    v_MapMask = a_MapMask;
    v_Surface = a_Surface;
}
