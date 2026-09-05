/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.util.EnumSet;
import net.minecraft.resources.Identifier;

/**
 * Native compute/tessellation surface. It is separate from Blaze3D RenderPipeline because
 * Minecraft 26.2 exposes only vertex and fragment stages there.
 */
public interface AdvancedShaderBackend extends AutoCloseable {
    AdvancedShaderBackend UNSUPPORTED = new AdvancedShaderBackend() {
        @Override
        public EnumSet<RhiShaderStage> stages() {
            return EnumSet.of(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT);
        }

        @Override
        public RhiStorageBuffer createStorageBuffer(StorageBufferDescriptor descriptor) {
            throw new UnsupportedOperationException("Native SSBO backend is unavailable");
        }

        @Override
        public RhiStorageImage createStorageImage(StorageImageDescriptor descriptor) {
            throw new UnsupportedOperationException("Native storage-image backend is unavailable");
        }

        @Override
        public RhiComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
            throw new UnsupportedOperationException("Native compute backend is unavailable");
        }

        @Override
        public RhiPatchPipeline createPatchPipeline(PatchPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException("Native tessellation backend is unavailable");
        }

        @Override
        public void dispatch(ComputeDispatchCommand command) {
            throw new UnsupportedOperationException("Native compute backend is unavailable");
        }

        @Override
        public void barrier(RhiResourceBarrier barrier) {
            throw new UnsupportedOperationException("Native shader synchronization is unavailable");
        }

        @Override
        public void drawPatches(PatchDrawCommand command) {
            throw new UnsupportedOperationException("Native tessellation backend is unavailable");
        }

        @Override
        public void close() {
        }
    };

    EnumSet<RhiShaderStage> stages();

    default boolean supports(RhiShaderStage stage) {
        return stage != null && stages().contains(stage);
    }

    RhiStorageBuffer createStorageBuffer(StorageBufferDescriptor descriptor);

    RhiStorageImage createStorageImage(StorageImageDescriptor descriptor);

    RhiComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor);

    /** Convenience overload for simple SSBO-free kernels. */
    default RhiComputePipeline createComputePipeline(String label, Identifier shader) {
        return createComputePipeline(new ComputePipelineDescriptor(label, shader, ShaderResourceLayout.EMPTY));
    }

    RhiPatchPipeline createPatchPipeline(PatchPipelineDescriptor descriptor);

    void dispatch(ComputeDispatchCommand command);

    void barrier(RhiResourceBarrier barrier);

    void drawPatches(PatchDrawCommand command);

    @Override
    void close();
}
