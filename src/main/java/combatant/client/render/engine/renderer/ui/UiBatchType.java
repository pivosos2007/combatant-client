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
    COLORED(CombatantRenderPipelines.UI_COLORED_FAST, true, false),
    TEXTURED(CombatantRenderPipelines.UI_TEXTURED_FAST, true, true),
    SVG_MSDF(CombatantRenderPipelines.UI_SVG_MSDF_FAST, true, true),
    LINES(CombatantRenderPipelines.UI_COLORED_LINES_FAST, true, false),
    CIRCLE(CombatantRenderPipelines.UI_CIRCLE_BATCH, true, false),
    ARC(CombatantRenderPipelines.UI_ARC_BATCH, true, false),
    ORBIZ_RING(CombatantRenderPipelines.UI_ORBIZ_RING_BATCH, true, false),
    ROUNDED(CombatantRenderPipelines.UI_ROUNDED_BATCH, true, false),
    ROUNDED_FILL_SMOKE(CombatantRenderPipelines.UI_ROUNDED_FILL_SMOKE_BATCH, true, false),
    ROUNDED_STROKE(CombatantRenderPipelines.UI_ROUNDED_STROKE_BATCH, true, false),
    ROUNDED_STROKE_ANGULAR(CombatantRenderPipelines.UI_ROUNDED_STROKE_ANGULAR_BATCH, true, false),
    CHAMFERED(CombatantRenderPipelines.UI_CHAMFERED_BATCH, true, false),
    CHAMFERED_STROKE(CombatantRenderPipelines.UI_CHAMFERED_STROKE_BATCH, true, false),
    SHAPE(CombatantRenderPipelines.UI_SHAPE_BATCH, true, false),
    SHAPE_WARPED(CombatantRenderPipelines.UI_SHAPE_WARPED_BATCH, true, false),
    ROUNDED_STROKE_CORNERS(CombatantRenderPipelines.UI_ROUNDED_STROKE_CORNERS_BATCH, true, false),
    ROUNDED_CORNERS(CombatantRenderPipelines.UI_ROUNDED_CORNERS_BATCH, true, false),
    ROUNDED_GLOW(CombatantRenderPipelines.UI_ROUNDED_GLOW_BATCH, true, false),
    ROUNDED_SHADOW(CombatantRenderPipelines.UI_ROUNDED_SHADOW_BATCH, true, false),
    ROUNDED_SOFT_SHADOW(CombatantRenderPipelines.UI_ROUNDED_SOFT_SHADOW_BATCH, true, false),
    RADIAL_GLOW(CombatantRenderPipelines.UI_RADIAL_GLOW_BATCH, true, false),
    ROUNDED_TEXTURED(CombatantRenderPipelines.UI_ROUNDED_TEXTURED_BATCH, true, true),
    ROUNDED_TEXTURED_MASK(CombatantRenderPipelines.UI_ROUNDED_TEXTURED_MASK_BATCH, true, true),
    BLUR(CombatantRenderPipelines.UI_BLUR_BATCH, true, true),
    GLASS_BLUR(CombatantRenderPipelines.UI_BLUR_BATCH_CORNERS, true, true),
    BLUR_COMPLEX(CombatantRenderPipelines.UI_BLUR_BATCH_CORNERS, true, true),
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
