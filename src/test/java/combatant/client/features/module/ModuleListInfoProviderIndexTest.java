/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import combatant.client.runtime.discovery.CombatantIndex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModuleListInfoProviderIndexTest {
    @Test
    void compileTimeIndexIncludesBuiltInSuppliers() {
        List<String> entries = CombatantIndex.entries(CombatantIndex.Kind.MODULE_LIST_INFO_PROVIDER);
        for (String module : List.of("Speed", "Flight", "NoSlow", "Timer")) {
            String name = "combatant.client.features.module.modules.movement." + module
                    + "#moduleListDetail";
            assertTrue(entries.contains(name), "Missing annotated ModuleList supplier: " + name);
        }
    }
}
