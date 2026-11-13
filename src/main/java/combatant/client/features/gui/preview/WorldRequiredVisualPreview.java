/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.preview;

import net.minecraft.client.Minecraft;

/** Live player/level dependency for hand and player-model visual modules. */
public final class WorldRequiredVisualPreview {
    private WorldRequiredVisualPreview() { }

    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null && mc.gameMode != null;
    }
}
