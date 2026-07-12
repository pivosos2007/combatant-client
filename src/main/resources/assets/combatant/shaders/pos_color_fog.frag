#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

layout (std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

in vec4 v_Color;
in float v_SphericalDistance;
in float v_CylindricalDistance;

out vec4 color;

float linear_fog_value(float vertexDistance, float fogStart, float fogEnd) {
    if (vertexDistance <= fogStart) {
        return 0.0;
    } else if (vertexDistance >= fogEnd) {
        return 1.0;
    }
    return (vertexDistance - fogStart) / (fogEnd - fogStart);
}

float total_fog_value(float sphericalVertexDistance, float cylindricalVertexDistance,
                      float environmentalStart, float environmantalEnd,
                      float renderDistanceStart, float renderDistanceEnd) {
    return max(
            linear_fog_value(sphericalVertexDistance, environmentalStart, environmantalEnd),
            linear_fog_value(cylindricalVertexDistance, renderDistanceStart, renderDistanceEnd)
    );
}

vec4 apply_fog(vec4 inColor, float sphericalVertexDistance, float cylindricalVertexDistance,
               float environmentalStart, float environmantalEnd,
               float renderDistanceStart, float renderDistanceEnd,
               vec4 fogColor) {
    float fogValue = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance,
            environmentalStart, environmantalEnd, renderDistanceStart, renderDistanceEnd);
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

void main() {
    vec4 c = v_Color;
    if (c.a == 0.0) {
        discard;
    }
    color = apply_fog(c, v_SphericalDistance, v_CylindricalDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd,
            FogColor);
}
