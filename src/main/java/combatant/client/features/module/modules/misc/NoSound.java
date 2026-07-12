/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.SetValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

import java.util.Locale;
import java.util.Set;

//todo Description
@ModuleInfo(
        id = "nosound",
        displayName = "NoSound",
        category = ModuleCategory.MISC
)
public class NoSound extends Module {
    private static final String SETTING_SOUND_IDS = "sound_ids";
    private final SetValue soundIds = textList("noSoundIds", SETTING_SOUND_IDS, TextListSetting.PickerMode.SOUNDS);

    {
        soundIds.set(Set.of("minecraft:entity.ender_dragon.growl"));
    }

    public boolean shouldMute(String id) {
        if (!isEnabled()) return false;
        if (id == null || id.isEmpty()) return false;
        String normalized = id.toLowerCase(Locale.ROOT);
        for (String mutedId : soundIds.get()) {
            if (mutedId != null && mutedId.toLowerCase(Locale.ROOT).equals(normalized)) return true;
        }
        return false;
    }
}
