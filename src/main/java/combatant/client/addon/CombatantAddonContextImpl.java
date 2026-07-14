/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import combatant.client.api.v0.addon.CombatantAddonContext;
import combatant.client.api.v0.clickgui.CombatantClickGuiSection;
import combatant.client.api.v0.client.CombatantClientApi;
import combatant.client.api.v0.module.CombatantModuleExtension;
import combatant.client.api.v0.render.CombatantPostProcessCallback;
import combatant.client.api.v0.render.CombatantRenderCallback;
import combatant.client.api.v0.render.CombatantRenderStage;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandManager;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.module.Module;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;

final class CombatantAddonContextImpl implements CombatantAddonContext {
    private final AddonRegistration registration;

    CombatantAddonContextImpl(AddonRegistration registration) {
        this.registration = registration;
    }

    @Override
    public String addonId() {
        return registration.descriptor.id();
    }

    @Override
    public CombatantClientApi client() {
        return CombatantClientApi.get();
    }

    @Override
    public ModContainer modContainer() {
        return registration.entrypoint.getProvider();
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path addonConfigDir() {
        return configDir().resolve("combatant").resolve("addons").resolve(addonId());
    }

    @Override
    public void registerModule(Module module) {
        if (module == null) return;
        module.postInit();
        registration.modules.add(module.name());
        AddonManager.noteModuleOwner(addonId(), module.name());
    }

    @Override
    public void registerDraggableHudElement(DraggableHudElement element) {
        if (element == null) return;
        DraggableHudElementRegistry.register(element);
        registration.draggableHudElements.add(element.getId());
        AddonManager.noteDraggableHudOwner(addonId(), element.getId());
    }

    @Override
    public void registerStaticHudElement(AbstractHudElement element) {
        if (element == null) return;
        StaticHudElementRegistry.register(element);
        registration.staticHudElements.add(element.getId());
        AddonManager.noteStaticHudOwner(addonId(), element.getId());
    }

    @Override
    public void registerCommand(ClientCommand command) {
        if (command == null) return;
        CommandManager.register(new AddonClientCommand(addonId(), command));
        registration.commands.add(command.metadata().id());
    }

    @Override
    public void registerClickGuiSection(String sectionId, String label, CombatantClickGuiSection section) {
        if (ClickGuiSectionManager.register(addonId(), sectionId, label, section)) {
            registration.clickGuiSections.add(sectionId);
        } else {
            registration.issue(AddonIssue.Severity.WARNING, "ClickGui section registration failed", sectionId);
        }
    }

    @Override
    public void registerModuleExtension(String moduleId, CombatantModuleExtension extension) {
        if (ModuleExtensionManager.register(addonId(), moduleId, extension)) {
            registration.moduleExtensions.add(moduleId);
        } else {
            registration.issue(AddonIssue.Severity.WARNING, "Module extension target is unavailable", moduleId);
        }
    }

    @Override
    public void registerIrisShaderPatchManifest(String manifestResourcePath) {
        if (ShaderPatchEngine.registerManifestResource(manifestResourcePath)) {
            registration.irisPatchManifests.add(manifestResourcePath);
        } else {
            registration.issue(AddonIssue.Severity.WARNING, "Iris patch manifest registration failed", manifestResourcePath);
        }
    }

    @Override
    public void registerRenderCallback(String callbackId, CombatantRenderStage stage, CombatantRenderCallback callback) {
        if (AddonRenderPipelineManager.register(addonId(), callbackId, stage, callback)) {
            registration.renderCallbacks.add(stage.name().toLowerCase(java.util.Locale.ROOT) + ":" + callbackId);
        } else {
            registration.issue(AddonIssue.Severity.WARNING, "Render callback registration failed", callbackId);
        }
    }

    @Override
    public void registerPostProcessPass(String passId,
                                        PostProcessPass.Phase phase,
                                        int priority,
                                        CombatantPostProcessCallback callback) {
        if (AddonRenderPipelineManager.registerPostProcess(addonId(), passId, phase, priority, callback)) {
            registration.postProcessPasses.add(phase.name().toLowerCase(java.util.Locale.ROOT) + ":" + passId);
        } else {
            registration.issue(AddonIssue.Severity.WARNING, "Post-process registration failed", passId);
        }
    }

    @Override
    public RenderPipeline registerRenderPipeline(RenderPipeline pipeline) {
        if (pipeline == null) return null;
        RenderPipeline registered = CombatantRenderPipelines.registerAddonPipeline(pipeline);
        registration.renderCallbacks.add("pipeline:" + pipeline.getLocation());
        return registered;
    }

    private record AddonClientCommand(String addonId, ClientCommand delegate) implements ClientCommand {
        @Override
        public combatant.client.features.command.CommandMetadata metadata() {
            return delegate.metadata();
        }

        @Deprecated(forRemoval = true)
        @Override
        @SuppressWarnings("removal")
        public String name() {
            return delegate.name();
        }

        @Deprecated(forRemoval = true)
        @Override
        @SuppressWarnings("removal")
        public java.util.List<String> aliases() {
            return delegate.aliases();
        }

        @Deprecated(forRemoval = true)
        @Override
        @SuppressWarnings("removal")
        public String usage() {
            return delegate.usage();
        }

        @Deprecated(forRemoval = true)
        @Override
        @SuppressWarnings("removal")
        public String description() {
            return delegate.description();
        }

        @Override
        public boolean isAvailable() {
            return AddonManager.isActive(addonId) && delegate.isAvailable();
        }

        @Override
        public java.util.List<String> suggest(combatant.client.features.command.CommandContext ctx, int argIndex, String token) {
            return delegate.suggest(ctx, argIndex, token);
        }

        @Override
        public boolean execute(combatant.client.features.command.CommandContext ctx) {
            return isAvailable() && delegate.execute(ctx);
        }
    }
}
