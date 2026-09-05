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
uniform sampler2D u_PreviousTexture;
uniform sampler2D u_Texture;

layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};

layout (std140) uniform MenuTextureTransition {
    vec4 uTransition; // x = eased blend from previous to current texture
};

vec2 coverUv(vec2 baseUv, vec2 textureDimensions) {
    vec2 uv = vec2(baseUv.x, 1.0 - baseUv.y);
    float screenAspect = uScreen.x / max(uScreen.y, 1.0);
    float textureAspect = textureDimensions.x / max(textureDimensions.y, 1.0);

    if (screenAspect > textureAspect) {
        float visibleHeight = textureAspect / screenAspect;
        uv.y = (uv.y - 0.5) * visibleHeight + 0.5;
    } else {
        float visibleWidth = screenAspect / textureAspect;
        uv.x = (uv.x - 0.5) * visibleWidth + 0.5;
    }
    return uv;
}

void main() {
    float blend = clamp(uTransition.x, 0.0, 1.0);
    vec2 previousUv = coverUv(v_TexCoord, vec2(textureSize(u_PreviousTexture, 0)));
    vec2 currentUv = coverUv(v_TexCoord, vec2(textureSize(u_Texture, 0)));
    vec3 previous = texture(u_PreviousTexture, previousUv).rgb;
    vec3 current = texture(u_Texture, currentUv).rgb;
    color = vec4(mix(previous, current, blend), 1.0);
}
