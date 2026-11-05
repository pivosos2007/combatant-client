#version 330 core

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec2 LocalSurface;
layout(location = 3) in vec4 Color;
layout(location = 4) in vec4 Params;
layout(location = 5) in vec4 Tess;
layout(location = 6) in vec4 Params2;
layout(location = 7) in vec4 Optical;
layout(location = 8) in uint MaterialId;
layout(location = 9) in uint FluidTypeId;
layout(location = 10) in uint MaterialMapMask;
layout(location = 11) in uint MaterialFeatureMask;
layout(location = 12) in uint SurfaceFlags;
layout(location = 13) in uint MaterialSurface;

layout(std140) uniform MeshData {
    mat4 u_MeshProjection;
    mat4 u_MeshModelView;
};

layout(std140) uniform WaterFrame {
    mat4 u_CurrentView;
    mat4 u_CurrentProjection;
    mat4 u_CurrentInverseProjection;
    mat4 u_CurrentInverseView;
    mat4 u_PreviousView;
    mat4 u_PreviousProjection;
    vec4 u_CurrentCameraTime;
    vec4 u_PreviousCameraTime;
    vec4 u_Viewport;
    vec4 u_DepthTransform;
    vec4 u_Deformation0;
    vec4 u_Deformation1;
    vec4 u_MediumReflection;
    vec4 u_MediumBoundary;
    vec4 u_OpticalAbsorption;
    vec4 u_OpticalScattering;
    vec4 u_ReflectionMeta;
};

#moj_import <combatant:deferred_water_surface_model.glsl>

out vec2 v_Uv;
out vec2 v_LocalSurface;
out vec4 v_Color;
out vec4 v_Params;
out vec4 v_Optical;
out vec3 v_ViewPosition;
out vec3 v_WorldPosition;
out vec3 v_ViewNormal;
out vec4 v_CurrentClip;
out vec4 v_PreviousClip;
out vec3 v_PreviousViewPosition;
out float v_SkyLight;
flat out uint v_MaterialId;
flat out uint v_FluidTypeId;
flat out uint v_MapMask;
flat out uint v_FeatureMask;
flat out uint v_SurfaceFlags;
flat out uint v_Surface;

void main() {
    vec3 baseWorld = Position + u_CurrentCameraTime.xyz;
    float skyLight = clamp(Params2.y, 0.0, 1.0);
    bool topSurface = (SurfaceFlags & COMBATANT_WATER_FLAG_TOP_SURFACE) != 0u;
    float currentDisplacement = topSurface
            ? combatantWaterDisplacement(baseWorld, skyLight, u_CurrentCameraTime.w) : 0.0;
    float previousDisplacement = topSurface
            ? combatantWaterDisplacement(baseWorld, skyLight, u_PreviousCameraTime.w) : 0.0;
    vec3 currentWorld = baseWorld + vec3(0.0, currentDisplacement, 0.0);
    vec3 previousWorld = baseWorld + vec3(0.0, previousDisplacement, 0.0);
    vec3 currentRelative = currentWorld - u_CurrentCameraTime.xyz;
    vec3 previousRelative = previousWorld - u_PreviousCameraTime.xyz;
    vec4 currentView = u_CurrentView * vec4(currentRelative, 1.0);
    vec4 previousView = u_PreviousView * vec4(previousRelative, 1.0);
    vec4 currentClip = u_CurrentProjection * currentView;
    vec4 previousClip = u_PreviousProjection * previousView;

    vec3 normalWorld = normalize(vec3(Params2.w, Optical.z, Optical.w));

    gl_Position = currentClip;
    v_Uv = UV0;
    v_LocalSurface = LocalSurface;
    v_Color = Color;
    v_Params = Params;
    v_Optical = Optical;
    v_ViewPosition = currentView.xyz;
    v_WorldPosition = currentWorld;
    v_ViewNormal = normalize(mat3(u_CurrentView) * normalWorld);
    v_CurrentClip = currentClip;
    v_PreviousClip = previousClip;
    v_PreviousViewPosition = previousView.xyz;
    v_SkyLight = skyLight;
    v_MaterialId = MaterialId;
    v_FluidTypeId = FluidTypeId;
    v_MapMask = MaterialMapMask;
    v_FeatureMask = MaterialFeatureMask;
    v_SurfaceFlags = SurfaceFlags;
    v_Surface = MaterialSurface;
}
