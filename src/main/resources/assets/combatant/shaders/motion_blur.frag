#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

out vec4 color;

uniform sampler2D u_Texture;
uniform sampler2D u_PreviousColor;

layout (std140) uniform MotionBlur {
    vec4 u_Screen;  // invW, invH, width, height
    vec4 u_Blur;    // spatialVelocityPx.x, spatialVelocityPx.y, maxBlurPx, minMotionPx
    vec4 u_History; // historyVelocityPx.x, historyVelocityPx.y, historyBlend, historyValid
    vec4 u_Params;  // taps, historyClamp, reserved, reserved
};

in vec2 v_TexCoord;

const int MAX_TAPS = 15;
const float EPSILON = 0.000001;

bool validVec2(vec2 v) {
    return v.x == v.x && v.y == v.y && abs(v.x) < 100000.0 && abs(v.y) < 100000.0;
}

float sampleWeight(float t) {
    float x = abs(t * 2.0);
    return max(0.0, 1.0 - x * x);
}

float colorDistance(vec3 a, vec3 b) {
    vec3 d = a - b;
    return dot(d, d);
}

vec3 closestHistorySample(vec2 uv, vec2 historyUv, vec3 current) {
    vec3 h0 = texture(u_PreviousColor, clamp(uv - historyUv, vec2(0.0), vec2(1.0))).rgb;
    vec3 h1 = texture(u_PreviousColor, clamp(uv + historyUv, vec2(0.0), vec2(1.0))).rgb;
    vec3 h2 = texture(u_PreviousColor, uv).rgb;

    float d0 = colorDistance(h0, current);
    float d1 = colorDistance(h1, current);
    float d2 = colorDistance(h2, current);

    vec3 best = h0;
    float bestD = d0;
    if (d1 < bestD) {
        best = h1;
        bestD = d1;
    }
    if (d2 < bestD) {
        best = h2;
    }
    return best;
}

vec3 neighborhoodMin(vec2 uv, vec2 px) {
    vec3 mn = texture(u_Texture, uv).rgb;
    mn = min(mn, texture(u_Texture, clamp(uv + vec2( px.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
    mn = min(mn, texture(u_Texture, clamp(uv + vec2(-px.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
    mn = min(mn, texture(u_Texture, clamp(uv + vec2(0.0,  px.y), vec2(0.0), vec2(1.0))).rgb);
    mn = min(mn, texture(u_Texture, clamp(uv + vec2(0.0, -px.y), vec2(0.0), vec2(1.0))).rgb);
    return mn;
}

vec3 neighborhoodMax(vec2 uv, vec2 px) {
    vec3 mx = texture(u_Texture, uv).rgb;
    mx = max(mx, texture(u_Texture, clamp(uv + vec2( px.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
    mx = max(mx, texture(u_Texture, clamp(uv + vec2(-px.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
    mx = max(mx, texture(u_Texture, clamp(uv + vec2(0.0,  px.y), vec2(0.0), vec2(1.0))).rgb);
    mx = max(mx, texture(u_Texture, clamp(uv + vec2(0.0, -px.y), vec2(0.0), vec2(1.0))).rgb);
    return mx;
}

vec3 spatialBlur(vec2 uv, vec3 centerColor, vec2 velocityUv, int taps) {
    vec3 accum = centerColor;
    float weightSum = 1.0;
    float denom = max(float(taps - 1), 1.0);

    for (int i = 0; i < MAX_TAPS; i++) {
        if (i >= taps) {
            break;
        }

        float t = float(i) / denom - 0.5;
        if (abs(t) < 0.0001) {
            continue;
        }

        vec2 sampleUv = uv + velocityUv * t;
        if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
            continue;
        }

        float weight = sampleWeight(t);
        accum += texture(u_Texture, sampleUv).rgb * weight;
        weightSum += weight;
    }

    return accum / max(weightSum, EPSILON);
}

void main() {
    vec2 uv = v_TexCoord;
    vec4 centerSample = texture(u_Texture, uv);
    vec3 centerColor = centerSample.rgb;

    vec2 spatialVelocityPx = u_Blur.xy;
    vec2 historyVelocityPx = u_History.xy;
    if (!validVec2(spatialVelocityPx) || !validVec2(historyVelocityPx)) {
        color = vec4(centerColor, 1.0);
        return;
    }

    float lenPx = length(spatialVelocityPx);
    float minPx = max(u_Blur.w, 0.0);
    float response = 0.0;
    if (lenPx > max(minPx, 0.05)) {
        float maxPx = max(u_Blur.z, 0.0);
        if (maxPx > 0.0 && lenPx > maxPx) {
            spatialVelocityPx *= maxPx / max(lenPx, EPSILON);
            lenPx = maxPx;
        }
        response = smoothstep(minPx, max(minPx * 2.25, minPx + 0.5), lenPx);
        spatialVelocityPx *= response;
    }

    vec2 spatialVelocityUv = spatialVelocityPx * u_Screen.xy;
    int taps = int(clamp(floor(u_Params.x + 0.5), 3.0, float(MAX_TAPS)));
    if ((taps & 1) == 0) {
        taps += 1;
    }

    vec3 blurred = response > 0.001
            ? spatialBlur(uv, centerColor, spatialVelocityUv, taps)
            : centerColor;

    float historyValid = u_History.w;
    float historyBlend = clamp(u_History.z, 0.0, 0.92);
    if (historyValid < 0.5 || historyBlend <= 0.0001) {
        color = vec4(blurred, 1.0);
        return;
    }

    vec2 historyVelocityUv = historyVelocityPx * u_Screen.xy;
    float historyLenPx = length(historyVelocityPx);
    float historyResponse = smoothstep(max(0.05, minPx * 0.35), max(0.25, minPx * 1.5), historyLenPx);
    if (historyResponse <= 0.0001) {
        color = vec4(blurred, 1.0);
        return;
    }

    vec3 history = closestHistorySample(uv, historyVelocityUv, centerColor);

    vec2 px = u_Screen.xy;
    vec3 mn = neighborhoodMin(uv, px);
    vec3 mx = neighborhoodMax(uv, px);

    float clampStrength = clamp(u_Params.y, 0.0, 1.0);
    float pad = mix(0.10, 0.025, clampStrength);
    vec3 historyClamped = clamp(history, mn - vec3(pad), mx + vec3(pad));

    float diff = sqrt(colorDistance(historyClamped, centerColor));
    float colorConfidence = 1.0 - smoothstep(0.08, 0.32, diff);
    float edgeSpan = length(mx - mn);
    float neighborhoodConfidence = 1.0 - smoothstep(0.45, 1.15, edgeSpan);

    float finalBlend = historyBlend * historyResponse * mix(0.45, 1.0, colorConfidence) * mix(0.55, 1.0, neighborhoodConfidence);
    finalBlend = clamp(finalBlend, 0.0, 0.85);

    color = vec4(mix(blurred, historyClamped, finalBlend), 1.0);
}
