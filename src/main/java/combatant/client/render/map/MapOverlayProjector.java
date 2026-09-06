/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Projects immutable world overlays and generates the chunk/region grid draw list. */
public final class MapOverlayProjector {
    public MapOverlayDrawList project(MapViewport viewport,
                                      MapOverlaySnapshot snapshot,
                                      MapGridSpec gridSpec) {
        if (viewport == null) throw new NullPointerException("viewport");
        MapOverlaySnapshot data = snapshot == null ? MapOverlaySnapshot.EMPTY : snapshot;
        MapGridSpec grid = gridSpec == null ? MapGridSpec.DEFAULT : gridSpec;
        List<MapOverlayDrawList.Line> lines = new ArrayList<>();
        List<MapOverlayDrawList.Ellipse> ellipses = new ArrayList<>();
        List<MapOverlayDrawList.Circle> circles = new ArrayList<>();
        List<MapOverlayDrawList.Text> labels = new ArrayList<>();
        List<MapHitIndex.Entry> hits = new ArrayList<>();

        if (grid.enabled()) appendGrid(viewport, grid, lines, labels);
        for (MapUncertainty uncertainty : data.uncertainties()) {
            MapScreenPoint center = viewport.project(uncertainty.centerX(), uncertainty.centerZ());
            double radiusX = uncertainty.radiusX() * viewport.pixelsPerBlock();
            double radiusY = uncertainty.radiusZ() * viewport.pixelsPerBlock();
            MapRect bounds = new MapRect(center.x() - radiusX, center.y() - radiusY, radiusX * 2.0, radiusY * 2.0);
            if (!viewport.screenBounds().intersects(bounds)) continue;
            ellipses.add(new MapOverlayDrawList.Ellipse(
                    uncertainty.id(), center.x(), center.y(), radiusX, radiusY,
                    uncertainty.fillArgb(), uncertainty.strokeArgb(), uncertainty.priority()
            ));
            hits.add(new MapHitIndex.Entry(
                    uncertainty.id(), MapHitIndex.Kind.UNCERTAINTY,
                    center.x(), center.y(), radiusX, radiusY, uncertainty.priority()
            ));
        }
        for (MapMarker marker : data.markers()) {
            MapScreenPoint center = viewport.project(marker.worldX(), marker.worldZ());
            double radius = marker.radiusPixels();
            if (!viewport.screenBounds().intersects(new MapRect(
                    center.x() - radius, center.y() - radius, radius * 2.0, radius * 2.0))) continue;
            circles.add(new MapOverlayDrawList.Circle(
                    marker.id(), center.x(), center.y(), radius,
                    marker.fillArgb(), marker.strokeArgb(), marker.priority()
            ));
            hits.add(new MapHitIndex.Entry(
                    marker.id(), MapHitIndex.Kind.MARKER,
                    center.x(), center.y(), radius, radius, marker.priority()
            ));
        }
        for (MapLabel label : data.labels()) {
            MapScreenPoint point = viewport.project(label.worldX(), label.worldZ());
            if (!viewport.screenBounds().contains(point.x(), point.y())) continue;
            labels.add(new MapOverlayDrawList.Text(
                    label.id(), point.x(), point.y(), label.text(), label.argb(), label.priority()
            ));
        }

        ellipses.sort(Comparator.comparingInt(MapOverlayDrawList.Ellipse::priority));
        circles.sort(Comparator.comparingInt(MapOverlayDrawList.Circle::priority));
        labels.sort(Comparator.comparingInt(MapOverlayDrawList.Text::priority));
        return new MapOverlayDrawList(lines, ellipses, circles, labels, new MapHitIndex(hits));
    }

    private static void appendGrid(MapViewport viewport,
                                   MapGridSpec spec,
                                   List<MapOverlayDrawList.Line> lines,
                                   List<MapOverlayDrawList.Text> labels) {
        long chunk = spec.chunkBlockSpan();
        long region = Math.multiplyExact(chunk, spec.regionChunkSpan());
        boolean showChunks = chunk * viewport.pixelsPerBlock() >= spec.minimumChunkSpacingPixels();
        long step = showChunks ? chunk : region;
        MapRect world = viewport.visibleWorldBounds();
        long firstX = floorMultiple(world.x(), step);
        long firstZ = floorMultiple(world.y(), step);
        int count = 0;

        for (long x = firstX; x <= world.maxX() && count < spec.maxLines(); x += step) {
            MapScreenPoint top = viewport.project(x, world.y());
            boolean major = Math.floorMod(x, region) == 0L;
            lines.add(new MapOverlayDrawList.Line(
                    top.x(), viewport.screenBounds().y(), top.x(), viewport.screenBounds().maxY(),
                    major ? spec.regionLineArgb() : spec.chunkLineArgb(), major ? 1.0 : 0.5
            ));
            if (major) {
                labels.add(new MapOverlayDrawList.Text(
                        "grid-x:" + x,
                        top.x() + 2.0,
                        viewport.screenBounds().y() + 2.0,
                        Long.toString(x),
                        spec.coordinateLabelArgb(),
                        Integer.MIN_VALUE
                ));
            }
            count++;
            if (x > Long.MAX_VALUE - step) break;
        }
        for (long z = firstZ; z <= world.maxY() && count < spec.maxLines(); z += step) {
            MapScreenPoint left = viewport.project(world.x(), z);
            boolean major = Math.floorMod(z, region) == 0L;
            lines.add(new MapOverlayDrawList.Line(
                    viewport.screenBounds().x(), left.y(), viewport.screenBounds().maxX(), left.y(),
                    major ? spec.regionLineArgb() : spec.chunkLineArgb(), major ? 1.0 : 0.5
            ));
            if (major) {
                labels.add(new MapOverlayDrawList.Text(
                        "grid-z:" + z,
                        viewport.screenBounds().x() + 2.0,
                        left.y() + 2.0,
                        Long.toString(z),
                        spec.coordinateLabelArgb(),
                        Integer.MIN_VALUE
                ));
            }
            count++;
            if (z > Long.MAX_VALUE - step) break;
        }
    }

    private static long floorMultiple(double value, long step) {
        double index = Math.floor(value / step);
        if (index < Long.MIN_VALUE / (double) step || index > Long.MAX_VALUE / (double) step) {
            throw new IllegalStateException("Grid coordinate exceeds long range.");
        }
        return (long) index * step;
    }
}
