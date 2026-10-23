/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic.view3d;

/**
 * What the mesher needs to know about one block state: whether it is a plain cube (drawn as box faces from the
 * block atlas and culled against neighbours), whether it hides the faces behind it, and otherwise the baked model
 * quads in block-local space. Quad arrays follow the layout of {@code SchematicGhostModel}.
 */
public final class BlockShape {

    /** Full opaque cube without model quads; what every block is at coarse level of detail. */
    public static final BlockShape CUBE = new BlockShape(true, true, 0, new float[0], new float[0], new float[0], new int[0], new byte[0]);

    final boolean cube;
    final boolean occludes;
    final int quadCount;
    /** 12 floats per quad: four xyz corners. */
    final float[] pos;
    /** 8 floats per quad: four atlas uv pairs. */
    final float[] uv;
    /** 3 floats per quad: unit front normal. */
    final float[] normal;
    /** Multiply tint per quad, {@code 0xAARRGGBB}. */
    final int[] tint;
    /** {@link FaceList} direction of the block side the quad lies flat on, or -1. */
    final byte[] cull;

    public BlockShape(boolean cube, boolean occludes, int quadCount, float[] pos, float[] uv, float[] normal, int[] tint, byte[] cull) {
        this.cube = cube;
        this.occludes = occludes;
        this.quadCount = quadCount;
        this.pos = pos;
        this.uv = uv;
        this.normal = normal;
        this.tint = tint;
        this.cull = cull;
    }
}
