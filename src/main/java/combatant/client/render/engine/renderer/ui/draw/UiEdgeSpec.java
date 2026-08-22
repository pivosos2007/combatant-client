/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.draw;

/**
 * Per-side box modifier. offset is measured from the side start in clockwise
 * order. For centered notches use UiEdgeSpec.notchedCenter(...).
 */
public record UiEdgeSpec(UiEdgeKind kind,
                         float offset,
                         float size,
                         float depth) {
    public static final UiEdgeSpec STRAIGHT = new UiEdgeSpec(UiEdgeKind.STRAIGHT, 0f, 0f, 0f);

    public UiEdgeSpec {
        kind = kind != null ? kind : UiEdgeKind.STRAIGHT;
        size = Math.max(0f, size);
        depth = Math.max(0f, depth);
    }

    public static UiEdgeSpec straight() {
        return STRAIGHT;
    }

    public static UiEdgeSpec notched(double offset, double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.NOTCHED, (float) offset, (float) width, (float) depth);
    }

    public static UiEdgeSpec notchedCenter(double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.NOTCHED, -1f, (float) width, (float) depth);
    }

    public static UiEdgeSpec inset(double depth) {
        return new UiEdgeSpec(UiEdgeKind.INSET, 0f, 0f, (float) depth);
    }

    /** Triangular inward cut on one side. */
    public static UiEdgeSpec cut(double offset, double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.CUT, (float) offset, (float) width, (float) depth);
    }

    public static UiEdgeSpec cutCenter(double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.CUT, -1f, (float) width, (float) depth);
    }

    /** Rectangular tab extending out of the panel rather than cutting into it. */
    public static UiEdgeSpec protrusion(double offset, double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.PROTRUSION, (float) offset, (float) width, (float) depth);
    }

    public static UiEdgeSpec protrusionCenter(double width, double depth) {
        return new UiEdgeSpec(UiEdgeKind.PROTRUSION, -1f, (float) width, (float) depth);
    }

    public boolean isStraight() {
        return kind == UiEdgeKind.STRAIGHT || (size <= 0.0001f && depth <= 0.0001f);
    }
}
