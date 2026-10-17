#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout (location = 0) in vec3 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;
layout (location = 3) in vec4 Params;
layout (location = 4) in vec4 Params2;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout(location = 0) out vec2 v_TexCoord;
layout(location = 2) out vec4 v_Color;
layout(location = 4) out vec4 v_Params;
layout(location = 5) out vec4 v_Params2;
layout(location = 6) out vec3 v_ViewPosition;
layout(location = 7) out vec3 v_ViewNormal;

void main() {
    vec4 view = u_ModelView * vec4(Position, 1.0);
    gl_Position = u_Proj * view;
    v_TexCoord = UV0;
    v_Color = Color;
    v_Params = Params;
    v_Params2 = Params2;
    v_ViewPosition = view.xyz;
    v_ViewNormal = normalize(mat3(u_ModelView) * Params2.xyz);
}
