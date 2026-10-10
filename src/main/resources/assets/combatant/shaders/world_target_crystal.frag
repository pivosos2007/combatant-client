#version 330 core

in vec2 v_TexCoord;
in vec4 v_Color;
in vec4 v_Params;
in vec4 v_Params2;
in vec3 v_ViewPosition;
in vec3 v_ViewNormal;
out vec4 color;

const float TAU = 6.283185307179586;
float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec3 normal = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    float ndv = sat(abs(dot(normal, viewDir)));
    float fresnel = pow(1.0 - ndv, 2.35);

    float time = v_Params.x;
    float index = v_Params.y;
    float hit = sat(v_Params.z);
    float facet = fract(v_Params2.w);
    vec2 uv = v_TexCoord;

    // Facet planes are the material language. Keep them broad and coherent so the shard reads as a
    // solid prism rather than a transparent polygon with random sparkles.
    float facetPhase = facet * TAU * 2.0 + index * 0.47;
    float faceLight = 0.5 + 0.5 * dot(normal, normalize(vec3(0.37, 0.78, 0.50)));
    float facetTone = 0.5 + 0.5 * sin(facetPhase + normal.x * 2.1 - normal.z * 1.7 + time * 0.32);

    float diag0 = 1.0 - smoothstep(0.025, 0.135, abs(uv.y - uv.x));
    float diag1 = 1.0 - smoothstep(0.025, 0.135, abs(uv.y - (1.0 - uv.x)));
    float spine = max(diag0, diag1);
    float edgeCarrier = smoothstep(0.28, 0.92, fresnel);
    float internalFacet = spine * (0.24 + edgeCarrier * 0.76);

    // Slow moving internal planes create thickness without turning the surface into noisy glass.
    float internalA = 0.5 + 0.5 * sin(uv.x * 8.0 + uv.y * 3.0 + facetPhase - time * 0.74);
    float internalB = 0.5 + 0.5 * sin(uv.x * -4.0 + uv.y * 9.0 - facetPhase * 0.7 + time * 0.53);
    float internalBand = smoothstep(0.66, 0.94, internalA * 0.58 + internalB * 0.42);

    vec3 base = max(v_Color.rgb, vec3(0.001));
    vec3 body = mix(base * 0.42, base * 0.88, faceLight * 0.72 + facetTone * 0.28);
    vec3 cold = mix(base, vec3(0.55, 0.78, 1.0), 0.34);
    vec3 hot = mix(base, vec3(1.0), 0.74);

    vec3 spectral = vec3(
        0.5 + 0.5 * sin(facetPhase + fresnel * 1.7),
        0.5 + 0.5 * sin(facetPhase + fresnel * 1.7 + 2.0943951),
        0.5 + 0.5 * sin(facetPhase + fresnel * 1.7 + 4.1887902)
    );
    spectral = mix(cold, spectral, 0.18 + fresnel * 0.22);

    vec3 outColor = body * (0.68 + ndv * 0.22);
    outColor += spectral * fresnel * 0.46;
    outColor += hot * internalFacet * 0.24;
    outColor += cold * internalBand * (0.10 + fresnel * 0.12);
    outColor += hot * pow(fresnel, 5.0) * 0.42;
    outColor = mix(outColor, vec3(1.0, 0.075, 0.055) * (0.48 + outColor * 0.52), hit * 0.52);
    outColor = outColor / (1.0 + outColor * 0.08);

    // Dense enough to read as a physical shard while still leaving room for the dedicated scene
    // refraction pass to sell transmission through the body.
    float alpha = v_Color.a * (
        0.36
        + ndv * 0.12
        + fresnel * 0.34
        + internalFacet * 0.10
        + internalBand * 0.055
    );
    if (alpha <= 0.003) discard;
    color = vec4(outColor, sat(alpha));
}
