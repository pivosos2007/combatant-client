/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.seedcracker;

import combatant.client.features.command.CommandOutput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/** All upstream chat messages, including copyable seeds, pass through CommandOutput. */
public final class SeedCrackerOutput {
    private static String previousMessage = "";
    private static long previousTimeMs;
    private SeedCrackerOutput() { }

    public static void forward(Component message) {
        if (message == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (!mc.isSameThread()) {
            mc.execute(() -> forward(message));
            return;
        }
        // Only suppress immediate identical emissions, not later discovery/progress.
        String plain = message.getString();
        if (plain.isBlank()) return;
        long now = System.currentTimeMillis();
        if (plain.equals(previousMessage) && now - previousTimeMs < 800L) return;
        previousMessage = plain;
        previousTimeMs = now;
        CommandOutput.send(message, tone(message));
    }

    public static void feedback(String text, ChatFormatting color) {
        if (text == null || text.isBlank()) return;
        forward(Component.literal(text).withStyle(color));
    }

    private static CommandOutput.Tone tone(Component message) {
        TextColor color = message.getStyle().getColor();
        if (color == null) return CommandOutput.Tone.INFO;

        int rgb = color.getValue() & 0xFFFFFF;

        if (rgb == 0xFF5555 || rgb == 0xAA0000)
            return CommandOutput.Tone.ERROR;

        if (rgb == 0x55FF55 || rgb == 0x00AA00)
            return CommandOutput.Tone.SUCCESS;

        return CommandOutput.Tone.INFO;
    }
}
