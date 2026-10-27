package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import java.util.List;

/** One DrawIndexedIndirect command (five uint32 fields, 20 bytes) sourced from a GPU-writable SSBO. */
public record MeshIndirectDrawCommand(String label, RhiMeshPipeline pipeline,
                                      GpuTextureView colorAttachment, GpuMeshHandle mesh,
                                      RhiStorageBuffer indirectCommands, long indirectOffset,
                                      List<StorageBinding> storageBindings, List<SampledTextureBinding> sampledTextures) {
    public MeshIndirectDrawCommand {
        if (pipeline == null || colorAttachment == null || mesh == null || indirectCommands == null)
            throw new IllegalArgumentException("Indirect draw requires pipeline, render target, mesh and command buffer");
        if (!indirectCommands.descriptor().indirectSource() || indirectOffset < 0L || (indirectOffset & 3L) != 0L
                || indirectOffset > indirectCommands.descriptor().byteSize() - 20L)
            throw new IllegalArgumentException("Invalid indirect buffer or command offset");
        label = label == null || label.isBlank() ? "combatant-mesh-indirect" : label;
        storageBindings = storageBindings == null ? List.of() : List.copyOf(storageBindings);
        sampledTextures = sampledTextures == null ? List.of() : List.copyOf(sampledTextures);
    }
}
