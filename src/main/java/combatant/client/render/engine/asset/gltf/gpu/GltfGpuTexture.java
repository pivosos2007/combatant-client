/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.gpu;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.util.Objects;

/** One backend-neutral resident view of an imported glTF image. */
public final class GltfGpuTexture implements AutoCloseable {
    private final GpuTexture texture;
    private final GpuTextureView view;
    private final GpuSampler sampler;
    private final GltfGpuTextureUsage usage;
    private final int width;
    private final int height;
    private final int mipLevels;
    private final long gpuBytesEstimate;
    private boolean closed;

    GltfGpuTexture(GpuTexture texture,
                   GpuTextureView view,
                   GpuSampler sampler,
                   GltfGpuTextureUsage usage,
                   int width,
                   int height,
                   int mipLevels) {
        this.texture = Objects.requireNonNull(texture, "texture");
        this.view = Objects.requireNonNull(view, "view");
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.width = width;
        this.height = height;
        this.mipLevels = mipLevels;
        long pixels = 0L;
        int w = width;
        int h = height;
        for (int level = 0; level < mipLevels; level++) {
            pixels += (long) Math.max(1, w) * Math.max(1, h);
            w = Math.max(1, w >> 1);
            h = Math.max(1, h >> 1);
        }
        this.gpuBytesEstimate = pixels * 4L;
    }

    public GpuTexture texture() { ensureOpen(); return texture; }
    public GpuTextureView view() { ensureOpen(); return view; }
    public GpuSampler sampler() { ensureOpen(); return sampler; }
    public GltfGpuTextureUsage usage() { return usage; }
    public int width() { return width; }
    public int height() { return height; }
    public int mipLevels() { return mipLevels; }
    public long gpuBytesEstimate() { return gpuBytesEstimate; }
    public boolean isClosed() { return closed; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try { view.close(); } catch (Throwable ignored) { }
        try { texture.close(); } catch (Throwable ignored) { }
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("glTF GPU texture is closed");
    }
}
