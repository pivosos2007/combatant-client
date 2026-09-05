/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

public enum StorageAccess {
    READ_ONLY(true, false),
    WRITE_ONLY(false, true),
    READ_WRITE(true, true);

    private final boolean readable;
    private final boolean writable;

    StorageAccess(boolean readable, boolean writable) {
        this.readable = readable;
        this.writable = writable;
    }

    public boolean readable() {
        return readable;
    }

    public boolean writable() {
        return writable;
    }

    /**
     * Returns true when an actual binding access is no broader than the access declared by
     * the shader resource slot.
     */
    public boolean allows(StorageAccess actual) {
        if (actual == null) return false;
        return (!actual.readable || readable) && (!actual.writable || writable);
    }
}
