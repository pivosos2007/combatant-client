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
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 farWorld = u_InvViewProj * vec4(ndc, u_DepthTransform.z, 1.0);
    if (abs(farWorld.w) < 1.0e-6) {
        color = vec4(base, 1.0);
        return;
    }
    farWorld.xyz /= farWorld.w;
    vec3 rayDir = normalize(farWorld.xyz);

    if (abs(rayDir.y) < 1.0e-4) {
        color = vec4(base, 1.0);
        return;
    }

    float t = u_CenterRadius.y / rayDir.y;
    if (t <= 0.0) {
        color = vec4(base, 1.0);
        return;
    }

    vec3 hit = rayDir * t;
    float d = length(hit.xz - u_CenterRadius.xz);
    float ringDist = abs(d - u_CenterRadius.w);
    float thickness = u_Params.x;
    if (thickness <= 0.0001 || ringDist >= thickness) {
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
                float sceneDist = length(sceneWorld.xyz);
                if (t > sceneDist + max(0.05, sceneDist * 0.005)) {
                    color = vec4(base, 1.0);
                    return;
                }
            }
        }
    }

    float band = 1.0 - ringDist / thickness;
    float refrProfile = band * band;
    float bandSq = band * band;
    float glowProfile = bandSq * bandSq;
    float refr = 0.04 * refrProfile * u_Params.y;

    vec2 grad = vec2(dFdx(d), dFdy(d));
    float gradLength = length(grad);
    vec2 distortion = vec2(0.0);
    if (gradLength > 1.0e-6) {
        vec2 direction = grad / gradLength;
        distortion = direction * refr * sign(d - u_CenterRadius.w);
    }

    vec2 refractedUv = clamp(uv + distortion, vec2(0.0), vec2(1.0));
    vec3 refracted = texture(u_Texture, refractedUv).rgb;
    vec3 glow = u_RingColor.rgb * (glowProfile * u_RingColor.a);
    color = vec4(refracted + glow, 1.0);
}
