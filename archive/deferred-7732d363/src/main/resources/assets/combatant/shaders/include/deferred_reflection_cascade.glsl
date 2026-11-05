#ifndef COMBATANT_DEFERRED_REFLECTION_CASCADE_GLSL
#define COMBATANT_DEFERRED_REFLECTION_CASCADE_GLSL

/*
 * Caller contract:
 *   - CombatantReflectionCascadeFace type is declared.
 *   - COMBATANT_REFLECTION_CASCADE_FACE(i) returns that face for index i.
 */
bool combatantSampleReflectionCascade(sampler2D cascadeColor,
                                       sampler2D cascadeDepth,
                                       vec3 currentWorldRelative,
                                       vec3 reflectedWorld,
                                       float surfaceDistance,
                                       int totalFaces,
                                       out vec3 color) {
    int safeFaceCount = clamp(totalFaces, 0, 24);
    int cascadeCount = safeFaceCount / 6;
    if (cascadeCount <= 0) return false;

    int selectedCascade = cascadeCount - 1;
    for (int cascade = 0; cascade < 4; ++cascade) {
        if (cascade >= cascadeCount) break;
        CombatantReflectionCascadeFace rangeFace = COMBATANT_REFLECTION_CASCADE_FACE(cascade * 6);
        if (surfaceDistance <= rangeFace.rangeFace.y) {
            selectedCascade = cascade;
            break;
        }
    }

    int firstFace = selectedCascade * 6;
    for (int faceOffset = 0; faceOffset < 6; ++faceOffset) {
        CombatantReflectionCascadeFace face = COMBATANT_REFLECTION_CASCADE_FACE(firstFace + faceOffset);
        vec3 captureRelative = currentWorldRelative - face.originDelta.xyz;
        vec3 probePoint = captureRelative + reflectedWorld * max(1.0, face.rangeFace.y);
        vec4 clip = face.viewProjection * vec4(probePoint, 1.0);
        if (clip.w <= 1.0e-6) continue;

        vec2 localUv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(localUv, vec2(0.001))) || any(greaterThan(localUv, vec2(0.999)))) continue;

        vec2 atlasUv = localUv * face.atlasScaleBias.xy + face.atlasScaleBias.zw;
        float capturedDepth = textureLod(cascadeDepth, atlasUv, 0.0).r;
        if (capturedDepth <= 0.0) continue;

        color = textureLod(cascadeColor, atlasUv, 0.0).rgb;
        return true;
    }
    return false;
}

#endif
