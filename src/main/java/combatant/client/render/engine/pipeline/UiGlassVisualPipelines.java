/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.pipeline;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import combatant.client.render.engine.shader.CombatantShaderSources;
import combatant.client.render.engine.uniform.impl.UiGlassVisualUniforms;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Render-thread cache keyed only by shader and clip/underlay specialization, never by draw data. */
public final class UiGlassVisualPipelines {
    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();

    private UiGlassVisualPipelines() {}

    public static RenderPipeline resolve(Identifier shader, boolean analyticClip, boolean underlay) {
        return PIPELINES.computeIfAbsent(new Key(shader, analyticClip, underlay), UiGlassVisualPipelines::build);
    }

    private static RenderPipeline build(Key key) {
        Identifier fragment = key.shader();
        if (key.analyticClip()) fragment = CombatantShaderSources.analyticClipVariantId(fragment);
        if (key.underlay()) fragment = CombatantShaderSources.uiUnderlayVariantId(fragment);
        var builder = new ExtendedRenderPipelineBuilder(
                CombatantRenderPipelines.meshUniforms(), CombatantRenderPipelines.uiBatchUniforms(),
                CombatantRenderPipelines.uiBlendUniforms(), CombatantRenderPipelines.uiGlassFrameUniforms(),
                CombatantRenderPipelines.uiGlassStackUniforms())
                .withLocation(fragment.withPath(path -> "pipeline/ui_glass_visual/" + path))
                .withVertexFormat(CombatantVertexFormats.POS2_TEXTURE_LOCAL_COLOR_RECT_PARAMS9,
                        PrimitiveTopology.TRIANGLES)
                .withVertexShader(Identifier.fromNamespaceAndPath("combatant", "shaders/pos_tex_local_color_rect_params9.vert"))
                .withFragmentShader(fragment)
                .withUniform(UiGlassVisualUniforms.BLOCK_NAME, UniformType.UNIFORM_BUFFER)
                .withSampler("u_Texture")
                .withSampler("u_BlurTexture")
                .withSampler("u_StackTexture")
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false);
        if (key.analyticClip()) builder.withUniform("UIClip", UniformType.UNIFORM_BUFFER);
        if (key.underlay()) {
            builder.withUniform("UIBackdrop", UniformType.UNIFORM_BUFFER).withSampler("u_UiUnderlayTexture");
        }
        return CombatantRenderPipelines.registerAddonPipeline(builder.build());
    }

    private record Key(Identifier shader, boolean analyticClip, boolean underlay) {}
}
