#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec4 Color;

#moj_import <combatant:ui_batch.glsl>

out vec4 v_Color;

void main() {
    vec2 logicalSize = max(uScreen.zw, vec2(1.0));
    vec2 clip = Position.xy / logicalSize * 2.0 - 1.0;
    gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);
    v_Color = Color;
}
