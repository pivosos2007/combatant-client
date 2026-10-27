/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.donut;

import combatant.client.config.values.BooleanValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.server.DonutAuctionParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Swallows an outgoing {@code /pay <player> <amount>} and shows the "You paid ..." receipt locally instead, so
 * nothing is sent to the server. Purely a client-side preview.
 */
@ModuleInfo(
        id = "fakepay",
        displayName = "FakePay",
        aliases = {"PayFake"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.fakepay.description")
public final class FakePay extends Module {

    private static final double[] UNITS = {1.0e12, 1.0e9, 1.0e6, 1.0e3};
    private static final String[] SUFFIXES = {"T", "B", "M", "K"};

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue sound = bool("fakePaySound", "sound", true);
    private final BooleanValue actionBar = bool("fakePayActionBar", "action_bar", true);

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled() || event.isCancelled()) return;
        Object packet = event.getPacket();
        String command = null;
        if (packet instanceof ServerboundChatCommandPacket plain) {
            command = plain.command();
        } else if (packet instanceof ServerboundChatCommandSignedPacket signed) {
            command = signed.command();
        }
        if (command != null && handlePay(command)) event.cancel();
    }

    private boolean handlePay(String command) {
        if (mc.player == null) return false;
        String[] parts = command.trim().split("\\s+");
        if (parts.length < 3 || !"pay".equals(parts[0].toLowerCase(Locale.ROOT))) return false;
        double amount = DonutAuctionParser.parseMoney(parts[2]);
        if (!Double.isFinite(amount) || amount <= 0.0) return false;

        MutableComponent receipt = Component.literal("You paid ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(parts[1]).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" $" + formatMoney(amount)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY));
        mc.player.sendSystemMessage(receipt);
        if (actionBar.get()) mc.gui.hud.setOverlayMessage(receipt.copy(), false);
        if (sound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, 1.0f, 1.0f));
        }
        return true;
    }

    private static String formatMoney(double value) {
        for (int i = 0; i < UNITS.length; i++) {
            if (value >= UNITS[i]) return trim(value / UNITS[i]) + SUFFIXES[i];
        }
        return trim(value);
    }

    private static String trim(double value) {
        if (Math.rint(value) == value) return Long.toString((long) value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
