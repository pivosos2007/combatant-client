/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.textures.GpuTexture;

import java.util.ArrayDeque;
import java.util.Queue;

public final class TexturePool implements AutoCloseable {
    private final Queue<GpuTexture> retired = new ArrayDeque<>();
    private final ResourceLeakTracker leakTracker;
    private long retirements;
    private long closes;

    public TexturePool(ResourceLeakTracker leakTracker) {
        this.leakTracker = leakTracker;
    }

    /**
     * Compatibility constructor for legacy tests/tools.
     */
    public TexturePool() {
        this(new ResourceLeakTracker());
    }

    public void register(GpuTexture texture) {
        if (texture != null) leakTracker.register(texture);
    }

    public void retire(GpuTexture texture) {
        if (texture == null) return;
        retired.add(texture);
        retirements++;
    }

    public void drain(int budget) {
        while (budget-- > 0 && !retired.isEmpty()) {
            GpuTexture texture = retired.poll();
            leakTracker.release(texture);
            if (!texture.isClosed()) texture.close();
            closes++;
        }
    }

    public long retirements() {
        return retirements;
    }

    public long closes() {
        return closes;
    }

    public int backlog() {
        return retired.size();
    }

    @Override
    public void close() {
        drain(Integer.MAX_VALUE);
    }
}
