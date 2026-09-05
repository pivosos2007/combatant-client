/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import combatant.client.features.gui.clickgui.layout.screen.settings.MenuScreen;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.text.TextRenderer;

import java.nio.file.Path;
import java.util.List;

public enum SettingsTabRuntime {
    ;
    private static final MenuScreen SCREEN = new MenuScreen();

    public static void render(float areaX, float areaY, float areaW, float areaH, float mx, float my) {
        SCREEN.render(areaX, areaY, areaW, areaH, mx, my);
    }

    public static void open() {
        SCREEN.open();
    }

    public static void close() {
        SCREEN.close();
    }

    public static boolean isVisible() {
        return SCREEN.isVisible();
    }

    public static boolean mousePressed(float mx, float my, int button) {
        return SCREEN.mouseClicked(mx, my, button);
    }

    public static void mouseReleased(float mx, float my, int button) {
        SCREEN.mouseReleased(mx, my, button);
    }

    public static void mouseScrolled(float mx, float my, double delta) {
        SCREEN.mouseScrolled(mx, my, delta);
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return SCREEN.keyPressed(keyCode, scanCode, modifiers);
    }

    public static boolean charTyped(char chr, int modifiers) {
        return SCREEN.charTyped(chr, modifiers);
    }

    public static boolean onFilesDrop(List<Path> paths) {
        return SCREEN.onFilesDrop(paths);
    }

    public static void renderEditor(Renderer2D rendererIn,
                                    TextRenderer fallback,
                                    GuiGraphicsExtractor ctx,
                                    float tickDelta,
                                    int fbw,
                                    int fbh,
                                    float mx,
                                    float my) {
    }

    public static void handleEditorMouseButton(float mx, float my, int button, boolean pressed) {
    }

    public static void handleEditorMouseScroll(float mx, float my, double delta) {
    }

    public static boolean isEditorOpen() {
        return false;
    }

    public static void closeGuiEditor() {
    }

    public static boolean isColorEditorOpen() {
        return false;
    }

    public static void closeColorEditor() {
    }

    public static void handleColorEditorMouseButton(float mx, float my, int button, boolean pressed) {
    }

    public static void handleColorEditorScroll(float mx, float my, double delta) {
    }

    public static void markDirty() {
        SCREEN.markDirty();
    }

    public static void clearScreens() {
        SCREEN.clearScreens();
    }
}
