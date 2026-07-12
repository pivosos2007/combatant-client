/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.msaa;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.core.CombatantRenderSystem;

@Deprecated
public enum MsaaResolveUtil {
    ;

    public static boolean resolve(RenderTarget src, RenderTarget dst, boolean color, boolean depth) {
        RenderSystem.assertOnRenderThread();
        return CombatantRenderSystem.rhi().msaa().resolve(src, dst, color, depth);
    }
}
