/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import combatant.client.render.engine.command.UiItemCommand;
import combatant.client.render.engine.core.ViewportContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Backend submission for Renderer2D item calls. */
public final class UiItemSubmission {
    private UiItemSubmission() {
    }

    public static void submit(
            boolean texturedRenderer,
            double rendererAlpha,
            @Nullable LocalPlayer player,
            ItemStack stack,
            double x,
            double y,
            float scaleX,
            float scaleY,
            float pivotX,
            float pivotY,
            boolean pivoted,
            int seed,
            boolean drawItem,
            int overlayFlags,
            @Nullable String stackCountText,
            int durabilityThresholdPercent,
            int durabilityTextColorThresholdPercent) {
        if (texturedRenderer) {
            throw new IllegalStateException("Item drawing is supported only on Renderer2D.COLOR.");
        }

        GuiGraphicsExtractor context = ViewportContext.getCurrentContext();
        UiRenderDispatcher.record(new UiItemCommand(
                stack,
                (int) Math.round(x),
                (int) Math.round(y),
                Math.max(scaleX, scaleY)
        ));

        boolean auto = UiRenderDispatcher.beginAutoBatch();
        ItemBatch batch = combatant.client.render.engine.renderer.Renderer2D.UI_BATCHER.getOrCreateItemBatch(context, stack);
        if (batch != null) {
            batch.add(new ItemDrawCommand(
                    context,
                    player,
                    stack,
                    (float) x,
                    (float) y,
                    scaleX,
                    scaleY,
                    pivotX,
                    pivotY,
                    pivoted,
                    seed,
                    drawItem,
                    overlayFlags,
                    stackCountText,
                    durabilityThresholdPercent,
                    durabilityTextColorThresholdPercent,
                    (float) rendererAlpha
            ));
        }
        UiRenderDispatcher.endAutoBatch(auto);
    }
}
