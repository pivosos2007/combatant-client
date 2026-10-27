/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * What the Printer needs from a schematic provider. The only implementation lives in
 * {@code compat.litematica.impl}, the single package that references Litematica classes; everything else
 * (scanner, planner, module) talks to this interface so it loads and tests without Litematica present.
 *
 * <p>Expected states come from the provider's already placed and transformed schematic world, never from
 * schematic files. Positions are world coordinates. All methods run on the client thread.</p>
 */
public interface SchematicSource {

    /** Receives one expected, non-air block. */
    @FunctionalInterface
    interface ExpectedVisitor {
        void visit(int x, int y, int z, BlockState expected);
    }

    /** True while at least one enabled placement exists and the provider's schematic world is available. */
    boolean isReady();

    /**
     * Changes whenever the expected content of any position may have changed: placements added, removed, moved,
     * rotated, mirrored or toggled, the layer range moved, the provider's world was recreated. Cheap enough to
     * read every tick.
     */
    long revision();

    /** Every placement, enabled or not. */
    List<PlacementInfo> placements();

    /** Whether any enabled placement's bounds touch the box. */
    boolean overlapsPlacement(SchematicBox box);

    /**
     * Whether the provider has finished materialising the 16x16 column ({@code chunkX}, {@code chunkZ}); content of
     * a column that is not ready yet is incomplete and must not be cached.
     */
    boolean isColumnReady(int chunkX, int chunkZ);

    /**
     * Visits every non-air expected block inside {@code box} that lies within the provider's layer range and an
     * enabled placement.
     */
    void forEachExpected(SchematicBox box, ExpectedVisitor visitor);

    /** The active material list, or an empty list when none was built yet. */
    List<MaterialCount> materials();
}
