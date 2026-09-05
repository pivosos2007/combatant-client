/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record PatchDrawCommand(String label,
                               RhiPatchPipeline pipeline,
                               GpuTextureView colorAttachment,
                               @Nullable GpuTextureView depthAttachment,
                               GpuMeshHandle mesh,
                               List<StorageBinding> storageBindings,
                               List<SampledTextureBinding> sampledTextures,
                               List<StorageImageBinding> storageImages) {
    public PatchDrawCommand {
        label = label == null || label.isBlank() ? "combatant-patches" : label;
        if (pipeline == null || colorAttachment == null || mesh == null) {
            throw new IllegalArgumentException("Patch draw requires pipeline, color attachment and mesh");
        }
        storageBindings = storageBindings == null || storageBindings.isEmpty()
                ? List.of()
                : List.copyOf(storageBindings);
        sampledTextures = sampledTextures == null || sampledTextures.isEmpty()
                ? List.of()
                : List.copyOf(sampledTextures);
        storageImages = storageImages == null || storageImages.isEmpty()
                ? List.of()
                : List.copyOf(storageImages);
    }

    /** Compatibility overload for SSBO-only patch draws. */
    public PatchDrawCommand(String label, RhiPatchPipeline pipeline, GpuTextureView colorAttachment,
                            @Nullable GpuTextureView depthAttachment, GpuMeshHandle mesh,
                            List<StorageBinding> storageBindings) {
        this(label, pipeline, colorAttachment, depthAttachment, mesh, storageBindings, List.of(), List.of());
    }
}
