#version 450 core

layout(binding = 0) uniform sampler2D u_OpaqueDepth;

layout(location = 5) in vec3 te_ViewPosition;
layout(location = 7) in vec3 te_ViewNormal;

layout(location = 0) out vec4 outBoundary;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 extent = textureSize(u_OpaqueDepth, 0);
    if (any(lessThan(pixel, ivec2(0))) || any(greaterThanEqual(pixel, extent))) discard;

    // Primary world depth is reversed-Z. Reject water hidden behind opaque geometry.
    float opaqueDepth = texelFetch(u_OpaqueDepth, pixel, 0).r;
    if (gl_FragCoord.z + 1.0e-6 < opaqueDepth) discard;

    vec3 incident = normalize(te_ViewPosition); // camera -> boundary
    vec3 normal = normalize(te_ViewNormal);
    float orientedSide = dot(incident, normal);
    float transition = orientedSide < 0.0 ? 1.0 : 2.0; // entering / exiting
    float distanceToBoundary = length(te_ViewPosition);

    outBoundary = vec4(max(distanceToBoundary, 0.0), transition, 1.0, 1.0);
}
