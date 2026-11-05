#ifndef COMBATANT_DEFERRED_FROXEL_TRANSPORT_GLSL
#define COMBATANT_DEFERRED_FROXEL_TRANSPORT_GLSL

float combatant_froxel_boundary_distance(int boundaryIndex, int depthSlices,
                                         float maxDistance, float exponentValue) {
    if (boundaryIndex <= 0) return 0.0;
    float t = clamp(float(boundaryIndex) / float(max(depthSlices, 1)), 0.0, 1.0);
    return maxDistance * pow(t, max(exponentValue, 1.0));
}

#endif
