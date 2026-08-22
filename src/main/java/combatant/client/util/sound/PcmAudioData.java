/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import org.lwjgl.openal.AL10;

/** Signed 16-bit little-endian PCM ready for OpenAL upload. */
public record PcmAudioData(int channels, int sampleRate, byte[] data) {
    public PcmAudioData {
        if (channels != 1 && channels != 2) throw new IllegalArgumentException("Only mono/stereo PCM is supported");
        if (sampleRate <= 0) throw new IllegalArgumentException("Invalid sample rate");
        if (data == null || data.length == 0) throw new IllegalArgumentException("PCM data is empty");
    }

    public int openAlFormat() {
        return channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
    }

    public PcmAudioData toMono() {
        if (channels == 1) return this;
        byte[] mono = new byte[data.length / 2];
        int out = 0;
        for (int i = 0; i + 3 < data.length; i += 4) {
            int left = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
            int right = (short) ((data[i + 2] & 0xff) | (data[i + 3] << 8));
            short sample = (short) ((left + right) / 2);
            mono[out++] = (byte) sample;
            mono[out++] = (byte) (sample >>> 8);
        }
        return new PcmAudioData(1, sampleRate, mono);
    }
}
