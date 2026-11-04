/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.core.CombatantRenderSystem;
import net.minecraft.client.Minecraft;

/** A dedicated depth attachment for the preview's 3D content, not world/GUI depth. */
final class VisualPreviewDepthTarget {
    private VisualPreviewDepthTarget() {}

    static TextureTarget clear(Minecraft mc) {
        var main = mc.gameRenderer.mainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return null;
        TextureTarget depthTarget = CombatantRenderSystem.resources().persistentFramebuffer(
                "visual-preview-subject-depth", main.width, main.height, true, "VisualPreview");
        RenderSystem.getDevice().createCommandEncoder()
                .clearDepthTexture(depthTarget.getDepthTexture(), 0.0D);
        return depthTarget;
    }
}
