/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class AudioDecoderTest {
    @Test
    void decodesPcm16Wav() throws Exception {
        byte[] samples = {1, 0, -1, 127};
        PcmAudioData decoded = new WavAudioDecoder().decode(new ByteArrayInputStream(wav(samples, 1, 22_050)));

        assertEquals(1, decoded.channels());
        assertEquals(22_050, decoded.sampleRate());
        assertArrayEquals(samples, decoded.data());
    }

    @Test
    void spatialDownmixAveragesStereoFrames() {
        byte[] stereo = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1_000).putShort((short) -1_000)
                .putShort((short) 20_000).putShort((short) 10_000)
                .array();

        PcmAudioData mono = new PcmAudioData(2, 48_000, stereo).toMono();
        assertEquals(1, mono.channels());
        assertArrayEquals(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0).putShort((short) 15_000).array(), mono.data());
    }

    private static byte[] wav(byte[] samples, int channels, int sampleRate) {
        int byteRate = sampleRate * channels * Short.BYTES;
        int blockAlign = channels * Short.BYTES;
        return ByteBuffer.allocate(44 + samples.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46464952)
                .putInt(36 + samples.length)
                .putInt(0x45564157)
                .putInt(0x20746d66)
                .putInt(16)
                .putShort((short) 1)
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(byteRate)
                .putShort((short) blockAlign)
                .putShort((short) 16)
                .putInt(0x61746164)
                .putInt(samples.length)
                .put(samples)
                .array();
    }
}
