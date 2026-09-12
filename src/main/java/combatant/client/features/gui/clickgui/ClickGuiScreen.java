/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import combatant.client.features.module.ModuleManager;

import java.nio.file.Path;
import java.util.List;

public final class ClickGuiScreen extends Screen {

    public ClickGuiScreen() {
        super(Component.literal("ClickGui"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ClickGuiRenderer.onMouseMoveScaled(mouseX, mouseY);
        ClickGuiRenderer.renderScreen(context, delta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No vanilla blur/darkening behind ClickGUI.
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        ClickGuiRenderer.onMouseMoveScaled(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        ClickGuiRenderer.onMouseButtonScaled(click.x(), click.y(), click.button(), true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        ClickGuiRenderer.onMouseButtonScaled(click.x(), click.y(), click.button(), false);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ClickGuiRenderer.onMouseScrollScaled(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!ClickGuiRenderer.onFilesDrop(paths)) {
            super.onFilesDrop(paths);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModuleManager.setEnabled("clickgui", false);
    }
}
