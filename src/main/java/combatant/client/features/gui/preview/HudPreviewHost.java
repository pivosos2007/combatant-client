/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview;

import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.nondraggable.StaticHudElementRegistry;
import combatant.client.features.gui.preview.api.HudPreviewCanvas;
import combatant.client.features.gui.preview.api.PreviewCatalog;
import combatant.client.features.gui.preview.api.PreviewFrame;
import combatant.client.features.module.HudPhase;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Map;

/**
 * Executes preview participants in the same draw context as the ordinary HUD.
 * A participant contributes sample state, not a second visual implementation.
 * This host runs only when a HUD settings panel requests preview; vanilla hooks
 * are never fired artificially and live client/player data is never mutated.
 */
public final class HudPreviewHost {
    private HudPreviewHost() { }

    public static void render(Renderer2D renderer, TextRenderer text, GuiGraphicsExtractor graphics,
                              float tickDelta, int virtualWidth, int virtualHeight) {
        if (!DraggableHudElementRegistry.isForceVisible()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean worldAvailable = minecraft != null && minecraft.level != null && minecraft.player != null;
        String selectedId = DraggableHudElementRegistry.editorPreviewTargetId();
        PreviewFrame scene = new PreviewFrame(PreviewFrame.Mode.SAMPLE, worldAvailable,
                selectedId != null ? selectedId : "settings", Map.of());
        HudPreviewCanvas canvas = new HudPreviewCanvas(renderer, text, graphics, tickDelta,
                virtualWidth, virtualHeight);
        try (PreviewFrame.Scope ignored = PreviewFrame.enter(scene)) {
            // Reuse the existing draggable render dispatcher, including layout, anchoring,
            // material state and per-widget error isolation. Never hand-draw fake widgets.
            for (HudPhase phase : HudPhase.values()) {
                if (phase != HudPhase.NONE) {
                    DraggableHudElementRegistry.renderAllEngine(phase, renderer, text, graphics, tickDelta);
                }
            }
            DraggableHudElementRegistry.renderEditorOverlay(renderer, text, virtualWidth, virtualHeight);
            // The "all enabled" editor mode previews draggable widgets only. Static HUD
            // samples belong exclusively to the settings page of the selected element.
            // Otherwise every enabled static widget contributes a fake example on screen.
            if (selectedId == null) return;

            // Each preview declares its production projection. Projection transitions
            // flush the pending 2D batch, keeping draggable and native HUD draws ordered.
            for (var entry : PreviewCatalog.entries().entrySet()) {
                PreviewCatalog.Id id = entry.getKey();
                if (id.surface() != PreviewCatalog.Surface.HUD) continue;
                if (!selectedId.equals(id.owner())
                        && !("vanilla_tooltips".equals(id.owner())
                        && ("vanilla_tooltip_gui".equals(selectedId)
                        || "vanilla_tooltip_items".equals(selectedId)
                        || "vanilla_shulker_preview".equals(selectedId)))) continue;
                if (StaticHudElementRegistry.getById(id.owner()) == null) continue;
                try (PreviewFontBoundary fontBoundary = PreviewFontBoundary.capture()) {
                    switch (entry.getValue().previewSpace()) {
                        case LOGICAL_HUD -> ViewportContext.logicalProjection();
                        case FRAMEBUFFER -> ViewportContext.unscaledProjection();
                        case VANILLA_GUI -> ViewportContext.scaledProjection();
                    }
                    PreviewCatalog.render(id, scene, canvas);
                } catch (RuntimeException | LinkageError failure) {
                    combatant.client.util.logging.DebugLog.warnOnce(
                            "hud-static-preview-" + id.owner(),
                            "HUD preview failed: " + id.owner(), failure);
                } finally {
                    // A mixed-space presenter may switch projection for its own text;
                    // the next participant and ClickGui must always start in LOGICAL.
                    ViewportContext.logicalProjection();
                }
            }
        }
    }
}
