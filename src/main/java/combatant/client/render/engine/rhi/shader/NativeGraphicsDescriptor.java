package combatant.client.render.engine.rhi.shader;

import com.mojang.renderpearl.api.GpuFormat;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import net.minecraft.resources.Identifier;
import java.util.List;

/** Native graphics pipeline metadata shared by optional tessellation and ordinary triangle draws. */
public interface NativeGraphicsDescriptor {
    String label();
    Identifier vertexShader();
    Identifier tessControlShader();
    Identifier tessEvaluationShader();
    Identifier geometryShader();
    Identifier fragmentShader();
    VertexLayoutSpec vertexLayout();
    int controlPoints();
    ShaderResourceLayout resources();
    AdvancedBlendMode blendMode();
    AdvancedDepthMode depthMode();
    AdvancedCullMode cullMode();
    List<GpuFormat> colorFormats();
    GpuFormat depthFormat();
    int samples();
}
