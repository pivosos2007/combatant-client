/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.minecraft.network.chat.Component;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.SetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.Locale;
import java.util.Set;

//todo Description
@ModuleInfo(
        id = "message_filter",
        displayName = "MessageFilter",
        category = ModuleCategory.MISC
)
public class MessageFilter extends Module {

    private static final String SETTING_FILTER_CHAT = "filter_chat";
    private static final String SETTING_FILTER_HUD = "filter_hud";
    private static final String SETTING_CHAT_FILTERS = "chat_filters";
    private static final String SETTING_HUD_FILTERS = "hud_filters";

    private final BooleanValue chatEnabled = bool("filter_chat", SETTING_FILTER_CHAT, false);

    private final SetValue chatFilters = textList("chat_filters", SETTING_CHAT_FILTERS, TextListSetting.PickerMode.TEXT);

    private final BooleanValue hudEnabled = bool("filter_hud", SETTING_FILTER_HUD, false);

    private final SetValue hudFilters = textList("hud_filters", SETTING_HUD_FILTERS, TextListSetting.PickerMode.TEXT);

    public boolean shouldHideChat(Component message) {
        return isEnabled() && chatEnabled.get() && matches(message, chatFilters.get());
    }

    public boolean shouldHideHud(Component message) {
        return isEnabled() && hudEnabled.get() && matches(message, hudFilters.get());
    }

    private boolean matches(Component message, Set<String> patterns) {
        if (patterns.isEmpty() || message == null) return false;
        String body = message.getString().toLowerCase(Locale.ROOT);
        for (String pat : patterns) {
            if (pat == null) continue;
            String needle = pat.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) continue;
            if (body.contains(needle)) return true;
        }
        return false;
    }
}
