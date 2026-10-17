#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * Analytic world-space spherical post-process mask.
 *
 * Params:
 *   v_Params.x = refraction strength [0,1]
 *   v_Params.y = signed swirl [-1,1]
 *   v_Params.z = animated phase (radians)
 *   v_Params.w = internal warp/breakup [0,1]
 *   v_Color.r  = cavity weight [0,1]
 *   v_Color.g  = rim weight [0,1]
 *   v_Params2.xyz = sphere normal
 *   v_Params2.w   = opacity
 *
 * Output matches the shared world-mask contract:
 *   RG = encoded signed screen flow, B = local strength, A = coverage.
 */

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec3 v_ViewPosition;
layout(location = 7) in vec3 v_ViewNormal;
layout(location = 0) out vec4 color;

const float TAU = 6.283185307179586;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    float strength = sat(v_Params.x);
    float swirl = clamp(v_Params.y, -1.0, 1.0);
    float phase = v_Params.z;
    float warp = sat(v_Params.w);
    float cavityWeight = sat(v_Color.r);
    float rimWeight = sat(v_Color.g);
    float opacity = sat(v_Params2.w);

    vec3 worldN = normalize(v_Params2.xyz);
    vec3 viewN = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);

    // facing=1 at the projected sphere center, 0 toward its silhouette.
    float facing = sat(abs(dot(viewN, viewDir)));
    float fresnel = 1.0 - facing;
    float cavity = pow(facing, 0.62);
    float shoulder = smoothstep(0.08, 0.62, fresnel) * (1.0 - smoothstep(0.72, 0.99, fresnel));
    float rim = smoothstep(0.56, 0.97, fresnel);

    // The sphere normal gives a seam-free projected radial flow. At the exact center use the
    // animated phase as a stable fallback instead of lat/long UVs.
    vec2 radial = viewN.xy;
    float radialLength = length(radial);
    if (radialLength > 1.0e-5) radial /= radialLength;
    else radial = vec2(cos(phase), sin(phase));
    vec2 tangent = vec2(-radial.y, radial.x);

    // Low-amplitude 3D breakup: this modulates a coherent cavity, it does not define the shape.
    vec3 axisA = normalize(vec3(cos(phase * 0.73), 0.61, sin(phase * 0.73)));
    vec3 axisB = normalize(vec3(-0.47, sin(phase * 0.51) * 0.42 + 0.74, 0.53));
    float waveA = sin(dot(worldN, axisA) * 9.0 + phase * 1.7);
    float waveB = cos(dot(worldN, axisB) * 13.0 - phase * 1.13);
    float breakup = (waveA * 0.58 + waveB * 0.42) * warp;

    // Interior flow curls gently while the rim kicks outward. This produces an actual refractive
    // cavity over the full projected sphere instead of a texture-shaped caustic shell.
    float tangentBias = swirl * (0.28 + cavity * 0.72) + breakup * 0.24;
    vec2 flow = radial * (0.58 + rim * 0.72 - cavity * 0.18)
              + tangent * tangentBias;
    float flowLength = length(flow);
    if (flowLength > 1.0e-5) flow /= flowLength;

    float interiorCoverage = cavityWeight * (0.44 + cavity * 0.56 + shoulder * 0.18);
    float rimCoverage = rimWeight * (rim * 0.92 + shoulder * 0.28);
    float coverage = sat((interiorCoverage + rimCoverage) * opacity);
    coverage *= 0.92 + breakup * 0.08;

    float localStrength = strength * (
            cavityWeight * (0.58 + cavity * 0.52)
          + rimWeight * (0.48 + rim * 0.92)
          + shoulder * 0.12
    );
    localStrength = sat(localStrength * (0.96 + breakup * 0.10));

    if (coverage <= 0.003 || localStrength <= 0.001) discard;

    vec3 encoded = vec3(flow * 0.5 + 0.5, localStrength);
    color = vec4(encoded * coverage, coverage);
}
