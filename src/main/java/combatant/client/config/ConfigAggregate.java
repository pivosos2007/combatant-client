/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import combatant.client.config.values.ConfigValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility/composition config that owns no persistence file of its own.
 * Serializer operations are fanned out to its child config objects.
 */
public interface ConfigAggregate extends ConfigObject {
    List<? extends ConfigObject> configChildren();

    @Override
    default List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> out = new ArrayList<>();
        List<? extends ConfigObject> children = configChildren();
        if (children == null) return out;
        for (ConfigObject child : children) {
            if (child != null) out.addAll(child.getConfigValues());
        }
        return List.copyOf(out);
    }
}
