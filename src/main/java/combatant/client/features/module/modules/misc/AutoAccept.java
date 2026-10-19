/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.misc.serverutil.TeleportRequestParser;
import combatant.client.features.relations.PlayerRelations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Answers teleport requests from friends and whitelisted players with {@code /tpaccept} after a short delay.
 * Only server system messages are parsed, so another player cannot forge a request line in public chat.
 */
@ModuleInfo(
        id = "autoaccept",
        displayName = "AutoAccept",
        aliases = {"AutoTPA", "AutoTpAccept"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autoaccept.description")
public final class AutoAccept extends Module {

    private static final long DUPLICATE_WINDOW_MS = 3000L;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue acceptFriends = bool("autoAcceptFriends", "accept_friends", true);
    private final SetValue whitelist = textList("autoAcceptWhitelist", "whitelist", TextListSetting.PickerMode.TEXT);
    private final BooleanValue useDefaultPatterns = bool("autoAcceptDefaultPatterns", "default_patterns", true);
    private final SetValue customPatterns =
            textList("autoAcceptPatterns", "patterns", TextListSetting.PickerMode.TEXT);
    private final NumberValue<Integer> delayTicks = num("autoAcceptDelayTicks", "delay_ticks", 10, 0, 200);
    private final StringValue command = text("autoAcceptCommand", "command", "tpaccept");
    private final BooleanValue appendName = bool("autoAcceptAppendName", "append_name", false);

    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private TeleportRequestParser parser = TeleportRequestParser.of(Set.of(), true);
    private Set<String> parserPatterns = Set.of();
    private boolean parserDefaults = true;
    private String lastName = "";
    private long lastAt;
    private long tick;

    private record Pending(String name, long dueTick) {
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventHandler
    private void onChat(PvpChatEvent event) {
        if (!isEnabled() || event.source != PvpChatEvent.Source.GAME_MESSAGE || event.message == null) return;
        String sender = currentParser().senderOf(event.message);
        if (sender != null) incoming.add(sender);
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        reset();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }
        tick++;

        String name;
        long now = System.currentTimeMillis();
        while ((name = incoming.poll()) != null) {
            if (!isTrusted(name)) continue;
            if (name.equalsIgnoreCase(lastName) && now - lastAt < DUPLICATE_WINDOW_MS) continue;
            lastName = name;
            lastAt = now;
            pending.add(new Pending(name, tick + delayTicks.get()));
        }

        Pending next;
        while ((next = pending.peek()) != null && next.dueTick() <= tick) {
            pending.poll();
            String cmd = sanitizedCommand();
            if (cmd.isEmpty()) continue;
            player.connection.sendCommand(appendName.get() ? cmd + " " + next.name() : cmd);
            Notifier.success(I18n.get("notification.autoaccept.accepted", next.name()));
        }
    }

    private boolean isTrusted(String name) {
        if (acceptFriends.get() && PlayerRelations.get().isFriend(name)) return true;
        for (String entry : whitelist.get()) {
            if (entry != null && entry.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private String sanitizedCommand() {
        String cmd = command.get() == null ? "" : command.get().trim();
        return cmd.startsWith("/") ? cmd.substring(1).trim() : cmd;
    }

    private synchronized TeleportRequestParser currentParser() {
        Set<String> patterns = customPatterns.get();
        boolean defaults = useDefaultPatterns.get();
        if (defaults != parserDefaults || !patterns.equals(parserPatterns)) {
            parserPatterns = new HashSet<>(patterns);
            parserDefaults = defaults;
            parser = TeleportRequestParser.of(parserPatterns, defaults);
        }
        return parser;
    }

    private void reset() {
        incoming.clear();
        pending.clear();
        lastName = "";
        lastAt = 0L;
        tick = 0L;
    }
}
