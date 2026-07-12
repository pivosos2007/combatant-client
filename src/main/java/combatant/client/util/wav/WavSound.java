/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import net.minecraft.resources.Identifier;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SliderSetting;

/**
 * Helper for modules: WAV sound entry with per-sound volume setting.
 */
public class WavSound {

    private final Identifier id;
    private final NumberValue<Double> volume;

    public WavSound(String name, Identifier id, double defVolume) {
        this.id = id;
        this.volume = new NumberValue<>(name + "_vol", defVolume, 0.0, 2.0);
    }

    public Identifier getId() {
        return id;
    }

    public ConfigValue<?> getVolumeValue() {
        return volume;
    }

    public Setting createVolumeSetting(String label) {
        return new SliderSetting(label, volume);
    }

    public void play(double pitch, boolean loop, boolean relative) {
        CustomSoundEngine.get().play(id, volume.get(), pitch, loop, relative);
    }

    public void play() {
        play(1.0, false, true);
    }
}
