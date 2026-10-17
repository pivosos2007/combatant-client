package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudElementRegister;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.draw.UiGlassInteraction;
import combatant.client.render.engine.renderer.ui.draw.UiGlassOpticsSpec;
import combatant.client.render.engine.renderer.ui.draw.UiLiquidGlassMaterial;
import combatant.client.render.engine.renderer.ui.runtime.core.UiMaterialInteractionState;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;


/**
 * Dev-only, full-size optical calibration board. Each tile renders its own high-contrast
 * pattern before CURRENT_TARGET glass captures it. Without that ordered capture, the
 * probe would show the world behind it instead of the calibration pattern.
 */
@HudElementRegister(order = Integer.MAX_VALUE - 2)
@HudElementInfo(
        id = "dev_liquid_glass_probe",
        displayName = "Dev Liquid Glass",
        enabledByDefault = true,
        order = Integer.MAX_VALUE - 2,
        description = "hud.draggable.dev_liquid_glass_probe.description")
public final class DevLiquidGlassProbe extends DraggableHudElement {
    private static final float CARD_W = 225f;
    private static final float CARD_H = 158f;
    private static final float GAP = 14f;
    private static final float PAD = 18f;
    private static final float HEADER_H = 60f;
    private static final float FOOTER_H = 23f;
    private static final float PANEL_W = CARD_W * 3f + GAP * 2f + PAD * 2f;
    private static final float PANEL_H = CARD_H * 3f + GAP * 2f + PAD * 2f + HEADER_H + FOOTER_H;
    private static final float RADIUS = 12f;

    private final UiMaterialInteractionState coreState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState clarityState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState bevelState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState prismState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState fluidState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState lowerState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState upperState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState openingState = new UiMaterialInteractionState();
    private final UiMaterialInteractionState closingState = new UiMaterialInteractionState();

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        float fit = fit(screenW, screenH);
        moveTo(Math.max(10f, (screenW - PANEL_W * fit) * 0.5f),
                Math.max(10f, (screenH - PANEL_H * fit) * 0.5f));
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer, TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx, float tickDelta, int screenW, int screenH) {
        TextRenderer fonts = textRenderer != null ? textRenderer : TextRenderer.get();
        float s = fit(screenW, screenH);
        float w = PANEL_W * s;
        float h = PANEL_H * s;
        float left = Math.max(10f * s, Math.min(x, Math.max(10f * s, screenW - w - 10f * s)));
        float top = Math.max(10f * s, Math.min(y, Math.max(10f * s, screenH - h - 10f * s)));
        setBounds(left, top, w, h);

        // The shell is opaque enough for legibility, but not a second blur pass.
        renderer.roundedRect(left, top, w, h, 17f * s, 1f, 0xE5121722);
        renderer.roundedRect(left + 1f * s, top + 1f * s, w - 2f * s,
                h - 2f * s, 16f * s, 1f, 0x1A61697A);
        label(fonts, true, "LIQUID GLASS  /  DEV OPTICAL PROBE",
                left + PAD * s, top + 13f * s, 18f * s, w - 2f * PAD * s, 0xFFFFFFFF);
        label(fonts, false, "Independent phases on a calibrated UI source  ·  move and click over FLUID",
                left + PAD * s, top + 37f * s, 12.2f * s, w - 2f * PAD * s, 0xFFC3CBD8);

        float gridLeft = left + PAD * s;
        float gridTop = top + (PAD + HEADER_H) * s;
        float stepX = (CARD_W + GAP) * s;
        float stepY = (CARD_H + GAP) * s;
        float cw = CARD_W * s;
        float ch = CARD_H * s;

        tile(renderer, fonts, gridLeft, gridTop, cw, ch, s, "SOURCE / REFERENCE",
                "Sharp RGB bands, no glass", Phase.SOURCE, null, null);
        tile(renderer, fonts, gridLeft + stepX, gridTop, cw, ch, s, "MATTE CORE",
                "Frosted center / low clarity", Phase.GLASS, coreState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0f).withCleanReveal(0.06f)
                        .withInteractionStrength(0f).withSpecular(false));
        tile(renderer, fonts, gridLeft + 2f * stepX, gridTop, cw, ch, s, "CLEAN REVEAL",
                "Edge transmission; compare center", Phase.GLASS, clarityState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.07f).withCleanReveal(1f)
                        .withInteractionStrength(0f).withSpecular(false));

        float r2 = gridTop + stepY;
        tile(renderer, fonts, gridLeft, r2, cw, ch, s, "BEVEL / SPECULAR",
                "Exaggerated SDF facet lighting", Phase.GLASS, bevelState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.08f).withBevel(0.35f, 0.30f)
                        .withCleanReveal(0.52f).withInteractionStrength(0f).withSpecular(true));
        tile(renderer, fonts, gridLeft + stepX, r2, cw, ch, s, "PRISM / CHROMATIC",
                "RGB split + optical magnification", Phase.GLASS, prismState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.22f).withChromaticAberration(0.86f)
                        .withMagnification(1.14f).withBevel(0.32f, 0.25f)
                        .withCleanReveal(0.66f).withInteractionStrength(0f));
        tile(renderer, fonts, gridLeft + 2f * stepX, r2, cw, ch, s, "FLUID / PRESS",
                "Move, stop, then click inside", Phase.FLUID, fluidState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.19f).withChromaticAberration(0.53f)
                        .withMagnification(1.06f).withBevel(0.26f, 0.18f)
                        .withCleanReveal(0.80f).withInteractionStrength(1.30f)
                        .withFluid(0.57f, 0.60f));

        float r3 = gridTop + 2f * stepY;
        tile(renderer, fonts, gridLeft, r3, cw, ch, s, "STACKED LENSES",
                "Lower + upper; alpha-masked stack", Phase.STACKED, lowerState, null);
        tile(renderer, fonts, gridLeft + stepX, r3, cw, ch, s, "REVEAL LIFECYCLE",
                "Automatic open impulse / 3 sec", Phase.OPEN, openingState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.15f).withBevel(0.30f, 0.23f)
                        .withCleanReveal(0.7f).withInteractionStrength(1f));
        tile(renderer, fonts, gridLeft + 2f * stepX, r3, cw, ch, s, "DISMISS LIFECYCLE",
                "Automatic close impulse / 3 sec", Phase.CLOSE, closingState,
                UiGlassOpticsSpec.LIQUID.withRefraction(0.15f).withBevel(0.30f, 0.23f)
                        .withCleanReveal(0.7f).withInteractionStrength(1f));

        label(fonts, false, "DEV-ONLY  ·  CURRENT_TARGET capture per test  ·  source texture is drawn before the lens",
                left + PAD * s, top + h - 21f * s, 11f * s, w - 2f * PAD * s, 0xFF9EACBF);
    }

    private static float fit(int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return 1f;
        // Adapt to the actual logical viewport, not HudScale's 1920x1080 design space.
        return Math.min(1f, Math.min((screenW - 26f) / PANEL_W, (screenH - 26f) / PANEL_H));
    }

    private enum Phase { SOURCE, GLASS, FLUID, STACKED, OPEN, CLOSE }

    private void tile(Renderer2D renderer, TextRenderer fonts, float left, float top,
                      float w, float h, float s, String title, String subtitle,
                      Phase phase, UiMaterialInteractionState owner, UiGlassOpticsSpec optics) {
        renderer.roundedRect(left, top, w, h, RADIUS * s, 1f, 0xFF232A38);
        renderer.roundedRect(left + 1f * s, top + 1f * s, w - 2f * s,
                h - 2f * s, (RADIUS - 1f) * s, 1f, 0x225E6878);

        float gx = left + 10f * s;
        float gy = top + 47f * s;
        float gw = w - 20f * s;
        float gh = h - 58f * s;
        float radius = 11f * s;
        if (phase != Phase.STACKED) {
            drawCalibrationPattern(renderer, gx, gy, gw, gh, radius, s);
        }

        if (phase == Phase.SOURCE) {
            // The calibration reference must stay completely sharp.
        } else if (phase == Phase.STACKED) {
            drawStacked(renderer, gx, gy, gw, gh, radius, s);
        } else {
            long now = System.nanoTime();
            boolean period = ((now / 3_000_000_000L) & 1L) != 0L;
            boolean dismiss = phase == Phase.OPEN ? period
                    : phase == Phase.CLOSE && !period;
            drawGlass(renderer, gx, gy, gw, gh, radius, owner, optics,
                    false, phase == Phase.FLUID, dismiss, now);
        }

        // All strokes are inset inside the card and follow the same SDF as the lens.
        renderer.roundedRectStroke(gx, gy, gw, gh, radius, 1f * s, 0x778C9BAA);

        // Overlay typography is deliberately drawn AFTER the glass, never refracted away.
        label(fonts, true, title, left + 11f * s, top + 9f * s,
                15.2f * s, w - 22f * s, 0xFFF6F8FF);
        label(fonts, false, subtitle, left + 11f * s, top + 29f * s,
                11.8f * s, w - 22f * s, 0xFFB7C5D8);
    }

    private void drawStacked(Renderer2D renderer, float x, float y, float w, float h,
                             float radius, float s) {
        // A bounded neutral well makes the lower and upper SDF silhouettes obvious.
        // Neither the RGB pattern nor the lenses may paint outside this viewport.
        renderer.roundedRect(x, y, w, h, radius, 1f, 0xFF0A111B);
        float bx = x + 4f * s;
        float by = y + 20f * s;
        float bw = w * 0.82f;
        float bh = h * 0.76f;
        // Stacked reference lives INSIDE the lower lens. Previously the entire
        // viewport was striped, which looked like color escaping the glass.
        drawCalibrationPattern(renderer, bx, by, bw, bh, Math.min(radius, bh * 0.30f), s);
        UiGlassOpticsSpec base = UiGlassOpticsSpec.LIQUID.withRefraction(0.11f)
                .withBevel(0.24f, 0.20f).withCleanReveal(0.47f)
                .withInteractionStrength(0.0f);
        drawGlass(renderer, bx, by, bw, bh, Math.min(radius, bh * 0.30f), lowerState, base,
                false, false, false, System.nanoTime());

        float ux = x + w * 0.36f;
        float uy = y + 4f * s;
        float uw = w * 0.60f;
        float uh = h * 0.68f;
        UiGlassOpticsSpec top = UiGlassOpticsSpec.LIQUID.withRefraction(0.22f)
                .withChromaticAberration(0.70f).withMagnification(1.10f)
                .withBevel(0.30f, 0.28f).withCleanReveal(0.64f)
                .withInteractionStrength(0.80f).withFluid(0.52f, 0.55f);
        drawGlass(renderer, ux, uy, uw, uh, Math.max(4f * s, radius - 2f * s),
                upperState, top, true, true, false, System.nanoTime());
        renderer.roundedRectStroke(bx, by, bw, bh, Math.min(radius, bh * 0.30f),
                1f * s, 0xAA7D9DB1);
        renderer.roundedRectStroke(ux, uy, uw, uh, Math.max(4f * s, radius - 2f * s),
                1.2f * s, 0xFFCAD7DF);
    }

    private static void label(TextRenderer fallback, boolean heading, String string,
                              float x, float y, float fontSize, int argb) {
        label(fallback, heading, string, x, y, fontSize, Float.POSITIVE_INFINITY, argb);
    }

    /** Single-line label fitted to its own column, not to the full board. */
    private static void label(TextRenderer fallback, boolean heading, String string,
                              float x, float y, float fontSize, float maxWidth, int argb) {
        TextRenderer font = heading ? BuiltinFontCatalog.INTER_BOLD.renderer(fallback)
                : BuiltinFontCatalog.ONEST_MEDIUM.renderer(fallback);
        font.beginSize(fontSize, true, false);
        float textWidth = (float) font.getWidth(string, false);
        font.end();
        float adjusted = textWidth > maxWidth && maxWidth > 0f
                ? fontSize * Math.max(0.1f, (maxWidth - 1f) / textWidth)
                : fontSize;
        font.beginSize(adjusted, true, false);
        font.render(string, x, y, new RenderColor(argb), false);
        font.end();
    }

    /**
     * Every calibration mark is rasterized against the very same rounded SDF as its
     * viewport, not merely drawn over a rounded background. A rectangular scissor
     * would still leak colored corners, while a stencil clip would introduce a
     * separate MSAA clip pass into the glass diagnostic itself.
     */
    private static void drawCalibrationPattern(Renderer2D renderer,
                                               float x, float y, float w, float h,
                                               float radius, float s) {
        if (w <= 0f || h <= 0f) return;
        renderer.roundedRect(x, y, w, h, radius, 1f, 0xFF070B11);

        int[] colors = {0xFFF45151, 0xFF141B29, 0xFF31D6F4, 0xFF161B27,
                0xFF73ED72, 0xFF111827, 0xFFF7D05D, 0xFF111827};
        float bandWidth = w / colors.length;
        for (int i = 0; i < colors.length; ++i) {
            float bandX = x + i * bandWidth;
            // Small overlap removes seams without allowing any fragment outside the SDF.
            float right = i == colors.length - 1 ? x + w : bandX + bandWidth + 0.35f * s;
            maskedMark(renderer, bandX, y, right - bandX, h,
                    x, y, w, h, radius, colors[i]);
        }

        // Fixed markers are deliberately clipped too; they can never spill into
        // the card header, neighboring tile, or outside the test viewport.
        maskedMark(renderer, x + 6f * s, y + 13f * s, w - 12f * s, 2f * s,
                x, y, w, h, radius, 0xFFFFFFFF);
        maskedMark(renderer, x + 6f * s, y + h * 0.66f, w - 12f * s, 3f * s,
                x, y, w, h, radius, 0xFF080C12);
        float hatchX = x + 8f * s;
        float hatchEnd = x + Math.min(w * 0.48f, 85f * s);
        float hatchStep = Math.max(3.0f * s, (hatchEnd - hatchX) / 18f);
        for (float pos = hatchX; pos + 1.8f * s <= hatchEnd; pos += hatchStep) {
            maskedMark(renderer, pos, y + h * 0.38f, 1.8f * s, h * 0.26f,
                    x, y, w, h, radius, 0xF8FFFFFF);
        }
    }

    private static void maskedMark(Renderer2D renderer,
                                   float x, float y, float w, float h,
                                   float maskX, float maskY, float maskW, float maskH,
                                   float maskRadius, int color) {
        if (w <= 0f || h <= 0f) return;
        renderer.roundedRectMaskedQuad(x, y, w, h,
                maskX, maskY, maskW, maskH, maskRadius, 1f,
                color, color, color, color);
    }

    private static void drawGlass(Renderer2D renderer, float x, float y, float w,
                                  float h, float radius, UiMaterialInteractionState owner,
                                  UiGlassOpticsSpec optics, boolean stacked,
                                  boolean reactive, boolean dismissing, long nowNanos) {
        UiGlassInteraction interaction = UiGlassInteraction.FRAME_POINTER;
        Minecraft mc = Minecraft.getInstance();
        if (owner != null && mc != null && mc.getWindow() != null && mc.mouseHandler != null) {
            float rawW = Math.max(1f, mc.getWindow().getScreenWidth());
            float rawH = Math.max(1f, mc.getWindow().getScreenHeight());
            ViewportContext viewport = ViewportContext.current();
            float viewW = viewport != null ? Math.max(1f, viewport.width()) : rawW;
            float viewH = viewport != null ? Math.max(1f, viewport.height()) : rawH;
            float mx = (float) mc.mouseHandler.xpos() / rawW * viewW;
            float my = (float) mc.mouseHandler.ypos() / rawH * viewH;
            boolean down = combatant.client.input.CombatantInput.state().isPressed(
                    combatant.client.input.InputDevice.MOUSE, combatant.client.input.InputKeys.MOUSE_LEFT);
            interaction = owner.sample(mx, my, down, x, y, w, h,
                    optics.interactionViscosity(), optics.interactionStrength(), nowNanos, dismissing);
        }
        UiLiquidGlassMaterial material = UiLiquidGlassMaterial.DEFAULT.withOptics(optics)
                .withInteraction(interaction);
        Runnable draw = () -> HudRenderUtil.drawLiquidGlass(x, y, w, h, radius, 1f, false,
                reactive ? 0.58f : 0.72f, 0.92f);
        // Diagnostic-only CURRENT_TARGET: the glass must actually see the pattern that
        // this tile drew earlier in the same ordered render stream.
        renderer.withLiquidGlassSceneSource(stacked
                        ? UiBackdropRequest.SceneSource.STACKED_LENSES
                        : UiBackdropRequest.SceneSource.CURRENT_TARGET,
                () -> renderer.withLiquidGlassMaterial(material, draw));
    }
}
