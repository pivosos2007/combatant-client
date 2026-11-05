#version 430 core
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout(location = 0) in vec3 Position;
layout(std430, binding = 2) readonly buffer SchematicCamera {
    mat4 u_View;
    vec4 u_Projection;
    vec4 u_Depth;
    vec4 u_Eye;
    vec4 u_Lod;
};
struct Face {
    vec4 p0, p1, p2, p3;  // xyz position; p0..p2.w normal, p3.w texture V span
    vec4 uv01, uv23;     // per-vertex UV; cube UV in block units, model UV in atlas units
    vec4 atlas0, atlas1; // block sprite regions, sampled with wrapping *inside* the sprite
    uvec4 style;         // flat colour, base tint, overlay tint, flags
};
layout(std430, binding = 3) readonly buffer SchematicFaces { Face faces[]; };
layout(std430, binding = 6) readonly buffer SchematicVisible { uint visible[]; };
layout(location = 0) out vec2 v_UV;
layout(location = 1) out float v_VFraction;
layout(location = 2) flat out vec4 v_Atlas0;
layout(location = 3) flat out vec4 v_Atlas1;
layout(location = 4) flat out uvec4 v_Style;

void main() {
#ifdef VULKAN
    uint faceId = visible[uint(gl_InstanceIndex)];
#else
    uint faceId = visible[uint(gl_InstanceID)];
#endif
    Face f = faces[faceId];
    int corner = Position.y > 0.5 ? (Position.x > 0.5 ? 2 : 3)
                                  : (Position.x > 0.5 ? 1 : 0);
    vec4 p = corner == 0 ? f.p0 : corner == 1 ? f.p1 : corner == 2 ? f.p2 : f.p3;
    vec3 normal = vec3(f.p0.w, f.p1.w, f.p2.w);
    // This is the original opaque-face backface policy, without CPU camera culling.
    vec3 centre = (f.p0.xyz + f.p1.xyz + f.p2.xyz + f.p3.xyz) * 0.25;
    if ((f.style.w & 8u) != 0u && dot(normal, u_Eye.xyz - centre) < -0.0001) {
        gl_Position = vec4(0.0, 0.0, -1.0, 1.0); // all 4 vertices collapse outside the clip volume
    } else {
        vec4 view = u_View * vec4(p.xyz, 1.0);
        gl_Position = vec4(view.x * u_Projection.x + view.z * u_Projection.z,
                           view.y * u_Projection.y + view.z * u_Projection.w,
                           view.z * u_Depth.x + u_Depth.y, view.z);
    }
    v_UV = corner == 0 ? f.uv01.xy : corner == 1 ? f.uv01.zw
         : corner == 2 ? f.uv23.xy : f.uv23.zw;
    v_VFraction = (f.style.w & 16u) != 0u ? v_UV.y / max(f.p3.w, 0.0001) : 0.0;
    v_Atlas0 = f.atlas0;
    v_Atlas1 = f.atlas1;
    v_Style = f.style;
}
