#version 330 core
#extension GL_ARB_separate_shader_objects : require

// Grayscale glyph masks are rasterized at 4x the selected physical font size.
// The 4x4 quadrature reconstructs one framebuffer pixel in physical-pixel
// coordinates. Unlike a fixed four-tap atlas-space filter, it works at
// fractional/anisotropic UI scales and is stable during subpixel movement.
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
    // Each tap is centered on one of the 4x4 subareas of a *screen* pixel.
    // At the nominal 4x resolution the offsets are +/-0.5 and +/-1.5 atlas
    // texels. This integrates bilinear coverage over the pixel more precisely
    // than the old four fixed taps at +/-1 atlas texel; at arbitrary scales
    // the derivative vectors automatically resize and orient the footprint.
    vec2 pixelDx = dFdx(v_TexCoord);
    vec2 pixelDy = dFdy(v_TexCoord);
    float coverage = 0.0;
    for (int sx = 0; sx < 4; ++sx) {
        float subX = (float(sx) + 0.5) * 0.25 - 0.5;
        for (int sy = 0; sy < 4; ++sy) {
            float subY = (float(sy) + 0.5) * 0.25 - 0.5;
            coverage += texture(u_Texture, v_TexCoord + subX * pixelDx + subY * pixelDy).r;
        }
    }
    coverage *= 1.0 / 16.0;
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
