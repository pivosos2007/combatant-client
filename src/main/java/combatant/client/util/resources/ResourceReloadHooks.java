/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources;

import combatant.client.features.module.Modules;
import combatant.client.features.gui.clickgui.settings.I18nDuplicateScanner;
import combatant.client.features.gui.clickgui.settings.I18nPreflightManager;
import net.minecraft.server.packs.resources.ResourceManager;
import combatant.client.features.module.modules.visuals.NameTags;
import combatant.client.util.resources.asset.AssetAutoLoader;
import combatant.client.render.engine.prewarm.RenderPrewarmManager;
import combatant.client.render.engine.visuals.CombatantVisuals;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.media.MediaSessionService;

public enum ResourceReloadHooks {
    ;

    public static void onReload(ResourceManager manager) {
        if (manager == null) return;

        boolean shaderResourcesPublished = false;
        try {
            // Static resource-backed systems are discovered through @AssetLoad.
            AssetAutoLoader.reload(manager);

            I18nDuplicateScanner.scan(manager, "resource reload");
            I18nPreflightManager.preflight("resource reload");

            // Per-instance/dynamic caches keep their explicit owners.
            NameTags tags = Modules.get(NameTags.class);
            if (tags != null) {
                tags.onResourceReload();
            }

            MediaSessionService.get().onResourceReload();
            CombatantVisuals.onResourceReload(manager);

            try {
                IrisRuntime.registerCombatantPipelines();
            } catch (RuntimeException t) {
                DebugLog.error("[Combatant] Iris pipeline registration failed after resource reload", t);
            }

            // Shader compilation is a discovered post-reload asset hook.
            AssetAutoLoader.postReload(manager);
            shaderResourcesPublished = true;

            try {
                RenderPrewarmManager.prewarm("shader reload complete");
            } catch (RuntimeException t) {
                DebugLog.error("[Combatant] Render prewarm failed after resource reload", t);
            }
        } catch (RuntimeException e) {
            DebugLog.error("Combatant resource reload failed before render resources were published", e);
            throw e;
        } finally {
            // A failed reload must not authorize custom UI/pipeline compilation.
            // Optional prewarm failures do not invalidate already published shaders.
            if (shaderResourcesPublished) RenderResourceReadiness.markReady("shader reload complete");
        }
    }
}
