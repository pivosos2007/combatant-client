/*
 * Shared GPU palette resolver for Shader ESP.
 * Mode ids are kept in sync with AnimatedRenderColors.shaderMode().
 */

float shaderEspWrap01(float value) {
    return fract(value);
}

float shaderEspCyclicMix(float value) {
    float wrapped = shaderEspWrap01(value);
    return wrapped < 0.5 ? wrapped * 2.0 : (1.0 - wrapped) * 2.0;
}

vec3 shaderEspHsvToRgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec3 shaderEspRgbToHsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

float shaderEspGradientProgress(vec2 coords, float angleDeg) {
    vec2 c = clamp(coords, vec2(0.0), vec2(1.0));
    float angle = radians(angleDeg);
    vec2 direction = vec2(cos(angle), sin(angle));
    float projection = dot(c, direction);
    float minProjection = min(0.0, direction.x) + min(0.0, direction.y);
    float maxProjection = max(0.0, direction.x) + max(0.0, direction.y);
    float range = maxProjection - minProjection;
    return range > 1.0e-6
        ? clamp((projection - minProjection) / range, 0.0, 1.0)
        : 0.5;
}

vec3 shaderEspPaletteColor(int mode, float phaseDeg, vec3 primary, vec3 secondary) {
    float p = shaderEspWrap01(phaseDeg / 360.0);

    if (mode == 1) {
        return shaderEspHsvToRgb(vec3(p, 1.0, 1.0));
    }
    if (mode == 2) {
        return shaderEspHsvToRgb(vec3(p, 0.6, 1.0));
    }
    if (mode == 3) {
        float hue = p < 0.5 ? -p : p;
        return shaderEspHsvToRgb(vec3(hue, 0.5, 1.0));
    }
    if (mode == 4) {
        vec3 hsv = shaderEspRgbToHsv(primary);
        hsv.z = 0.5 + 0.5 * shaderEspCyclicMix(p);
        return shaderEspHsvToRgb(hsv);
    }
    if (mode == 5 || mode == 7) {
        return mix(primary, secondary, shaderEspCyclicMix(p));
    }
    if (mode == 6) {
        vec3 primaryHsv = shaderEspRgbToHsv(primary);
        vec3 secondaryHsv = shaderEspRgbToHsv(secondary);
        return shaderEspHsvToRgb(mix(primaryHsv, secondaryHsv, shaderEspCyclicMix(p)));
    }

    return primary;
}

vec3 shaderEspResolveColor(vec2 coords,
                           int mode,
                           float baseAngleDeg,
                           float spatialSpreadDeg,
                           float gradientAngleDeg,
                           vec3 primary,
                           vec3 secondary) {
    float progress = shaderEspGradientProgress(coords, gradientAngleDeg);
    float phaseDeg = baseAngleDeg + progress * spatialSpreadDeg;
    return shaderEspPaletteColor(mode, phaseDeg, primary, secondary);
}
