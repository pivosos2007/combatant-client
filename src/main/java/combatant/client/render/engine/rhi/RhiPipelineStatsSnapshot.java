/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

/** Per-frame attribution for one concrete RHI pipeline identity. */
public record RhiPipelineStatsSnapshot(String pipelineId,
                                       String vertexShaderId,
                                       String fragmentShaderId,
                                       long draws,
                                       long binds,
                                       long switchesInto,
                                       long estimatedAluOps,
                                       long estimatedTranscendentalOps,
                                       long estimatedTextureOps,
                                       long estimatedBranchOps,
                                       long estimatedLoopOps) {
}
