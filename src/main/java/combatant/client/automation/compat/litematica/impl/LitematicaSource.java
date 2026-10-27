/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica.impl;

import combatant.client.automation.compat.litematica.MaterialCount;
import combatant.client.automation.compat.litematica.PlacementInfo;
import combatant.client.automation.compat.litematica.SchematicBox;
import combatant.client.automation.compat.litematica.SchematicSource;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.ChunkSchematicState;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.position.LayerRange;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class that talks to Litematica. Reads its placements, its already transformed schematic world and its
 * render layer range; no schematic file is parsed. Loaded only after {@code isModLoaded("litematica")}.
 */
public final class LitematicaSource implements SchematicSource {

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    @Override
    public boolean isReady() {
        if (SchematicWorldHandler.getSchematicWorld() == null) return false;
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (enabled(placement)) return true;
        }
        return false;
    }

    private static boolean enabled(SchematicPlacement placement) {
        return placement.isEnabled() && placement.isRenderingEnabled();
    }

    @Override
    public long revision() {
        long h = 1125899906842597L;
        h = mix(h, System.identityHashCode(SchematicWorldHandler.getSchematicWorld()));
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            h = mix(h, p.getHashId().hashCode());
            h = mix(h, enabled(p) ? 1 : 0);
            h = mix(h, p.getOrigin().asLong());
            h = mix(h, p.getRotation().ordinal() * 8L + p.getMirror().ordinal());
            for (SubRegionPlacement s : p.getAllSubRegionsPlacements()) {
                h = mix(h, s.isEnabled() ? s.getPos().asLong() : -1L);
                h = mix(h, s.getRotation().ordinal() * 8L + s.getMirror().ordinal());
            }
        }
        LayerRange layer = DataManager.getRenderLayerRange();
        h = mix(h, layer.getLayerMode().ordinal() * 8L + layer.getAxis().ordinal());
        h = mix(h, layer.getLayerSingle());
        h = mix(h, layer.getLayerAbove());
        h = mix(h, layer.getLayerBelow());
        h = mix(h, layer.getLayerRangeMin());
        h = mix(h, layer.getLayerRangeMax());
        return h;
    }

    private static long mix(long h, long v) {
        return (h ^ v) * 1099511628211L + (v >>> 29);
    }

    @Override
    public List<PlacementInfo> placements() {
        List<PlacementInfo> out = new ArrayList<>();
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            out.add(new PlacementInfo(p.getName(), bounds(p), enabled(p)));
        }
        return out;
    }

    private static SchematicBox bounds(SchematicPlacement placement) {
        Box box = placement.getEclosingBox();
        if (box == null || box.getPos1() == null || box.getPos2() == null) return null;
        BlockPos a = box.getPos1();
        BlockPos b = box.getPos2();
        return new SchematicBox(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
    }

    private static List<SchematicBox> enabledBounds() {
        List<SchematicBox> out = new ArrayList<>();
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (!enabled(p)) continue;
            SchematicBox box = bounds(p);
            if (box != null) out.add(box);
        }
        return out;
    }

    @Override
    public boolean overlapsPlacement(SchematicBox box) {
        for (SchematicBox bounds : enabledBounds()) {
            if (bounds.intersects(box)) return true;
        }
        return false;
    }

    @Override
    public boolean isColumnReady(int chunkX, int chunkZ) {
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        if (world == null || world.getChunkSource().getChunkIfExists(chunkX, chunkZ) == null) return false;
        ChunkSchematicState state = world.getChunkSource().getChunkState(chunkX, chunkZ);
        // FILLED is set by Litematica's placing utils (SchematicPlacingUtils/WorldPlacingUtils) once the column's
        // blocks are written into the schematic world; LOADED is already set by loadChunk() while still empty.
        return state != null && state.atLeast(ChunkSchematicState.FILLED);
    }

    @Override
    public void forEachExpected(SchematicBox box, ExpectedVisitor visitor) {
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        if (world == null) return;
        List<SchematicBox> bounds = enabledBounds();
        LayerRange layer = DataManager.getRenderLayerRange();
        for (SchematicBox placed : bounds) {
            SchematicBox part = box.intersection(placed);
            if (part == null) continue;
            for (int x = part.minX(); x <= part.maxX(); x++) {
                for (int z = part.minZ(); z <= part.maxZ(); z++) {
                    for (int y = part.minY(); y <= part.maxY(); y++) {
                        // Overlapping placements: visit a position only from the first one containing it.
                        if (firstContaining(bounds, x, y, z) != placed) continue;
                        if (!layer.isPositionWithinRange(x, y, z)) continue;
                        BlockState state = world.getBlockState(cursor.set(x, y, z));
                        if (!state.isAir()) visitor.visit(x, y, z, state);
                    }
                }
            }
        }
    }

    private static SchematicBox firstContaining(List<SchematicBox> boxes, int x, int y, int z) {
        for (SchematicBox box : boxes) {
            if (box.contains(x, y, z)) return box;
        }
        return null;
    }

    @Override
    public List<MaterialCount> materials() {
        MaterialListBase list = DataManager.getMaterialList();
        List<MaterialCount> out = new ArrayList<>();
        if (list == null) return out;
        for (MaterialListEntry entry : list.getMaterialsAll()) {
            ItemStack stack = entry.getStack().copyWithCount(1);
            out.add(new MaterialCount(stack, entry.getCountTotal(), entry.getCountMissing(), entry.getCountAvailable()));
        }
        return out;
    }
}
