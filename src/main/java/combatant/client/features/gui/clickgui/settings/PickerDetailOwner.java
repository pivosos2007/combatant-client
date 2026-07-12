/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

/**
 * Optional extension for picker-backed settings that need an inline editor inside the picker overlay.
 */
public interface PickerDetailOwner {
    float pickerDetailWidth();

    void onPickerFocusChanged(String id);

    /**
     * Regular picker clicks toggle selection by default. Detail-backed pickers can
     * use grid clicks only as focus changes and expose add/remove actions inside
     * their details panel.
     */
    default boolean shouldToggleSelectionOnCardClick() {
        return true;
    }

    void renderPickerDetails(float x, float y, float w, float h, String focusedId, float mouseX, float mouseY);

    boolean mouseClickedPickerDetails(float mouseX, float mouseY, int button);

    default void mouseReleasedPickerDetails(float mouseX, float mouseY, int button) {
    }

    /**
     * Return true when the detail panel consumed the wheel event.
     */
    default boolean mouseScrolledPickerDetails(float mouseX, float mouseY, double delta) {
        return false;
    }
}
