/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import combatant.client.util.CompiledPipelineSource;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.Executor;

@Mixin(PipelineBuilder.class)
public abstract class PipelineBuilderMixin {
    @Inject(method = "lambda$compilePipeline$1", at = @At("RETURN"))
    private static void combatant$rememberSourcePipeline(RenderPipeline pipeline,
                                                         BackendRenderPipeline.Pending pending,
                                                         Executor executor,
                                                         ReferenceArrayList<?> shaders,
                                                         Object2IntOpenHashMap<?> indices,
                                                         CallbackInfoReturnable<CompiledRenderPipeline> cir) {
        if (cir.getReturnValue() instanceof CompiledPipelineSource source) {
            source.combatant$setSource(pipeline);
        }
    }

    /**
     * The 26.3 pipeline compiler rejects vertex shaders that declare more components for an input
     * attribute than the vertex format supplies. Combatant's shaders declare {@code vec4 Position}
     * for 2D/3D formats and rely on the implicit w = 1 default, so the check is relaxed for them.
     */
    @ModifyExpressionValue(method = "generateBackendCreateInfo",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/GpuFormat;componentCount()I",
                    ordinal = 0))
    private int combatant$allowWiderShaderAttributes(int formatComponents) {
        return Math.max(formatComponents, 4);
    }
}
