/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.openal.AL10;
import combatant.client.util.logging.DebugLog;

import java.io.InputStream;

/**
 * Minimal WAV sound engine that uses Minecraft's OpenAL context.
 * Must be called from a thread where the context is current (render thread).
 */
public final class CustomSoundEngine {

    private static final CustomSoundEngine INSTANCE = new CustomSoundEngine();

    private final WavBufferCache bufferCache = new WavBufferCache();
    private final SourcePool sourcePool = new SourcePool(24);

    private float masterGain = 1.0f;

    private CustomSoundEngine() {
    }

    public static CustomSoundEngine get() {
        return INSTANCE;
    }

    public void setMasterGain(double gain) {
        masterGain = (float) Math.max(0f, gain);
    }

    public void play(Identifier id, double gain, double pitch, boolean loop, boolean relative) {
        try {
            int buffer = ensureBuffer(id);
            if (buffer == 0) return;

            int src = sourcePool.acquire();
            if (src == 0) {
                DebugLog.warn("No free OpenAL source for %s", id);
                return;
            }

            AL10.alSourcei(src, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(src, AL10.AL_GAIN, masterGain * (float) gain);
            AL10.alSourcef(src, AL10.AL_PITCH, (float) pitch);
            AL10.alSourcei(src, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
            AL10.alSourcei(src, AL10.AL_SOURCE_RELATIVE, relative ? AL10.AL_TRUE : AL10.AL_FALSE);
            AL10.alSource3f(src, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcePlay(src);
        } catch (Exception e) {
            DebugLog.error("Failed to play wav %s", e, id);
        }
    }


    private int ensureBuffer(Identifier id) throws Exception {
        int existing = bufferCache.get(id.toString());
        if (existing != 0) return existing;

        InputStream is = getResourceStream(id);
        if (is == null) {
            DebugLog.warn("WAV not found: %s", id);
            return 0;
        }
        try (is) {
            return bufferCache.load(id.toString(), is);
        }
    }


    private InputStream getResourceStream(Identifier id) {
        return Minecraft.getInstance()
                .getResourceManager()
                .getResource(id)
                .map(r -> {
                    try {
                        return r.open();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Should be called when audio resets.
     */
    public void reset() {
        sourcePool.stopAll();
        sourcePool.clear();
        bufferCache.clear();
    }
}
