/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/** Typed reason why temporal consumers must reject previous-frame history. */
public enum DeferredHistoryResetReason {
    NONE(0),
    FIRST_FRAME(10),
    FRAME_GAP(20),
    SUBSYSTEM_REENABLED(25),
    RESIZE(40),
    RENDER_SCALE_CHANGE(45),
    CAMERA_CUT(50),
    TELEPORT(60),
    PROJECTION_DISCONTINUITY(65),
    DIMENSION_CHANGE(70),
    RESOURCE_RELOAD(75),
    MATERIAL_CONTRACT_VERSION_CHANGE(80),
    PERSISTENT_RESOURCE_RECREATION(85),
    POLICY_CHANGE(30),
    BACKEND_RECREATION(100),
    RENDERER_RESET(110),

    /** Compatibility aliases retained for callers compiled against the first temporal slice. */
    @Deprecated WORLD_CHANGE(70),
    @Deprecated BACKEND_CHANGE(100);

    private final int priority;

    DeferredHistoryResetReason(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    /** Keeps the strongest exact lifecycle event when several invalidations arrive in one frame. */
    public static DeferredHistoryResetReason merge(DeferredHistoryResetReason current,
                                                   DeferredHistoryResetReason incoming) {
        if (current == null || current == NONE) return incoming == null ? NONE : incoming;
        if (incoming == null || incoming == NONE) return current;
        return incoming.priority > current.priority ? incoming : current;
    }
}
