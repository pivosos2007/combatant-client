/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record PatchDrawCommand(String label,
                               RhiPatchPipeline pipeline,
                               List<GpuTextureView> colorAttachments,
                               @Nullable GpuTextureView depthAttachment,
                               GpuMeshHandle mesh,
                               List<StorageBinding> storageBindings,
                               List<SampledTextureBinding> sampledTextures,
                               List<StorageImageBinding> storageImages,
                               List<SampledVolumeBinding> sampledVolumes) {
    public PatchDrawCommand {
        label = label == null || label.isBlank() ? "combatant-patches" : label;
        if (pipeline == null || colorAttachments == null || colorAttachments.isEmpty()
                || colorAttachments.stream().anyMatch(java.util.Objects::isNull) || mesh == null) {
            throw new IllegalArgumentException("Patch draw requires pipeline, color attachments and mesh");
        }
        colorAttachments = List.copyOf(colorAttachments);
        storageBindings = storageBindings == null || storageBindings.isEmpty() ? List.of() : List.copyOf(storageBindings);
        sampledTextures = sampledTextures == null || sampledTextures.isEmpty() ? List.of() : List.copyOf(sampledTextures);
        storageImages = storageImages == null || storageImages.isEmpty() ? List.of() : List.copyOf(storageImages);
        sampledVolumes = sampledVolumes == null || sampledVolumes.isEmpty() ? List.of() : List.copyOf(sampledVolumes);
    }

    public PatchDrawCommand(String label,
                            RhiPatchPipeline pipeline,
                            List<GpuTextureView> colorAttachments,
                            @Nullable GpuTextureView depthAttachment,
                            GpuMeshHandle mesh,
                            List<StorageBinding> storageBindings,
                            List<SampledTextureBinding> sampledTextures,
                            List<StorageImageBinding> storageImages) {
        this(label, pipeline, colorAttachments, depthAttachment, mesh,
                storageBindings, sampledTextures, storageImages, List.of());
    }

    public PatchDrawCommand(String label, RhiPatchPipeline pipeline, GpuTextureView colorAttachment,
                            @Nullable GpuTextureView depthAttachment, GpuMeshHandle mesh,
                            List<StorageBinding> storageBindings, List<SampledTextureBinding> sampledTextures,
                            List<StorageImageBinding> storageImages) {
        this(label, pipeline, List.of(colorAttachment), depthAttachment, mesh,
                storageBindings, sampledTextures, storageImages, List.of());
    }

    public PatchDrawCommand(String label, RhiPatchPipeline pipeline, GpuTextureView colorAttachment,
                            @Nullable GpuTextureView depthAttachment, GpuMeshHandle mesh,
                            List<StorageBinding> storageBindings) {
        this(label, pipeline, List.of(colorAttachment), depthAttachment, mesh,
                storageBindings, List.of(), List.of(), List.of());
    }

    public GpuTextureView colorAttachment() {
        if (colorAttachments.size() != 1) {
            throw new IllegalStateException("Patch draw has " + colorAttachments.size() + " color attachments");
        }
        return colorAttachments.get(0);
    }
}
