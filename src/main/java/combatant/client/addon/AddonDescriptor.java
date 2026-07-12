/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import java.util.List;

record AddonDescriptor(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String iconPath,
        int apiVersion,
        String entrypointDefinition
) {
}
