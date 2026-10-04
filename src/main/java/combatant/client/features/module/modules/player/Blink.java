/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.network.TransferOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@ModuleInfo(
        id = "blink",
        displayName = "Blink",
        aliases = {"PacketChoke", "Desync"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.blink.description")
public final class Blink extends Module {
    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<PacketMode> packetMode = enumSetting("blinkPacketMode", "packet_mode", PacketMode.MOVEMENT_ONLY, PacketMode.values());
    private final NumberValue<Integer> limit = num("blinkLimit", "limit", 160, 10, 1000);
    private final EnumValue<LimitAction> limitAction = enumSetting("blinkLimitAction", "limit_action", LimitAction.DISABLE, LimitAction.values());
    private final BooleanValue pulse = bool("blinkPulse", "pulse", false);
    private final NumberValue<Integer> pulseDelayMs = visibleWhen(num("blinkPulseDelayMs", "pulse_delay_ms", 500, 50, 5000), pulse::get);
    private final BooleanValue cancelOnDisable = bool("blinkCancelOnDisable", "cancel_on_disable", false);
    private final BooleanValue renderGhost = bool("blinkRenderGhost", "render_ghost", true);
    private final BooleanValue renderTrail = bool("blinkRenderTrail", "render_trail", true);
    private final RGBAColorValue fillColor = color("blinkFillColor", "fill_color", "#285A9C33");
    private final RGBAColorValue lineColor = color("blinkLineColor", "line_color", "#55AAFFFF");

    private Vec3 startPos;
    private long lastPulseTime;
    private boolean correctionShutdown;

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            setEnabled(false);
            return;
        }
        FakeLag fakeLag = ModuleManager.get(FakeLag.class);
        if (fakeLag != null && fakeLag.isEnabled()) {
            fakeLag.setEnabled(false);
        }
        correctionShutdown = false;
        startPos = player.position();
        lastPulseTime = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        if (correctionShutdown) {
            BlinkManager.INSTANCE.getPacketQueue().removeIf(snapshot -> snapshot.origin() == TransferOrigin.OUTGOING);
        } else if (cancelOnDisable.get()) {
            if (mc.player != null && startPos != null) {
                mc.player.setPos(startPos);
            }
            BlinkManager.INSTANCE.getPacketQueue().removeIf(snapshot -> snapshot.origin() == TransferOrigin.OUTGOING);
        } else {
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        }
        correctionShutdown = false;
        startPos = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            setEnabled(false);
            return;
        }

        long queued = BlinkManager.INSTANCE.getPacketQueue().stream()
                .filter(snapshot -> snapshot.origin() == TransferOrigin.OUTGOING)
                .count();
        if (queued >= limit.get()) {
            if (limitAction.get() == LimitAction.DISABLE) {
                setEnabled(false);
                return;
            }
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
            startPos = player.position();
        }

        if (pulse.get()) {
            long now = System.currentTimeMillis();
            if (now - lastPulseTime >= pulseDelayMs.get()) {
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                startPos = player.position();
                lastPulseTime = now;
            }
        }
    }

    @EventHandler(priority = 100)
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!isEnabled() || event.getOrigin() != TransferOrigin.OUTGOING) return;
        Packet<?> packet = event.getPacket();
        // BlinkManager flushes the queue whenever a packet or the per-tick poll (packet == null) comes back FLUSH.
        // The client sends a tick-end packet every tick, so in movement-only mode it has to be held with the
        // movement packets, and the poll has to answer QUEUE, or nothing is ever held back.
        if (packet != null && packetMode.get() == PacketMode.MOVEMENT_ONLY && !isMovementTraffic(packet)) return;
        event.setAction(BlinkManager.Action.QUEUE);
    }

    private static boolean isMovementTraffic(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket || packet instanceof ServerboundClientTickEndPacket;
    }

    @EventHandler(priority = 100)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            correctionShutdown = true;
            setEnabled(false);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null) return;
        List<Vec3> positions = BlinkManager.INSTANCE.getQueuedMovePositions();
        Vec3 serverPos = startPos;
        if (serverPos == null) return;

        if (renderGhost.get()) {
            AABB box = mc.player.getDimensions(mc.player.getPose()).makeBoundingBox(serverPos);
            renderer.filledBox(box, fillColor.getArgb(), Renderer3D.DepthMode.NONE);
            renderer.outlineBox(box, lineColor.getArgb(), 2.0f, Renderer3D.DepthMode.NONE);
        }

        if (renderTrail.get()) {
            int argb = lineColor.getArgb();
            int a = argb >>> 24 & 0xFF;
            int r = argb >>> 16 & 0xFF;
            int g = argb >>> 8 & 0xFF;
            int b = argb & 0xFF;
            Vec3 prev = serverPos;
            for (Vec3 pos : positions) {
                renderer.line(prev.x, prev.y + 0.1, prev.z, pos.x, pos.y + 0.1, pos.z, r, g, b, a);
                prev = pos;
            }
            renderer.line(prev.x, prev.y + 0.1, prev.z, mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), r, g, b, a);
        }
    }

    public enum PacketMode implements EnumValue.IdProvider {
        MOVEMENT_ONLY("movement_only"),
        ALL_OUTGOING("all_outgoing");

        private final String id;

        PacketMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum LimitAction implements EnumValue.IdProvider {
        DISABLE("disable"),
        FLUSH("flush");

        private final String id;

        LimitAction(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
