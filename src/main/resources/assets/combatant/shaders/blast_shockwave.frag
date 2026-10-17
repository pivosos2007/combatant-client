#version 330 core
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

layout (std140) uniform JumpShockwave {
    mat4 u_InvViewProj;
    vec4 u_CenterRadius;
    vec4 u_Params;
    vec4 u_RingColor;
    vec4 u_DepthTransform;
};

layout(location = 0) in vec2 v_TexCoord;
layout(location = 0) out vec4 color;

void main() {
    vec2 uv = v_TexCoord;
    vec3 base = texture(u_Texture, uv).rgb;
    float radius = u_CenterRadius.w;
    if (radius <= 1.0e-4) {
        color = vec4(base, 1.0);
        return;
    }

    vec2 ndc = uv * 2.0 - 1.0;
    vec4 farWorld = u_InvViewProj * vec4(ndc, u_DepthTransform.z, 1.0);
    if (abs(farWorld.w) < 1.0e-6) {
        color = vec4(base, 1.0);
        return;
    }
    farWorld.xyz /= farWorld.w;
    vec3 rayDir = normalize(farWorld.xyz);

    float alongRay = dot(rayDir, u_CenterRadius.xyz);
    if (alongRay <= 0.0) {
        color = vec4(base, 1.0);
        return;
    }

    float centerSq = dot(u_CenterRadius.xyz, u_CenterRadius.xyz);
    float perpendicular = sqrt(max(0.0, centerSq - alongRay * alongRay));
    float radialT = perpendicular / radius;
    if (radialT >= 1.02) {
        color = vec4(base, 1.0);
        return;
    }

    float inside = max(0.0, radius * radius - perpendicular * perpendicular);
    float hitDistance = alongRay - sqrt(inside);
    if (hitDistance <= 0.0) hitDistance = alongRay;
    vec3 hit = rayDir * hitDistance;
    if (hit.y < u_CenterRadius.y) {
        color = vec4(base, 1.0);
        return;
    }

    if (u_Params.z > 0.5) {
        float rawDepth = texture(u_Depth, uv).r;
        if (rawDepth > 1.0e-6) {
            float depthNdc = rawDepth * u_DepthTransform.x + u_DepthTransform.y;
            vec4 sceneWorld = u_InvViewProj * vec4(ndc, depthNdc, 1.0);
            if (abs(sceneWorld.w) > 1.0e-6) {
                sceneWorld.xyz /= sceneWorld.w;
                float sceneDistance = length(sceneWorld.xyz);
                if (hitDistance > sceneDistance + max(0.05, sceneDistance * 0.005)) {
                    color = vec4(base, 1.0);
                    return;
                }
            }
        }
    }

    vec2 gradient = vec2(dFdx(perpendicular), dFdy(perpendicular));
    float gradientLength = length(gradient);
    if (gradientLength < 1.0e-6) {
        color = vec4(base, 1.0);
        return;
    }
    vec2 direction = gradient / gradientLength;

    float edgeFade = smoothstep(1.02, 0.9, radialT);
    float ripple = sin(radialT * 3.14159265 * 3.0);
    float amplitude = 0.026 * (0.7 + 0.3 * radialT) * edgeFade * u_Params.y;
    vec2 offset = direction * (ripple * amplitude);

    vec2 sampleUv = clamp(uv + offset, vec2(0.0), vec2(1.0));
    vec3 result = texture(u_Texture, sampleUv).rgb;
    float blurStep = edgeFade * 0.003 * u_Params.y;
    if (blurStep > 1.0e-5) {
        result += texture(u_Texture, clamp(sampleUv + direction * blurStep, vec2(0.0), vec2(1.0))).rgb;
        result += texture(u_Texture, clamp(sampleUv - direction * blurStep, vec2(0.0), vec2(1.0))).rgb;
        result += texture(u_Texture, clamp(sampleUv + direction * blurStep * 2.0, vec2(0.0), vec2(1.0))).rgb;
        result += texture(u_Texture, clamp(sampleUv - direction * blurStep * 2.0, vec2(0.0), vec2(1.0))).rgb;
        result *= 0.2;
    }

    color = vec4(result, 1.0);
}
