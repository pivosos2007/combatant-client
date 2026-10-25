package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Native non-tessellated triangle pipeline. Supports SSBO vertex fetch and indirect indexed draws. */
public record MeshPipelineDescriptor(String label, Identifier vertexShader, Identifier fragmentShader,
                                     VertexLayoutSpec vertexLayout, ShaderResourceLayout resources,
                                     AdvancedBlendMode blendMode, AdvancedDepthMode depthMode,
                                     AdvancedCullMode cullMode, List<GpuFormat> colorFormats,
                                     @Nullable GpuFormat depthFormat, int samples) implements NativeGraphicsDescriptor {
    public MeshPipelineDescriptor {
        if (vertexShader == null || fragmentShader == null || vertexLayout == null || vertexLayout.nativeFormat() == null)
            throw new IllegalArgumentException("Native mesh pipeline requires vertex/fragment and vertex layout");
        if (colorFormats == null || colorFormats.isEmpty() || colorFormats.stream().anyMatch(java.util.Objects::isNull))
            throw new IllegalArgumentException("Native mesh pipeline requires color formats");
        if (samples != 1 && samples != 2 && samples != 4 && samples != 8 && samples != 16)
            throw new IllegalArgumentException("Unsupported MSAA sample count");
        if (depthMode != null && depthMode != AdvancedDepthMode.DISABLED && depthFormat == null)
            throw new IllegalArgumentException("Depth format is required for depth testing");
        label = label == null || label.isBlank() ? vertexShader.toString() : label;
        colorFormats = List.copyOf(colorFormats);
        resources = resources == null ? ShaderResourceLayout.EMPTY : resources;
        blendMode = blendMode == null ? AdvancedBlendMode.PREMULTIPLIED_ALPHA : blendMode;
        depthMode = depthMode == null ? AdvancedDepthMode.DISABLED : depthMode;
        cullMode = cullMode == null ? AdvancedCullMode.NONE : cullMode;
    }
    @Override public Identifier tessControlShader() { return null; }
    @Override public Identifier tessEvaluationShader() { return null; }
    @Override public Identifier geometryShader() { return null; }
    @Override public int controlPoints() { return 0; }
}
