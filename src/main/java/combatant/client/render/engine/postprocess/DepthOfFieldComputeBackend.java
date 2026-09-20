/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;

import java.util.List;

/** Native compute DoF gather. The legacy fullscreen shader remains the capability/runtime fallback. */
public final class DepthOfFieldComputeBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("combatant", "depth_of_field");
    private static final Identifier FOCUS_SHADER = Identifier.fromNamespaceAndPath("combatant", "depth_of_field_focus");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("projection", Std430Type.MAT4)
            .member("screen", Std430Type.VEC4)
            .member("focus", Std430Type.VEC4)
            .member("params", Std430Type.VEC4)
            .member("state", Std430Type.VEC4)
            .member("depthA", Std430Type.VEC4)
            .member("depthB", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout FOCUS_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline pipeline;
    private RhiComputePipeline focusPipeline;
    private RhiStorageBuffer params;
    private RhiStorageImage focusImage;

    public boolean supported(CombatantRhi rhi, RhiStorageImage destination) {
        return destination != null && PostProcessExecutionPolicy.useCompute(rhi);
    }

    public void render(CombatantRhi rhi,
                       RhiStorageImage destination,
                       GpuTextureView source,
                       GpuTextureView mainDepth,
                       GpuTextureView translucentDepth,
                       GpuTextureView itemEntityDepth,
                       GpuTextureView particlesDepth,
                       GpuTextureView weatherDepth,
                       GpuTextureView cloudsDepth,
                       GpuSampler sampler,
                       Matrix4fc projection,
                       int width,
                       int height,
                       float focusMode,
                       float focusDistance,
                       float farStart,
                       float farTransition,
                       float strength,
                       float maxRadius,
                       int taps,
                       float edgeProtection,
                       boolean debugCoc,
                       boolean mainAvailable,
                       boolean translucentAvailable,
                       boolean itemEntityAvailable,
                       boolean particlesAvailable,
                       boolean weatherAvailable,
                       boolean cloudsAvailable) {
        if (!supported(rhi, destination)) throw new IllegalStateException("Compute DoF is unavailable");
        if (source == null || mainDepth == null || translucentDepth == null
                || itemEntityDepth == null || particlesDepth == null || weatherDepth == null || cloudsDepth == null
                || sampler == null || projection == null) {
            throw new IllegalArgumentException("Compute DoF bindings are incomplete");
        }

        ensureOwner(rhi);
        boolean focusTextureReady = mainAvailable || translucentAvailable || itemEntityAvailable
                || particlesAvailable || weatherAvailable || cloudsAvailable;
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putMat4(0, "projection", projection)
                .putVec4(0, "screen",
                        width > 0 ? 1.0f / width : 0.0f,
                        height > 0 ? 1.0f / height : 0.0f,
                        width, height)
                .putVec4(0, "focus", focusMode, focusDistance, farStart, farTransition)
                .putVec4(0, "params", strength, maxRadius, taps, edgeProtection)
                .putVec4(0, "state", debugCoc ? 1.0f : 0.0f, focusTextureReady ? 1.0f : 0.0f, 0.0f, 0.0f)
                .putVec4(0, "depthA",
                        mainAvailable ? 1.0f : 0.0f,
                        translucentAvailable ? 1.0f : 0.0f,
                        itemEntityAvailable ? 1.0f : 0.0f,
                        particlesAvailable ? 1.0f : 0.0f)
                .putVec4(0, "depthB",
                        weatherAvailable ? 1.0f : 0.0f,
                        cloudsAvailable ? 1.0f : 0.0f,
                        0.0f, 0.0f);
        RhiStorageBuffer data = params();
        data.upload(writer.buffer(), 0L);

        // Graph source/depth/focus views can come from raster or transfer work. Synchronize
        // those writes before native compute starts; sampled views are not necessarily
        // Combatant-owned storage images, so this is intentionally a global dependency.
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL,
                RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.READ,
                List.of(),
                List.of()
        ));

        GpuTextureView focusTexture = source;
        if (focusTextureReady) {
            RhiStorageImage focus = focusImage();
            owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant DepthOfField Focus Compute",
                    focusPipeline(),
                    1, 1, 1,
                    List.of(new StorageBinding(9, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(
                            new SampledTextureBinding(0, mainDepth, sampler),
                            new SampledTextureBinding(1, translucentDepth, sampler),
                            new SampledTextureBinding(2, itemEntityDepth, sampler),
                            new SampledTextureBinding(3, particlesDepth, sampler),
                            new SampledTextureBinding(4, weatherDepth, sampler),
                            new SampledTextureBinding(5, cloudsDepth, sampler)
                    ),
                    List.of(new StorageImageBinding(6, focus, StorageAccess.WRITE_ONLY))
            ));
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE,
                    RhiResourceBarrier.Access.READ,
                    List.of(),
                    List.of(focus)
            ));
            focusTexture = focus.view();
        }

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant DepthOfField Compute",
                pipeline(),
                groups(destination.descriptor().width()),
                groups(destination.descriptor().height()),
                1,
                List.of(new StorageBinding(9, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, source, sampler),
                        new SampledTextureBinding(1, focusTexture, sampler),
                        new SampledTextureBinding(2, mainDepth, sampler),
                        new SampledTextureBinding(3, translucentDepth, sampler),
                        new SampledTextureBinding(4, itemEntityDepth, sampler),
                        new SampledTextureBinding(5, particlesDepth, sampler),
                        new SampledTextureBinding(6, weatherDepth, sampler),
                        new SampledTextureBinding(7, cloudsDepth, sampler)
                ),
                List.of(new StorageImageBinding(8, destination, StorageAccess.WRITE_ONLY))
        ));
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE,
                RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.ALL,
                RhiResourceBarrier.Access.READ,
                List.of(),
                List.of(destination)
        ));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline pipeline() {
        if (pipeline == null) {
            pipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-depth-of-field-compute", SHADER, LAYOUT));
        }
        return pipeline;
    }

    private RhiComputePipeline focusPipeline() {
        if (focusPipeline == null) {
            focusPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-depth-of-field-focus-compute", FOCUS_SHADER, FOCUS_LAYOUT));
        }
        return focusPipeline;
    }

    private RhiStorageImage focusImage() {
        if (focusImage == null) {
            focusImage = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-depth-of-field-focus-compute", 1, 1, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, false));
        }
        return focusImage;
    }

    private RhiStorageBuffer params() {
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-depth-of-field-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return params;
    }

    private void closeOwned() {
        if (pipeline != null) {
            try { pipeline.close(); } catch (Throwable ignored) { }
            pipeline = null;
        }
        if (focusPipeline != null) {
            try { focusPipeline.close(); } catch (Throwable ignored) { }
            focusPipeline = null;
        }
        if (focusImage != null) {
            try { focusImage.close(); } catch (Throwable ignored) { }
            focusImage = null;
        }
        if (params != null) {
            try { params.close(); } catch (Throwable ignored) { }
            params = null;
        }
    }

    public void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }
}

