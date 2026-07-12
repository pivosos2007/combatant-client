/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Minimal WAV (PCM 16-bit) loader.
 */
public enum WavLoader {
    ;

    public static WavData load(InputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        if (bytes.length < 44) throw new IOException("Invalid WAV: header too short");

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        if (buf.getInt(0) != 0x46464952) throw new IOException("Invalid WAV: missing RIFF"); // "RIFF"
        if (buf.getInt(8) != 0x45564157) throw new IOException("Invalid WAV: missing WAVE"); // "WAVE"

        // Walk chunks after RIFF header (first chunk starts at offset 12)
        int offset = 12;
        Integer audioFormat = null;
        Integer channels = null;
        Integer sampleRate = null;
        Integer bitsPerSample = null;
        byte[] dataBytes = null;

        while (offset + 8 <= bytes.length) {
            int chunkId = buf.getInt(offset);
            int chunkSize = buf.getInt(offset + 4);
            int dataStart = offset + 8;
            int dataEnd = Math.min(bytes.length, dataStart + chunkSize);

            // pad to even boundary
            int next = dataStart + chunkSize + (chunkSize % 2);

            if (dataEnd > bytes.length) break;

            if (chunkId == 0x20746D66) { // "fmt "
                if (chunkSize < 16) throw new IOException("Invalid WAV: fmt chunk too small");
                audioFormat = buf.getShort(dataStart) & 0xFFFF;
                channels = buf.getShort(dataStart + 2) & 0xFFFF;
                sampleRate = buf.getInt(dataStart + 4);
                bitsPerSample = buf.getShort(dataStart + 14) & 0xFFFF;
            } else if (chunkId == 0x61746164) { // "data"
                dataBytes = Arrays.copyOfRange(bytes, dataStart, dataEnd);
            }

            offset = next;
        }

        if (audioFormat == null || channels == null || sampleRate == null || bitsPerSample == null) {
            throw new IOException("Invalid WAV: missing fmt chunk");
        }
        if (dataBytes == null || dataBytes.length == 0) {
            throw new IOException("Invalid WAV: no data chunk");
        }

        if (audioFormat != 1) throw new IOException("Unsupported WAV format (only PCM)");
        if (bitsPerSample != 16) throw new IOException("Unsupported WAV bps: " + bitsPerSample);
        if (channels != 1 && channels != 2) throw new IOException("Unsupported WAV channels: " + channels);

        return new WavData(channels, sampleRate, dataBytes);
    }
}
