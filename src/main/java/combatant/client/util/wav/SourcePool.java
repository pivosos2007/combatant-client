/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import org.lwjgl.openal.AL10;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Simple source pool to avoid exhausting OpenAL sources.
 */
public final class SourcePool {

    private final int maxSources;
    private final Queue<Integer> free = new ArrayDeque<>();
    private final Set<Integer> used = new HashSet<>();

    public SourcePool(int maxSources) {
        this.maxSources = maxSources;
    }

    public int acquire() {
        // reclaim finished sources
        used.removeIf(src -> {
            int state = AL10.alGetSourcei(src, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                AL10.alSourceStop(src);
                AL10.alSourcei(src, AL10.AL_BUFFER, 0);
                free.offer(src);
                return true;
            }
            return false;
        });

        Integer src = free.poll();
        if (src != null && AL10.alIsSource(src)) {
            used.add(src);
            return src;
        }

        if (used.size() + free.size() < maxSources) {
            int created = AL10.alGenSources();
            WavDebugStats.onSourceCreated();
            used.add(created);
            return created;
        }

        return 0; // none available
    }

    public void release(int src) {
        if (src == 0) return;
        AL10.alSourceStop(src);
        AL10.alSourcei(src, AL10.AL_BUFFER, 0);
        used.remove(src);
        free.offer(src);
    }

    public void clear() {
        int count = used.size() + free.size();
        for (int src : used) AL10.alDeleteSources(src);
        for (int src : free) AL10.alDeleteSources(src);
        used.clear();
        free.clear();
        WavDebugStats.onSourcesCleared(count);
    }

    public void stopAll() {
        for (int src : used) AL10.alSourceStop(src);
        for (int src : free) AL10.alSourceStop(src);
    }
}
