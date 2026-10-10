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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Shared plumbing for finders that flag whole chunks: scanning, marker spacing, alerts and
 * rendering. Subclasses only decide what makes a chunk interesting.
 *
 * <p>Not a module by itself (abstract, no {@code @ModuleInfo}), so discovery skips it.</p>
 */
public abstract class ChunkFinderModule<R extends ChunkFinderModule.Hit> extends Module {

    /** Why a chunk was flagged and the block that proves it. */
    public interface Hit {
        String reason();

        BlockPos evidence();
    }

    private static final int MAX_SCAN_RADIUS = 16;
    private static final double BEAM_HEIGHT = 128.0;

    protected final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> scanRadius;
    private final NumberValue<Integer> markerY;
    private final NumberValue<Integer> minMarkerDistance;
    private final BooleanValue tracers;
    private final BooleanValue beams;
    private final BooleanValue chatAlerts;
    private final BooleanValue soundAlerts;
    private final RGBAColorValue fillColor;
    private final RGBAColorValue lineColor;

    private final LoadedChunkScanner<R> scanner;
    // Chunks currently shown, in the order they were accepted.
    private final Map<Long, R> accepted = new LinkedHashMap<>();
    // Survives chunk unloads so walking back and forth does not re-alert the same spot.
    private final Set<Long> alerted = new HashSet<>();
    private final Object packetListener = new PacketListener();
    private String lastSignature = "";
    private ClientLevel lastLevel;

    protected ChunkFinderModule(int defaultMinMarkerDistance, String defaultFillHex, String defaultLineHex,
                                int chunksPerTick, long microsPerTick) {
        this.scanRadius = num("scan_radius", MAX_SCAN_RADIUS, 2, MAX_SCAN_RADIUS);
        this.markerY = num("marker_y", 63, -64, 320);
        this.minMarkerDistance = num("min_marker_distance", defaultMinMarkerDistance, 0, 48);
        this.tracers = bool("tracers", true);
        // A plate at one height is easy to miss from above or below; a beam is visible anywhere.
        this.beams = bool("beams", false);
        this.chatAlerts = bool("chat_alerts", true);
        this.soundAlerts = bool("sound_alerts", true);
        this.fillColor = color("fill_color", defaultFillHex);
        this.lineColor = color("line_color", defaultLineHex);
        this.scanner = new LoadedChunkScanner<>(this::analyzeChunk, this::scanRadius, chunksPerTick, microsPerTick);
    }

    /** Called on the client thread, a few chunks per tick. Return null when nothing is found. */
    protected abstract R analyzeChunk(ClientLevel level, LevelChunk chunk);

    protected int scanRadius() {
        return Math.min(mc.options.renderDistance().get(), scanRadius.get());
    }

    /** Colour for one marker; subclasses can colour by reason. */
    protected int fillFor(R hit) {
        return fillColor.getArgb();
    }

    protected int lineFor(R hit) {
        return lineColor.getArgb();
    }

    /**
     * A value that changes whenever a setting that affects {@link #analyzeChunk} changes.
     * The base class rescans everything when it differs from the previous tick.
     */
    protected String analysisSignature() {
        return "";
    }

    private void rescan() {
        scanner.rescanAll();
        accepted.clear();
    }

    @Override
    public void onEnable() {
        resetState();
        Events.BUS.registerOwned(this, packetListener);
    }

    @Override
    public void onDisable() {
        Events.BUS.unregister(packetListener);
        resetState();
    }

    private void resetState() {
        scanner.clear();
        accepted.clear();
        alerted.clear();
        lastLevel = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        if (mc.level != lastLevel) {
            resetState();
            lastLevel = mc.level;
        }
        String signature = analysisSignature();
        if (!signature.equals(lastSignature)) {
            lastSignature = signature;
            rescan();
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
                FinderAlerts.found(getDisplayName(), hit.reason(),
                        evidence.getX(), evidence.getY(), evidence.getZ(),
                        chatAlerts.get(), soundAlerts.get());
            }
        }
    }

    // Several neighbouring chunks usually fire for the same base; one marker per area is
    // enough and keeps the screen readable.
    private boolean farEnoughFromAccepted(long key) {
        int min = minMarkerDistance.get();
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

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || accepted.isEmpty()) return;

        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        double y = markerY.get();
        for (Map.Entry<Long, R> entry : accepted.entrySet()) {
            int chunkX = ChunkPos.getX(entry.getKey());
            int chunkZ = ChunkPos.getZ(entry.getKey());
            R hit = entry.getValue();
            draw.chunkPlate(chunkX, chunkZ, y, fillFor(hit), lineFor(hit));
            double centerX = (chunkX << 4) + 8.0;
            double centerZ = (chunkZ << 4) + 8.0;
            if (tracers.get()) draw.tracer(new Vec3(centerX, y, centerZ), lineFor(hit));
            if (beams.get()) draw.beam(centerX, centerZ, y, y + BEAM_HEIGHT, lineFor(hit));
        }
    }

    private final class PacketListener {
        @EventHandler
        private void onPacketReceive(PacketEvent.Receive event) {
            scanner.onPacket(event.getPacket());
        }
    }
}
