#version 430 core

layout(location = 0) in vec2 v_UV;
layout(location = 1) in vec4 v_Optics;
layout(location = 2) in float v_Fade;
layout(location = 0) out vec4 fragColor;

layout(binding = 1) uniform sampler2D u_Texture;
layout(binding = 2) uniform sampler2D u_Depth;
layout(std430, binding = 3) readonly buffer RainFrame {
    mat4 u_ViewProjection;
    vec4 u_Frame;
    vec4 u_WindScreen;
    vec4 u_Camera;
    vec4 u_NearAnchor;
    vec4 u_MidAnchor;
    vec4 u_FarAnchor;
    vec4 u_Extra;
};

void main() {
    if (v_Fade <= 0.003) discard;
    vec2 pixel = gl_FragCoord.xy;
    vec2 screenUV = pixel / max(u_WindScreen.zw, vec2(1.0));
    // The actual rasterized depth of THIS fragment, not the head of the drop.
    // gl_FragCoord.z is in the same [0, 1] window-depth convention as the sampled
    // attachment on both GL and Vulkan, including Minecraft's reverse-Z projection.
    // Comparing clip Z/W from a different point on the drop can reject the entire streak.
    float sceneDepth = texture(u_Depth, screenUV).r;
    float dropDepth = gl_FragCoord.z;
    float depthTolerance = 0.000035 + 0.00010 / max(v_Optics.z, 1.0);
    if (sceneDepth > dropDepth + depthTolerance) discard;

    float y = v_UV.y;
    float x = v_UV.x * 2.0 - 1.0;
    // A rounded, refracting leading bead with a short exposure-integrated taper.
    float bead = (1.0 - smoothstep(0.06, 0.40, abs(y - 0.80)));
    float radius = mix(0.20, 0.93, bead);
    float horizontal = abs(x) / max(radius, 0.025);
    float core = 1.0 - smoothstep(0.72, 1.06, horizontal);
    float vertical = smoothstep(0.0, 0.08, y) * (1.0 - smoothstep(0.93, 1.0, y));
    float coverage = core * vertical;
    if (coverage < 0.01) discard;

    float lod = v_Optics.w;
    float nearFactor = 1.0 - smoothstep(7.0, 32.0, v_Optics.z);
    float strength = clamp(v_Optics.y * 0.43, 0.15, 2.8) * nearFactor;
    // The lens normal bends scene samples mainly across the drop's curved bead.
    vec2 lensNormal = vec2(x * 0.82, (y - 0.80) * 0.95);
    float curvature = sqrt(max(0.0, 1.0 - min(1.0, dot(lensNormal, lensNormal))));
    vec2 bendPixels = lensNormal * (0.48 + 1.15 * (1.0 - curvature)) * strength;
    vec2 invScreen = 1.0 / max(u_WindScreen.zw, vec2(1.0));
    vec3 optical;
    if (lod < 0.5 && strength > 0.45) {
        // Only large near beads pay for modest dispersion. Never use expensive
        // multi-refraction at the thousands of subpixel distant particles.
        vec2 uvR = clamp(screenUV + bendPixels * invScreen * 1.045, vec2(0.0), vec2(1.0));
        vec2 uvG = clamp(screenUV + bendPixels * invScreen, vec2(0.0), vec2(1.0));
        vec2 uvB = clamp(screenUV + bendPixels * invScreen * 0.96, vec2(0.0), vec2(1.0));
        optical = vec3(texture(u_Texture, uvR).r,
                       texture(u_Texture, uvG).g,
                       texture(u_Texture, uvB).b);
    } else if (nearFactor > 0.025) {
        optical = texture(u_Texture, clamp(screenUV + bendPixels * invScreen, vec2(0.0), vec2(1.0))).rgb;
    } else {
        optical = texture(u_Texture, screenUV).rgb;
    }

    float rim = pow(clamp(horizontal, 0.0, 1.0), 3.2);
    float head = bead * pow(1.0 - clamp(horizontal, 0.0, 1.0), 0.65);
    float sceneLuma = dot(optical, vec3(0.2126, 0.7152, 0.0722));
    float ambient = clamp(sceneLuma * 1.75 + 0.07, 0.08, 1.0);
    // Refracted scene color alone is nearly invisible once alpha-blended back over
    // the identical source color. Give the bead a distinct, directional Fresnel rim
    // with a shaded interior, preserving scene-dependent illumination rather than
    // additive glow. Subpixel distant drops use this cheap contrast path too.
    float asymmetricShade = (0.5 + 0.5 * x) * rim;
    float transmission = 0.73 + 0.12 * head - 0.17 * asymmetricShade;
    float reflectance = (0.16 + 0.23 * head + 0.29 * rim)
                      * ambient * (0.72 + u_Camera.y * 0.10);
    vec3 result = optical * transmission + vec3(0.73, 0.83, 0.94) * reflectance;
    float alpha = v_Fade * coverage * (0.55 + 0.23 * head + 0.12 * rim);
    // Distant streaks are subpixel-thin; do not suppress their remaining contrast.
    if (lod > 1.5) alpha *= 0.65;
    fragColor = vec4(result, clamp(alpha, 0.0, 0.91));
}
