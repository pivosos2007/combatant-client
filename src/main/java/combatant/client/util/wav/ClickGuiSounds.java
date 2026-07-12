/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.wav;

import combatant.client.features.module.Modules;
import net.minecraft.resources.Identifier;
import combatant.client.features.module.modules.misc.ClickGui;

public enum ClickGuiSounds {
    ;
    private static final double DEFAULT_GAIN = 1.0;
    private static final double DEFAULT_PITCH = 1.0;
    private static final boolean LOOP = false;
    private static final boolean RELATIVE = true;
    private static final double MODULE_TOGGLE_GAIN = 1.4;

    private static final long SCROLL_COOLDOWN_NS = 40_000_000L;
    private static final long SLIDER_COOLDOWN_NS = 35_000_000L;
    private static final String KEY_BINDING = "guibinding";
    private static final String KEY_BINDING_NULL = "guibindingnull";
    private static final String KEY_BINDING_START = "guibindingstart";
    private static final String KEY_BIND_RESET = "guibindreset";
    private static final String KEY_CHANGE_MODE = "guichangemode";
    private static final String KEY_GUI_OPEN = "guiyes";
    private static final String KEY_GUI_CLOSE = "guino";
    private static final String KEY_MODULE_OPEN = "moduleopen";
    private static final String KEY_MODULE_CLOSE = "moduleclose";
    private static final String KEY_MODULE_ON_OPEN = "moduleonopen";
    private static final String KEY_MODULE_ON_CLOSE = "moduleonclose";
    private static final String KEY_SCROLL = "guiscroll";
    private static final String KEY_SLIDER_MOVE = "guislidermove";
    private static final Identifier GUI_BINDING =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guibinding.wav");
    private static final Identifier GUI_BINDING_NULL =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guibindingnull.wav");
    private static final Identifier GUI_BINDING_START =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guibindingstart.wav");
    private static final Identifier GUI_BIND_RESET =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guibindreset.wav");
    private static final Identifier GUI_CHANGE_MODE =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guichangemode.wav");
    private static final Identifier GUI_YES =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guiyes.wav");
    private static final Identifier GUI_NO =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guino.wav");
    private static final Identifier MODULE_OPEN =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/moduleopen.wav");
    private static final Identifier MODULE_CLOSE =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/moduleclose.wav");
    private static final Identifier MODULE_ON_OPEN =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/moduleonopen.wav");
    private static final Identifier MODULE_ON_CLOSE =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/moduleonclose.wav");
    private static final Identifier GUI_SCROLL =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guiscroll.wav");
    private static final Identifier GUI_SLIDER_MOVE =
            Identifier.fromNamespaceAndPath("combatant", "sounds/gui/guislidermove.wav");
    private static long lastScrollNs;
    private static long lastSliderNs;

    public static void bindingStart() {
        play(KEY_BINDING_START, GUI_BINDING_START);
    }

    public static void bindingSuccess() {
        play(KEY_BINDING, GUI_BINDING);
    }

    public static void bindingReset() {
        play(KEY_BIND_RESET, GUI_BIND_RESET);
    }

    public static void bindingNull() {
        play(KEY_BINDING_NULL, GUI_BINDING_NULL);
    }

    public static void changeMode() {
        play(KEY_CHANGE_MODE, GUI_CHANGE_MODE);
    }

    public static void guiOpen() {
        playScaled(KEY_GUI_OPEN, GUI_YES, 0.25);
    }

    public static void guiClose() {
        playScaled(KEY_GUI_CLOSE, GUI_NO, 0.25);
    }

    public static void moduleExpandOpen() {
        play(KEY_MODULE_OPEN, MODULE_OPEN);
    }

    public static void moduleExpandClose() {
        play(KEY_MODULE_CLOSE, MODULE_CLOSE);
    }

    public static void moduleToggleOn() {
        playScaled(KEY_MODULE_ON_OPEN, MODULE_ON_OPEN, MODULE_TOGGLE_GAIN);
    }

    public static void moduleToggleOff() {
        playScaled(KEY_MODULE_ON_CLOSE, MODULE_ON_CLOSE, MODULE_TOGGLE_GAIN);
    }

    public static void scroll() {
        long now = System.nanoTime();
        if (now - lastScrollNs < SCROLL_COOLDOWN_NS) {
            return;
        }
        lastScrollNs = now;
        play(KEY_SCROLL, GUI_SCROLL);
    }

    public static void sliderMove() {
        long now = System.nanoTime();
        if (now - lastSliderNs < SLIDER_COOLDOWN_NS) {
            return;
        }
        lastSliderNs = now;
        play(KEY_SLIDER_MOVE, GUI_SLIDER_MOVE);
    }

    private static void play(String key, Identifier id) {
        if (!isEnabled(key)) return;
        double gain = DEFAULT_GAIN * getVolume();
        if (gain <= 0.0) return;
        CustomSoundEngine.get().play(id, gain, DEFAULT_PITCH, LOOP, RELATIVE);
    }

    private static void playScaled(String key, Identifier id, double scale) {
        if (!isEnabled(key)) return;
        if (scale <= 0.0) return;
        double gain = DEFAULT_GAIN * getVolume() * scale;
        if (gain <= 0.0) return;
        CustomSoundEngine.get().play(id, gain, DEFAULT_PITCH, LOOP, RELATIVE);
    }


    private static boolean isEnabled(String key) {
        ClickGui cfg = Modules.get(ClickGui.class);
        return cfg == null || cfg.isGuiSoundEnabled(key);
    }

    private static double getVolume() {
        ClickGui cfg = Modules.get(ClickGui.class);
        return cfg != null ? cfg.getGuiSoundVolume() : 1.0;
    }
}
