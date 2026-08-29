/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.preview;

import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.util.ClickGuiHintOverlay;
import combatant.client.features.gui.clickgui.util.ClickGuiI18n;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.RenderPhase;
import combatant.client.render.engine.core.RenderPhaseScope;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.features.gui.preview.render.VisualPreviewBackgroundRenderer;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.FastFps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public enum VisualPreviewRenderer {
    ;

    public static void renderTopLayer(GuiGraphicsExtractor context, float tickDelta) {
        VisualPreviewScreen screen = VisualPreviewRuntime.activeScreen();
        if (screen == null || context == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        screen.updateFrameState();

        float width = Math.max(1.0f, HudScale.virtualWidth(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        float height = Math.max(1.0f, HudScale.virtualHeight(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        float settingsReserve = screen.settingsVisible() ? Math.min(430.0f, width * 0.38f) : 20.0f;
        float subjectX = 24.0f;
        float subjectY = 42.0f;
        float subjectW = Math.max(1.0f, width - subjectX - settingsReserve);
        float subjectH = Math.max(1.0f, height - subjectY - 42.0f);
        VisualPreviewSceneContext scene = new VisualPreviewSceneContext(
                mc, screen, width, height, subjectX, subjectY, subjectW, subjectH, tickDelta
        );

        CombatantRenderSystem.ensureFrameContext();
        ViewportContext.beginUnscaledLogical(context);
        try (RenderPhaseScope ignored = CombatantRenderSystem.phase(RenderPhase.SCREEN_TOP, "visual_preview")) {
            SystemCursor.beginFrame(context);
            try {
                renderBackgroundBase(width, height);
                Renderer2D.deferRenderThreadAction(() -> VisualPreviewBackgroundRenderer.render(scene));
                renderBackgroundShade(width, height);
                Renderer2D.deferRenderThreadAction(() -> screen.provider().renderSubject(scene));
                renderChrome(scene);
            } finally {
                SystemCursor.endFrame();
            }
        } finally {
            ViewportContext.end(context);
        }
    }

    private static void renderBackgroundBase(float width, float height) {
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        renderer.quad(0.0, 0.0, width, height,
                0xFF17313A, 0xFF10252F, 0xFF091820, 0xFF0D2028);
        renderer.render();
    }

    private static void renderBackgroundShade(float width, float height) {
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        renderer.quad(0.0, 0.0, width, height,
                0x1804080D, 0x0804080D, 0x4002060B, 0x2A02060B);
        renderer.render();
    }

    private static void renderChrome(VisualPreviewSceneContext scene) {
        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        float scale = Math.max(0.85f, Math.min(1.35f, scene.height() / 720.0f));
        float titleSize = 13.0f * scale;

        ClickGuiRenderer.drawText(
                ClickGuiRenderer.getInterMedium(),
                scene.screen().provider().title() + " | " + Math.max(0, FastFps.getFps()) + " FPS",
                20.0f * scale,
                17.0f * scale,
                titleSize,
                0xFFF2F5F8,
                false
        );
        scene.screen().provider().renderOverlay(scene, renderer);
        scene.screen().renderSettings(scene.width(), scene.height());
        renderHints(scene);
        renderer.render();
    }

    private static void renderHints(VisualPreviewSceneContext scene) {
        String settings = scene.screen().settingsVisible()
                ? hint("settings.hide", "Alt+S - hide settings")
                : hint("settings.show", "Alt+S - show settings");
        String reset = hint("reset", "R - reset view");
        String back = hint("back", "Esc - back to ClickGui");
        VisualPreviewInteractionProfile profile = scene.screen().provider().interactionProfile();
        if (profile.equals(VisualPreviewInteractionProfile.HAND_INSPECTION)) {
            ClickGuiHintOverlay.renderBottomLeft(
                    0.0f, 0.0f, scene.width(), scene.height(), 2.0f, 1.0f,
                    hint("hand.orbit", "LMB/RMB - orbit camera"),
                    hint("hand.rotate_subject", "MMB - rotate subject"),
                    hint("hand.distance", "Wheel - camera distance"),
                    reset,
                    settings,
                    back
            );
        } else if (profile.equals(VisualPreviewInteractionProfile.FIXED)) {
            ClickGuiHintOverlay.renderBottomLeft(
                    0.0f, 0.0f, scene.width(), scene.height(), 2.0f, 1.0f,
                    hint("fixed", "Fixed camera for ViewModel positioning"),
                    settings,
                    back
            );
        } else if (profile.cameraMode() == VisualPreviewCameraMode.FREE_FLY) {
            ClickGuiHintOverlay.renderBottomLeft(
                    0.0f, 0.0f, scene.width(), scene.height(), 2.0f, 1.0f,
                    hint("free_look", "LMB/RMB - look around"),
                    hint("free_fly", "WASD/Space/Shift - fly"),
                    hint("free_speed", "Wheel - flight speed"),
                    reset,
                    settings,
                    back
            );
        } else {
            ClickGuiHintOverlay.renderBottomLeft(
                    0.0f, 0.0f, scene.width(), scene.height(), 2.0f, 1.0f,
                    hint("object.rotate", "LMB - rotate subject"),
                    hint("object.zoom", "Wheel - zoom subject"),
                    reset,
                    settings,
                    back
            );
        }
    }

    private static String hint(String suffix, String fallback) {
        return ClickGuiI18n.tr("clickgui.hints.visual_preview." + suffix, fallback);
    }
}
