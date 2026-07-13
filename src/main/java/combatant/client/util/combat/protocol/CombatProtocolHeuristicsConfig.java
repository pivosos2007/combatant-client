/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.combat.protocol;

import combatant.client.config.ConfigNameProvider;
import combatant.client.config.ConfigObject;
import combatant.client.config.ConfigSerializer;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.SetValue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CombatProtocolHeuristicsConfig implements ConfigObject, ConfigNameProvider {
    public static final CombatProtocolHeuristicsConfig INSTANCE = new CombatProtocolHeuristicsConfig();

    public static final String DEFAULT_LEGACY_PATTERN = "\\b1[._\\s-]+8(?:\\b|\\+)";
    public static final String DEFAULT_MODERN_PATTERN = "\\b1[._\\s-]+(?:9|[1-9][0-9])(?:[._\\s-]+[0-9]+)*(?:\\b|\\+)";

    private final EnumMap<CombatProtocolHeuristicSource, SetValue> legacyPatterns =
            new EnumMap<>(CombatProtocolHeuristicSource.class);
    private final EnumMap<CombatProtocolHeuristicSource, SetValue> modernPatterns =
            new EnumMap<>(CombatProtocolHeuristicSource.class);

    private CombatProtocolHeuristicsConfig() {
        for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
            legacyPatterns.put(source, new SetValue(source.key() + "_legacy_patterns", defaultLegacyPatterns()));
            modernPatterns.put(source, new SetValue(source.key() + "_modern_patterns", defaultModernPatterns()));
        }
        ConfigSerializer.load(this);
    }

    public static CombatProtocolHeuristicsConfig get() {
        return INSTANCE;
    }

    public Set<String> patterns(CombatProtocolHeuristicSource source, ProtocolFamily family) {
        SetValue value = value(source, family);
        return value == null ? Set.of() : new LinkedHashSet<>(value.get());
    }

    public void setPatterns(CombatProtocolHeuristicSource source, ProtocolFamily family, Iterable<String> patterns) {
        SetValue value = value(source, family);
        if (value == null) return;

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (patterns != null) {
            for (String pattern : patterns) {
                if (pattern == null) continue;
                String clean = pattern.trim();
                if (!clean.isEmpty()) normalized.add(clean);
            }
        }
        value.set(normalized);
        save();
    }

    public void reset(CombatProtocolHeuristicSource source) {
        if (source == null) return;
        legacyPatterns.get(source).set(defaultLegacyPatterns());
        modernPatterns.get(source).set(defaultModernPatterns());
        save();
    }

    public void save() {
        ConfigSerializer.requestSave(this);
    }

    @Override
    public String getConfigName() {
        return "combatprotocolheuristics";
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> values = new ArrayList<>();
        for (CombatProtocolHeuristicSource source : CombatProtocolHeuristicSource.values()) {
            values.add(legacyPatterns.get(source));
            values.add(modernPatterns.get(source));
        }
        return values;
    }

    private SetValue value(CombatProtocolHeuristicSource source, ProtocolFamily family) {
        if (source == null || family == null) return null;
        return family == ProtocolFamily.LEGACY ? legacyPatterns.get(source) : modernPatterns.get(source);
    }

    private static Set<String> defaultLegacyPatterns() {
        return Set.of(DEFAULT_LEGACY_PATTERN);
    }

    private static Set<String> defaultModernPatterns() {
        return Set.of(DEFAULT_MODERN_PATTERN);
    }

    public enum ProtocolFamily {
        LEGACY,
        MODERN
    }
}
