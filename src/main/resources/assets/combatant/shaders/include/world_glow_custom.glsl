/*
 * User-defined world glow effects. Overridable through resource packs:
 *   assets/combatant/shaders/include/world_glow_custom.glsl
 * Do not add #version, uniforms or varyings. All slots must retain these signatures.
 * This include is expanded by CombatantShaderSources and compiled for both GL and Vulkan.
 * See docs/world-glow-customization.md for the contract and an example.
 */
CombatantGlowVisual combatantGlowCustom1(vec2 uv, vec3 viewPosition, float t, float phase) {
    return combatantGlowVisual(vec3(1.0), 1.0, 0.0);
}
CombatantGlowVisual combatantGlowCustom2(vec2 uv, vec3 viewPosition, float t, float phase) {
    return combatantGlowVisual(vec3(1.0), 1.0, 0.0);
}
CombatantGlowVisual combatantGlowCustom3(vec2 uv, vec3 viewPosition, float t, float phase) {
    return combatantGlowVisual(vec3(1.0), 1.0, 0.0);
}
CombatantGlowVisual combatantGlowCustom4(vec2 uv, vec3 viewPosition, float t, float phase) {
    return combatantGlowVisual(vec3(1.0), 1.0, 0.0);
}
