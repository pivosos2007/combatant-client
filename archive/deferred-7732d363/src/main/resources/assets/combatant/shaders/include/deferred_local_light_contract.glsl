#ifndef COMBATANT_DEFERRED_LOCAL_LIGHT_CONTRACT_GLSL
#define COMBATANT_DEFERRED_LOCAL_LIGHT_CONTRACT_GLSL

struct LocalLight {
    vec4 positionRadius;
    vec4 radianceType;
    vec4 directionOuter;
    vec4 coneArea;
    vec4 shadowInfo;
};

struct LocalShadowView {
    mat4 viewProjection;
    vec4 atlasScaleBias;
    vec4 originNear;
    vec4 rangeTexel;
};

const int COMBATANT_LOCAL_LIGHT_POINT = 0;
const int COMBATANT_LOCAL_LIGHT_SPOT = 1;
const int COMBATANT_LOCAL_LIGHT_SPHERE = 2;

#endif
