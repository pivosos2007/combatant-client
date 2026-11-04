#version 330 core
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec2 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec4 Rect;
layout(location = 4) in vec4 Params;

#include <combatant:ui_batch.glsl>

layout(location = 0) out vec2 v_TexCoord;
layout(location = 1) flat out vec4 v_Bounds;
layout(location = 2) out vec4 v_Color;
layout(location = 3) flat out vec4 v_Outline;

void main() {
    vec2 clip = (Position - uLayer.xy) / max(uLayer.zw, vec2(1.0)) * 2.0 - 1.0;
    gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);
    v_TexCoord = UV0;
    v_Bounds = Rect;
    v_Color = Color;
    v_Outline = Params;
}
