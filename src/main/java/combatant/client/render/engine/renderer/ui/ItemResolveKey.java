/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class ItemResolveKey {
    final @Nullable LocalPlayer player;
    final ItemStack stack;
    final int seed;
    final int hash;

    ItemResolveKey(@Nullable LocalPlayer player, ItemStack stack, int seed) {
        this.player = player;
        this.stack = stack.copy();
        this.seed = seed;
        this.hash = 31 * (31 * ItemStack.hashItemAndComponents(this.stack) + seed) + System.identityHashCode(player);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemResolveKey other)) return false;
        return this.seed == other.seed
                && this.player == other.player
                && ItemStack.isSameItemSameComponents(this.stack, other.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
