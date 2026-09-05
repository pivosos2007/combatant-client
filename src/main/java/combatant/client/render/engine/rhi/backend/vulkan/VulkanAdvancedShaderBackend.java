/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.*;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;

/**
 * Vulkan-native advanced-resource foundation.
 *
 * <p>Storage buffers are real VMA allocations backed by Mojang's existing allocator. Compute and
 * patch submission stay explicitly unsupported until Combatant owns descriptor/pipeline/command
 * lowering; exposing fake dispatch here would be worse than keeping the fallback.</p>
 */
final class VulkanAdvancedShaderBackend implements AdvancedShaderBackend {
    private final RhiStats stats;

    VulkanAdvancedShaderBackend(RhiStats stats) {
        this.stats = stats;
    }

    @Override
    public EnumSet<RhiShaderStage> stages() {
        return EnumSet.of(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT);
    }

    @Override
    public RhiStorageBuffer createStorageBuffer(StorageBufferDescriptor descriptor) {
        IVulkanBackendInfo backend = VulkanBackendAccess.current();
        if (backend == null || backend.combatant$vma() == 0L) {
            throw new UnsupportedOperationException("Native Vulkan storage allocator is unavailable");
        }
        return new VulkanStorageBuffer(backend, descriptor, stats);
    }

    @Override
    public RhiComputePipeline createComputePipeline(String label, Identifier shader) {
        throw new UnsupportedOperationException(
                "Vulkan compute pipeline lowering is not implemented yet; storage allocation is available");
    }

    @Override
    public RhiPatchPipeline createPatchPipeline(String label,
                                                Identifier vertexShader,
                                                Identifier tessControlShader,
                                                Identifier tessEvaluationShader,
                                                Identifier geometryShader,
                                                Identifier fragmentShader,
                                                int controlPoints) {
        throw new UnsupportedOperationException("Vulkan patch pipeline lowering is not implemented yet");
    }

    @Override
    public void dispatch(ComputeDispatchCommand command) {
        throw new UnsupportedOperationException("Vulkan compute dispatch is not implemented yet");
    }

    @Override
    public void barrier(RhiResourceBarrier barrier) {
        throw new UnsupportedOperationException("Vulkan advanced-shader barriers are not implemented yet");
    }

    @Override
    public void drawPatches(PatchDrawCommand command) {
        throw new UnsupportedOperationException("Vulkan patch draws are not implemented yet");
    }

    @Override
    public void close() {
    }
}
