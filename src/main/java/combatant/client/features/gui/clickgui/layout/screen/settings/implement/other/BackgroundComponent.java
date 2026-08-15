/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions derived from ThunderHack Recode, copyright (c) 2023-2024 Pan4ur & 06ED.
 * Upstream: https://github.com/Pan4ur/ThunderHack-Recode
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.layout.screen.settings.implement.other;

import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.SettingsGlassMaterial;

public final class BackgroundComponent {

    public void render(float x, float y, float w, float h, float scale) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        SettingsGlassMaterial.workspace(x, y, w, h, scale, palette);
    }

    public void render(float x, float y, float w, float h, float scale, float prismProgress) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        SettingsGlassMaterial.workspace(x, y, w, h, scale, palette, prismProgress);
    }
}
