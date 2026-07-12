/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import java.util.List;

public record AddonSnapshot(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String iconPath,
        int apiVersion,
        AddonStatus status,
        boolean enabled,
        boolean restartRequired,
        int modules,
        int draggableHudElements,
        int staticHudElements,
        int commands,
        int clickGuiSections,
        int moduleExtensions,
        int irisPatchManifests,
        List<AddonIssue> issues
) {
}
