/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.draw;

/**
 * Optional Voronoi structure painted over any material on an eligible UiPrimitive.
 * The structure is NOT a replacement base material: paint/tint and geometry are
 * supplied independently by the caller. Seed is stable across frames and moves.
 */
public record UiCellularStructureSpec(Profile profile, float cellSizePx, float jitter,
                                      float seamWidthPx, float depth, float opacity,
                                      float seed) {
    public static final UiCellularStructureSpec CELLS =
            new UiCellularStructureSpec(Profile.CELLS, 36f, 0.86f, 1.15f, 0.46f, 0.16f, 0f);
    public static final UiCellularStructureSpec CRACKS =
            new UiCellularStructureSpec(Profile.CRACKS, 48f, 0.94f, 0.70f, 0.42f, 0.12f, 0f);
    public static final UiCellularStructureSpec CRYSTALLINE =
            new UiCellularStructureSpec(Profile.CRYSTALLINE, 40f, 0.88f, 0.85f, 0.54f, 0.14f, 0f);
    public static final UiCellularStructureSpec RELIEF =
            new UiCellularStructureSpec(Profile.RELIEF, 28f, 0.74f, 1.65f, 0.56f, 0.13f, 0f);

    public enum Profile { CELLS, CRACKS, CRYSTALLINE, RELIEF }

    public UiCellularStructureSpec {
        profile = profile != null ? profile : Profile.CELLS;
        cellSizePx = finiteClamp(cellSizePx, 8f, 512f);
        jitter = finiteClamp(jitter, 0f, 1f);
        seamWidthPx = finiteClamp(seamWidthPx, 0.15f, 8f);
        depth = finiteClamp(depth, 0f, 1f);
        opacity = finiteClamp(opacity, 0f, 1f);
        seed = Float.isFinite(seed) ? Math.clamp(seed, -65536f, 65536f) : 0f;
    }

    public UiCellularStructureSpec withProfile(Profile p) {
        return new UiCellularStructureSpec(p, cellSizePx, jitter, seamWidthPx, depth, opacity, seed);
    }
    public UiCellularStructureSpec withCellSize(float size) {
        return new UiCellularStructureSpec(profile, size, jitter, seamWidthPx, depth, opacity, seed);
    }
    public UiCellularStructureSpec withJitter(float value) {
        return new UiCellularStructureSpec(profile, cellSizePx, value, seamWidthPx, depth, opacity, seed);
    }
    public UiCellularStructureSpec withSeamWidth(float value) {
        return new UiCellularStructureSpec(profile, cellSizePx, jitter, value, depth, opacity, seed);
    }
    public UiCellularStructureSpec withDepth(float value) {
        return new UiCellularStructureSpec(profile, cellSizePx, jitter, seamWidthPx, value, opacity, seed);
    }
    public UiCellularStructureSpec withOpacity(float value) {
        return new UiCellularStructureSpec(profile, cellSizePx, jitter, seamWidthPx, depth, value, seed);
    }
    public UiCellularStructureSpec withSeed(float value) {
        return new UiCellularStructureSpec(profile, cellSizePx, jitter, seamWidthPx, depth, opacity, value);
    }

    private static float finiteClamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.clamp(v, lo, hi) : lo;
    }
}
