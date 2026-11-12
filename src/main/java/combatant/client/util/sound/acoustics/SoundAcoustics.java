/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound.acoustics;

import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.modules.visuals.PhysicSound;
import combatant.client.util.sound.SoundSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Main-thread sampler for vanilla and Combatant sounds. The audio engine accepts
 * immutable profiles so sound executor tasks never read ClientLevel or entity state.
 */
public final class SoundAcoustics {
    private static final int VANILLA_SAMPLES_PER_TICK = 12;
    private static final int RESAMPLE_TICKS = 6;
    private static final int ROOM_RESAMPLE_TICKS = 12;

    private final SoundSystem system;
    private final IdentityHashMap<SoundInstance, Tracked> tracked = new IdentityHashMap<>();
    private ClientLevel level;
    private Vec3 listener = Vec3.ZERO;
    private AcousticProfile.Room room = AcousticProfile.Room.NONE;
    private Settings previousSettings;
    private long observedContext;
    private int ticks;
    private int customTicks;
    private int scanCursor;
    private int initialSamples;
    private int roomTicks;

    public SoundAcoustics(SoundSystem system) {
        this.system = system;
    }

    /** Schedules a profile after SoundEngine has configured a new channel. */
    public void onVanillaStarted(SoundInstance sound, ChannelAccess.ChannelHandle handle) {
        if (handle == null || isIgnored(sound) || !system.isReady() || system.acousticsUnavailable()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || client.player == null) return;
        Settings settings = settings();
        if (settings == null || initialSamples >= VANILLA_SAMPLES_PER_TICK) return;
        initialSamples++;

        Vec3 ears = client.player.getEyePosition();
        Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
        AcousticProfile.Room currentRoom = roomAt(client.level, ears, settings.caveEcho);
        AcousticProfile profile = AcousticEnvironment.sample(client.level, client.player, ears, position,
                settings.underwater, settings.occlusion, currentRoom);
        tracked.put(sound, new Tracked(handle, position, ears, ticks, profile));
        system.enableAcoustics();
        system.applyVanillaAcoustics(handle, profile);
    }

    public void updateVanilla(Map<SoundInstance, ChannelAccess.ChannelHandle> playing) {
        Settings settings = settings();
        if (settings == null || !system.isReady()) {
            if (previousSettings != null) disable();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        if (client.level == null || client.player == null) {
            if (previousSettings != null) disable();
            return;
        }

        if (level != client.level || observedContext != system.acousticContextGeneration()) {
            if (level != null && level != client.level) system.clearAcoustics();
            tracked.clear();
            level = client.level;
            observedContext = system.acousticContextGeneration();
            roomTicks = ROOM_RESAMPLE_TICKS;
            ticks = 0;
            scanCursor = 0;
        }
        if (!settings.equals(previousSettings)) {
            tracked.clear();
            roomTicks = ROOM_RESAMPLE_TICKS;
            previousSettings = settings;
        }
        system.enableAcoustics();
        if (system.acousticsUnavailable()) return;

        ticks++;
        initialSamples = 0;
        if (++roomTicks >= ROOM_RESAMPLE_TICKS || listener.distanceToSqr(client.player.getEyePosition()) > 4.0) {
            listener = client.player.getEyePosition();
            room = settings.caveEcho ? AcousticEnvironment.room(level, listener) : AcousticProfile.Room.NONE;
            roomTicks = 0;
        } else {
            listener = client.player.getEyePosition();
        }

        Iterator<Map.Entry<SoundInstance, Tracked>> cursor = tracked.entrySet().iterator();
        while (cursor.hasNext()) {
            Map.Entry<SoundInstance, Tracked> entry = cursor.next();
            if (playing.get(entry.getKey()) != entry.getValue().handle) cursor.remove();
        }

        int budget = VANILLA_SAMPLES_PER_TICK;
        int size = playing.size();
        if (size == 0) return;
        int start = Math.floorMod(scanCursor, size);
        scanCursor = (start + VANILLA_SAMPLES_PER_TICK) % size;
        Iterator<Map.Entry<SoundInstance, ChannelAccess.ChannelHandle>> sounds = playing.entrySet().iterator();
        for (int i = 0; i < start; i++) sounds.next();
        for (int inspected = 0; inspected < size && budget > 0; inspected++) {
            if (!sounds.hasNext()) sounds = playing.entrySet().iterator();
            Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry = sounds.next();
            SoundInstance sound = entry.getKey();
            ChannelAccess.ChannelHandle handle = entry.getValue();
            if (handle == null || handle.isStopped() || isIgnored(sound)) continue;

            Tracked previous = tracked.get(sound);
            Vec3 position = new Vec3(sound.getX(), sound.getY(), sound.getZ());
            boolean resample = previous == null || previous.handle != handle
                    || ticks - previous.tick >= RESAMPLE_TICKS
                    || position.distanceToSqr(previous.position) >= 0.25
                    || listener.distanceToSqr(previous.listener) >= 0.25;
            if (resample) {
                AcousticProfile target = AcousticEnvironment.sample(level, client.player, listener, position,
                        settings.underwater, settings.occlusion, room);
                if (previous == null || previous.handle != handle) {
                    previous = new Tracked(handle, position, listener, ticks, target);
                    tracked.put(sound, previous);
                    system.applyVanillaAcoustics(handle, target);
                } else {
                    previous.target = target;
                    previous.position = position;
                    previous.listener = listener;
                    previous.tick = ticks;
                }
                budget--;
            }
            if (previous != null && !previous.applied.equals(previous.target)) {
                int current = previous.applied.lowPass();
                int target = previous.target.lowPass();
                current += Integer.signum(target - current) * Math.min(2, Math.abs(target - current));
                previous.applied = new AcousticProfile(current, previous.target.room());
                system.applyVanillaAcoustics(handle, previous.applied);
            }
        }
    }

    /** Called from the custom engine's client-tick callback for positional custom audio. */
    public void updateCustom(Map<Long, Vec3> soundPositions) {
        if (soundPositions.isEmpty() || !system.isReady()) return;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || client.player == null) return;
        Settings settings = settings();
        if (settings == null || system.acousticsUnavailable()) return;
        if (++customTicks % RESAMPLE_TICKS != 0) return;
        Vec3 ears = client.player.getEyePosition();
        AcousticProfile.Room currentRoom = roomAt(client.level, ears, settings.caveEcho);
        Map<Long, AcousticProfile> profiles = new HashMap<>();
        for (Map.Entry<Long, Vec3> entry : soundPositions.entrySet()) {
            profiles.put(entry.getKey(), AcousticEnvironment.sample(client.level, client.player, ears, entry.getValue(),
                    settings.underwater, settings.occlusion, currentRoom));
        }
        system.applyCustomAcoustics(profiles);
    }

    public AcousticProfile initialCustomProfile(Vec3 position) {
        if (position == null) return null;
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null || client.player == null) return null;
        Settings settings = settings();
        if (settings == null || system.acousticsUnavailable()) return null;
        Vec3 ears = client.player.getEyePosition();
        AcousticProfile.Room currentRoom = roomAt(client.level, ears, settings.caveEcho);
        system.enableAcoustics();
        return AcousticEnvironment.sample(client.level, client.player, ears, position,
                settings.underwater, settings.occlusion, currentRoom);
    }

    private AcousticProfile.Room roomAt(ClientLevel currentLevel, Vec3 ears, boolean enabled) {
        if (!enabled) return AcousticProfile.Room.NONE;
        if (currentLevel == level && roomTicks < ROOM_RESAMPLE_TICKS && listener.distanceToSqr(ears) <= 4.0) {
            return room;
        }
        return AcousticEnvironment.room(currentLevel, ears);
    }

    public void disable() {
        tracked.clear();
        previousSettings = null;
        level = null;
        ticks = 0;
        customTicks = 0;
        scanCursor = 0;
        initialSamples = 0;
        roomTicks = ROOM_RESAMPLE_TICKS;
        room = AcousticProfile.Room.NONE;
        system.clearAcoustics();
    }

    private static Settings settings() {
        PhysicSound module = ModuleManager.get(PhysicSound.class);
        return module != null && module.isEnabled()
                ? new Settings(module.underwaterEnabled(), module.occlusionEnabled(), module.caveEchoEnabled())
                : null;
    }

    private static boolean isIgnored(SoundInstance sound) {
        if (sound == null || sound.isRelative()) return true;
        SoundSource type = sound.getSource();
        return type == SoundSource.MUSIC || type == SoundSource.RECORDS || type == SoundSource.UI;
    }

    private record Settings(boolean underwater, boolean occlusion, boolean caveEcho) {
    }

    private static final class Tracked {
        final ChannelAccess.ChannelHandle handle;
        Vec3 position;
        Vec3 listener;
        int tick;
        AcousticProfile target;
        AcousticProfile applied;

        Tracked(ChannelAccess.ChannelHandle handle, Vec3 position, Vec3 listener, int tick, AcousticProfile profile) {
            this.handle = handle;
            this.position = position;
            this.listener = listener;
            this.tick = tick;
            this.target = profile;
            this.applied = profile;
        }
    }
}
