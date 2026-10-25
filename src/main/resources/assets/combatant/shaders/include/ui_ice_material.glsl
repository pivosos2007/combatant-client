/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
#ifndef COMBATANT_ICE_MATERIAL
#define COMBATANT_ICE_MATERIAL

// Samplers are declared by the solid/glass caller. Alpha in color is AO, not opacity.
struct CombatantIceSurface {
    vec2 facetNormal;
    vec3 albedo;
    float roughness;
    float ao;
    float crack;
    float frost;
    float rim;
    float coverage;
    float specular;
    float tint;
};

#include <combatant:ui_pbr_math.glsl>
// Caller includes ui_voronoi_structure.glsl once before this ICE implementation.
// Avoid duplicate UiVoronoiCell declarations after UniformCodegen expands includes.

vec3 iceLinear(vec3 c) { return combatantUiToLinear(c); }
vec3 iceSrgb(vec3 c) { return combatantUiToSrgb(c); }

// Detail: tile px, normal, height, roughness variation. Finish: specular, tint, angle, roughness.
// Crystalline ice is not a photograph pasted on glass. The PBR texture is
// sub-facet microscopic roughness only; actual macro facets, joints, frost
// deposition and their normals are generated in surface pixel coordinates.
CombatantIceSurface combatantIceSurface(vec2 uv, vec2 sizePx, float signedDistancePx,
                                        vec4 controls, vec4 detail, vec4 finish) {
    float angle = radians(finish.z);
    mat2 rotation = mat2(cos(angle), sin(angle), -sin(angle), cos(angle));
    vec2 physical = uv * sizePx;
    vec2 p = transpose(rotation) * physical / max(detail.x, 16.0)
            + fract(controls.x * vec2(0.1371, 0.0793));
    vec2 dx = dFdx(p), dy = dFdy(p);
    vec4 micro = textureGrad(u_MaterialMicro, p, dx, dy);
    vec4 colorAo = textureGrad(u_MaterialColor, p, dx, dy);
    vec2 xy = clamp(micro.rg * 2.0 - 1.0, vec2(-1.0), vec2(1.0));
    vec3 fineNormal = normalize(vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0001))));
    // One 20–60 px crystal facet, independent of the packed PBR tile size.
    float facetPx = clamp(62.0 / max(controls.y, 0.5), 14.0, 76.0);
    UiVoronoiCell crystal = uiVoronoi(physical / facetPx, 0.79, controls.x + 5.7);
    float seamPx = crystal.border * facetPx;
    float seamAa = max(fwidth(seamPx), 0.7);
    float seam = 1.0 - smoothstep(0.45, 1.7 + seamAa, seamPx);
    float ridge = 1.0 - smoothstep(0.0, 5.5, seamPx);
    // Constant-per-cell facet orientation, not per-pixel snow noise.
    float phase = 6.2831853 * fract(crystal.id * 7.618);
    vec2 facetNormal = vec2(cos(phase), sin(phase))
            * (0.12 + 0.19 * clamp(finish.y, 0.0, 1.0));
    facetNormal += crystal.edgeNormal * ridge * 0.15;
    vec2 normal = rotation * (fineNormal.xy * detail.y * 0.20 * vec2(1.0, -1.0)
                           + facetNormal * clamp(finish.y + 0.15, 0.1, 1.0));
    normal *= min(1.0, 0.94 / max(length(normal), 0.0001));
    float roughness = clamp(finish.w + (micro.b - 0.59) * detail.w * 0.20
                            + seam * controls.z * 0.12, 0.06, 0.88);
    // Frost nucleates at the actual SDF perimeter and along crystal joins.
    float rimWidth = clamp(min(sizePx.x, sizePx.y) * 0.13, 3.5, 40.0);
    float variedRim = rimWidth * (0.55 + 0.85 * crystal.id);
    float rim = 1.0 - smoothstep(1.0, variedRim, max(-signedDistancePx, 0.0));
    float frost = clamp(controls.z * (rim * 0.72 + ridge * 0.20
                            + seam * 0.08), 0.0, 1.0);
    float cracks = clamp(controls.w * seam * (0.55 + 0.45 * crystal.id), 0.0, 1.0);
    float coverage = clamp(rim * controls.z * 0.70 + cracks * 0.75
                    + ridge * controls.z * 0.15, 0.0, 1.0);
    // Source albedo is intentionally NOT used as a snowy colour wash. The map
    // informs only tiny optical inclusions; the colour is theme-controlled.
    vec3 inclusion = iceLinear(colorAo.rgb) * 0.08 + vec3(0.92, 0.95, 1.0) * 0.92;
    return CombatantIceSurface(normal, inclusion, roughness, colorAo.a,
            cracks, frost, rim, coverage, finish.x, finish.y);
}

// Ice/glass is a transmissive layer, NOT an opaque PBR coating. The optical
// glass pipeline has already refracted and blurred the scene before this stage.
// Only texture-driven frost/crack regions may perturb that result. A completely
// un-frozen lens must return the exact incoming colour (including its blend mode).
float combatantIceGlassCoverage(CombatantIceSurface ice) {
    // Coverage is the *strength of an optical perturbation*, not an alpha mask.
    // Use separate crack and frost features, with a narrow SDF-driven rim.
    return clamp(ice.frost * 0.95 + ice.crack * 0.90
            + ice.rim * (ice.frost * 0.30 + ice.crack * 0.10), 0.0, 1.0);
}

vec3 combatantIceGlassColor(vec3 transmittedSrgb, CombatantIceSurface ice,
                            vec2 shapeNormal) {
    float coverage = combatantIceGlassCoverage(ice);
    if (coverage <= 0.0001) return transmittedSrgb;

    // ICE glass is transmissive: the scene is the dominant source of radiance.
    // The PBR normals and crack/frost masks define a weak, spatially varying
    // scattering/reflection response, without painting albedo over the scene.
    vec2 xy = ice.facetNormal + shapeNormal * ice.rim * 0.10;
    xy *= min(1.0, 0.999 / max(length(xy), 1e-6));
    vec3 n = normalize(vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0001))));
    vec3 light = normalize(vec3(-0.45, -0.65, 0.9));
    vec3 view = vec3(0.0, 0.0, 1.0);
    vec3 halfVector = normalize(light + view);
    float nl = max(dot(n, light), 0.0);
    float alpha = max(ice.roughness * ice.roughness, 0.015);
    vec3 tangent = normalize(vec3(1.0, 0.0, 0.0) - n * n.x);
    vec3 bitangent = normalize(cross(n, tangent));
    float distribution = combatantUiGgxDistribution(n, halfVector, tangent, bitangent, alpha, alpha);
    float visibility = combatantUiSmithVisibility(n, view, light, tangent, bitangent, alpha, alpha);
    float fresnel = combatantUiFresnel(vec3(0.01801), max(dot(view, halfVector), 0.0)).r;
    float brdfReflection = min(distribution * visibility * nl * fresnel * ice.specular, 0.08);

    // Only crystalline edges scatter substantially. Clear facets transmit
    // almost all incoming light; the original glass optics remain intact.
    float facetActivity = smoothstep(0.025, 0.20, length(ice.facetNormal));
    float frostScattering = clamp(ice.frost * (0.11 + 0.075 * facetActivity)
            + ice.crack * (0.065 + 0.025 * facetActivity), 0.0, 0.18);
    vec3 transmitted = iceLinear(transmittedSrgb);
    // A restrained cool scatter, not source-albedo replacement. Linear-light
    // blend preserves input color where the material has no microstructure.
    vec3 scatterTint = mix(vec3(0.72, 0.83, 0.96), ice.albedo,
            clamp(ice.tint, 0.0, 1.0) * 0.12);
    vec3 result = mix(transmitted, scatterTint, frostScattering);
    // Subtle dielectric response; avoid high-frequency sparkle over text.
    float reflection = coverage * (brdfReflection * 0.27
            + 0.021 * facetActivity * (0.3 + 0.7 * nl));
    result += (vec3(1.0) - result) * vec3(0.81, 0.91, 1.0) * reflection;
    return iceSrgb(clamp(result, vec3(0.0), vec3(1.0)));
}

vec3 combatantIceColor(vec3 color, CombatantIceSurface ice, vec2 shapeNormal, vec4 optics) {
    vec2 xy = ice.facetNormal + shapeNormal * ice.rim * 0.15;
    // Reconstruct a normalized upper-hemisphere normal even for extreme controls.
    xy *= min(1.0, 0.999 / max(length(xy), 1e-6));
    vec3 n = normalize(vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0001))));
    vec3 light = normalize(vec3(-0.45, -0.65, 0.9));
    vec3 halfVector = normalize(light + vec3(0.0, 0.0, 1.0));
    float nl = max(dot(n, light), 0.0);
    float rough = clamp(ice.roughness, 0.045, 1.0);
    float alpha = max(rough * rough, 0.015);
    vec3 tangent = normalize(vec3(1.0, 0.0, 0.0) - n * n.x);
    vec3 bitangent = normalize(cross(n, tangent));
    vec3 view = vec3(0.0, 0.0, 1.0);
    float distribution = combatantUiGgxDistribution(n, halfVector, tangent, bitangent, alpha, alpha);
    float visibility = combatantUiSmithVisibility(n, view, light, tangent, bitangent, alpha, alpha);
    // IOR 1.31: F0 = ((1.31 - 1)/(1.31 + 1))^2 = 0.01801.
    vec3 fresnel = combatantUiFresnel(vec3(0.01801), max(dot(view, halfVector), 0.0));
    float specular = distribution * visibility * nl * fresnel.r * ice.specular;
    // Solid ICE: dielectric crystalline depth and per-cell normals instead
    // of replacing the surface with its snowy source photograph.
    vec3 tint = iceLinear(color);
    vec3 deepTransmission = tint * mix(vec3(0.96, 0.99, 1.0),
            vec3(0.67, 0.83, 0.96), clamp(optics.x, 0.0, 1.0) * 0.30);
    float facetLight = clamp(nl * 0.72 + 0.30, 0.0, 1.0);
    float crystalShadow = 1.0 - ice.crack * 0.24 - ice.frost * 0.11;
    vec3 result = deepTransmission * mix(1.0, facetLight, clamp(optics.z, 0.0, 1.0));
    result *= crystalShadow;
    // Only frost nucleation points are milky; the facets themselves are clear.
    result = mix(result, mix(tint, vec3(0.73, 0.84, 0.95), 0.40),
                 clamp(ice.frost * 0.30, 0.0, 0.30));
    result += vec3(min(specular, 0.16)) * vec3(0.82, 0.92, 1.0);
    return iceSrgb(clamp(result, vec3(0.0), vec3(1.0)));
}

#endif
