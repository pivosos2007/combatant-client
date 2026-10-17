/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

/** Duck interface on FrontendRenderPipeline exposing the RenderPipeline it was compiled from. */
public interface CompiledPipelineSource {
    RenderPipeline combatant$getSource();

    void combatant$setSource(RenderPipeline pipeline);
}
