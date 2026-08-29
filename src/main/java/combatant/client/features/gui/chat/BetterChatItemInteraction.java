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

    public static boolean tryOpenPreview(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        VisualPreviewScreen.open(new ItemVisualPreviewProvider(stack));
        return true;
    }
}
