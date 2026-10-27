/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded KTX1 reader for the shipped 2D RGBA8/RGBA16 UNORM material mip chains. */
public final class UiMaterialTextureData {
    private static final byte[] MAGIC = {(byte)0xab, 0x4b, 0x54, 0x58, 0x20, 0x31, 0x31, (byte)0xbb, 13, 10, 26, 10};
    private final List<byte[]> levels;
    private final int componentBytes;
    private final int size;

    private UiMaterialTextureData(List<byte[]> levels, int componentBytes, int size) {
        this.levels = List.copyOf(levels);
        this.componentBytes = componentBytes;
        this.size = size;
    }
    public int size() { return size; }
    public int componentBytes() { return componentBytes; }
    public int mipLevels() { return levels.size(); }
    public ByteBuffer mip(int level) { return ByteBuffer.wrap(levels.get(level)).asReadOnlyBuffer().order(ByteOrder.nativeOrder()); }

    public static UiMaterialTextureData read(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(44 * 1024 * 1024 + 1);
        if (bytes.length < 64 || bytes.length > 44 * 1024 * 1024
                || !Arrays.equals(MAGIC, Arrays.copyOf(bytes, 12))) throw new IOException("Invalid UI PBR KTX header");
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        data.position(12);
        int endian = data.getInt();
        if (endian == 0x01020304) data.order(ByteOrder.BIG_ENDIAN);
        else if (endian != 0x04030201) throw new IOException("Invalid KTX byte order");
        int type = data.getInt(), componentBytes = data.getInt(), format = data.getInt(), internal = data.getInt();
        if (format != 0x1908 || !(type == 0x1401 && componentBytes == 1 && internal == 0x8058
                || type == 0x1403 && componentBytes == 2 && internal == 0x805b))
            throw new IOException("Unsupported UI PBR KTX component format");
        int baseFormat = data.getInt(), width = data.getInt(), height = data.getInt();
        int depth = data.getInt(), arrays = data.getInt(), faces = data.getInt(), mipLevels = data.getInt();
        if (baseFormat != 0x1908 || width < 1 || width > 2048 || (width & (width - 1)) != 0
                || height != width || depth != 0 || arrays != 0 || faces != 1
                || mipLevels != Integer.numberOfTrailingZeros(width) + 1)
            throw new IOException("Unsupported UI PBR KTX layout");
        int metadata = data.getInt();
        if (metadata < 0 || metadata > data.remaining() || (metadata & 3) != 0)
            throw new IOException("Invalid KTX metadata size");
        data.position(data.position() + metadata);
        List<byte[]> levels = new ArrayList<>(mipLevels);
        for (int mip = 0; mip < mipLevels; mip++) {
            int size = width >> mip, length = size * size * 4 * componentBytes;
            if (data.remaining() < 4 || data.getInt() != length || data.remaining() < length)
                throw new IOException("Invalid or truncated UI PBR mip " + mip);
            byte[] pixels = new byte[length]; data.get(pixels);
            if (componentBytes == 2 && data.order() != ByteOrder.nativeOrder()) {
                for (int i = 0; i < pixels.length; i += 2) {
                    byte low = pixels[i]; pixels[i] = pixels[i + 1]; pixels[i + 1] = low;
                }
            }
            levels.add(pixels);
        }
        if (data.hasRemaining()) throw new IOException("Unexpected trailing KTX data");
        return new UiMaterialTextureData(levels, componentBytes, width);
    }
}
