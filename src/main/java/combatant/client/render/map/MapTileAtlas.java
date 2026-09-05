/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.map;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** Fixed-slot atlas pages. Tiles share page bindings and never allocate one texture per tile. */
public final class MapTileAtlas implements AutoCloseable {
    private static final int GUTTER = 1;

    private final int tileSize;
    private final int columns;
    private final int rows;
    private final int stride;
    private final Page[] pages;
    private final long[][] uploadedGenerations;

    public MapTileAtlas(int tileSize, int pageCount, int columns, int rows) {
        if (tileSize <= 0 || pageCount <= 0 || columns <= 0 || rows <= 0) {
            throw new IllegalArgumentException("Map atlas dimensions must be positive.");
        }
        this.tileSize = tileSize;
        this.columns = columns;
        this.rows = rows;
        this.stride = tileSize + GUTTER * 2;
        this.pages = new Page[pageCount];
        this.uploadedGenerations = new long[pageCount][Math.multiplyExact(columns, rows)];
    }

    public int tileSize() {
        return tileSize;
    }

    public int pageCount() {
        return pages.length;
    }

    public int pageCapacity() {
        return Math.multiplyExact(columns, rows);
    }

    public synchronized void upload(MapTileSlot slot, MapTilePixels pixels) {
        validateSlot(slot);
        if (pixels.width() != tileSize || pixels.height() != tileSize) {
            throw new IllegalArgumentException("Tile payload does not match atlas tile size.");
        }
        Page page = page(slot.page());
        byte[] padded = padWithDuplicatedEdges(pixels.copyBytes());
        ByteBuffer buffer = MemoryUtil.memAlloc(padded.length);
        try {
            buffer.put(padded).flip();
            int column = slot.slot() % columns;
            int row = slot.slot() / columns;
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                    page.texture,
                    buffer,
                    0,
                    0,
                    column * stride,
                    row * stride,
                    stride,
                    stride
            );
            uploadedGenerations[slot.page()][slot.slot()] = slot.generation();
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public synchronized boolean isUploaded(MapTileSlot slot) {
        validateSlot(slot);
        return uploadedGenerations[slot.page()][slot.slot()] == slot.generation();
    }

    public synchronized PageView pageView(int pageIndex) {
        Page page = page(pageIndex);
        return new PageView(page.view, page.sampler);
    }

    public UvRect uv(MapTileSlot slot) {
        validateSlot(slot);
        int width = columns * stride;
        int height = rows * stride;
        int column = slot.slot() % columns;
        int row = slot.slot() / columns;
        double x = column * stride + GUTTER;
        double y = row * stride + GUTTER;
        return new UvRect(x / width, y / height, (x + tileSize) / width, (y + tileSize) / height);
    }

    @Override
    public synchronized void close() {
        for (int i = 0; i < pages.length; i++) {
            Page page = pages[i];
            if (page == null) continue;
            page.view.close();
            page.texture.close();
            pages[i] = null;
        }
    }

    private Page page(int index) {
        if (index < 0 || index >= pages.length) throw new IndexOutOfBoundsException(index);
        Page existing = pages[index];
        if (existing != null) return existing;
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
        GpuTexture texture = RenderSystem.getDevice().createTexture(
                "Combatant map tile atlas " + index,
                usage,
                GpuFormat.RGBA8_UNORM,
                columns * stride,
                rows * stride,
                1,
                1
        );
        GpuTextureView view = RenderSystem.getDevice().createTextureView(texture);
        GpuSampler sampler = RenderSystem.getSamplerCache().getSampler(
                AddressMode.CLAMP_TO_EDGE,
                AddressMode.CLAMP_TO_EDGE,
                FilterMode.LINEAR,
                FilterMode.LINEAR,
                false
        );
        Page created = new Page(texture, view, sampler);
        pages[index] = created;
        return created;
    }

    private void validateSlot(MapTileSlot slot) {
        if (slot == null) throw new NullPointerException("slot");
        if (slot.page() >= pages.length || slot.slot() >= pageCapacity()) {
            throw new IllegalArgumentException("Slot does not belong to this atlas: " + slot);
        }
    }

    private byte[] padWithDuplicatedEdges(byte[] source) {
        int paddedSize = stride * stride * 4;
        byte[] output = new byte[paddedSize];
        for (int paddedY = 0; paddedY < stride; paddedY++) {
            int sourceY = clamp(paddedY - GUTTER, 0, tileSize - 1);
            for (int paddedX = 0; paddedX < stride; paddedX++) {
                int sourceX = clamp(paddedX - GUTTER, 0, tileSize - 1);
                int sourceOffset = (sourceY * tileSize + sourceX) * 4;
                int targetOffset = (paddedY * stride + paddedX) * 4;
                System.arraycopy(source, sourceOffset, output, targetOffset, 4);
            }
        }
        return output;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Page(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
    }

    public record PageView(GpuTextureView textureView, GpuSampler sampler) {
    }

    public record UvRect(double minU, double minV, double maxU, double maxV) {
    }
}
