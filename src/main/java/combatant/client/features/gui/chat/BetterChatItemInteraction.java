/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import combatant.client.features.gui.preview.VisualPreviewScreen;
import combatant.client.features.gui.preview.provider.ItemVisualPreviewProvider;
import net.minecraft.world.item.ItemStack;

public enum BetterChatItemInteraction {
    ;

    private static volatile boolean requireControl = true;

    public static boolean tryOpenPreview(ItemStack stack, boolean controlDown) {
        if (stack == null || stack.isEmpty()) return false;
        if (requireControl && !controlDown) return false;
        VisualPreviewScreen.open(new ItemVisualPreviewProvider(stack));
        return true;
    }

    public static void setRequireControl(boolean value) {
        requireControl = value;
    }
}
