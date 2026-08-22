/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class AudioDecoders {
    private static final AudioDecoder WAV = new WavAudioDecoder();
    private static final AudioDecoder OGG = new OggAudioDecoder();

    private AudioDecoders() {
    }

    static PcmAudioData decode(Identifier resource, InputStream input) throws IOException {
        String path = resource.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".wav")) return WAV.decode(input);
        if (path.endsWith(".ogg")) return OGG.decode(input);
        throw new IOException("Unsupported audio format for " + resource + " (expected .wav or .ogg)");
    }
}
