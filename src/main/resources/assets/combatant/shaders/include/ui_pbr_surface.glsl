/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#include <combatant:ui_pbr_math.glsl>

vec3 uiPbrLinear(vec3 c) { return combatantUiToLinear(c); }
vec3 uiPbrSrgb(vec3 c) { return combatantUiToSrgb(c); }

#ifdef COMBATANT_SCENE_LIT_MATERIAL
vec3 uiPbrScene(vec2 uv) {
    return uiPbrLinear(texture(u_SceneLight, clamp(uv, vec2(0.001), vec2(0.999))).rgb);
}

vec3 uiPbrEnvironment(vec3 n, vec2 tangent, float roughness, float anisotropy, float radius) {
    vec2 position = v_Local.xy / (abs(v_Local.z) > 1e-6 ? v_Local.z : 1.0);
    vec2 uv = vec2(position.x, uScreen.w - position.y) / max(uScreen.zw, vec2(1.0));
    vec2 pixel = 1.0 / max(uScreen.zw, vec2(1.0));
    uv += vec2(n.x, -n.y) * radius * pixel * (1.0 - roughness * 0.6);
    vec2 t = vec2(tangent.x, -tangent.y) * radius * pixel;
    vec2 b = vec2(-tangent.y, -tangent.x) * radius * pixel;
    float spread = mix(0.12, 1.0, roughness);
    t *= spread * (1.0 - anisotropy * 0.75);
    b *= spread;
    return uiPbrScene(uv) * 0.28
            + (uiPbrScene(uv + t) + uiPbrScene(uv - t)) * 0.18
            + (uiPbrScene(uv + b) + uiPbrScene(uv - b)) * 0.18;
}
#endif

vec3 uiPbrSurface(vec2 local, vec2 bevelNormal, float edge, int mode) {
    float angle = radians(v_Params10.x);
    vec2 direction = vec2(cos(angle), sin(angle));
    mat2 rotation = mat2(direction, vec2(-direction.y, direction.x));
    // HUD microtextures are screen-anchored: a widget's entrance/exit animation
    // reveals the field instead of dragging or rescaling the micrograin.
    // uScreen.zw = framebuffer pixels; uScreen.xy = GUI logical viewport.
    vec2 framebufferPerLogical = max(uScreen.zw / max(uScreen.xy, vec2(1.0)), vec2(1e-6));
    vec2 stableUiPosition = v_Local.xy / (abs(v_Local.z) > 1e-6 ? v_Local.z : 1.0);
    vec2 uv = transpose(rotation) * (stableUiPosition * framebufferPerLogical)
            / max(v_Params8.x, 16.0);
    vec2 dx = dFdx(uv), dy = dFdy(uv);
    vec4 surface = textureGrad(u_MaterialMicro, uv, dx, dy);
    vec4 sourceColor = textureGrad(u_MaterialColor, uv, dx, dy);
    vec3 albedo = mix(uiPbrLinear(sourceColor.rgb), uiPbrLinear(v_Color.rgb), v_Params9.z);
    float metallic = sourceColor.a * v_Params9.x;
    float specular = v_Params9.y;
    float anisotropy = (mode == 1 || mode == 6) ? v_Params9.w : 0.0;
    float meanRoughness = mode == 4 ? 0.6492 : mode == 2 ? 0.4534 : mode == 1 ? 0.4081 : mode == 6 ? 0.2648 : 0.0651;
    float roughness = clamp(v_Params6.z + (surface.b - meanRoughness) * v_Params8.w, 0.045, 1.0);

    vec2 xy = surface.rg * 2.0 - 1.0;
    float z = sqrt(max(1.0 - dot(xy, xy), 0.001));
    // OpenGL tangent +Y is up; UI-local +Y and image row coordinates are down.
    xy.y = -xy.y;
    vec2 texel = 1.0 / vec2(textureSize(u_MaterialMicro, 0));
    float hx = textureGrad(u_MaterialMicro, uv + vec2(texel.x, 0.0), dx, dy).a
            - textureGrad(u_MaterialMicro, uv - vec2(texel.x, 0.0), dx, dy).a;
    float hy = textureGrad(u_MaterialMicro, uv + vec2(0.0, texel.y), dx, dy).a
            - textureGrad(u_MaterialMicro, uv - vec2(0.0, texel.y), dx, dy).a;
    xy = rotation * (xy * v_Params8.y - vec2(hx, hy) * v_Params8.z * 4.0);
    vec3 n = normalize(vec3(xy + bevelNormal * edge * v_Params7.w * 0.55, z));
    vec3 tangent = normalize(vec3(direction, 0.0) - n * dot(n.xy, direction));
    vec3 bitangent = normalize(cross(n, tangent));
    vec2 surfaceUv = local / max(v_Rect.zw, vec2(1.0));
    vec2 pointerUv = (uGlassPointer.zw - v_Rect.xy) / max(v_Rect.zw, vec2(1.0));
    vec2 lightPosition = mix(vec2(0.22, -0.25), pointerUv, v_Params6.w);
    vec3 light = normalize(vec3((lightPosition - surfaceUv) * vec2(0.85, 1.0), 0.85));
    vec3 view = vec3(0.0, 0.0, 1.0);
    vec3 halfVector = normalize(light + view);
    float nl = max(dot(n, light), 0.0), nv = max(n.z, 0.001);
    float vh = max(dot(view, halfVector), 0.0);
    float alpha = max(roughness * roughness, 0.015);
    float aspect = sqrt(1.0 - anisotropy * 0.9);
    float alphaX = max(alpha * aspect, 0.012);
    float alphaY = max(alpha / aspect, 0.012);
    float distribution = combatantUiGgxDistribution(n, halfVector, tangent, bitangent,
            alphaX, alphaY);
    float visibility = combatantUiSmithVisibility(n, view, light, tangent, bitangent,
            alphaX, alphaY);
    vec3 f0 = mix(vec3(mode == 2 ? 0.025 : 0.04), albedo, metallic);
    vec3 fresnel = combatantUiFresnel(f0, vh);
    // Direct BRDF: D * G / (4 NdotL NdotV) times NdotL is D * V * NdotL.
    vec3 reflected = fresnel * distribution * visibility * nl;
    // Burley diffuse retroreflection, with a stable UI ambient floor.
    float fd90 = 0.5 + 2.0 * roughness * vh * vh;
    float fdL = 1.0 + (fd90 - 1.0) * pow(1.0 - nl, 5.0);
    float fdV = 1.0 + (fd90 - 1.0) * pow(1.0 - nv, 5.0);
    float diffuse = (0.32 + 0.68 * nl) * fdL * fdV;
    if (mode == 4) {
        // Rough graphite/powder absorbs and diffuses light more strongly.
        diffuse *= 1.0 - 0.3 * roughness;
        reflected *= 0.45;
    } else if (mode == 2) {
        // Soft-touch: broad grazing response and reduced lobe energy.
        diffuse *= 0.94;
        reflected *= 0.58;
    } else if (mode == 5) {
        // Thin ceramic clear coat, evaluated in the same linear-light domain.
        float clearRough = max(0.09, roughness * 0.55);
        float clearAlpha = clearRough * clearRough;
        float clearD = combatantUiGgxDistribution(n, halfVector, tangent, bitangent,
                clearAlpha, clearAlpha);
        float clearV = combatantUiSmithVisibility(n, view, light, tangent, bitangent,
                clearAlpha, clearAlpha);
        reflected += vec3(0.04 * clearD * clearV * nl * 0.25);
    }
    vec3 color = albedo * (1.0 - metallic) * diffuse * (vec3(1.0) - f0)
            + reflected * specular + albedo * metallic * 0.28;
#ifdef COMBATANT_SCENE_LIT_MATERIAL
    float strength = clamp(v_Params5.z, 0.0, 1.0);
    vec3 environment = uiPbrEnvironment(n, direction, roughness, anisotropy, v_Params5.w);
    vec3 environmentFresnel = f0 + (1.0 - f0) * pow(1.0 - nv, 5.0);
    color += strength * environment * (albedo * (1.0 - metallic) * 0.35
            + environmentFresnel * specular * mix(0.95, 0.35, roughness));
#endif
    color *= 1.0 - edge * (1.0 - nl) * v_Params7.w * 0.16;
    // Do not apply an artistic shoulder to the base paint: it alters chosen RGB values.
    // Clamp only at the LDR framebuffer boundary; the intermediate remains linear.
    return uiPbrSrgb(clamp(color, vec3(0.0), vec3(1.0)));
}
