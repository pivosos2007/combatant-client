/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import combatant.client.util.CompiledPipelineSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FrontendRenderPipeline.class)
public abstract class FrontendRenderPipelineMixin implements CompiledPipelineSource {
    @Unique
    private RenderPipeline combatant$source;

    @Override
    public RenderPipeline combatant$getSource() {
        return combatant$source;
    }

    @Override
    public void combatant$setSource(RenderPipeline pipeline) {
        this.combatant$source = pipeline;
    }
}
