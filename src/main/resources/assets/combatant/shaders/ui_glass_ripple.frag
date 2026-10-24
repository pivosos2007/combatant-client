#version 330 core
#extension GL_ARB_separate_shader_objects : require
#define COMBATANT_GLASS_CUSTOM_VISUAL 1
#define COMBATANT_GLASS_CUSTOM_SHADE 1

#include <combatant:ui_glass_surface.glsl>

// params[0]: speed, radial frequency, displacement in framebuffer pixels, local clarity.
// params[1]: phase offset, red/green/blue tint modulation.
void combatantGlassVisual(CombatantGlassContext c, inout CombatantGlassField f) {
    vec4 p = uGlassVisualParams[0];
    vec2 delta = (c.localUv - c.pointer) * c.sizePx / max(min(c.sizePx.x, c.sizePx.y), 1.0);
    float radius = length(delta);
    float phase = radius * p.y - c.time * p.x * 4.0 + uGlassVisualParams[1].x;
    float envelope = clamp(max(c.activity, c.press), 0.0, 1.0) * exp(-radius * 3.0);
    float bandLimit = 1.0 - smoothstep(1.0, 3.0, fwidth(phase));
    float wave = sin(phase) * envelope * bandLimit;
    vec2 axis = combatantGlassDirection(vec2(delta.x, -delta.y), c.normal);
    f.offsetPx = axis * wave * p.z;
    f.bodyChromaPx = axis * wave * p.z * c.spectralAberration;
    f.bodyCoverage = envelope * bandLimit;
    f.cleanReveal = max(wave, 0.0) * p.w;
    f.spectralPhase = wave;
}

vec3 combatantGlassShade(CombatantGlassContext c, vec3 color) {
    return color * (vec3(1.0) + uGlassVisualParams[1].yzw * c.activity * c.edge);
}

void main() {
    combatantDrawGlass();
}
