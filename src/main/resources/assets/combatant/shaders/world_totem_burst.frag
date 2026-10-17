#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * Layered spherical TotemFX surface.
 *
 * Params:
 *   v_Params.x = normalized lifetime progress
 *   v_Params.y = stable seed [0,1]
 *   v_Params.z = profile (0 self/defensive, 1 attacked/offensive)
 *   v_Params.w = intensity
 *   v_Params2.xyz = unit sphere normal
 *   v_Params2.w = layer (0 outer shock shell, 1 hot core, 2 inner energy rim)
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec3 v_ViewPosition;
layout(location = 7) in vec3 v_ViewNormal;
layout(location = 0) out vec4 color;

const float PI = 3.141592653589793;
const float TAU = 6.283185307179586;

float sat(float x) { return clamp(x, 0.0, 1.0); }
float hash11(float p) { return fract(sin(p * 127.1 + 311.7) * 43758.5453123); }

vec3 rotateY(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * v.x + s * v.z, v.y, -s * v.x + c * v.z);
}

vec3 rotateX(vec3 v, float a) {
    float c = cos(a), s = sin(a);
    return vec3(v.x, c * v.y - s * v.z, s * v.y + c * v.z);
}

float circleBand(vec3 n, vec3 axis, float width) {
    return 1.0 - smoothstep(width, width * 2.45, abs(dot(n, normalize(axis))));
}

void main() {
    float t = sat(v_Params.x);
    float seed = v_Params.y * 97.0;
    float offensive = step(0.5, v_Params.z);
    float intensity = max(v_Params.w, 0.0);
    float layer = v_Params2.w;

    vec3 n = normalize(v_Params2.xyz);
    vec3 viewN = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    float facing = sat(abs(dot(viewN, viewDir)));
    float fresnel = 1.0 - facing;

    float enter = smoothstep(0.0, 0.065, t);
    float leave = 1.0 - smoothstep(offensive > 0.5 ? 0.58 : 0.52, 1.0, t);
    float life = enter * leave;

    float outerLayer = 1.0 - step(0.5, layer);
    float coreLayer = step(0.5, layer) * (1.0 - step(1.5, layer));
    float innerLayer = step(1.5, layer);

    // A readable two-step Fresnel structure: a thin hot outer edge and a broader inner shoulder.
    float rimOuter = smoothstep(0.70, 0.985, fresnel);
    float rimShoulder = smoothstep(0.24, 0.70, fresnel) * (1.0 - smoothstep(0.79, 0.985, fresnel));
    float centerFacing = pow(facing, 1.35);

    // Rotating great-circle energy arcs. These are coherent structures, not noise/caustics.
    float spin = t * (offensive > 0.5 ? 5.8 : 4.15) + seed * 0.037;
    vec3 axis0 = rotateY(normalize(vec3(0.26, 0.91, 0.31)), spin);
    vec3 axis1 = rotateX(rotateY(normalize(vec3(-0.72, 0.38, 0.58)), -spin * 0.73), spin * 0.31);
    vec3 axis2 = rotateY(normalize(vec3(0.48, -0.56, 0.68)), spin * 1.27 + 1.4);
    float width = mix(0.050, 0.022, sat(t * 1.08));
    float arc0 = circleBand(n, axis0, width);
    float arc1 = circleBand(n, axis1, width * 0.84);
    float arc2 = circleBand(n, axis2, width * (offensive > 0.5 ? 0.72 : 1.04));
    float arcs = max(arc0, max(arc1 * 0.82, arc2 * (0.58 + offensive * 0.34)));

    // Traveling segmented pulses along the arcs make them feel energetic instead of painted on.
    float longitude = atan(n.z, n.x);
    float latitude = asin(clamp(n.y, -1.0, 1.0));
    float segment = 0.5 + 0.5 * sin(longitude * (offensive > 0.5 ? 8.0 : 6.0)
                                  + latitude * 3.0 - spin * 2.2 + seed * 0.21);
    segment = smoothstep(0.48, 0.92, segment);
    arcs *= 0.54 + segment * 0.72;

    // Very low amplitude breakup only prevents mathematical perfection; it no longer defines the effect.
    float breakup = 0.5 + 0.5 * sin(dot(n, normalize(vec3(0.73, 0.41, -0.55))) * 15.0
                                    + seed * 0.19 - t * 6.2);
    breakup = mix(0.90, 1.08, breakup);

    float outerEnergy = outerLayer * life * (
            rimOuter * 1.18
          + rimShoulder * 0.46
          + arcs * (0.34 + rimShoulder * 0.42)
          + 0.055 * centerFacing
    ) * breakup;

    // The inner shell is deliberately rim-heavy and slightly delayed, making a nested energy boundary.
    float innerDelay = smoothstep(0.035, 0.13, t);
    float innerFade = 1.0 - smoothstep(offensive > 0.5 ? 0.46 : 0.40, 0.86, t);
    float innerPulse = 0.72 + 0.28 * sin(t * 20.0 + seed * 0.17);
    float innerEnergy = innerLayer * innerDelay * innerFade * (
            rimOuter * 0.92
          + rimShoulder * 0.74
          + arcs * 0.38
    ) * innerPulse;

    // Hot nucleus: compact and smooth, with only arc echoes. No high-frequency caustic field.
    float coreLife = enter * (1.0 - smoothstep(0.10, offensive > 0.5 ? 0.52 : 0.45, t));
    float corePulse = 0.82 + 0.18 * sin(t * 24.0 + seed * 0.13);
    float coreEnergy = coreLayer * coreLife * (
            centerFacing * 0.74
          + rimShoulder * 0.28
          + rimOuter * 0.42
          + arcs * 0.26
    ) * corePulse;

    // Offensive pops carry a few harder slash great-circles, still wrapped around the actual sphere.
    float slashes = 0.0;
    if (offensive > 0.5 && outerLayer > 0.5) {
        vec3 s0 = rotateY(normalize(vec3(0.52, 0.68, -0.51)), -spin * 0.42);
        vec3 s1 = rotateX(normalize(vec3(-0.72, 0.31, 0.62)), spin * 0.36);
        vec3 s2 = rotateY(normalize(vec3(0.21, -0.86, 0.47)), spin * 0.61 + 0.8);
        float sw = mix(0.052, 0.018, t);
        slashes = max(circleBand(n, s0, sw), max(circleBand(n, s1, sw), circleBand(n, s2, sw)));
        slashes *= enter * (1.0 - smoothstep(0.44, 0.84, t));
    }

    vec3 primary = v_Color.rgb;
    vec3 secondary = offensive > 0.5 ? vec3(1.0, 0.58, 0.24) : vec3(0.31, 1.0, 0.80);
    vec3 hot = mix(secondary, vec3(1.0), offensive > 0.5 ? 0.48 : 0.32);

    float shell = outerEnergy * (offensive > 0.5 ? 1.10 : 0.92);
    float inner = innerEnergy * (offensive > 0.5 ? 1.02 : 0.90);
    float core = coreEnergy * (offensive > 0.5 ? 1.02 : 0.88);

    vec3 glow = primary * (shell * 0.62 + inner * 0.36)
              + secondary * (shell * 0.48 + inner * 0.72 + core * 0.42)
              + hot * (rimOuter * shell * 0.46 + core * 0.92 + slashes * 0.88 + arcs * inner * 0.26);

    float energy = (shell + inner + core + slashes * 0.82) * intensity * v_Color.a;
    if (energy <= 0.002) discard;

    glow *= intensity;
    glow = glow / (1.0 + glow * 0.24);
    float peak = max(max(glow.r, glow.g), glow.b);
    if (peak > 1.0) glow /= peak;

    color = vec4(glow, sat(energy));
}
