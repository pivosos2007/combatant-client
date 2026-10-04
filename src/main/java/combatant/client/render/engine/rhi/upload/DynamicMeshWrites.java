/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.upload;

import java.util.ArrayList;

/**
 * Tracks dynamic-mesh arenas that hold open write mappings and makes their data visible to the GPU.
 *
 * <p>Blaze3D's {@code MappedView.close()} flushes the <em>whole</em> buffer (the arenas are 32 MiB / 8 MiB)
 * and unmaps it. Closing a view after every mesh therefore cost two full-buffer flushes plus map/unmap
 * calls per uploaded mesh. Arenas now keep one view open per buffer while meshes are being recorded and
 * this class closes them once, right before the first draw that could read the data.</p>
 */
public final class DynamicMeshWrites {
    private static final ArrayList<Blaze3dMeshArena> PENDING = new ArrayList<>(4);
    private static volatile boolean pending;

    private DynamicMeshWrites() {
    }

    static void track(Blaze3dMeshArena arena) {
        synchronized (PENDING) {
            if (arena.trackedForFlush) return;
            arena.trackedForFlush = true;
            PENDING.add(arena);
            pending = true;
        }
    }

    /** Flushes and unmaps every arena buffer that received writes since the last call. Cheap when idle. */
    public static void flushPending() {
        if (!pending) return;
        synchronized (PENDING) {
            try {
                for (int i = 0, size = PENDING.size(); i < size; i++) {
                    Blaze3dMeshArena arena = PENDING.get(i);
                    arena.trackedForFlush = false;
                    arena.closeMappings();
                }
            } finally {
                PENDING.clear();
                pending = false;
            }
        }
    }
}
