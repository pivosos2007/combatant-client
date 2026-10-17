/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.pipeline.UiRenderPipelines;
import combatant.client.render.engine.pipeline.UiBatchRenderPipelines;

public enum UiBatchType {
    TEXTURED(UiRenderPipelines.UI_TEXTURED_FAST, UiRenderPipelines.UI_TEXTURED_FAST_ANALYTIC_CLIP, true, true),
    SVG_MSDF(UiRenderPipelines.UI_SVG_MSDF_FAST, UiRenderPipelines.UI_SVG_MSDF_FAST_ANALYTIC_CLIP, true, true),
    LINES(UiRenderPipelines.UI_COLORED_LINES_FAST, true, false),
    ORBIZ_RING(UiBatchRenderPipelines.UI_ORBIZ_RING_BATCH, true, false),
    ROUNDED_FILL_SMOKE(UiBatchRenderPipelines.UI_ROUNDED_FILL_SMOKE_BATCH, true, false),
    MODULE_CATEGORY_SURFACE(UiBatchRenderPipelines.UI_MODULE_CATEGORY_SURFACE_BATCH, true, false),
    WIDGET_SURFACE(UiBatchRenderPipelines.UI_WIDGET_SURFACE_BATCH, true, false),
    MAIN_MENU_HONEYCOMB(UiBatchRenderPipelines.UI_MAIN_MENU_HONEYCOMB_BATCH, true, true),
    ROUNDED_STROKE_ANGULAR(UiBatchRenderPipelines.UI_ROUNDED_STROKE_ANGULAR_BATCH, true, false),
    PATH(UiBatchRenderPipelines.UI_PATH_BATCH, UiBatchRenderPipelines.UI_PATH_BATCH_ANALYTIC_CLIP, true, false),
    SHAPE(UiBatchRenderPipelines.UI_SHAPE_BATCH, UiBatchRenderPipelines.UI_SHAPE_BATCH_ANALYTIC_CLIP, true, false),
    PRIMITIVE(UiBatchRenderPipelines.UI_PRIMITIVE_BATCH, true, false),
    MATERIAL_PRIMITIVE(UiBatchRenderPipelines.UI_MATERIAL_PRIMITIVE_BATCH, true, false),
    GLOW(UiBatchRenderPipelines.UI_GLOW_BATCH, true, false),
    TEXTURED_SHAPE(UiBatchRenderPipelines.UI_TEXTURED_SHAPE_BATCH, UiBatchRenderPipelines.UI_TEXTURED_SHAPE_BATCH_ANALYTIC_CLIP, true, true),
    BLUR(UiBatchRenderPipelines.UI_BLUR_BATCH, UiBatchRenderPipelines.UI_BLUR_BATCH_ANALYTIC_CLIP, true, true),
    BLUR_CORNERS(UiBatchRenderPipelines.UI_BLUR_BATCH_CORNERS, UiBatchRenderPipelines.UI_BLUR_BATCH_CORNERS_ANALYTIC_CLIP, true, true),
    LIQUID_GLASS(UiBatchRenderPipelines.UI_LIQUID_GLASS_BATCH, UiBatchRenderPipelines.UI_LIQUID_GLASS_BATCH_ANALYTIC_CLIP, true, true);

    public final RenderPipeline pipeline;
    public final RenderPipeline analyticClipPipeline;
    public final boolean needsUiBatch;
    public final boolean usesSampler;

    UiBatchType(RenderPipeline pipeline, boolean needsUiBatch, boolean usesSampler) {
        this(pipeline, null, needsUiBatch, usesSampler);
    }

    UiBatchType(RenderPipeline pipeline, RenderPipeline analyticClipPipeline, boolean needsUiBatch, boolean usesSampler) {
        this.pipeline = pipeline;
        this.analyticClipPipeline = analyticClipPipeline;
        this.needsUiBatch = needsUiBatch;
        this.usesSampler = usesSampler;
    }

    public RenderPipeline pipelineFor(combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot clip) {
        if (clip != null && clip.usesAnalyticPipeline() && analyticClipPipeline != null) {
            return analyticClipPipeline;
        }
        return pipeline;
    }

    public boolean supportsAnalyticClip() {
        return analyticClipPipeline != null;
    }

    /** Materials that sample both the captured clean scene and its shared Kawase blur. */
    public boolean usesPreparedGlass() {
        return this == LIQUID_GLASS || this == MAIN_MENU_HONEYCOMB;
    }
}
