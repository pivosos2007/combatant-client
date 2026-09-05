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
import java.util.Optional;

/** Immutable priority-ordered hit-test structure for projected map items. */
public final class MapHitIndex {
    public static final MapHitIndex EMPTY = new MapHitIndex(List.of());

    private final List<Entry> entries;

    public MapHitIndex(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries == null ? List.of() : entries);
        copy.sort(Comparator.comparingInt(Entry::priority).reversed());
        this.entries = List.copyOf(copy);
    }

    public Optional<Entry> hit(double x, double y) {
        for (Entry entry : entries) {
            if (entry.contains(x, y)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(String id,
                        Kind kind,
                        double centerX,
                        double centerY,
                        double radiusX,
                        double radiusY,
                        int priority) {
        public Entry {
            if (id == null || kind == null) throw new NullPointerException();
            if (radiusX < 0.0 || radiusY < 0.0) throw new IllegalArgumentException("Hit radii cannot be negative.");
        }

        public boolean contains(double x, double y) {
            if (radiusX == 0.0 || radiusY == 0.0) return false;
            double nx = (x - centerX) / radiusX;
            double ny = (y - centerY) / radiusY;
            return nx * nx + ny * ny <= 1.0;
        }
    }

    public enum Kind {
        MARKER,
        UNCERTAINTY
    }
}
