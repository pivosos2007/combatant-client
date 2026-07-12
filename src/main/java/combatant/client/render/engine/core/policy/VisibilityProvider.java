/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.core.policy;

import net.minecraft.world.phys.AABB;

public interface VisibilityProvider {
    VisibilityProvider ALWAYS_VISIBLE = new VisibilityProvider() {
        @Override
        public boolean isBoxVisible(AABB box) {
            return true;
        }

        @Override
        public boolean isBoxVisible(AABB box, VisibilityQuery query) {
            return true;
        }

        @Override
        public String debugName() {
            return "always_visible";
        }
    };

    boolean isBoxVisible(AABB box);

    default boolean isBoxVisible(AABB box, VisibilityQuery query) {
        return isBoxVisible(box);
    }

    default String debugName() {
        return getClass().getSimpleName();
    }
}
