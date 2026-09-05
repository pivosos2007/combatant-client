/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.util.List;

/** Backend-neutral dependency lowered to glMemoryBarrier or a Vulkan pipeline barrier. */
public record RhiResourceBarrier(Stage sourceStage,
                                 Access sourceAccess,
                                 Stage destinationStage,
                                 Access destinationAccess,
                                 List<RhiStorageBuffer> buffers) {
    public enum Stage { COMPUTE, GRAPHICS, INDIRECT, TRANSFER }
    public enum Access { READ, WRITE, READ_WRITE }

    public RhiResourceBarrier {
        if (sourceStage == null || sourceAccess == null
                || destinationStage == null || destinationAccess == null) {
            throw new IllegalArgumentException("Barrier stage/access must be explicit");
        }
        buffers = buffers == null || buffers.isEmpty() ? List.of() : List.copyOf(buffers);
    }
}
