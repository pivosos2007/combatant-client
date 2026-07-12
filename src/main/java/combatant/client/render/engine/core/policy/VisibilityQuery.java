/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core.policy;

import net.minecraft.world.phys.AABB;

/**
 * Per-call visibility policy. Lets important/special overlays opt out of Sodium section culling.
 */
public record VisibilityQuery(AABB box,
                              boolean useSectionVisibility,
                              boolean alwaysVisible,
                              String reason) {
    public static VisibilityQuery worldOverlay(AABB box) {
        return new VisibilityQuery(box, true, false, "world_overlay");
    }

    public static VisibilityQuery alwaysVisible(AABB box, String reason) {
        return new VisibilityQuery(box, false, true, reason != null ? reason : "always_visible");
    }

    public static VisibilityQuery noSectionVisibility(AABB box, String reason) {
        return new VisibilityQuery(box, false, false, reason != null ? reason : "section_visibility_disabled");
    }
}
