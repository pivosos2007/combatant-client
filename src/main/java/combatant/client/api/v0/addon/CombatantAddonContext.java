/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.addon;

import combatant.client.events.UsedImplicitly;
import combatant.client.features.command.ClientCommand;
import combatant.client.api.v0.clickgui.CombatantClickGuiSection;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.api.v0.module.CombatantModuleExtension;
import combatant.client.api.v0.render.CombatantPostProcessCallback;
import combatant.client.api.v0.render.CombatantRenderCallback;
import combatant.client.api.v0.render.CombatantRenderStage;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import combatant.client.render.engine.postprocess.PostProcessPass;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;

public interface CombatantAddonContext extends CombatantAddonRuntimeContext {
    @UsedImplicitly
    ModContainer modContainer();
    @UsedImplicitly
    Path gameDir();

    Path configDir();
    @UsedImplicitly
    Path addonConfigDir();
    /**
     * Registers an addon-owned second-level module category. The id is local to this addon and
     * primary module category; Combatant namespaces it internally, so ids may be reused safely.
     */
    @UsedImplicitly
    boolean registerModuleSubcategory(ModuleCategory category, String subcategoryId, String displayName);

    @UsedImplicitly
    void registerModule(Module module);

    /** Registers a module and explicitly assigns it to an addon-owned subcategory. */
    @UsedImplicitly
    void registerModule(Module module, String subcategoryId);
    @UsedImplicitly
    void registerDraggableHudElement(DraggableHudElement element);
    @UsedImplicitly
    void registerStaticHudElement(AbstractHudElement element);
    @UsedImplicitly
    void registerCommand(ClientCommand command);
    @UsedImplicitly
    void registerClickGuiSection(String sectionId, String label, CombatantClickGuiSection section);
    @UsedImplicitly
    void registerModuleExtension(String moduleId, CombatantModuleExtension extension);
    @UsedImplicitly
    void registerIrisShaderPatchManifest(String manifestResourcePath);
    @UsedImplicitly
    void registerRenderCallback(String callbackId, CombatantRenderStage stage, CombatantRenderCallback callback);
    @UsedImplicitly
    void registerPostProcessPass(String passId, PostProcessPass.Phase phase, int priority, CombatantPostProcessCallback callback);
    @UsedImplicitly
    RenderPipeline registerRenderPipeline(RenderPipeline pipeline);
}
