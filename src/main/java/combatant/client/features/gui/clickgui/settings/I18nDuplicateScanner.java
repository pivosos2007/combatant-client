/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import combatant.client.util.logging.DebugLog;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum I18nDuplicateScanner {
    ;

    private static final Pattern KEY_PATTERN = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");

    public static void scan(ResourceManager manager, String reason) {
        if (manager == null) return;

        Map<Identifier, List<Resource>> resources = manager.listResourceStacks(
                "lang",
                id -> "combatant".equals(id.getNamespace()) && id.getPath().endsWith(".json")
        );
        if (resources.isEmpty()) return;

        StringBuilder report = new StringBuilder();
        int filesWithProblems = 0;
        int duplicateCount = 0;

        for (Map.Entry<Identifier, List<Resource>> entry : resources.entrySet()) {
            Identifier id = entry.getKey();
            List<Resource> stack = entry.getValue();
            if (id == null || stack == null || stack.isEmpty()) continue;

            StringBuilder fileReport = new StringBuilder();
            for (Resource resource : stack) {
                ScanResult result = scanResource(resource);
                if (!result.hasProblems()) continue;
                duplicateCount += result.problemCount();
                fileReport.append("  - ").append(resource.sourcePackId()).append('\n');
                for (String line : result.lines()) {
                    fileReport.append("    - ").append(line).append('\n');
                }
            }

            if (!fileReport.isEmpty()) {
                filesWithProblems++;
                report.append("- ").append(id).append('\n').append(fileReport);
            }
        }

        if (filesWithProblems > 0) {
            DebugLog.warn(
                    "I18n duplicate keys (reason=%s, files=%d, duplicates=%d):%n%s",
                    reason,
                    filesWithProblems,
                    duplicateCount,
                    report
            );
        }
    }

    private static ScanResult scanResource(Resource resource) {
        LinkedHashMap<String, Occurrence> exact = new LinkedHashMap<>();
        LinkedHashMap<String, Occurrence> caseFolded = new LinkedHashMap<>();
        ArrayList<String> lines = new ArrayList<>();
        if (resource == null) return new ScanResult(lines);

        try (Reader reader = resource.openAsReader();
             BufferedReader buffered = new BufferedReader(reader)) {
            String line;
            int lineNo = 0;
            while ((line = buffered.readLine()) != null) {
                lineNo++;
                Matcher matcher = KEY_PATTERN.matcher(line);
                if (!matcher.find()) continue;

                String key = matcher.group(1);
                Occurrence previousExact = exact.putIfAbsent(key, new Occurrence(key, lineNo));
                if (previousExact != null) {
                    lines.add("exact line " + lineNo + " duplicates line " + previousExact.line() + ": " + key);
                    continue;
                }

                String folded = key.toLowerCase(Locale.ROOT);
                Occurrence previousFolded = caseFolded.putIfAbsent(folded, new Occurrence(key, lineNo));
                if (previousFolded != null && !previousFolded.key().equals(key)) {
                    lines.add("case line " + lineNo + " duplicates line " + previousFolded.line()
                            + ": " + previousFolded.key() + " / " + key);
                }
            }
        } catch (IOException e) {
            lines.add("scan failed: " + e.getMessage());
        }

        return new ScanResult(lines);
    }

    private record Occurrence(String key, int line) {
    }

    private record ScanResult(List<String> lines) {
        boolean hasProblems() {
            return !lines.isEmpty();
        }

        int problemCount() {
            return lines.size();
        }
    }
}
