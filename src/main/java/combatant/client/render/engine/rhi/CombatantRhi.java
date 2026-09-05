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
import combatant.client.render.engine.rhi.shader.AdvancedShaderBackend;
import combatant.client.render.engine.rhi.upload.DynamicMeshBackend;

import java.util.List;

public interface CombatantRhi extends AutoCloseable {
    DynamicMeshBackend dynamicMeshes();

    FullscreenBackend fullscreen();

    TextureBlitter textureBlitter();

    MsaaControl msaa();

    ShapeClipBackend shapeClip();

    PipelineStateBackend pipelineState();

    default AdvancedShaderBackend advancedShaders() {
        return AdvancedShaderBackend.UNSUPPORTED;
    }

    RhiStats stats();

    default RhiCapabilities capabilities() {
        return RhiCapabilities.current();
    }

    RenderPipelineRegistry pipelines();

    RenderResourceManager resources();

    void beginFrame(long frameId);

    void endRenderSubmission();

    void framePresented();

    default void drawMesh(RhiDrawCommand command) {
        if (command != null) drawMeshes(List.of(command));
    }

    /**
     * Executes an ordered draw stream. Backends may keep one render pass open across adjacent
     * commands when their attachments are compatible. Command order is never changed.
     */
    void drawMeshes(List<RhiDrawCommand> commands);

    void drawFullscreen(FullscreenDrawCommand command);

    @Override
    void close();
}
