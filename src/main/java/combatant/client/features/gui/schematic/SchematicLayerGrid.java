/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicAttention;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Consumer;

/**
 * Top-down picture of one schematic layer. Cells of the same colour on a row are merged into runs and the runs
 * are cached per (schematic, layer, sampling step, diff revision), so a frame draws a few hundred quads instead
 * of one per block. Large builds are sampled at a coarser step once a cell would be smaller than a pixel.
 */
final class SchematicLayerGrid {

    private static final float MIN_CELL = 1.5f;
    // The diff revision changes in bursts while the world scan runs; re-tinting on every frame of that is waste.
    private static final long TINT_REFRESH_NANOS = 250_000_000L;
    /** Cell status for a block the builder could not make exactly; drawn in the warning colour. */
    private static final int ATTENTION = 8;
    /** Layers with more filled cells than this, or cells smaller than the minimum, use cached averaged colours. */
    private static final int MAX_TEXTURED_CELLS = 1500;
    private static final float MIN_TEXTURED_CELL = 5f;

    private Schematic paletteOwner;
    private int[] paletteColors = new int[0];
    private int[] flatColors = new int[0];
    private SchematicTextures.Entry[] textures = new SchematicTextures.Entry[0];
    private int texturesGeneration = -1;
    private boolean cachedTextured;
    private int cachedTexturesGeneration = -1;

    private Schematic cachedSchematic;
    private int cachedLayer = -1;
    private int cachedStep = -1;
    private SchematicDiff cachedDiff;
    private int cachedDiffRevision = -1;
    private int cachedAttentionRevision = -1;
    private long lastBuildNanos;

    private int[] runX = new int[256];
    private int[] runZ = new int[256];
    private int[] runLength = new int[256];
    private int[] runColor = new int[256];
    private int runCount;
    private int[] cellStatus = new int[0];

    private int[] cellX = new int[256];
    private int[] cellZ = new int[256];
    private float[] cellUv = new float[256 * 4];
    private int[] cellColor = new int[256];
    private int cellCount;
    private float drawAlpha;
    private final Consumer<Renderer2D.TexturedQuadBatch> drawCells = this::drawCells;

    float originX;
    float originY;
    float cellSize;
    int step = 1;

    void layout(Schematic schematic, float x, float y, float w, float h) {
        float raw = Math.min(w / schematic.sizeX(), h / schematic.sizeZ());
        step = raw >= MIN_CELL ? 1 : (int) Math.ceil(MIN_CELL / Math.max(raw, 1.0e-4f));
        cellSize = raw;
        originX = x + (w - raw * schematic.sizeX()) * 0.5f;
        originY = y + (h - raw * schematic.sizeZ()) * 0.5f;
    }

    void render(Schematic schematic, int layer, SchematicDiff diff, float alpha) {
        ensurePalette(schematic);
        ensureRuns(schematic, layer, diff);
        float cs = cellSize;
        Renderer2D.COLOR.quad(originX, originY, cs * schematic.sizeX(), cs * schematic.sizeZ(),
                SchematicUi.withAlpha(SchematicUi.WELL, Math.round(alpha * 200f)));
        for (int i = 0; i < runCount; i++) {
            float x = originX + runX[i] * cs;
            float y = originY + runZ[i] * cs;
            float w = Math.min(runLength[i], schematic.sizeX() - runX[i]) * cs;
            float h = Math.min(step, schematic.sizeZ() - runZ[i]) * cs;
            Renderer2D.COLOR.quad(x, y, w, h, SchematicUi.scaleAlpha(runColor[i], alpha));
        }
        GpuTextureView view = SchematicTextures.view();
        if (cellCount > 0 && view != null && SchematicTextures.sampler() != null) {
            drawAlpha = alpha;
            Renderer2D.COLOR.texturedQuadBatch(view, SchematicTextures.sampler(), drawCells);
        }
        if (cs >= 9f) drawGridLines(schematic, alpha);
    }

    private void drawGridLines(Schematic schematic, float alpha) {
        int line = SchematicUi.withAlpha(0xFFFFFF, Math.round(alpha * 14f));
        float w = cellSize * schematic.sizeX();
        float h = cellSize * schematic.sizeZ();
        for (int x = 1; x < schematic.sizeX(); x++) {
            Renderer2D.COLOR.quad(originX + x * cellSize, originY, 1f, h, line);
        }
        for (int z = 1; z < schematic.sizeZ(); z++) {
            Renderer2D.COLOR.quad(originX, originY + z * cellSize, w, 1f, line);
        }
    }

    /** Schematic cell under a fixed-space point, as {x, z}, or null outside the grid. */
    int[] cellAt(Schematic schematic, float mx, float my) {
        if (cellSize <= 0f) return null;
        int x = (int) Math.floor((mx - originX) / cellSize);
        int z = (int) Math.floor((my - originY) / cellSize);
        if (x < 0 || z < 0 || x >= schematic.sizeX() || z >= schematic.sizeZ()) return null;
        return new int[]{x, z};
    }

    int paletteColor(Schematic schematic, int paletteIndex) {
        ensurePalette(schematic);
        return paletteIndex >= 0 && paletteIndex < paletteColors.length ? paletteColors[paletteIndex] : 0;
    }

    /** Map colour per palette index, shared with the 3D view so both pictures agree. */
    int[] paletteColors(Schematic schematic) {
        ensurePalette(schematic);
        return paletteColors;
    }

    private void ensurePalette(Schematic schematic) {
        int generation = SchematicTextures.generation();
        if (paletteOwner == schematic && generation == texturesGeneration) return;
        if (paletteOwner != schematic) {
            paletteOwner = schematic;
            paletteColors = new int[schematic.paletteSize()];
            for (int i = 1; i < paletteColors.length; i++) {
                paletteColors[i] = colorOf(schematic.paletteState(i));
            }
        }
        texturesGeneration = generation;
        textures = SchematicTextures.resolvePalette(schematic);
        // Flat fallback colour: the atlas average of the block's top sprite, else its map colour.
        flatColors = new int[paletteColors.length];
        for (int i = 1; i < flatColors.length; i++) {
            int average = textures[i].average;
            if (average == 0) {
                flatColors[i] = paletteColors[i];
            } else {
                int opaque = 0xFF000000 | average;
                flatColors[i] = RedstoneRules.isComponent(schematic.paletteState(i))
                        ? SchematicUi.mix(opaque, SchematicUi.REDSTONE, 0.45f) : opaque;
            }
        }
    }

    static int colorOf(BlockState state) {
        int base = baseColorOf(state);
        // Redstone parts lean red in both the layer grid and the 3D model, so a machine's wiring reads at a glance.
        return RedstoneRules.isComponent(state) ? SchematicUi.mix(base, SchematicUi.REDSTONE, 0.45f) : base;
    }

    private static int baseColorOf(BlockState state) {
        try {
            MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (color != null && color != MapColor.NONE && color.col != 0) return 0xFF000000 | color.col;
        } catch (RuntimeException ignored) {
            // Some blocks read neighbours for their map colour; fall back to a stable hash colour.
        }
        int hash = BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode();
        int r = 96 + (hash & 0x7F), g = 96 + ((hash >>> 8) & 0x7F), b = 96 + ((hash >>> 16) & 0x7F);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void ensureRuns(Schematic schematic, int layer, SchematicDiff diff) {
        int revision = diff == null ? -1 : diff.revision();
        int attentionRevision = SchematicState.INSTANCE.attentionRevision();
        boolean textured = step == 1 && cellSize >= MIN_TEXTURED_CELL && SchematicTextures.view() != null;
        boolean sameShape = schematic == cachedSchematic && layer == cachedLayer && step == cachedStep && diff == cachedDiff
                && textured == cachedTextured && texturesGeneration == cachedTexturesGeneration;
        if (sameShape && revision == cachedDiffRevision && attentionRevision == cachedAttentionRevision) return;
        long now = System.nanoTime();
        if (sameShape && now - lastBuildNanos < TINT_REFRESH_NANOS) return;
        cachedSchematic = schematic;
        cachedLayer = layer;
        cachedStep = step;
        cachedDiff = diff;
        cachedDiffRevision = revision;
        cachedAttentionRevision = attentionRevision;
        cachedTextured = textured;
        cachedTexturesGeneration = texturesGeneration;
        lastBuildNanos = now;
        fillStatus(schematic, layer, diff);
        buildRuns(schematic, layer);
    }

    /** Status per cell of this layer, or all zero without a placement. */
    private void fillStatus(Schematic schematic, int layer, SchematicDiff diff) {
        int area = schematic.layerArea();
        if (cellStatus.length < area) cellStatus = new int[area];
        java.util.Arrays.fill(cellStatus, 0, area, 0);
        if (diff == null || diff.schematic() != schematic) return;
        int base = layer * area;
        for (int cell = diff.layerStart(layer), end = diff.layerEnd(layer); cell < end; cell++) {
            cellStatus[diff.schematicIndex(cell) - base] = diff.status(cell);
        }
        SchematicPlacement placement = diff.placement();
        for (SchematicAttention.Entry entry : SchematicState.INSTANCE.attention()) {
            BlockPos pos = entry.pos();
            if (pos == null || placement.localY(pos.getY()) != layer) continue;
            int x = placement.localX(pos.getX(), pos.getZ());
            int z = placement.localZ(pos.getX(), pos.getZ());
            if (schematic.contains(x, layer, z)) cellStatus[z * schematic.sizeX() + x] = ATTENTION;
        }
    }

    private void buildRuns(Schematic schematic, int layer) {
        runCount = 0;
        cellCount = 0;
        int sizeX = schematic.sizeX();
        int sizeZ = schematic.sizeZ();
        int base = layer * schematic.layerArea();
        if (cachedTextured) {
            int filled = 0;
            for (int i = 0, n = schematic.layerArea(); i < n && filled <= MAX_TEXTURED_CELLS; i++) {
                if (schematic.paletteIndexAt(base + i) != 0) filled++;
            }
            // Past the cap a textured quad per block costs more than it shows: use cached averaged colours.
            if (filled <= MAX_TEXTURED_CELLS) {
                buildTexturedCells(schematic, layer);
                return;
            }
        }
        for (int z = 0; z < sizeZ; z += step) {
            int runStart = -1;
            int runColour = 0;
            for (int x = 0; x < sizeX; x += step) {
                int colour = sampleColor(schematic, base, x, z, sizeX, sizeZ);
                if (colour != runColour) {
                    if (runColour != 0) addRun(runStart, z, x - runStart, runColour);
                    runStart = x;
                    runColour = colour;
                }
            }
            if (runColour != 0) addRun(runStart, z, sizeX - runStart, runColour);
        }
    }

    private int sampleColor(Schematic schematic, int base, int x, int z, int sizeX, int sizeZ) {
        for (int dz = 0; dz < step && z + dz < sizeZ; dz++) {
            for (int dx = 0; dx < step && x + dx < sizeX; dx++) {
                int local = (z + dz) * sizeX + x + dx;
                int palette = schematic.paletteIndexAt(base + local);
                if (palette != 0) return tint(flatColors[palette], cellStatus[local]);
            }
        }
        return 0;
    }

    /** One quad per block from the top-face sprite; blocks without a sprite fall back to a flat averaged cell. */
    private void buildTexturedCells(Schematic schematic, int layer) {
        int sizeX = schematic.sizeX();
        int sizeZ = schematic.sizeZ();
        int base = layer * schematic.layerArea();
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int local = z * sizeX + x;
                int palette = schematic.paletteIndexAt(base + local);
                if (palette == 0) continue;
                int status = cellStatus[local];
                SchematicTextures.Entry entry = textures[palette];
                int light = tint(SchematicTextures.WHITE, status);
                boolean drawn = false;
                for (int layerIndex = 0; layerIndex < SchematicTextures.LAYERS; layerIndex++) {
                    int slot = SchematicTextures.UP_SLOT + layerIndex;
                    if (!entry.has[slot]) continue;
                    addCell(x, z, entry.uv, slot, SchematicTextures.multiply(light, entry.tint[slot]));
                    drawn = true;
                }
                if (!drawn) addRun(x, z, 1, tint(flatColors[palette], status));
            }
        }
    }

    private void addCell(int x, int z, float[] uv, int slot, int color) {
        if (cellCount == cellX.length) {
            int next = cellX.length * 2;
            cellX = java.util.Arrays.copyOf(cellX, next);
            cellZ = java.util.Arrays.copyOf(cellZ, next);
            cellUv = java.util.Arrays.copyOf(cellUv, next * 4);
            cellColor = java.util.Arrays.copyOf(cellColor, next);
        }
        cellX[cellCount] = x;
        cellZ[cellCount] = z;
        System.arraycopy(uv, slot * 4, cellUv, cellCount * 4, 4);
        cellColor[cellCount] = color;
        cellCount++;
    }

    private void drawCells(Renderer2D.TexturedQuadBatch batch) {
        float cs = cellSize;
        for (int i = 0; i < cellCount; i++) {
            batch.quad(originX + cellX[i] * cs, originY + cellZ[i] * cs, cs, cs,
                    cellUv[i * 4], cellUv[i * 4 + 1], cellUv[i * 4 + 2], cellUv[i * 4 + 3],
                    SchematicUi.scaleAlpha(cellColor[i], drawAlpha));
        }
    }

    private static int tint(int color, int status) {
        return switch (status) {
            case SchematicDiff.CORRECT -> SchematicUi.mix(color, SchematicUi.GOOD, 0.55f);
            case SchematicDiff.MISSING -> SchematicUi.mix(color, SchematicUi.BAD, 0.5f);
            case SchematicDiff.WRONG -> SchematicUi.mix(color, SchematicUi.WARN, 0.6f);
            case ATTENTION -> SchematicUi.mix(color, SchematicUi.WARN, 0.85f);
            default -> color;
        };
    }

    private void addRun(int x, int z, int length, int color) {
        if (runCount == runX.length) {
            int next = runX.length * 2;
            runX = java.util.Arrays.copyOf(runX, next);
            runZ = java.util.Arrays.copyOf(runZ, next);
            runLength = java.util.Arrays.copyOf(runLength, next);
            runColor = java.util.Arrays.copyOf(runColor, next);
        }
        runX[runCount] = x;
        runZ[runCount] = z;
        runLength[runCount] = length;
        runColor[runCount] = color;
        runCount++;
    }
}
