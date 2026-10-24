/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.schematic.SchematicPanel;
import combatant.client.input.InputKeys;

/** ClickGUI tab hosting the schematic menu: the same {@link SchematicPanel} the {@code @schematic} command opens. */
@ClickGuiSectionInfo(id = "combatant:schematics", label = "Schematics", order = 300)
public final class SchematicsSection implements ClickGuiSection {
    private static final long DOUBLE_CLICK_NANOS = 350_000_000L;
    private static final float DOUBLE_CLICK_SLOP = 6f;

    private final SchematicPanel panel = new SchematicPanel();
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean selected;
    private long lastClickNanos;
    private float lastClickX;
    private float lastClickY;

    @Override
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        panel.renderEmbedded(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        // The ClickGUI reports Combatant's own button codes (left = 1); the panel uses GLFW's (left = 0).
        boolean left = button == InputKeys.MOUSE_LEFT;
        int glfwButton = left ? 0 : button == InputKeys.MOUSE_RIGHT ? 1 : button == InputKeys.MOUSE_MIDDLE ? 2 : button;
        long now = System.nanoTime();
        boolean doubled = left && now - lastClickNanos < DOUBLE_CLICK_NANOS
                && Math.abs(mouseX - lastClickX) <= DOUBLE_CLICK_SLOP
                && Math.abs(mouseY - lastClickY) <= DOUBLE_CLICK_SLOP;
        if (left) {
            lastClickNanos = doubled ? 0L : now;
            lastClickX = mouseX;
            lastClickY = mouseY;
        }
        return panel.mousePressed(mouseX, mouseY, glfwButton, doubled);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        panel.mouseReleased(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return panel.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return panel.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return panel.charTyped(chr);
    }

    @Override
    public void onSelected() {
        if (!selected) panel.restartOpenAnimation();
        selected = true;
        panel.init();
    }

    @Override
    public void onDeselected() {
        selected = false;
        panel.mouseReleased(-10_000f, -10_000f);
        panel.removed();
    }

    @Override
    public boolean isVisible() {
        return selected;
    }
}
