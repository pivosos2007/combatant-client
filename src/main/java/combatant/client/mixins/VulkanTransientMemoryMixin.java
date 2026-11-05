/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.backend.vulkan.VulkanTransientMemory;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanUploadAlignment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.util.List;

@Mixin(VulkanTransientMemory.class)
public abstract class VulkanTransientMemoryMixin {
    @ModifyVariable(method = "allocateStaging(JJIJJ)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice$MappedView;",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private long combatant$alignStagingUpload(long alignment) {
        return VulkanUploadAlignment.staging(alignment);
    }

    @Redirect(method = "multiUpload",
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanTransientMemory;allocateGpuMapped(JJI)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice$MappedView;", ordinal = 1))
    private GpuBufferSlice.MappedView combatant$preserveStagingAllocation(VulkanTransientMemory memory,
                                                                       long size, long alignment, int usage,
                                                                       List<ByteBuffer> data, long requestedAlignment,
                                                                       int requestedUsage, boolean staging) {
        // The overflow branch in 26.3 uses GPU-mapped memory even for multiUploadStaging.
        return staging ? memory.allocateStaging(size, alignment, usage)
                : memory.allocateGpuMapped(size, alignment, usage);
    }
}
