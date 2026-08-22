#version 330 core

/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 * Material and layout direction: pivosos2007.
 */

out vec4 color;
in vec2 v_TexCoord;
uniform sampler2D u_Texture;

layout (std140) uniform UIBatch {
    vec4 uScreen;
};

void main() {
    vec2 uv = vec2(v_TexCoord.x, 1.0 - v_TexCoord.y);
    vec2 textureDimensions = vec2(textureSize(u_Texture, 0));
    float screenAspect = uScreen.x / max(uScreen.y, 1.0);
    float textureAspect = textureDimensions.x / max(textureDimensions.y, 1.0);

    if (screenAspect > textureAspect) {
        float visibleHeight = textureAspect / screenAspect;
        uv.y = (uv.y - 0.5) * visibleHeight + 0.5;
    } else {
        float visibleWidth = screenAspect / textureAspect;
        uv.x = (uv.x - 0.5) * visibleWidth + 0.5;
    }

    color = vec4(texture(u_Texture, uv).rgb, 1.0);
}
