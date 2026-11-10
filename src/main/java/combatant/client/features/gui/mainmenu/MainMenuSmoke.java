/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;

import combatant.client.config.subsystem.VisualConfig;
import combatant.client.render.engine.postprocess.PostProcessExecutionPolicy;
import combatant.client.features.theme.Theme;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.core.CombatantRenderSystem;

final class MainMenuSmoke {
    private final MainMenuSmokeComputeBackend compute = new MainMenuSmokeComputeBackend();
    private long lastFrame;
    private float previousWidth;
    private float previousHeight;
    private boolean previousLatticeMode;

    boolean computeSupported() {
        return PostProcessExecutionPolicy.useCompute(CombatantRenderSystem.rhi()) && compute.operational();
    }

    boolean canAttemptCompute() {
        return PostProcessExecutionPolicy.useCompute(CombatantRenderSystem.rhi()) && compute.canRetry();
    }

    void render(float width, float height, float opacity, MainMenuHexTrail hex, MainMenuBackdrop.GridLayout grid) {
        if (width <= 0f || height <= 0f || opacity <= 0.001f) return;
        long now = System.nanoTime();
        VisualConfig options = VisualConfig.get();
        boolean latticeMode = options.menuCursorGlowEnabled();
        boolean reset = lastFrame == 0L || now - lastFrame > 1_000_000_000L
                || width != previousWidth || height != previousHeight
                || latticeMode != previousLatticeMode;
        float delta = reset ? (1f / 60f) : Math.min(0.05f, Math.max(0f, (now - lastFrame) * 1e-9f));
        previousLatticeMode = latticeMode;
        lastFrame = now;
        previousWidth = width;
        previousHeight = height;
        if (!canAttemptCompute()) return;
        Themes.Theme theme = Theme.theme();
        int accent = theme != null ? theme.accent() : 0xFF65B4FB;
        int latticeGlow = options.menuCustomColors() ? options.menuGlowColorValue().getArgb() : accent;
        int soft = theme != null ? theme.accentSoft() : 0xFF7DA8CC;
        int cold = theme != null ? MainMenuBackdrop.coldColor(accent) : 0xFF89E0FF;
        int warm = MainMenuBackdrop.warmColor(accent);
        if (options.menuCustomColors()) {
            accent = options.menuFluidColorValue().getArgb();
            soft = MainMenuBackdrop.coldColor(accent);
            cold = MainMenuBackdrop.coldColor(soft);
            warm = MainMenuBackdrop.warmColor(accent);
        }
        if (options.menuFluidRainbow()) {
            float hue = (float)((now * 1.0e-9) * 0.055 % 1.0);
            accent = java.awt.Color.HSBtoRGB(hue, 0.84f, 1f);
            soft = java.awt.Color.HSBtoRGB((hue + 0.16f) % 1f, 0.80f, 1f);
            cold = java.awt.Color.HSBtoRGB((hue + 0.48f) % 1f, 0.86f, 1f);
            warm = java.awt.Color.HSBtoRGB((hue + 0.82f) % 1f, 0.83f, 1f);
        }
        float time = (float) ((now * 1e-9) % 2048.0);
        compute.render(CombatantRenderSystem.rhi(), width, height,
                hex, grid, delta, time, opacity, accent, soft, cold, warm, latticeGlow, reset);
    }

    void close() {
        compute.close();
        lastFrame = 0L;
        previousLatticeMode = false;
    }

}
