/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat.protocol;

import combatant.client.events.EventHandler;
import combatant.client.events.impl.CombatProtocolBossbarEvent;
import combatant.client.events.impl.PvpChatEvent;
import combatant.client.events.impl.PvpOverlayEvent;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.text.LegacyTextUtil;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CombatProtocolHeuristics {
    public static final CombatProtocolHeuristics INSTANCE = new CombatProtocolHeuristics();

    private static final long SIGNAL_TTL_MS = 300_000L;
    private static final Pattern[] LEGACY_PATTERNS = compile(
            "\\b1[._\\s-]+8(?:\\b|\\+)"
    );
    private static final Pattern[] MODERN_PATTERNS = compile(
            "\\b1[._\\s-]+(?:9|[1-9][0-9])(?:[._\\s-]+[0-9]+)*(?:\\b|\\+)"
    );

    private final Object lock = new Object();
    private final EnumMap<CombatProtocolHeuristicSource, Signal> signals =
            new EnumMap<>(CombatProtocolHeuristicSource.class);

    private CombatProtocolHeuristics() {
    }

    public static LinkedHashMap<String, Boolean> defaultSourceToggles() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
            defaults.put(source.key(), source == CombatProtocolHeuristicSource.BOSSBAR);
        }
        return defaults;
    }

    public Optional<Boolean> resolveLegacy(Map<String, Boolean> sourceToggles) {
        long now = System.currentTimeMillis();
        synchronized (lock) {
            Signal best = null;
            for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
                if (!sourceEnabled(sourceToggles, source)) continue;
                Signal signal = signals.get(source);
                if (signal == null || now - signal.timeMs > SIGNAL_TTL_MS) continue;
                if (best == null || signal.timeMs > best.timeMs) {
                    best = signal;
                }
            }
            return best == null ? Optional.empty() : Optional.of(best.legacy);
        }
    }

    public boolean resolveLegacyOrDefault(Map<String, Boolean> sourceToggles, boolean fallbackLegacy) {
        return resolveLegacy(sourceToggles).orElse(fallbackLegacy);
    }

    @EventHandler
    public void onOverlay(PvpOverlayEvent event) {
        if (event == null || event.message == null) return;
        accept(CombatProtocolHeuristicSource.OVERLAY, event.message.getString(), event.timeMs);
    }

    @EventHandler
    public void onChat(PvpChatEvent event) {
        if (event == null || event.message == null) return;
        CombatProtocolHeuristicSource source = event.source == PvpChatEvent.Source.CHAT_MESSAGE
                ? CombatProtocolHeuristicSource.CHAT
                : CombatProtocolHeuristicSource.GAME_MESSAGE;
        accept(source, event.message, event.timeMs);
    }

    @EventHandler
    public void onBossbar(CombatProtocolBossbarEvent event) {
        if (event == null || event.names == null || event.names.isEmpty()) return;
        for (String name : event.names) {
            accept(CombatProtocolHeuristicSource.BOSSBAR, name, event.timeMs);
        }
    }

    public void accept(CombatProtocolHeuristicSource source, String raw, long timeMs) {
        if (source == null || raw == null || raw.isBlank()) return;
        Boolean legacy = detectLegacy(raw);
        if (legacy == null) return;

        synchronized (lock) {
            signals.put(source, new Signal(legacy, timeMs, raw));
        }

        if (DebugLog.serverOnly()) {
            DebugLog.server("Combat protocol heuristic: source=%s legacy=%s raw=\"%s\"",
                    source.key(), legacy, raw);
        }
    }

    public static Boolean detectLegacy(String raw) {
        String text = normalize(raw);
        if (text.isBlank()) return null;

        boolean legacy = matchesAny(text, LEGACY_PATTERNS);
        boolean modern = matchesAny(text, MODERN_PATTERNS);
        if (legacy == modern) return null;
        return legacy;
    }

    private static boolean sourceEnabled(Map<String, Boolean> toggles, CombatProtocolHeuristicSource source) {
        return toggles != null && Boolean.TRUE.equals(toggles.get(source.key()));
    }

    private static String normalize(String raw) {
        String text = LegacyTextUtil.stripLegacy(raw);
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesAny(String text, Pattern[] patterns) {
        if (text == null || text.isBlank() || patterns == null) return false;
        for (Pattern pattern : patterns) {
            if (pattern != null && pattern.matcher(text).find()) return true;
        }
        return false;
    }

    private static Pattern[] compile(String... patterns) {
        if (patterns == null || patterns.length == 0) return new Pattern[0];
        return List.of(patterns).stream()
                .map(CombatProtocolHeuristics::compileOne)
                .filter(pattern -> pattern != null)
                .toArray(Pattern[]::new);
    }

    private static Pattern compileOne(String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException ignored) {
            return null;
        }
    }

    private record Signal(boolean legacy, long timeMs, String raw) {
    }
}
