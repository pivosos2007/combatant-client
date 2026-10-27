/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Shared engine for finders that flag whole chunks: scanning, marker spacing, alerts and
 * rendering. The owning module supplies the settings and decides what makes a chunk interesting.
 */
public final class ChunkMarkers<R extends ChunkMarkers.Hit> {

    /** Why a chunk was flagged and the block that proves it. */
    public interface Hit {
        String reason();

        BlockPos evidence();
    }

    /** Settings the owning module declares; read live so edits apply immediately. */
    public record Settings(NumberValue<Integer> scanRadius,
                           NumberValue<Integer> markerY,
                           NumberValue<Integer> minMarkerDistance,
                           BooleanValue tracers,
                           BooleanValue beams,
                           BooleanValue chatAlerts,
                           BooleanValue soundAlerts,
                           RGBAColorValue fillColor,
                           RGBAColorValue lineColor) {
    }

    /** Called on the client thread, a few chunks per tick. Return null when nothing is found. */
    @FunctionalInterface
    public interface Analyzer<R> {
        R analyze(ClientLevel level, LevelChunk chunk);
    }

    public static final int MAX_SCAN_RADIUS = 16;
    private static final double BEAM_HEIGHT = 128.0;
    // Shader surfaces need some height for the rim and scan band to read; the flat plate is only 0.06 thick.
    public static final double SHADER_SLAB_HEIGHT = 1.0;

    private final Minecraft mc = Minecraft.getInstance();
    private final Settings settings;
    private final Supplier<String> alertSource;
    private final Supplier<String> signature;
    private final LoadedChunkScanner<R> scanner;
    // Chunks currently shown, in the order they were accepted.
    private final Map<Long, R> accepted = new LinkedHashMap<>();
    // Survives chunk unloads so walking back and forth does not re-alert the same spot.
    private final Set<Long> alerted = new HashSet<>();
    private String lastSignature = "";
    private ClientLevel lastLevel;

    /**
     * @param signature a value that changes whenever a setting that affects {@code analyzer} changes;
     *                  everything is rescanned when it differs from the previous tick
     */
    public ChunkMarkers(Settings settings, Supplier<String> alertSource, Supplier<String> signature,
                        Analyzer<R> analyzer, int chunksPerTick, long microsPerTick) {
        this.settings = settings;
        this.alertSource = alertSource;
        this.signature = signature;
        this.scanner = new LoadedChunkScanner<>(analyzer::analyze, this::scanRadius, chunksPerTick, microsPerTick);
    }

    private int scanRadius() {
        return Math.min(mc.options.renderDistance().get(), settings.scanRadius().get());
    }

    public void onPacket(Packet<?> packet) {
        scanner.onPacket(packet);
    }

    public void reset() {
        scanner.clear();
        accepted.clear();
        alerted.clear();
        lastLevel = null;
    }

    public void tick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        String current = signature.get();
        if (!current.equals(lastSignature)) {
            lastSignature = current;
            scanner.rescanAll();
            accepted.clear();
        }
        scanner.tick(mc);
        reconcile();
    }

    private void reconcile() {
        Map<Long, R> results = scanner.results();
        accepted.keySet().removeIf(key -> !results.containsKey(key));

        for (Map.Entry<Long, R> entry : results.entrySet()) {
            long key = entry.getKey();
            if (accepted.containsKey(key) || !farEnoughFromAccepted(key)) continue;

            R hit = entry.getValue();
            accepted.put(key, hit);
            if (alerted.add(key)) {
                BlockPos evidence = hit.evidence();
                FinderAlerts.found(alertSource.get(), hit.reason(),
                        evidence.getX(), evidence.getY(), evidence.getZ(),
                        settings.chatAlerts().get(), settings.soundAlerts().get());
            }
        }
    }

    // Several neighbouring chunks usually fire for the same base; one marker per area is
    // enough and keeps the screen readable.
    private boolean farEnoughFromAccepted(long key) {
        int min = settings.minMarkerDistance().get();
        if (min <= 0) return true;
        int x = ChunkPos.getX(key);
        int z = ChunkPos.getZ(key);
        for (long other : accepted.keySet()) {
            int dx = ChunkPos.getX(other) - x;
            int dz = ChunkPos.getZ(other) - z;
            if (dx * dx + dz * dz < min * min) return false;
        }
        return true;
    }

    public void render(Renderer3D renderer, ShaderBoxModule style) {
        if (renderer == null || accepted.isEmpty()) return;

        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        double y = settings.markerY().get();
        int fill = settings.fillColor().getArgb();
        int line = settings.lineColor().getArgb();
        // Shader modes take their alpha from the module's shader settings, so tint with an opaque colour.
        int shaderColor = line | 0xFF000000;
        for (Map.Entry<Long, R> entry : accepted.entrySet()) {
            int chunkX = ChunkPos.getX(entry.getKey());
            int chunkZ = ChunkPos.getZ(entry.getKey());
            double minX = chunkX << 4;
            double minZ = chunkZ << 4;
            AABB slab = new AABB(minX, y, minZ, minX + 16.0, y + SHADER_SLAB_HEIGHT, minZ + 16.0);
            if (!style.drawShaderBox(renderer, slab, shaderColor)) {
                draw.chunkPlate(chunkX, chunkZ, y, fill, line);
            }
            double centerX = minX + 8.0;
            double centerZ = minZ + 8.0;
            if (settings.tracers().get()) draw.tracer(new Vec3(centerX, y, centerZ), line);
            if (settings.beams().get()) draw.beam(centerX, centerZ, y, y + BEAM_HEIGHT, line);
        }
    }
}
