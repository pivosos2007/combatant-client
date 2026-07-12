#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

uniform sampler2D u_Texture;

layout (std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

layout (std140) uniform SkyboxShader {
    vec4 u_Color;
    vec4 u_SkyFogColor;
    vec4 u_Params;
};

in vec2 v_TexCoord;
in vec4 v_Color;
in float v_SphericalDistance;
in float v_CylindricalDistance;
in float v_Horizon;

out vec4 color;

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (fogEnd <= fogStart) {
        return 1.0;
    }
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

void main() {
    vec4 tex = texture(u_Texture, v_TexCoord);
    float alpha = tex.a * v_Color.a;
    if (alpha <= 0.001) {
        discard;
    }

    vec3 skyColor = max(v_Color.rgb, vec3(0.015));
    float skyFogBlend = clamp(u_SkyFogColor.a, 0.0, 1.0);
    vec3 tinted = mix(tex.rgb, tex.rgb * skyColor * 1.35, 0.52 * skyFogBlend);

    float luma = dot(tex.rgb, vec3(0.2126, 0.7152, 0.0722));
    float skyBlend = (0.13 + smoothstep(0.08, 0.85, luma) * 0.18) * (1.0 - v_Horizon * 0.45) * skyFogBlend;
    tinted = mix(tinted, skyColor, skyBlend);

    float skyEnd = max(FogSkyEnd, 1.0);
    float fogStart = min(FogEnvironmentalStart, FogRenderDistanceStart);
    float distanceFog = linear_fog_value(v_CylindricalDistance, fogStart, skyEnd);
    float horizonFog = v_Horizon * 0.86;
    float fogValue = clamp(max(distanceFog, horizonFog) * FogColor.a * skyFogBlend, 0.0, 1.0);

    vec3 fogTarget = mix(skyColor, FogColor.rgb, clamp(FogColor.a + v_Horizon * 0.45, 0.0, 1.0));
    color = vec4(mix(tinted, fogTarget, fogValue), alpha);
}
