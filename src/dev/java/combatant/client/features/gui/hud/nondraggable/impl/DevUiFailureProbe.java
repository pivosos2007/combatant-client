package combatant.client.features.gui.hud.nondraggable.impl;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.gui.hud.AbstractHudElement;
import combatant.client.features.gui.hud.HudElementInfo;
import combatant.client.features.gui.hud.HudRenderSpace;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Development-only fixture for the static UI branch of ClickGUI Settings. */
@HudElementInfo(
        id = "dev_ui_failure_probe",
        displayName = "Dev UI Failure",
        enabledByDefault = true,
        order = Integer.MAX_VALUE
)
public final class DevUiFailureProbe extends AbstractHudElement {
    private final BooleanValue failNextRender = bool(
            "dev_ui_fail_next_render", "fail_next_render", false);

    @Override
    public boolean usesEngineRenderer() {
        return true;
    }

    @Override
    public HudRenderSpace getRenderSpace() {
        return HudRenderSpace.SCALED;
    }

    @Override
    public void renderEngine(Renderer2D renderer,
                             TextRenderer textRenderer,
                             GuiGraphicsExtractor ctx,
                             float tickDelta,
                             int screenW,
                             int screenH) {
        float panelW = 92f;
        float panelH = 24f;
        float panelX = Math.max(8f, screenW - panelW - 18f);
        float panelY = Math.max(8f, screenH * 0.35f);
        setBounds(panelX, panelY, panelW, panelH);
        if (failNextRender.get()) {
            failNextRender.set(false);
            throw new IllegalStateException("DEVELOPMENT TEST: static UI render failure");
        }
        // Diagnostic fixture only: participating in the HUD callback is enough.
        // Do not draw a fake widget into the user's HUD.
    }
}
