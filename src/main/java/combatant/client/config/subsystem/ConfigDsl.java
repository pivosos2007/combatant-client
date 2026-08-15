/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Lightweight declaration DSL shared by subsystem configs. */
public final class ConfigDsl {
    private final List<ConfigValue<?>> values = new ArrayList<>();

    public BooleanValue bool(String name, boolean defaultValue) {
        return value(new BooleanValue(name, defaultValue));
    }

    public ModeValue mode(String name, String defaultValue, String... options) {
        return value(new ModeValue(name, defaultValue, options));
    }

    public <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, Class<E> enumClass) {
        return value(new EnumValue<>(name, defaultValue, enumClass));
    }

    public <N extends Number> NumberValue<N> number(String name, N defaultValue, N min, N max) {
        return value(new NumberValue<>(name, defaultValue, min, max));
    }

    public SetValue stringSet(String name) {
        return value(new SetValue(name));
    }

    public SetValue stringSet(String name, Set<String> defaultValue) {
        return value(new SetValue(name, defaultValue));
    }

    public <V extends ConfigValue<?>> V value(V value) {
        V nonNull = Objects.requireNonNull(value, "value");
        values.add(nonNull);
        return nonNull;
    }

    public List<ConfigValue<?>> values() {
        return List.copyOf(values);
    }

    public List<SettingDef> settings(SettingDef... definitions) {
        if (definitions == null || definitions.length == 0) return List.of();
        List<SettingDef> out = new ArrayList<>(definitions.length);
        for (SettingDef definition : definitions) {
            if (definition != null) out.add(definition);
        }
        return List.copyOf(out);
    }
}
