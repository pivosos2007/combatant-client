/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import combatant.client.render.engine.renderer.Renderer2D;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

/**
 * Zero-vertex vanilla GuiRenderState marker used to splice Combatant's deferred 2D
 * batches into the same logical HUD order as the Fabric/vanilla HUD callback that
 * recorded them.
 */
public final class HudDeferredGuiElement implements GuiElementRenderState {
    private final Renderer2D.Deferred2DLayer layer;
    private final ScreenRectangle bounds;

    public HudDeferredGuiElement(Renderer2D.Deferred2DLayer layer, ScreenRectangle bounds) {
        this.layer = layer;
        this.bounds = bounds;
    }

    public Renderer2D.Deferred2DLayer layer() {
        return layer;
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        // Marker only. GuiRendererMixin consumes it before it reaches the vanilla mesh builder.
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        return bounds;
    }
}
