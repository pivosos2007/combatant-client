/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

final class OggAudioDecoder implements AudioDecoder {
    @Override
    public PcmAudioData decode(InputStream input) throws IOException {
        byte[] compressed = input.readAllBytes();
        if (compressed.length == 0) throw new IOException("Empty OGG stream");

        ByteBuffer encoded = BufferUtils.createByteBuffer(compressed.length);
        encoded.put(compressed).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsOut = stack.mallocInt(1);
            IntBuffer sampleRateOut = stack.mallocInt(1);
            ShortBuffer decoded = STBVorbis.stb_vorbis_decode_memory(encoded, channelsOut, sampleRateOut);
            if (decoded == null) throw new IOException("STB Vorbis could not decode the OGG stream");
            try {
                int channels = channelsOut.get(0);
                int sampleRate = sampleRateOut.get(0);
                if (channels != 1 && channels != 2) {
                    throw new IOException("Unsupported OGG channel count: " + channels);
                }
                byte[] pcm = new byte[decoded.remaining() * Short.BYTES];
                int offset = 0;
                while (decoded.hasRemaining()) {
                    short sample = decoded.get();
                    pcm[offset++] = (byte) sample;
                    pcm[offset++] = (byte) (sample >>> 8);
                }
                return new PcmAudioData(channels, sampleRate, pcm);
            } finally {
                MemoryUtil.memFree(decoded);
            }
        }
    }
}
