package combatant.client.features.gui.hud.draggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.draggable.DraggableHudElement;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Development-only fixture for the HUD branch of ClickGUI Settings. */
@HudElementInfo(
        id = "dev_hud_failure_probe",
        displayName = "Dev HUD Failure",
        enabledByDefault = true,
        order = Integer.MAX_VALUE - 1
)
public final class DevHudFailureProbe extends DraggableHudElement {
    private final BooleanValue failNextRender = bool(
            "dev_hud_fail_next_render", "fail_next_render", false);

    @Override
    public void applyDefaultPosition(int screenW, int screenH) {
        moveTo(18f, Math.max(18f, screenH * 0.35f));
    }

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        setBounds(x, y, 92f, 24f);
        if (failNextRender.get()) {
            failNextRender.set(false);
            throw new IllegalStateException("DEVELOPMENT TEST: draggable HUD render failure");
        }
        // Diagnostic fixture only: participating in the HUD callback is enough.
        // Do not draw a fake widget into the user's HUD.
    }
}
