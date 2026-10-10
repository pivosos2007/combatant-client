#version 330 core

uniform sampler2D u_Texture;
uniform sampler2D u_Depth;

layout (std140) uniform JumpShockwave {
    mat4 u_InvViewProj;
    vec4 u_CenterRadius;   // xyz camera-relative center, w outer lens radius
    vec4 u_Params;         // x horizon radius, y strength/fade, z depth-test
    vec4 u_RingColor;      // rgb tint, a phase [0,1]
    vec4 u_DepthTransform;
};

in vec2 v_TexCoord;
out vec4 color;

const float TAU = 6.283185307179586;
float sat(float x) { return clamp(x, 0.0, 1.0); }

void main() {
    vec2 uv = v_TexCoord;
    vec3 base = texture(u_Texture, uv).rgb;
    float outerRadius = u_CenterRadius.w;
    float horizonRadius = max(u_Params.x, 1.0e-4);
    float strength = max(u_Params.y, 0.0);
    float fade = sat(strength);
    if (outerRadius <= horizonRadius || strength <= 1.0e-4) {
        color = vec4(base, 1.0);
        return;
    }

    vec2 ndc = uv * 2.0 - 1.0;
    vec4 farWorld = u_InvViewProj * vec4(ndc, u_DepthTransform.z, 1.0);
    if (abs(farWorld.w) < 1.0e-6) {
        color = vec4(base, 1.0);
        return;
    }
    farWorld.xyz /= farWorld.w;
    vec3 rayDir = normalize(farWorld.xyz);

    float alongRay = dot(rayDir, u_CenterRadius.xyz);
    if (alongRay <= 0.0) {
        color = vec4(base, 1.0);
        return;
    }

    float centerSq = dot(u_CenterRadius.xyz, u_CenterRadius.xyz);
    float perpendicular = sqrt(max(0.0, centerSq - alongRay * alongRay));
    if (perpendicular >= outerRadius * 1.02) {
        color = vec4(base, 1.0);
        return;
    }

    float outerInside = max(0.0, outerRadius * outerRadius - perpendicular * perpendicular);
    float outerHitDistance = alongRay - sqrt(outerInside);
    if (outerHitDistance <= 0.0) outerHitDistance = alongRay;

    if (u_Params.z > 0.5) {
        float rawDepth = texture(u_Depth, uv).r;
        if (rawDepth > 1.0e-6) {
            float depthNdc = rawDepth * u_DepthTransform.x + u_DepthTransform.y;
            vec4 sceneWorld = u_InvViewProj * vec4(ndc, depthNdc, 1.0);
            if (abs(sceneWorld.w) > 1.0e-6) {
                sceneWorld.xyz /= sceneWorld.w;
                float sceneDistance = length(sceneWorld.xyz);
                if (outerHitDistance > sceneDistance + max(0.035, sceneDistance * 0.004)) {
                    color = vec4(base, 1.0);
                    return;
                }
            }
        }
    }

    float coreT = perpendicular / horizonRadius;
    float lensT = sat((perpendicular - horizonRadius) / max(outerRadius - horizonRadius, 1.0e-4));

    // A continuous event-horizon SDF. At full target alpha the inner region is mathematically black,
    // but the edge and the whole object can fade out cleanly with TargetESP alpha.
    float coreAbsorb = 1.0 - smoothstep(0.90, 1.045, coreT);
    float edgeAbsorb = 1.0 - smoothstep(1.00, 1.18, coreT);
    if (coreT < 0.82) {
        color = vec4(mix(base, vec3(0.0), fade), 1.0);
        return;
    }

    vec2 gradient = vec2(dFdx(perpendicular), dFdy(perpendicular));
    float gl = length(gradient);
    if (gl < 1.0e-7) {
        color = vec4(mix(base, vec3(0.0), coreAbsorb * fade), 1.0);
        return;
    }
    vec2 radial = gradient / gl;
    vec2 tangent = vec2(-radial.y, radial.x);

    float phase = u_RingColor.a * TAU;
    float nearHorizon = exp(-max(coreT - 1.0, 0.0) * 4.2);
    float field = pow(1.0 - lensT, 2.05) * strength;
    float shear = sin(coreT * 8.0 - phase * 1.35) * 0.065 * field;

    float deflection = (0.0052 + nearHorizon * 0.030) * field;
    vec2 offset = radial * deflection + tangent * (deflection * shear);

    vec2 uvMain = clamp(uv + offset, vec2(0.0), vec2(1.0));
    vec2 uvEcho = clamp(uv - offset * (0.44 + nearHorizon * 0.64), vec2(0.0), vec2(1.0));

    // Analytic ring: no quad/vertex interpolation seams. The full gradient comes from the same
    // screen-space radial field that owns the event horizon.
    float photon = exp(-pow((coreT - 1.095) / 0.078, 2.0));
    float photonOuter = exp(-pow((coreT - 1.23) / 0.15, 2.0));
    float chroma = (0.00065 + 0.0026 * photon) * strength;

    vec3 refracted = vec3(
            texture(u_Texture, clamp(uvMain + radial * chroma, vec2(0.0), vec2(1.0))).r,
            texture(u_Texture, uvMain).g,
            texture(u_Texture, clamp(uvMain - radial * chroma, vec2(0.0), vec2(1.0))).b
    );
    vec3 echo = texture(u_Texture, uvEcho).rgb;
    refracted = mix(refracted, echo, nearHorizon * 0.31);

    if (nearHorizon > 0.03) {
        vec3 gather = refracted;
        gather += texture(u_Texture, clamp(uvMain + tangent * deflection * 0.31, vec2(0.0), vec2(1.0))).rgb;
        gather += texture(u_Texture, clamp(uvMain - tangent * deflection * 0.31, vec2(0.0), vec2(1.0))).rgb;
        refracted = mix(refracted, gather / 3.0, nearHorizon * 0.36);
    }

    vec3 tint = max(u_RingColor.rgb, vec3(0.001));
    vec3 photonColor = mix(tint, vec3(1.0), 0.74);
    float doppler = 0.5 + 0.5 * sin(atan(radial.y, radial.x) * 2.0 - phase * 1.8);
    photonColor *= mix(vec3(0.72, 0.84, 1.0), vec3(1.0, 0.70, 0.38), doppler);

    vec3 result = refracted;
    result += photonColor * photon * (0.16 + 0.24 * strength);
    result += tint * photonOuter * 0.028 * strength;

    // The dark shoulder and core use the exact same radial SDF as the optical ring, so the four
    // vertices of the old mesh can no longer disagree about where the gradient lives.
    float darkShoulder = exp(-pow((coreT - 1.025) / 0.095, 2.0));
    result *= 1.0 - darkShoulder * 0.58 * fade;
    result = mix(result, vec3(0.0), sat(coreAbsorb * fade));
    result *= 1.0 - edgeAbsorb * 0.18 * fade;

    float opticalBlend = sat(field * (0.50 + nearHorizon * 0.50));
    float totalBlend = max(opticalBlend, coreAbsorb * fade);
    color = vec4(mix(base, result, sat(totalBlend)), 1.0);
}
