#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

in vec4 v_Color;
in vec3 v_ViewNormal;
in vec3 v_ViewPosition;
in vec2 v_AgePhase;
in float v_Membrane;

out vec4 fragColor;

void main() {
    vec3 normal = normalize(v_ViewNormal);
    vec3 viewDir = normalize(-v_ViewPosition);
    vec3 lightDir = normalize(vec3(-0.34, 0.78, 0.52));
    vec3 halfDir = normalize(lightDir + viewDir);

    float facing = clamp(dot(normal, viewDir), 0.0, 1.0);
    float fresnel = pow(1.0 - facing, 1.72);
    float diffuse = clamp(dot(normal, lightDir) * 0.5 + 0.5, 0.0, 1.0);
    float specular = pow(max(dot(normal, halfDir), 0.0), 72.0);

    // Soap-film colour movement. It is intentionally subtle and phase-shifted per particle so a
    // group of bubbles does not look like a synchronized animated texture.
    float filmPhase = (1.0 - facing) * 17.0
            + diffuse * 2.4
            + v_AgePhase.x * 1.15
            + v_AgePhase.y * 1.73
            + v_Membrane * 28.0;
    float film = 0.5 + 0.5 * sin(filmPhase);
    vec3 filmCold = vec3(0.62, 0.88, 1.00);
    vec3 filmWarm = vec3(1.00, 0.70, 0.92);
    vec3 filmColor = mix(filmCold, filmWarm, film);

    vec3 base = v_Color.rgb;
    vec3 surface = mix(base, filmColor, 0.10 + fresnel * 0.20);
    surface = mix(surface, vec3(1.0), specular * 0.78);
    surface *= 0.82 + diffuse * 0.22;

    float alpha = v_Color.a * (0.055 + fresnel * 0.58 + specular * 0.46);
    alpha *= 0.94 + film * fresnel * 0.12;
    alpha = clamp(alpha, 0.0, 0.86);

    if (alpha <= 0.003) discard;
    fragColor = vec4(surface, alpha);
}
