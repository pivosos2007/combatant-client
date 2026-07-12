/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.Rect2i;
import combatant.client.mixins.accessors.ChatInputSuggestorAccessor;
import combatant.client.mixins.accessors.ChatScreenAccessor;
import combatant.client.mixins.accessors.ChatScreenSuggestorAccessor;
import combatant.client.mixins.accessors.SuggestionWindowAccessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Reflection helpers for vanilla ChatInputSuggestor; lets us draw suggestions in custom style.
 */
public enum CommandSuggestorBridge {
    ;

    private static CommandSuggestions.SuggestionsList lastWindow;
    private static String lastError = "";

    /**
     * Peek current suggestor window without mutating state.
     */
    public static CommandSuggestions.SuggestionsList peekWindow(ChatScreen screen) {
        try {
            Object suggestor = findSuggestor(screen);
            if (suggestor == null) return null;
            CommandSuggestions s = (CommandSuggestions) suggestor;
            return ((ChatInputSuggestorAccessor) s).getCombatant$window();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static SuggestionSnapshot snapshot(ChatScreen screen) {
        try {
            Object suggestor = findSuggestor(screen);
            if (suggestor == null) {
                note("no suggestor");
                return null;
            }
            CommandSuggestions s = (CommandSuggestions) suggestor;
            CommandSuggestions.SuggestionsList window = ((ChatInputSuggestorAccessor) s).getCombatant$window();

            CommandContext ctx = commandContext(screen);
            List<Suggestion> manual = buildTellLikeSuggestions(ctx);

            if (window == null) {
                if (manual.isEmpty()) {
                    note("no window");
                    return null;
                }
                List<Suggestion> merged = mergeSuggestions(List.of(), manual);
                List<String> texts = collectTexts(merged);
                int selection = 0;
                int start = 0;
                int visible = Math.min(5, Math.max(1, merged.size()));
                note("manual");
                lastWindow = null;
                return new SuggestionSnapshot(0, 0, 0, 0, texts, merged, selection, start, visible);
            }

            SuggestionWindowAccessor acc = (SuggestionWindowAccessor) window;
            Rect2i area = acc.getCombatant$area();
            int x = area.getX();
            int y = area.getY();
            int w = area.getWidth();
            int h = area.getHeight();

            List<Suggestion> suggestions = collectSuggestions(acc.getCombatant$suggestions(), currentToken(screen), isCommand(screen));
            suggestions = mergeSuggestions(suggestions, manual);
            List<String> texts = collectTexts(suggestions);
            int selection = clamp(acc.getCombatant$selection(), suggestions.size());
            int start = clamp(acc.getCombatant$inWindowIndex(), suggestions.size());
            int visible = Math.max(1, h / 12);

            note("ok");
            lastWindow = window;
            return new SuggestionSnapshot(x, y, w, h, texts, suggestions, selection, start, visible);
        } catch (Throwable ignored) {
            note("exception");
            return null;
        }
    }

    private static void note(String msg) {
        if (!msg.equals(lastError)) {
            DebugLog.info("[BetterChat] Suggest snapshot: %s", msg);
            lastError = msg;
        }
    }

    private static Object findSuggestor(ChatScreen screen) {
        if (screen instanceof ChatScreenSuggestorAccessor acc) {
            return acc.getCombatant$suggestor();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static List<Suggestion> collectSuggestions(List<?> list, String token, boolean isCommand) {
        if (list == null || list.isEmpty()) return List.of();
        List<Suggestion> out = new ArrayList<>(list.size());
        for (Object obj : list) {
            if (obj instanceof Suggestion s) {
                out.add(s);
            } else if (obj != null) {
                out.add(new Suggestion(StringRange.at(0), obj.toString()));
            }
        }
        return out;
    }

    private static List<String> collectTexts(List<Suggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return List.of();
        List<String> texts = new ArrayList<>(suggestions.size());
        for (Suggestion s : suggestions) {
            texts.add(s.getText());
        }
        return texts;
    }

    public static CommandSuggestions.SuggestionsList lastWindow() {
        return lastWindow;
    }

    private static String currentToken(ChatScreen screen) {
        try {
            EditBox field = ((ChatScreenAccessor) screen).getChatField();
            if (field == null) return "";
            String text = field.getValue();
            int cursor = field.getCursorPosition();
            if (cursor > text.length()) cursor = text.length();
            String before = text.substring(0, cursor);
            int lastSpace = before.lastIndexOf(' ');
            String token = lastSpace >= 0 ? before.substring(lastSpace + 1) : before;
            return token.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isCommand(ChatScreen screen) {
        try {
            EditBox field = ((ChatScreenAccessor) screen).getChatField();
            if (field == null) return false;
            String text = field.getValue();
            return text != null && text.startsWith("/");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static CommandContext commandContext(ChatScreen screen) {
        try {
            EditBox field = ((ChatScreenAccessor) screen).getChatField();
            if (field == null) return null;
            String text = field.getValue();
            if (text == null || !text.startsWith("/")) return null;
            int cursor = Math.min(field.getCursorPosition(), text.length());
            String before = text.substring(0, cursor);
            String afterSlash = before.substring(1);
            String[] parts = afterSlash.split("\\s+", -1);
            if (parts.length == 0) return null;
            String cmd = parts[0].toLowerCase(Locale.ROOT);
            int argIndex = parts.length - 1;
            String token = parts[parts.length - 1];
            int tokenStart = before.lastIndexOf(' ');
            tokenStart = tokenStart >= 0 ? tokenStart + 1 : 1;
            return new CommandContext(cmd, argIndex, token, tokenStart);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<Suggestion> buildTellLikeSuggestions(CommandContext ctx) {
        if (ctx == null) return List.of();
        if (ctx.argIndex != 1) return List.of();
        if (!(ctx.command.equals("tell") || ctx.command.equals("msg") || ctx.command.equals("m"))) return List.of();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return List.of();
        String tokenLower = ctx.token.toLowerCase(Locale.ROOT);
        int start = Math.max(0, ctx.tokenStart);
        int end = Math.max(start, start + ctx.token.length());
        List<Suggestion> out = new ArrayList<>();
        String self = mc.getUser().getName();
        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            String name = entry.getProfile().name();
            if (name == null || name.isEmpty()) continue;
            if (name.equals(self)) continue;
            String lower = name.toLowerCase(Locale.ROOT);
            if (!tokenLower.isEmpty() && !lower.startsWith(tokenLower)) continue;
            out.add(new Suggestion(StringRange.between(start, end), name));
        }
        return out;
    }

    private static List<Suggestion> mergeSuggestions(List<Suggestion> base, List<Suggestion> extra) {
        if ((base == null || base.isEmpty()) && (extra == null || extra.isEmpty())) return List.of();
        LinkedHashMap<String, Suggestion> map = new LinkedHashMap<>();
        if (base != null) {
            for (Suggestion s : base) {
                if (s == null || s.getText() == null) continue;
                map.put(s.getText(), s);
            }
        }
        if (extra != null) {
            for (Suggestion s : extra) {
                if (s == null || s.getText() == null) continue;
                map.putIfAbsent(s.getText(), s);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static int clamp(int idx, int size) {
        if (size <= 0) return 0;
        return Math.max(0, Math.min(idx, size - 1));
    }

    private static boolean looksLikeName(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) return false;
        }
        return true;
    }

    public record SuggestionSnapshot(int x, int y, int w, int h,
                                     List<String> texts,
                                     List<Suggestion> suggestions,
                                     int selection,
                                     int startIndex,
                                     int visibleCount) {
    }

    private record CommandContext(String command, int argIndex, String token, int tokenStart) {
    }
}


