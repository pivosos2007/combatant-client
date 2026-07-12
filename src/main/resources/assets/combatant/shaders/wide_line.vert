#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec4 Color;
layout (location = 2) in vec4 Line;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
    vec4 u_Viewport; // width, height, 1 / width, 1 / height
};

out vec4 v_Color;

void main() {
    vec4 currentClip = u_Proj * u_ModelView * Position;
    vec4 otherClip = u_Proj * u_ModelView * vec4(Line.xyz, 1.0);

    vec2 currentNdc = currentClip.xy / max(abs(currentClip.w), 1.0e-6);
    vec2 otherNdc = otherClip.xy / max(abs(otherClip.w), 1.0e-6);
    vec2 dir = otherNdc - currentNdc;
    float len = length(dir);

    vec2 offsetNdc = vec2(0.0);
    if (len > 1.0e-6) {
        vec2 normal = vec2(-dir.y, dir.x) / len;
        vec2 pxToNdc = vec2(2.0 * u_Viewport.z, 2.0 * u_Viewport.w);
        offsetNdc = normal * (Line.w * 0.5) * pxToNdc;
    }

    currentClip.xy += offsetNdc * currentClip.w;
    gl_Position = currentClip;
    v_Color = Color;
}
