/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import net.minecraft.world.phys.AABB;
import combatant.client.render.engine.core.policy.VisibilityProvider;
import combatant.client.render.engine.core.policy.VisibilityQuery;

/**
 * World-overlay visibility provider backed by Sodium's section visibility graph.
 * <p>
 * It deliberately does not own Sodium runtime access. Runtime access stays in SodiumRenderBridge,
 * so renderers/modules depend only on VisibilityProvider.
 */
public final class SodiumSectionVisibilityProvider implements VisibilityProvider {
    private final SodiumRenderBridge bridge;

    public SodiumSectionVisibilityProvider(SodiumRenderBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean isBoxVisible(AABB box) {
        return bridge.isSectionBoxVisible(box, VisibilityQuery.worldOverlay(box));
    }

    @Override
    public boolean isBoxVisible(AABB box, VisibilityQuery query) {
        return bridge.isSectionBoxVisible(box, query != null ? query : VisibilityQuery.worldOverlay(box));
    }

    @Override
    public String debugName() {
        return "sodium_section_visibility";
    }
}
