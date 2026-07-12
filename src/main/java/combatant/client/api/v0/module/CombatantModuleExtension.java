/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.api.v0.module;

import combatant.client.features.module.Module;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface CombatantModuleExtension {
    default void onRegister(ModuleExtensionContext context) {
    }

    default boolean beforeEnable(Module module) {
        return true;
    }

    default void afterEnable(Module module) {
    }

    default boolean beforeDisable(Module module) {
        return true;
    }

    default void afterDisable(Module module) {
    }

    default boolean beforeTick(Module module) {
        return true;
    }

    default void afterTick(Module module) {
    }

    default boolean beforeFrame(Module module, float frameDeltaTicks) {
        return true;
    }

    default void afterFrame(Module module, float frameDeltaTicks) {
    }

    default boolean beforeHudRender(Module module,
                                    Renderer2D renderer,
                                    TextRenderer textRenderer,
                                    GuiGraphicsExtractor ctx,
                                    float tickDelta) {
        return true;
    }

    default void afterHudRender(Module module,
                                Renderer2D renderer,
                                TextRenderer textRenderer,
                                GuiGraphicsExtractor ctx,
                                float tickDelta) {
    }

    default boolean beforeWorldRender(Module module,
                                      Renderer3D renderer,
                                      Renderer3D depthRenderer,
                                      float tickDelta) {
        return true;
    }

    default void afterWorldRender(Module module,
                                  Renderer3D renderer,
                                  Renderer3D depthRenderer,
                                  float tickDelta) {
    }
}
