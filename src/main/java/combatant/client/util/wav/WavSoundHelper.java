/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import net.minecraft.resources.Identifier;
import combatant.client.config.values.ConfigValue;
import combatant.client.features.gui.clickgui.settings.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Компактный helper для модулей: регистрирует WavSound, добавляет громкости в конфиг и в ClickGUI.
 * Использование:
 * WavSoundHelper wav = new WavSoundHelper();
 * WavSound hit = wav.add("hit", Identifier.of("combatant", "bell"), 1.0);
 * // в getConfigValues -> wav.getConfigValues()
 * // в loadSettings    -> wav.getSettings("Volume")
 * // воспроизведение   -> hit.play();
 */
public class WavSoundHelper {

    private final List<WavSound> sounds = new ArrayList<>();

    public WavSound add(String name, Identifier id, double defaultVolume) {
        WavSound s = new WavSound(name, id, defaultVolume);
        sounds.add(s);
        return s;
    }

    public List<ConfigValue<?>> getConfigValues() {
        List<ConfigValue<?>> list = new ArrayList<>();
        for (WavSound s : sounds) {
            list.add(s.getVolumeValue());
        }
        return list;
    }

    public List<Setting> getSettings(String labelPrefix) {
        List<Setting> list = new ArrayList<>();
        for (WavSound s : sounds) {
            String label = labelPrefix + " " + s.getId().getPath();
            list.add(s.createVolumeSetting(label));
        }
        return list;
    }

    public void setMasterGain(double gain) {
        CustomSoundEngine.get().setMasterGain(gain);
    }
}
