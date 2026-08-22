/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.event;

import combatant.client.events.Event;
import net.minecraft.resources.Identifier;

public final class SoundLoadedEvent extends Event {
    private final Identifier resource;
    private final int channels;
    private final int sampleRate;
    private final int bytes;
    private final boolean spatialBuffer;

    public SoundLoadedEvent(Identifier resource, int channels, int sampleRate, int bytes, boolean spatialBuffer) {
        this.resource = resource;
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.bytes = bytes;
        this.spatialBuffer = spatialBuffer;
    }

    public Identifier resource() { return resource; }
    public int channels() { return channels; }
    public int sampleRate() { return sampleRate; }
    public int bytes() { return bytes; }
    public boolean spatialBuffer() { return spatialBuffer; }
}
