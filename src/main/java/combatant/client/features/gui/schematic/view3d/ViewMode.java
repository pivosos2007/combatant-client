/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/** How the 3D schematic view treats every layer relative to the selected one. */
public enum ViewMode {
    SOLID_BELOW,
    LAYER_ONLY,
    ALL,
    EXPLODED;

    public static final byte HIDDEN = 0;
    public static final byte BELOW = 1;
    public static final byte SELECTED = 2;
    public static final byte GHOST = 3;
    public static final byte OTHER = 4;

    private static final ViewMode[] VALUES = values();

    public byte classOf(int y, int selected) {
        if (y == selected) return SELECTED;
        return switch (this) {
            case SOLID_BELOW -> y < selected ? BELOW : GHOST;
            case LAYER_ONLY -> HIDDEN;
            case ALL, EXPLODED -> OTHER;
        };
    }

    /** Ghost layers are drawn for context only, so the cursor and face culling look straight through them. */
    public static boolean pickable(byte cls) {
        return cls != HIDDEN && cls != GHOST;
    }

    public boolean exploded() {
        return this == EXPLODED;
    }

    /** World-space bottom of layer {@code y}; exploded layers are pushed apart by {@code gap} blocks each. */
    public float layerBase(int y, float gap) {
        return this == EXPLODED ? y * (1f + gap) : y;
    }

    public float layerPitch(float gap) {
        return this == EXPLODED ? 1f + gap : 1f;
    }

    public ViewMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }
}
