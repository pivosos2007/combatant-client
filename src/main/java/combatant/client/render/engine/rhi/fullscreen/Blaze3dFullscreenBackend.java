/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.fullscreen;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.BufferUtils;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.MeshOwnership;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Persistent fullscreen quad mesh owned by Blaze3D GPU buffers.
 */
public final class Blaze3dFullscreenBackend implements FullscreenBackend {
    private GpuBuffer vertexBuffer;
    private GpuBuffer indexBuffer;
    private GpuMeshHandle quad;

    @Override
    public void ensureInitialized() {
        if (quad != null) return;

        ByteBuffer vertices = BufferUtils.createByteBuffer(4 * 2 * Float.BYTES).order(ByteOrder.nativeOrder());
        vertices.putFloat(-1f).putFloat(-1f);
        vertices.putFloat(-1f).putFloat(1f);
        vertices.putFloat(1f).putFloat(1f);
        vertices.putFloat(1f).putFloat(-1f);
        vertices.flip();

        ByteBuffer indices = BufferUtils.createByteBuffer(6 * Integer.BYTES).order(ByteOrder.nativeOrder());
        indices.putInt(0).putInt(1).putInt(2).putInt(2).putInt(3).putInt(0);
        indices.flip();

        vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Combatant Fullscreen Quad VBO", GpuBuffer.USAGE_VERTEX, vertices);
        indexBuffer = RenderSystem.getDevice().createBuffer(() -> "Combatant Fullscreen Quad IBO", GpuBuffer.USAGE_INDEX, indices);
        quad = new GpuMeshHandle(vertexBuffer, indexBuffer, 0L, 0, 6, com.mojang.blaze3d.IndexType.INT, MeshOwnership.PERSISTENT);
    }

    @Override
    public GpuMeshHandle quad() {
        ensureInitialized();
        return quad;
    }

    @Override
    public GpuBuffer vertexBuffer() {
        ensureInitialized();
        return vertexBuffer;
    }

    @Override
    public GpuBuffer indexBuffer() {
        ensureInitialized();
        return indexBuffer;
    }

    @Override
    public void close() {
        if (vertexBuffer != null && !vertexBuffer.isClosed()) vertexBuffer.close();
        if (indexBuffer != null && !indexBuffer.isClosed()) indexBuffer.close();
        vertexBuffer = null;
        indexBuffer = null;
        quad = null;
    }
}
