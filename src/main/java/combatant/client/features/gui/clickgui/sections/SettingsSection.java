/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.clickgui.sections;

import combatant.client.features.gui.clickgui.settings.SettingsImplementation;
import combatant.client.features.gui.clickgui.settings.SettingsImplementations;
import combatant.client.features.gui.clickgui.settings.SettingsPage;

import java.nio.file.Path;
import java.util.List;

/** Settings tab host. Rendering, layout and input come from the selected implementation. */
@ClickGuiSectionInfo(id = "combatant:settings", label = "Settings", order = 100)
public final class SettingsSection implements ClickGuiSection {
    private SettingsImplementation implementation;
    private SettingsPage page;
    private boolean selected;
    private float x, y, width, height;

    /** Switching implementations closes the old page and opens the newly selected page. */
    private SettingsPage page() {
        SettingsImplementation chosen = SettingsImplementations.active();
        if (implementation != chosen) {
            if (page != null && selected) page.close();
            implementation = chosen;
            page = chosen.page();
            if (page == null) throw new IllegalStateException("Settings implementation returned no page: " + chosen.id());
            if (selected) {
                page.open();
                page.markDirty();
            }
        }
        return page;
    }

    @Override
    public void layout(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void render(float mouseX, float mouseY) {
        page().render(x, y, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mousePressed(float mouseX, float mouseY, int button) {
        return page().mousePressed(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        page().mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        return page().mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return page().keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return page().charTyped(chr, modifiers);
    }

    @Override
    public boolean onCancel() { return page().cancel(); }

    @Override
    public boolean onFilesDrop(List<Path> paths) { return page().filesDropped(paths); }

    @Override
    public void onSelected() {
        if (selected) return;
        page();
        selected = true;
        page.open();
        page.markDirty();
    }

    @Override
    public void onDeselected() {
        if (!selected) return;
        selected = false;
        if (page != null) page.close();
    }

    @Override
    public boolean isVisible() {
        return page().visible();
    }
}
