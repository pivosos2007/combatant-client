/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import java.util.List;

/** One DrawIndexedIndirect command (five uint32 fields, 20 bytes) sourced from a GPU-writable SSBO. */
public record MeshIndirectDrawCommand(String label, RhiMeshPipeline pipeline,
                                      GpuTextureView colorAttachment, GpuMeshHandle mesh,
                                      RhiStorageBuffer indirectCommands, long indirectOffset,
                                      List<StorageBinding> storageBindings, List<SampledTextureBinding> sampledTextures,
                                      int drawCount, int strideBytes) {
    public MeshIndirectDrawCommand {
        if (pipeline == null || colorAttachment == null || mesh == null || indirectCommands == null)
            throw new IllegalArgumentException("Indirect draw requires pipeline, render target, mesh and command buffer");
        if (!indirectCommands.descriptor().indirectSource() || indirectOffset < 0L || (indirectOffset & 3L) != 0L
                || drawCount < 1 || strideBytes < 20 || (strideBytes & 3) != 0
                || (long) (drawCount - 1) * strideBytes > Long.MAX_VALUE - indirectOffset
                || indirectOffset + (long) (drawCount - 1) * strideBytes
                    > indirectCommands.descriptor().byteSize() - 20L)
            throw new IllegalArgumentException("Invalid indexed-indirect command range/count/stride");
        label = label == null || label.isBlank() ? "combatant-mesh-indirect" : label;
        storageBindings = storageBindings == null ? List.of() : List.copyOf(storageBindings);
        sampledTextures = sampledTextures == null ? List.of() : List.copyOf(sampledTextures);
    }

    /** Existing one-command API remains source compatible. */
    public MeshIndirectDrawCommand(String label, RhiMeshPipeline pipeline,
                                   GpuTextureView colorAttachment, GpuMeshHandle mesh,
                                   RhiStorageBuffer indirectCommands, long indirectOffset,
                                   List<StorageBinding> storageBindings,
                                   List<SampledTextureBinding> sampledTextures) {
        this(label, pipeline, colorAttachment, mesh, indirectCommands, indirectOffset,
                storageBindings, sampledTextures, 1, 20);
    }
}
