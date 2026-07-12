/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;

/**
 * Per-frame context shared between future visual passes.
 */
public final class CombatantVisualFrame {
    private int viewportWidth;
    private int viewportHeight;
    private float tickDelta;
    private GpuTextureView sceneColor;
    private GpuTextureView depth;

    public CombatantVisualFrame begin(int viewportWidth, int viewportHeight, float tickDelta,
                                      GpuTextureView sceneColor, GpuTextureView depth) {
        this.viewportWidth = Math.max(0, viewportWidth);
        this.viewportHeight = Math.max(0, viewportHeight);
        this.tickDelta = tickDelta;
        this.sceneColor = sceneColor;
        this.depth = depth;
        return this;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public float getTickDelta() {
        return tickDelta;
    }

    public GpuTextureView getSceneColor() {
        return sceneColor;
    }

    public GpuTextureView getDepth() {
        return depth;
    }
}
