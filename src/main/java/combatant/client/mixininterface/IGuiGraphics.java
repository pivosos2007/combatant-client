/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

public interface IGuiGraphics {
    /**
     * Runs a task without vanilla UI scaling applied to DrawContext's matrix stack.
     */
    void combatant$runUnscaled(Runnable task);

    /**
     * Runs a task with an additional 2D transform applied to DrawContext's matrix stack.
     * Order: translate(tx, ty) then rotate(angleRad). Angle in radians.
     */
    void combatant$withTransform(float tx, float ty, float angleRad, Runnable task);

    void combatant$drawItemBar(net.minecraft.world.item.ItemStack stack, int x, int y);

    void combatant$drawCooldownProgress(net.minecraft.world.item.ItemStack stack, int x, int y);

    void combatant$addDeferredHudMarker(combatant.client.render.engine.renderer.Renderer2D.Deferred2DLayer layer);
}

