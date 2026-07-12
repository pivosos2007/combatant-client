/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi;

import combatant.client.render.engine.rhi.blit.TextureBlitter;
import combatant.client.render.engine.rhi.clip.ShapeClipBackend;
import combatant.client.render.engine.rhi.fullscreen.FullscreenBackend;
import combatant.client.render.engine.rhi.msaa.MsaaControl;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.rhi.resource.RenderResourceManager;
import combatant.client.render.engine.rhi.state.PipelineStateBackend;
import combatant.client.render.engine.rhi.upload.DynamicMeshBackend;

public interface CombatantRhi extends AutoCloseable {
    DynamicMeshBackend dynamicMeshes();

    FullscreenBackend fullscreen();

    TextureBlitter textureBlitter();

    MsaaControl msaa();

    ShapeClipBackend shapeClip();

    PipelineStateBackend pipelineState();

    RhiStats stats();

    RenderPipelineRegistry pipelines();

    RenderResourceManager resources();

    void beginFrame(long frameId);

    void endRenderSubmission();

    void framePresented();

    void drawMesh(RhiDrawCommand command);

    void drawFullscreen(FullscreenDrawCommand command);

    @Override
    void close();
}
