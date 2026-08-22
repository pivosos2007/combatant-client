/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sound;

import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.ClickGui;
import combatant.client.util.sound.SoundAsset;
import combatant.client.util.sound.SoundCatalog;
import combatant.client.util.sound.SoundKey;
import combatant.client.util.sound.SoundOptions;

/** ClickGUI's declarative catalog. Registration is performed by SoundRegistry/ClassGraph. */
@SoundCatalog(namespace = "combatant", root = "sounds/gui", idPrefix = "gui")
public enum GuiSound implements SoundKey {
    @SoundAsset(value = "guibinding.wav", id = "guibinding")
    BINDING,
    @SoundAsset(value = "guibindingnull.wav", id = "guibindingnull")
    BINDING_NULL,
    @SoundAsset(value = "guibindingstart.wav", id = "guibindingstart")
    BINDING_START,
    @SoundAsset(value = "guibindreset.wav", id = "guibindreset")
    BIND_RESET,
    @SoundAsset(value = "guichangemode.wav", id = "guichangemode")
    CHANGE_MODE,
    @SoundAsset(value = "guiyes.wav", id = "guiyes")
    OPEN,
    @SoundAsset(value = "guino.wav", id = "guino")
    CLOSE,
    @SoundAsset(value = "moduleopen.wav", id = "moduleopen")
    MODULE_OPEN,
    @SoundAsset(value = "moduleclose.wav", id = "moduleclose")
    MODULE_CLOSE,
    @SoundAsset(value = "moduleonopen.wav", id = "moduleonopen", gain = 1.4f)
    MODULE_ON,
    @SoundAsset(value = "moduleonclose.wav", id = "moduleonclose", gain = 1.4f)
    MODULE_OFF,
    @SoundAsset(value = "guiscroll.wav", id = "guiscroll")
    SCROLL(40),
    @SoundAsset(value = "guislidermove.wav", id = "guislidermove")
    SLIDER_MOVE(35);

    private final long cooldownNs;
    private long lastPlayNs;

    GuiSound() {
        this(0L);
    }

    GuiSound(long cooldownMs) {
        this.cooldownNs = cooldownMs * 1_000_000L;
    }

    public void feedback() {
        feedback(1.0);
    }

    public void feedback(double gain) {
        ClickGui config = Modules.get(ClickGui.class);
        if (config != null && !config.isGuiSoundEnabled(configKey())) return;
        if (cooldownNs > 0L) {
            long now = System.nanoTime();
            if (now - lastPlayNs < cooldownNs) return;
            lastPlayNs = now;
        }
        double volume = config == null ? 1.0 : config.getGuiSoundVolume();
        if (gain > 0.0 && volume > 0.0) play(SoundOptions.gain(gain * volume));
    }

    private String configKey() {
        String path = soundDefinition().id().getPath();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
