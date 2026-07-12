/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;

/**
 * Renderer3D draw command before RHI compilation.
 * <p>
 * This is still CPU-side command data: pipeline/depth/bindings + the mesh encoder that received vertices.
 * WorldPassCompiler owns the transition from this command to RhiDrawCommand and dynamic mesh upload.
 */
public record WorldDrawCommand(RenderPipeline pipeline, Renderer3D.DepthMode depthMode, int lineWidthBits,
                               Renderer3D.BatchBindings bindings, MeshBuilder mesh) {
    public WorldDrawCommand(RenderPipeline pipeline,
                            Renderer3D.DepthMode depthMode,
                            int lineWidthBits,
                            Renderer3D.BatchBindings bindings,
                            MeshBuilder mesh) {
        this.pipeline = pipeline;
        this.depthMode = depthMode != null ? depthMode : Renderer3D.DepthMode.MAIN;
        this.lineWidthBits = lineWidthBits;
        this.bindings = bindings != null ? bindings : Renderer3D.BatchBindings.none();
        this.mesh = mesh;
    }

    public float lineWidth() {
        return lineWidthBits != 0 ? Float.intBitsToFloat(lineWidthBits) : 1.0f;
    }

    public boolean lineMode() {
        return lineWidthBits != 0;
    }

    public boolean canMerge(WorldDrawCommand other) {
        if (other == null) return false;
        return pipeline == other.pipeline
                && depthMode == other.depthMode
                && lineWidthBits == other.lineWidthBits
                && bindings.compatibleWith(other.bindings);
    }
}
