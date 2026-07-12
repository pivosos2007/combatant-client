/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import org.lwjgl.openal.AL10;

/**
 * Parsed WAV payload suitable for uploading into an OpenAL buffer.
 */
public record WavData(int channels, int sampleRate, byte[] data) {

    public int getFormat() {
        return channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
    }

    public int byteSize() {
        return data == null ? 0 : data.length;
    }
}
