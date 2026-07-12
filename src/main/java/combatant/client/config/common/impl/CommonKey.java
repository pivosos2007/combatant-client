/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.common.impl;

import combatant.client.config.common.CommonSettingSchema;

import java.util.ArrayList;
import java.util.List;

public final class CommonKey implements CommonSettingSchema {
    private final List<String> keys;

    public CommonKey(String key, String... aliases) {
        ArrayList<String> out = new ArrayList<>();
        add(out, key);
        if (aliases != null) {
            for (String alias : aliases) {
                add(out, alias);
            }
        }
        this.keys = List.copyOf(out);
    }

    @Override
    public String commonI18nKey() {
        return keys.isEmpty() ? null : keys.get(0);
    }

    @Override
    public List<String> commonI18nKeys() {
        return keys;
    }

    private static void add(ArrayList<String> out, String key) {
        if (key == null) return;
        String normalized = key.trim();
        if (!normalized.isBlank() && !out.contains(normalized)) {
            out.add(normalized);
        }
    }
}
