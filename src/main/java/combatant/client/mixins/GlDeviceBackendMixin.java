/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import combatant.client.render.engine.profiler.ProfilerPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;
import combatant.client.mixininterface.IGlBackendInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class GlDeviceBackendMixin implements IGlBackendInfo {
    @Override
    @Invoker("directStateAccess")
    public abstract DirectStateAccess combatant$directStateAccess();

    @Override
    @Invoker("frameBufferCache")
    public abstract FrameBufferCache combatant$frameBufferCache();
    @Unique
    private ProfilerPhase.Scope combatant$pipelineCompileScope;

    @Inject(method = "compilePipeline", at = @At("HEAD"))
    private void combatant$profilePipelineCompileHead(RenderPipeline pipeline, ShaderSource shaderSource, CallbackInfoReturnable<GlRenderPipeline> cir) {
        ProfilerPhase.Scope staleScope = combatant$pipelineCompileScope;
        combatant$pipelineCompileScope = null;
        if (staleScope != null) staleScope.close();

        if (ProfilerPhase.isActive()) {
            combatant$pipelineCompileScope = ProfilerPhase.scope("gl:compile_pipeline");
        }
    }

    @Inject(method = "compilePipeline", at = @At("RETURN"))
    private void combatant$profilePipelineCompileReturn(RenderPipeline pipeline, ShaderSource shaderSource, CallbackInfoReturnable<GlRenderPipeline> cir) {
        ProfilerPhase.Scope scope = combatant$pipelineCompileScope;
        combatant$pipelineCompileScope = null;
        if (scope != null) scope.close();
    }

}
