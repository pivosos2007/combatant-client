#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec2 v_TexCoord;
out vec4 color;

uniform sampler2D u_GbufferSurface;
uniform sampler2D u_GbufferGeometry;
uniform sampler2D u_GbufferMaterial;
uniform sampler2D u_GbufferDepth;
uniform sampler2D u_EnvironmentIrradiance;
uniform sampler2D u_ResolvedDepth;
uniform sampler2D u_ShadowVisibility;
uniform sampler2D u_CloudShadowVisibility;
uniform sampler2D u_AmbientVisibility;

layout(std140) uniform DeferredLighting {
    mat4 u_InverseProjection;
    vec4 u_FogColor;
    vec4 u_FogRanges;
    vec4 u_DirectionalDirection;
    vec4 u_DirectionalRadiance;
    vec4 u_DepthAndFlags;
    vec4 u_CloudShadowFlags;
};

const float PI = 3.14159265358979323846;

vec3 combatant_decode_octahedral(vec2 encoded) {
    vec2 f = encoded * 2.0 - 1.0;
    vec3 n = vec3(f, 1.0 - abs(f.x) - abs(f.y));
    if (n.z < 0.0) n.xy = (1.0 - abs(n.yx)) * vec2(n.x >= 0.0 ? 1.0 : -1.0, n.y >= 0.0 ? 1.0 : -1.0);
    return normalize(n);
}

vec3 combatant_reconstruct_view(vec2 uv, float depth) {
    vec2 ndcXY = uv * 2.0 - 1.0;
    float ndcZ = depth * u_DepthAndFlags.x + u_DepthAndFlags.y;
    vec4 h = u_InverseProjection * vec4(ndcXY, ndcZ, 1.0);
    if (abs(h.w) < 1e-7) return vec3(0.0, 0.0, -1.0);
    return h.xyz / h.w;
}

bool combatant_owns_gbuffer_pixel(vec2 uv) {
    float gbufferDepth = texture(u_GbufferDepth, uv).r;
    float currentDepth = texture(u_ResolvedDepth, uv).r;
    if (gbufferDepth <= 0.0 || currentDepth <= 0.0) return false;
    float tolerance = max(1.0e-6, abs(gbufferDepth) * 1.0e-5);
    return abs(gbufferDepth - currentDepth) <= tolerance;
}

float combatant_ggx_distribution(float ndoth, float roughness) {
    float a = max(roughness * roughness, 1e-4);
    float a2 = a * a;
    float d = ndoth * ndoth * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1e-6);
}

float combatant_smith_ggx_correlated(float ndotv, float ndotl, float roughness) {
    float a = max(roughness * roughness, 1e-4);
    float a2 = a * a;
    float gv = ndotl * sqrt(max(ndotv * ndotv * (1.0 - a2) + a2, 1e-6));
    float gl = ndotv * sqrt(max(ndotl * ndotl * (1.0 - a2) + a2, 1e-6));
    return 0.5 / max(gv + gl, 1e-6);
}

vec3 combatant_fresnel_schlick(float vdoth, vec3 f0) {
    float factor = pow(clamp(1.0 - vdoth, 0.0, 1.0), 5.0);
    return f0 + (vec3(1.0) - f0) * factor;
}

float combatant_burley_diffuse(float ndotv, float ndotl, float ldoth, float roughness) {
    float fd90 = 0.5 + 2.0 * roughness * ldoth * ldoth;
    float lightScatter = 1.0 + (fd90 - 1.0) * pow(1.0 - ndotl, 5.0);
    float viewScatter = 1.0 + (fd90 - 1.0) * pow(1.0 - ndotv, 5.0);
    return lightScatter * viewScatter / PI;
}

float combatant_lift_ao(float value) {
    float lifted = (1.5 * value) / max(1.0 + 0.5 * value, 1e-5);
    return mix(1.0, lifted, 0.9);
}

void main() {
    if (!combatant_owns_gbuffer_pixel(v_TexCoord)) discard;

    vec4 surface = texture(u_GbufferSurface, v_TexCoord);
    vec4 geometry = texture(u_GbufferGeometry, v_TexCoord);
    vec4 material = texture(u_GbufferMaterial, v_TexCoord);

    vec3 albedo = max(surface.rgb, vec3(0.0));
    float materialAo = clamp(surface.a, 0.0, 1.0);
    if (u_DepthAndFlags.w > 0.5) materialAo = combatant_lift_ao(materialAo);
    float roughness = clamp(material.r, 0.045, 1.0);
    float metallic = clamp(material.g, 0.0, 1.0);
    float dielectricF0 = clamp(material.b, 0.0, 1.0);
    float emission = max(material.a, 0.0);

    vec3 normal = combatant_decode_octahedral(geometry.rg);
    float depth = texture(u_ResolvedDepth, v_TexCoord).r;
    vec3 viewPosition = combatant_reconstruct_view(v_TexCoord, depth);
    vec3 viewDirection = normalize(-viewPosition);

    vec3 f0 = mix(vec3(dielectricF0), albedo, metallic);
    vec3 environmentIrradiance = max(texture(u_EnvironmentIrradiance, v_TexCoord).rgb, vec3(0.0));
    float ambientVisibility = u_DepthAndFlags.w > 0.5
            ? clamp(texture(u_AmbientVisibility, v_TexCoord).r, 0.0, 1.0)
            : 1.0;

    // Incoming environment irradiance is an explicit renderer contract. Vanilla lightmap data is
    // neither sampled nor inferred here.
    vec3 ambientDiffuseWeight = (vec3(1.0) - f0) * (1.0 - metallic);
    vec3 litColor = albedo * ambientDiffuseWeight
            * environmentIrradiance * materialAo * ambientVisibility;

    if (u_DirectionalDirection.w > 0.5) {
        vec3 lightDirection = normalize(u_DirectionalDirection.xyz);
        vec3 halfVector = normalize(viewDirection + lightDirection);
        float ndotv = max(dot(normal, viewDirection), 0.0);
        float ndotl = max(dot(normal, lightDirection), 0.0);
        float ndoth = max(dot(normal, halfVector), 0.0);
        float vdoth = max(dot(viewDirection, halfVector), 0.0);

        if (ndotv > 0.0 && ndotl > 0.0) {
            vec3 fresnel = combatant_fresnel_schlick(vdoth, f0);
            float distribution = combatant_ggx_distribution(ndoth, roughness);
            float visibility = combatant_smith_ggx_correlated(ndotv, ndotl, roughness);
            vec3 specularBrdf = fresnel * distribution * visibility;
            float ldoth = max(dot(lightDirection, halfVector), 0.0);
            float diffuseTerm = combatant_burley_diffuse(ndotv, ndotl, ldoth, roughness);
            vec3 diffuseBrdf = (vec3(1.0) - fresnel) * (1.0 - metallic) * albedo * diffuseTerm;
            float shadowVisibility = u_DepthAndFlags.z > 0.5
                    ? clamp(texture(u_ShadowVisibility, v_TexCoord).r, 0.0, 1.0)
                    : 1.0;
            float cloudShadowVisibility = u_CloudShadowFlags.x > 0.5
                    ? clamp(texture(u_CloudShadowVisibility, v_TexCoord).r, 0.0, 1.0)
                    : 1.0;
            litColor += (diffuseBrdf + specularBrdf)
                    * max(u_DirectionalRadiance.rgb, vec3(0.0))
                    * ndotl * shadowVisibility * cloudShadowVisibility;
        }
    }

    litColor += albedo * emission;
    color = vec4(litColor, 1.0);
}
