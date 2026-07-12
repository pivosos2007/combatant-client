/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command;


import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public enum CommandOutput {
    ;

    public static final String PREFIX = "[Combatant]";
    private static final int MAX_RECENT_MESSAGES = 128;
    private static final Map<Component, Tone> COMBATANT_MESSAGES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Deque<MessageLine> RECENT_MESSAGES = new ArrayDeque<>();

    public record MessageLine(Component text, long timestampMs, Tone tone) {
    }

    public enum Tone {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public static void send(String message) {
        send(message, Tone.INFO);
    }

    public static void success(String message) {
        send(message, Tone.SUCCESS);
    }

    public static void warning(String message) {
        send(message, Tone.WARNING);
    }

    public static void error(String message) {
        send(message, Tone.ERROR);
    }

    public static void send(String message, Tone tone) {
        if (message == null || message.isBlank()) return;
        send(Component.literal(message), tone == null ? Tone.INFO : tone);
    }

    public static void send(Component text) {
        send(text, Tone.INFO);
    }

    public static void send(Component text, Tone tone) {
        if (text == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return;

        Tone resolvedTone = tone == null ? Tone.INFO : tone;
        Component formatted = mark(format(text, resolvedTone), resolvedTone);
        remember(formatted, resolvedTone);
        mc.gui.hud.getChat().addClientSystemMessage(formatted);
    }

    public static boolean isCombatantMessage(Component text) {
        if (text == null) return false;
        synchronized (COMBATANT_MESSAGES) {
            return COMBATANT_MESSAGES.containsKey(text);
        }
    }

    public static Tone toneOf(Component text) {
        if (text == null) return Tone.INFO;
        synchronized (COMBATANT_MESSAGES) {
            Tone tone = COMBATANT_MESSAGES.get(text);
            return tone == null ? Tone.INFO : tone;
        }
    }

    public static List<MessageLine> recentMessages() {
        synchronized (RECENT_MESSAGES) {
            return new ArrayList<>(RECENT_MESSAGES);
        }
    }

    public static void clearRecentMessages() {
        synchronized (RECENT_MESSAGES) {
            RECENT_MESSAGES.clear();
        }
    }

    private static void remember(Component text, Tone tone) {
        synchronized (RECENT_MESSAGES) {
            RECENT_MESSAGES.addLast(new MessageLine(text, System.currentTimeMillis(), tone));
            while (RECENT_MESSAGES.size() > MAX_RECENT_MESSAGES) {
                RECENT_MESSAGES.removeFirst();
            }
        }
    }

    private static Component mark(Component text, Tone tone) {
        synchronized (COMBATANT_MESSAGES) {
            COMBATANT_MESSAGES.put(text, tone == null ? Tone.INFO : tone);
        }
        return text;
    }

    private static MutableComponent format(Component body, Tone tone) {
        MutableComponent out = Component.empty();
        appendGradientPrefix(out, tone);
        out.append(Component.literal(" "));
        out.append(copyBody(body, bodyColor(tone)).withStyle(style -> style.withBold(false)));
        return out;
    }

    private static MutableComponent copyBody(Component body, int color) {
        MutableComponent copy = body.copy();
        return copy.withStyle(style -> style.withColor(color & 0x00FFFFFF));
    }

    private static void appendGradientPrefix(MutableComponent out, Tone tone) {
        String prefix = PREFIX;
        int start = prefixStartColor(tone);
        int end = prefixEndColor(tone);
        int steps = Math.max(1, prefix.length() - 1);
        for (int i = 0; i < prefix.length(); i++) {
            final int color = mixRgb(start, end, i / (float) steps) & 0x00FFFFFF;
            out.append(Component.literal(String.valueOf(prefix.charAt(i)))
                    .withStyle(style -> style.withColor(color).withBold(true)));
        }
    }

    public static int prefixStartColor(Tone tone) {
        Themes.Theme theme = Theme.theme();
        int accent = theme.accent();
        return switch (tone) {
            case SUCCESS -> mixRgb(accent, 0xFF55FF93, 0.45f);
            case WARNING -> mixRgb(accent, 0xFFFFC85A, 0.45f);
            case ERROR -> mixRgb(accent, 0xFFFF6A6A, 0.55f);
            default -> accent;
        };
    }

    public static int prefixEndColor(Tone tone) {
        Themes.Theme theme = Theme.theme();
        int text = theme.textPrimary();
        int accentSoft = theme.accentSoft();
        return switch (tone) {
            case SUCCESS -> mixRgb(text, 0xFF55FF93, 0.28f);
            case WARNING -> mixRgb(text, 0xFFFFD985, 0.32f);
            case ERROR -> mixRgb(text, 0xFFFF8A8A, 0.42f);
            default -> mixRgb(text, accentSoft, 0.35f);
        };
    }

    public static int bodyColor(Tone tone) {
        Themes.Theme theme = Theme.theme();
        int base = mixRgb(theme.textPrimary(), 0xFFFFFFFF, 0.24f);
        return switch (tone) {
            case SUCCESS -> mixRgb(base, 0xFFBFFFE0, 0.18f);
            case WARNING -> mixRgb(base, 0xFFFFE5A8, 0.18f);
            case ERROR -> mixRgb(base, 0xFFFFB0B0, 0.25f);
            default -> base;
        };
    }

    public static int mixRgb(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
