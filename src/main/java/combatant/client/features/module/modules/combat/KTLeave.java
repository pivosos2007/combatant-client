/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.util.Util;
import net.minecraft.world.item.Items;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.AutoTotem;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.pvp.client.CooldownsState;

//todo Description
@ModuleInfo(
        id = "ktleave",
        displayName = "KTLeave",
        category = ModuleCategory.COMBAT
)
public final class KTLeave extends Module {
    private static final String ACTION_LEAVE = "leave";

    private final NumberValue<Integer> hpThreshold = numCommon(
            "ktLeaveHpThreshold",
            "hp_threshold",
            CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
            6,
            1,
            20
    );
    private final BooleanValue includeAbsorption = bool("ktLeaveIncludeAbsorption", "include_absorption", true);
    private final EnumValue<Mode> mode = enumSetting("ktLeaveMode", "mode", Mode.LEGIT);
    private final EnumValue<TotemMode> totemMode = enumSetting("ktLeaveTotemMode", "totem_mode", TotemMode.AUTO_TOTEM);
    private final NumberValue<Integer> leaveDelayMs = num("ktLeaveDelayMs", "leave_delay_ms", 250, 0, 5000);
    private final NumberValue<Integer> packetFallbackMs = visibleWhen(
            num("ktLeavePacketFallbackMs", "packet_fallback_ms", 1500, 0, 5000),
            () -> mode.get() == Mode.PACKET_KICK
    );
    private final NumberValue<Integer> joinCooldownMs =
            num("ktLeaveJoinCooldownMs", "join_cooldown_ms", 1500, 0, 60000);

    private final Minecraft mc = Minecraft.getInstance();
    private ClientLevel lastWorld;
    private boolean pendingLowHealth;
    private boolean leaveScheduled;
    private boolean packetKickSent;
    private boolean manualLeaveScheduled;
    private long joinCooldownUntilMs;
    private long leaveAtMs;
    private long manualLeaveAtMs;
    private long packetFallbackAtMs;
    private boolean armedInPvp;

    public KTLeave() {
        action(ACTION_LEAVE, "NONE", BindMode.PRESS);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) {
            resetState();
            return;
        }

        long now = Util.getMillis();
        if (mc.level != lastWorld) {
            resetState();
            lastWorld = mc.level;
            joinCooldownUntilMs = now + joinCooldownMs.get();
            return;
        }

        if (isActionPressedOnce(ACTION_LEAVE)) {
            manualLeaveScheduled = true;
            manualLeaveAtMs = now + leaveDelayMs.get();
        }

        if (packetKickSent) {
            if (packetFallbackMs.get() > 0 && now >= packetFallbackAtMs) {
                disconnectNormally();
            }
            return;
        }

        if (manualLeaveScheduled) {
            if (now >= manualLeaveAtMs) {
                executeConfiguredLeave(now);
            }
            return;
        }

        if (now < joinCooldownUntilMs) {
            return;
        }

        if (currentHealth(mc.player) > hpThreshold.get()) {
            clearLowHealthState();
            return;
        }

        if (shouldHoldForTotem(mc.player)) {
            clearLowHealthState();
            return;
        }

        if (CooldownsState.MANAGER.isInPvp()) {
            pendingLowHealth = true;
            armedInPvp = true;
            leaveScheduled = false;
            return;
        }
        if (!armedInPvp) {
            return;
        }

        pendingLowHealth = true;
        executeLeave(now);
    }

    private void executeLeave(long now) {
        if (!pendingLowHealth || mc.getConnection() == null) {
            return;
        }

        if (!leaveScheduled) {
            leaveScheduled = true;
            leaveAtMs = now + leaveDelayMs.get();
            return;
        }
        if (now < leaveAtMs) {
            return;
        }

        executeConfiguredLeave(now);
    }

    private void executeConfiguredLeave(long now) {
        if (mode.get() == Mode.PACKET_KICK && !mc.hasSingleplayerServer()) {
            sendKickPackets(now);
            return;
        }
        disconnectNormally();
    }

    private boolean shouldHoldForTotem(LocalPlayer player) {
        TotemMode guardMode = totemMode.get();
        if (guardMode == TotemMode.OFF) {
            return false;
        }
        if (player == null) {
            return false;
        }

        if (isTotemHeld(player)) {
            return guardMode == TotemMode.AUTO_TOTEM
                    || guardMode == TotemMode.HELD
                    || guardMode == TotemMode.INVENTORY;
        }

        return switch (guardMode) {
            case AUTO_TOTEM -> canAutoTotemHandleNow(player);
            case INVENTORY -> hasTotemInInventory(player);
            case HELD, OFF -> false;
        };
    }

    private boolean canAutoTotemHandleNow(LocalPlayer player) {
        AutoTotem autoTotem = Modules.get(AutoTotem.class);
        if (autoTotem == null || !autoTotem.isEnabled()) {
            return false;
        }
        if (autoTotem.isTotemSwapPending()) {
            return true;
        }
        return autoTotem.shouldHoldTotemNow(player) && autoTotem.canProvideTotemNow(player);
    }

    private boolean isTotemHeld(LocalPlayer player) {
        return player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
    }

    private boolean hasTotemInInventory(LocalPlayer player) {
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    private void clearLowHealthState() {
        pendingLowHealth = false;
        armedInPvp = false;
        leaveScheduled = false;
    }

    private int currentHealth(LocalPlayer player) {
        float hp = player.getHealth();
        if (includeAbsorption.get()) {
            hp += player.getAbsorptionAmount();
        }
        return Math.round(hp);
    }

    private void sendKickPackets(long now) {
        Connection connection = mc.getConnection().getConnection();
        if (connection == null || !connection.isConnected()) {
            return;
        }

        int seed = (int) System.nanoTime();
        BlinkManager.INSTANCE.sendSilently(new ServerboundPongPacket(seed ^ 0x5A5A5A5A));
        BlinkManager.INSTANCE.sendSilently(new ServerboundPongPacket(Integer.MIN_VALUE + (seed & 0xFFFF)));
        BlinkManager.INSTANCE.sendSilently(new ServerboundPongPacket(Integer.MAX_VALUE - (seed & 0xFFFF)));

        packetKickSent = true;
        packetFallbackAtMs = now + packetFallbackMs.get();
        Notifier.info("KTLeave packet kick sent");
    }

    private void disconnectNormally() {
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().getConnection().disconnect(Component.empty());
    }

    private void resetState() {
        pendingLowHealth = false;
        armedInPvp = false;
        leaveScheduled = false;
        packetKickSent = false;
        manualLeaveScheduled = false;
        leaveAtMs = 0L;
        manualLeaveAtMs = 0L;
        packetFallbackAtMs = 0L;
    }

    public enum Mode implements EnumValue.IdProvider {
        LEGIT("legit"),
        PACKET_KICK("packet_kick");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum TotemMode implements EnumValue.IdProvider {
        OFF("off"),
        AUTO_TOTEM("auto_totem"),
        HELD("held"),
        INVENTORY("inventory");

        private final String id;

        TotemMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
