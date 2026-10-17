#version 330 core
#extension GL_ARB_separate_shader_objects : require
#include <combatant:reimagined_sky_common.glsl>

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;
uniform sampler2D u_CurrentClouds;
uniform sampler2D u_CloudHistory;

// Previous world-to-view is the exact inverse of skyRayParameters()'s pitch/yaw.
vec2 previousUv(vec3 worldDir) {
    float sy = sin(u_PrevView.x), cy = cos(u_PrevView.x);
    vec3 yaw = vec3(cy * worldDir.x + sy * worldDir.z,
                    worldDir.y, -sy * worldDir.x + cy * worldDir.z);
    float sp = sin(u_PrevView.y), cp = cos(u_PrevView.y);
    vec3 eye = vec3(yaw.x, cp * yaw.y + sp * yaw.z,
                    -sp * yaw.y + cp * yaw.z);
    if (eye.z < 0.001) return vec2(-1.0);
    vec2 plane = eye.xy / eye.z;
    return 0.5 + plane / (2.0 * vec2(u_PrevView.w, 1.0) * tan(u_PrevView.z * 0.5));
}

void main() {
    vec4 current = texture(u_CurrentClouds, v_TexCoord);
    color = current;
    if (u_Temporal.x < 0.5 || u_Rotation.w > 0.5) return;

    vec3 ray = skyRay(v_TexCoord);
    if (abs(ray.y) < 0.045) return; // depth uncertainty around the cloud horizon
    float height = mix(u_Layer.x, u_Layer.y, 0.52);
    float distanceToCloud = (height - u_Camera.y) / ray.y;
    if (distanceToCloud <= 0.0 || distanceToCloud >= u_CloudShape.w) return;

    vec3 anchor = u_Camera.xyz + ray * distanceToCloud;
    float elapsed = clamp(u_Camera.w - u_PrevCamera.w, 0.0, 0.5);
    anchor += vec3(1.3, 0.0, 0.49) * elapsed * u_Climate.w;
    vec2 historyUv = previousUv(normalize(anchor - u_PrevCamera.xyz));
    vec2 texel = 1.0 / vec2(textureSize(u_CurrentClouds, 0));
    if (any(lessThan(historyUv, texel)) || any(greaterThan(historyUv, 1.0 - texel))) return;

    vec4 history = texture(u_CloudHistory, historyUv);
    // 3x3 neighborhood box clipping prevents large, bright cloud silhouettes from
    // dragging across disocclusions when yaw/pitch or camera position changes.
    vec4 lo = current;
    vec4 hi = current;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec4 candidate = texture(u_CurrentClouds, v_TexCoord + vec2(x, y) * texel);
            lo = min(lo, candidate);
            hi = max(hi, candidate);
        }
    }
    float alphaError = abs(history.a - current.a);
    if (alphaError > 0.30) return;
    vec4 clippedHistory = clamp(history, lo - vec4(0.025), hi + vec4(0.025));
    float motionPixels = length((historyUv - v_TexCoord) / texel);
    float motionWeight = 1.0 - smoothstep(12.0, 120.0, motionPixels);
    float reactive = 1.0 - smoothstep(0.06, 0.30, alphaError);
    float historyWeight = u_Temporal.z * motionWeight * reactive;
    color = mix(current, clippedHistory, historyWeight);
    color.rgb = max(color.rgb, vec3(0.0));
    color.a = clamp(color.a, 0.0, 1.0);
}
