/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;

public enum UiBatchType {
    TEXTURED(CombatantRenderPipelines.UI_TEXTURED_FAST, true, true),
    SVG_MSDF(CombatantRenderPipelines.UI_SVG_MSDF_FAST, true, true),
    LINES(CombatantRenderPipelines.UI_COLORED_LINES_FAST, true, false),
    ORBIZ_RING(CombatantRenderPipelines.UI_ORBIZ_RING_BATCH, true, false),
    ROUNDED_FILL_SMOKE(CombatantRenderPipelines.UI_ROUNDED_FILL_SMOKE_BATCH, true, false),
    ROUNDED_STROKE_ANGULAR(CombatantRenderPipelines.UI_ROUNDED_STROKE_ANGULAR_BATCH, true, false),
    SHAPE(CombatantRenderPipelines.UI_SHAPE_BATCH, true, false),
    GLOW(CombatantRenderPipelines.UI_GLOW_BATCH, true, false),
    TEXTURED_SHAPE(CombatantRenderPipelines.UI_TEXTURED_SHAPE_BATCH, true, true),
    BLUR(CombatantRenderPipelines.UI_BLUR_BATCH, true, true),
    BLUR_CORNERS(CombatantRenderPipelines.UI_BLUR_BATCH_CORNERS, true, true),
    LIQUID_GLASS(CombatantRenderPipelines.UI_LIQUID_GLASS_BATCH, true, true);

    public final RenderPipeline pipeline;
    public final boolean needsUiBatch;
    public final boolean usesSampler;

    UiBatchType(RenderPipeline pipeline, boolean needsUiBatch, boolean usesSampler) {
        this.pipeline = pipeline;
        this.needsUiBatch = needsUiBatch;
        this.usesSampler = usesSampler;
    }
}
