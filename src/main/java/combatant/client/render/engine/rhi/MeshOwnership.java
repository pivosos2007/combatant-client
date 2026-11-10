/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

/**
 * Identifies whether a mesh allocation belongs to the current backend frame or to a persistent resource owner.
 * Neither category transfers release responsibility to an individual draw.
 */
public enum MeshOwnership {
    /**
     * Backed by backend-owned ring buffers. Do not close from draw code.
     */
    BACKEND_FRAME,
    /**
     * Backed by persistent resource manager object. Do not close from draw code.
     */
    PERSISTENT
}
