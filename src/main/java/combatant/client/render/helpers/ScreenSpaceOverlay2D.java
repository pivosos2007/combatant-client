/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.RuntimeTextLayout;

import java.util.List;

public enum ScreenSpaceOverlay2D {
    ;

    public static final double VANILLA_TEXT_SCALE = 0.54;
    public static final double TEXT_SCALE = 0.82;

    private static final float BOX_THICKNESS = 1.45f;
    private static final float BOX_OUTLINE_EXPAND = 1.0f;
    private static final float BOX_OUTLINE_THICKNESS = 1.0f;
    private static final int FRAME_ALPHA = 235;
    private static final int FRAME_OUTLINE_ALPHA = 170;

    private static final double LABEL_MARGIN = 4.0;
    private static final boolean PIXEL_SNAP = true;
    private static final double LABEL_PAD_X = 2.0;
    private static final double LABEL_PAD_Y = 1.0;
    private static final double LABEL_SIDE_GAP = 3.5;
    private static final float LABEL_RADIUS = 1.75f;
    private static final float LABEL_BLUR = 4.6f;
    private static final float LABEL_INNER_ALPHA = 0.18f;
    private static final int LABEL_SHADOW_ALPHA = 170;

    public static TextRenderer labelRenderer(TextRenderer fallback) {
        return Fonts.renderer("OnestMedium", FontInfo.Type.Regular, fallback);
    }

    public static ScreenRect projectEntityBox(Entity entity, Vec3 lerpedPos, float tickDelta, double expandXZ, double expandTop) {
        AABB box = entity.getBoundingBox().move(
                lerpedPos.x - entity.getX(),
                lerpedPos.y - entity.getY(),
                lerpedPos.z - entity.getZ()
        );
        if (expandXZ != 0.0 || expandTop != 0.0) {
            box = new AABB(
                    box.minX - expandXZ,
                    box.minY,
                    box.minZ - expandXZ,
                    box.maxX + expandXZ,
                    box.maxY + expandTop,
                    box.maxZ + expandXZ
            );
        }
        return projectBox(box, tickDelta);
    }

    public static ScreenRect projectBox(AABB box, float tickDelta) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean any = false;

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 screen = ScreenProjection.worldToScreen(new Vec3(x, y, z), tickDelta);
                    if (screen == null) continue;
                    any = true;
                    minX = Math.min(minX, screen.x);
                    minY = Math.min(minY, screen.y);
                    maxX = Math.max(maxX, screen.x);
                    maxY = Math.max(maxY, screen.y);
                }
            }
        }

        if (!any || maxX <= minX || maxY <= minY) return null;
        if (PIXEL_SNAP) {
            minX = Math.floor(minX);
            minY = Math.floor(minY);
            maxX = Math.ceil(maxX);
            maxY = Math.ceil(maxY);
        }
        return new ScreenRect(minX, minY, maxX, maxY);
    }

    public static LabelEntry createCenteredLabel(TextRenderer textRenderer, String text, int color, ScreenRect rect) {
        return createCenteredLabel(textRenderer, text, null, color, color, rect);
    }

    public static LabelEntry createCenteredLabel(TextRenderer textRenderer, String leftText, String rightText,
                                                 int leftColor, int rightColor, ScreenRect rect) {
        String left = RuntimeTextLayout.singleLine(leftText);
        String rightValue = RuntimeTextLayout.singleLine(rightText);
        String right = rightValue.isBlank() ? null : rightValue;
        double leftWidth = Math.max(0.0, textRenderer.getWidth(left, false));
        double rightWidth = right == null ? 0.0 : Math.max(0.0, textRenderer.getWidth(right, false));
        double gap = right == null ? 0.0 : LABEL_SIDE_GAP;
        double totalWidth = leftWidth + gap + rightWidth;
        double height = textRenderer.getHeight(false);
        double x = rect.minX + (rect.width() - totalWidth) * 0.5;
        double y = rect.minY - height - LABEL_MARGIN;
        if (PIXEL_SNAP) {
            x = snapText(x);
            y = snapText(y);
        }
        return new LabelEntry(left, right, x, y, leftColor, rightColor, leftWidth, gap, totalWidth, height);
    }

    public static LabelEntry labelAt(String text, double x, double y, int color) {
        return new LabelEntry(text, null, x, y, color, color, 0.0, 0.0, 0.0, 0.0);
    }

    public static void drawFrame(Renderer2D renderer, ScreenRect rect, int color) {
        drawFrame(renderer, rect.minX, rect.minY, rect.width(), rect.height(), withAlpha(color, FRAME_ALPHA));
    }

    public static void renderLabelBackplate(Renderer2D renderer, LabelEntry label) {
        double x = label.x - LABEL_PAD_X;
        double y = label.y - LABEL_PAD_Y;
        double width = label.totalWidth + LABEL_PAD_X * 2.0;
        double height = label.height + LABEL_PAD_Y * 2.0;
        if (width <= 0.0 || height <= 0.0) return;

        int shadowColor = withAlpha(0x000000, LABEL_SHADOW_ALPHA);
        renderer.roundedRectSoftShadow(x, y, width, height, LABEL_RADIUS, LABEL_BLUR, LABEL_INNER_ALPHA, shadowColor);
    }

    public static void renderLabels(TextRenderer textRenderer, List<LabelEntry> labels, boolean shadow) {
        renderLabels(textRenderer, labels, shadow, 1.0f);
    }

    public static void renderLabels(TextRenderer textRenderer,
                                    List<LabelEntry> labels,
                                    boolean shadow,
                                    float alpha) {
        for (LabelEntry label : labels) {
            double nextX = textRenderer.render(label.leftText, label.x, label.y,
                    new RenderColor(scaleAlpha(label.leftColor, alpha)), shadow);
            if (label.rightText != null) {
                double rightX = label.leftWidth > 0.0 ? label.x + label.leftWidth + label.gap : nextX + label.gap;
                textRenderer.render(label.rightText, rightX, label.y,
                        new RenderColor(scaleAlpha(label.rightColor, alpha)), shadow);
            }
        }
    }

    private static void drawFrame(Renderer2D renderer, double x, double y, double width, double height, int argb) {
        if (width <= 1.0 || height <= 1.0) return;
        double x1 = x;
        double y1 = y;
        double x2 = x + width;
        double y2 = y + height;

        int outline = withAlpha(0x000000, FRAME_OUTLINE_ALPHA);
        drawMonoOutline(renderer, x1 - BOX_OUTLINE_EXPAND, y1 - BOX_OUTLINE_EXPAND, x2 + BOX_OUTLINE_EXPAND, y2 + BOX_OUTLINE_EXPAND, BOX_OUTLINE_THICKNESS, outline);
        drawMonoOutline(renderer, x1, y1, x2, y2, BOX_THICKNESS, argb);
        drawMonoOutline(renderer, x1 + BOX_THICKNESS, y1 + BOX_THICKNESS, x2 - BOX_THICKNESS, y2 - BOX_THICKNESS, BOX_OUTLINE_THICKNESS, outline);
    }

    private static void drawMonoOutline(Renderer2D renderer, double x1, double y1, double x2, double y2, double thickness, int argb) {
        double width = x2 - x1;
        double height = y2 - y1;
        if (width <= 0.0 || height <= 0.0 || thickness <= 0.0) return;

        renderer.quad(x1, y1, width, thickness, argb);
        renderer.quad(x1, y2 - thickness, width, thickness, argb);
        renderer.quad(x1, y1, thickness, height, argb);
        renderer.quad(x2 - thickness, y1, thickness, height, argb);
    }

    private static int withAlpha(int argb, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaleAlpha(int argb, float alpha) {
        int source = (argb >>> 24) & 0xFF;
        return withAlpha(argb, Math.round(source * Math.max(0.0f, Math.min(1.0f, alpha))));
    }

    private static double snapText(double value) {
        return Math.floor(value + 0.5);
    }

    public record ScreenRect(double minX, double minY, double maxX, double maxY) {
        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxY - minY;
        }
    }

    public record LabelEntry(String leftText,
                             String rightText,
                             double x,
                             double y,
                             int leftColor,
                             int rightColor,
                             double leftWidth,
                             double gap,
                             double totalWidth,
                             double height) {
    }
}
