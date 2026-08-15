#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

#moj_import <combatant:ui_batch.glsl>

out vec2 v_TexCoord;
out vec4 v_Color;
out vec2 v_UiPosition;

void main() {
    vec4 clipPosition = u_Proj * u_ModelView * Position;
    gl_Position = clipPosition;
    vec2 ndc = clipPosition.xy / max(abs(clipPosition.w), 0.000001);
    v_UiPosition = vec2(
        (ndc.x * 0.5 + 0.5) * uScreen.z,
        (-ndc.y * 0.5 + 0.5) * uScreen.w
    );
    v_TexCoord = UV0;
    v_Color = Color;
}
