/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import combatant.client.render.engine.core.CombatantRenderSystem;

public final class MotionBlurResources {
    private TextureTarget previousColor;
    private boolean previousValid;

    public boolean ensure(Minecraft mc) {
        if (mc == null || mc.getWindow() == null) return false;
        return ensure(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight()
        );
    }

    public boolean ensure(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (w <= 0 || h <= 0) return false;

        TextureTarget next = CombatantRenderSystem.resources().persistentFramebuffer(
                "combatant-motion-blur-previous-color", w, h, false, "MotionBlurResources");
        if (previousColor != next) {
            previousColor = next;
            previousValid = false;
        }
        return true;
    }

    public GpuTextureView previousColorView() {
        return previousColor != null ? previousColor.getColorTextureView() : null;
    }

    public boolean hasPreviousColor() {
        return previousValid && previousColorView() != null;
    }

    public void captureCurrent(GpuTextureView src) {
        GpuTextureView previous = previousColorView();
        if (src == null || previous == null) return;
        PostProcessManager.copy(src, previous);
        previousValid = true;
    }

    public void invalidate() {
        previousValid = false;
    }
}
