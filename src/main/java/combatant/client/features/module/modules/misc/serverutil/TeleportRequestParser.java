/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc.serverutil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Extracts the requester's name from a teleport-request chat line. A pattern yields the name from the
 * named group {@code name}, or from group 1 when it has none.
 */
public final class TeleportRequestParser {

    public static final List<String> DEFAULT_PATTERNS = List.of(
            "^(?:\\[[^\\]]*\\]\\s*)*(?<name>\\.?\\w{1,16})\\s+(?:has requested to teleport to you|wants to teleport to you"
                    + "|has requested that you teleport to (?:them|him|her)|sent you a teleport request)",
            "^(?:Teleport request from|TPA request from)\\s+(?<name>\\.?\\w{1,16})",
            "(?<name>\\.?\\w{1,16})\\s+(?:хочет|просит)\\s+телепортироваться"
    );

    private final List<Pattern> patterns;

    private TeleportRequestParser(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    /** Builds a parser from the defaults (optional) followed by user patterns; invalid user patterns are skipped. */
    public static TeleportRequestParser of(Collection<String> custom, boolean includeDefaults) {
        List<Pattern> compiled = new ArrayList<>();
        if (includeDefaults) {
            for (String raw : DEFAULT_PATTERNS) compiled.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
        }
        if (custom != null) {
            for (String raw : custom) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    compiled.add(Pattern.compile(raw.trim(), Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException ignored) {
                    // a broken user regex must not disable the rest
                }
            }
        }
        return new TeleportRequestParser(List.copyOf(compiled));
    }

    /** Error message for an invalid regex, or null when it compiles. */
    public static String validate(String regex) {
        try {
            Pattern.compile(regex);
            return null;
        } catch (PatternSyntaxException e) {
            return e.getDescription();
        }
    }

    public int patternCount() {
        return patterns.size();
    }

    /** The requester's name, or null when the line is not a teleport request. */
    public String senderOf(String message) {
        String line = ServerChatText.stripSection(message);
        if (line.isEmpty()) return null;
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(line);
            if (!m.find()) continue;
            String name = extractName(m);
            if (ServerChatText.isPlayerName(name)) return name;
        }
        return null;
    }

    private static String extractName(Matcher m) {
        try {
            String named = m.group("name");
            if (named != null) return named;
        } catch (IllegalArgumentException ignored) {
            // pattern has no "name" group: fall back to group 1
        }
        return m.groupCount() >= 1 ? m.group(1) : null;
    }
}
