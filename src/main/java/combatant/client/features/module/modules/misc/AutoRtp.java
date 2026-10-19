/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.ServerHopRules;
import combatant.client.util.server.ServerHopRules.StopCondition;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;

/**
 * Sends your random-teleport command (default {@code /rtp}) at an interval until the spot you land on is good: no
 * other player nearby, far enough from spawn, or both. The landing spot is judged only after a teleport was seen and
 * the world had time to settle. Stops by itself on the condition, on the attempt limit and when you leave the world.
 */
@ModuleInfo(
        id = "autortp",
        displayName = "AutoRTP",
        aliases = {"AutoRandomTeleport", "RtpLoop"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autortp.description")
public final class AutoRtp extends Module {

    /** A one-tick jump this far is a teleport, not movement. */
    private static final double TELEPORT_JUMP = 12.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue command = text("autoRtpCommand", "command", "rtp");
    private final NumberValue<Integer> intervalTicks =
            num("autoRtpIntervalTicks", "interval_ticks", 120, 20, 1200);
    private final NumberValue<Integer> settleTicks = num("autoRtpSettleTicks", "settle_ticks", 40, 0, 200);
    private final NumberValue<Integer> maxAttempts = num("autoRtpMaxAttempts", "max_attempts", 0, 0, 500);
    private final EnumValue<StopCondition> stopWhen = enumSetting(
            "autoRtpStopWhen", "stop_when", StopCondition.NO_PLAYERS_NEARBY, StopCondition.values());
    private final NumberValue<Integer> playerRadius =
            visibleWhen(num("autoRtpPlayerRadius", "player_radius", 64, 8, 256), this::usesPlayers);
    private final NumberValue<Integer> spawnX =
            visibleWhen(num("autoRtpSpawnX", "spawn_x", 0, -30000000, 30000000), this::usesSpawn);
    private final NumberValue<Integer> spawnZ =
            visibleWhen(num("autoRtpSpawnZ", "spawn_z", 0, -30000000, 30000000), this::usesSpawn);
    private final NumberValue<Integer> minSpawnDistance =
            visibleWhen(num("autoRtpMinSpawnDistance", "min_spawn_distance", 1000, 50, 30000000), this::usesSpawn);

    private long ticks;
    private long nextSendTick;
    private long settledAtTick;
    private int attempts;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLast;
    private boolean landed;

    @Override
    public void onEnable() {
        ticks = 0;
        nextSendTick = 0;
        settledAtTick = 0;
        attempts = 0;
        hasLast = false;
        landed = false;
    }

    @Override
    public void onDisable() {
        hasLast = false;
        landed = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        ticks++;

        if (hasLast && ServerHopRules.isTeleport(
                player.getX() - lastX, player.getY() - lastY, player.getZ() - lastZ, TELEPORT_JUMP)) {
            settledAtTick = ticks + settleTicks.get();
            landed = true;
        }
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        hasLast = true;

        if (ticks < settledAtTick) return;

        if (landed && ServerHopRules.conditionMet(stopWhen.get(), usesPlayers() ? playersNearby(player) : 0,
                ServerHopRules.horizontalDistance(player.getX(), player.getZ(), spawnX.get(), spawnZ.get()),
                minSpawnDistance.get())) {
            Notifier.success(I18n.get("notification.autortp.done", attempts));
            setEnabled(false);
            return;
        }

        int limit = maxAttempts.get();
        if (limit > 0 && attempts >= limit && landed) {
            Notifier.warning(I18n.get("notification.autortp.limit", attempts));
            setEnabled(false);
            return;
        }

        if (!ServerHopRules.due(ticks, nextSendTick)) return;
        if (limit > 0 && attempts >= limit) {
            // The last command is still in flight: give it one interval to land before giving up.
            Notifier.warning(I18n.get("notification.autortp.limit", attempts));
            setEnabled(false);
            return;
        }
        if (!ServerChat.command(command.get())) {
            Notifier.error(I18n.get("notification.autortp.no_command"));
            setEnabled(false);
            return;
        }
        attempts++;
        landed = false;
        nextSendTick = ticks + intervalTicks.get();
    }

    private boolean usesPlayers() {
        StopCondition condition = stopWhen.get();
        return condition == StopCondition.NO_PLAYERS_NEARBY || condition == StopCondition.BOTH
                || condition == StopCondition.EITHER;
    }

    private boolean usesSpawn() {
        StopCondition condition = stopWhen.get();
        return condition == StopCondition.FAR_FROM_SPAWN || condition == StopCondition.BOTH
                || condition == StopCondition.EITHER;
    }

    private int playersNearby(LocalPlayer self) {
        double radiusSq = (double) playerRadius.get() * playerRadius.get();
        int count = 0;
        for (Player other : mc.level.players()) {
            if (other == self || other.isSpectator()) continue;
            if (other.distanceToSqr(self) <= radiusSq) count++;
        }
        return count;
    }
}
