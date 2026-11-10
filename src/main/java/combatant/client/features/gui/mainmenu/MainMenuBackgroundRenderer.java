/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.mainmenu;

/** Reuses the title screen's backdrop materials for other screens without a duplicate renderer. */
public final class MainMenuBackgroundRenderer {
    private MainMenuBackgroundRenderer() {
    }

    /** Render inside an active Renderer2D.COLOR batch using unscaled logical coordinates. */
    public static void render(float width, float height, float mouseX, float mouseY) {
        MainMenuBackdrop.render(width, height, 1.0f, mouseX, mouseY,
                MainMenuBackdrop.layout(width, height), null);
    }
}
