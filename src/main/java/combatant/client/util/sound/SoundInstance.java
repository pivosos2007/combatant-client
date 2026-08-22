/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.world.phys.Vec3;

/** Safe playback handle. Stale handles cannot control a reused OpenAL source. */
public final class SoundInstance {
    static final SoundInstance REJECTED = new SoundInstance(null, 0L);

    private final SoundSystem owner;
    private final long id;

    SoundInstance(SoundSystem owner, long id) {
        this.owner = owner;
        this.id = id;
    }

    public long id() { return id; }
    public boolean isValid() { return owner != null && id != 0L; }
    public boolean isPlaying() { return isValid() && owner.isPlaying(id); }
    public boolean stop() { return isValid() && owner.stop(id); }
    public boolean pause() { return isValid() && owner.pause(id); }
    public boolean resume() { return isValid() && owner.resume(id); }
    public boolean setGain(double gain) { return isValid() && owner.setGain(id, gain); }
    public boolean setPitch(double pitch) { return isValid() && owner.setPitch(id, pitch); }
    public boolean setPosition(Vec3 position) { return isValid() && owner.setPosition(id, position); }
    public boolean setPosition(double x, double y, double z) { return setPosition(new Vec3(x, y, z)); }
}
