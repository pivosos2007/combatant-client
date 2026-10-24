/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps streams and screenshots free of leaks: container and inventory slots are drawn as blank
 * placeholders (tooltips replaced) and the connected server address is masked in rendered text.
 */
@ModuleInfo(
        id = "noinvleak",
        displayName = "NoInvLeak",
        aliases = {"StreamSafe", "HideInventory"},
        category = ModuleCategory.MISC, subcategory = ModuleSubcategory.CLIENT,
        description = "module.noinvleak.description")
public final class NoInvLeak extends Module {

    public static final String MASK = "hidden";

    private final BooleanValue hideContainers = bool("hide_containers", true);
    private final BooleanValue hideOwnInventory = bool("hide_own_inventory", false);
    private final BooleanValue hideTooltips = bool("hide_tooltips", true);
    private final BooleanValue placeholder = bool("placeholder", true);
    private final BooleanValue hideAddress = bool("hide_server_address", true);

    private String cachedHost = "";
    private Pattern cachedPattern;

    /** Whether the slot's item must not be drawn. */
    public static boolean hidesSlot(Slot slot) {
        NoInvLeak module = active();
        if (module == null || slot == null || slot.getItem().isEmpty()) return false;
        return slot.container instanceof Inventory ? module.hideOwnInventory.get() : module.hideContainers.get();
    }

    public static boolean hidesTooltipOf(Slot slot) {
        NoInvLeak module = active();
        return module != null && module.hideTooltips.get() && hidesSlot(slot);
    }

    public static boolean drawsPlaceholder() {
        NoInvLeak module = active();
        return module != null && module.placeholder.get();
    }

    /** Masks the current server address in {@code text}; returns the same instance when nothing matched. */
    public static String applyDisplay(String text) {
        if (text == null || text.isEmpty()) return text;
        NoInvLeak module = active();
        if (module == null || !module.hideAddress.get()) return text;
        Pattern pattern = module.addressPattern();
        if (pattern == null) return text;
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.replaceAll(MASK) : text;
    }

    private static NoInvLeak active() {
        NoInvLeak module = Modules.get(NoInvLeak.class);
        return module != null && module.isEnabled() ? module : null;
    }

    private Pattern addressPattern() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        String host = server == null || server.ip == null ? "" : server.ip.trim();
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        if (host.isEmpty() || host.equalsIgnoreCase("localhost")) return null;
        if (!host.equals(cachedHost)) {
            cachedHost = host;
            cachedPattern = Pattern.compile(Pattern.quote(host.toLowerCase(Locale.ROOT)),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        return cachedPattern;
    }
}
