/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import combatant.client.events.Events;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.sound.event.SoundErrorEvent;
import combatant.client.util.sound.event.SoundLoadedEvent;
import combatant.client.util.sound.event.SoundPlayEvent;
import combatant.client.util.sound.event.SoundStartedEvent;
import combatant.client.util.sound.event.SoundStoppedEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Format-independent OpenAL sound system sharing Minecraft's current audio context.
 * Supports cached WAV/OGG assets, positional playback and controllable instances.
 */
public final class SoundSystem {
    private static final int MAX_SOURCES = 32;
    private static final SoundSystem INSTANCE = new SoundSystem();

    private final SoundThreadBridge audio = new SoundThreadBridge();
    private final AtomicBoolean reapQueued = new AtomicBoolean();
    private volatile float masterGain = 1.0f;

    private final Map<BufferKey, Integer> buffers = new HashMap<>();
    private final Map<Long, ActiveSound> active = new LinkedHashMap<>();
    private final ArrayDeque<Integer> freeSources = new ArrayDeque<>();
    private int sourceCount;
    private long nextPlaybackId = 1L;
    private Boolean efxSupported;
    private boolean efxWarningLogged;

    private SoundSystem() {
        SoundRegistry.get().discover("combatant.client");
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static SoundSystem get() {
        return INSTANCE;
    }

    public SoundInstance play(SoundKey key) {
        return play(key.soundDefinition(), SoundOptions.DEFAULT);
    }

    public SoundInstance play(SoundKey key, SoundOptions options) {
        return play(key.soundDefinition(), options);
    }

    /** Plays either a WAV or a vanilla-style OGG resource directly. */
    public SoundInstance play(Identifier resource) {
        return play(SoundDefinition.direct(resource), SoundOptions.DEFAULT);
    }

    public SoundInstance play(Identifier resource, SoundOptions options) {
        return play(SoundDefinition.direct(resource), options);
    }

    public SoundInstance play(SoundDefinition definition, SoundOptions options) {
        if (definition == null || !audio.isReady()) return SoundInstance.REJECTED;
        SoundRequest request = SoundRequest.create(definition, options);
        SoundPlayEvent playEvent = Events.BUS.post(new SoundPlayEvent(request));
        if (playEvent != null && playEvent.isCancelled()) return SoundInstance.REJECTED;
        if (request.gain() <= 0.0f) return SoundInstance.REJECTED;
        return audio.call(() -> playOnAudio(definition, request), SoundInstance.REJECTED);
    }

    private SoundInstance playOnAudio(SoundDefinition definition, SoundRequest request) {
        int acquiredSource = 0;
        try {
            reapFinished();
            int buffer = ensureBuffer(definition.resource(), request.spatial());
            acquiredSource = acquireSource();
            if (acquiredSource == 0) return SoundInstance.REJECTED;

            long playbackId = nextPlaybackId++;
            if (nextPlaybackId <= 0L) nextPlaybackId = 1L;
            SoundInstance instance = new SoundInstance(this, playbackId);
            EfxBinding efx = configure(acquiredSource, buffer, request);
            ActiveSound sound = new ActiveSound(playbackId, acquiredSource, definition, instance,
                    request.gain(), request.looping(), request.spatial(), efx);
            active.put(playbackId, sound);
            AL10.alSourcePlay(acquiredSource);
            postEvent(new SoundStartedEvent(definition, instance));
            return instance;
        } catch (RuntimeException error) {
            boolean tracked = false;
            for (ActiveSound sound : active.values()) {
                if (sound.source == acquiredSource) {
                    tracked = true;
                    break;
                }
            }
            if (acquiredSource != 0 && !tracked) {
                try {
                    AL10.alSourceStop(acquiredSource);
                    AL10.alSourcei(acquiredSource, AL10.AL_BUFFER, 0);
                    freeSources.offerLast(acquiredSource);
                } catch (RuntimeException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            report(SoundErrorEvent.Operation.PLAY, definition, error);
            return SoundInstance.REJECTED;
        }
    }

    /** Bound by SoundEngine's constructor before Library.init is ever called. */
    public void attachExecutor(Executor executor) {
        audio.attach(executor);
    }

    /** Called at Library.init RETURN, after vanilla has installed its capabilities. */
    public void onLibraryReady() {
        // getCapabilities is safe here: unlike a raw AL10 call it does not initialize
        // LWJGL's write-once function-pointer holder with an absent context.
        AL.getCapabilities();
        audio.opened();
        discardCaches();
    }

    /** Called at Library.cleanup HEAD, while the old context still exists. */
    public void onLibraryClosing() {
        if (!audio.isReady()) return;
        audio.close(this::resetNative);
    }

    public boolean isReady() { return audio.isReady(); }

    public void setMasterGain(double gain) {
        masterGain = Math.max(0.0f, (float) gain);
        audio.execute(() -> {
            for (ActiveSound sound : active.values())
                AL10.alSourcef(sound.source, AL10.AL_GAIN, masterGain * sound.gain);
        }, e -> report(SoundErrorEvent.Operation.CONTROL, null, e));
    }

    public float getMasterGain() { return masterGain; }

    public void tick() {
        if (!audio.isReady() || !reapQueued.compareAndSet(false, true)) return;
        audio.execute(() -> {
            try { reapFinished(); }
            finally { reapQueued.set(false); }
        }, e -> {
            reapQueued.set(false);
            report(SoundErrorEvent.Operation.CONTROL, null, e);
        });
    }

    boolean isPlaying(long id) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null) return false;
            int state = AL10.alGetSourcei(sound.source, AL10.AL_SOURCE_STATE);
            return state == AL10.AL_PLAYING || state == AL10.AL_PAUSED;
        }, false);
    }

    boolean stop(long id) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null) return false;
            AL10.alSourceStop(sound.source);
            retire(sound, SoundStoppedEvent.Reason.STOPPED);
            return true;
        }, false);
    }

    boolean pause(long id) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null || AL10.alGetSourcei(sound.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) return false;
            AL10.alSourcePause(sound.source);
            return true;
        }, false);
    }

    boolean resume(long id) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null || AL10.alGetSourcei(sound.source, AL10.AL_SOURCE_STATE) != AL10.AL_PAUSED) return false;
            AL10.alSourcePlay(sound.source);
            return true;
        }, false);
    }

    boolean setGain(long id, double gain) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null) return false;
            sound.gain = Math.max(0.0f, (float) gain);
            AL10.alSourcef(sound.source, AL10.AL_GAIN, masterGain * sound.gain);
            return true;
        }, false);
    }

    boolean setPitch(long id, double pitch) {
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null) return false;
            AL10.alSourcef(sound.source, AL10.AL_PITCH, Math.max(0.01f, (float) pitch));
            return true;
        }, false);
    }

    boolean setPosition(long id, Vec3 position) {
        if (position == null) return false;
        return audio.call(() -> {
            ActiveSound sound = active.get(id);
            if (sound == null || !sound.spatial) return false;
            AL10.alSource3f(sound.source, AL10.AL_POSITION,
                    (float) position.x, (float) position.y, (float) position.z);
            return true;
        }, false);
    }

    @AssetLoad(order = 400)
    public static void reloadAssets() { get().reset(); }

    /** Resource reloads invalidate cached native objects only on Minecraft's audio executor. */
    public void reset() {
        try {
            audio.call(() -> {
                resetNative();
                return true;
            }, false);
        } catch (RuntimeException e) {
            report(SoundErrorEvent.Operation.RESET, null, e);
        }
    }

    private void resetNative() {
        try {
            List<ActiveSound> sounds = new ArrayList<>(active.values());
            for (ActiveSound sound : sounds) {
                AL10.alSourceStop(sound.source);
                retire(sound, SoundStoppedEvent.Reason.RESET);
            }
            for (int source : freeSources) AL10.alDeleteSources(source);
            for (int buffer : buffers.values()) AL10.alDeleteBuffers(buffer);
        } finally {
            discardCaches();
        }
    }

    /** Java-only invalidation; never delete IDs belonging to a previous context. */
    private void discardCaches() {
        SoundDebugStats.onSourcesCleared(sourceCount);
        SoundDebugStats.onBuffersCleared(buffers.size());
        active.clear();
        freeSources.clear();
        buffers.clear();
        sourceCount = 0;
        efxSupported = null;
        efxWarningLogged = false;
        reapQueued.set(false);
    }

    private int ensureBuffer(Identifier resource, boolean spatial) {
        BufferKey key = new BufferKey(resource, spatial);
        Integer cached = buffers.get(key);
        if (cached != null && AL10.alIsBuffer(cached)) return cached;

        PcmAudioData decoded;
        try (InputStream stream = resourceStream(resource)) {
            if (stream == null) throw new FileNotFoundException("Sound resource not found: " + resource);
            decoded = AudioDecoders.decode(resource, stream);
        } catch (IOException error) {
            postEvent(new SoundErrorEvent(SoundErrorEvent.Operation.LOAD, SoundDefinition.direct(resource), error));
            throw new UncheckedIOException(error);
        }
        if (spatial) decoded = decoded.toMono();

        ByteBuffer data = BufferUtils.createByteBuffer(decoded.data().length);
        data.put(decoded.data()).flip();
        int buffer = AL10.alGenBuffers();
        try {
            AL10.alBufferData(buffer, decoded.openAlFormat(), data, decoded.sampleRate());
        } catch (RuntimeException e) {
            AL10.alDeleteBuffers(buffer);
            throw e;
        }
        buffers.put(key, buffer);
        SoundDebugStats.onBufferCreated(decoded.data().length);
        postEvent(new SoundLoadedEvent(resource, decoded.channels(), decoded.sampleRate(), decoded.data().length, spatial));
        return buffer;
    }

    private InputStream resourceStream(Identifier resource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getResourceManager() == null) return null;
        return minecraft.getResourceManager().getResource(resource).map(found -> {
            try {
                return found.open();
            } catch (Exception ignored) {
                return null;
            }
        }).orElse(null);
    }

    private int acquireSource() {
        Integer source = freeSources.pollFirst();
        if (source != null && AL10.alIsSource(source)) return source;

        if (sourceCount < MAX_SOURCES) {
            int created = AL10.alGenSources();
            sourceCount++;
            SoundDebugStats.onSourceCreated();
            return created;
        }

        ActiveSound victim = null;
        for (ActiveSound candidate : active.values()) {
            if (!candidate.looping) {
                victim = candidate;
                break;
            }
        }
        if (victim == null && !active.isEmpty()) victim = active.values().iterator().next();
        if (victim == null) return 0;
        int reused = victim.source;
        AL10.alSourceStop(reused);
        retire(victim, SoundStoppedEvent.Reason.STOLEN, false);
        return reused;
    }

    private EfxBinding configure(int source, int buffer, SoundRequest request) {
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, masterGain * request.gain());
        AL10.alSourcef(source, AL10.AL_PITCH, request.pitch());
        AL10.alSourcei(source, AL10.AL_LOOPING, request.looping() ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, request.spatial() ? AL10.AL_FALSE : AL10.AL_TRUE);
        Vec3 position = request.spatial() ? request.position() : Vec3.ZERO;
        AL10.alSource3f(source, AL10.AL_POSITION, (float) position.x, (float) position.y, (float) position.z);
        Vec3 velocity = request.spatial() ? request.velocity() : Vec3.ZERO;
        Vec3 direction = request.spatial() ? request.direction() : Vec3.ZERO;
        AL10.alSource3f(source, AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);
        AL10.alSource3f(source, AL10.AL_DIRECTION, (float) direction.x, (float) direction.y, (float) direction.z);
        AL10.alSourcef(source, AL10.AL_CONE_INNER_ANGLE, request.spatial() ? request.coneInnerAngle() : 360.0f);
        AL10.alSourcef(source, AL10.AL_CONE_OUTER_ANGLE, request.spatial() ? request.coneOuterAngle() : 360.0f);
        AL10.alSourcef(source, AL10.AL_CONE_OUTER_GAIN, request.spatial() ? request.coneOuterGain() : 0.0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, request.spatial() ? request.rolloff() : 0.0f);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, request.referenceDistance());
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, request.maxDistance());
        if (request.spatial() && supportsEfx()) {
            AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, request.airAbsorption());
            AL10.alSourcef(source, EXTEfx.AL_ROOM_ROLLOFF_FACTOR, request.roomRolloff());
        }
        return applyEqualizer(source, request.equalizer());
    }

    private EfxBinding applyEqualizer(int source, SoundEqualizer equalizer) {
        if (equalizer == null) return EfxBinding.NONE;
        if (!supportsEfx()) {
            if (!efxWarningLogged) {
                efxWarningLogged = true;
                DebugLog.warn("OpenAL EFX is unavailable; custom sound equalizers will be bypassed");
            }
            return EfxBinding.NONE;
        }

        int effect = 0;
        int slot = 0;
        int dryFilter = 0;
        try {
            AL10.alGetError(); // Clear the previous error once, without an unbounded loop.
            effect = EXTEfx.alGenEffects();
            if (effect == 0) throw new IllegalStateException("OpenAL could not allocate an EFX effect");
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EQUALIZER);
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_LOW_GAIN, equalizer.lowGainLinear());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_LOW_CUTOFF, equalizer.lowCutoffHz());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID1_GAIN, equalizer.mid1GainLinear());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID1_CENTER, equalizer.mid1CenterHz());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID1_WIDTH, equalizer.mid1Width());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID2_GAIN, equalizer.mid2GainLinear());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID2_CENTER, equalizer.mid2CenterHz());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_MID2_WIDTH, equalizer.mid2Width());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_HIGH_GAIN, equalizer.highGainLinear());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EQUALIZER_HIGH_CUTOFF, equalizer.highCutoffHz());

            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            if (slot == 0) throw new IllegalStateException("OpenAL has no free auxiliary effect slot");
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, equalizer.wetMix());

            dryFilter = EXTEfx.alGenFilters();
            if (dryFilter == 0) throw new IllegalStateException("OpenAL could not allocate a direct filter");
            EXTEfx.alFilteri(dryFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            float dryGain = 1.0f - equalizer.wetMix();
            EXTEfx.alFilterf(dryFilter, EXTEfx.AL_LOWPASS_GAIN, dryGain);
            EXTEfx.alFilterf(dryFilter, EXTEfx.AL_LOWPASS_GAINHF, dryGain);
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, dryFilter);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, EXTEfx.AL_FILTER_NULL);
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                throw new IllegalStateException("OpenAL rejected the equalizer chain (error 0x"
                        + Integer.toHexString(error) + ")");
            }
            return new EfxBinding(effect, slot, dryFilter);
        } catch (RuntimeException error) {
            if (dryFilter != 0) EXTEfx.alDeleteFilters(dryFilter);
            if (slot != 0) EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            if (effect != 0) EXTEfx.alDeleteEffects(effect);
            report(SoundErrorEvent.Operation.PLAY, null, error);
            return EfxBinding.NONE;
        }
    }

    private boolean supportsEfx() {
        if (efxSupported != null) return efxSupported;
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0L ? 0L : ALC10.alcGetContextsDevice(context);
        efxSupported = device != 0L && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
        return efxSupported;
    }

    private void reapFinished() {
        if (active.isEmpty()) return;
        List<ActiveSound> finished = new ArrayList<>();
        for (ActiveSound sound : active.values()) {
            int state = AL10.alGetSourcei(sound.source, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && state != AL10.AL_PAUSED) finished.add(sound);
        }
        for (ActiveSound sound : finished) retire(sound, SoundStoppedEvent.Reason.FINISHED);
    }

    private void retire(ActiveSound sound, SoundStoppedEvent.Reason reason) {
        retire(sound, reason, true);
    }

    private void retire(ActiveSound sound, SoundStoppedEvent.Reason reason, boolean recycleSource) {
        if (active.remove(sound.id) == null) return;
        releaseEfx(sound.source, sound.efx);
        AL10.alSourcei(sound.source, AL10.AL_BUFFER, 0);
        if (recycleSource) freeSources.offerLast(sound.source);
        postEvent(new SoundStoppedEvent(sound.definition, sound.instance, reason));
    }

    private void releaseEfx(int source, EfxBinding binding) {
        if (binding == null || binding == EfxBinding.NONE) return;
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 0, EXTEfx.AL_FILTER_NULL);
        EXTEfx.alAuxiliaryEffectSloti(binding.slot, EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
        EXTEfx.alDeleteFilters(binding.dryFilter);
        EXTEfx.alDeleteAuxiliaryEffectSlots(binding.slot);
        EXTEfx.alDeleteEffects(binding.effect);
    }

    private static void postEvent(combatant.client.events.Event event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        // Do not run game/module listeners on Minecraft's native audio thread.
        if (mc.isSameThread()) Events.BUS.post(event);
        else mc.execute(() -> Events.BUS.post(event));
    }

    private void report(SoundErrorEvent.Operation operation, SoundDefinition definition, Throwable error) {
        DebugLog.error("Custom sound %s failed for %s", error, operation,
                definition == null ? "<engine>" : definition.resource());
        postEvent(new SoundErrorEvent(operation, definition, error));
    }

    private record BufferKey(Identifier resource, boolean spatial) {
    }

    private static final class ActiveSound {
        final long id;
        final int source;
        final SoundDefinition definition;
        final SoundInstance instance;
        final boolean looping;
        final boolean spatial;
        final EfxBinding efx;
        float gain;

        ActiveSound(long id, int source, SoundDefinition definition, SoundInstance instance,
                    float gain, boolean looping, boolean spatial, EfxBinding efx) {
            this.id = id;
            this.source = source;
            this.definition = definition;
            this.instance = instance;
            this.gain = gain;
            this.looping = looping;
            this.spatial = spatial;
            this.efx = efx;
        }
    }

    private record EfxBinding(int effect, int slot, int dryFilter) {
        private static final EfxBinding NONE = new EfxBinding(0, 0, 0);
    }
}
