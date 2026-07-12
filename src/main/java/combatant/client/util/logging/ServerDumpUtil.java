/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.logging;

import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum ServerDumpUtil {
    ;

    private static final Map<String, String> LAST_SIGNATURES = new ConcurrentHashMap<>();

    public static void dumpTabText(String section, Component text) {
        if (text == null || !DebugLog.serverOnly()) return;

        String raw = text.getString();
        if (raw.isBlank()) return;

        String key = "tab:" + section;
        if (!markChanged(key, raw)) return;

        DebugLog.server("==== TAB %s ====", section);
        for (String line : raw.split("\\R")) {
            DebugLog.server("[TAB] %s", line);
        }
        DebugLog.server("========================");
    }

    public static void dumpScoreboardSidebar(Objective objective,
                                             List<PlayerScoreEntry> entries,
                                             List<String> renderedLabels,
                                             List<String> renderedValues) {
        if (objective == null || !DebugLog.serverOnly()) return;

        StringBuilder signature = new StringBuilder();
        signature.append(objective.getName()).append('|')
                .append(objective.getDisplayName().getString()).append('|');
        for (int i = 0; i < entries.size(); i++) {
            PlayerScoreEntry entry = entries.get(i);
            signature.append(i).append(':')
                    .append(entry.value()).append(':')
                    .append(entry.owner()).append(':')
                    .append(entry.ownerName().getString()).append('\n');
        }

        if (!markChanged("scoreboard:sidebar", signature.toString())) return;

        DebugLog.server("[ScoreboardDump] objectiveName=%s display=\"%s\" entries=%d",
                objective.getName(), objective.getDisplayName().getString(), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            PlayerScoreEntry entry = entries.get(i);
            PlayerTeam team = objective.getScoreboard().getPlayersTeam(entry.owner());
            Component decorated = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            String renderedLabel = i < renderedLabels.size() ? renderedLabels.get(i) : "<skipped>";
            String renderedValue = i < renderedValues.size() ? renderedValues.get(i) : "<skipped>";
            DebugLog.server("[ScoreboardDump] [%d] score=%d owner=\"%s\" rawName=\"%s\" decorated=\"%s\" label=\"%s\" value=\"%s\"",
                    i,
                    entry.value(),
                    entry.owner(),
                    entry.ownerName().getString(),
                    decorated.getString(),
                    renderedLabel,
                    renderedValue);
        }
    }

    private static boolean markChanged(String key, String signature) {
        String previous = LAST_SIGNATURES.put(key, signature);
        return !signature.equals(previous);
    }
}
