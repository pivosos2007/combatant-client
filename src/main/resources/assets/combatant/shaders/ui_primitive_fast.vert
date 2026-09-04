#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (location = 0) in vec4 Position;
layout (location = 1) in vec4 Local;
layout (location = 2) in vec4 Color;
layout (location = 3) in vec4 Rect;
layout (location = 4) in vec4 Params;
layout (location = 5) in vec4 Params2;
layout (location = 6) in vec4 Params3;
layout (location = 7) in vec4 Params4;
layout (location = 8) in vec4 Params5;

#moj_import <combatant:ui_batch.glsl>
#moj_import <combatant:ui_warp.glsl>

out vec4 v_Local;
out vec4 v_Color;
out vec4 v_Rect;
out vec4 v_Params;
out vec4 v_Params2;
out vec4 v_Params3;
out vec4 v_Params4;
out vec4 v_Params5;

void main() {
    gl_Position = uiProjectiveClipPosition(Position.xy, uScreen.zw, Local);
    v_Local = uiProjectiveSourceLocal(Local);
    v_Color = Color;
    v_Rect = Rect;
    v_Params = Params;
    v_Params2 = Params2;
    v_Params3 = Params3;
    v_Params4 = Params4;
    v_Params5 = Params5;
}
