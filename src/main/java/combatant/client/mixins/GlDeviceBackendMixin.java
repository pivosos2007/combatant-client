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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import combatant.client.util.logging.DebugLog;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import combatant.client.render.engine.profiler.ProfilerPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;
import combatant.client.mixininterface.IGlBackendInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class GlDeviceBackendMixin implements IGlBackendInfo {
    @Shadow
    protected static boolean USE_GL_KHR_debug;
    @Shadow
    protected static boolean USE_GL_ARB_direct_state_access;

    @Override
    @Invoker("directStateAccess")
    public abstract DirectStateAccess combatant$directStateAccess();

    @Override
    @Invoker("frameBufferCache")
    public abstract FrameBufferCache combatant$frameBufferCache();

    @Override
    public boolean combatant$nativeDirectStateAccess() {
        return USE_GL_ARB_direct_state_access;
    }

    @Override
    public boolean combatant$khrDebug() {
        return USE_GL_KHR_debug;
    }
    @Unique
    private ProfilerPhase.Scope combatant$pipelineCompileScope;


    @WrapOperation(
            method = {"compileShader", "compileProgram"},
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"
            )
    )
    private void combatant$wrapCombatantCompileError(Logger logger,
                                                       String format,
                                                       Object first,
                                                       Object second,
                                                       Operation<Void> original) {
        if (combatant$containsCombatantId(first) || combatant$containsCombatantId(second)) {
            DebugLog.error("[RenderPipeline] " + combatant$formatSlf4j(format, first, second));
            return;
        }

        original.call(logger, format, first, second);
    }

    @WrapOperation(
            method = "compileShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;[Ljava/lang/Object;)V"
            )
    )
    private void combatant$wrapCombatantShaderCompileError(Logger logger,
                                                             String format,
                                                             Object[] args,
                                                             Operation<Void> original) {
        if (combatant$containsCombatantId(args)) {
            DebugLog.error("[RenderPipeline] " + combatant$formatSlf4j(format, args));
            return;
        }

        original.call(logger, format, args);
    }

    @Unique
    private static boolean combatant$containsCombatantId(Object value) {
        if (value instanceof Identifier id) {
            return "combatant".equals(id.getNamespace());
        }
        if (value instanceof Object[] values) {
            for (Object element : values) {
                if (combatant$containsCombatantId(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static String combatant$formatSlf4j(String format, Object... args) {
        String template = String.valueOf(format);
        if (args == null || args.length == 0) {
            return template;
        }

        StringBuilder out = new StringBuilder(template.length() + args.length * 16);
        int cursor = 0;
        int argIndex = 0;
        while (argIndex < args.length) {
            int placeholder = template.indexOf("{}", cursor);
            if (placeholder < 0) {
                break;
            }
            out.append(template, cursor, placeholder);
            out.append(String.valueOf(args[argIndex++]));
            cursor = placeholder + 2;
        }
        out.append(template, cursor, template.length());
        while (argIndex < args.length) {
            out.append(' ').append(String.valueOf(args[argIndex++]));
        }
        return out.toString();
    }

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
