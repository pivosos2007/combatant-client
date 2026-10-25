#version 330 core
#extension GL_ARB_separate_shader_objects : require

// The source is a grayscale mask at 4x the physical screen-pixel resolution.
// Its texels are NOT pre-filtered to 1x. Four bilinear samples integrate all
// 16 high-resolution texels in one destination pixel without positional snaps.
layout(location = 0) in vec2 v_TexCoord;
layout(location = 2) in vec4 v_Color;
layout(location = 0) out vec4 color;
uniform sampler2D u_Texture;

#ifdef COMBATANT_ANALYTIC_CLIP
layout (std140) uniform UIBatch {
    vec4 uScreen;
    vec4 uLayer;
};
#include <combatant:ui_geometry.glsl>
#include <combatant:ui_clip.glsl>
#endif

void main() {
    vec2 oneTexel = 1.0 / vec2(textureSize(u_Texture, 0));
    vec2 offsetX = vec2(oneTexel.x, 0.0);
    vec2 offsetY = vec2(0.0, oneTexel.y);
    // Each LINEAR sample averages a 2x2 group of high-resolution texels.
    // Four sample footprints together span the full 4x4 source pixel area.
    float coverage = (texture(u_Texture, v_TexCoord - offsetX - offsetY).r
                    + texture(u_Texture, v_TexCoord + offsetX - offsetY).r
                    + texture(u_Texture, v_TexCoord - offsetX + offsetY).r
                    + texture(u_Texture, v_TexCoord + offsetX + offsetY).r) * 0.25;
    float alpha = coverage * v_Color.a;
#ifdef COMBATANT_ANALYTIC_CLIP
    vec2 logicalScale = max(uScreen.zw, vec2(1.0)) / max(uScreen.xy, vec2(1.0));
    alpha *= combatantClipCoverage(
        combatantClipDistance(combatantLogicalFragCoord()), logicalScale
    );
    if (alpha <= 0.001) discard;
#endif
    color = vec4(v_Color.rgb, alpha);
}
