/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;

/** Production TAA history/reprojection backend. It is independent from the legacy deferred graph. */
public final class TemporalAntiAliasingBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final float CONTRACT_VERSION = 3.0f;
    private static final Identifier RESOLVE_SHADER = id("temporal/taa_resolve");
    private static final Identifier PRESENT_SHADER = id("temporal/taa_present");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("cameraDelta", Std430Type.VEC4)
            .member("extentAndPolicy", Std430Type.VEC4)
            .member("depthPolicy", Std430Type.VEC4)
            .member("temporalPolicy", Std430Type.VEC4)
            .member("presentationPolicy", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout PRESENT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline resolvePipeline;
    private RhiComputePipeline presentPipeline;
    private RhiStorageBuffer paramsBuffer;
    private RhiStorageImage historyColorA;
    private RhiStorageImage historyColorB;
    private RhiStorageImage historyDepthA;
    private RhiStorageImage historyDepthB;
    private RhiStorageImage presentationIntermediate;
    private boolean historyAIsRead = true;
    private boolean historyReady;

    public boolean supported(CombatantRhi rhi, RhiStorageImage destination) {
        return destination != null && PostProcessExecutionPolicy.useCompute(rhi);
    }

    public void invalidateHistory() {
        historyReady = false;
    }

    public void render(CombatantRhi rhi,
                       RhiStorageImage destination,
                       GpuTextureView currentColor,
                       GpuTextureView currentDepth,
                       Matrix4fc currentView,
                       Matrix4fc currentProjection,
                       Matrix4fc previousView,
                       Matrix4fc previousProjection,
                       Vec3 cameraDelta,
                       boolean externalHistoryValid,
                       boolean fxaaEnabled,
                       boolean sharpeningEnabled,
                       float sharpeningIntensity) {
        if (!supported(rhi, destination)) throw new IllegalStateException("TAA compute path is unavailable");
        if (currentColor == null || currentDepth == null || currentView == null || currentProjection == null
                || previousView == null || previousProjection == null || cameraDelta == null) {
            throw new IllegalArgumentException("TAA bindings are incomplete");
        }

        ensureOwner(rhi);
        int width = destination.descriptor().width();
        int height = destination.descriptor().height();
        boolean resized = ensureHistoryImages(width, height);
        if (resized) historyReady = false;

        boolean useHistory = historyReady && externalHistoryValid;
        boolean zeroToOne = rhi.capabilities().zeroToOneDepth();
        Matrix4f inverseProjection = new Matrix4f(currentProjection).invert();
        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putMat4(0, "currentInverseProjection", inverseProjection)
                .putMat4(0, "currentInverseView", new Matrix4f(currentView).invert())
                .putMat4(0, "previousView", previousView)
                .putMat4(0, "previousProjection", previousProjection)
                .putVec4(0, "cameraDelta", (float) cameraDelta.x, (float) cameraDelta.y, (float) cameraDelta.z, 0.0f)
                .putVec4(0, "extentAndPolicy", width, height, useHistory ? 1.0f : 0.0f, CONTRACT_VERSION)
                .putVec4(0, "depthPolicy", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        0.015f, 0.0f)
                .putVec4(0, "temporalPolicy", 0.35f, 0.10f, 0.25f, 0.0f)
                .putVec4(0, "presentationPolicy", fxaaEnabled ? 1.0f : 0.0f,
                        sharpeningEnabled ? 1.0f : 0.0f,
                        Math.max(0.0f, Math.min(1.0f, sharpeningIntensity)), 0.0f);
        paramsBuffer().upload(writer.buffer(), 0L);

        RhiStorageImage historyColorRead = historyAIsRead ? historyColorA : historyColorB;
        RhiStorageImage historyColorWrite = historyAIsRead ? historyColorB : historyColorA;
        RhiStorageImage historyDepthRead = historyAIsRead ? historyDepthA : historyDepthB;
        RhiStorageImage historyDepthWrite = historyAIsRead ? historyDepthB : historyDepthA;

        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of()));

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant temporal anti-aliasing",
                resolvePipeline(), groups(width), groups(height), 1,
                List.of(new StorageBinding(7, paramsBuffer(), 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, currentColor, linear),
                        new SampledTextureBinding(1, currentDepth, nearest),
                        new SampledTextureBinding(2, historyColorRead.view(), linear),
                        new SampledTextureBinding(3, historyDepthRead.view(), nearest)
                ),
                List.of(
                        new StorageImageBinding(4, destination, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(5, historyColorWrite, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(6, historyDepthWrite, StorageAccess.WRITE_ONLY)
                )
        ));

        boolean sharpenActive = sharpeningEnabled && sharpeningIntensity > 0.0f;
        boolean presentationPass = fxaaEnabled || sharpenActive;
        if (presentationPass) {
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ_WRITE,
                    List.of(), List.of(destination, historyColorWrite)));

            if (fxaaEnabled && sharpenActive) {
                ensurePresentationIntermediate(width, height);

                writer.putVec4(0, "presentationPolicy", 1.0f, 1.0f,
                        Math.max(0.0f, Math.min(1.0f, sharpeningIntensity)), 1.0f);
                paramsBuffer().upload(writer.buffer(), 0L);
                dispatchPresentation("Combatant TAA FXAA", historyColorWrite.view(), currentColor,
                        presentationIntermediate, width, height, writer.byteSize(), linear, nearest);

                owner.advancedShaders().barrier(new RhiResourceBarrier(
                        RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                        RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                        List.of(), List.of(presentationIntermediate)));

                writer.putVec4(0, "presentationPolicy", 1.0f, 1.0f,
                        Math.max(0.0f, Math.min(1.0f, sharpeningIntensity)), 2.0f);
                paramsBuffer().upload(writer.buffer(), 0L);
                dispatchPresentation("Combatant TAA CAS", presentationIntermediate.view(), currentColor,
                        destination, width, height, writer.byteSize(), linear, nearest);
            } else {
                float mode = fxaaEnabled ? 1.0f : 2.0f;
                writer.putVec4(0, "presentationPolicy", fxaaEnabled ? 1.0f : 0.0f,
                        sharpenActive ? 1.0f : 0.0f,
                        Math.max(0.0f, Math.min(1.0f, sharpeningIntensity)), mode);
                paramsBuffer().upload(writer.buffer(), 0L);
                dispatchPresentation(fxaaEnabled ? "Combatant TAA FXAA" : "Combatant TAA CAS",
                        historyColorWrite.view(), currentColor, destination,
                        width, height, writer.byteSize(), linear, nearest);
            }
        }
        owner.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                List.of(), List.of(destination, historyColorWrite, historyDepthWrite)));

        historyAIsRead = !historyAIsRead;
        historyReady = true;
    }

    private void dispatchPresentation(String label,
                                      GpuTextureView resolvedColor,
                                      GpuTextureView currentColor,
                                      RhiStorageImage output,
                                      int width,
                                      int height,
                                      int paramsBytes,
                                      GpuSampler linear,
                                      GpuSampler nearest) {
        owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                label,
                presentPipeline(), groups(width), groups(height), 1,
                List.of(new StorageBinding(7, paramsBuffer(), 0L, paramsBytes, StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, resolvedColor, linear),
                        new SampledTextureBinding(1, currentColor, nearest)
                ),
                List.of(new StorageImageBinding(2, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void ensurePresentationIntermediate(int width, int height) {
        if (presentationIntermediate != null
                && presentationIntermediate.descriptor().width() == width
                && presentationIntermediate.descriptor().height() == height) {
            return;
        }
        presentationIntermediate = close(presentationIntermediate);
        presentationIntermediate = image("combatant-taa-presentation-intermediate",
                width, height, GpuFormat.RGBA8_UNORM);
    }

    private boolean ensureHistoryImages(int width, int height) {
        if (historyColorA != null && historyColorA.descriptor().width() == width && historyColorA.descriptor().height() == height) {
            return false;
        }
        closeHistoryImages();
        historyColorA = image("combatant-taa-history-color-a", width, height, GpuFormat.RGBA16_FLOAT);
        historyColorB = image("combatant-taa-history-color-b", width, height, GpuFormat.RGBA16_FLOAT);
        historyDepthA = image("combatant-taa-history-depth-a", width, height, GpuFormat.R32_FLOAT);
        historyDepthB = image("combatant-taa-history-depth-b", width, height, GpuFormat.R32_FLOAT);
        historyAIsRead = true;
        return true;
    }

    private RhiStorageImage image(String label, int width, int height, GpuFormat format) {
        return owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                label, width, height, format, StorageAccess.READ_WRITE, true, false));
    }

    private RhiStorageBuffer paramsBuffer() {
        if (paramsBuffer == null) {
            paramsBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-taa-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return paramsBuffer;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) {
            resolvePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-temporal-aa", RESOLVE_SHADER, RESOLVE_LAYOUT));
        }
        return resolvePipeline;
    }

    private RhiComputePipeline presentPipeline() {
        if (presentPipeline == null) {
            presentPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-temporal-aa-present", PRESENT_SHADER, PRESENT_LAYOUT));
        }
        return presentPipeline;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    public void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void closeHistoryImages() {
        historyColorA = close(historyColorA);
        historyColorB = close(historyColorB);
        historyDepthA = close(historyDepthA);
        historyDepthB = close(historyDepthB);
        historyReady = false;
    }

    private void closeOwned() {
        resolvePipeline = close(resolvePipeline);
        presentPipeline = close(presentPipeline);
        paramsBuffer = close(paramsBuffer);
        presentationIntermediate = close(presentationIntermediate);
        closeHistoryImages();
    }

    private static RhiComputePipeline close(RhiComputePipeline value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static RhiStorageBuffer close(RhiStorageBuffer value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static RhiStorageImage close(RhiStorageImage value) { if (value != null) try { value.close(); } catch (Throwable ignored) { } return null; }
    private static int groups(int value) { return Math.max(1, (Math.max(1, value) + LOCAL_SIZE - 1) / LOCAL_SIZE); }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("combatant", path); }

    @Override public void close() { closeOwned(); owner = null; }
}
