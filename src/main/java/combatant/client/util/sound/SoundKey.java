/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.world.phys.Vec3;

/** Implement on an annotated enum to get registration and playback for free. */
public interface SoundKey {
    default SoundDefinition soundDefinition() {
        return SoundRegistry.get().resolve(this);
    }

    default SoundInstance play() {
        return SoundSystem.get().play(soundDefinition(), SoundOptions.DEFAULT);
    }

    default SoundInstance play(SoundOptions options) {
        return SoundSystem.get().play(soundDefinition(), options);
    }

    default SoundInstance playAt(Vec3 position) {
        return play(SoundOptions.at(position));
    }

    default SoundInstance playAt(double x, double y, double z) {
        return play(SoundOptions.at(x, y, z));
    }
}
