/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces your name (and optionally everyone else's) in rendered text, for recording.
 *
 * <p>Hooked at {@code StringDecomposer.iterateFormatted}, the path every vanilla text draw
 * and width measurement goes through, so layout stays consistent with what is drawn. That
 * path runs thousands of times per frame: the replacement table and regex are rebuilt at most
 * once per tick, and the common "no name in this string" case costs one regex scan.</p>
 */
@ModuleInfo(
        id = "nameprotect",
        displayName = "NameProtect",
        aliases = {"StreamerMode", "NameHider"},
        category = ModuleCategory.MISC, subcategory = ModuleSubcategory.CLIENT,
        description = "module.nameprotect.description")
public final class NameProtect extends Module {

    private final StringValue alias = text("alias", "You");
    private final BooleanValue hideOthers = bool("hide_others", false);
    private final EnumValue<OthersStyle> othersStyle =
            visibleWhen(enumMode("others_style", OthersStyle.NUMBERED), hideOthers::get);

    private final Minecraft mc = Minecraft.getInstance();

    // Volatile snapshot: text can be laid out off the client thread (e.g. chat narration).
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private long snapshotTick = Long.MIN_VALUE;
    private String snapshotSignature = "";

    /** Returns {@code text} itself when nothing needs replacing, so callers can test identity. */
    public static String apply(String text) {
        if (text == null || text.isEmpty()) return text;
        NameProtect module = Modules.get(NameProtect.class);
        if (module == null || !module.isEnabled()) return text;
        return module.replace(text);
    }

    private String replace(String text) {
        Snapshot current = currentSnapshot();
        if (current.pattern == null) return text;

        Matcher matcher = current.pattern.matcher(text);
        if (!matcher.find()) return text;

        StringBuilder out = new StringBuilder(text.length());
        int last = 0;
        do {
            String replacement = current.replacements.get(matcher.group(1).toLowerCase(Locale.ROOT));
            out.append(text, last, matcher.start()).append(replacement != null ? replacement : matcher.group(1));
            last = matcher.end();
        } while (matcher.find());
        return out.append(text, last, text.length()).toString();
    }

    private Snapshot currentSnapshot() {
        // Called for every string drawn, so the hot path is one comparison and no allocation.
        // Without a level (menus) game time stands still, so fall back to wall-clock ticks.
        long tick = mc.level != null ? mc.level.getGameTime() : System.currentTimeMillis() / 50L;
        // Only the client thread rebuilds; other threads keep using the last snapshot.
        if (tick == snapshotTick || !mc.isSameThread()) return snapshot;
        snapshotTick = tick;

        // Compiling a regex over a full tab list every tick is wasteful; rebuild only when the
        // settings or the set of online names actually changed.
        String signature = alias.get() + '|' + hideOthers.get() + '|' + othersStyle.get()
                + '|' + mc.getUser().getName() + '|' + rosterHash();
        if (!signature.equals(snapshotSignature)) {
            snapshot = buildSnapshot();
            snapshotSignature = signature;
        }
        return snapshot;
    }

    // Order-independent, so tab reshuffles do not force a rebuild.
    private long rosterHash() {
        ClientPacketListener connection = mc.getConnection();
        if (!hideOthers.get() || connection == null) return 0L;
        long sum = 0L;
        int count = 0;
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null) continue;
            sum += name.hashCode() * 0x9E3779B97F4A7C15L;
            count++;
        }
        return sum ^ count;
    }

    private Snapshot buildSnapshot() {
        Map<String, String> replacements = new HashMap<>();
        String self = mc.getUser().getName();
        if (self != null && !self.isBlank()) {
            String selfAlias = alias.get();
            replacements.put(self.toLowerCase(Locale.ROOT), selfAlias == null || selfAlias.isBlank() ? "You" : selfAlias);
        }

        ClientPacketListener connection = mc.getConnection();
        if (hideOthers.get() && connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name == null || name.isBlank()) continue;
                replacements.putIfAbsent(name.toLowerCase(Locale.ROOT), otherAlias(name));
            }
        }

        // An alias equal to the name would match forever through the decomposer hook.
        replacements.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(e.getValue()));
        return new Snapshot(buildPattern(replacements.keySet()), Map.copyOf(replacements));
    }

    private String otherAlias(String name) {
        return switch (othersStyle.get()) {
            case NUMBERED -> "Player " + (Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), 99) + 1);
            case GENERIC -> "Player";
            case BLANK -> "";
        };
    }

    private static Pattern buildPattern(Iterable<String> names) {
        List<String> sorted = new ArrayList<>();
        names.forEach(sorted::add);
        if (sorted.isEmpty()) return null;
        // Longest first, so "Steve123" wins over "Steve" when both are online.
        sorted.sort((a, b) -> Integer.compare(b.length(), a.length()));

        StringBuilder regex = new StringBuilder("(?i)(?<![A-Za-z0-9_])(");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) regex.append('|');
            regex.append(Pattern.quote(sorted.get(i)));
        }
        return Pattern.compile(regex.append(")(?![A-Za-z0-9_])").toString());
    }

    private record Snapshot(Pattern pattern, Map<String, String> replacements) {
        static final Snapshot EMPTY = new Snapshot(null, Map.of());
    }

    public enum OthersStyle {
        NUMBERED,
        GENERIC,
        BLANK
    }
}
