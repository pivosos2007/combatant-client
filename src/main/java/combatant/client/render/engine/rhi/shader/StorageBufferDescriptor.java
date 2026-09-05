/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

public record StorageBufferDescriptor(String label,
                                      Std430StructLayout elementLayout,
                                      int elementCapacity,
                                      StorageAccess access,
                                      boolean indirectSource) {
    public StorageBufferDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-buffer" : label;
        if (elementLayout == null) throw new IllegalArgumentException("elementLayout");
        elementCapacity = Math.max(1, elementCapacity);
        access = access != null ? access : StorageAccess.READ_WRITE;
    }

    public long byteSize() {
        return Math.multiplyExact((long) elementLayout.arrayStride(), elementCapacity);
    }
}
