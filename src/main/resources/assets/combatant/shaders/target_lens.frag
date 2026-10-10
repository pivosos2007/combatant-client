#version 330 core

uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

layout (std140) uniform TargetLens {
    vec4 u_Params;
    vec4 u_Lenses[18];
    vec4 u_LensColors[18];
    vec4 u_LensShapes[18];
    vec4 u_LensMeta[18];
};

in vec2 v_TexCoord;
out vec4 color;

float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec2 uv = v_TexCoord;
    float sceneDepth = texture(u_Depth, uv).r;
    int count = int(u_Params.x + 0.5);
    float aspect = u_Params.y;
    bool depthAvailable = u_Params.w > 0.5;

    vec2 offsetR = vec2(0.0);
    vec2 offsetG = vec2(0.0);
    vec2 offsetB = vec2(0.0);
    vec2 blurAxis = vec2(0.0);
    vec3 prismGlow = vec3(0.0);
    float totalWeight = 0.0;
    float bodyWeight = 0.0;
    float facetWeight = 0.0;

    for (int i = 0; i < 18; i++) {
        if (i >= count) break;
        vec4 lens = u_Lenses[i];
        vec4 shape = u_LensShapes[i];
        vec4 meta = u_LensMeta[i];
        float radius = abs(lens.w);
        float axialRadius = max(shape.z, radius * 1.15);
        if (radius <= 0.0 || axialRadius <= 0.0) continue;

        vec2 d = (uv - lens.xy) * vec2(aspect, 1.0);
        vec2 axis = shape.xy;
        float axisLen = length(axis);
        if (axisLen <= 1.0e-6) axis = vec2(1.0, 0.0);
        else axis /= axisLen;
        vec2 perp = vec2(-axis.y, axis.x);
        vec2 local = vec2(dot(d, axis) / axialRadius, dot(d, perp) / radius);
        vec2 a = abs(local);

        float planar = max(a.y * 0.92, a.x * 0.57);
        float diamond = a.y * 0.74 + pow(a.x, 0.86);
        float shapeDistance = max(planar, diamond * 0.88);
        float inside = 1.0 - smoothstep(0.90, 1.025, shapeDistance);
        if (inside <= 0.001) continue;

        float visibility = 1.0;
        if (lens.w > 0.0 && depthAvailable) {
            float depthDelta = sceneDepth - lens.z;
            visibility = 1.0 - smoothstep(-0.0006, 0.0028, depthDelta);
            if (visibility <= 0.001) continue;
        }

        vec3 tint = u_LensColors[i].rgb;
        float tintAlpha = u_LensColors[i].a;
        float phase = meta.y;
        float optical = clamp(meta.x * 7.25, 0.06, 2.45);
        float chromatic = clamp(meta.w, 0.0, 1.0);

        float edge = smoothstep(0.52, 0.96, shapeDistance) * inside;
        float core = pow(sat(1.0 - shapeDistance), 0.82) * inside;
        float shoulder = smoothstep(0.18, 0.68, core) * (1.0 - edge * 0.35);

        float facetA = abs(local.y - local.x * 0.48);
        float facetB = abs(local.y + local.x * 0.42);
        float facetC = abs(local.y * 0.42 + local.x * 0.92 - 0.18);
        float facetD = abs(local.y * 0.58 - local.x * 0.78 + 0.12);
        float facetBand = (1.0 - smoothstep(0.055, 0.18, min(min(facetA, facetB), min(facetC, facetD)))) * inside;

        float planeA = 0.5 + 0.5 * sin((local.x * 4.0 + local.y * 6.0 + phase * 2.7) * 3.14159265);
        float planeB = 0.5 + 0.5 * sin((local.x * -6.5 + local.y * 2.4 - phase * 4.1) * 3.14159265);
        float internalPlane = smoothstep(0.60, 0.95, planeA * 0.62 + planeB * 0.38) * inside;

        vec2 radialLocal = local / max(length(local), 1.0e-5);
        vec2 refractLocal = vec2(radialLocal.x * 0.52, radialLocal.y);
        refractLocal += vec2(sign(local.x), -sign(local.y)) * facetBand * 0.17;
        refractLocal += vec2(-sign(local.y), sign(local.x)) * internalPlane * 0.055;
        vec2 baseOffset = (axis * refractLocal.x + perp * refractLocal.y) / vec2(aspect, 1.0);

        float displacement = radius * optical * visibility
                * (core * 0.72 + shoulder * 0.28 + edge * 0.34 + facetBand * 0.32 + internalPlane * 0.15);
        float dispersion = radius * optical * visibility
                * (edge * 0.52 + facetBand * 0.42 + internalPlane * 0.16) * chromatic;
        offsetR += baseOffset * (displacement + dispersion * 0.90);
        offsetG += baseOffset * displacement;
        offsetB += baseOffset * (displacement - dispersion * 0.72);

        vec2 localBlur = (axis * 0.72 + perp * sign(local.y) * 0.28) / vec2(aspect, 1.0);
        blurAxis += localBlur * inside * visibility * optical * radius;

        vec3 spectralTint = mix(tint, vec3(tint.b, tint.r, tint.g), (0.24 + facetBand * 0.22) * chromatic);
        prismGlow += spectralTint * (edge * 0.14 + facetBand * 0.13 + internalPlane * 0.065)
                * (0.34 + tintAlpha * 0.66) * visibility * optical;
        totalWeight += inside * visibility * optical * (0.42 + core * 0.52 + edge * 0.30 + facetBand * 0.20);
        bodyWeight += core * visibility * optical;
        facetWeight += facetBand * visibility * optical;
    }

    vec3 base = texture(u_Texture, uv).rgb;
    if (totalWeight <= 1.0e-6) {
        color = vec4(base, 1.0);
        return;
    }

    float maxOffset = 0.092;
    float lenR = length(offsetR);
    float lenG = length(offsetG);
    float lenB = length(offsetB);
    if (lenR > maxOffset) offsetR *= maxOffset / lenR;
    if (lenG > maxOffset) offsetG *= maxOffset / lenG;
    if (lenB > maxOffset) offsetB *= maxOffset / lenB;

    vec2 uvR = clamp(uv + offsetR, vec2(0.0), vec2(1.0));
    vec2 uvG = clamp(uv + offsetG, vec2(0.0), vec2(1.0));
    vec2 uvB = clamp(uv + offsetB, vec2(0.0), vec2(1.0));
    vec3 refracted = vec3(texture(u_Texture, uvR).r, texture(u_Texture, uvG).g, texture(u_Texture, uvB).b);

    float blurLen = length(blurAxis);
    if (blurLen > 1.0e-6 && bodyWeight > 0.025) {
        vec2 dir = blurAxis / blurLen;
        float radius = min(0.0065, 0.0012 + bodyWeight * 0.00085);
        vec3 thick = refracted;
        thick += texture(u_Texture, clamp(uvG + dir * radius, vec2(0.0), vec2(1.0))).rgb;
        thick += texture(u_Texture, clamp(uvG - dir * radius, vec2(0.0), vec2(1.0))).rgb;
        thick += texture(u_Texture, clamp(uvG + dir * radius * 2.0, vec2(0.0), vec2(1.0))).rgb;
        thick += texture(u_Texture, clamp(uvG - dir * radius * 2.0, vec2(0.0), vec2(1.0))).rgb;
        thick *= 0.2;
        refracted = mix(refracted, thick, sat(bodyWeight * 0.24));
    }

    float mixWeight = sat(totalWeight * 1.02);
    vec3 outColor = mix(base, refracted, min(mixWeight, 0.96));
    outColor *= 1.0 - sat(bodyWeight * 0.035);
    outColor += prismGlow * min(0.30 + totalWeight * 0.22, 0.72);
    outColor += prismGlow * sat(facetWeight * 0.05);
    color = vec4(outColor, 1.0);
}
