#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_MainDepth;
uniform sampler2D u_TranslucentDepth;
uniform sampler2D u_ItemEntityDepth;
uniform sampler2D u_ParticlesDepth;
uniform sampler2D u_WeatherDepth;
uniform sampler2D u_CloudsDepth;

layout (std140) uniform DepthOfField {
    mat4 u_Projection;
    vec4 u_Screen;       // invW, invH, width, height
    vec4 u_Focus;        // mode, fallbackFocusDistance, farStart, farTransition
    vec4 u_Params;       // strength, maxRadiusPx, taps, edgeProtection
    vec4 u_State;        // debugCoc, reserved, reserved, reserved
    vec4 u_DepthA;       // main, translucent, item_entity, particles availability
    vec4 u_DepthB;       // weather, clouds, reserved, reserved availability
};

in vec2 v_TexCoord;

const float DEPTH_NEAR_EPS = 0.000001;
const float DEPTH_FAR_EPS = 0.999999;
const float INVALID_DISTANCE = 100000.0;

bool depthEnabled(float v) {
    return v > 0.5;
}

bool validRawDepth(float d) {
    return d == d && d > DEPTH_NEAR_EPS && d < DEPTH_FAR_EPS;
}

bool hasEnabledDepthSource() {
    return depthEnabled(u_DepthA.x)
        || depthEnabled(u_DepthA.y)
        || depthEnabled(u_DepthA.z)
        || depthEnabled(u_DepthA.w)
        || depthEnabled(u_DepthB.x)
        || depthEnabled(u_DepthB.y);
}

float mergeDepth(float currentDepth, float candidateDepth, float enabled) {
    if (!depthEnabled(enabled) || !validRawDepth(candidateDepth)) {
        return currentDepth;
    }
    if (!validRawDepth(currentDepth)) {
        return candidateDepth;
    }
    return min(currentDepth, candidateDepth);
}

float readSceneDepth(vec2 uv) {
    float d = 1.0;
    d = mergeDepth(d, texture(u_MainDepth, uv).r, u_DepthA.x);
    d = mergeDepth(d, texture(u_TranslucentDepth, uv).r, u_DepthA.y);
    d = mergeDepth(d, texture(u_ItemEntityDepth, uv).r, u_DepthA.z);
    d = mergeDepth(d, texture(u_ParticlesDepth, uv).r, u_DepthA.w);
    d = mergeDepth(d, texture(u_WeatherDepth, uv).r, u_DepthB.x);
    d = mergeDepth(d, texture(u_CloudsDepth, uv).r, u_DepthB.y);
    return d;
}

vec3 reconstructViewPosition(vec2 uv, float rawDepth) {
    float m22 = u_Projection[2][2];
    float m23 = u_Projection[2][3];
    float m32 = u_Projection[3][2];
    float m33 = u_Projection[3][3];
    float denom = rawDepth * m23 - m22;
    if (abs(denom) < 0.000001) {
        denom = denom < 0.0 ? -0.000001 : 0.000001;
    }

    float z = (m32 - rawDepth * m33) / denom;
    float clipW = m23 * z + m33;
    vec2 ndc = uv * 2.0 - 1.0;
    float rhsX = ndc.x * clipW - u_Projection[2][0] * z - u_Projection[3][0];
    float rhsY = ndc.y * clipW - u_Projection[2][1] * z - u_Projection[3][1];
    float det = u_Projection[0][0] * u_Projection[1][1] - u_Projection[1][0] * u_Projection[0][1];
    if (abs(det) < 0.000001) {
        det = det < 0.0 ? -0.000001 : 0.000001;
    }

    float x = (rhsX * u_Projection[1][1] - u_Projection[1][0] * rhsY) / det;
    float y = (u_Projection[0][0] * rhsY - rhsX * u_Projection[0][1]) / det;
    return vec3(x, y, z);
}

float reconstructDistance(vec2 uv, float rawDepth) {
    if (!validRawDepth(rawDepth)) {
        return INVALID_DISTANCE;
    }
    return length(reconstructViewPosition(uv, rawDepth));
}

void accumulateFocusSample(vec2 uv, inout float sum, inout float count) {
    float d = readSceneDepth(uv);
    if (!validRawDepth(d)) {
        return;
    }
    float distanceToCamera = reconstructDistance(uv, d);
    if (distanceToCamera >= INVALID_DISTANCE * 0.5) {
        return;
    }
    sum += distanceToCamera;
    count += 1.0;
}

float centerFocusDistance() {
    vec2 texel = u_Screen.xy;
    vec2 c = vec2(0.5);
    float fallback = max(u_Focus.y, 0.01);
    float sum = 0.0;
    float count = 0.0;

    accumulateFocusSample(c, sum, count);
    accumulateFocusSample(c + vec2(texel.x * 2.0, 0.0), sum, count);
    accumulateFocusSample(c - vec2(texel.x * 2.0, 0.0), sum, count);
    accumulateFocusSample(c + vec2(0.0, texel.y * 2.0), sum, count);
    accumulateFocusSample(c - vec2(0.0, texel.y * 2.0), sum, count);

    if (count <= 0.5) {
        return fallback;
    }
    return clamp(sum / count, 0.01, INVALID_DISTANCE);
}

float focusDistance() {
    if (u_Focus.x < 0.5) {
        return centerFocusDistance();
    }
    return max(u_Focus.y, 0.01);
}

float computeCoc(float distanceToCamera, float focus) {
    if (distanceToCamera >= INVALID_DISTANCE * 0.5) {
        return 0.0;
    }
    float farStart = max(u_Focus.z, 0.0);
    float farTransition = max(u_Focus.w, 0.001);
    float coc = smoothstep(farStart, farStart + farTransition, distanceToCamera);
    coc = pow(clamp(coc, 0.0, 1.0), 1.35);
    return coc * max(u_Params.x, 0.0);
}

float depthCompatibility(float centerDistance, float sampleDistance) {
    if (sampleDistance >= INVALID_DISTANCE * 0.5 || centerDistance >= INVALID_DISTANCE * 0.5) {
        return 0.0;
    }

    float protection = clamp(u_Params.w, 0.0, 1.0);
    float relBase = max(centerDistance, 1.0);
    float relativeDelta = abs(sampleDistance - centerDistance) / relBase;
    float softWidth = mix(0.10, 0.018, protection);
    float weight = 1.0 - smoothstep(softWidth, softWidth * 3.0, relativeDelta);

    if (sampleDistance + 0.001 < centerDistance) {
        float foregroundDelta = (centerDistance - sampleDistance) / relBase;
        float fgWidth = mix(0.07, 0.010, protection);
        weight *= 1.0 - smoothstep(fgWidth, fgWidth * 3.0, foregroundDelta);
    }

    return clamp(weight, 0.0, 1.0);
}

vec3 depthAwareBlur(vec2 uv, float centerDistance, float centerCoc, float radiusPx, float focus) {
    int taps = int(clamp(floor(u_Params.z + 0.5), 1.0, 32.0));
    vec2 texel = u_Screen.xy;

    vec3 accum = texture(u_Texture, uv).rgb;
    float weightSum = 1.0;

    for (int i = 0; i < 32; i++) {
        if (i >= taps) {
            break;
        }

        float fi = float(i) + 0.5;
        float ftaps = max(float(taps), 1.0);
        float r = sqrt(fi / ftaps);
        float a = fi * 2.399963229728653;
        vec2 sampleUv = uv + vec2(cos(a), sin(a)) * r * radiusPx * texel;

        if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
            continue;
        }

        float sampleRawDepth = readSceneDepth(sampleUv);
        if (!validRawDepth(sampleRawDepth)) {
            continue;
        }

        float sampleDistance = reconstructDistance(sampleUv, sampleRawDepth);
        float sampleCoc = computeCoc(sampleDistance, focus);
        float cocWeight = smoothstep(0.0, 0.30, max(centerCoc, sampleCoc));
        float depthWeight = depthCompatibility(centerDistance, sampleDistance);
        float radialWeight = 1.0 - r * 0.35;
        float weight = radialWeight * depthWeight * cocWeight;

        if (weight <= 0.001) {
            continue;
        }

        accum += texture(u_Texture, sampleUv).rgb * weight;
        weightSum += weight;
    }

    return accum / max(weightSum, 0.0001);
}

void main() {
    vec2 uv = v_TexCoord;
    vec4 base = texture(u_Texture, uv);

    float rawDepth = readSceneDepth(uv);
    bool validDepth = validRawDepth(rawDepth);
    bool anyDepthSource = hasEnabledDepthSource();

    if (!anyDepthSource) {
        if (u_State.x > 0.5) {
            color = vec4(1.0, 0.0, 1.0, 1.0); // no depth binding reached the shader
        } else {
            color = vec4(base.rgb, 1.0);
        }
        return;
    }

    if (!validDepth) {
        if (u_State.x > 0.5) {
            color = vec4(1.0, 0.0, 0.0, 1.0); // clear/invalid depth pixel, not a far object
        } else {
            color = vec4(base.rgb, 1.0);
        }
        return;
    }

    float strength = max(u_Params.x, 0.0);
    float maxRadius = max(u_Params.y, 0.0);
    if (strength <= 0.0001 || maxRadius <= 0.0001) {
        color = vec4(base.rgb, 1.0);
        return;
    }

    float distanceToCamera = reconstructDistance(uv, rawDepth);
    float focus = focusDistance();
    float coc = computeCoc(distanceToCamera, focus);
    float radiusPx = clamp(coc * maxRadius, 0.0, maxRadius);

    if (u_State.x > 0.5) {
        color = vec4(vec3(clamp(coc, 0.0, 1.0)), 1.0);
        return;
    }

    if (radiusPx < 0.35) {
        color = vec4(base.rgb, 1.0);
        return;
    }

    vec3 blurred = depthAwareBlur(uv, distanceToCamera, coc, radiusPx, focus);
    vec3 result = mix(base.rgb, blurred, clamp(coc, 0.0, 1.0));
    color = vec4(result, 1.0);
}
