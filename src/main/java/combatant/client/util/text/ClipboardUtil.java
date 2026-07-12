/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import net.minecraft.client.Minecraft;

public enum ClipboardUtil {
    ;

    public static void copy(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            try {
                mc.keyboardHandler.setClipboard(text == null ? "" : text);
            } catch (Throwable ignored) {
            }
        }
    }

    public static String get() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            try {
                String clip = mc.keyboardHandler.getClipboard();
                return clip == null ? "" : clip;
            } catch (Throwable ignored) {
            }
        }
        return "";
    }
}
