/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.debug;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.renderer.ui.draw.UiShape;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.logging.DebugLog;

/**
 * Manual hybrid clip acceptance overlay. Ordinary clip calls prefer the 2x MSAA fallback while
 * explicitly required material-visible boundaries remain analytic.
 * Enable with {@code -Dcombatant.render.debug.clipScene=true} or
 * {@code COMBATANT_UI_CLIP_DEBUG=1}.
 */
public enum UiClipDebugScene {
    ;
    private static final String ENABLE_PROPERTY = "combatant.render.debug.clipScene";
    private static final String ENABLE_ENV = "COMBATANT_UI_CLIP_DEBUG";
    private static final boolean ENABLED = Boolean.getBoolean(ENABLE_PROPERTY)
            || enabledValue(System.getenv(ENABLE_ENV));
    private static long frame;

    public static boolean enabled() {
        return ENABLED;
    }

    private static boolean enabledValue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    public static void renderAfterGui() {
        if (!enabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || Renderer2D.COLOR == null) return;

        ViewportContext previousViewport = ViewportContext.current();
        boolean previousRendering3D = RenderState.rendering3D;
        int previousClipDepth = ClipFunction.depth();
        var modelView = RenderSystem.getModelViewStack();
        boolean pushedModelView = false;
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "debug:ui_clip_scene")) {
            modelView.pushMatrix();
            pushedModelView = true;
            modelView.identity();

            ViewportContext viewport = ViewportContext.capture(ViewportContext.ProjectionMode.SCALED);
            ViewportContext.applyCaptured(viewport);
            draw(viewport, mc);
            frame++;
        } catch (Throwable failure) {
            DebugLog.errorOnce("ui_clip_debug_scene", "[UIClipDebugScene] render failed: %s", failure);
        } finally {
            while (ClipFunction.depth() > previousClipDepth) ClipFunction.pop();
            if (pushedModelView) modelView.popMatrix();
            if (previousViewport != null) ViewportContext.applyCaptured(previousViewport);
            RenderState.rendering3D = previousRendering3D;
        }
    }

    private static void draw(ViewportContext viewport, Minecraft mc) {
        Renderer2D renderer = Renderer2D.COLOR;
        float availableW = Math.max(1.0f, viewport.width());
        float availableH = Math.max(1.0f, viewport.height());
        float panelW = Math.min(720.0f, availableW - 16.0f);
        float panelH = Math.min(390.0f, availableH - 16.0f);
        if (panelW < 480.0f || panelH < 360.0f) return;

        float x = (availableW - panelW) * 0.5f;
        float y = (availableH - panelH) * 0.5f;
        float innerX = x + 12.0f;
        float innerY = y + 28.0f;
        float innerW = panelW - 24.0f;

        renderer.begin();
        renderer.roundedRect(x, y, panelW, panelH, 14.0f, 0xF20A0D14);
        renderer.roundedRectStroke(x, y, panelW, panelH, 14.0f, 1.0f, 0xAA78A8FF);
        drawLabel("HYBRID CLIP ACCEPTANCE  |  DEFAULT=2x  |  phase " + (frame % 120L), x + 12.0f, y + 8.0f, 11.0f, 0xFFEAF2FF);

        drawRadiusRow(renderer, innerX, innerY, innerW);
        drawShapeMatrix(renderer, mc, innerX, innerY + 58.0f, innerW);
        drawNestedAndMaterialMatrix(renderer, mc, innerX, innerY + 126.0f, innerW);
        drawFractionalText(renderer, innerX, innerY + 230.0f, innerW);
        drawExplicitScissorAndMsaa(renderer, viewport, mc, innerX, innerY + 286.0f, innerW);

        renderer.render();
    }

    private static void drawRadiusRow(Renderer2D renderer, float x, float y, float width) {
        float[] radii = {1.0f, 2.0f, 4.0f, 8.0f, 16.0f, 24.0f, 48.0f};
        float gap = 5.0f;
        float cellW = (width - gap * (radii.length - 1)) / radii.length;
        for (int i = 0; i < radii.length; i++) {
            float cx = x + i * (cellW + gap);
            float radius = Math.min(radii[i], Math.min(cellW, 42.0f) * 0.5f);
            boolean clipped = ClipFunction.pushRoundedRect(cx, y, cellW, 42.0f, radius);
            renderer.quad(cx - 8.0f, y - 5.0f, cellW + 16.0f, 52.0f, 0xFF18243A);
            renderer.quad(cx - 6.0f, y + 17.0f, cellW + 12.0f, 12.0f, 0xFFD84E78);
            renderer.quad(cx + cellW * 0.52f, y - 4.0f, 12.0f, 50.0f, 0xFF4EC9B0);
            if (clipped) ClipFunction.pop();
            drawLabel(Integer.toString((int) radii[i]), cx + 3.0f, y + 14.0f, 8.0f, 0xFFFFFFFF);
        }
    }

    private static void drawShapeMatrix(Renderer2D renderer, Minecraft mc, float x, float y, float width) {
        float gap = 8.0f;
        float cellW = (width - gap * 3.0f) / 4.0f;

        boolean asymmetric = ClipFunction.pushRoundedRect(x, y, cellW, 52.0f, 3.0f, 20.0f, 7.0f, 26.0f);
        drawCrossingContent(renderer, mc, x, y, cellW, 52.0f, "ASYM");
        if (asymmetric) ClipFunction.pop();

        float x1 = x + cellW + gap;
        boolean circle = ClipFunction.pushCircle(x1 + cellW * 0.5f, y + 26.0f, Math.min(cellW, 52.0f) * 0.5f);
        drawCrossingContent(renderer, mc, x1, y, cellW, 52.0f, "CIRCLE");
        if (circle) ClipFunction.pop();

        float x2 = x1 + cellW + gap;
        boolean chamfer = ClipFunction.pushChamferedRect(x2, y, cellW, 52.0f, 12.0f);
        drawCrossingContent(renderer, mc, x2, y, cellW, 52.0f, "CHAMFER");
        if (chamfer) ClipFunction.pop();

        float x3 = x2 + cellW + gap;
        boolean rounded = ClipFunction.pushRoundedRect(x3, y, cellW, 52.0f, 18.0f);
        renderer.svg("brain-cog", x3 - 5.0f, y - 5.0f, 62.0f, 62.0f,
                SvgRenderOptions.fromFile().withAlpha(0.92f));
        drawLabel("SVG MSDF CLIP", x3 + 8.0f, y + 20.0f, 8.5f, 0xFFFFFFFF);
        if (rounded) ClipFunction.pop();
    }

    private static void drawNestedAndMaterialMatrix(Renderer2D renderer, Minecraft mc,
                                                     float x, float y, float width) {
        float gap = 10.0f;
        float cellW = (width - gap * 2.0f) / 3.0f;
        float h = 88.0f;

        boolean outer = ClipFunction.pushRoundedRect(x, y, cellW, h, 22.0f);
        boolean inner = ClipFunction.pushCircle(x + cellW * 0.62f, y + h * 0.48f, 38.0f);
        renderer.quad(x - 10.0f, y - 8.0f, cellW + 20.0f, h + 16.0f, 0xFF243A5A);
        renderer.quad(x - 8.0f, y + 34.0f, cellW + 18.0f, 24.0f, 0xFFD84E78);
        drawLabel("NESTED 2x", x + 9.0f, y + 36.0f, 10.0f, 0xFFFFFFFF);
        if (inner) ClipFunction.pop();
        if (outer) ClipFunction.pop();

        float tx = x + cellW + gap;
        boolean textureClip = ClipFunction.pushRoundedRect(tx, y, cellW, h, 22.0f);
        drawTexture(renderer, mc, tx - 12.0f, y - 12.0f, cellW + 24.0f, h + 24.0f);
        drawLabel("UI_TEXTURE", tx + 10.0f, y + 38.0f, 10.0f, 0xFFFFFFFF);
        if (textureClip) ClipFunction.pop();

        float gx = tx + cellW + gap;
        Renderer2D.requestLiquidGlassBlurBeforeNextShapeClip();
        boolean glassClip = ClipFunction.pushRoundedRectAnalyticRequired(gx, y, cellW, h, 22.0f);
        renderer.liquidGlassRect(gx - 24.0f, y + 8.0f, cellW + 48.0f, 70.0f,
                24.0f, 0xFF8AB4FF, 0.92f, Renderer2D.LiquidGlassPreset.BALANCED);
        drawLabel("VISIBLE GLASS EDGE", gx + 8.0f, y + 38.0f, 9.0f, 0xFFFFFFFF);
        if (glassClip) ClipFunction.pop();
    }

    private static void drawFractionalText(Renderer2D renderer, float x, float y, float width) {
        float phase = 0.5f + (float) Math.sin(frame * 0.055) * 0.35f;
        float clipY = y + phase;
        boolean clipped = ClipFunction.pushRoundedRect(x + 0.5f, clipY, width, 42.0f, 16.0f);
        renderer.quad(x - 12.0f, clipY - 8.0f, width + 24.0f, 58.0f, 0xFF172033);
        renderer.quad(x - 8.0f, clipY + 28.5f, width + 16.0f, 7.0f, 0xFF44D6B3);
        drawLabel("FRACTIONAL SCROLL — bitmap/MSDF text crosses both rounded corners",
                x - 7.25f, clipY + 11.25f, 12.0f, 0xFFFFFFFF);
        if (clipped) ClipFunction.pop();
    }

    private static void drawExplicitScissorAndMsaa(Renderer2D renderer, ViewportContext viewport,
                                                    Minecraft mc, float x, float y, float width) {
        float gap = 10.0f;
        float scissorW = width * 0.58f;
        float scale = Math.max(1.0f, viewport.scaleFactor());
        boolean scissored = ScissorFunction.pushScaled(x, y, scissorW, 32.0f, scale);
        try {
            renderer.quad(x - 40.0f, y, scissorW + 80.0f, 32.0f,
                    0xFF55336F, 0xFF31567A, 0xFF1E8A76, 0xFF7D3F58);
            drawLabel("RECT SCISSOR — independent",
                    x + 8.0f, y + 9.0f, 9.0f, 0xFFFFFFFF);
        } finally {
            if (scissored) ScissorFunction.pop();
        }

        float px = x + scissorW + gap;
        float pw = width - scissorW - gap;
        double[] polygon = {
                px + 8.0f, y,
                px + pw - 18.0f, y + 1.0f,
                px + pw, y + 13.0f,
                px + pw - 7.0f, y + 32.0f,
                px + 20.0f, y + 30.0f,
                px, y + 17.0f
        };
        boolean analyticParent = ClipFunction.pushRoundedRectAnalyticRequired(px, y, pw, 32.0f, 9.0f);
        boolean clipped = ClipFunction.pushMsaaStencil(UiShape.polyline(polygon, 6, true));
        renderer.quad(px - 10.0f, y - 7.0f, pw + 20.0f, 46.0f, 0xFF18304A);
        drawTexture(renderer, mc, px + pw - 42.0f, y - 8.0f, 50.0f, 50.0f);
        drawLabel("ANALYTIC > 2x", px + 8.0f, y + 9.0f, 8.5f, 0xFFFFFFFF);
        if (clipped) ClipFunction.pop();
        if (analyticParent) ClipFunction.pop();
    }

    private static void drawCrossingContent(Renderer2D renderer, Minecraft mc,
                                            float x, float y, float w, float h, String label) {
        renderer.quad(x - 8.0f, y - 6.0f, w + 16.0f, h + 12.0f, 0xFF1B2940);
        drawTexture(renderer, mc, x + w - 42.0f, y - 8.0f, 54.0f, 54.0f);
        renderer.quad(x - 5.0f, y + h - 16.0f, w + 10.0f, 20.0f, 0xBBD84E78);
        drawLabel(label, x + 7.0f, y + 19.0f, 9.0f, 0xFFFFFFFF);
    }

    private static void drawTexture(Renderer2D renderer, Minecraft mc,
                                    float x, float y, float w, float h) {
        AbstractTexture texture = mc.getTextureManager().getTexture(TextureStorage.DEFAULT_CIRCLE);
        if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) return;
        renderer.textureQuad(texture.getTextureView(), texture.getSampler(), x, y, w, h, 0xEFFFFFFF);
    }

    private static void drawLabel(String value, float x, float y, float size, int argb) {
        TextRenderer fallback = TextRenderer.get();
        TextRenderer text = Fonts.renderer("Inter", FontInfo.Type.Regular, fallback);
        if (text == null) return;
        text.begin(Math.max(0.1f, size / 18.0f), false, false);
        try {
            text.render(value, x, y, new RenderColor(argb), false);
        } finally {
            text.end();
        }
    }
}
