#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec2 Position;
layout (location = 1) in vec2 UV0;
layout (location = 2) in vec4 Color;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

#include <combatant:ui_batch.glsl>

layout(location = 0) out vec2 v_TexCoord;
layout(location = 2) out vec4 v_Color;
layout(location = 4) out vec2 v_UiPosition;

void main() {
    vec4 clipPosition = u_Proj * u_ModelView * vec4(Position, 0.0, 1.0);
    gl_Position = clipPosition;
    vec2 ndc = clipPosition.xy / max(abs(clipPosition.w), 0.000001);
    v_UiPosition = vec2(
        (ndc.x * 0.5 + 0.5) * uScreen.z,
        (-ndc.y * 0.5 + 0.5) * uScreen.w
    );
    v_TexCoord = UV0;
    v_Color = Color;
}
