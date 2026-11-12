/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview;

import combatant.client.features.gui.clickgui.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

/** Live player/level dependency for hand and player-model visual modules. */
public final class WorldRequiredVisualPreview {
    private WorldRequiredVisualPreview() { }

    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null && mc.gameMode != null;
    }

    /** Keep persisted settings unchanged while making controls read-only outside the world. */
    public static void restrictSettings(List<Setting> settings, Setting keyBind) {
        for (Setting setting : settings) {
            if (setting == keyBind) continue;
            setting.unavailableReason(() -> available()
                    ? null : I18n.get("screen.combatant.clickgui.section_offline"));
        }
    }
}
