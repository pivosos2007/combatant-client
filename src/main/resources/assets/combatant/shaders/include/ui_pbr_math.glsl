/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
#ifndef COMBATANT_UI_PBR_MATH_GLSL
#define COMBATANT_UI_PBR_MATH_GLSL

// Scene/UI colours are encoded sRGB; normals, AO, height, roughness and metallic
// are linear UNORM data. Perform BRDF work in linear light and encode only once.
vec3 combatantUiToLinear(vec3 encoded) {
    vec3 c = max(encoded, vec3(0.0));
    vec3 low = c / 12.92;
    vec3 high = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), c));
}

vec3 combatantUiToSrgb(vec3 linearColor) {
    vec3 c = max(linearColor, vec3(0.0));
    vec3 low = c * 12.92;
    vec3 high = 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055;
    return mix(low, high, step(vec3(0.0031308), c));
}

vec3 combatantUiFresnel(vec3 f0, float viewDotHalf) {
    float f = pow(1.0 - clamp(viewDotHalf, 0.0, 1.0), 5.0);
    return f0 + (vec3(1.0) - f0) * f;
}

// Anisotropic GGX normal distribution and height-correlated Smith visibility.
// Both use alpha = perceptualRoughness^2 and the same tangent basis.
float combatantUiGgxDistribution(vec3 normal, vec3 halfVector,
                                 vec3 tangent, vec3 bitangent,
                                 float alphaX, float alphaY) {
    float nh = max(dot(normal, halfVector), 0.0);
    float ht = dot(tangent, halfVector) / alphaX;
    float hb = dot(bitangent, halfVector) / alphaY;
    float d = ht * ht + hb * hb + nh * nh;
    return nh > 0.0 ? 1.0 / (3.141592653589793 * alphaX * alphaY * max(d * d, 1e-7)) : 0.0;
}

float combatantUiSmithVisibility(vec3 normal, vec3 view, vec3 light,
                                 vec3 tangent, vec3 bitangent,
                                 float alphaX, float alphaY) {
    float nv = max(dot(normal, view), 0.0);
    float nl = max(dot(normal, light), 0.0);
    if (nv <= 0.0 || nl <= 0.0) return 0.0;
    vec2 v = vec2(dot(view, tangent) * alphaX, dot(view, bitangent) * alphaY);
    vec2 l = vec2(dot(light, tangent) * alphaX, dot(light, bitangent) * alphaY);
    float lambdaV = sqrt(dot(v, v) + nv * nv);
    float lambdaL = sqrt(dot(l, l) + nl * nl);
    return 0.5 / max(nl * lambdaV + nv * lambdaL, 1e-5);
}

#endif
