#version 330 core
#extension GL_ARB_separate_shader_objects : require

/* Iris depth copies are sampled textures, not Blaze3D render attachments. */
uniform sampler2D u_Depth;

layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;
layout(location = 4) in vec4 v_Params;
layout(location = 5) in vec4 v_Params2;
layout(location = 6) in vec3 v_ViewPosition;
layout(location = 7) in vec3 v_ViewNormal;
layout(location = 0) out vec4 color;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec2 depthSize = vec2(textureSize(u_Depth, 0));
    vec2 depthUv = clamp(gl_FragCoord.xy / depthSize, vec2(0.0), vec2(1.0));
    float sceneDepth = texture(u_Depth, depthUv).r;
    // Minecraft 26.2 uses reversed depth: larger window-depth values are closer.
    if (gl_FragCoord.z + 0.00002 < sceneDepth) discard;

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

    float facing = sat(abs(dot(viewN, viewDir)));
    float fresnel = 1.0 - facing;
    float cavity = pow(facing, 0.62);
    float shoulder = smoothstep(0.08, 0.62, fresnel) * (1.0 - smoothstep(0.72, 0.99, fresnel));
    float rim = smoothstep(0.56, 0.97, fresnel);

    vec2 radial = viewN.xy;
    float radialLength = length(radial);
    if (radialLength > 1.0e-5) radial /= radialLength;
    else radial = vec2(cos(phase), sin(phase));
    vec2 tangent = vec2(-radial.y, radial.x);

    vec3 axisA = normalize(vec3(cos(phase * 0.73), 0.61, sin(phase * 0.73)));
    vec3 axisB = normalize(vec3(-0.47, sin(phase * 0.51) * 0.42 + 0.74, 0.53));
    float waveA = sin(dot(worldN, axisA) * 9.0 + phase * 1.7);
    float waveB = cos(dot(worldN, axisB) * 13.0 - phase * 1.13);
    float breakup = (waveA * 0.58 + waveB * 0.42) * warp;

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
