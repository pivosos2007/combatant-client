/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <combatant:ui_aa.glsl>
#include <combatant:ui_geometry.glsl>
#include <combatant:ui_voronoi_structure.glsl>
#include <combatant:ui_pbr_math.glsl>


layout(location = 1) in vec4 v_Local;
layout(location = 2) in vec4 v_Color;
layout(location = 3) in vec4 v_Rect;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec4 v_Params3;
layout(location = 7) in vec4 v_Params4;
layout(location = 8) in vec4 v_Params5;
layout(location = 9) in vec4 v_Params6;
layout(location = 10) in vec4 v_Params7;
layout(location = 11) in vec4 v_Params8;
layout(location = 12) in vec4 v_Params9;
layout(location = 13) in vec4 v_Params10;

layout(location = 0) out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

#include <combatant:ui_climate_droplets.glsl>

#ifdef COMBATANT_CLIMATE_FIELD
// Immutable snapshot of the actual UI target, taken after the underlying panel
// was rendered. Unlike an unblurred world capture, this preserves any existing
// panel blur, Liquid Glass, tint, PBR finish and lower UI layers.
uniform sampler2D u_ClimateBackdrop;
#endif

// Pointer/time uniforms are independent from optional scene lighting.
layout (std140) uniform UIGlassFrame {
    vec4 uGlassPointer;
    vec4 uGlassMotion;
    vec4 uGlassClock;
};

#ifdef COMBATANT_MATERIAL_MICROTEXTURE
uniform sampler2D u_MaterialMicro;
uniform sampler2D u_MaterialColor;
#include <combatant:ui_ice_material.glsl>
#endif

#ifdef COMBATANT_SCENE_LIT_MATERIAL
uniform sampler2D u_SceneLight;

vec3 materialSceneIllumination(vec2 normal, float radiusPx) {
    // Low frequency environment irradiance, not a literal 2D reflection.
    // All sampling UVs are framebuffer-global and captured before this draw.
    vec2 uiPosition = v_Local.xy / (abs(v_Local.z) > 1e-6 ? v_Local.z : 1.0);
    vec2 uv = vec2(uiPosition.x / max(uScreen.z, 1.0),
                   1.0 - uiPosition.y / max(uScreen.w, 1.0));
    vec2 stepUv = max(radiusPx, 0.0) / max(uScreen.zw, vec2(1.0));
    vec2 tangent = vec2(-normal.y, normal.x);
    vec3 sum = texture(u_SceneLight, clamp(uv, 0.001, 0.999)).rgb * 0.30;
    sum += texture(u_SceneLight, clamp(uv + vec2(stepUv.x, 0.0), 0.001, 0.999)).rgb * 0.15;
    sum += texture(u_SceneLight, clamp(uv - vec2(stepUv.x, 0.0), 0.001, 0.999)).rgb * 0.15;
    sum += texture(u_SceneLight, clamp(uv + vec2(0.0, stepUv.y), 0.001, 0.999)).rgb * 0.15;
    sum += texture(u_SceneLight, clamp(uv - vec2(0.0, stepUv.y), 0.001, 0.999)).rgb * 0.15;
    // Directional anisotropy of the reflected colour along the bevel tangent.
    vec2 t = tangent * stepUv * 0.65;
    sum += texture(u_SceneLight, clamp(uv + t, 0.001, 0.999)).rgb * 0.05;
    sum += texture(u_SceneLight, clamp(uv - t, 0.001, 0.999)).rgb * 0.05;
    return sum;
}
#endif

#include <combatant:ui_pbr_surface.glsl>

vec2 primitivePoint(int index) {
    if (index == 0) return v_Params.xy;
    if (index == 1) return v_Params.zw;
    if (index == 2) return v_Params2.xy;
    if (index == 3) return v_Params2.zw;
    if (index == 4) return v_Params3.xy;
    if (index == 5) return v_Params3.zw;
    if (index == 6) return v_Params4.xy;
    return v_Params4.zw;
}

float smoothMaximum(float a, float b, float radius) {
    if (radius <= 0.0001) return max(a, b);
    float h = clamp(0.5 + 0.5 * (a - b) / radius, 0.0, 1.0);
    return mix(b, a, h) + radius * h * (1.0 - h);
}

float primitiveSdf(vec2 p, int count, float rounding) {
    float d = -1.0e20;
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        int next = i + 1;
        if (next >= count) next = 0;
        vec2 a = primitivePoint(i);
        vec2 b = primitivePoint(next);
        vec2 edge = b - a;
        float edgeLength = max(length(edge), 0.0001);
        float edgeDistance = -(edge.x * (p.y - a.y) - edge.y * (p.x - a.x)) / edgeLength;
        d = i == 0 ? edgeDistance : smoothMaximum(d, edgeDistance, rounding);
    }
    return d;
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec2 logicalScale = uScreen.zw / max(uScreen.xy, vec2(1.0));
    float invW = abs(v_Local.z) > 0.000001 ? v_Local.z : 1.0;
    vec2 local = v_Local.xy / invW - v_Rect.xy;
    int count = int(clamp(floor(v_Params5.x + 0.5), 3.0, 8.0));
    // Negative vertex count marks a true four-radius rounded box. Its radii
    // occupy the otherwise unused 5th/6th polygon vertex slots (v_Params3).
    float d = v_Params5.x < 0.0
            ? roundedCornersSdf(local - v_Rect.zw * 0.5, v_Rect.zw * 0.5,
                    normalizeRadii(v_Params3, v_Rect.zw))
            : primitiveSdf(local, count, max(v_Params5.y, 0.0));
    float alpha = coverage(d, analyticAa(d, logicalScale));
    if (alpha <= 0.001) discard;

    int mode = int(floor(v_Params6.x + 0.5));
    float detailScale = max(v_Params6.y, 0.01);
    float roughness = clamp(v_Params6.z, 0.0, 1.0);
    float response = clamp(v_Params6.w, 0.0, 1.0);
    vec3 accent = clamp(v_Params7.rgb, 0.0, 1.0);
    float bevelStrength = clamp(v_Params7.w, 0.0, 1.0);
    vec3 color = clamp(v_Color.rgb, 0.0, 1.0);

    vec2 gradient = vec2(dFdx(d) / max(logicalScale.x, 0.0001),
                         dFdy(d) / max(logicalScale.y, 0.0001));
    vec2 normal = length(gradient) > 0.0001 ? normalize(gradient) : vec2(0.0, -1.0);
    float bevelWidth = mix(1.5, 6.5, bevelStrength);
    float edge = 1.0 - smoothstep(0.0, bevelWidth, max(-d, 0.0));

    #ifdef COMBATANT_CLIMATE_FIELD
    if (mode == 9) { // GPU-resident climate film shared with Liquid Glass.
        vec2 physical = local / max(logicalScale, vec2(1e-6));
        float rain = clamp(v_Params5.z, 0.0, 1.0);
        float water = clamp(v_Params5.w, 0.0, 1.0);
        float frost = clamp(v_Params6.y, 0.0, 1.0);
        float snow = clamp(v_Params6.z, 0.0, 1.0);
        float condensation = clamp(v_Params6.w, 0.0, 1.0);
        vec2 waterNormal = vec2(0.0);
        float trail = 0.0, shine = 0.0;
        // Material and Liquid Glass must read the same physical screen-space
        // water field; local coordinates duplicated rain between HUD widgets.
        vec2 screenPx = (local + v_Rect.xy) / max(logicalScale, vec2(1e-6));
        float body = combatantClimateDroplets(screenPx,
                v_Rect.zw / max(logicalScale, vec2(1e-6)),
                rain, water, uGlassClock.x, waterNormal, trail, shine);
        vec2 centerUv = clamp(vec2(screenPx.x / max(uScreen.x, 1.0),
                1.0 - screenPx.y / max(uScreen.y, 1.0)), 0.001, 0.999);
        vec2 opticalPx = clamp(waterNormal * 15.0, vec2(-13.0), vec2(13.0));
        vec2 refractedUv = clamp(centerUv + opticalPx / max(uScreen.xy, vec2(1.0)),
                0.001, 0.999);
        float opticalCoverage = smoothstep(0.012, 0.14, length(waterNormal));
        // The trail is water left on the surface, not a reflective white ring.
        // It must not generate a uniform translucent patch over a flat material.
        float film = condensation * (0.55 + 0.45 * hash21(floor(physical / 24.0)));
        float frostEdge = frost * (1.0 - smoothstep(0.0, 9.0,
                max(-d, 0.0) / max(min(logicalScale.x, logicalScale.y), 1e-6)));
        float coldCover = clamp(frostEdge * 0.18 + snow * 0.08 + film * 0.015,
                                0.0, 0.21);
        vec3 refractedScene = texture(u_ClimateBackdrop, refractedUv).rgb;
        float relief = climateDirectionalRelief(waterNormal,
                climateHudDetailFactor(v_Rect.zw / max(logicalScale, vec2(1e-6))));
        relief += trail * 0.016 * clamp(waterNormal.x * 5.0, -1.0, 1.0);
        vec3 frostTint = mix(clamp(refractedScene + vec3(relief), 0.0, 1.0),
                             vec3(0.76, 0.86, 0.96), coldCover);
        // Translucent composition interpolates with the already-drawn base.
        // The center of a drop can have zero slope; its material stays intact.
        float refractionAlpha = opticalCoverage * 0.94;
        float overlayAlpha = clamp(max(refractionAlpha, coldCover), 0.0, 0.96);
        fragColor = vec4(clamp(frostTint, 0.0, 1.0),
                         alpha * v_Color.a * overlayAlpha);
        return;
    }
    #endif

    if (mode == 1 || mode == 2 || mode == 4 || mode == 5 || mode == 6) {
#ifdef COMBATANT_MATERIAL_MICROTEXTURE
        color = uiPbrSurface(local, normal, edge, mode);
#else
        color = uiPbrProceduralSurface(local, normal, edge, mode);
#endif
        fragColor = vec4(clamp(color, 0.0, 1.0), v_Color.a * alpha);
        return;
    }

    if (mode == 8) { // CELLULAR_STRUCTURE: composited over the pre-existing base.
        vec2 physical = local / max(logicalScale, vec2(0.0001));
        vec4 structure = uiVoronoiStructure(physical,
                v_Params6.y, v_Params6.w, v_Params6.z, v_Params7.w,
                v_Params5.z, int(floor(v_Params5.w + 0.5)), color);
        fragColor = vec4(structure.rgb, v_Color.a * alpha * structure.a);
        return;
    } else if (mode == 3) { // PHOSPHOR LED: emissive pixel substrate across the entire shell.
        // Physical-pixel coordinates keep dot pitch stable across logical UI scales.
        vec2 physical = local / max(logicalScale, vec2(0.0001));
        vec2 physicalSize = v_Rect.zw / max(logicalScale, vec2(0.0001));
        float pitch = clamp(detailScale, 3.5, 10.0);
        vec2 grid = physical / pitch;
        vec2 gridCell = fract(grid) - 0.5;
        float r = length(gridCell);
        float aa = max(fwidth(r), 0.012);
        float pixelShape = 1.0 - smoothstep(0.265 - aa, 0.355 + aa, r);
        float halo = 1.0 - smoothstep(0.35 + aa, 0.66 + aa, r);
        // Suppress high frequencies before the pixel spacing becomes sub-Nyquist.
        float densityFilter = 1.0 - smoothstep(0.33, 0.66, max(fwidth(grid.x), fwidth(grid.y)));
        vec2 uv = clamp(physical / max(physicalSize, vec2(1.0)), vec2(0.0), vec2(1.0));

        // The whole surface is an LED display, not a gradient with a decorative
        // progress row. The substrate and unlit cells exist even with no signal.
        vec3 substrate = color * 0.55 + vec3(0.005, 0.008, 0.014);
        float off = clamp(roughness, 0.0, 1.0);
        float inactive = densityFilter * pixelShape;
        color = substrate + accent * inactive * (0.09 + off * 0.25);

        // Two broad, low-intensity emissive regions behind the content. The CPU
        // sends real telemetry through response. Light remains local to the cells;
        // it never resolves into a timeline or a full-surface gradient overlay.
        vec2 d1 = (uv - vec2(0.24, 0.49)) * vec2(1.05, 1.85);
        vec2 d2 = (uv - vec2(0.78, 0.53)) * vec2(1.10, 1.90);
        float fieldA = 1.0 - smoothstep(0.18, 0.55, length(d1));
        float fieldB = 1.0 - smoothstep(0.16, 0.48, length(d2));
        float phosphorDrive = 0.16 + 0.55 * clamp(response, 0.0, 1.0);
        float field = clamp((fieldA * 0.70 + fieldB * 0.55) * phosphorDrive, 0.0, 1.0);
        float emitted = field * densityFilter;
        color += accent * (pixelShape * emitted * 0.64 + halo * emitted * bevelStrength * 0.20);
        // Restrained optical edge belongs to the display body, not a white glow.
        float grazing = clamp(dot(normal, normalize(vec2(-0.48, -0.88))) * 0.5 + 0.5, 0.0, 1.0);
        color *= 1.0 - edge * (0.065 + (1.0 - grazing) * 0.055);
        color += accent * edge * grazing * 0.012;
    } else if (mode == 7) { // ICE: same shape-aware material used by glass.
        #ifdef COMBATANT_MATERIAL_MICROTEXTURE
        vec2 surfaceUv = local / max(v_Rect.zw, vec2(1.0));
        vec2 surfaceSizePx = v_Rect.zw / max(logicalScale, vec2(1e-6));
        float shapeDistancePx = d / max(min(logicalScale.x, logicalScale.y), 1e-6);
        vec4 iceShape = vec4(v_Params5.zw, v_Params6.yz);
        vec4 iceOptics = vec4(v_Params6.w, v_Params7.xyz);
        CombatantIceSurface ice = combatantIceSurface(
                surfaceUv, surfaceSizePx, shapeDistancePx, iceShape, v_Params8,
                vec4(v_Params9.yz, v_Params10.xy));
        color = combatantIceColor(color, ice, normal, iceOptics);
        #ifdef COMBATANT_SCENE_LIT_MATERIAL
        vec3 environment = iceLinear(materialSceneIllumination(ice.facetNormal, v_Params10.w));
        color = iceSrgb(iceLinear(color) * mix(vec3(1.0), environment * 1.5, v_Params10.z));
        #endif
        #else
        color = mix(color, accent, 0.14 + 0.20 * edge * bevelStrength);
        #endif
    }

    fragColor = vec4(clamp(color, 0.0, 1.0), v_Color.a * alpha);
}
