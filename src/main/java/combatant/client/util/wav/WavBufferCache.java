/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches OpenAL buffers for WAV assets.
 */
public final class WavBufferCache {

    private final Map<String, Integer> buffers = new HashMap<>();

    public int get(String id) {
        return buffers.getOrDefault(id, 0);
    }

    public int load(String id, InputStream is) throws Exception {
        int existing = buffers.getOrDefault(id, 0);
        if (existing != 0) return existing;

        WavData wav = WavLoader.load(is);
        ByteBuffer data = BufferUtils.createByteBuffer(wav.byteSize());
        data.put(wav.data()).flip(); // OpenAL expects a direct buffer
        int buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, wav.getFormat(), data, wav.sampleRate());
        WavDebugStats.onBufferCreated(wav.byteSize());
        buffers.put(id, buffer);
        return buffer;
    }


    public void clear() {
        for (int buf : buffers.values()) {
            AL10.alDeleteBuffers(buf);
        }
        WavDebugStats.onBuffersCleared(buffers.size());
        buffers.clear();
    }

}
