/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.schematic.SchematicPanel;
import combatant.client.input.InputKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;
import combatant.client.render.engine.text.BuiltinFontCatalog;
import combatant.client.render.engine.text.TextRenderer;

/** ClickGUI tab hosting the schematic menu: the same {@link SchematicPanel} the {@code @schematic} command opens. */
@ClickGuiSectionInfo(id = "combatant:schematics", label = "Schematics", order = 300,
        requiredMods = "litematica")
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
        if (!worldReady()) {
            renderOffline();
            return;
        }
        panel.renderEmbedded(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        if (!worldReady()) return false;
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
        if (worldReady()) panel.mouseReleased(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return worldReady() && panel.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return worldReady() && panel.keyPressed(keyCode);
    }

    @Override
    public boolean onCancel() { return worldReady() && panel.cancelCurrentAction(); }

    @Override
    public boolean blocksMovementInput() { return worldReady() && panel.isTextInputActive(); }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return worldReady() && panel.charTyped(chr);
    }

    @Override
    public void onSelected() {
        if (!selected && worldReady()) panel.restartOpenAnimation();
        selected = true;
        if (worldReady()) panel.init();
    }

    @Override
    public void onDeselected() {
        selected = false;
        if (worldReady()) {
            panel.mouseReleased(-10_000f, -10_000f);
            panel.removed();
        }
    }

    private static boolean worldReady() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    private void renderOffline() {
        String text = I18n.get("screen.combatant.clickgui.schematics_offline");
        TextRenderer font = BuiltinFontCatalog.ONEST_MEDIUM.renderer();
        float fontSize = 14f;
        float textWidth = LayoutRender2D.textWidth(font, text, fontSize);
        LayoutRender2D.drawText(font, text, x + (width - textWidth) * 0.5f,
                y + (height - fontSize) * 0.5f, fontSize, Theme.theme().textMuted(), false);
    }

    @Override
    public boolean isVisible() {
        return selected;
    }
}
