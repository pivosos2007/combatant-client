/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import combatant.client.render.engine.guard.LegacyRenderPath;

import java.util.Map;

public record RhiStatsSnapshot(long frameId,
                               long drawCalls,
                               long renderPasses,
                               long renderPassAttachmentSwitches,
                               long fullscreenPasses,
                               long textureFastCopies,
                               long textureShaderCopies,
                               long meshUploads,
                               long uploadedVertexBytes,
                               long uploadedIndexBytes,
                               long ringWraps,
                               long ringStalls,
                               long immediateFallbackUploads,
                               long temporaryOwnedMeshes,
                               long dynamicArenaAllocations,
                               long dynamicPersistentArenaAllocations,
                               long dynamicSpillArenaAllocations,
                               long dynamicArenaReuses,
                               long dynamicArenaRetires,
                               long dynamicFenceChecks,
                               long dynamicFenceCompletions,
                               long dynamicArenaBacklogEvents,
                               long dynamicPersistentArenaBytes,
                               long dynamicSpillArenaBytes,
                               long legacyPathUses,
                               Map<LegacyRenderPath, Long> legacyPathBreakdown) {
}
