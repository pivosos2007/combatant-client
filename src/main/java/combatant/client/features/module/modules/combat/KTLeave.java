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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BindMode;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.Offhand;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.network.BlinkManager;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.pvp.client.CooldownsState;
import combatant.client.util.pvp.opponents.TotemPopCounter;

@ModuleInfo(
        id = "ktleave",
        displayName = "KTLeave",
        category = ModuleCategory.COMBAT, subcategory = ModuleSubcategory.PROTECT,
        description = "module.ktleave.description")
public final class KTLeave extends Module {
    private static final String ACTION_LEAVE = "leave";
    private static final String CONDITION_LOW_HEALTH = "low_health";
    private static final String CONDITION_NO_TOTEMS = "no_totems";
    private static final String CONDITION_NEARBY_CRYSTAL = "nearby_crystal";
    private static final String CONDITION_STAFF = "staff";
    private static final String CONDITION_NEARBY_PLAYER = "nearby_player";
    private static final String CONDITION_TOTEM_POPS = "totem_pops";

    private final BooleanMapValue conditions = group("ktLeaveConditions", "conditions", defaultConditions());
    private final NumberValue<Integer> hpThreshold = visibleWhen(numCommon(
            "ktLeaveHpThreshold",
            "hp_threshold",
            CommonSettingSchemas.PLAYER_HEALTH_THRESHOLD,
            6,
            1,
            20
    ), () -> conditions.get(CONDITION_LOW_HEALTH));
    private final BooleanValue includeAbsorption = visibleWhen(
            bool("ktLeaveIncludeAbsorption", "include_absorption", true),
            () -> conditions.get(CONDITION_LOW_HEALTH)
    );
    private final NumberValue<Integer> popThreshold = visibleWhen(
            num("ktLeavePopThreshold", "pop_threshold", 3, 1, 10),
            () -> conditions.get(CONDITION_TOTEM_POPS)
    );
    private final NumberValue<Float> nearbyPlayerRange = visibleWhen(
            num("ktLeaveNearbyPlayerRange", "nearby_player_range", 10.0F, 1.0F, 100.0F),
            () -> conditions.get(CONDITION_NEARBY_PLAYER)
    );
    private final NumberValue<Float> crystalRange = visibleWhen(
            num("ktLeaveCrystalRange", "crystal_range", 6.0F, 1.0F, 16.0F),
            () -> conditions.get(CONDITION_NEARBY_CRYSTAL)
    );
    private final EnumValue<Mode> mode = enumSetting("ktLeaveMode", "mode", Mode.LEGIT);
    private final EnumValue<TotemMode> totemMode = enumSetting("ktLeaveTotemMode", "totem_mode", TotemMode.OFFHAND);
    private final NumberValue<Integer> leaveDelayMs = num("ktLeaveDelayMs", "leave_delay_ms", 250, 0, 5000);
    private final NumberValue<Integer> packetFallbackMs = visibleWhen(
            num("ktLeavePacketFallbackMs", "packet_fallback_ms", 1500, 0, 5000),
            () -> mode.get() == Mode.PACKET_KICK
    );
    private final NumberValue<Integer> joinCooldownMs =
            num("ktLeaveJoinCooldownMs", "join_cooldown_ms", 1500, 0, 60000);

    private final Minecraft mc = Minecraft.getInstance();
    private ClientLevel lastWorld;
    private boolean pendingTrigger;
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
            // The pop counter keeps entries for minutes; without this, rejoining would leave again at once.
            TotemPopCounter.reset(mc.player.getUUID());
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

        TriggerState trigger = evaluateTriggers(mc.player);
        if (!trigger.any()) {
            clearTriggerState();
            return;
        }

        if (trigger.lowHealthOnly() && shouldHoldForTotem(mc.player)) {
            clearTriggerState();
            return;
        }

        if (CooldownsState.MANAGER.isInPvp()) {
            pendingTrigger = true;
            armedInPvp = true;
            leaveScheduled = false;
            return;
        }
        if (!armedInPvp && trigger.lowHealthOnly()) {
            return;
        }

        pendingTrigger = true;
        executeLeave(now);
    }

    private TriggerState evaluateTriggers(LocalPlayer player) {
        boolean lowHealth = conditions.get(CONDITION_LOW_HEALTH) && currentHealth(player) <= hpThreshold.get();
        boolean noTotems = conditions.get(CONDITION_NO_TOTEMS) && !hasAnyTotem(player);
        boolean nearbyCrystal = conditions.get(CONDITION_NEARBY_CRYSTAL) && hasNearbyCrystal(player);
        boolean staff = conditions.get(CONDITION_STAFF) && hasStaffNearby(player);
        boolean nearbyPlayer = conditions.get(CONDITION_NEARBY_PLAYER) && hasNearbyTargetPlayer();
        boolean totemPops = conditions.get(CONDITION_TOTEM_POPS)
                && TotemPopCounter.getCount(player.getUUID()) >= popThreshold.get();
        return new TriggerState(lowHealth, noTotems, nearbyCrystal, staff, nearbyPlayer, totemPops);
    }

    private boolean hasAnyTotem(LocalPlayer player) {
        return isTotemHeld(player) || hasTotemInInventory(player);
    }

    private boolean hasNearbyCrystal(LocalPlayer player) {
        if (mc.level == null || player == null) return false;
        double range = crystalRange.get();
        return !mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                crystal -> crystal != null && !crystal.isRemoved()
        ).isEmpty();
    }

    private boolean hasStaffNearby(LocalPlayer player) {
        if (mc.level == null || player == null) return false;
        for (Player other : mc.level.players()) {
            if (other == null || other == player || !other.isAlive() || other.isRemoved()) continue;
            if (CategoryRules.determine(other.getGameProfile().name()) == CategoryType.STAFF) return true;
        }
        return false;
    }

    private boolean hasNearbyTargetPlayer() {
        return TargetingUtil.findBestTarget(
                mc,
                new TargetingUtil.TargetingSettings(
                        nearbyPlayerRange.get(),
                        180.0F,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        TargetingUtil.TargetPriority.DISTANCE
                )
        ) != null;
    }

    private void executeLeave(long now) {
        if (!pendingTrigger || mc.getConnection() == null) {
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
            return guardMode == TotemMode.OFFHAND
                    || guardMode == TotemMode.HELD
                    || guardMode == TotemMode.INVENTORY;
        }

        return switch (guardMode) {
            case OFFHAND -> canOffhandHandleNow(player);
            case INVENTORY -> hasTotemInInventory(player);
            case HELD, OFF -> false;
        };
    }

    private boolean canOffhandHandleNow(LocalPlayer player) {
        Offhand offhand = Modules.get(Offhand.class);
        if (offhand == null || !offhand.isEnabled()) {
            return false;
        }
        if (offhand.isTotemSwapPending()) {
            return true;
        }
        return offhand.shouldHoldTotemNow(player) && offhand.canProvideTotemNow(player);
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

    private void clearTriggerState() {
        pendingTrigger = false;
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
        pendingTrigger = false;
        armedInPvp = false;
        leaveScheduled = false;
        packetKickSent = false;
        manualLeaveScheduled = false;
        leaveAtMs = 0L;
        manualLeaveAtMs = 0L;
        packetFallbackAtMs = 0L;
    }

    private static java.util.Map<String, Boolean> defaultConditions() {
        java.util.LinkedHashMap<String, Boolean> defaults = new java.util.LinkedHashMap<>();
        defaults.put(CONDITION_LOW_HEALTH, true);
        defaults.put(CONDITION_NO_TOTEMS, false);
        defaults.put(CONDITION_NEARBY_CRYSTAL, false);
        defaults.put(CONDITION_STAFF, false);
        defaults.put(CONDITION_NEARBY_PLAYER, false);
        defaults.put(CONDITION_TOTEM_POPS, false);
        return defaults;
    }

    private record TriggerState(
            boolean lowHealth,
            boolean noTotems,
            boolean nearbyCrystal,
            boolean staff,
            boolean nearbyPlayer,
            boolean totemPops
    ) {
        private boolean any() {
            return lowHealth || noTotems || nearbyCrystal || staff || nearbyPlayer || totemPops;
        }

        private boolean lowHealthOnly() {
            return lowHealth && !noTotems && !nearbyCrystal && !staff && !nearbyPlayer && !totemPops;
        }
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

    public enum TotemMode implements EnumValue.IdProvider, EnumValue.AliasProvider {
        OFF("off"),
        OFFHAND("offhand", "auto_totem"),
        HELD("held"),
        INVENTORY("inventory");

        private final String id;
        private final java.util.List<String> aliases;

        TotemMode(String id, String... aliases) {
            this.id = id;
            this.aliases = java.util.List.of(aliases);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public java.util.List<String> aliases() {
            return aliases;
        }
    }
}
