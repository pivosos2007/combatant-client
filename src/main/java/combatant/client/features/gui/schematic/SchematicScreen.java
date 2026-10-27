/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.schematic;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Standalone host of the schematic menu ({@code @schematic} command); the ClickGUI "Schematics" section hosts the same {@link SchematicPanel}. */
public final class SchematicScreen extends Screen {

    private final Screen parent;
    private final SchematicPanel panel;

    public SchematicScreen(Screen parent) {
        super(Component.translatable("screen.combatant.schematic.title"));
        this.parent = parent instanceof ChatScreen ? null : parent;
        this.panel = new SchematicPanel(this::closeToParent);
    }

    @Override
    protected void init() {
        panel.init();
    }

    @Override
    public void tick() {
        panel.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mx, int my, float delta) {
        panel.renderStandalone(context, mx, my);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return panel.mousePressed(SchematicPanel.toFixed((float) click.x()), SchematicPanel.toFixed((float) click.y()),
                click.button(), doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (panel.mouseDragged(SchematicPanel.toFixed((float) click.x()), SchematicPanel.toFixed((float) click.y()))) return true;
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        panel.mouseReleased(SchematicPanel.toFixed((float) click.x()), SchematicPanel.toFixed((float) click.y()));
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return panel.mouseScrolled(SchematicPanel.toFixed((float) x), SchematicPanel.toFixed((float) y), vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return panel.keyPressed(input.key()) || super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return panel.charTyped(input.codepoint()) || super.charTyped(input);
    }

    @Override
    public void removed() {
        panel.removed();
        super.removed();
    }

    private void closeToParent() {
        if (minecraft != null) ClientScreen.show(minecraft, parent);
    }
}
