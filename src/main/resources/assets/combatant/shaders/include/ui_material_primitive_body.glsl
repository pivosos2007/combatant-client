/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <combatant:ui_aa.glsl>
#include <combatant:ui_geometry.glsl>
#include <combatant:ui_voronoi_structure.glsl>
#include <combatant:ui_climate_droplets.glsl>


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
#ifdef COMBATANT_MATERIAL_MICROTEXTURE
layout(location = 11) in vec4 v_Params8;
layout(location = 12) in vec4 v_Params9;
layout(location = 13) in vec4 v_Params10;
#endif

layout(location = 0) out vec4 fragColor;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

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

#ifdef COMBATANT_MATERIAL_MICROTEXTURE
#include <combatant:ui_pbr_surface.glsl>
#endif

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

    if (mode == 9) { // Opt-in environmental film for non-glass materials.
        vec2 physical = local / max(logicalScale, vec2(1e-6));
        vec2 rainNormal = vec2(0.0), waterNormal = vec2(0.0);
        float rainTrail = 0.0, rainShine = 0.0;
        float waterTrail = 0.0, waterShine = 0.0;
        float rain = clamp(v_Params5.z, 0.0, 1.0);
        float water = clamp(v_Params5.w, 0.0, 1.0);
        float frost = clamp(v_Params6.y, 0.0, 1.0);
        float snow = clamp(v_Params6.z, 0.0, 1.0);
        float condensation = clamp(v_Params6.w, 0.0, 1.0);
        float dropA = 0.0, dropB = 0.0;
        if (rain > 0.002) dropA = combatantClimateDroplets(physical, uGlassClock.x, rain,
                25.0, 52.0, 7.31, rainNormal, rainTrail, rainShine);
        if (water > 0.002) dropB = combatantClimateDroplets(physical, uGlassClock.x, water,
                42.0, 26.0, 31.73, waterNormal, waterTrail, waterShine);
        // Humidity is a thin low-contrast film, NOT thousands of extra beads.
        float film = condensation * (0.55 + 0.45 * hash21(floor(physical / 24.0)));
        float wetted = clamp(max(dropA, rainTrail * 0.70) * rain
                           + max(dropB, waterTrail * 0.70) * water,
                           0.0, 1.0);
        float glint = clamp(rainShine * rain + waterShine * water, 0.0, 1.0);
        float facet = 1.0 - smoothstep(0.0, mix(3.0, 20.0, frost),
                                       max(-d, 0.0) / max(min(logicalScale.x, logicalScale.y), 1e-6));
        float cold = (frost * facet + snow * 0.14) *
                     (0.45 + 0.55 * hash21(floor(physical / 6.0)));
        // Underlying material remains readable. Water bends its highlight;
        // it is not an opaque white disk or an artificial flood fill.
        vec3 wetTint = mix(vec3(0.055, 0.085, 0.115), vec3(0.76, 0.87, 0.96),
                           clamp(glint * 1.2, 0.0, 1.0));
        vec3 frostTint = vec3(0.72, 0.86, 0.98);
        float outAlpha = clamp(wetted * (0.29 + glint * 0.24) +
                               glint * 0.18 + film * 0.07 + cold * 0.37,
                               0.0, 0.64);
        color = mix(wetTint, frostTint, clamp(cold / max(wetted + cold, 1e-3), 0.0, 1.0));
        fragColor = vec4(color, alpha * v_Color.a * outAlpha);
        return;
    }

    #ifdef COMBATANT_MATERIAL_MICROTEXTURE
    if (mode == 1 || mode == 2 || mode == 4 || mode == 5 || mode == 6) {
        color = uiPbrSurface(local, normal, edge, mode);
        fragColor = vec4(clamp(color, 0.0, 1.0), v_Color.a * alpha);
        return;
    }
    #endif

    if (mode == 8) { // CELLULAR_STRUCTURE: composited over the pre-existing base.
        vec2 physical = local / max(logicalScale, vec2(0.0001));
        vec4 structure = uiVoronoiStructure(physical,
                v_Params6.y, v_Params6.w, v_Params6.z, v_Params7.w,
                v_Params5.z, int(floor(v_Params5.w + 0.5)), color);
        fragColor = vec4(structure.rgb, v_Color.a * alpha * structure.a);
        return;
    } else if (mode == 1) {
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        // Anisotropy is locked to the top face. No phase jump when the whole
        // hexagon shifts down on hover/press.
        float tangent = uv.x + uv.y * 0.17;
        float bands = sin(tangent * (90.0 * detailScale)) * 0.5 + 0.5;
        float bandFilter = 1.0 - smoothstep(0.28, 0.72, fwidth(tangent) * 90.0 * detailScale);

        // A travelling specular lobe over the entire face, not just a rim tint.
        // The input is physical surface-local pointer position and only affects
        // this primitive when the corresponding hover parameter is non-zero.
        vec2 pointerLocal = uGlassPointer.zw - v_Rect.xy;
        vec2 pointerDelta = (local - pointerLocal) / max(v_Rect.zw, vec2(1.0));
        float lightDistance = length(pointerDelta * vec2(0.77, 1.32));
        float spot = 1.0 - smoothstep(0.04, 0.75, lightDistance);
        float specSpot = spot * spot * (3.0 - 2.0 * spot);
        vec2 lightDirection = normalize(vec2(-0.48, -1.0)
                + clamp(-pointerDelta * response, vec2(-0.48), vec2(0.48)));
        float facing = dot(normal, lightDirection) * 0.5 + 0.5;
        float rimSpec = pow(max(facing, 0.0), mix(2.2, 5.6, roughness));
        float satinSpec = specSpot * response * (1.0 - roughness * 0.35);
        color = mix(color, accent, 0.045 + edge * rimSpec * 0.10);
        color += vec3((bands - 0.5) * bandFilter * (1.0 - roughness) * 0.046);
        color += mix(accent, vec3(0.92, 0.95, 0.98), 0.38) * satinSpec * 0.19;
        color *= 1.0 - edge * (0.10 - rimSpec * 0.14);
    } else if (mode == 2) {
        float grain = hash21(floor(local * detailScale)) - 0.5;
        color += grain * (0.018 * (1.0 - roughness * 0.45));
        float diffuse = dot(normal, normalize(vec2(-0.35, -1.0))) * 0.5 + 0.5;
        color = mix(color, accent, edge * bevelStrength * diffuse * 0.12);
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
    } else if (mode == 4) { // GRAPHITE
        vec2 localMicro = local * detailScale;
        vec2 cells = floor(localMicro);
        float pix = hash21(cells + vec2(5.17, 12.43)) - 0.5;
        float filtered = 1.0 - smoothstep(0.25, 0.78, length(fwidth(localMicro)));
        float coarse = hash21(floor(local * 0.075)) - 0.5;
        color = color * (0.94 + coarse * 0.040)
                + pix * filtered * (0.045 + 0.020 * roughness);
        float grazing = clamp(dot(normal, normalize(vec2(-0.30, -0.95))) * 0.5 + 0.5, 0.0, 1.0);
        color += accent * edge * grazing * bevelStrength * 0.08;
        color *= 1.0 - edge * (1.0 - grazing) * 0.10;
    } else if (mode == 5) { // CERAMIC
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        float glossy = pow(max(0.0, 1.0 - length((uv - vec2(0.24, 0.04)) * vec2(0.72, 1.60))),
                           mix(1.6, 6.0, roughness));
        float grazing = clamp(dot(normal, normalize(vec2(-0.45, -0.89))) * 0.5 + 0.5, 0.0, 1.0);
        color = mix(color, accent, 0.04 + edge * grazing * 0.10);
        color += vec3(glossy * (1.0 - roughness) * 0.075);
        color += vec3(edge * pow(grazing, 5.0) * bevelStrength * 0.12);
        color *= 1.0 - edge * (1.0 - grazing) * 0.13;
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
        // A texture-free pipeline can occur during capability changes. Preserve the
        // ice silhouette and supplied tint instead of dropping the entire primitive.
        color = mix(color, accent, 0.14 + 0.20 * edge * bevelStrength);
        #endif
    } else if (mode == 6) { // BRUSHED METAL
        vec2 uv = local / max(v_Rect.zw, vec2(1.0));
        float brush = uv.x * 0.92 + uv.y * 0.065;
        float wave = sin(brush * (160.0 * detailScale) +
                         sin(uv.y * 19.0) * 0.16);
        float aliasGate = 1.0 - smoothstep(0.25, 0.64, fwidth(brush) * 160.0 * detailScale);
        float coarse = hash21(floor(local * 0.18)) - 0.5;
        color += wave * aliasGate * (0.035 + (1.0 - roughness) * 0.035);
        color += coarse * 0.018;
        vec2 dp = (local - (uGlassPointer.zw - v_Rect.xy)) / max(v_Rect.zw, vec2(1.0));
        float spot = 1.0 - smoothstep(0.06, 0.84, length(dp * vec2(0.60, 1.25)));
        float grazing = clamp(dot(normal, normalize(vec2(-0.50, -0.87))) * 0.5 + 0.5, 0.0, 1.0);
        color = mix(color, accent, edge * grazing * 0.14);
        color += accent * spot * response * (1.0 - roughness * 0.25) * 0.23;
        color *= 1.0 - edge * (1.0 - grazing) * 0.12;
    }

#ifdef COMBATANT_SCENE_LIT_MATERIAL
    if (mode == 1 || mode == 2 || mode == 4 || mode == 5 || mode == 6) {
        float strength = clamp(v_Params5.z, 0.0, 1.0);
        if (strength > 0.001) {
            vec3 environment = materialSceneIllumination(normal, clamp(v_Params5.w, 0.0, 32.0));
            float luma = dot(environment, vec3(0.2126, 0.7152, 0.0722));
            // Mild exposure normalization preserves source hue without blowing out whites.
            vec3 hue = environment / max(luma, 0.07);
            hue = clamp(hue, vec3(0.35), vec3(1.65));
            float highlight = smoothstep(0.04, 0.82, luma);
            float metalWeight = mode == 1 || mode == 6 ? 1.0 : mode == 5 ? 0.58 : 0.28;
            float reflection = strength * metalWeight * (0.08 + 0.27 * highlight);
            color = mix(color, color * hue, reflection);
            color += accent * strength * metalWeight * (luma - 0.35) * (0.035 + edge * 0.085);
        }
    }
#endif
    fragColor = vec4(clamp(color, 0.0, 1.0), v_Color.a * alpha);
}
