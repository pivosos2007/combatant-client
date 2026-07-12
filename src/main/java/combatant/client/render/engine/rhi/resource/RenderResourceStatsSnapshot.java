/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

public record RenderResourceStatsSnapshot(
        long frameId,
        int persistentFramebuffers,
        int temporaryFramebuffers,
        long framebufferBorrows,
        long framebufferReleases,
        long framebufferCreates,
        long framebufferResizes,
        long framebufferInvalidations,
        long textureRetirements,
        long textureCloses,
        long retirementQueued,
        long retirementClosed,
        int retirementBacklog,
        int leakedResources
) {
}
