/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

import com.mojang.blaze3d.GpuFormat;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Native tessellation/patch pipeline contract, intentionally separate from Blaze3D RenderPipeline. */
public record PatchPipelineDescriptor(String label,
                                      Identifier vertexShader,
                                      Identifier tessControlShader,
                                      Identifier tessEvaluationShader,
                                      @Nullable Identifier geometryShader,
                                      Identifier fragmentShader,
                                      VertexLayoutSpec vertexLayout,
                                      int controlPoints,
                                      ShaderResourceLayout resources,
                                      AdvancedBlendMode blendMode,
                                      AdvancedDepthMode depthMode,
                                      AdvancedCullMode cullMode,
                                      GpuFormat colorFormat,
                                      @Nullable GpuFormat depthFormat,
                                      int samples) {
    public PatchPipelineDescriptor {
        if (vertexShader == null || tessControlShader == null || tessEvaluationShader == null || fragmentShader == null) {
            throw new IllegalArgumentException("patch pipeline requires vertex/tess-control/tess-evaluation/fragment stages");
        }
        if (vertexLayout == null || vertexLayout.nativeFormat() == null) {
            throw new IllegalArgumentException("patch pipeline requires a native vertex layout");
        }
        if (controlPoints < 1 || controlPoints > 32) {
            throw new IllegalArgumentException("controlPoints must be in [1, 32]");
        }
        if (colorFormat == null) throw new IllegalArgumentException("colorFormat");
        if (samples != 1 && samples != 2 && samples != 4 && samples != 8 && samples != 16) {
            throw new IllegalArgumentException("unsupported patch sample count: " + samples);
        }
        label = label == null || label.isBlank() ? vertexShader.toString() : label;
        resources = resources == null ? ShaderResourceLayout.EMPTY : resources;
        blendMode = blendMode == null ? AdvancedBlendMode.PREMULTIPLIED_ALPHA : blendMode;
        depthMode = depthMode == null ? AdvancedDepthMode.DISABLED : depthMode;
        cullMode = cullMode == null ? AdvancedCullMode.NONE : cullMode;
        if (depthMode != AdvancedDepthMode.DISABLED && depthFormat == null) {
            throw new IllegalArgumentException("depthFormat is required when depth is enabled");
        }
    }
}
