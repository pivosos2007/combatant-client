/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview.api;

import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;

/** Typed render target for HUD preview participants; contains no fake game objects. */
public record HudPreviewCanvas(Renderer2D renderer, TextRenderer text,
                               GuiGraphicsExtractor graphics, float tickDelta,
                               int virtualWidth, int virtualHeight) {
    /** Exact vanilla GUI-scaled dimensions for screen/UI presenters. */
    public float guiWidth() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledWidth() : virtualWidth;
    }

    public float guiHeight() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScaledHeight() : virtualHeight;
    }

    /** Real vanilla GUI-to-framebuffer scale; do not derive this from virtualWidth. */
    public float guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null ? (float) mc.getWindow().getGuiScale() : 1f;
    }

    public float hudScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null
                ? combatant.client.render.engine.math.HudScale.scale(
                    mc.getWindow().getWidth(), mc.getWindow().getHeight()) : 1f;
    }
}
