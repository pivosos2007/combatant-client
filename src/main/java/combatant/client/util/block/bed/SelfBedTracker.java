/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.bed;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.gui.hud.draggable.impl.HudNotifier;

public final class SelfBedTracker {
    public static final SelfBedTracker INSTANCE = new SelfBedTracker();

    private static final double DEFAULT_MANUAL_TRACK_RANGE = 16.0;
    private static final double MIN_SPAWN_PACKET_DISTANCE_SQ = 16.0 * 16.0;

    private Vec3 trackedSpawnLocation;
    private BlockPos trackedManualBed;
    private ClientLevel lastWorld;

    private SelfBedTracker() {
    }

    public boolean shouldDefend(
            SelfBedMode mode,
            BlockState state,
            BlockPos pos,
            LocalPlayer player,
            double spawnBedDistance
    ) {
        return mode == SelfBedMode.NONE || isSelfBed(mode, state, pos, player, spawnBedDistance);
    }

    public boolean isSelfBed(
            SelfBedMode mode,
            BlockState state,
            BlockPos pos,
            LocalPlayer player,
            double spawnBedDistance
    ) {
        if (mode == null || state == null || pos == null || !(state.getBlock() instanceof BedBlock bedBlock)) {
            return false;
        }

        return switch (mode) {
            case NONE -> false;
            case COLOR -> matchesArmorColor(player, bedBlock);
            case SPAWN_LOCATION -> isNearTrackedSpawn(pos, spawnBedDistance);
            case MANUAL -> matchesManualBed(state, pos);
        };
    }

    public void trackNearestBed(LocalPlayer player) {
        trackNearestBed(player, DEFAULT_MANUAL_TRACK_RANGE);
    }

    public void trackNearestBed(LocalPlayer player, double range) {
        if (player == null || player.level() == null) {
            return;
        }

        BedBlockUtil.BedSearchEntry bed = BedBlockUtil.findClosestBed(
                player.level(),
                player.getEyePosition(),
                range,
                (pos, state) -> true
        );

        if (bed == null) {
            HudNotifier.pushMessage("SelfBed: cannot find any bed around you. Get closer to your bed.", HudNotifier.NotifyType.NO);
            return;
        }

        trackedManualBed = bed.pos().immutable();
        lastWorld = Minecraft.getInstance().level;
        HudNotifier.pushMessage(
                "SelfBed: tracked bed position " + trackedManualBed.getX() + " " + trackedManualBed.getY() + " " + trackedManualBed.getZ(),
                HudNotifier.NotifyType.YES
        );
    }

    public void untrackManualBed() {
        if (trackedManualBed == null) {
            return;
        }

        BlockPos pos = trackedManualBed;
        trackedManualBed = null;
        HudNotifier.pushMessage(
                "SelfBed: bed position " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " has been untracked.",
                HudNotifier.NotifyType.INFO
        );
    }

    public void reset() {
        trackedSpawnLocation = null;
        trackedManualBed = null;
        lastWorld = null;
    }

    public Vec3 getTrackedSpawnLocation() {
        return trackedSpawnLocation;
    }

    public BlockPos getTrackedManualBed() {
        return trackedManualBed;
    }

    @EventHandler(priority = 1000)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        Vec3 position = packet.change().position();
        if (player.position().distanceToSqr(position) > MIN_SPAWN_PACKET_DISTANCE_SQ) {
            trackedSpawnLocation = position;
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel world = mc.level;

        if (world != lastWorld) {
            if (trackedManualBed != null && lastWorld != null) {
                BlockPos pos = trackedManualBed;
                trackedManualBed = null;
                HudNotifier.pushMessage(
                        "SelfBed: bed position " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " has been untracked due to world change.",
                        HudNotifier.NotifyType.INFO
                );
            }
            lastWorld = world;
        }
    }

    private boolean matchesArmorColor(LocalPlayer player, BedBlock bedBlock) {
        Integer armorColor = getArmorColor(player);
        return armorColor != null && armorColor == bedBlock.getColor().getTextureDiffuseColor();
    }

    private boolean isNearTrackedSpawn(BlockPos pos, double distance) {
        if (trackedSpawnLocation == null || distance < 0.0) {
            return false;
        }
        return trackedSpawnLocation.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= distance * distance;
    }

    private boolean matchesManualBed(BlockState state, BlockPos pos) {
        if (trackedManualBed == null) {
            return false;
        }
        if (trackedManualBed.equals(pos)) {
            return true;
        }

        Direction direction = BedBlockUtil.anotherBedPartDirection(state);
        return direction != null && pos.relative(direction).equals(trackedManualBed);
    }

    private Integer getArmorColor(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int color = DyedItemColor.getOrDefault(stack, -1);
            if (color != -1) {
                return color;
            }
        }

        return null;
    }
}
