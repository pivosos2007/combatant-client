/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common;

import java.util.Map;

public interface CommonBooleanGroupSchema extends CommonSettingSchema {
    Map<String, Boolean> defaults();
}