/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
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
 * Stores marked entity types; consumers decide whether to include or exclude them.
 * 
 */
public final class EntityFilters implements ConfigObject, ConfigNameProvider {

    public static final EntityFilters INSTANCE = new EntityFilters();

    // Persisted key is retained for existing profiles.
    private final ItemIdSetValue markedEntities = new ItemIdSetValue("ignored_entities");

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

    public boolean isMarkedEntity(String id) {
        return markedEntities.get().contains(normalize(id));
    }

    public Set<String> getMarkedEntities() {
        return markedEntities.get();
    }

    public void addMarkedEntity(String id) {
        String norm = normalize(id);
        markedEntities.get().add(norm);
        DebugLog.info("[EntityFilters] add %s -> %s", norm, markedEntities.get());
        save();
    }

    public void removeMarkedEntity(String id) {
        String norm = normalize(id);
        markedEntities.get().remove(norm);
        DebugLog.info("[EntityFilters] remove %s -> %s", norm, markedEntities.get());
        save();
    }

    public ItemIdSetValue getMarkedEntitiesValue() {
        return markedEntities;
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
        list.add(markedEntities);
        return list;
    }
}
