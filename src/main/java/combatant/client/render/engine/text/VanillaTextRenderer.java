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

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.render.engine.renderer.Renderer2D;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.helpers.ScissorFunction;

import static net.minecraft.client.Minecraft.getInstance;

public class VanillaTextRenderer implements TextRenderer {
    public static final VanillaTextRenderer INSTANCE = new VanillaTextRenderer();
    private static final Style UNICODE_FALLBACK_STYLE = Style.EMPTY.withFont(new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("combatant", "unicode_fallback")
    ));

    private final PoseStack matrices = new PoseStack();
    public double scale = 2;
    public boolean scaleIndividually;
    private SubmitNodeStorage textSubmits = new SubmitNodeStorage();
    private FeatureRenderDispatcher featureDispatcher;
    private boolean building;
    private boolean drawing;
    private double alpha = 1;

    private VanillaTextRenderer() {
    }

    private static double fadeAlphaAt(double x,
                                      double clipLeft,
                                      double clipRight,
                                      double fadeLeft,
                                      double fadeRight) {
        if (clipRight <= clipLeft) return 1.0;
        if (x <= clipLeft || x >= clipRight) return 0.0;

        double alpha = 1.0;
        double leftFade = Math.max(0.0, fadeLeft);
        double rightFade = Math.max(0.0, fadeRight);
        if (leftFade > 0.0 && x < clipLeft + leftFade) {
            alpha = Math.min(alpha, (x - clipLeft) / leftFade);
        }
        if (rightFade > 0.0 && x > clipRight - rightFade) {
            alpha = Math.min(alpha, (clipRight - x) / rightFade);
        }
        return Math.max(0.0, Math.min(1.0, alpha));
    }

    private static int scaleArgbAlpha(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = (int) Math.round(a * Math.max(0.0, Math.min(1.0, factor)));
        return (argb & 0x00FFFFFF) | ((scaled & 0xFF) << 24);
    }

    private static void applyArgb(RenderColor color, int argb) {
        color.a = (argb >>> 24) & 0xFF;
        color.r = (argb >>> 16) & 0xFF;
        color.g = (argb >>> 8) & 0xFF;
        color.b = argb & 0xFF;
    }

    @Override
    public void setAlpha(double a) {
        alpha = a;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        if (text.isEmpty()) return 0;
        if (length != text.length()) text = text.substring(0, length);
        return (getInstance().font.width(unicodeSequence(text)) + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public double getHeight(boolean shadow) {
        return (getInstance().font.lineHeight + (shadow ? 1 : 0)) * scale;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return true;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new RuntimeException("VanillaTextRenderer.begin() called twice");
        this.scale = scale * 2;
        this.building = true;
        this.drawing = false;
    }

    @Override
    public double render(String text, double x, double y, RenderColor color, boolean shadow) {
        boolean wasBuilding = building;
        if (!wasBuilding) begin();
        ensureDrawingStarted();

        x += 0.5 * scale;
        y += 0.5 * scale;

        int preA = color.a;
        color.a = (int) (((double) color.a / 255 * alpha) * 255);

        int packed = (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
        matrices.pushPose();
        matrices.scale((float) scale, (float) scale, 1.0f);
        FormattedCharSequence sequence = unicodeSequence(text);
        textSubmits.submitText(
                matrices,
                (float) (x / scale),
                (float) (y / scale),
                sequence,
                shadow,
                DisplayMode.NORMAL,
                0x00F000F0,
                packed,
                0,
                0
        );
        matrices.popPose();
        double x2 = (x / scale) + getInstance().font.width(sequence);

        color.a = preA;

        if (!wasBuilding) end();
        return (x2 - 1) * scale;
    }

    @Override
    public double renderGradient(String text, double x, double y, Font.GlyphGradient gradient, boolean shadow) {
        if (text == null || text.isEmpty() || gradient == null) return x;
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        double cursorX = x;
        int glyphIndex = 0;
        int[] colors = new int[2];
        RenderColor color = new RenderColor(0xFFFFFFFF);
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                gradient.colors(glyphIndex, cp, cursorX, colors);
                applyArgb(color, colors[0]);
                render(glyph, cursorX, y, color, shadow);
                cursorX += getWidth(glyph, shadow);
                i += Character.charCount(cp);
                glyphIndex++;
            }
            return cursorX;
        } finally {
            if (!wasBuilding) end();
        }
    }

    @Override
    public double renderHorizontalFadeClipped(String text,
                                              double x,
                                              double y,
                                              RenderColor color,
                                              double clipLeft,
                                              double clipRight,
                                              double fadeLeft,
                                              double fadeRight,
                                              boolean shadow) {
        if (text == null || text.isEmpty() || color == null) return x;
        boolean wasBuilding = building;
        if (!wasBuilding) begin();

        boolean clipped = false;
        try {
            clipped = ScissorFunction.pushRaw(
                    (float) clipLeft,
                    (float) y,
                    (float) Math.max(0.0, clipRight - clipLeft),
                    (float) Math.max(0.0, getHeight(shadow))
            );
            int argb = color.argb();
            double endX = renderGradient(text, x, y, (idx, cp, glyphX, out) -> {
                String glyph = new String(Character.toChars(cp));
                double glyphAdvance = getWidth(glyph, false);
                double leftAlpha = fadeAlphaAt(glyphX, clipLeft, clipRight, fadeLeft, fadeRight);
                double rightAlpha = fadeAlphaAt(glyphX + glyphAdvance, clipLeft, clipRight, fadeLeft, fadeRight);
                out[0] = scaleArgbAlpha(argb, leftAlpha);
                out[1] = scaleArgbAlpha(argb, rightAlpha);
            }, shadow);
            flushDrawing();
            return endX;
        } finally {
            if (clipped) {
                ScissorFunction.pop();
            }
            if (!wasBuilding) {
                end();
            }
        }
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new RuntimeException("VanillaTextRenderer.end() called without calling begin()");

        flushDrawing();

        this.scale = 2;
        this.building = false;
        this.drawing = false;
    }

    private void ensureDrawingStarted() {
        if (drawing) {
            return;
        }
        if (!Renderer2D.isFlushingBatch()) {
            Renderer2D.flushBatch(
                    Renderer2D.FlushReason.VANILLA_TEXT
            );
        }
        drawing = true;
    }

    private void flushDrawing() {
        if (!drawing) {
            return;
        }
        if (!textSubmits.getSubmitsPerOrder().isEmpty()) {
            getFeatureDispatcher().renderAllFeatures(textSubmits);
            textSubmits = new SubmitNodeStorage();
        }
        drawing = false;
    }

    private FeatureRenderDispatcher getFeatureDispatcher() {
        if (featureDispatcher == null) {
            var mc = getInstance();
            featureDispatcher = new FeatureRenderDispatcher(
                    mc.gameRenderer.renderBuffers(),
                    mc.getModelManager(),
                    mc.getAtlasManager(),
                    mc.font,
                    mc.gameRenderer.gameRenderState()
            );
        }
        return featureDispatcher;
    }

    private static FormattedCharSequence unicodeSequence(String text) {
        String visual = needsBidirectionalLayout(text)
                ? getInstance().font.bidirectionalShaping(text)
                : text;
        return FormattedCharSequence.forward(visual, UNICODE_FALLBACK_STYLE);
    }

    static boolean needsBidirectionalLayout(String text) {
        if (text == null || text.isEmpty()) return false;
        if (getInstance().font.isBidirectional()) return true;
        return containsRightToLeftCodePoint(text);
    }

    static boolean containsRightToLeftCodePoint(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            byte directionality = Character.getDirectionality(codePoint);
            if (directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
                    || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING
                    || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}
