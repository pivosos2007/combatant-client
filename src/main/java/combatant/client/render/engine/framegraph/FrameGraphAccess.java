/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.framegraph;

public enum FrameGraphAccess {
    READ(true, false),
    WRITE(false, true),
    READ_WRITE(true, true);

    private final boolean reads;
    private final boolean writes;

    FrameGraphAccess(boolean reads, boolean writes) {
        this.reads = reads;
        this.writes = writes;
    }

    public boolean reads() {
        return reads;
    }

    public boolean writes() {
        return writes;
    }
}
