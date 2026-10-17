/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.*;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Constant-cost separable mask blur for glow/outline effects. Raster remains the capability/runtime fallback. */
public final class SeparableMaskBlurComputeBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "mask_blur");
    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("texelRadius", Std430Type.VEC4)
            .member("direction", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiStorageBuffer horizontalParams;
    private RhiStorageBuffer verticalParams;
    private RhiStorageImage ping;
    private RhiStorageImage output;
    private int width = -1;
    private int height = -1;
    private boolean disabledForSession;

    public boolean supported(CombatantRhi rhi) {
        return !disabledForSession && PostProcessExecutionPolicy.useCompute(rhi);
    }

    /**
     * Performs the same horizontal+vertical fixed-kernel mask blur as Shader ESP. The second pass
     * rejects pixels covered by {@code originalMask}, matching the raster shader's discard path.
     */
    public @Nullable GpuTextureView blur(CombatantRhi rhi,
                                         GpuTextureView source,
                                         GpuTextureView originalMask,
                                         GpuSampler sampler,
                                         int width,
                                         int height,
                                         float radius) {
        if (!supported(rhi) || source == null || originalMask == null || sampler == null) return null;
        if (width <= 0 || height <= 0) return null;

        try {
            ensureOwner(rhi);
            ensureImages(width, height);

            // Source/mask can have just been written by graphics/MSAA resolve.
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of()));

            dispatch(source, originalMask, sampler, ping, params(false), width, height, radius, 1.0f, 0.0f, false);
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(ping)));

            dispatch(ping.view(), originalMask, sampler, output, params(true), width, height, radius, 0.0f, 1.0f, true);
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(output)));
            PostProcessExecutionPolicy.logComputeActive("shader-esp-blur", "Shader ESP blur");
            return output.view();
        } catch (Throwable t) {
            if (PostProcessExecutionPolicy.isTransientBackendResourceMismatch(t)) {
                PostProcessExecutionPolicy.warnTransientResourceFallback("shader-esp-blur", "Shader ESP blur", t);
                return null;
            }
            disabledForSession = true;
            PostProcessExecutionPolicy.warnRuntimeFallback("shader-esp-blur", "Shader ESP blur", t);
            closeOwned();
            return null;
        }
    }

    private void dispatch(GpuTextureView source,
                          GpuTextureView mask,
                          GpuSampler sampler,
                          RhiStorageImage destination,
                          RhiStorageBuffer paramsBuffer,
                          int width,
                          int height,
                          float radius,
                          float dx,
                          float dy,
                          boolean rejectMask) {
        float safeRadius = Math.max(0.0f, Math.min(radius, 63.0f));
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "texelRadius", 1.0f / Math.max(1, width), 1.0f / Math.max(1, height),
                        safeRadius, 0.0f)
                .putVec4(0, "direction", dx, dy, rejectMask ? 1.0f : 0.0f, 0.0f);
        paramsBuffer.upload(writer.buffer(), 0L);

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                rejectMask ? "Combatant Mask Blur Vertical" : "Combatant Mask Blur Horizontal",
                pipeline(), groups(width), groups(height), 1,
                List.of(new StorageBinding(3, paramsBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, source, sampler),
                        new SampledTextureBinding(1, mask, sampler)
                ),
                List.of(new StorageImageBinding(2, destination, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensureImages(int width, int height) {
        if (ping != null && output != null && this.width == width && this.height == height) return;
        closeImages();
        this.width = width;
        this.height = height;
        ping = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-mask-blur-compute-ping", width, height, GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE, true, false));
        try {
            output = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-mask-blur-compute-output", width, height, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, false));
        } catch (Throwable t) {
            closeImages();
            throw t;
        }
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-mask-blur-compute", SHADER, LAYOUT));
        }
        return pipeline;
    }

    private RhiStorageBuffer params(boolean vertical) {
        if (vertical) {
            if (verticalParams == null) {
                verticalParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                        "combatant-mask-blur-vertical-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
            }
            return verticalParams;
        }
        if (horizontalParams == null) {
            horizontalParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-mask-blur-horizontal-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return horizontalParams;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        disabledForSession = false;
    }

    public void release(CombatantRhi rhi) {
        if (owner == null || rhi == null || owner == rhi) {
            closeOwned();
            owner = null;
            disabledForSession = false;
        }
    }

    private void closeImages() {
        if (ping != null) {
            try { ping.close(); } catch (Throwable ignored) { }
            ping = null;
        }
        if (output != null) {
            try { output.close(); } catch (Throwable ignored) { }
            output = null;
        }
        width = -1;
        height = -1;
    }

    private void closeOwned() {
        closeImages();
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (horizontalParams != null) {
            try { horizontalParams.close(); } catch (Throwable ignored) { }
            horizontalParams = null;
        }
        if (verticalParams != null) {
            try { verticalParams.close(); } catch (Throwable ignored) { }
            verticalParams = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
        disabledForSession = false;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }
}
