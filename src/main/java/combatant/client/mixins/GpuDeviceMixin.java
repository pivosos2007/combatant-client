/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.util.function.Supplier;
import combatant.client.mixininterface.IGpuDevice;
import combatant.client.mixininterface.IMsaaDevice;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.scissor.GlobalScissorState;
import combatant.client.render.engine.profiler.ProfilerPhase;

@Mixin(GpuDevice.class)
public abstract class GpuDeviceMixin implements IGpuDevice, IMsaaDevice {
    @Override
    public void combatant$pushScissor(int x, int y, int width, int height) {
        GlobalScissorState.push(x, y, width, height);
    }

    @Override
    public void combatant$popScissor() {
        GlobalScissorState.pop();
    }

    @Override
    public GpuTexture combatant$createMsaaTexture(String label, int usage, GpuFormat format, int width, int height, int samples) {
        return CombatantRenderSystem.rhi().msaa().createTexture(label, usage, format, width, height, samples);
    }

    @Redirect(
            method = "createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDeviceBackend;createTexture(Ljava/util/function/Supplier;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;")
    )
    private GpuTexture combatant$profileCreateTexture(GpuDeviceBackend backend,
                                                       Supplier<String> label,
                                                       int usage,
                                                       GpuFormat format,
                                                       int width,
                                                       int height,
                                                       int depthOrLayers,
                                                       int mipLevels) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_resource:create_texture")) {
            return backend.createTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
        }
    }

    @Redirect(
            method = "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDeviceBackend;createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;")
    )
    private GpuTexture combatant$profileCreateTextureNamed(GpuDeviceBackend backend,
                                                            String label,
                                                            int usage,
                                                            GpuFormat format,
                                                            int width,
                                                            int height,
                                                            int depthOrLayers,
                                                            int mipLevels) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_resource:create_texture")) {
            return backend.createTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
        }
    }

    @Redirect(
            method = "createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDeviceBackend;createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/blaze3d/buffers/GpuBuffer;")
    )
    private GpuBuffer combatant$profileCreateBuffer(GpuDeviceBackend backend,
                                                     Supplier<String> label,
                                                     int usage,
                                                     long size) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_resource:create_buffer")) {
            return backend.createBuffer(label, usage, size);
        }
    }

    @Redirect(
            method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDeviceBackend;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;")
    )
    private GpuBuffer combatant$profileCreateBufferWithData(GpuDeviceBackend backend,
                                                             Supplier<String> label,
                                                             int usage,
                                                             ByteBuffer data) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_resource:create_buffer_with_data")) {
            return backend.createBuffer(label, usage, data);
        }
    }

    @Redirect(
            method = "precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDeviceBackend;precompilePipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/shaders/ShaderSource;)Lcom/mojang/blaze3d/pipeline/CompiledRenderPipeline;")
    )
    private CompiledRenderPipeline combatant$profilePipelineCompile(GpuDeviceBackend backend,
                                                                     RenderPipeline pipeline,
                                                                     ShaderSource shaderSource) {
        try (ProfilerPhase.Scope ignored = ProfilerPhase.scope("gpu_resource:compile_pipeline")) {
            return backend.precompilePipeline(pipeline, shaderSource);
        }
    }

}
