/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.LevelEvent;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.finder.FinderRender;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Shows where other players are working underground, from packets the server sends anyway:
 * block-break progress, break effects and block changes below a Y level.
 *
 * <p>Combines NeonChunkFinder's "packet leaks" and WaterClient's ActivityDebug, using the
 * typed packets instead of reflecting over every packet's fields.</p>
 */
@ModuleInfo(
        id = "activityfinder",
        displayName = "ActivityFinder",
        aliases = {"ActivityDebug", "MiningDetector"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.activityfinder.description")
public final class ActivityFinder extends Module {

    private static final String MINING = "mining_progress";
    private static final String BREAKS = "block_breaks";
    // Plain block updates include water flow, redstone and your own changes, so this is noisy.
    private static final String UPDATES = "block_updates";

    private final NumberValue<Integer> maxY = num("max_y", 16, -64, 320);
    private final NumberValue<Integer> ignoreNearSelf = num("ignore_near_self", 8, 0, 32);
    private final BooleanMapValue sources = group("sources", defaultSources());
    private final NumberValue<Integer> rememberSeconds = num("remember_seconds", 120, 10, 1800);
    private final NumberValue<Integer> alertCooldownSeconds = num("alert_cooldown", 60, 0, 600);
    private final BooleanValue tracers = bool("tracers", true);
    private final BooleanValue chatAlerts = bool("chat_alerts", true);
    private final BooleanValue soundAlerts = bool("sound_alerts", true);
    private final RGBAColorValue fillColor = color("fill_color", "#40FFDC00");
    private final RGBAColorValue lineColor = color("line_color", "#FFFFDC00");

    private final Minecraft mc = Minecraft.getInstance();
    private final ConcurrentLinkedQueue<Signal> incoming = new ConcurrentLinkedQueue<>();
    private final Map<Long, Activity> activity = new LinkedHashMap<>();
    private ClientLevel lastLevel;

    private static Map<String, Boolean> defaultSources() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(MINING, true);
        defaults.put(BREAKS, true);
        defaults.put(UPDATES, false);
        return defaults;
    }

    @Override
    public void onDisable() {
        incoming.clear();
        activity.clear();
        lastLevel = null;
    }

    // Runs on the network thread: only filter and enqueue, no world access.
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.getPacket();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (packet instanceof ClientboundBlockDestructionPacket destruction && sources.get(MINING)) {
            if (destruction.getId() != player.getId()) offer(destruction.getPos(), "Mining");
        } else if (packet instanceof ClientboundLevelEventPacket levelEvent && sources.get(BREAKS)) {
            if (levelEvent.getType() == LevelEvent.PARTICLES_DESTROY_BLOCK) offer(levelEvent.getPos(), "Block broken");
        } else if (packet instanceof ClientboundBlockUpdatePacket update && sources.get(UPDATES)) {
            offer(update.getPos(), "Block changed");
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket section && sources.get(UPDATES)) {
            section.runUpdates((pos, state) -> offer(pos, "Block changed"));
        }
    }

    private void offer(BlockPos pos, String kind) {
        if (pos.getY() <= maxY.get()) incoming.add(new Signal(pos.immutable(), kind));
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.level != lastLevel) {
            // Packets queued before the switch belong to the old level.
            incoming.clear();
            activity.clear();
            lastLevel = mc.level;
        }
        LocalPlayer player = mc.player;
        long now = System.currentTimeMillis();
        double ignoreSq = (double) ignoreNearSelf.get() * ignoreNearSelf.get();

        Signal signal;
        while ((signal = incoming.poll()) != null) {
            if (player == null) continue;
            // Your own digging shows up in the same packets.
            if (player.distanceToSqr(Vec3.atCenterOf(signal.pos)) <= ignoreSq) continue;

            long key = ChunkPos.pack(signal.pos);
            Activity entry = activity.computeIfAbsent(key, k -> new Activity());
            entry.lastPos = signal.pos;
            entry.kind = signal.kind;
            entry.count++;
            entry.lastSeenMs = now;
            if (now - entry.lastAlertMs >= alertCooldownSeconds.get() * 1000L) {
                entry.lastAlertMs = now;
                FinderAlerts.found(getDisplayName(), signal.kind,
                        signal.pos.getX(), signal.pos.getY(), signal.pos.getZ(),
                        chatAlerts.get(), soundAlerts.get());
            }
        }

        long keepMs = rememberSeconds.get() * 1000L;
        activity.values().removeIf(entry -> now - entry.lastSeenMs > keepMs);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || activity.isEmpty()) return;
        FinderRender draw = FinderRender.begin(renderer, 1.5f);
        for (Map.Entry<Long, Activity> entry : activity.entrySet()) {
            BlockPos pos = entry.getValue().lastPos;
            draw.chunkPlate(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()), pos.getY(),
                    fillColor.getArgb(), 0);
            draw.blockBox(pos.getX(), pos.getY(), pos.getZ(), 0, lineColor.getArgb());
            if (tracers.get()) draw.tracer(Vec3.atCenterOf(pos), lineColor.getArgb());
        }
    }

    private record Signal(BlockPos pos, String kind) {
    }

    private static final class Activity {
        private BlockPos lastPos;
        private String kind;
        private int count;
        private long lastSeenMs;
        private long lastAlertMs;
    }
}
