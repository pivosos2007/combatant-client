/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.ServerHopRules;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Locale;

/**
 * Sends {@code /tpa <name>} to one configured player at an interval until the request is accepted: a chat line that
 * matches one of the accept phrases (and names the player), or a teleport you did not cause. Denial phrases stop it
 * too, as does the attempt limit and leaving the world.
 */
@ModuleInfo(
        id = "autotpa",
        displayName = "AutoTPA",
        aliases = {"TpaSpam", "AutoTeleportRequest"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autotpa.description")
public final class AutoTpa extends Module {

    private static final double TELEPORT_JUMP = 12.0;

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue target = text("autoTpaTarget", "target", "");
    private final StringValue command = text("autoTpaCommand", "command", "tpa");
    private final NumberValue<Integer> intervalTicks =
            num("autoTpaIntervalTicks", "interval_ticks", 400, 40, 6000);
    private final NumberValue<Integer> maxAttempts = num("autoTpaMaxAttempts", "max_attempts", 0, 0, 500);
    private final StringValue acceptPhrases =
            text("autoTpaAcceptPhrases", "accept_phrases", "accepted,teleporting");
    private final StringValue denyPhrases = text("autoTpaDenyPhrases", "deny_phrases", "denied,declined");
    private final BooleanValue requireName = bool("autoTpaRequireName", "require_name", true);

    private volatile boolean accepted;
    private volatile boolean denied;
    private volatile boolean worldChanged;
    private long ticks;
    private long nextSendTick;
    private int attempts;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasLast;

    @Override
    public void onEnable() {
        accepted = false;
        denied = false;
        worldChanged = false;
        ticks = 0;
        nextSendTick = 0;
        hasLast = false;
    }

    @Override
    public void onDisable() {
        hasLast = false;
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
        if (target.get().isBlank()) {
            Notifier.error(I18n.get("notification.autotpa.no_target"));
            setEnabled(false);
            return;
        }

        boolean teleported = hasLast && attempts > 0 && ServerHopRules.isTeleport(
                player.getX() - lastX, player.getY() - lastY, player.getZ() - lastZ, TELEPORT_JUMP);
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        hasLast = true;

        if (accepted || teleported || worldChanged && attempts > 0) {
            Notifier.success(I18n.get("notification.autotpa.accepted", target.get(), attempts));
            setEnabled(false);
            return;
        }
        if (denied) {
            Notifier.warning(I18n.get("notification.autotpa.denied", target.get()));
            setEnabled(false);
            return;
        }

        int limit = maxAttempts.get();
        if (!ServerHopRules.due(ticks, nextSendTick)) return;
        if (limit > 0 && attempts >= limit) {
            Notifier.warning(I18n.get("notification.autotpa.limit", target.get(), attempts));
            setEnabled(false);
            return;
        }
        if (!ServerChat.command(ServerHopRules.fillCommand(command.get(), target.get()))) {
            Notifier.error(I18n.get("notification.autotpa.no_command"));
            setEnabled(false);
            return;
        }
        attempts++;
        nextSendTick = ticks + intervalTicks.get();
    }

    @EventHandler
    private void onChat(PvpChatEvent event) {
        if (!isEnabled() || attempts == 0 || event.message == null) return;
        String name = target.get().strip();
        if (requireName.get() && !event.message.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
            return;
        }
        List<String> accept = ServerHopRules.parsePhrases(acceptPhrases.get());
        List<String> deny = ServerHopRules.parsePhrases(denyPhrases.get());
        if (ServerHopRules.containsAny(event.message, deny)) {
            denied = true;
        } else if (ServerHopRules.containsAny(event.message, accept)) {
            accepted = true;
        }
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        if (!isEnabled()) return;
        if (event.reason() == WorldChangeEvent.Reason.DISCONNECT) {
            setEnabled(false);
        } else {
            // Joining another dimension is how a cross-dimension teleport request shows up.
            worldChanged = true;
        }
    }
}
