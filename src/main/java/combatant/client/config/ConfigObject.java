/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import combatant.client.config.values.ConfigValue;

import java.util.List;

public interface ConfigObject {
    List<ConfigValue<?>> getConfigValues();
}
