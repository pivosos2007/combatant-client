/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine;

import combatant.client.render.engine.guard.RenderBoundaryAudit;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRegistry;
import combatant.client.render.engine.visuals.CombatantVisuals;
import combatant.client.render.iris.IrisRuntime;

public enum CombatantRenderEngineBootstrap {
    ;

    public static void init() {
        RenderBoundaryAudit.runOnce();
        // RHI backend selection must be lazy: onInitializeClient can run before RenderSystem has
        // created the final GpuDevice, which would permanently select the wrong backend on Vulkan.
        Renderer2D.init();
        IrisRuntime.registerCombatantPipelines();
        SvgRegistry.init();
        CombatantVisuals.init();
    }

}
