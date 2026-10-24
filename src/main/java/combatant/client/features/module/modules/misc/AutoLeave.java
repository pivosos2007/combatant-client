/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.misc.serverutil.AutoLeaveRules;
import combatant.client.features.module.modules.misc.serverutil.AutoLeaveRules.Trigger;
import combatant.client.features.module.modules.misc.serverutil.ServerChatText;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.features.relations.StaffData;
import combatant.client.features.relations.StaffTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Leaves the server the moment a configured danger shows up: low health, a stranger in range, a staff member
 * (via the staff tracker) or a chat keyword. The exit is a disconnect, or {@code /hub} / {@code /spawn}.
 */
@ModuleInfo(
        id = "autoleave",
        displayName = "AutoLeave",
        aliases = {"AutoLog", "SafeLeave"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autoleave.description")
public final class AutoLeave extends Module {

    private static final int STAFF_SCAN_INTERVAL = 10;

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue healthTrigger = bool("autoLeaveHealth", "health_trigger", true);
    private final NumberValue<Float> healthThreshold = visibleWhen(
            num("autoLeaveHealthThreshold", "health_threshold", 6.0f, 1.0f, 20.0f), healthTrigger::get);
    private final BooleanValue playerTrigger = bool("autoLeavePlayer", "player_trigger", false);
    private final NumberValue<Float> playerDistance = visibleWhen(
            num("autoLeavePlayerDistance", "player_distance", 32.0f, 2.0f, 128.0f), playerTrigger::get);
    private final BooleanValue ignoreFriends = visibleWhen(
            bool("autoLeaveIgnoreFriends", "ignore_friends", true), playerTrigger::get);
    private final BooleanValue staffTrigger = bool("autoLeaveStaff", "staff_trigger", true);
    private final BooleanValue chatTrigger = bool("autoLeaveChat", "chat_trigger", false);
    private final SetValue chatKeywords = visibleWhen(
            textList("autoLeaveChatKeywords", "chat_keywords", TextListSetting.PickerMode.TEXT), chatTrigger::get);
    private final ModeValue action = modeSetting("autoLeaveAction", "action", "disconnect", "disconnect", "hub", "spawn");
    private final NumberValue<Integer> graceTicks = num("autoLeaveGraceTicks", "grace_ticks", 60, 0, 600);
    private final BooleanValue disableAfter = bool("autoLeaveDisableAfter", "disable_after", true);

    /** Chat lines arrive on the network thread; matches are handed to the tick thread through this field. */
    private volatile String pendingKeyword;
    private LocalPlayer lastPlayer;
    private int ticksSinceJoin;
    private boolean staffOnline;
    private boolean fired;

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
        if (!isEnabled() || !chatTrigger.get() || event.message == null) return;
        String hit = ServerChatText.firstKeyword(event.message, chatKeywords.get());
        if (hit != null) pendingKeyword = hit;
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
        if (player != lastPlayer) {
            lastPlayer = player;
            ticksSinceJoin = 0;
            pendingKeyword = null;
            fired = false;
        }
        ticksSinceJoin++;
        if (fired || ticksSinceJoin < graceTicks.get()) {
            // drop stale chat hits collected during the grace period
            if (!fired) pendingKeyword = null;
            return;
        }

        if (staffTrigger.get()) {
            if (ticksSinceJoin % STAFF_SCAN_INTERVAL == 0) staffOnline = scanStaff();
        } else {
            staffOnline = false;
        }

        AutoLeaveRules.Config config = new AutoLeaveRules.Config(
                healthTrigger.get(), healthThreshold.get(),
                playerTrigger.get(), playerDistance.get(),
                staffTrigger.get(), chatTrigger.get());
        String keyword = pendingKeyword;
        AutoLeaveRules.Snapshot snapshot = new AutoLeaveRules.Snapshot(
                player.getHealth(),
                playerTrigger.get() ? nearestStrangerDistance(player) : Double.NaN,
                staffOnline,
                keyword);
        Trigger trigger = AutoLeaveRules.evaluate(config, snapshot);
        if (trigger == null) {
            pendingKeyword = null;
            return;
        }
        leave(player, trigger, keyword);
    }

    private void leave(LocalPlayer player, Trigger trigger, String keyword) {
        fired = true;
        String reason = I18n.get("notification.autoleave." + trigger.name().toLowerCase(Locale.ROOT),
                trigger == Trigger.CHAT && keyword != null ? keyword : "");
        Notifier.warning(reason);
        CommandOutput.warning(reason);

        switch (action.get()) {
            case "hub" -> player.connection.sendCommand("hub");
            case "spawn" -> player.connection.sendCommand("spawn");
            default -> player.connection.getConnection().disconnect(Component.literal(reason));
        }
        if (disableAfter.get()) setEnabled(false);
    }

    private double nearestStrangerDistance(LocalPlayer self) {
        double best = Double.NaN;
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == self || other.isRemoved() || other.isSpectator()) continue;
            if (ignoreFriends.get() && PlayerRelations.get().isFriend(other.getGameProfile().name())) continue;
            double d = self.distanceTo(other);
            if (Double.isNaN(best) || d < best) best = d;
        }
        return best;
    }

    private static boolean scanStaff() {
        for (StaffData.StaffEntry entry : StaffData.getEntries()) {
            StaffTracker.Status status = entry.status();
            if (status == StaffTracker.Status.GM0 || status == StaffTracker.Status.GM1
                    || status == StaffTracker.Status.GM2 || status == StaffTracker.Status.GM3
                    || status == StaffTracker.Status.VANISH) {
                return true;
            }
        }
        return false;
    }

    private void reset() {
        lastPlayer = null;
        ticksSinceJoin = 0;
        staffOnline = false;
        fired = false;
        pendingKeyword = null;
    }
}
