/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Frame-local primary-view history contract. Resource histories reuse the same epoch/reset
 * semantics through {@link DeferredTemporalHistoryRegistry}.
 */
public record DeferredHistoryDescriptor(
        long epoch,
        long currentFrameId,
        long previousFrameId,
        int age,
        boolean valid,
        DeferredHistoryResetReason resetReason
) {
    public DeferredHistoryDescriptor {
        age = Math.max(0, age);
        if (resetReason == null) resetReason = DeferredHistoryResetReason.RENDERER_RESET;
        if (valid && resetReason != DeferredHistoryResetReason.NONE) {
            throw new IllegalArgumentException("Valid history cannot carry reset reason " + resetReason);
        }
        if (!valid && age != 0) age = 0;
    }
}
