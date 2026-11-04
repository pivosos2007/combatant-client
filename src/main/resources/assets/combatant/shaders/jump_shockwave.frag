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

// Depth snapshots are reverse-Z with [0, 1] clip depth, or conventional
// depth with [-1, 1] clip depth. A zero w marks a missing depth sample.
vec4 scenePosition(vec2 uv) {
    float raw = texture(u_Depth, uv).r;
    bool valid = u_DepthTransform.x < 1.5
            ? raw > 1.0e-6
            : raw < 1.0 - 1.0e-6;
    if (!valid) return vec4(0.0);

    vec4 world = u_InvViewProj * vec4(
            uv * 2.0 - 1.0, raw * u_DepthTransform.x + u_DepthTransform.y, 1.0);
    if (abs(world.w) < 1.0e-6) return vec4(0.0);
    return vec4(world.xyz / world.w, 1.0);
}

void main() {
    vec2 uv = v_TexCoord;
    vec4 sceneColor = texture(u_Texture, uv);
    vec3 base = sceneColor.rgb;
    vec2 ndc = uv * 2.0 - 1.0;

    vec4 farWorld = u_InvViewProj * vec4(ndc, u_DepthTransform.z, 1.0);
    if (abs(farWorld.w) < 1.0e-6) {
        color = vec4(base, sceneColor.a);
        return;
    }
    vec3 rayDir = normalize(farWorld.xyz / farWorld.w);
    float rayY = rayDir.y;
    if (abs(rayY) < 1.0e-4) {
        color = vec4(base, sceneColor.a);
        return;
    }

    float t = u_CenterRadius.y / rayY;
    if (t <= 0.0) {
        color = vec4(base, sceneColor.a);
        return;
    }

    vec3 hit = rayDir * t;
    vec2 radial = hit.xz - u_CenterRadius.xz;
    float d = length(radial);
    float signedRingDist = d - u_CenterRadius.w;
    float ringDist = abs(signedRingDist);
    float thickness = u_Params.x;

    // Coarse rejection only: the final ring boundary is filtered according
    // to the actual pixel footprint, so no hard one-pixel outline remains.
    if (thickness <= 0.0001 || ringDist > thickness + 0.5) {
        color = vec4(base, sceneColor.a);
        return;
    }

    vec2 resolution = vec2(textureSize(u_Texture, 0));

    // Analytical screen-space derivative of the plane/ring intersection.
    // Unlike dFdx/dFdy after divergent early returns, this is defined at
    // every evaluated fragment (including at depth discontinuities).
    vec3 rayDx = u_InvViewProj[0].xyz;
    vec3 rayDy = u_InvViewProj[1].xyz;
    vec2 dx = rayDx.xz * farWorld.y - farWorld.xz * rayDx.y;
    vec2 dy = rayDy.xz * farWorld.y - farWorld.xz * rayDy.y;
    vec2 gradient = u_CenterRadius.y * vec2(
            dot(radial, dx) / resolution.x,
            dot(radial, dy) / resolution.y);
    float gradientLength = length(gradient);
    float worldPerPixel = 2.0 * gradientLength /
            max(d * farWorld.y * farWorld.y, 1.0e-8);
    float ringWidthPixels = thickness / max(worldPerPixel, 1.0e-5);

    // A nearly edge-on annulus collapses to a broken horizontal line at
    // subpixel thickness. Fade it out instead of drawing sporadic fragments.
    float footprintFade = smoothstep(0.75, 2.5, ringWidthPixels);
    float grazingFade = smoothstep(0.025, 0.085, abs(rayY));
    float edgeAA = min(0.5, max(worldPerPixel * 0.75, 1.0e-5));
    float ringCoverage = 1.0 - smoothstep(
            max(0.0, thickness - edgeAA), thickness + edgeAA, ringDist);
    float visibility = footprintFade * grazingFade * ringCoverage;
    if (visibility <= 1.0e-4) {
        color = vec4(base, sceneColor.a);
        return;
    }

    // The annulus lives in a world-space horizontal plane, even where the
    // terrain ends. Depth only occludes it when geometry is closer to the
    // camera; it is NOT a requirement that a ground surface exists there.
    vec4 surface = vec4(0.0);
    if (u_Params.z > 0.5) {
        surface = scenePosition(uv);
        if (surface.w > 0.0) {
            float sceneDist = length(surface.xyz);
            if (t > sceneDist + max(0.05, sceneDist * 0.005)) {
                color = vec4(base, sceneColor.a);
                return;
            }
        }
    }

    float band = clamp(1.0 - ringDist / thickness, 0.0, 1.0);
    float refrProfile = band * band;
    float glowProfile = refrProfile * refrProfile;
    float refr = 0.04 * refrProfile * u_Params.y;

    vec2 offset = vec2(0.0);
    if (gradientLength > 1.0e-7) {
        float centerAA = max(worldPerPixel, 1.0e-5);
        float side = signedRingDist * inversesqrt(
                signedRingDist * signedRingDist + centerAA * centerAA);
        offset = (gradient / gradientLength) * (refr * side);

        // UV displacement of 0.08 used to pull scene pixels >150px away
        // at 1080p. Bound it to the local band width and a small screen
        // distance to avoid dragging entire block textures through edges.
        float maxOffsetPixels = min(28.0, ringWidthPixels * 0.35);
        float offsetPixels = length(offset * resolution);
        if (offsetPixels > maxOffsetPixels && offsetPixels > 1.0e-5) {
            offset *= maxOffsetPixels / offsetPixels;
        }
    }

    vec2 sampleUv = uv + offset;
    vec2 safeUv = clamp(sampleUv, 0.5 / resolution, 1.0 - 0.5 / resolution);
    float outOfBounds = length((sampleUv - safeUv) * resolution);
    float refractionMask = 1.0 - smoothstep(0.0, 2.0, outOfBounds);

    if (u_Params.z > 0.5 && refractionMask > 0.0) {
        vec4 shiftedSurface = scenePosition(safeUv);
        if (surface.w > 0.0 && shiftedSurface.w > 0.0) {
            // Prevent sampling a different block/face through a depth edge.
            // Neither surface has to be at the ring's height: the wave must
            // also distort the scene beyond an overhang or a block edge.
            float heightGap = abs(shiftedSurface.y - surface.y);
            float surfaceDistance = length(surface.xyz);
            float depthGap = abs(length(shiftedSurface.xyz) - surfaceDistance);
            float depthThreshold = max(0.08, surfaceDistance * 0.015);
            refractionMask *= (1.0 - smoothstep(0.15, 0.55, heightGap))
                    * (1.0 - smoothstep(depthThreshold, depthThreshold * 3.0, depthGap));
        } else if (surface.w != shiftedSurface.w) {
            // A shifted sample crossing between sky/void and actual geometry
            // must not drag the block texture into the empty region (or vice
            // versa). Refraction fades here; the visible ring stays intact.
            refractionMask = 0.0;
        }
        // Both invalid depth samples represent unobstructed sky/void.
    }

    vec3 refracted = texture(u_Texture, safeUv).rgb;
    vec3 glow = u_RingColor.rgb * (glowProfile * u_RingColor.a);
    color = vec4(mix(base, refracted, refractionMask * visibility)
            + glow * visibility, sceneColor.a);
}
