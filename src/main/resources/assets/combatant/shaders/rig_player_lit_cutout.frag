#version 330 core
#extension GL_ARB_separate_shader_objects : require

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

#ifdef COMBATANT_RIG_SCENE_LINEAR
#include "include/scene_color.glsl"
#endif

layout(location = 0) out vec4 color;
uniform sampler2D u_Texture;
uniform sampler2D u_Lightmap;
layout(std140) uniform RigPlayerLighting {
    vec4 u_LightCoord; // xy: lightmap, z: opacity, w: damage intensity
    vec4 u_EntityTint; // rgb: vanilla/model tint
};
layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};
layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;
layout(location = 4) in vec3 v_Normal;

void main() {
    vec4 albedo = texture(u_Texture, v_TexCoord) * v_Color;
    if (albedo.a < 0.1) discard;
    float opacity = clamp(u_LightCoord.z, 0.0, 1.0);
    if (opacity < 0.999) {
        vec2 pixel = floor(gl_FragCoord.xy);
        float threshold = fract(52.9829189 * fract(dot(pixel,
                vec2(0.06711056, 0.00583715))));
        if (albedo.a * opacity <= threshold) discard;
    }
    // Actual Minecraft 16x16 level lightmap: sky/block light, gamma, night vision
    // and modded lightmap overrides are all evaluated by Minecraft, not guessed here.
    vec3 worldLight = texture(u_Lightmap, clamp(u_LightCoord.xy,
            vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
    vec3 normal = normalize(v_Normal);
    // Minecraft entity directional light, not a hardcoded light direction.
    float directional = min(1.0, 0.4 + 0.6 *
            (max(0.0, dot(normal, Light0_Direction)) +
             max(0.0, dot(normal, Light1_Direction))));
    vec3 lit = albedo.rgb * worldLight * directional * u_EntityTint.rgb;
    // Smooth damage coloration without switching to vanilla's flashing overlay.
    vec3 hurt = min(vec3(1.0), lit * vec3(1.2, 0.35, 0.35)
            + worldLight * vec3(0.08, 0.0, 0.0));
    vec3 shaded = mix(lit, hurt, clamp(u_LightCoord.w, 0.0, 1.0));
#ifdef COMBATANT_RIG_SCENE_LINEAR
    shaded = combatantLegacyToSceneLinear(shaded);
#endif
    color = vec4(shaded, 1.0);
}
