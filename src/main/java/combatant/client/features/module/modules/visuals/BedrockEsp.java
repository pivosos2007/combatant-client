/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.world.BedrockGaps;
import combatant.client.util.world.BedrockGaps.Cell;
import combatant.client.util.world.BedrockGaps.Gap;
import combatant.client.util.world.BedrockGaps.Side;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "bedrockesp",
        displayName = "BedrockESP",
        aliases = {"bedrockholes"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.bedrockesp.description")
public final class BedrockEsp extends Module {
    private static final int SCANS_PER_PASS = 2;
    private static final int PASS_TICKS = 4;
    private static final int REFRESH_TICKS = 600;

    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Integer> rangeChunks = num("bedrockesp_range_chunks", "range_chunks", 6, 1, 16);
    private final NumberValue<Integer> thickness = num("bedrockesp_thickness", "thickness", 5, 1, 5);
    private final BooleanValue floor = bool("bedrockesp_floor", "floor", true);
    private final BooleanValue roof = bool("bedrockesp_roof", "roof", true);
    private final BooleanValue throughWalls = bool("bedrockesp_through_walls", "through_walls", true);
    private final RGBAColorValue holeColor = color("bedrockesp_hole_color", "hole_color", "#80FF3B30");
    private final RGBAColorValue shaftColor = color("bedrockesp_shaft_color", "shaft_color", "#A0FFD60A");

    private record ChunkGaps(List<Gap> gaps, int scannedTick) {
    }

    private final Long2ObjectOpenHashMap<ChunkGaps> cache = new Long2ObjectOpenHashMap<>();
    private ClientLevel lastLevel;
    private int ticks;

    @Override
    public void onDisable() {
        cache.clear();
        lastLevel = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        ticks++;
        if (mc.level != lastLevel) {
            cache.clear();
            lastLevel = mc.level;
        }
        if (ticks % PASS_TICKS != 0) return;

        int pcx = mc.player.chunkPosition().x();
        int pcz = mc.player.chunkPosition().z();
        int r = rangeChunks.get();

        if (ticks % 40 == 0) {
            cache.long2ObjectEntrySet().removeIf(e -> {
                int cx = ChunkPos.getX(e.getLongKey());
                int cz = ChunkPos.getZ(e.getLongKey());
                return Math.abs(cx - pcx) > r + 2 || Math.abs(cz - pcz) > r + 2;
            });
        }

        for (int scans = 0; scans < SCANS_PER_PASS; scans++) {
            long best = Long.MIN_VALUE;
            int bestDist = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    long key = ChunkPos.pack(pcx + dx, pcz + dz);
                    ChunkGaps cached = cache.get(key);
                    if (cached != null && ticks - cached.scannedTick() < REFRESH_TICKS) continue;
                    int dist = dx * dx + dz * dz;
                    if (dist < bestDist && mc.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz) != null) {
                        bestDist = dist;
                        best = key;
                    }
                }
            }
            if (bestDist == Integer.MAX_VALUE) break;
            cache.put(best, new ChunkGaps(scanChunk(mc.level, ChunkPos.getX(best), ChunkPos.getZ(best)), ticks));
        }
    }

    private List<Gap> scanChunk(ClientLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        List<Gap> out = new ArrayList<>();
        if (chunk == null) return out;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BedrockGaps.Grid grid = (x, y, z) -> {
            if (y < level.getMinY() || y > level.getMaxY()) return Cell.UNKNOWN;
            pos.set(x, y, z);
            return chunk.getBlockState(pos).is(Blocks.BEDROCK) ? Cell.BEDROCK : Cell.OTHER;
        };
        int x0 = cx << 4;
        int z0 = cz << 4;
        int layers = thickness.get();
        if (floor.get() && level.dimension() != Level.END) {
            out.addAll(BedrockGaps.scan(grid, x0, z0, x0 + 15, z0 + 15, level.getMinY(), layers, Side.FLOOR));
        }
        if (roof.get() && level.dimension() == Level.NETHER) {
            out.addAll(BedrockGaps.scan(grid, x0, z0, x0 + 15, z0 + 15, level.getMaxY(), layers, Side.ROOF));
        }
        return out;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.player == null || cache.isEmpty()) return;
        double maxSq = Math.pow(rangeChunks.get() * 16.0, 2);
        Renderer3D.DepthMode depth = throughWalls.get() ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN;
        for (ChunkGaps entry : cache.values()) {
            for (Gap gap : entry.gaps()) {
                double dx = gap.x() + 0.5 - mc.player.getX();
                double dz = gap.z() + 0.5 - mc.player.getZ();
                if (dx * dx + dz * dz > maxSq) continue;
                AABB box = new AABB(gap.x(), gap.y(), gap.z(), gap.x() + 1, gap.y() + 1, gap.z() + 1);
                if (!Renderer3D.Culling.isInFrustum(box)) continue;
                int argb = gap.kind() == BedrockGaps.Kind.SHAFT ? shaftColor.getArgb() : holeColor.getArgb();
                renderer.filledBox(box, argb, depth);
                renderer.outlineBox(box, argb | 0xFF000000, 1.5f, depth);
            }
        }
    }
}
