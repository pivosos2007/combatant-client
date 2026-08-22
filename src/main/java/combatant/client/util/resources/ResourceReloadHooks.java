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
import combatant.client.render.engine.TextureStorage;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.prewarm.RenderPrewarmManager;
import combatant.client.render.engine.svg.SvgRegistry;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.visuals.CombatantVisuals;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.media.MediaSessionService;
import combatant.client.util.sound.SoundSystem;

public enum ResourceReloadHooks {
    ;

    public static void onReload(ResourceManager manager) {
        if (manager == null) return;

        try {
            // Refresh resource-backed systems
            Fonts.refresh();
            TextureStorage.preload();
            SvgRegistry.reload(manager);
            SoundSystem.get().reset();
            I18nDuplicateScanner.scan(manager, "resource reload");
            I18nPreflightManager.preflight("resource reload");

            // Clear cached font renderers that keep old textures
            NameTags tags = Modules.get(NameTags.class);
            if (tags != null) {
                tags.onResourceReload();
            }

            // Reset dynamic media textures (e.g. artwork) so they re-register.
            MediaSessionService.get().onResourceReload();
            CombatantVisuals.onResourceReload(manager);

            try {
                IrisRuntime.registerCombatantPipelines();
                CombatantRenderPipelines.precompile(manager);
                RenderPrewarmManager.prewarm("shader reload complete");
            } catch (Throwable t) {
                DebugLog.error("[Combatant] Shader precompile failed after resource reload", t);
            }
        } finally {
            RenderResourceReadiness.markReady("shader reload complete");
        }

    }
}
