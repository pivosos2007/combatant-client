/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess.graph;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;

/**
 * Owner for temporal/history resources used by motion blur and future temporal effects.
 */
public final class HistoryBufferManager {
    private @Nullable GpuTextureView previousColor;
    private long previousFrameId = -1L;

    public void update(long frameId, @Nullable GpuTextureView finalColor) {
        previousFrameId = frameId;
        previousColor = finalColor;
    }

    public @Nullable GpuTextureView previousColor() {
        return previousColor;
    }

    public long previousFrameId() {
        return previousFrameId;
    }

    public void invalidate() {
        previousColor = null;
        previousFrameId = -1L;
    }
}
