/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.uniform.storage;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.DynamicUniformStorage.DynamicUniform;

/**
 * Fixed-size std140 uniform storage with per-frame rotation.
 */
public final class FixedUniformStorage<T extends DynamicUniform> {

    private final DynamicUniformStorage<T> storage;

    public FixedUniformStorage(String name, int blockSize, int capacity) {
        this.storage = new DynamicUniformStorage<>(name, blockSize, capacity);
    }

    /**
     * Write one uniform block, returns GPU slice
     */
    public GpuBufferSlice write(T value) {
        return storage.writeUniform(value);
    }

}
