/*
 * Spatial hand-effect palette shared by raster materials and compute ghosting.
 * 0 = static, 1 = rainbow, 2 = light rainbow, 3 = sky,
 * 4 = fade, 5 = two-color, 6 = analogous, 7 = theme gradient.
 */
const float HAND_PALETTE_TAU = 6.28318530718;

vec3 handPaletteHue(float hue, float saturation) {
    vec3 spectrum = clamp(abs(mod(hue * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return mix(vec3(1.0), spectrum * spectrum * (3.0 - 2.0 * spectrum), saturation);
}

vec4 handPaletteColor(vec2 uv, float time, vec4 startColor, vec4 endColor, vec4 settings) {
    int mode = clamp(int(settings.x + 0.5), 0, 7);
    if (mode == 0) return startColor;

    vec2 axis = vec2(cos(radians(settings.w)), sin(radians(settings.w)));
    float position = dot(uv - 0.5, axis) * 1.41421356;
    float phase = time * settings.y;

    // Theme gradients are taken from the actual theme specification, including its angle.
    if (mode == 7) {
        float u = clamp(0.5 + position + 0.12 * sin(phase * HAND_PALETTE_TAU), 0.0, 1.0);
        return mix(startColor, endColor, smoothstep(0.0, 1.0, u));
    }

    // A full hue wheel should be visible even across a relatively small first-person hand.
    float spatialCycles = max(settings.z, 0.05) * (mode <= 3 ? 2.5 : 1.0);
    float t = fract(0.5 + position * spatialCycles + phase);
    if (mode <= 3) {
        float hue = mode == 3 ? mix(0.51, 0.69, 1.0 - abs(2.0 * t - 1.0)) : t;
        float saturation = mode == 1 ? 1.0 : (mode == 2 ? 0.60 : 0.55);
        return vec4(handPaletteHue(hue, saturation), startColor.a);
    }

    float wave = 1.0 - abs(2.0 * t - 1.0);
    wave = smoothstep(0.0, 1.0, wave);
    if (mode == 4) return vec4(startColor.rgb * mix(0.38, 1.0, wave), startColor.a);
    return mix(startColor, endColor, wave);
}
