/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.CoordFormatter;
import combatant.client.util.server.CoordFormatter.Format;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.ServerChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

/**
 * Snaps your current coordinates on a keybind: copies them to the clipboard, prints them in your own chat and
 * optionally says them to the server, in the format you pick.
 */
@ModuleInfo(
        id = "cordsnapper",
        displayName = "CordSnapper",
        aliases = {"CoordSnapper", "CopyCoords"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.cordsnapper.description")
public final class CordSnapper extends Module {

    private static final String ACTION_SNAP = "snap";

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Format> format =
            enumSetting("cordSnapperFormat", "format", Format.SPACES, Format.values());
    private final NumberValue<Integer> decimals = num("cordSnapperDecimals", "decimals", 0, 0, 3);
    private final BooleanValue includeDimension = bool("cordSnapperIncludeDimension", "include_dimension", false);
    private final BooleanValue copy = bool("cordSnapperCopy", "copy_to_clipboard", true);
    private final BooleanValue echo = bool("cordSnapperEcho", "echo_in_chat", true);
    private final BooleanValue say = bool("cordSnapperSay", "send_to_server_chat", false);
    private final StringValue sayPrefix = visibleWhen(text("cordSnapperSayPrefix", "say_prefix", ""), say::get);

    public CordSnapper() {
        action(ACTION_SNAP, "NONE");
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (!isActionPressedOnce(ACTION_SNAP)) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || combatant.client.util.screen.ClientScreen.current() != null) return;

        String dimension = includeDimension.get() ? mc.level.dimension().identifier().getPath() : null;
        String text = CoordFormatter.format(format.get(), player.getX(), player.getY(), player.getZ(),
                decimals.get(), dimension);

        if (copy.get()) {
            ClipboardUtil.copy(text);
            Notifier.success(I18n.get("notification.cordsnapper.copied", text));
        }
        if (echo.get()) CommandOutput.send(text);
        if (say.get()) {
            String prefix = sayPrefix.get().strip();
            ServerChat.chat(prefix.isEmpty() ? text : prefix + " " + text);
        }
    }
}
