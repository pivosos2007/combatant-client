/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.resources.Identifier;

/** Immutable registered sound metadata. */
public record SoundDefinition(
        Identifier id,
        Identifier resource,
        float gain,
        float pitch,
        boolean looping,
        boolean spatial,
        float rolloff,
        float referenceDistance,
        float maxDistance
) {
    public SoundDefinition {
        if (id == null) throw new IllegalArgumentException("Sound id cannot be null");
        if (resource == null) throw new IllegalArgumentException("Sound resource cannot be null");
        gain = Math.max(0.0f, gain);
        pitch = Math.max(0.01f, pitch);
        rolloff = Math.max(0.0f, rolloff);
        referenceDistance = Math.max(0.01f, referenceDistance);
        maxDistance = Math.max(referenceDistance, maxDistance);
    }

    public static SoundDefinition direct(Identifier resource) {
        return new SoundDefinition(resource, resource, 1.0f, 1.0f, false, false, 1.0f, 1.0f, 64.0f);
    }
}
