#version 330 core

layout (location = 0) in vec4 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;
layout (location = 3) in vec4 Params;
layout (location = 4) in vec4 Params2;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec2 v_TexCoord;
out vec4 v_Color;
out vec4 v_Params;
out vec4 v_Params2;
out vec3 v_ViewPosition;
out vec3 v_WorldPosition;

void main() {
    vec4 view = u_ModelView * Position;
    gl_Position = u_Proj * view;
    v_TexCoord = UV0;
    v_Color = Color;
    v_Params = Params;
    v_Params2 = Params2;
    v_ViewPosition = view.xyz;
    v_WorldPosition = Position.xyz;
}
