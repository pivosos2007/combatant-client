#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on, adapted from, or implemented
 * with reference to Meteor Client
 * (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 *
 * Licensed under the GNU General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

out vec4 color;

uniform sampler2D u_Texture;

in vec2 v_TexCoord;
in vec4 v_Color;

void main() {
    color = texture(u_Texture, v_TexCoord) * v_Color;
}
