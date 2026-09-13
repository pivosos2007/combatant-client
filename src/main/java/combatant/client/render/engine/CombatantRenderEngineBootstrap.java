/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine;

import combatant.client.addon.ClickGuiSectionManager;
import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.render.engine.guard.RenderBoundaryAudit;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.visuals.CombatantVisuals;
import combatant.client.render.iris.IrisRuntime;
import net.minecraft.client.Minecraft;

public enum CombatantRenderEngineBootstrap {
    ;

    public static void init() {
        RenderBoundaryAudit.runOnce();
        // Overlap the expensive ClickGUI ClassGraph scan with the rest of client bootstrap.
        ClickGuiSectionManager.beginDiscoveryAsync();
        // RHI backend selection must be lazy: onInitializeClient can run before RenderSystem has
        // created the final GpuDevice, which would permanently select the wrong backend on Vulkan.
        Renderer2D.init();
        IrisRuntime.registerCombatantPipelines();
        Minecraft mc = Minecraft.getInstance();
        AssetAutoLoader.initialize(mc != null ? mc.getResourceManager() : null);
        CombatantVisuals.init();
    }

}
