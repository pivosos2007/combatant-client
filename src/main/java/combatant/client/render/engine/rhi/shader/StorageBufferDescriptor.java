/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

/**
 * Storage-buffer layout and allocation policy.
 * Capacity is measured in layout elements; static uploads cannot be combined with GPU-owned output.
 */
public record StorageBufferDescriptor(String label,
                                      Std430StructLayout elementLayout,
                                      int elementCapacity,
                                      StorageAccess access,
                                      boolean indirectSource, boolean gpuOwned, boolean staticUpload) {
    public StorageBufferDescriptor {
        label = label == null || label.isBlank() ? "combatant-storage-buffer" : label;
        if (elementLayout == null) throw new IllegalArgumentException("elementLayout");
        elementCapacity = Math.max(1, elementCapacity);
        access = access != null ? access : StorageAccess.READ_WRITE;
        if (staticUpload && gpuOwned) throw new IllegalArgumentException("staticUpload and gpuOwned are mutually exclusive");
    }

    /** Static CPU uploads use one device-local Vulkan buffer and one staged copy, not a 64x submit ring. */
    public StorageBufferDescriptor(String label, Std430StructLayout layout,
                                   int capacity, StorageAccess access, boolean indirectSource, boolean gpuOwned) {
        this(label, layout, capacity, access, indirectSource, gpuOwned, false);
    }

    /** Legacy descriptors retain upload-snapshot semantics. GPU-owned output buffers use submit-local slots. */
    public StorageBufferDescriptor(String label, Std430StructLayout elementLayout,
                                   int elementCapacity, StorageAccess access, boolean indirectSource) {
        this(label, elementLayout, elementCapacity, access, indirectSource, false);
    }

    public long byteSize() {
        return Math.multiplyExact((long) elementLayout.arrayStride(), elementCapacity);
    }
}
