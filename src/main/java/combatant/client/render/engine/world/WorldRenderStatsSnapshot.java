/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

public record WorldRenderStatsSnapshot(long frameId,
                                       long recordedCommands,
                                       long submittedCommands,
                                       long skippedEmptyCommands,
                                       long submittedVertices,
                                       long submittedIndices,
                                       long fogBindings,
                                       long depthPrePassBindings,
                                       long depthMainBindings,
                                       long depthDisabledBindings) {
}
