/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ClipFunction;

/** Submits map overlays within the requested clipping rectangle. */
public final class MapOverlayRenderer {
    private static final int ELLIPSE_SEGMENTS = 48;

    public void render(MapRect clip, MapOverlayDrawList drawList, TextRenderer textRenderer) {
        if (clip == null || drawList == null) return;
        try (ClipFunction.Scope ignored = ClipFunction.rectScope(clip.x(), clip.y(), clip.width(), clip.height())) {
            for (MapOverlayDrawList.Line line : drawList.lines()) {
                drawLine(line);
            }
            for (MapOverlayDrawList.Ellipse ellipse : drawList.ellipses()) {
                drawEllipse(ellipse);
            }
            for (MapOverlayDrawList.Circle circle : drawList.circles()) {
                Renderer2D.COLOR.circle(circle.centerX(), circle.centerY(), circle.radius(), circle.fillArgb());
                if (((circle.strokeArgb() >>> 24) & 0xFF) != 0) {
                    Renderer2D.COLOR.circleStroke(circle.centerX(), circle.centerY(), circle.radius(), 1.0, circle.strokeArgb());
                }
            }
            for (MapOverlayDrawList.Player player : drawList.players()) {
                MapPlayerMarkerRenderer.drawMarker((float) player.x(), (float) player.y(), player.playerUuid(), player.playerName(),
                        player.accentArgb(), player.sourceGlyph(), player.sizePixels(), player.alpha());
            }
            if (!drawList.players().isEmpty()) {
                Renderer2D.flushBatch();
                for (MapOverlayDrawList.Player player : drawList.players()) {
                    MapPlayerMarkerRenderer.drawLabel((float) player.x(), (float) player.y(), player.playerUuid(), player.playerName(),
                            player.accentArgb(), player.sizePixels(), player.alpha());
                }
            }
            if (textRenderer != null && !drawList.labels().isEmpty()) {
                boolean building = textRenderer.isBuilding();
                if (!building) textRenderer.begin();
                try {
                    for (MapOverlayDrawList.Text label : drawList.labels()) {
                        double width = textRenderer.getWidth(label.text(), false);
                        double height = textRenderer.getHeight(false);
                        textRenderer.render(
                                label.text(),
                                label.x() - width * 0.5,
                                label.y() - height * 0.5,
                                new RenderColor(label.argb()),
                                false
                        );
                    }
                } finally {
                    if (!building) textRenderer.end();
                }
            }
        }
    }

    private static void drawLine(MapOverlayDrawList.Line line) {
        drawClippableSegment(
                line.x1(), line.y1(), line.x2(), line.y2(),
                Math.max(0.5, line.thickness()), line.argb()
        );
    }

    /**
     * Map overlays render under an analytic ClipFunction snapshot. UiBatchType.LINES intentionally
     * has no analytic-clip pipeline, so using Renderer2D.COLOR.line() here is invalid and crashes
     * UiPassCompiler. Emit the stroke as a thin SHAPE quad instead; SHAPE has an analytic-clip
     * variant and therefore obeys the same map viewport clip as the other overlay primitives.
     */
    private static void drawClippableSegment(double x1, double y1,
                                              double x2, double y2,
                                              double thickness, int argb) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(length) || length <= 1.0e-6 || thickness <= 0.0) return;

        double invLength = 1.0 / length;
        double ux = dx * invLength;
        double uy = dy * invLength;
        double half = thickness * 0.5;

        // Slightly overlap neighbouring ellipse segments so their flat quad caps cannot leave
        // sub-pixel cracks at joins. The viewport analytic clip trims any overlap at its boundary.
        double ex = ux * half;
        double ey = uy * half;
        double nx = -uy * half;
        double ny = ux * half;
        double ax = x1 - ex;
        double ay = y1 - ey;
        double bx = x2 + ex;
        double by = y2 + ey;

        Renderer2D.COLOR.polygon(new double[]{
                ax + nx, ay + ny,
                ax - nx, ay - ny,
                bx - nx, by - ny,
                bx + nx, by + ny
        }, 4, argb);
    }

    private static void drawEllipse(MapOverlayDrawList.Ellipse ellipse) {
        if (ellipse.radiusX() <= 0.0 || ellipse.radiusY() <= 0.0) return;
        double[] points = new double[ELLIPSE_SEGMENTS * 2];
        double rotation = ellipse.angleRadians();
        double cosR = Math.cos(rotation);
        double sinR = Math.sin(rotation);
        for (int i = 0; i < ELLIPSE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / ELLIPSE_SEGMENTS;
            double lx = Math.cos(angle) * ellipse.radiusX();
            double ly = Math.sin(angle) * ellipse.radiusY();
            points[i * 2] = ellipse.centerX() + lx * cosR - ly * sinR;
            points[i * 2 + 1] = ellipse.centerY() + lx * sinR + ly * cosR;
        }
        if (((ellipse.fillArgb() >>> 24) & 0xFF) != 0) {
            Renderer2D.COLOR.polygon(points, ELLIPSE_SEGMENTS, ellipse.fillArgb());
        }
        if (((ellipse.strokeArgb() >>> 24) & 0xFF) != 0) {
            for (int i = 0; i < ELLIPSE_SEGMENTS; i++) {
                int next = (i + 1) % ELLIPSE_SEGMENTS;
                drawClippableSegment(
                        points[i * 2], points[i * 2 + 1],
                        points[next * 2], points[next * 2 + 1],
                        1.0, ellipse.strokeArgb()
                );
            }
        }
    }
}
