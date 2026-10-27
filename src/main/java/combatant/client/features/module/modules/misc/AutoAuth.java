/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.profile.ConfigServerBindings;
import combatant.client.config.values.SetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import java.util.Set;
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
import combatant.client.features.module.modules.misc.serverutil.AuthPromptDetector;
import combatant.client.features.module.modules.misc.serverutil.AuthPromptDetector.Prompt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

/**
 * Answers {@code /login} and {@code /register} prompts from auth plugins with the stored password.
 * Only server system messages are trusted (player chat can be forged), attempts are capped per session,
 * and auto-auth is restricted to explicitly configured trusted servers. Sensitive values (passwords) are
 * excluded from config profile exports and share codes.
 */
@ModuleInfo(
        id = "autoauth",
        displayName = "AutoAuth",
        aliases = {"AutoLogin", "AutoRegister"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autoauth.description")
public final class AutoAuth extends Module {

    private static final int RETRY_COOLDOWN_TICKS = 60;

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue password = text("autoAuthPassword", "password", "").sensitive();
    private final SetValue trustedServers = textList("autoAuthTrustedServers", "trusted_servers", TextListSetting.PickerMode.TEXT);
    private final BooleanValue autoRegister = bool("autoAuthRegister", "auto_register", false);
    private final NumberValue<Integer> delayTicks = num("autoAuthDelayTicks", "delay_ticks", 20, 0, 200);
    private final NumberValue<Integer> maxAttempts = num("autoAuthMaxAttempts", "max_attempts", 2, 1, 5);
    private record Request(Prompt prompt, boolean confirm) {
    }

    /** Written by the network thread, consumed on the tick thread. */
    private volatile Request request;
    private long tick;
    private long dueTick;
    private long lastSentTick = Long.MIN_VALUE / 2;
    private int attempts;

    public boolean isServerTrusted() {
        String current = ConfigServerBindings.currentAddress();
        if (current.isEmpty()) return false;
        Set<String> trusted = trustedServers.get();
        if (trusted == null || trusted.isEmpty()) return false;
        for (String entry : trusted) {
            if (entry != null && ConfigServerBindings.normalize(entry).equalsIgnoreCase(current)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
        request = null;
        attempts = 0;
        tick = 0L;
    }

    @Override
    public void onDisable() {
        request = null;
    }

    @EventHandler
    private void onChat(PvpChatEvent event) {
        if (!isEnabled() || event.source != PvpChatEvent.Source.GAME_MESSAGE || event.message == null) return;
        Prompt prompt = AuthPromptDetector.detect(event.message);
        if (prompt == Prompt.NONE) return;
        if (!isServerTrusted()) {
            String current = ConfigServerBindings.currentAddress();
            Notifier.warning(I18n.get("notification.autoauth.untrusted_server", current.isEmpty() ? "?" : current));
            return;
        }
        if (prompt == Prompt.REGISTER && !autoRegister.get()) return;
        request = new Request(prompt, prompt == Prompt.REGISTER
                && AuthPromptDetector.registerNeedsConfirmation(event.message));
    }
    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        request = null;
        if (event.reason() == WorldChangeEvent.Reason.DISCONNECT) attempts = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            request = null;
            return;
        }
        tick++;

        Request pending = request;
        if (pending == null) {
            dueTick = 0L;
            return;
        }
        if (dueTick == 0L) {
            dueTick = tick + delayTicks.get();
            return;
        }
        if (tick < dueTick) return;
        dueTick = 0L;
        request = null;

        if (!isServerTrusted()) {
            request = null;
            return;
        }
        if (attempts >= maxAttempts.get() || tick - lastSentTick < RETRY_COOLDOWN_TICKS) return;
        String command = AuthPromptDetector.buildCommand(pending.prompt(), password.get(), pending.confirm());
        if (command == null) {
            Notifier.warning(I18n.get("notification.autoauth.no_password"));
            return;
        }
        attempts++;
        lastSentTick = tick;
        player.connection.sendCommand(command);
        Notifier.info(I18n.get(pending.prompt() == Prompt.LOGIN
                ? "notification.autoauth.login_sent" : "notification.autoauth.register_sent"));
    }
}
