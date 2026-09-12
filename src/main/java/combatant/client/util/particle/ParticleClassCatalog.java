/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.particle;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import net.minecraft.client.particle.Particle;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lazily discovered catalog of client particle implementation classes.
 *
 * <p>The picker stores class names rather than particle registry ids. This is
 * deliberate: one particle option can resolve to different runtime particle
 * implementations and modded particles do not have to be known to Combatant
 * at compile time.</p>
 */
public enum ParticleClassCatalog {
    ;

    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Entry::id, String.CASE_INSENSITIVE_ORDER);

    private static volatile List<Entry> discovered;

    public static List<Entry> entries(Set<String> selectedIds) {
        LinkedHashMap<String, Entry> merged = new LinkedHashMap<>();
        for (Entry entry : discoveredEntries()) {
            merged.putIfAbsent(entry.id(), entry);
        }

        if (selectedIds != null) {
            for (String id : selectedIds) {
                if (id == null || id.isBlank()) continue;
                merged.putIfAbsent(id, new Entry(id, labelForClassName(id)));
            }
        }

        List<Entry> out = new ArrayList<>(merged.values());
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<Entry> discoveredEntries() {
        List<Entry> snapshot = discovered;
        if (snapshot != null) return snapshot;

        synchronized (ParticleClassCatalog.class) {
            snapshot = discovered;
            if (snapshot != null) return snapshot;

            LinkedHashMap<String, Entry> found = new LinkedHashMap<>();
            try (ScanResult scan = new ClassGraph()
                    .enableClassInfo()
                    .ignoreClassVisibility()
                    .scan()) {
                for (ClassInfo info : scan.getSubclasses(Particle.class.getName())) {
                    String className = info.getName();
                    if (className == null || className.isBlank()) continue;
                    found.putIfAbsent(className, new Entry(className, labelForClassName(className)));
                }
            } catch (Throwable error) {
                DebugLog.warnOnce(
                        "particle-class-catalog-discovery",
                        "Failed to discover particle implementation classes: %s",
                        error.toString()
                );
            }

            snapshot = new ArrayList<>(found.values());
            snapshot.sort(ENTRY_ORDER);
            discovered = List.copyOf(snapshot);
            return discovered;
        }
    }

    public static String labelForClassName(String className) {
        if (className == null || className.isBlank()) return "";

        int packageSplit = className.lastIndexOf('.');
        String localName = packageSplit >= 0 ? className.substring(packageSplit + 1) : className;
        String[] nested = localName.split("\\$");
        StringBuilder label = new StringBuilder(localName.length() + 8);
        for (String part : nested) {
            if (part == null || part.isBlank()) continue;
            if (label.length() > 0) label.append(" · ");
            label.append(humanize(part));
        }
        return label.length() == 0 ? className : label.toString();
    }

    private static String humanize(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) return "";

        String base = simpleName;
        if (base.endsWith("Particle") && base.length() > "Particle".length()) {
            base = base.substring(0, base.length() - "Particle".length());
        }

        StringBuilder out = new StringBuilder(base.length() + 8);
        for (int i = 0; i < base.length(); i++) {
            char ch = base.charAt(i);
            char prev = i > 0 ? base.charAt(i - 1) : 0;
            char next = i + 1 < base.length() ? base.charAt(i + 1) : 0;
            boolean boundary = i > 0 && Character.isUpperCase(ch)
                    && (Character.isLowerCase(prev) || (next != 0 && Character.isLowerCase(next)));
            if (boundary) out.append(' ');
            out.append(ch);
        }

        String text = out.toString().replace('_', ' ').trim();
        if (text.isEmpty()) return simpleName;
        if (text.length() == 1) return text.toUpperCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    public record Entry(String id, String label) {
    }
}
