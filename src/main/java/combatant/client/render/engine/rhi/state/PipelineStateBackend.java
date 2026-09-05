/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.state;

import com.mojang.blaze3d.pipeline.RenderPipeline;

/**
 * RHI-owned out-of-band render state applied around Mojang RenderPipeline binds.
 *
 * <p>Keep backend-native state here instead of leaking GL calls from mixins or renderer code.
 * Current GL implementation owns GL_LINE_SMOOTH, line width, MSAA alpha-to-coverage and shape
 * clip state restoration.</p>
 */
public interface PipelineStateBackend {
    void applyPipelineState(RenderPipeline pipeline);

    /**
     * Marks backend-native shadow state unknown after crossing code owned by Blaze3D/another renderer.
     * Backends whose state is fully explicit (for example Vulkan) may keep the default no-op.
     */
    default void invalidateForeignState() {
    }

    void resetRenderPassState();
}
