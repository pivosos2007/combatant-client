/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** A real inline item layout node, not a font glyph. */
public final class ItemNode implements BetterChatNode {
    private final ItemStack stack;

    public ItemNode(ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public ItemStack stack() {
        return stack.copy();
    }

    @Override
    public String plainText() {
        if (stack.isEmpty()) return "";
        return "[" + stack.getHoverName().getString() + "]";
    }

    public boolean semanticallyEquals(ItemNode other) {
        if (other == null) return false;
        ItemStack theirs = other.stack;
        if (stack.isEmpty() || theirs.isEmpty()) return stack.isEmpty() && theirs.isEmpty();
        return stack.getCount() == theirs.getCount()
                && stack.getItem() == theirs.getItem()
                && Objects.equals(stack.getComponents(), theirs.getComponents());
    }
}
