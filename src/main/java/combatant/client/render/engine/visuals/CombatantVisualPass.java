/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Base contract for the custom world visual graph.
 */
public interface CombatantVisualPass {
    Identifier getId();

    CombatantVisualPhase getPhase();

    default boolean isEnabled() {
        return true;
    }

    default void init() {
    }

    default void onResourceReload(ResourceManager manager) {
    }

    default void prepareFrame(CombatantVisualFrame frame) {
    }

    default boolean render(CombatantVisualFrame frame, GpuTextureView src, GpuTextureView dst) {
        return false;
    }
}
