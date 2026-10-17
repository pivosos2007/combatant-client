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

/** Compute-only image-space helpers for hand Chams. Geometry/material rendering remains raster. */
public final class HandChamsComputeBackend implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier OCCUPANCY_SHADER = Identifier.fromNamespaceAndPath("combatant", "hand_mask_occupancy");
    private static final Identifier OCCUPANCY_DILATE_SHADER = Identifier.fromNamespaceAndPath("combatant", "hand_mask_occupancy_dilate");
    private static final Identifier GHOST_HISTORY_SHADER = Identifier.fromNamespaceAndPath("combatant", "hand_ghosting_history");
    private static final Identifier GHOST_COMPOSITE_SHADER = Identifier.fromNamespaceAndPath("combatant", "hand_ghosting");

    private static final Std430StructLayout OCCUPANCY_PARAMS = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout OCCUPANCY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final Std430StructLayout GHOST_PARAMS = Std430StructLayout.builder()
            .member("screen", Std430Type.VEC4)
            .member("color", Std430Type.VEC4)
            .member("params", Std430Type.VEC4)
            .member("noise0", Std430Type.VEC4)
            .member("noise1", Std430Type.VEC4)
            .member("state", Std430Type.VEC4)
            .build();
    private static final ShaderResourceLayout GHOST_HISTORY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout GHOST_COMPOSITE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline occupancyPipeline;
    private RhiComputePipeline occupancyDilatePipeline;
    private RhiComputePipeline ghostHistoryPipeline;
    private RhiComputePipeline ghostCompositePipeline;
    private RhiStorageBuffer occupancyRawParams;
    private RhiStorageBuffer occupancyDilateParams;
    private RhiStorageBuffer ghostParams;

    private RhiStorageImage occupancyRaw;
    private RhiStorageImage occupancyDilated;
    private int occupancyW = -1;
    private int occupancyH = -1;

    private RhiStorageImage ghostRead;
    private RhiStorageImage ghostWrite;
    private int ghostW = -1;
    private int ghostH = -1;
    private boolean ghostHistoryValid;
    private boolean occupancyDisabledForSession;
    private boolean ghostDisabledForSession;

    public boolean supported(CombatantRhi rhi) {
        return !ghostDisabledForSession && PostProcessExecutionPolicy.useCompute(rhi);
    }

    public boolean occupancySupported(CombatantRhi rhi) {
        return !occupancyDisabledForSession && PostProcessExecutionPolicy.useCompute(rhi);
    }

    /** Builds the low-resolution conservative occupancy field used to cull expensive metallic probes. */
    public @Nullable GpuTextureView buildMetallicOccupancy(CombatantRhi rhi,
                                                            GpuTextureView mask,
                                                            GpuSampler sampler,
                                                            int framebufferW,
                                                            int framebufferH,
                                                            float edgeWidth,
                                                            int cellSize) {
        if (!occupancySupported(rhi) || mask == null || sampler == null || framebufferW <= 0 || framebufferH <= 0) return null;
        try {
            ensureOwner(rhi);
            int cell = Math.max(1, cellSize);
            int w = Math.max(1, (framebufferW + cell - 1) / cell);
            int h = Math.max(1, (framebufferH + cell - 1) / cell);
            ensureOccupancyImages(w, h);

            owner.advancedShaders().barrier(globalToCompute());
            Std430Writer first = new Std430Writer(OCCUPANCY_PARAMS, 1)
                    .putVec4(0, "params", framebufferW, framebufferH, cell, 0.0f);
            RhiStorageBuffer rawParams = occupancyParams(false);
            rawParams.upload(first.buffer(), 0L);
            owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant Hand Metallic Occupancy Compute",
                    occupancyPipeline(), groups(w), groups(h), 1,
                    List.of(new StorageBinding(2, rawParams, 0L, first.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(0, mask, sampler)),
                    List.of(new StorageImageBinding(1, occupancyRaw, StorageAccess.WRITE_ONLY))));
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(occupancyRaw)));

            Std430Writer second = new Std430Writer(OCCUPANCY_PARAMS, 1)
                    .putVec4(0, "params", edgeWidth, cell, w, h);
            RhiStorageBuffer dilateParams = occupancyParams(true);
            dilateParams.upload(second.buffer(), 0L);
            owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant Hand Metallic Occupancy Dilate Compute",
                    occupancyDilatePipeline(), groups(w), groups(h), 1,
                    List.of(new StorageBinding(2, dilateParams, 0L, second.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(0, occupancyRaw.view(), sampler)),
                    List.of(new StorageImageBinding(1, occupancyDilated, StorageAccess.WRITE_ONLY))));
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(occupancyDilated)));
            PostProcessExecutionPolicy.logComputeActive("chams-metallic-occupancy", "Chams metallic occupancy");
            return occupancyDilated.view();
        } catch (Throwable t) {
            if (PostProcessExecutionPolicy.isTransientBackendResourceMismatch(t)) {
                PostProcessExecutionPolicy.warnTransientResourceFallback(
                        "chams-metallic-occupancy", "Chams metallic occupancy", t);
                return null;
            }
            occupancyDisabledForSession = true;
            PostProcessExecutionPolicy.warnRuntimeFallback("chams-metallic-occupancy", "Chams metallic occupancy", t);
            closeOccupancyResources();
            return null;
        }
    }

    /** Compute history update + final composite. Returns false so caller can use the raster fallback. */
    public boolean renderGhosting(CombatantRhi rhi,
                                  RhiStorageImage destination,
                                  GpuTextureView source,
                                  GpuTextureView mask,
                                  GpuSampler sampler,
                                  int screenW,
                                  int screenH,
                                  float dt,
                                  float time,
                                  float colorR,
                                  float colorG,
                                  float colorB,
                                  float colorA,
                                  float decay,
                                  float strength,
                                  float blurPx,
                                  float currentReject,
                                  int quality,
                                  int octaves,
                                  float speed,
                                  float scale,
                                  float swirl,
                                  float contrast,
                                  float density,
                                  float historyScale,
                                  boolean forceReset) {
        if (!supported(rhi) || destination == null || source == null || mask == null || sampler == null) return false;
        if (screenW <= 0 || screenH <= 0) return false;
        try {
            ensureOwner(rhi);
            int w = Math.max(1, Math.round(screenW * Math.max(0.05f, historyScale)));
            int h = Math.max(1, Math.round(screenH * Math.max(0.05f, historyScale)));
            ensureGhostImages(w, h);

            boolean reset = forceReset || !ghostHistoryValid;
            Std430Writer writer = new Std430Writer(GHOST_PARAMS, 1)
                    .putVec4(0, "screen", screenW, screenH, dt, time)
                    .putVec4(0, "color", colorR, colorG, colorB, colorA)
                    .putVec4(0, "params", decay, strength, blurPx, currentReject)
                    .putVec4(0, "noise0", quality, octaves, speed, scale)
                    .putVec4(0, "noise1", swirl, contrast, density, historyScale)
                    .putVec4(0, "state", reset ? 1.0f : 0.0f, 0.0f, 0.0f, 0.0f);
            RhiStorageBuffer params = ghostParams();
            params.upload(writer.buffer(), 0L);

            owner.advancedShaders().barrier(globalToCompute());
            owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant Hand Ghost History Compute",
                    ghostHistoryPipeline(), groups(w), groups(h), 1,
                    List.of(new StorageBinding(3, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(
                            new SampledTextureBinding(0, ghostRead.view(), sampler),
                            new SampledTextureBinding(1, mask, sampler)
                    ),
                    List.of(new StorageImageBinding(2, ghostWrite, StorageAccess.WRITE_ONLY))));
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(ghostWrite)));

            owner.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant Hand Ghost Composite Compute",
                    ghostCompositePipeline(), groups(destination.descriptor().width()), groups(destination.descriptor().height()), 1,
                    List.of(new StorageBinding(3, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(
                            new SampledTextureBinding(0, source, sampler),
                            new SampledTextureBinding(1, ghostWrite.view(), sampler)
                    ),
                    List.of(new StorageImageBinding(2, destination, StorageAccess.WRITE_ONLY))));
            owner.advancedShaders().barrier(new RhiResourceBarrier(
                    RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                    RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                    List.of(), List.of(destination)));

            RhiStorageImage tmp = ghostRead;
            ghostRead = ghostWrite;
            ghostWrite = tmp;
            ghostHistoryValid = true;
            PostProcessExecutionPolicy.logComputeActive("chams-ghosting", "Chams ghosting");
            return true;
        } catch (Throwable t) {
            if (PostProcessExecutionPolicy.isTransientBackendResourceMismatch(t)) {
                PostProcessExecutionPolicy.warnTransientResourceFallback("chams-ghosting", "Chams ghosting", t);
                return false;
            }
            ghostDisabledForSession = true;
            PostProcessExecutionPolicy.warnRuntimeFallback("chams-ghosting", "Chams ghosting", t);
            closeGhostResources();
            return false;
        }
    }

    public void invalidateGhostHistory() {
        ghostHistoryValid = false;
    }

    private void ensureOccupancyImages(int w, int h) {
        if (occupancyRaw != null && occupancyDilated != null && occupancyW == w && occupancyH == h) return;
        closeOccupancy();
        occupancyW = w;
        occupancyH = h;
        occupancyRaw = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-hand-metallic-occupancy-compute-raw", w, h, GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE, true, false));
        try {
            occupancyDilated = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-hand-metallic-occupancy-compute-dilated", w, h, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, false));
        } catch (Throwable t) {
            closeOccupancy();
            throw t;
        }
    }

    private void ensureGhostImages(int w, int h) {
        if (ghostRead != null && ghostWrite != null && ghostW == w && ghostH == h) return;
        closeGhostImages();
        ghostW = w;
        ghostH = h;
        ghostRead = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-hand-ghost-compute-a", w, h, GpuFormat.RGBA8_UNORM,
                StorageAccess.READ_WRITE, true, false));
        try {
            ghostWrite = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    "combatant-hand-ghost-compute-b", w, h, GpuFormat.RGBA8_UNORM,
                    StorageAccess.READ_WRITE, true, false));
        } catch (Throwable t) {
            closeGhostImages();
            throw t;
        }
        ghostHistoryValid = false;
    }

    private RhiComputePipeline occupancyPipeline() {
        if (occupancyPipeline == null) occupancyPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-hand-occupancy-compute", OCCUPANCY_SHADER, OCCUPANCY_LAYOUT));
        return occupancyPipeline;
    }

    private RhiComputePipeline occupancyDilatePipeline() {
        if (occupancyDilatePipeline == null) occupancyDilatePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-hand-occupancy-dilate-compute", OCCUPANCY_DILATE_SHADER, OCCUPANCY_LAYOUT));
        return occupancyDilatePipeline;
    }

    private RhiComputePipeline ghostHistoryPipeline() {
        if (ghostHistoryPipeline == null) ghostHistoryPipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-hand-ghost-history-compute", GHOST_HISTORY_SHADER, GHOST_HISTORY_LAYOUT));
        return ghostHistoryPipeline;
    }

    private RhiComputePipeline ghostCompositePipeline() {
        if (ghostCompositePipeline == null) ghostCompositePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-hand-ghost-composite-compute", GHOST_COMPOSITE_SHADER, GHOST_COMPOSITE_LAYOUT));
        return ghostCompositePipeline;
    }

    private RhiStorageBuffer occupancyParams(boolean dilate) {
        if (dilate) {
            if (occupancyDilateParams == null) occupancyDilateParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-hand-occupancy-dilate-params", OCCUPANCY_PARAMS, 1, StorageAccess.READ_ONLY, false));
            return occupancyDilateParams;
        }
        if (occupancyRawParams == null) occupancyRawParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-hand-occupancy-raw-params", OCCUPANCY_PARAMS, 1, StorageAccess.READ_ONLY, false));
        return occupancyRawParams;
    }

    private RhiStorageBuffer ghostParams() {
        if (ghostParams == null) ghostParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-hand-ghost-params", GHOST_PARAMS, 1, StorageAccess.READ_ONLY, false));
        return ghostParams;
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        occupancyDisabledForSession = false;
        ghostDisabledForSession = false;
    }

    public void release(CombatantRhi rhi) {
        if (owner == null || rhi == null || owner == rhi) {
            closeOwned();
            owner = null;
            occupancyDisabledForSession = false;
            ghostDisabledForSession = false;
        }
    }

    private static RhiResourceBarrier globalToCompute() {
        return new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of());
    }

    private void closeOccupancy() {
        if (occupancyRaw != null) {
            try { occupancyRaw.close(); } catch (Throwable ignored) { }
            occupancyRaw = null;
        }
        if (occupancyDilated != null) {
            try { occupancyDilated.close(); } catch (Throwable ignored) { }
            occupancyDilated = null;
        }
        occupancyW = -1;
        occupancyH = -1;
    }

    private void closeGhostImages() {
        if (ghostRead != null) {
            try { ghostRead.close(); } catch (Throwable ignored) { }
            ghostRead = null;
        }
        if (ghostWrite != null) {
            try { ghostWrite.close(); } catch (Throwable ignored) { }
            ghostWrite = null;
        }
        ghostW = -1;
        ghostH = -1;
        ghostHistoryValid = false;
    }

    private void closeOccupancyResources() {
        closeOccupancy();
        if (occupancyPipeline != null) { try { occupancyPipeline.close(); } catch (Throwable ignored) { } occupancyPipeline = null; }
        if (occupancyDilatePipeline != null) { try { occupancyDilatePipeline.close(); } catch (Throwable ignored) { } occupancyDilatePipeline = null; }
        if (occupancyRawParams != null) { try { occupancyRawParams.close(); } catch (Throwable ignored) { } occupancyRawParams = null; }
        if (occupancyDilateParams != null) { try { occupancyDilateParams.close(); } catch (Throwable ignored) { } occupancyDilateParams = null; }
    }

    private void closeGhostResources() {
        closeGhostImages();
        if (ghostHistoryPipeline != null) { try { ghostHistoryPipeline.close(); } catch (Throwable ignored) { } ghostHistoryPipeline = null; }
        if (ghostCompositePipeline != null) { try { ghostCompositePipeline.close(); } catch (Throwable ignored) { } ghostCompositePipeline = null; }
        if (ghostParams != null) { try { ghostParams.close(); } catch (Throwable ignored) { } ghostParams = null; }
    }

    private void closeOwned() {
        closeOccupancyResources();
        closeGhostResources();
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
        occupancyDisabledForSession = false;
        ghostDisabledForSession = false;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }
}
