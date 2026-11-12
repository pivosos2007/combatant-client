#version 330 core
#extension GL_ARB_separate_shader_objects : require
/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 *
 * Dreamcore direct sky: stable per-launch procedural catalogue, thin distant
 * cloud representations, high cirrus and an actual 3D froxel near field.
 * The sky layers are cheap distant impostors; they never substitute the near
 * volumetric renderer and are hidden by vanilla opaque world geometry.
 */
layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};
layout(std140) uniform SkyboxShader {
    mat4 u_InverseProjection;
    mat4 u_InverseView;
    vec4 u_Sky;          // vanilla sky RGB, night fraction
    vec4 u_View;         // frame dimensions
    vec4 u_Celestial;    // sun, moon, star angles, elapsed animation time
    vec4 u_Weather;      // rain, thunder, horizon fog, aurora intensity
    vec4 u_World;        // rebased camera xyz, animation time
    vec4 u_Clouds;       // near base, thickness, coverage, scale
    vec4 u_Layout;       // per-launch star seeds xy, cloud offsets zw
    vec4 u_CloudOptions;// distant enabled, cirrus enabled, density, aurora speed
    vec4 u_DayZenith;
    vec4 u_DayHorizon;
    vec4 u_NightZenith;
    vec4 u_NightHorizon;
    vec4 u_Glow;
    vec4 u_Stars;
    vec4 u_CloudLight;
    vec4 u_CloudShadow;
    vec4 u_AuroraPrimary;
    vec4 u_AuroraSecondary;
};
layout(location = 0) out vec4 color;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;

float hash12(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
#include "include/scene_color.glsl"

vec3 worldRay(vec2 uv) {
    vec4 viewPoint = u_InverseProjection * vec4(uv * 2.0 - 1.0, 0.5, 1.0);
    return normalize(mat3(u_InverseView) * normalize(viewPoint.xyz / max(abs(viewPoint.w), 1.e-5)));
}
vec3 orbitDirection(float angle) {
    return vec3(-sin(angle), cos(angle), 0.0);
}

// The catalogue itself is randomized each client launch, not regenerated each
// frame. Periodic longitude indexing removes the 0/360-degree seam.
vec3 stars(vec3 ray, float night, float time) {
    vec2 spherical = vec2(atan(ray.z, ray.x) / TAU + .5,
                          asin(clamp(ray.y, -.99999, .99999)) / PI + .5);
    vec2 cell = spherical * vec2(320., 176.);
    vec2 id = floor(cell);
    vec2 local = fract(cell);
    id.x = mod(id.x, 320.);
    vec2 seededId = id + u_Layout.xy;
    float existence = hash12(seededId + 13.72);
    float present = smoothstep(.968, .995, existence);
    vec2 center = vec2(hash12(seededId + 4.22), hash12(seededId + 17.77)) * .64 + .18;
    float delta = length((local - center) * vec2(1., .73));
    float starCore = 1. - smoothstep(.014, .096, delta);
    float halo = exp(-delta * 15.) * .16;
    float poleFade = 1. - smoothstep(.96, 1., abs(ray.y));
    float brightness = mix(.09, .85, night) * present * (starCore + halo) * poleFade;
    brightness *= .94 + .06 * sin(time * .83 + existence * 34.);
    return u_Stars.rgb * mix(.75, 1.25, hash12(seededId + 35.3)) * brightness;
}

// All actual cloud media remain in depth-aware compute passes. The aurora
// uses a thin directional curtain model: no fixed 2D cloud texture or sky stain.
vec3 aurora(vec3 direction, float time) {
    float speed = max(.05, u_CloudOptions.w);
    float heading = hash12(u_Layout.xy * .027) * TAU;
    vec3 north = normalize(vec3(cos(heading), 0., sin(heading)));
    float viewNorth = dot(direction, north);
    float azimuth = atan(direction.z, direction.x) - heading;
    float elevation = asin(clamp(direction.y, -1., 1.));
    float region = smoothstep(-.38, .36, viewNorth);
    float lower = smoothstep(.02, .21, elevation);
    float upper = 1. - smoothstep(.91, 1.45, elevation);
    if (region * lower * upper < .0001) return vec3(0.);

    vec3 light = vec3(0.);
    for (int band = 0; band < 3; band++) {
        float b = float(band);
        float x = azimuth * (2.5 + b * .5) + b * 4.83;
        float wave = sin(x * 2.0 + time * speed * (.08 + .026 * b)) * .065;
        wave += sin(x * 5.7 - time * speed * .12 + b * 3.4) * .018;
        wave += sin(x * 12.7 + time * speed * .06) * .007;
        float center = .34 + b * .19 + wave;
        float ribbon = exp(-pow((elevation - center) / (.055 + b * .013), 2.));
        float folds = .42 + .58 * pow(abs(sin(azimuth * (35. + 4. * b)
                          + time * speed * (.13 + .028 * b)
                          + 3. * sin(x * 3.1))), 2.);
        float intensity = ribbon * folds * region * lower * upper;
        vec3 tint = mix(u_AuroraPrimary.rgb, u_AuroraSecondary.rgb,
                        clamp(.25 + b * .32 + wave * 1.4, 0., 1.));
        light += tint * intensity * (.34 + .12 * b);
    }
    return light;
}

void main() {
    vec2 uv = gl_FragCoord.xy / max(u_View.xy, vec2(1.));
    vec3 ray = worldRay(uv);
    float night = clamp(u_Sky.w, 0., 1.);
    float zenith = smoothstep(.19, .94, clamp(ray.y * .5 + .5, 0., 1.));
    float horizon = 1. - smoothstep(.015, .35, abs(ray.y));
    float rain = clamp(u_Weather.x, 0., 1.);
    float thunder = clamp(u_Weather.y, 0., 1.);
    float storm = clamp(rain * .65 + thunder * .80, 0., 1.);

    vec3 sunDir = orbitDirection(u_Celestial.x);
    vec3 moonDir = orbitDirection(u_Celestial.y);
    vec3 day = mix(u_DayHorizon.rgb, u_DayZenith.rgb, zenith);
    vec3 dark = mix(u_NightHorizon.rgb, u_NightZenith.rgb, zenith);
    vec3 sky = mix(day, dark, night);
    float sunDot = max(dot(ray, sunDir), 0.);
    float moonDot = max(dot(ray, moonDir), 0.);
    float twilight = pow(max(0., 1. - abs(sunDir.y)), 3.);
    sky += u_Glow.rgb * twilight * pow(max(dot(ray,
           normalize(vec3(sunDir.x, .10, sunDir.z))), 0.), 11.) * .72;
    sky += u_Glow.rgb * pow(sunDot, 10.) * (1. - night * .70) * .45;
    // Sun and moon may exceed 1 in the intermediate linear HDR texture.
    sky += vec3(1.45, 1.17, .82) * (1.-smoothstep(.006,.014,1.-sunDot)) * (1.-night);
    sky += u_Stars.rgb * (1.-smoothstep(.0007,.0023,1.-moonDot)) * night * .85;

    float ca = cos(u_Celestial.z), sa = sin(u_Celestial.z);
    vec3 starRay = vec3(ray.x, ca * ray.y + sa * ray.z, -sa * ray.y + ca * ray.z);
    sky += stars(starRay, night, u_Celestial.w) * (1. - storm * .95);
    if (u_Weather.w > .001 && night > .015 && storm < .98) {
        sky += aurora(ray, u_Celestial.w) * u_Weather.w *
               (.035 + .965 * night) * (1. - storm * .98);
    }

    // Environmental fog is scene-linear here, converted from vanilla sRGB color.
    vec3 vanillaSky = combatantSrgbToLinear(u_Sky.rgb);
    vec3 vanillaFog = combatantSrgbToLinear(FogColor.rgb);
    sky = mix(sky, mix(vanillaSky, vanillaFog, .30),
              horizon * clamp(u_Weather.z, 0., 1.) * (1. - storm));

    // Always apply storm attenuation AFTER fog and celestial glow. In particular,
    // rain at midnight cannot reinstate bright vanilla day-sky colors.
    vec3 stormDay = mix(u_DayHorizon.rgb, vec3(.045, .067, .092), .78);
    vec3 stormNight = mix(u_NightZenith.rgb, vec3(.003, .006, .012), .58);
    vec3 stormSky = mix(stormDay, stormNight, night);
    sky = mix(sky, stormSky, storm * (.62 + .33 * thunder));
    color = vec4(max(sky, vec3(0.)), 1.);
}
