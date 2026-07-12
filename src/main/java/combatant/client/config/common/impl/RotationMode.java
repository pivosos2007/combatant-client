/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.common.CommonSettingSchema;

public final class RotationMode implements CommonSettingSchema {
    @Override
    public String commonI18nKey() {
        return "rotation.rotation_mode";
    }
}