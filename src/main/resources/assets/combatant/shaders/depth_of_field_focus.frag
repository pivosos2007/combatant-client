#version 330 core

/*
 * Resolves the frame-invariant center autofocus distance once. The main DoF pass reads this
 * single texel instead of repeating five multi-layer depth probes for every screen fragment.
 */

out vec4 color;

uniform sampler2D u_MainDepth;
uniform sampler2D u_TranslucentDepth;
uniform sampler2D u_ItemEntityDepth;
uniform sampler2D u_ParticlesDepth;
uniform sampler2D u_WeatherDepth;
uniform sampler2D u_CloudsDepth;

layout (std140) uniform DepthOfField {
    mat4 u_Projection;
    vec4 u_Screen;
    vec4 u_Focus;
    vec4 u_Params;
    vec4 u_State;
    vec4 u_DepthA;
    vec4 u_DepthB;
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

float mergeDepth(float currentDepth, float candidateDepth) {
    if (!validRawDepth(candidateDepth)) return currentDepth;
    if (!validRawDepth(currentDepth)) return candidateDepth;
    return min(currentDepth, candidateDepth);
}

float readSceneDepth(vec2 uv) {
    float d = 1.0;
    if (depthEnabled(u_DepthA.x)) d = mergeDepth(d, texture(u_MainDepth, uv).r);
    if (depthEnabled(u_DepthA.y)) d = mergeDepth(d, texture(u_TranslucentDepth, uv).r);
    if (depthEnabled(u_DepthA.z)) d = mergeDepth(d, texture(u_ItemEntityDepth, uv).r);
    if (depthEnabled(u_DepthA.w)) d = mergeDepth(d, texture(u_ParticlesDepth, uv).r);
    if (depthEnabled(u_DepthB.x)) d = mergeDepth(d, texture(u_WeatherDepth, uv).r);
    if (depthEnabled(u_DepthB.y)) d = mergeDepth(d, texture(u_CloudsDepth, uv).r);
    return d;
}

float reconstructDistance(vec2 uv, float rawDepth) {
    if (!validRawDepth(rawDepth)) return INVALID_DISTANCE;

    float m22 = u_Projection[2][2];
    float m23 = u_Projection[2][3];
    float m32 = u_Projection[3][2];
    float m33 = u_Projection[3][3];
    float denom = rawDepth * m23 - m22;
    if (abs(denom) < 0.000001) denom = denom < 0.0 ? -0.000001 : 0.000001;

    float z = (m32 - rawDepth * m33) / denom;
    float clipW = m23 * z + m33;
    vec2 ndc = uv * 2.0 - 1.0;
    float rhsX = ndc.x * clipW - u_Projection[2][0] * z - u_Projection[3][0];
    float rhsY = ndc.y * clipW - u_Projection[2][1] * z - u_Projection[3][1];
    float det = u_Projection[0][0] * u_Projection[1][1] - u_Projection[1][0] * u_Projection[0][1];
    if (abs(det) < 0.000001) det = det < 0.0 ? -0.000001 : 0.000001;

    float x = (rhsX * u_Projection[1][1] - u_Projection[1][0] * rhsY) / det;
    float y = (u_Projection[0][0] * rhsY - rhsX * u_Projection[0][1]) / det;
    return length(vec3(x, y, z));
}

void accumulateFocusSample(vec2 uv, inout float sum, inout float count) {
    float d = readSceneDepth(uv);
    if (!validRawDepth(d)) return;
    float distanceToCamera = reconstructDistance(uv, d);
    if (distanceToCamera >= INVALID_DISTANCE * 0.5) return;
    sum += distanceToCamera;
    count += 1.0;
}

vec3 encodeNormalized24(float value) {
    vec3 encoded = fract(clamp(value, 0.0, 0.99999994) * vec3(1.0, 255.0, 65025.0));
    encoded -= encoded.yzz * vec3(1.0 / 255.0, 1.0 / 255.0, 0.0);
    return encoded;
}

void main() {
    vec2 texel = u_Screen.xy;
    vec2 center = vec2(0.5);
    float sum = 0.0;
    float count = 0.0;

    accumulateFocusSample(center, sum, count);
    accumulateFocusSample(center + vec2(texel.x * 2.0, 0.0), sum, count);
    accumulateFocusSample(center - vec2(texel.x * 2.0, 0.0), sum, count);
    accumulateFocusSample(center + vec2(0.0, texel.y * 2.0), sum, count);
    accumulateFocusSample(center - vec2(0.0, texel.y * 2.0), sum, count);

    float focus = count > 0.5 ? clamp(sum / count, 0.01, INVALID_DISTANCE) : max(u_Focus.y, 0.01);
    float normalized = log2(focus + 1.0) / log2(INVALID_DISTANCE + 1.0);
    color = vec4(encodeNormalized24(normalized), 1.0);
}
