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
import com.mojang.blaze3d.opengl.VertexArrayCache;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import combatant.client.util.logging.DebugLog;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GL11C;
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
    @Invoker("vertexArrayCache")
    public abstract VertexArrayCache combatant$vertexArrayCache();

    @Override
    public boolean combatant$nativeDirectStateAccess() {
        return USE_GL_ARB_direct_state_access;
    }

    @Override
    public boolean combatant$khrDebug() {
        return USE_GL_KHR_debug;
    }

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;"
            )
    )
    private GLCapabilities combatant$captureMojangGlCapabilities(Operation<GLCapabilities> original) {
        GLCapabilities caps = original.call();
        combatant$computeShaders = caps.OpenGL43 || caps.GL_ARB_compute_shader;
        combatant$tessellationShaders = caps.OpenGL40 || caps.GL_ARB_tessellation_shader;
        combatant$geometryShaders = caps.OpenGL32 || caps.GL_ARB_geometry_shader4;
        combatant$shaderStorageBuffers = caps.OpenGL43 || caps.GL_ARB_shader_storage_buffer_object;
        combatant$imageLoadStore = caps.OpenGL42 || caps.GL_ARB_shader_image_load_store;
        combatant$multiBind = caps.OpenGL44 || caps.GL_ARB_multi_bind;
        combatant$copyImage = caps.OpenGL43 || caps.GL_ARB_copy_image;
        combatant$attachmentInvalidation = caps.OpenGL43 || caps.GL_ARB_invalidate_subdata;
        combatant$vendor = combatant$glString(GL11C.GL_VENDOR);
        combatant$renderer = combatant$glString(GL11C.GL_RENDERER);
        return caps;
    }

    @Override
    public boolean combatant$computeShaders() { return combatant$computeShaders; }

    @Override
    public boolean combatant$tessellationShaders() { return combatant$tessellationShaders; }

    @Override
    public boolean combatant$geometryShaders() { return combatant$geometryShaders; }

    @Override
    public boolean combatant$shaderStorageBuffers() { return combatant$shaderStorageBuffers; }

    @Override
    public boolean combatant$imageLoadStore() { return combatant$imageLoadStore; }

    @Override
    public boolean combatant$multiBind() { return combatant$multiBind; }

    @Override
    public boolean combatant$copyImage() { return combatant$copyImage; }

    @Override
    public boolean combatant$attachmentInvalidation() { return combatant$attachmentInvalidation; }
    @Override
    public String combatant$vendor() { return combatant$vendor; }

    @Override
    public String combatant$renderer() { return combatant$renderer; }

    @Unique
    private static String combatant$glString(int name) {
        try {
            String value = GL11C.glGetString(name);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Unique private boolean combatant$computeShaders;
    @Unique private boolean combatant$tessellationShaders;
    @Unique private boolean combatant$geometryShaders;
    @Unique private boolean combatant$shaderStorageBuffers;
    @Unique private boolean combatant$imageLoadStore;
    @Unique private boolean combatant$multiBind;
    @Unique private boolean combatant$copyImage;
    @Unique private boolean combatant$attachmentInvalidation;
    @Unique private String combatant$vendor = "";
    @Unique private String combatant$renderer = "";

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
