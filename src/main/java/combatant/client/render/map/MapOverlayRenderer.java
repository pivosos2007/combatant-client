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

/** Draws projected grid, marker, label and uncertainty batches through Combatant rendering. */
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
        double thickness = Math.max(0.5, line.thickness());
        if (line.x1() == line.x2()) {
            Renderer2D.COLOR.quad(
                    line.x1() - thickness * 0.5,
                    Math.min(line.y1(), line.y2()),
                    thickness,
                    Math.abs(line.y2() - line.y1()),
                    line.argb()
            );
        } else if (line.y1() == line.y2()) {
            Renderer2D.COLOR.quad(
                    Math.min(line.x1(), line.x2()),
                    line.y1() - thickness * 0.5,
                    Math.abs(line.x2() - line.x1()),
                    thickness,
                    line.argb()
            );
        } else {
            Renderer2D.COLOR.line(line.x1(), line.y1(), line.x2(), line.y2(), line.argb());
        }
    }

    private static void drawEllipse(MapOverlayDrawList.Ellipse ellipse) {
        if (ellipse.radiusX() <= 0.0 || ellipse.radiusY() <= 0.0) return;
        double[] points = new double[ELLIPSE_SEGMENTS * 2];
        for (int i = 0; i < ELLIPSE_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / ELLIPSE_SEGMENTS;
            points[i * 2] = ellipse.centerX() + Math.cos(angle) * ellipse.radiusX();
            points[i * 2 + 1] = ellipse.centerY() + Math.sin(angle) * ellipse.radiusY();
        }
        if (((ellipse.fillArgb() >>> 24) & 0xFF) != 0) {
            Renderer2D.COLOR.polygon(points, ELLIPSE_SEGMENTS, ellipse.fillArgb());
        }
        if (((ellipse.strokeArgb() >>> 24) & 0xFF) != 0) {
            for (int i = 0; i < ELLIPSE_SEGMENTS; i++) {
                int next = (i + 1) % ELLIPSE_SEGMENTS;
                Renderer2D.COLOR.line(
                        points[i * 2], points[i * 2 + 1],
                        points[next * 2], points[next * 2 + 1],
                        ellipse.strokeArgb()
                );
            }
        }
    }
}
