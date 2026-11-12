/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import java.util.concurrent.ThreadLocalRandom;

/**
 * One stable celestial/cloud layout for a client launch. Not tied to ticks or
 * render frames: the initial sky is fully seeded before its first draw, and a
 * fresh process receives another layout without changing Minecraft world seed.
 */
public final class DreamSkyLayout {
    private static final float[] OFFSETS = createOffsets();

    private DreamSkyLayout() {
    }

    private static float[] createOffsets() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new float[]{
                random.nextFloat() * 4096.0f,
                random.nextFloat() * 4096.0f,
                (random.nextFloat() * 2.0f - 1.0f) * 8192.0f,
                (random.nextFloat() * 2.0f - 1.0f) * 8192.0f
        };
    }

    public static float starX() { return OFFSETS[0]; }
    public static float starY() { return OFFSETS[1]; }
    public static float cloudX() { return OFFSETS[2]; }
    public static float cloudZ() { return OFFSETS[3]; }
}
