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

package combatant.client.render.engine.text;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import org.lwjgl.system.MemoryUtil;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.ScissorFunction;

import java.nio.ByteBuffer;

public class CustomTextRenderer implements TextRenderer {
    public static final RenderColor SHADOW_COLOR = new RenderColor(60, 60, 60, 180);
    public final FontFace fontFace;
    private final MeshBuilder mesh = new MeshBuilder(CombatantRenderPipelines.UI_TEXT);
    private GlyphFont[] fonts;
    private GlyphFont font;
    private int fontIndex;

    private boolean building;
    private boolean scaleOnly;
    private boolean meshStarted;
    private boolean fastUiText;
    private double fontScale = 1;
    private double scale = 1;

    public CustomTextRenderer(FontFace fontFace) {
        this.fontFace = fontFace;

        this.fonts = createFonts();
    }

    private static double fadeAlphaAt(double x,
                                      double clipLeft,
                                      double clipRight,
                                      double fadeLeft,
                                      double fadeRight) {
        if (clipRight <= clipLeft) return 1.0;
        if (x <= clipLeft || x >= clipRight) return 0.0;

        double alpha = 1.0;
        if (fadeLeft > 0.0 && x < clipLeft + fadeLeft) {
            alpha = Math.min(alpha, (x - clipLeft) / fadeLeft);
        }
        if (fadeRight > 0.0 && x > clipRight - fadeRight) {
            alpha = Math.min(alpha, (clipRight - x) / fadeRight);
        }
        return clamp01(alpha);
    }

    private static int scaleArgbAlpha(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = (int) Math.round(a * clamp01(factor));
        return (argb & 0x00FFFFFF) | ((scaled & 0xFF) << 24);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private GlyphFont[] createFonts() {
        boolean msdfEligible = fontFace instanceof BuiltinFontFace
                && !FontUtils.isIconFamily(fontFace.info.family());
        if (msdfEligible) {
            FontDebugStats.noteMsdfAttempt(fontFace.info);
            GlyphFont[] msdf = MsdfFont.tryCreate(fontFace);
            if (msdf != null) {
                FontDebugStats.noteMsdfSuccess(fontFace.info);
                return msdf;
            }
            FontDebugStats.noteMsdfFallback(fontFace.info, MsdfFont.getLastError());
            if (fontFace.isAtlasOnly()) {
                return createEmptyFonts();
            }
        }

        byte[] bytes = FontUtils.readBytes(fontFace.toStream());
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
        try {
            buffer.put(bytes).flip();

            GlyphFont[] result = new GlyphFont[5];
            for (int i = 0; i < result.length; i++) {
                result[i] = new Font(buffer, (int) Math.round(27 * ((i * 0.5) + 1)));
            }
            return result;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private GlyphFont[] createEmptyFonts() {
        GlyphFont[] result = new GlyphFont[5];
        for (int i = 0; i < result.length; i++) {
            int height = (int) Math.round(27 * ((i * 0.5) + 1));
            result[i] = new EmptyGlyphFont(height);
        }
        return result;
    }

    private boolean rebuildFonts() {
        GlyphFont[] old = this.fonts;
        GlyphFont[] fresh;
        try {
            fresh = createFonts();
        } catch (Exception e) {
            return false;
        }

        this.fonts = fresh;
        int index = Math.max(0, Math.min(fontIndex, fresh.length - 1));
        this.font = fresh[index];

        if (old != null) {
            for (GlyphFont f : old) {
                f.close();
            }
        }
        return true;
    }

    @Override
    public void setAlpha(double a) {
        mesh.alpha = a;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("CustomTextRenderer.begin() called twice");
        this.building = true;
        this.scaleOnly = scaleOnly;
        this.meshStarted = false;
        selectGlyphFont(scale, big);
    }

    GlyphSelection selectGlyphFont(double requestedScale, boolean big) {
        this.fontIndex = resolveFontIndex(requestedScale, big);
        this.font = fonts[fontIndex];
        this.fontScale = font.height() / 27.0;
        this.scale = 1 + (requestedScale - fontScale) / fontScale;
        double glyphScale = this.scale / 1.5;
        return new GlyphSelection(font, glyphScale, fontScale * glyphScale);
    }

    /**
     * Resolve metrics/font for direct world text without mutating the renderer's active UI scale.
     * WorldTextRenderer uses this outside begin()/end(); leaking its requested scale into the
     * shared renderer made later HUD getWidth()/getHeight() calls depend on 3D render order.
     */
    GlyphSelection prepareGlyphFont(double requestedScale, boolean big) {
        int index = resolveFontIndex(requestedScale, big);
        GlyphFont selected = fonts[index];
        if (!selected.isReady() && !building && rebuildFonts()) {
            index = resolveFontIndex(requestedScale, big);
            selected = fonts[index];
        }

        double selectedFontScale = selected.height() / 27.0;
        double selectedScale = 1 + (requestedScale - selectedFontScale) / selectedFontScale;
        double glyphScale = selectedScale / 1.5;
        return new GlyphSelection(selected, glyphScale, selectedFontScale * glyphScale);
    }

    private int resolveFontIndex(double requestedScale, boolean big) {
        if (big) return fonts.length - 1;

        double scaleA = Math.floor(requestedScale * 10) / 10;
        int scaleI;
        if (scaleA >= 3) scaleI = 5;
        else if (scaleA >= 2.5) scaleI = 4;
        else if (scaleA >= 2) scaleI = 3;
        else if (scaleA >= 1.5) scaleI = 2;
        else scaleI = 1;
        return Math.max(0, Math.min(fonts.length - 1, scaleI - 1));
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        GlyphFont activeFont = building ? this.font : fonts[0];
        return (activeFont.getWidth(text, length) + (shadow ? 1 : 0)) * scale / 1.5;
    }

    @Override
    public double getHeight(boolean shadow) {
        GlyphFont activeFont = building ? this.font : fonts[0];
        return (activeFont.height() + 1 + (shadow ? 1 : 0)) * scale / 1.5;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        GlyphFont activeFont = building && this.font != null ? this.font : fonts[0];
        return activeFont != null && activeFont.hasGlyph(codePoint);
    }

    @Override
    public double render(String text, double x, double y, RenderColor color, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        ensureMeshStarted();

        double width;
        if (shadow) {
            int preShadowA = SHADOW_COLOR.a;
            SHADOW_COLOR.a = (int) (color.a / 255.0 * preShadowA);

            font.render(mesh, text, x + fontScale * scale / 1.5, y + fontScale * scale / 1.5, SHADOW_COLOR, scale / 1.5);
            width = font.render(mesh, text, x, y, color, scale / 1.5);

            SHADOW_COLOR.a = preShadowA;
        } else {
            width = font.render(mesh, text, x, y, color, scale / 1.5);
        }

        if (!wasBuilding) end();
        return width;
    }

    public double renderGradient(String text, double x, double y, Font.GlyphGradient gradient, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        ensureMeshStarted();

        double width;
        if (shadow) {
            int preShadowA = SHADOW_COLOR.a;
            SHADOW_COLOR.a = (int) (preShadowA * mesh.alpha);

            font.render(mesh, text, x + fontScale * scale / 1.5, y + fontScale * scale / 1.5, SHADOW_COLOR, scale / 1.5);
            width = font.renderGradient(mesh, text, x, y, scale / 1.5, gradient);

            SHADOW_COLOR.a = preShadowA;
        } else {
            width = font.renderGradient(mesh, text, x, y, scale / 1.5, gradient);
        }

        if (!wasBuilding) end();
        return width;
    }

    @Override
    public double renderQuadGradient(String text, double x, double y, Font.GlyphQuadGradient gradient, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        ensureMeshStarted();

        double width;
        if (shadow) {
            int preShadowA = SHADOW_COLOR.a;
            SHADOW_COLOR.a = (int) (preShadowA * mesh.alpha);

            font.render(mesh, text, x + fontScale * scale / 1.5, y + fontScale * scale / 1.5, SHADOW_COLOR, scale / 1.5);
            width = font.renderQuadGradient(mesh, text, x, y, scale / 1.5, gradient);

            SHADOW_COLOR.a = preShadowA;
        } else {
            width = font.renderQuadGradient(mesh, text, x, y, scale / 1.5, gradient);
        }

        if (!wasBuilding) end();
        return width;
    }

    public double renderHorizontalFade(String text,
                                       double x,
                                       double y,
                                       RenderColor color,
                                       double clipLeft,
                                       double clipRight,
                                       double fadeLeft,
                                       double fadeRight,
                                       boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        ensureMeshStarted();

        double glyphScale = scale / 1.5;
        Font.GlyphGradient mainGradient = horizontalFadeGradient(color, clipLeft, clipRight, fadeLeft, fadeRight, glyphScale);

        double width;
        if (shadow) {
            int preShadowA = SHADOW_COLOR.a;
            SHADOW_COLOR.a = (int) (preShadowA * mesh.alpha);

            Font.GlyphGradient shadowGradient = horizontalFadeGradient(
                    SHADOW_COLOR, clipLeft, clipRight, fadeLeft, fadeRight, glyphScale
            );
            font.renderGradient(mesh, text, x + fontScale * glyphScale, y + fontScale * glyphScale, glyphScale, shadowGradient);
            width = font.renderGradient(mesh, text, x, y, glyphScale, mainGradient);

            SHADOW_COLOR.a = preShadowA;
        } else {
            width = font.renderGradient(mesh, text, x, y, glyphScale, mainGradient);
        }

        if (!wasBuilding) end();
        return width;
    }

    public double renderHorizontalFadeClipped(String text,
                                              double x,
                                              double y,
                                              RenderColor color,
                                              double clipLeft,
                                              double clipRight,
                                              double fadeLeft,
                                              double fadeRight,
                                              boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        boolean clipped = false;
        double width;
        try {
            double clipWidth = Math.max(0.0, clipRight - clipLeft);
            double clipHeight = Math.max(0.0, getHeight(shadow));
            clipped = ScissorFunction.pushRaw(
                    (float) clipLeft,
                    (float) y,
                    (float) clipWidth,
                    (float) clipHeight
            );
            width = renderHorizontalFade(text, x, y, color, clipLeft, clipRight, fadeLeft, fadeRight, shadow);
        } finally {
            if (clipped) {
                ScissorFunction.pop();
            }
            if (!wasBuilding) {
                end();
            }
        }
        return width;
    }

    private Font.GlyphGradient horizontalFadeGradient(RenderColor color,
                                                      double clipLeft,
                                                      double clipRight,
                                                      double fadeLeft,
                                                      double fadeRight,
                                                      double glyphScale) {
        int argb = ((color.a & 0xFF) << 24)
                | ((color.r & 0xFF) << 16)
                | ((color.g & 0xFF) << 8)
                | (color.b & 0xFF);
        double leftFade = Math.max(0.0, fadeLeft);
        double rightFade = Math.max(0.0, fadeRight);

        return (index, cp, glyphX, out) -> {
            double glyphAdvance = glyphAdvance(cp) * glyphScale;
            double leftAlpha = fadeAlphaAt(glyphX, clipLeft, clipRight, leftFade, rightFade);
            double rightAlpha = fadeAlphaAt(glyphX + glyphAdvance, clipLeft, clipRight, leftFade, rightFade);
            out[0] = scaleArgbAlpha(argb, leftAlpha);
            out[1] = scaleArgbAlpha(argb, rightAlpha);
        };
    }

    private double glyphAdvance(int codePoint) {
        String glyph = new String(Character.toChars(codePoint));
        return font.getWidth(glyph, glyph.length());
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new RuntimeException("CustomTextRenderer.end() called without calling begin()");

        if (meshStarted) {
            mesh.end();
            if (!font.isReady()) {
                rebuildFonts();
            }
            if (font.isReady()) {
                TextRenderSystem.submitGlyphMesh(
                        "Combatant UI Text",
                        font,
                        mesh,
                        fastUiText
                                ? (font.isMsdf() ? CombatantRenderPipelines.UI_TEXT_MSDF_FAST : CombatantRenderPipelines.UI_TEXT_FAST)
                                : (font.isMsdf() ? CombatantRenderPipelines.UI_TEXT_MSDF : CombatantRenderPipelines.UI_TEXT),
                        TextPlacementMode.UI
                );
            }
        }

        building = false;
        scaleOnly = false;
        meshStarted = false;
        fastUiText = false;
        scale = 1;
    }

    private void ensureMeshStarted() {
        if (meshStarted) {
            return;
        }
        // Do not flush Renderer2D here. UI text is enqueued as an ordered Renderer2D batch entry
        // by TextRenderSystem.submitGlyphMesh(...). Only adjacent compatible text runs may merge;
        // text order relative to shapes/items is preserved. Non-UI/world text still submits immediately.
        if (!mesh.isBuilding()) {
            mesh.begin();
        }
        ViewportContext.ProjectionMode projection = ViewportContext.activeMode();
        fastUiText = !RenderWarpStack.active()
                && (projection == ViewportContext.ProjectionMode.LOGICAL
                || projection == ViewportContext.ProjectionMode.SCALED);
        meshStarted = true;
        scaleOnly = false;
    }

    public void destroy() {
        mesh.close();
        if (fonts == null) return;
        for (GlyphFont f : this.fonts) {
            f.close();
        }
    }

    record GlyphSelection(GlyphFont font, double glyphScale, double shadowOffset) {
    }
}
