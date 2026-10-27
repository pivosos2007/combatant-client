/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import combatant.client.config.common.CommonSettingSchemas;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;

/**
 * Base for single-purpose finders that flag whole chunks. The scanning, marker spacing, alerts
 * and rendering live in {@link ChunkMarkers}; subclasses only decide what makes a chunk interesting.
 *
 * <p>Not a module by itself (abstract, no {@code @ModuleInfo}), so discovery skips it.</p>
 */
public abstract class ChunkFinderModule<R extends ChunkMarkers.Hit> extends ShaderBoxModule {

    private final ChunkMarkers<R> markers;
    private final Object packetListener = new PacketListener();

    protected ChunkFinderModule(int defaultMinMarkerDistance, String defaultFillHex, String defaultLineHex,
                                int chunksPerTick, long microsPerTick) {
        super(RenderMode.BOX, RenderMode.BOX, RenderMode.GLOW, RenderMode.SHADER);
        ChunkMarkers.Settings settings = new ChunkMarkers.Settings(
                numCommon("scan_radius", CommonSettingSchemas.FINDER_SCAN_RADIUS,
                        ChunkMarkers.MAX_SCAN_RADIUS, 2, ChunkMarkers.MAX_SCAN_RADIUS),
                numCommon("marker_y", CommonSettingSchemas.FINDER_MARKER_Y, 63, -64, 320),
                numCommon("min_marker_distance", CommonSettingSchemas.FINDER_MIN_MARKER_DISTANCE,
                        defaultMinMarkerDistance, 0, 48),
                boolCommon("tracers", CommonSettingSchemas.FINDER_TRACERS, true),
                // A plate at one height is easy to miss from above or below; a beam is visible anywhere.
                boolCommon("beams", CommonSettingSchemas.FINDER_BEAMS, false),
                boolCommon("chat_alerts", CommonSettingSchemas.FINDER_CHAT_ALERTS, true),
                boolCommon("sound_alerts", CommonSettingSchemas.FINDER_SOUND_ALERTS, true),
                color("fill_color", defaultFillHex),
                color("line_color", defaultLineHex));
        this.markers = new ChunkMarkers<>(settings, this::getDisplayName, this::analysisSignature,
                this::analyzeChunk, chunksPerTick, microsPerTick);
    }

    /** Called on the client thread, a few chunks per tick. Return null when nothing is found. */
    protected abstract R analyzeChunk(ClientLevel level, LevelChunk chunk);

    /**
     * A value that changes whenever a setting that affects {@link #analyzeChunk} changes.
     * The base class rescans everything when it differs from the previous tick.
     */
    protected String analysisSignature() {
        return "";
    }

    @Override
    public void onEnable() {
        markers.reset();
        Events.BUS.registerOwned(this, packetListener);
    }

    @Override
    public void onDisable() {
        Events.BUS.unregister(packetListener);
        markers.reset();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        markers.tick();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled()) return;
        markers.render(renderer, this);
    }

    private final class PacketListener {
        @EventHandler
        private void onPacketReceive(PacketEvent.Receive event) {
            markers.onPacket(event.getPacket());
        }
    }
}
