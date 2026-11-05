#ifndef COMBATANT_DEFERRED_FROXEL_MEDIA_DATA_GLSL
#define COMBATANT_DEFERRED_FROXEL_MEDIA_DATA_GLSL

layout(std430, binding = 0) readonly buffer FroxelMediaData {
    mat4 inverseProjection;
    mat4 inverseView;
    vec4 cameraTime;
    vec4 grid;
    vec4 macroGrid;
    vec4 macroOrigin;
    vec4 counts;
    vec4 noiseDomain;
    vec4 froxel;
    vec4 policy;
    vec4 shadowMapDomain;
    vec4 shadowSun;
    vec4 cloudBounds;
    vec4 aerialLayout;
    vec4 mediumDistribution;
    vec4 mediumScattering;
    vec4 mediumAbsorption;
    vec4 cameraMediumBoundary;
    vec4 cameraMediumScattering;
    vec4 cameraMediumAbsorption;
    vec4 mediumWeather;
    vec4 mediumWeatherState;
    vec4 mediumLighting;
    vec4 localFogBinning;
    vec4 localLightTransport;
    vec4 directionalDirection;
    vec4 directionalRadiance;
    vec4 cloudOccupancyDomain;
    vec4 cloudOccupancyPolicy;
} u_Data;

#endif
