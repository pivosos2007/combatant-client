/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.temporal.TemporalVelocityContract;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Production temporal motion-vector path extracted from the deferred camera-post chain.
 * It keeps the same velocity, tile-max, neighbor-max and depth-aware blur compute stages,
 * but owns only the resources needed by the standalone MotionBlur module.
 */
public final class TemporalMotionBlurBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final int TILE_SIZE = 16;

    private static final Identifier VELOCITY_SHADER = id("temporal/velocity_camera");
    private static final Identifier TILE_SHADER = id("temporal/motion_tile_max");
    private static final Identifier NEIGHBOR_SHADER = id("temporal/motion_neighbor_max");
    private static final Identifier BLUR_SHADER = id("temporal/motion_blur");

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("depthNdcTransform", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout MOTION_LAYOUT = Std430StructLayout.builder()
            .member("extent", Std430Type.VEC4)
            .member("policy", Std430Type.VEC4)
            .member("inverseProjection", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout VELOCITY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout TILE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout NEIGHBOR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout BLUR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline velocityPipeline;
    private RhiComputePipeline tilePipeline;
    private RhiComputePipeline neighborPipeline;
    private RhiComputePipeline blurPipeline;
    private RhiStorageBuffer cameraBuffer;
    private RhiStorageBuffer motionBuffer;
    private RhiStorageImage velocity;
    private RhiStorageImage validity;
    private RhiStorageImage tileMax;
    private RhiStorageImage neighborMax;

    public boolean supported(CombatantRhi rhi, RhiStorageImage destination) {
        return destination != null && PostProcessExecutionPolicy.useCompute(rhi);
    }

    public void render(CombatantRhi rhi,
                       RhiStorageImage destination,
                       GpuTextureView color,
                       GpuTextureView depth,
                       Matrix4fc currentView,
                       Matrix4fc currentProjection,
                       Matrix4fc previousView,
                       Matrix4fc previousProjection,
                       Vec3 cameraDelta,
                       boolean historyValid,
                       float strength,
                       float maxPixels,
                       float minMotionPixels,
                       float shutterScale,
                       int samples,
                       float depthEdgeProtection) {
        if (!supported(rhi, destination)) throw new IllegalStateException("Temporal motion blur compute path is unavailable");
        if (color == null || depth == null || currentView == null || currentProjection == null
                || previousView == null || previousProjection == null || cameraDelta == null) {
            throw new IllegalArgumentException("Temporal motion blur bindings are incomplete");
        }

        ensureOwner(rhi);
        int width = destination.descriptor().width();
        int height = destination.descriptor().height();
        ensureImages(width, height);

        boolean zeroToOne = rhi.capabilities().zeroToOneDepth();
        Matrix4f inverseProjection = new Matrix4f(currentProjection).invert();
        Std430Writer cameraWriter = new Std430Writer(CAMERA_LAYOUT, 1)
                .putMat4(0, "currentInverseProjection", inverseProjection)
                .putMat4(0, "currentInverseView", new Matrix4f(currentView).invert())
                .putMat4(0, "previousView", previousView)
                .putMat4(0, "previousProjection", previousProjection)
                .putVec4(0, "cameraDelta", (float) cameraDelta.x, (float) cameraDelta.y, (float) cameraDelta.z, 0.0f)
                .putVec4(0, "depthNdcTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        historyValid ? 1.0f : 0.0f, TemporalVelocityContract.VERSION);
        cameraBuffer().upload(cameraWriter.buffer(), 0L);

        Std430Writer motionWriter = new Std430Writer(MOTION_LAYOUT, 1)
                .putVec4(0, "extent", width, height, TILE_SIZE, Math.max(4, Math.min(64, samples)))
                .putVec4(0, "policy", strength, maxPixels, minMotionPixels, shutterScale)
                .putMat4(0, "inverseProjection", inverseProjection)
                .putVec4(0, "depthTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        depthEdgeProtection, 0.0f);
        motionBuffer().upload(motionWriter.buffer(), 0L);

        RhiResourceBarrier globalRead = new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of());
        owner.advancedShaders().barrier(globalRead);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal camera velocity", velocityPipeline(), groups(width), groups(height), 1,
                List.of(new StorageBinding(2, cameraBuffer(), 0L, cameraWriter.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, depth, nearest), new SampledTextureBinding(4, depth, nearest)),
                List.of(new StorageImageBinding(1, velocity, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(3, validity, StorageAccess.WRITE_ONLY))));
        barrier(List.of(velocity, validity));

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant motion tile max", tilePipeline(), groups(tileMax.descriptor().width()), groups(tileMax.descriptor().height()), 1,
                List.of(new StorageBinding(3, motionBuffer(), 0L, motionWriter.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, velocity.view(), nearest),
                        new SampledTextureBinding(1, validity.view(), nearest)),
                List.of(new StorageImageBinding(2, tileMax, StorageAccess.WRITE_ONLY))));
        barrier(List.of(tileMax));

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant motion neighbor max", neighborPipeline(), groups(neighborMax.descriptor().width()), groups(neighborMax.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, tileMax, StorageAccess.READ_ONLY),
                        new StorageImageBinding(1, neighborMax, StorageAccess.WRITE_ONLY))));
        barrier(List.of(neighborMax));

        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal motion blur", blurPipeline(), groups(width), groups(height), 1,
                List.of(new StorageBinding(6, motionBuffer(), 0L, motionWriter.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, color, linear),
                        new SampledTextureBinding(1, depth, nearest),
                        new SampledTextureBinding(2, velocity.view(), nearest),
                        new SampledTextureBinding(3, validity.view(), nearest),
                        new SampledTextureBinding(4, neighborMax.view(), nearest)),
                List.of(new StorageImageBinding(5, destination, StorageAccess.WRITE_ONLY))));
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                List.of(), List.of(destination)));
    }

    private void barrier(List<RhiStorageImage> images) {
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), images));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureImages(int width, int height) {
        int tilesX = Math.max(1, (width + TILE_SIZE - 1) / TILE_SIZE);
        int tilesY = Math.max(1, (height + TILE_SIZE - 1) / TILE_SIZE);
        if (velocity != null && velocity.descriptor().width() == width && velocity.descriptor().height() == height
                && tileMax != null && tileMax.descriptor().width() == tilesX && tileMax.descriptor().height() == tilesY) return;
        closeImages();
        velocity = image("combatant-temporal-velocity", width, height, GpuFormat.RG16_FLOAT);
        validity = image("combatant-temporal-validity", width, height, GpuFormat.R8_UNORM);
        tileMax = image("combatant-motion-tile-max", tilesX, tilesY, GpuFormat.RG16_FLOAT);
        neighborMax = image("combatant-motion-neighbor-max", tilesX, tilesY, GpuFormat.RG16_FLOAT);
    }

    private RhiStorageImage image(String label, int width, int height, GpuFormat format) {
        return owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                label, width, height, format, StorageAccess.READ_WRITE, true, false));
    }

    private RhiStorageBuffer cameraBuffer() {
        if (cameraBuffer == null) cameraBuffer = buffer("combatant-temporal-camera", CAMERA_LAYOUT);
        return cameraBuffer;
    }

    private RhiStorageBuffer motionBuffer() {
        if (motionBuffer == null) motionBuffer = buffer("combatant-motion-blur-params", MOTION_LAYOUT);
        return motionBuffer;
    }

    private RhiStorageBuffer buffer(String label, Std430StructLayout layout) {
        return owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                label, layout, 1, StorageAccess.READ_ONLY, false));
    }

    private RhiComputePipeline velocityPipeline() { if (velocityPipeline == null) velocityPipeline = pipeline("combatant-temporal-camera-velocity", VELOCITY_SHADER, VELOCITY_LAYOUT); return velocityPipeline; }
    private RhiComputePipeline tilePipeline() { if (tilePipeline == null) tilePipeline = pipeline("combatant-motion-tile-max", TILE_SHADER, TILE_LAYOUT); return tilePipeline; }
    private RhiComputePipeline neighborPipeline() { if (neighborPipeline == null) neighborPipeline = pipeline("combatant-motion-neighbor-max", NEIGHBOR_SHADER, NEIGHBOR_LAYOUT); return neighborPipeline; }
    private RhiComputePipeline blurPipeline() { if (blurPipeline == null) blurPipeline = pipeline("combatant-temporal-motion-blur", BLUR_SHADER, BLUR_LAYOUT); return blurPipeline; }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout) {
        return owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(label, shader, layout));
    }

    public void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void closeImages() {
        velocity = close(velocity);
        validity = close(validity);
        tileMax = close(tileMax);
        neighborMax = close(neighborMax);
    }

    private void closeOwned() {
        velocityPipeline = close(velocityPipeline);
        tilePipeline = close(tilePipeline);
        neighborPipeline = close(neighborPipeline);
        blurPipeline = close(blurPipeline);
        cameraBuffer = close(cameraBuffer);
        motionBuffer = close(motionBuffer);
        closeImages();
    }

    private static RhiComputePipeline close(RhiComputePipeline value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static RhiStorageBuffer close(RhiStorageBuffer value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static RhiStorageImage close(RhiStorageImage value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static int groups(int value) { return Math.max(1, (Math.max(1, value) + LOCAL_SIZE - 1) / LOCAL_SIZE); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("combatant", path); }

    @Override public void close() { closeOwned(); owner = null; }
}
