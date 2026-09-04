/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.clip;

import combatant.client.render.engine.renderer.ui.draw.UiRect;
import combatant.client.render.engine.renderer.ui.draw.UiShape;

import java.util.List;

/**
 * Immutable renderer-visible shape-clip state.
 *
 * <p>The primitive list describes the ordered intersection from outermost to innermost. Bounds are
 * culling/resource metadata only and never imply active GPU scissor state.</p>
 */
public record UiClipSnapshot(long id,
                             UiClipStrategy strategy,
                             UiRect logicalBounds,
                             List<UiShape> primitives,
                             int stencilReference,
                             int msaaSamples) {
    public static final UiClipSnapshot NONE = new UiClipSnapshot(
            0L, UiClipStrategy.ANALYTIC, new UiRect(0f, 0f, 0f, 0f), List.of(), 0, 1
    );

    public UiClipSnapshot {
        strategy = strategy != null ? strategy : UiClipStrategy.ANALYTIC;
        logicalBounds = logicalBounds != null ? logicalBounds : new UiRect(0f, 0f, 0f, 0f);
        primitives = primitives == null ? List.of() : List.copyOf(primitives);
        stencilReference = Math.max(0, stencilReference);
        msaaSamples = Math.max(1, msaaSamples);
    }

    public boolean active() {
        return id != 0L && stencilReference > 0 && !primitives.isEmpty();
    }

    public boolean usesAnalyticPipeline() {
        return active() && strategy == UiClipStrategy.ANALYTIC;
    }

    public boolean usesMsaaStencil() {
        return active() && strategy == UiClipStrategy.MSAA_STENCIL;
    }
}
