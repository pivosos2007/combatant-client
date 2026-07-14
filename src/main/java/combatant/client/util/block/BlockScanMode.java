/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block;

import combatant.client.config.values.EnumValue;

import java.util.List;

public enum BlockScanMode implements EnumValue.IdProvider, EnumValue.AliasProvider {
    LOS("los", "los_unlocked"),
    UNRESTRICTED("unrestricted", "aggressive");

    private final String id;
    private final List<String> aliases;

    BlockScanMode(String id, String... aliases) {
        this.id = id;
        this.aliases = List.of(aliases);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }
}
