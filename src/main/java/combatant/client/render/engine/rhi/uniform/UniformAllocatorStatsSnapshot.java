/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.uniform;

public record UniformAllocatorStatsSnapshot(long frameId,
                                            long writes,
                                            long uploadedBytes,
                                            long streamCount,
                                            long activeStreams,
                                            long ringCapacityBytes,
                                            long ringCursorBytes,
                                            long ringRotations,
                                            long ringGrows,
                                            long blockingBufferRequests,
                                            long staleReadMisses) {
}
