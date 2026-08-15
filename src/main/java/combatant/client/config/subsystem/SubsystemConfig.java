/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.ConfigNameProvider;
import combatant.client.config.ConfigSerializer;
import combatant.client.config.JsonConfigObject;
import combatant.client.config.SettingDef;
import combatant.client.config.SettingDefProvider;
import combatant.client.config.SettingOwner;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.SetValue;

import java.util.List;
import java.util.Set;

/** Base for independently persisted subsystem configs. */
public abstract class SubsystemConfig implements JsonConfigObject, ConfigNameProvider, SettingOwner, SettingDefProvider {
    private final ConfigDsl dsl = new ConfigDsl();

    protected final BooleanValue bool(String name, boolean defaultValue) {
        return dsl.bool(name, defaultValue);
    }

    protected final ModeValue mode(String name, String defaultValue, String... options) {
        return dsl.mode(name, defaultValue, options);
    }

    protected final <E extends Enum<E>> EnumValue<E> enumValue(String name, E defaultValue, Class<E> enumClass) {
        return dsl.enumValue(name, defaultValue, enumClass);
    }

    protected final <N extends Number> NumberValue<N> number(String name, N defaultValue, N min, N max) {
        return dsl.number(name, defaultValue, min, max);
    }

    protected final SetValue stringSet(String name) {
        return dsl.stringSet(name);
    }

    protected final SetValue stringSet(String name, Set<String> defaultValue) {
        return dsl.stringSet(name, defaultValue);
    }

    protected final <V extends ConfigValue<?>> V value(V value) {
        return dsl.value(value);
    }

    protected final List<SettingDef> settings(SettingDef... definitions) {
        return dsl.settings(definitions);
    }

    /** Call at the end of the concrete constructor, after all values were declared. */
    protected final void loadConfig() {
        ConfigSerializer.load(this);
        afterLoad();
    }

    protected void afterLoad() {
    }

    protected void beforeSave() {
    }

    @Override
    public final List<ConfigValue<?>> getConfigValues() {
        return dsl.values();
    }

    @Override
    public final String getConfigName() {
        ConfigSubsystem metadata = metadata();
        String path = metadata.value().trim().replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    @Override
    public String name() {
        ConfigSubsystem metadata = metadata();
        String owner = metadata.settingOwner();
        return owner == null || owner.isBlank() ? getConfigName() : owner.trim();
    }

    @Override
    public final void saveConfig() {
        beforeSave();
        ConfigSerializer.requestSave(this);
    }

    private ConfigSubsystem metadata() {
        ConfigSubsystem metadata = getClass().getAnnotation(ConfigSubsystem.class);
        if (metadata == null) {
            throw new IllegalStateException(getClass().getName() + " must be annotated with @ConfigSubsystem");
        }
        return metadata;
    }
}
