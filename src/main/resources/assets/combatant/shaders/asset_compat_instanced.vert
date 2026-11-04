#version 330 core
#extension GL_ARB_separate_shader_objects : require

// Static PBR geometry is stored once in VRAM. The draw selects each instance's
// model transform through gl_InstanceID; no per-frame CPU vertex deformation.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec2 UV1;
layout(location = 3) in vec3 Normal;
layout(location = 4) in vec4 Tangent;
layout(location = 5) in vec4 Color;

layout(std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

// 64 matrices = 4096 bytes. No storage buffers or backend-specific extensions.
layout(std140) uniform AssetInstances {
    mat4 u_InstanceModel[64];
};

layout(location = 0) out vec2 v_UV0;
layout(location = 1) out vec2 v_UV1;
layout(location = 2) out vec4 v_Color;
layout(location = 4) out vec3 v_Normal;
layout(location = 5) out vec4 v_Tangent;

void main() {
#ifdef VULKAN
    int instanceIndex = gl_InstanceIndex;
#else
    int instanceIndex = gl_InstanceID;
#endif
    mat4 modelView = u_ModelView * u_InstanceModel[instanceIndex];
    vec4 viewPosition = modelView * vec4(Position, 1.0);
    gl_Position = u_Proj * viewPosition;

    mat3 normalMatrix = transpose(inverse(mat3(modelView)));
    vec3 n = normalize(normalMatrix * Normal);
    vec3 t = normalize(mat3(modelView) * Tangent.xyz);
    t = normalize(t - n * dot(n, t));

    v_UV0 = UV0;
    v_UV1 = UV1;
    v_Color = Color;
    v_Normal = n;
    float handedness = determinant(mat3(modelView)) < 0.0 ? -1.0 : 1.0;
    v_Tangent = vec4(t, Tangent.w * handedness);
}
