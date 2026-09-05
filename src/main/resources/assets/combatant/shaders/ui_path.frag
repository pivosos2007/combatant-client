#version 330 core

/*
 * Continuous UI path coverage. The CPU tessellator emits a narrow outer fringe;
 * x is distance from the solid boundary and y is the fringe width in logical pixels.
 */

in vec4 v_Color;
in vec4 v_Path;
out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#ifdef COMBATANT_ANALYTIC_CLIP
#moj_import <combatant:ui_geometry.glsl>
#moj_import <combatant:ui_clip.glsl>
#endif

void main() {
    float fringe = max(v_Path.y, 0.0001);
    float edge = max(v_Path.x, 0.0);
    float aa = max(fwidth(edge), fringe * 0.35);
    float pathCoverage = 1.0 - smoothstep(max(0.0, fringe - aa), fringe, edge);
    vec4 value = v_Color;
    value.a *= pathCoverage;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 framebuffer = max(uScreen.xy, vec2(1.0));
    vec2 logical = max(uScreen.zw, vec2(1.0));
    vec2 logicalScale = logical / framebuffer;
    float clipDistance = combatantClipDistance(combatantLogicalFragCoord());
    value.a *= combatantClipCoverage(clipDistance, logicalScale);
#endif
    fragColor = value;
}
