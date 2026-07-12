/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import combatant.client.config.ConfigNameProvider;
import combatant.client.config.ConfigObject;
import combatant.client.config.ConfigSerializer;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Хранит список игнорируемых сущностей (типов) для логики хитбоксов/рейкаста.
 * Использует новую систему конфигов (ConfigSerializer).
 */
public final class EntityFilters implements ConfigObject, ConfigNameProvider {

    public static final EntityFilters INSTANCE = new EntityFilters();

    private final ItemIdSetValue ignoredEntities = new ItemIdSetValue("ignored_entities");

    private EntityFilters() {
        ConfigSerializer.load(this);
        ensureSaved();
    }

    public static EntityFilters get() {
        return INSTANCE;
    }

    @Override
    public String getConfigName() {
        return "entityfilters";
    }

    public boolean isIgnoredEntity(String id) {
        return ignoredEntities.get().contains(normalize(id));
    }

    public Set<String> getIgnoredEntities() {
        return ignoredEntities.get();
    }

    public void addIgnoredEntity(String id) {
        String norm = normalize(id);
        ignoredEntities.get().add(norm);
        DebugLog.info("[EntityFilters] add %s -> %s", norm, ignoredEntities.get());
        save();
    }

    public void removeIgnoredEntity(String id) {
        String norm = normalize(id);
        ignoredEntities.get().remove(norm);
        DebugLog.info("[EntityFilters] remove %s -> %s", norm, ignoredEntities.get());
        save();
    }

    public ItemIdSetValue getIgnoredEntitiesValue() {
        return ignoredEntities;
    }

    public void save() {
        ConfigSerializer.requestSave(this);
    }

    private void ensureSaved() {
        try {
            ConfigSerializer.save(this);
        } catch (Exception ignored) {
        }
    }

    private String normalize(String id) {
        if (id == null) return "";
        String s = id.trim().toLowerCase();
        if (!s.contains(":")) s = "minecraft:" + s;
        return s;
    }

    @Override
    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> list = new ArrayList<>();
        list.add(ignoredEntities);
        return list;
    }
}
