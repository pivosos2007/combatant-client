/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.SetValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ModuleInfo(
        id = "nameprotect",
        displayName = "NameProtect",
        aliases = {"StreamerMode", "NameHider"},
        category = ModuleCategory.MISC, subcategory = ModuleSubcategory.CLIENT,
        description = "module.nameprotect.description")
public final class NameProtect extends Module {

    private final StringValue alias = text("alias", "You");
    private final SetValue aliases = textList("aliases", "aliases", TextListSetting.PickerMode.TEXT);
    private final BooleanValue hideOthers = bool("hide_others", false);
    private final EnumValue<OthersStyle> othersStyle =
            visibleWhen(enumMode("others_style", OthersStyle.NUMBERED), hideOthers::get);

    private final Minecraft mc = Minecraft.getInstance();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private long snapshotTick = Long.MIN_VALUE;
    private String snapshotSignature = "";
    private volatile long revision = 1L;

    public enum State {
        RAW,
        DISPLAY,
        MEASURE,
        NARRATION
    }

    public static String apply(String text) {
        return apply(text, State.DISPLAY);
    }

    public static String applyDisplay(String text) {
        return apply(text, State.DISPLAY);
    }

    public static String applyMeasure(String text) {
        return apply(text, State.MEASURE);
    }

    public static String applyNarration(String text) {
        return apply(text, State.NARRATION);
    }

    public static String apply(String text, State state) {
        if (text == null || text.isEmpty() || state == State.RAW) return text;
        NameProtect module = Modules.get(NameProtect.class);
        if (module == null || !module.isEnabled()) return text;
        return module.replace(text);
    }

    public static DisplayMapping mapDisplay(String text) {
        if (text == null) return new DisplayMapping("", new int[]{0});
        NameProtect module = Modules.get(NameProtect.class);
        if (module == null || !module.isEnabled() || text.isEmpty()) {
            return DisplayMapping.identity(text);
        }
        return module.map(text);
    }

    public static long revision() {
        NameProtect module = Modules.get(NameProtect.class);
        if (module == null || !module.isEnabled()) return 0L;
        module.currentSnapshot();
        return module.revision;
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

    private DisplayMapping map(String text) {
        Snapshot current = currentSnapshot();
        if (current.pattern == null) return DisplayMapping.identity(text);
        Matcher matcher = current.pattern.matcher(text);
        if (!matcher.find()) return DisplayMapping.identity(text);

        StringBuilder out = new StringBuilder(text.length());
        List<Integer> boundaries = new ArrayList<>(text.length() + 1);
        boundaries.add(0);
        int raw = 0;
        do {
            while (raw < matcher.start()) {
                out.append(text.charAt(raw));
                raw++;
                boundaries.add(raw);
            }

            String source = matcher.group(1);
            String replacement = current.replacements.get(source.toLowerCase(Locale.ROOT));
            if (replacement == null) replacement = source;
            int rawStart = matcher.start();
            int rawLength = matcher.end() - rawStart;
            int replacementLength = replacement.length();
            out.append(replacement);
            for (int i = 1; i <= replacementLength; i++) {
                int mapped = rawStart + Math.round(rawLength * (i / (float) Math.max(1, replacementLength)));
                boundaries.add(Math.min(matcher.end(), mapped));
            }
            raw = matcher.end();
        } while (matcher.find());

        while (raw < text.length()) {
            out.append(text.charAt(raw));
            raw++;
            boundaries.add(raw);
        }

        int[] map = new int[boundaries.size()];
        for (int i = 0; i < map.length; i++) map[i] = boundaries.get(i);
        return new DisplayMapping(out.toString(), map);
    }

    private Snapshot currentSnapshot() {
        long tick = mc.level != null ? mc.level.getGameTime() : System.currentTimeMillis() / 50L;
        if (tick == snapshotTick || !mc.isSameThread()) return snapshot;
        snapshotTick = tick;

        Set<String> manualAliases = aliases.get();
        String signature = alias.get() + '|' + manualAliases + '|' + hideOthers.get() + '|' + othersStyle.get()
                + '|' + mc.getUser().getName() + '|' + rosterHash();
        if (!signature.equals(snapshotSignature)) {
            snapshot = buildSnapshot(manualAliases);
            snapshotSignature = signature;
            revision++;
        }
        return snapshot;
    }

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

    private Snapshot buildSnapshot(Set<String> manualAliases) {
        Map<String, String> replacements = new HashMap<>();
        String selfAlias = alias.get();
        if (selfAlias == null || selfAlias.isBlank()) selfAlias = "You";

        addSelfReplacement(replacements, mc.getUser().getName(), selfAlias);
        if (manualAliases != null) {
            for (String name : manualAliases) addSelfReplacement(replacements, name, selfAlias);
        }

        ClientPacketListener connection = mc.getConnection();
        if (hideOthers.get() && connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name == null || name.isBlank()) continue;
                replacements.putIfAbsent(name.toLowerCase(Locale.ROOT), otherAlias(name));
            }
        }

        String replacementAlias = selfAlias;
        replacements.entrySet().removeIf(e -> e.getKey().equalsIgnoreCase(e.getValue())
                || e.getKey().equalsIgnoreCase(replacementAlias));
        return new Snapshot(buildPattern(replacements.keySet()), Map.copyOf(replacements));
    }

    private static void addSelfReplacement(Map<String, String> replacements, String source, String target) {
        if (source == null || source.isBlank()) return;
        String trimmed = source.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(target)) return;
        replacements.put(trimmed.toLowerCase(Locale.ROOT), target);
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

    public record DisplayMapping(String text, int[] rawBoundaries) {
        static DisplayMapping identity(String text) {
            int[] boundaries = new int[text.length() + 1];
            for (int i = 0; i <= text.length(); i++) boundaries[i] = i;
            return new DisplayMapping(text, boundaries);
        }

        public int rawOffset(int displayBoundary) {
            if (rawBoundaries.length == 0) return 0;
            int index = Math.max(0, Math.min(displayBoundary, rawBoundaries.length - 1));
            return rawBoundaries[index];
        }
    }

    public enum OthersStyle {
        NUMBERED,
        GENERIC,
        BLANK
    }
}
