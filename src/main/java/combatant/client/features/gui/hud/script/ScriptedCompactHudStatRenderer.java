/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.script;

import combatant.client.util.resources.asset.UiScriptAsset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.engine.text.TextRenderer;

@UiScriptAsset("combatant:api/hud/draggable/compact_stat")
public final class ScriptedCompactHudStatRenderer {
    public static final ScriptedCompactHudStatRenderer INSTANCE = new ScriptedCompactHudStatRenderer();
private final Minecraft mc = Minecraft.getInstance();
    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(ScriptedCompactHudStatRenderer.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());

    private ScriptedCompactHudStatRenderer() {
    }

    public boolean render(CompactHudStatModel model,
                          Renderer2D renderer,
                          TextRenderer textRenderer,
                          GuiGraphicsExtractor ctx,
                          float tickDelta) {
        if (model == null || !model.visible()) return false;
        if (moduleHandle.isRuntimeBlocked()) return false;
        if (mc == null || mc.getResourceManager() == null) return false;

        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) {
            resetRuntime();
        }

        UiScriptModule module = ensureModule();
        if (module == null) return false;

        TextRenderer resolvedText = textRenderer != null ? textRenderer : TextRenderer.get();
        long treeSignature = model.structuralSignature();
        long layoutSignature = model.layoutSignature(treeSignature);
        UiRuntime runtime = scriptRuntime.updatePersistent(
                moduleHandle,
                module,
                model.getId(),
                treeSignature,
                layoutSignature,
                model.width(),
                model.height(),
                resolvedText,
                model.rootX(),
                model.rootY(),
                model.width(),
                model.height(),
                model::toTemplateProps,
                model::runtimePatches,
                model::runtimeBoundsPatches
        );
        if (runtime == null) return false;
        runtime.render(new UiRenderContext(renderer, resolvedText, ctx, tickDelta, UiProjectionMode.UNSCALED_LOGICAL));
        return true;
    }

    private UiScriptModule ensureModule() {
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return null;
        }
        moduleHandle.consumeChanged();
        return moduleHandle.module();
    }

    private void resetRuntime() {
        scriptRuntime.reset();
    }
}
