#version 330 core

/*
 * Combatant widget material vertex contract.
 * Params6 carries material direction/intensity controls explicitly rather than packing them into color alpha.
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
layout (location = 9) in vec4 Params6;

layout (std140) uniform MeshData {
    mat4 u_Proj;
    mat4 u_ModelView;
};

out vec4 v_Local;
out vec4 v_Color;
out vec4 v_Rect;
out vec4 v_CornerModes;
out vec4 v_CornerExtentX;
out vec4 v_CornerExtentY;
out vec4 v_EdgeModes;
out vec4 v_EdgeData;
out vec4 v_MaterialData;

void main() {
    gl_Position = u_Proj * u_ModelView * Position;
    v_Local = Local;
    v_Color = Color;
    v_Rect = Rect;
    v_CornerModes = Params;
    v_CornerExtentX = Params2;
    v_CornerExtentY = Params3;
    v_EdgeModes = Params4;
    v_EdgeData = Params5;
    v_MaterialData = Params6;
}
