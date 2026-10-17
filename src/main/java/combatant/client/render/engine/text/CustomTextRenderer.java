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

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.RenderWarpStack;
import combatant.client.render.engine.renderer.ui.blend.UiBackdropBlendSpec;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.text.backend.TextPlacementMode;
import org.lwjgl.system.MemoryUtil;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.UiRenderPipelines;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.helpers.ScissorFunction;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class CustomTextRenderer implements TextRenderer {
    public static final RenderColor SHADOW_COLOR = new RenderColor(60, 60, 60, 180);
    public final FontFace fontFace;
    private final MeshBuilder mesh = new MeshBuilder(UiRenderPipelines.UI_TEXT);
    private final MeshBuilder liquidGlassMesh = new MeshBuilder(UiRenderPipelines.UI_TEXT_MSDF_GLASS_FAST);
    private GlyphFont[] fonts;
    private GlyphFont font;
    private int fontIndex;
    /** One optional, lazily created bitmap atlas per MSDF face. */
    private GlyphFont smallBitmapFont;
    private boolean smallBitmapUnavailable;
    private double requestedScale = 1.0;
    private TextBackendPreference backendPreference = TextBackendPreference.AUTO;

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
        boolean msdfEligible = fontFace instanceof BuiltinFontFace;
        if (msdfEligible) {
            FontDebugStats.noteMsdfAttempt(fontFace.info);
            GlyphFont[] msdf = MsdfFont.tryCreate(fontFace);
            if (msdf != null) {
                FontDebugStats.noteMsdfSuccess(fontFace.info);
                return msdf;
            }
            FontDebugStats.noteMsdfFallback(fontFace.info, MsdfFont.getLastError());
            if (fontFace.isAtlasOnly()) {
                throw new IllegalStateException(
                        "Atlas-only font has no usable MSDF atlas: " + fontFace.info
                                + " (" + MsdfFont.getLastError() + ")"
                );
            }
        }

        byte[] bytes;
        try (InputStream source = fontFace.toStream()) {
            bytes = FontUtils.readBytes(source);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read font " + fontFace.info, exception);
        }
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

    private boolean rebuildFonts() {
        GlyphFont[] old = this.fonts;
        GlyphFont[] fresh;
        try {
            fresh = createFonts();
        } catch (Exception e) {
            return false;
        }

        this.fonts = fresh;
        GlyphFont oldSmall = smallBitmapFont;
        smallBitmapFont = null;
        smallBitmapUnavailable = false;
        if (oldSmall != null) oldSmall.close();
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
        begin(scale, scaleOnly, big, TextBackendPreference.AUTO);
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big, TextBackendPreference preference) {
        if (building) throw new RuntimeException("CustomTextRenderer.begin() called twice");
        this.building = true;
        this.scaleOnly = scaleOnly;
        this.meshStarted = false;
        this.backendPreference = preference != null ? preference : TextBackendPreference.AUTO;
        selectGlyphFont(scale, big);
    }

    GlyphSelection selectGlyphFont(double requestedScale, boolean big) {
        this.requestedScale = requestedScale;
        this.fontIndex = resolveFontIndex(requestedScale, big, true);
        this.font = fonts[fontIndex];
        if (font.isMsdf() && !fontFace.isAtlasOnly()
                && backendPreference != TextBackendPreference.MSDF
                && (backendPreference == TextBackendPreference.BITMAP_ATLAS
                    || FontRasterSelection.preferSmallBitmap(requestedScale,
                        FontRasterSelection.activePixelDensity(), big, RenderWarpStack.active()))) {
            GlyphFont bitmap = smallBitmapFont();
            if (bitmap != null && bitmap.isReady()) this.font = bitmap;
        }
        this.fontScale = font.height() / 27.0;
        this.scale = 1 + (requestedScale - fontScale) / fontScale;
        double glyphScale = this.scale / 1.5;
        return new GlyphSelection(font, glyphScale, fontScale * glyphScale);
    }

    private GlyphFont smallBitmapFont() {
        if (smallBitmapFont != null) return smallBitmapFont;
        if (smallBitmapUnavailable) return null;
        if (fontFace.isAtlasOnly()) {
            smallBitmapUnavailable = true;
            return null;
        }
        try (InputStream stream = fontFace.toStream()) {
            byte[] bytes = FontUtils.readBytes(stream);
            ByteBuffer source = MemoryUtil.memAlloc(bytes.length);
            try {
                source.put(bytes).flip();
                // Only one bitmap page per face; the MSDF nominal sizes share an atlas.
                smallBitmapFont = new Font(source, 27);
                return smallBitmapFont;
            } finally {
                MemoryUtil.memFree(source);
            }
        } catch (Exception exception) {
            smallBitmapUnavailable = true;
            return null; // Atlas-only/missing TrueType must never disable MSDF.
        }
    }

    /**
     * Glass glyphs must use the RGB distance-field atlas, even while an active UI
     * run is using its smaller R8 bitmap. Never change {@link #font} here: the
     * ordinary mesh may already contain vertices from that bitmap atlas, and
     * later plain text in the same begin/end block still needs the same source.
     */
    private GlyphFont liquidGlassFont() {
        GlyphFont atlasFont = fonts[fontIndex];
        return atlasFont != null && atlasFont.isMsdf() && atlasFont.isReady() && !RenderWarpStack.active()
                ? atlasFont : null;
    }

    private double liquidGlassGlyphScale(GlyphFont atlasFont) {
        // Compute the geometry scale from the actual MSDF nominal size, not
        // from the selected bitmap's nominal size.
        return requestedScale / (atlasFont.height() / 27.0) / 1.5;
    }

    /**
     * Resolve metrics/font for direct world text without mutating the renderer's active UI scale.
     * WorldTextRenderer uses this outside begin()/end(); leaking its requested scale into the
     * shared renderer made later HUD getWidth()/getHeight() calls depend on 3D render order.
     */
    GlyphSelection prepareGlyphFont(double requestedScale, boolean big) {
        int index = resolveFontIndex(requestedScale, big, false);
        GlyphFont selected = fonts[index];
        if (!selected.isReady() && !building && rebuildFonts()) {
            index = resolveFontIndex(requestedScale, big, false);
            selected = fonts[index];
        }

        double selectedFontScale = selected.height() / 27.0;
        double selectedScale = 1 + (requestedScale - selectedFontScale) / selectedFontScale;
        double glyphScale = selectedScale / 1.5;
        return new GlyphSelection(selected, glyphScale, selectedFontScale * glyphScale);
    }

    private int resolveFontIndex(double requestedScale, boolean big, boolean screenSpace) {
        // A logical HUD pixel is not necessarily a framebuffer pixel. Select the source
        // raster using the actual projection density, but keep metrics and geometry in
        // logical units. World billboards use their own scale and never inherit UI DPI.
        if (!screenSpace) {
            // WorldTextRenderer intentionally keeps its existing atlas-selection
            // semantics: its raster footprint is perspective-dependent, not the
            // active HUD framebuffer density.
            if (big) return fonts.length - 1;
            double scaleA = Math.floor(requestedScale * 10) / 10;
            int index = scaleA >= 3.0 ? 4 : scaleA >= 2.5 ? 3
                    : scaleA >= 2.0 ? 2 : scaleA >= 1.5 ? 1 : 0;
            return Math.min(index, fonts.length - 1);
        }
        return FontRasterSelection.selectAtlas(fonts, requestedScale,
                FontRasterSelection.activePixelDensity(), big);
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text == null || text.isEmpty()) return 0;
        GlyphFont activeFont = building ? this.font : fonts[0];
        if (!building && smallBitmapFont != null && smallBitmapFont.isReady()
                && FontRasterSelection.preferSmallBitmap(1.0,
                    FontRasterSelection.activePixelDensity(), false, RenderWarpStack.active())) {
            activeFont = smallBitmapFont;
        }
        double nominalScale = activeFont.height() / 27.0;
        return activeFont.getWidth(text, length) * scale / 1.5
                + (shadow ? nominalScale * scale / 1.5 : 0.0);
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

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight) {
        return renderLiquidGlassQuadGradient(text, x, y, gradient, boundsX, boundsY, boundsWidth, boundsHeight, null);
    }

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight,
                                                 RenderPipeline pipelineOverride) {
        return renderLiquidGlassQuadGradient(text, x, y, gradient, boundsX, boundsY,
                boundsWidth, boundsHeight, pipelineOverride, null);
    }

    @Override
    public double renderLiquidGlassQuadGradient(String text,
                                                 double x,
                                                 double y,
                                                 Font.GlyphQuadGradient gradient,
                                                 double boundsX,
                                                 double boundsY,
                                                 double boundsWidth,
                                                 double boundsHeight,
                                                 RenderPipeline pipelineOverride,
                                                 UiBackdropRequest backdrop) {
        if (text == null || text.isEmpty() || gradient == null) return x;
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        try {
            GlyphFont glassFont = liquidGlassFont();
            if (glassFont == null) {
                return renderQuadGradient(text, x, y, gradient, false);
            }

            // Preserve draw order: submit preceding ordinary glyphs before a
            // separately composited Liquid Glass draw, without changing their atlas.
            flushActiveMesh();
            if (liquidGlassMesh.isBuilding()) liquidGlassMesh.end();
            liquidGlassMesh.begin();
            double width = glassFont.renderQuadGradient(
                    liquidGlassMesh, text, x, y, liquidGlassGlyphScale(glassFont), gradient);
            liquidGlassMesh.end();

            TextRenderSystem.submitLiquidGlassGlyphMesh(
                    "Combatant UI Liquid Glass Text",
                    glassFont,
                    liquidGlassMesh,
                    pipelineOverride != null ? pipelineOverride : UiRenderPipelines.UI_TEXT_MSDF_GLASS_FAST,
                    TextPlacementMode.UI,
                    boundsX,
                    boundsY,
                    boundsWidth,
                    boundsHeight,
                    backdrop
            );
            return width;
        } finally {
            if (!wasBuilding) end();
        }
    }

    @Override
    public double renderLiquidGlassBlendQuadGradient(String text,
                                                      double x,
                                                      double y,
                                                      Font.GlyphQuadGradient gradient,
                                                      double boundsX,
                                                      double boundsY,
                                                      double boundsWidth,
                                                      double boundsHeight,
                                                      UiBackdropBlendSpec blend,
                                                      UiBackdropRequest backdrop) {
        if (text == null || text.isEmpty() || gradient == null) return x;
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        try {
            GlyphFont glassFont = liquidGlassFont();
            if (glassFont == null) {
                return renderQuadGradient(text, x, y, gradient, false);
            }

            flushActiveMesh();
            if (liquidGlassMesh.isBuilding()) liquidGlassMesh.end();
            liquidGlassMesh.begin();
            double width = glassFont.renderQuadGradient(
                    liquidGlassMesh, text, x, y, liquidGlassGlyphScale(glassFont), gradient);
            liquidGlassMesh.end();

            TextRenderSystem.submitLiquidGlassBlendGlyphMesh(
                    "Combatant UI Backdrop Blend Text",
                    glassFont,
                    liquidGlassMesh,
                    TextPlacementMode.UI,
                    boundsX,
                    boundsY,
                    boundsWidth,
                    boundsHeight,
                    blend,
                    backdrop
            );
            return width;
        } finally {
            if (!wasBuilding) end();
        }
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

        flushActiveMesh();

        building = false;
        scaleOnly = false;
        meshStarted = false;
        fastUiText = false;
        backendPreference = TextBackendPreference.AUTO;
        scale = 1;
    }

    /**
     * Finish only the current ordinary glyph run. A glass pass may appear
     * between two ordinary runs inside one begin/end pair; both ordinary runs
     * must retain their original font, texture and pipeline.
     */
    private void flushActiveMesh() {
        if (!meshStarted) return;
        mesh.end();
        GlyphFont drawnFont = font;
        if (drawnFont.isReady()) {
            TextRenderSystem.submitGlyphMesh(
                    "Combatant UI Text",
                    drawnFont,
                    mesh,
                    fastUiText
                            ? (drawnFont.isMsdf() ? UiRenderPipelines.UI_TEXT_MSDF_FAST : UiRenderPipelines.UI_TEXT_FAST)
                            : (drawnFont.isMsdf() ? UiRenderPipelines.UI_TEXT_MSDF : UiRenderPipelines.UI_TEXT),
                    TextPlacementMode.UI
            );
        } else {
            // Do not submit old UVs against a newly created texture when a
            // resource reload invalidated the atlas midway through a frame.
            rebuildFonts();
        }
        meshStarted = false;
        fastUiText = false;
    }

    /** Preallocates the text mesh so the first glyph draw does not allocate native buffers. */
    public void prewarmBuffers() {
        mesh.prewarmDefaultCapacity();
        liquidGlassMesh.reserve(256, 384);
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
        liquidGlassMesh.close();
        if (fonts == null) return;
        for (GlyphFont f : this.fonts) {
            f.close();
        }
        if (smallBitmapFont != null) {
            smallBitmapFont.close();
            smallBitmapFont = null;
        }
    }

    record GlyphSelection(GlyphFont font, double glyphScale, double shadowOffset) {
    }
}
