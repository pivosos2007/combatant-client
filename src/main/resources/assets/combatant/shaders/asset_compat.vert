#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout (location = 0) in vec3 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec2 UV1;
layout (location = 3) in vec3 Normal;
layout (location = 4) in vec4 Tangent;
layout (location = 5) in vec4 Color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

layout(location = 0) out vec2 v_UV0;
layout(location = 1) out vec2 v_UV1;
layout(location = 2) out vec4 v_Color;
layout(location = 4) out vec3 v_Normal;
layout(location = 5) out vec4 v_Tangent;

void main() {
    vec4 viewPosition = u_ModelView * vec4(Position, 1.0);
    gl_Position = u_Proj * viewPosition;

    mat3 normalMatrix = transpose(inverse(mat3(u_ModelView)));
    vec3 n = normalize(normalMatrix * Normal);
    vec3 t = normalize(mat3(u_ModelView) * Tangent.xyz);
    t = normalize(t - n * dot(n, t));

    v_UV0 = UV0;
    v_UV1 = UV1;
    v_Color = Color;
    v_Normal = n;
    float modelHandedness = determinant(mat3(u_ModelView)) < 0.0 ? -1.0 : 1.0;
    v_Tangent = vec4(t, Tangent.w * modelHandedness);
}
