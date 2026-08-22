/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import net.minecraft.world.phys.Vec3;

/** Mutable request exposed to the cancellable pre-play event. */
public final class SoundRequest {
    private final SoundDefinition definition;
    private float gain;
    private float pitch;
    private boolean looping;
    private boolean spatial;
    private Vec3 position;
    private float rolloff;
    private float referenceDistance;
    private float maxDistance;
    private Vec3 velocity;
    private Vec3 direction;
    private float coneInnerAngle;
    private float coneOuterAngle;
    private float coneOuterGain;
    private float airAbsorption;
    private float roomRolloff;
    private SoundEqualizer equalizer;

    private SoundRequest(SoundDefinition definition, SoundOptions options) {
        this.definition = definition;
        this.gain = definition.gain() * options.gain();
        this.pitch = definition.pitch() * options.pitch();
        this.looping = options.looping() != null ? options.looping() : definition.looping();
        this.spatial = options.spatial() != null ? options.spatial() : definition.spatial();
        SoundSpatialization details = options.spatialization();
        this.position = details == null ? Vec3.ZERO : details.position();
        this.velocity = details == null ? Vec3.ZERO : details.velocity();
        this.direction = details == null ? Vec3.ZERO : details.direction();
        this.rolloff = details != null && details.rolloff() != null ? details.rolloff() : definition.rolloff();
        this.referenceDistance = details != null && details.referenceDistance() != null
                ? details.referenceDistance() : definition.referenceDistance();
        this.maxDistance = details != null && details.maxDistance() != null ? details.maxDistance() : definition.maxDistance();
        this.coneInnerAngle = details == null ? 360.0f : details.coneInnerAngle();
        this.coneOuterAngle = details == null ? 360.0f : details.coneOuterAngle();
        this.coneOuterGain = details == null ? 0.0f : details.coneOuterGain();
        this.airAbsorption = details == null ? 0.0f : details.airAbsorption();
        this.roomRolloff = details == null ? 0.0f : details.roomRolloff();
        this.equalizer = options.equalizer();
        normalize();
    }

    public static SoundRequest create(SoundDefinition definition, SoundOptions options) {
        return new SoundRequest(definition, options == null ? SoundOptions.DEFAULT : options);
    }

    private void normalize() {
        gain = Math.max(0.0f, gain);
        pitch = Math.max(0.01f, pitch);
        rolloff = Math.max(0.0f, rolloff);
        referenceDistance = Math.max(0.01f, referenceDistance);
        maxDistance = Math.max(referenceDistance, maxDistance);
        if (position == null) position = Vec3.ZERO;
    }

    public SoundDefinition definition() { return definition; }
    public float gain() { return gain; }
    public float pitch() { return pitch; }
    public boolean looping() { return looping; }
    public boolean spatial() { return spatial; }
    public Vec3 position() { return position; }
    public float rolloff() { return rolloff; }
    public float referenceDistance() { return referenceDistance; }
    public float maxDistance() { return maxDistance; }
    public Vec3 velocity() { return velocity; }
    public Vec3 direction() { return direction; }
    public float coneInnerAngle() { return coneInnerAngle; }
    public float coneOuterAngle() { return coneOuterAngle; }
    public float coneOuterGain() { return coneOuterGain; }
    public float airAbsorption() { return airAbsorption; }
    public float roomRolloff() { return roomRolloff; }
    public SoundEqualizer equalizer() { return equalizer; }

    public void setGain(double gain) { this.gain = Math.max(0.0f, (float) gain); }
    public void setPitch(double pitch) { this.pitch = Math.max(0.01f, (float) pitch); }
    public void setLooping(boolean looping) { this.looping = looping; }
    public void setSpatial(boolean spatial) { this.spatial = spatial; }
    public void setPosition(Vec3 position) { this.position = position == null ? Vec3.ZERO : position; }
    public void setRolloff(double rolloff) { this.rolloff = Math.max(0.0f, (float) rolloff); }
    public void setReferenceDistance(double distance) {
        referenceDistance = Math.max(0.01f, (float) distance);
        maxDistance = Math.max(referenceDistance, maxDistance);
    }
    public void setMaxDistance(double distance) { maxDistance = Math.max(referenceDistance, (float) distance); }
    public void setVelocity(Vec3 velocity) { this.velocity = velocity == null ? Vec3.ZERO : velocity; }
    public void setDirection(Vec3 direction) { this.direction = direction == null ? Vec3.ZERO : direction; }
    public void setCone(float innerAngle, float outerAngle, float outerGain) {
        coneInnerAngle = Math.max(0.0f, Math.min(360.0f, innerAngle));
        coneOuterAngle = Math.max(coneInnerAngle, Math.min(360.0f, outerAngle));
        coneOuterGain = Math.max(0.0f, Math.min(1.0f, outerGain));
    }
    public void setAirAbsorption(double value) { airAbsorption = Math.max(0.0f, Math.min(10.0f, (float) value)); }
    public void setRoomRolloff(double value) { roomRolloff = Math.max(0.0f, Math.min(10.0f, (float) value)); }
    public void setEqualizer(SoundEqualizer equalizer) { this.equalizer = equalizer; }
}
