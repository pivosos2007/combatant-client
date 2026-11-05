#ifndef COMBATANT_DEFERRED_REFLECTION_TRACE_GLSL
#define COMBATANT_DEFERRED_REFLECTION_TRACE_GLSL

/*
 * Shared screen-space reflection traversal contract.
 *
 * The caller owns semantic ray construction (surface position/normal/material). This include owns
 * only projection, Hi-Z traversal, footprint/slope/distance-aware hit thickness and geometric
 * confidence so opaque and forward-water SSR cannot silently drift into different algorithms.
 */
struct CombatantReflectionTracePolicy {
    mat4 inverseProjection;
    mat4 projection;
    vec4 depthTransform;
    // xy: full-resolution extent, z: camera far, w: trace max-distance scale.
    vec4 traceExtentAndFar;
    // x: max steps, y: minimum step, z: view-depth step scale, w: step growth.
    vec4 traceParams0;
    // x: minimum hit thickness, y: normal bias, z: edge margin, w: Hi-Z mip step scale.
    vec4 traceParams1;
};

struct CombatantReflectionTraceResult {
    vec2 hitUv;
    float confidence;
    float travelled;
    float valid;
};

vec3 combatantReflectionDecodeOctahedral(vec2 encoded) {
    vec2 f = encoded * 2.0 - 1.0;
    vec3 n = vec3(f, 1.0 - abs(f.x) - abs(f.y));
    if (n.z < 0.0) n.xy = (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    return normalize(n);
}

vec2 combatantReflectionEncodeOctahedral(vec3 normal) {
    vec3 n = normalize(normal);
    n /= max(abs(n.x) + abs(n.y) + abs(n.z), 1.0e-7);
    vec2 encoded = n.xy;
    if (n.z < 0.0) encoded = (1.0 - abs(encoded.yx)) * vec2(encoded.x >= 0.0 ? 1.0 : -1.0, encoded.y >= 0.0 ? 1.0 : -1.0);
    return encoded * 0.5 + 0.5;
}

vec3 combatantReflectionReconstructView(CombatantReflectionTracePolicy policy, vec2 uv, float depth) {
    vec2 ndcXY = uv * 2.0 - 1.0;
    float ndcZ = depth * policy.depthTransform.x + policy.depthTransform.y;
    vec4 h = policy.inverseProjection * vec4(ndcXY, ndcZ, 1.0);
    return h.xyz / max(abs(h.w), 1.0e-7) * sign(h.w);
}

bool combatantReflectionProjectView(CombatantReflectionTracePolicy policy,
                                     vec3 viewPosition,
                                     out vec2 uv,
                                     out float depth) {
    vec4 clip = policy.projection * vec4(viewPosition, 1.0);
    if (abs(clip.w) < 1.0e-7) return false;
    vec3 ndc = clip.xyz / clip.w;
    uv = ndc.xy * 0.5 + 0.5;
    depth = ndc.z * policy.depthTransform.z + policy.depthTransform.w;
    return clip.w > 0.0;
}

bool combatantReflectionOwnsGbufferPixel(sampler2D gbufferDepth, vec2 uv, float currentDepth) {
    float ownedDepth = textureLod(gbufferDepth, uv, 0.0).r;
    if (ownedDepth <= 0.0 || currentDepth <= 0.0) return false;
    float tolerance = max(1.0e-6, abs(ownedDepth) * 1.0e-5);
    return abs(ownedDepth - currentDepth) <= tolerance;
}

float combatantReflectionConeFootprintPixels(CombatantReflectionTracePolicy policy,
                                              float roughness,
                                              float travel,
                                              vec3 rayPosition) {
    float alpha = roughness * roughness;
    float projectionScale = 0.5 * policy.traceExtentAndFar.y * abs(policy.projection[1][1]);
    return max(1.0, alpha * travel * projectionScale / max(abs(rayPosition.z), 1.0e-4));
}

float combatantReflectionFootprintThickness(CombatantReflectionTracePolicy policy,
                                             sampler2D resolvedDepth,
                                             vec2 uv,
                                             float footprintPixels,
                                             vec3 rayDirection,
                                             float travel,
                                             float maxDistance) {
    vec2 texel = 1.0 / max(policy.traceExtentAndFar.xy, vec2(1.0));
    vec2 footprint = texel * max(1.0, footprintPixels);
    vec2 lo = clamp(uv - footprint, vec2(0.0), vec2(1.0));
    vec2 hi = clamp(uv + footprint, vec2(0.0), vec2(1.0));
    float dx = abs(textureLod(resolvedDepth, vec2(hi.x, uv.y), 0.0).r
                 - textureLod(resolvedDepth, vec2(lo.x, uv.y), 0.0).r);
    float dy = abs(textureLod(resolvedDepth, vec2(uv.x, hi.y), 0.0).r
                 - textureLod(resolvedDepth, vec2(uv.x, lo.y), 0.0).r);

    // The footprint already widens with roughness. Local depth slope then expands tolerance for
    // grazing rays and progressively for distant samples, where a fixed depth epsilon is unstable.
    float localSlope = 0.5 * (dx + dy);
    float axial = max(abs(rayDirection.z), 0.125);
    float grazing = sqrt(max(1.0 - axial * axial, 0.0)) / axial;
    float distanceWeight = clamp(travel / max(maxDistance, 1.0e-4), 0.0, 1.0);
    float derivedThickness = localSlope * (1.0 + grazing * distanceWeight);
    return max(policy.traceParams1.x, derivedThickness);
}

int combatantReflectionEstimatedMipCount(sampler2D depthPyramid) {
    ivec2 baseSize = max(textureSize(depthPyramid, 0), ivec2(1));
    float largest = float(max(baseSize.x, baseSize.y));
    return max(1, 1 + int(floor(log2(largest))));
}

CombatantReflectionTraceResult combatantTraceScreenReflection(
        CombatantReflectionTracePolicy policy,
        sampler2D resolvedDepth,
        sampler2D gbufferDepth,
        sampler2D depthPyramid,
        sampler2D gbufferGeometry,
        vec3 surfaceViewPosition,
        vec3 surfaceViewNormal,
        vec3 rayDirection,
        float roughness) {
    CombatantReflectionTraceResult result;
    result.hitUv = vec2(-1.0);
    result.confidence = 0.0;
    result.travelled = 0.0;
    result.valid = 0.0;

    vec3 normal = normalize(surfaceViewNormal);
    vec3 direction = normalize(rayDirection);
    if (dot(direction, normal) <= 0.0) return result;

    vec3 rayPosition = surfaceViewPosition + normal * policy.traceParams1.y;
    float stepLength = max(policy.traceParams0.y, abs(surfaceViewPosition.z) * policy.traceParams0.z);
    float maxDistance = max(1.0, policy.traceExtentAndFar.z * policy.traceExtentAndFar.w);
    float stepGrowth = policy.traceParams0.w;
    float edgeMargin = policy.traceParams1.z;
    float mipStepScale = policy.traceParams1.w;
    int maxSteps = clamp(int(policy.traceParams0.x + 0.5), 1, 256);
    int mipCount = combatantReflectionEstimatedMipCount(depthPyramid);
    float hitAgreement = 0.0;
    float hitFacing = 0.0;

    for (int stepIndex = 0; stepIndex < 256; ++stepIndex) {
        if (stepIndex >= maxSteps) break;
        result.travelled += stepLength;
        if (result.travelled > maxDistance) break;
        rayPosition += direction * stepLength;
        stepLength *= stepGrowth;

        vec2 rayUv;
        float rayDepth;
        if (!combatantReflectionProjectView(policy, rayPosition, rayUv, rayDepth)) break;
        if (any(lessThanEqual(rayUv, vec2(edgeMargin)))
                || any(greaterThanEqual(rayUv, vec2(1.0 - edgeMargin)))) break;
        if (rayDepth <= 0.0 || rayDepth > 1.0) continue;

        float footprintPixels = combatantReflectionConeFootprintPixels(
                policy, clamp(roughness, 0.0, 1.0), result.travelled, rayPosition);
        float stepMip = floor(log2(1.0 + float(stepIndex) * mipStepScale));
        float coneMip = floor(log2(footprintPixels));
        float mip = clamp(max(stepMip, coneMip), 0.0, float(max(0, mipCount - 1)));
        float nearestDepth = textureLod(depthPyramid, rayUv, mip).r;
        if (nearestDepth <= 0.0) continue;

        float effectiveThickness = combatantReflectionFootprintThickness(
                policy, resolvedDepth, rayUv, footprintPixels, direction, result.travelled, maxDistance);
        // Reversed-Z: lower depth is farther away. Crossing the nearest depth means the ray entered
        // geometry; mip 0 then verifies ownership and geometric facing.
        if (rayDepth <= nearestDepth + effectiveThickness) {
            float fullDepth = textureLod(resolvedDepth, rayUv, 0.0).r;
            if (combatantReflectionOwnsGbufferPixel(gbufferDepth, rayUv, fullDepth)
                    && rayDepth <= fullDepth + effectiveThickness) {
                vec3 hitNormal = combatantReflectionDecodeOctahedral(
                        textureLod(gbufferGeometry, rayUv, 0.0).rg);
                float facing = clamp(dot(hitNormal, -direction), 0.0, 1.0);
                if (facing <= 0.0) continue;
                result.hitUv = rayUv;
                hitFacing = facing;
                hitAgreement = 1.0 - clamp(
                        abs(rayDepth - fullDepth) / max(effectiveThickness, 1.0e-7), 0.0, 1.0);
                break;
            }
        }
    }

    if (result.hitUv.x < 0.0) return result;

    float edgeDistance = min(min(result.hitUv.x, 1.0 - result.hitUv.x),
                             min(result.hitUv.y, 1.0 - result.hitUv.y));
    float edgeEnd = max(edgeMargin * 4.0, edgeMargin + 1.0e-4);
    float edgeConfidence = smoothstep(edgeMargin, edgeEnd, edgeDistance);
    float travelConfidence = clamp(1.0 - result.travelled / maxDistance, 0.0, 1.0);
    result.confidence = clamp(edgeConfidence * travelConfidence * hitFacing * hitAgreement, 0.0, 1.0);
    result.valid = 1.0;
    return result;
}

#endif
