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
        public RhiStorageVolume createStorageVolume(StorageVolumeDescriptor descriptor) {
            throw new UnsupportedOperationException("Native storage-volume backend is unavailable");
        }

        @Override
        public RhiVolumeCapabilities queryStorageVolumeCapabilities(StorageVolumeDescriptor descriptor) {
            return RhiVolumeCapabilities.unsupported("Native 3D volume backend is unavailable");
        }

        @Override
        public void copyStorageVolume(VolumeCopyCommand command) {
            throw new UnsupportedOperationException("Native 3D volume copy is unavailable");
        }

        @Override
        public void clearStorageVolume(VolumeClearCommand command) {
            throw new UnsupportedOperationException("Native 3D volume clear is unavailable");
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

    RhiStorageVolume createStorageVolume(StorageVolumeDescriptor descriptor);

    /** Descriptor-specific 3D resource support, including optional copy/clear paths and fallback reasons. */
    RhiVolumeCapabilities queryStorageVolumeCapabilities(StorageVolumeDescriptor descriptor);

    /** Records an exact-format native 3D image copy in the active backend command stream. */
    void copyStorageVolume(VolumeCopyCommand command);

    /** Clears complete mip levels selected by the target view. */
    void clearStorageVolume(VolumeClearCommand command);

    RhiComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor);

    /** Convenience overload for simple SSBO-free kernels. */
    default RhiComputePipeline createComputePipeline(String label, Identifier shader) {
        return createComputePipeline(new ComputePipelineDescriptor(label, shader, ShaderResourceLayout.EMPTY));
    }

    RhiPatchPipeline createPatchPipeline(PatchPipelineDescriptor descriptor);

    /** Compile a native triangle pipeline (no tessellation requirement) with SSBO descriptor bindings. */
    default RhiMeshPipeline createMeshPipeline(MeshPipelineDescriptor descriptor) {
        throw new UnsupportedOperationException("Native SSBO graphics pipeline unavailable");
    }

    /** Draw using a GPU-generated VkDrawIndexedIndirectCommand / GL DrawElementsIndirectCommand. */
    default void drawMeshIndirect(MeshIndirectDrawCommand command) {
        throw new UnsupportedOperationException("GPU indirect graphics draws unavailable");
    }

    void dispatch(ComputeDispatchCommand command);

    void barrier(RhiResourceBarrier barrier);

    void drawPatches(PatchDrawCommand command);

    @Override
    void close();
}
