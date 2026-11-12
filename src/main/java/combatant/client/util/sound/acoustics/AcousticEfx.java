/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.acoustics;

import combatant.client.util.logging.DebugLog;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import java.util.HashMap;
import java.util.Map;

/**
 * Owns shared EFX objects in Minecraft's OpenAL context. All native methods must run
 * on the SoundEngine executor while its Library is open.
 */
public final class AcousticEfx {
    private final int[] filters = new int[11];
    private final int[] effects = new int[2];
    private final int[] slots = new int[2];
    private final Map<Integer, Applied> sources = new HashMap<>();
    private volatile boolean enabled;
    private volatile boolean unavailable;
    private boolean initialized;
    private boolean supported;
    private boolean warningLogged;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isUnavailable() {
        return unavailable;
    }

    public void apply(int source, AcousticProfile profile) {
        if (!enabled || source == 0 || profile == null) return;
        ensureInitialized();
        if (!supported || !AL10.alIsSource(source)) return;

        int filter = filters[Math.max(0, Math.min(10, profile.lowPass()))];
        int slot = switch (profile.room()) {
            case NONE -> 0;
            case TUNNEL -> slots[0];
            case CAVE -> slots[1];
        };
        Applied previous = sources.get(source);
        if (previous != null && previous.filter == filter && previous.slot == slot) return;
        if (previous == null && filter == 0 && slot == 0) return;

        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                slot, 0, slot == 0 ? EXTEfx.AL_FILTER_NULL : filter);
        if (filter == 0 && slot == 0) sources.remove(source);
        else sources.put(source, new Applied(filter, slot));
    }

    public void detach(int source) {
        if (sources.remove(source) == null) return;
        if (supported && AL10.alIsSource(source)) resetSource(source);
    }

    /** Unbinds live sources before deleting filters, slots and effects. */
    public void dispose() {
        for (int source : sources.keySet()) {
            if (supported && AL10.alIsSource(source)) resetSource(source);
        }
        sources.clear();
        for (int slot : slots) {
            if (slot != 0) {
                EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
                EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            }
        }
        for (int effect : effects) if (effect != 0) EXTEfx.alDeleteEffects(effect);
        for (int filter : filters) if (filter != 0) EXTEfx.alDeleteFilters(filter);
        java.util.Arrays.fill(filters, 0);
        java.util.Arrays.fill(effects, 0);
        java.util.Arrays.fill(slots, 0);
        initialized = false;
        supported = false;
        unavailable = false;
    }

    /** Drops IDs after context replacement; the previous context already owns their destruction. */
    public void forgetContext() {
        sources.clear();
        java.util.Arrays.fill(filters, 0);
        java.util.Arrays.fill(effects, 0);
        java.util.Arrays.fill(slots, 0);
        initialized = false;
        supported = false;
        unavailable = false;
        warningLogged = false;
    }

    private void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        if (device == 0L || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            if (!warningLogged) {
                warningLogged = true;
                DebugLog.warn("PhysicSound is unavailable: OpenAL device does not support ALC_EXT_EFX");
            }
            unavailable = true;
            return;
        }

        try {
            AL10.alGetError();
            for (int i = 1; i <= 8; i++) {
                filters[i] = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(filters[i], EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                float occlusion = i / 8.0f;
                EXTEfx.alFilterf(filters[i], EXTEfx.AL_LOWPASS_GAIN, 1.0f - 0.32f * occlusion);
                EXTEfx.alFilterf(filters[i], EXTEfx.AL_LOWPASS_GAINHF, 1.0f - 0.84f * occlusion);
            }
            filters[9] = createLowPass(0.82f, 0.3f);
            filters[10] = createLowPass(0.75f, 0.06f);
            effects[0] = createReverb(1.25f, 0.36f, 0.88f, 0.75f);
            effects[1] = createReverb(2.6f, 0.46f, 0.82f, 0.65f);
            for (int i = 0; i < slots.length; i++) {
                slots[i] = EXTEfx.alGenAuxiliaryEffectSlots();
                EXTEfx.alAuxiliaryEffectSloti(slots[i], EXTEfx.AL_EFFECTSLOT_EFFECT, effects[i]);
            }
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                throw new IllegalStateException("OpenAL EFX initialization failed (0x" + Integer.toHexString(error) + ")");
            }
            supported = true;
        } catch (RuntimeException error) {
            if (!warningLogged) {
                warningLogged = true;
                DebugLog.warn("PhysicSound disabled: %s", error.getMessage());
            }
            dispose();
            initialized = true;
            unavailable = true;
        }
    }

    private int createLowPass(float gain, float highFrequencyGain) {
        int filter = EXTEfx.alGenFilters();
        EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
        EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, highFrequencyGain);
        return filter;
    }

    private int createReverb(float decayTime, float gain, float gainHighFrequency, float highFrequencyRatio) {
        int effect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, 0.96f);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, 0.91f);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, gain);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, gainHighFrequency);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, decayTime);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_HFRATIO, highFrequencyRatio);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, 0.52f);
        EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 0.82f);
        return effect;
    }

    private static void resetSource(int source) {
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER,
                EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
    }

    private record Applied(int filter, int slot) {
    }
}
