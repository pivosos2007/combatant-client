/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import net.minecraft.world.item.ItemStack;

public record UiItemCommand(ItemStack stack, int x, int y, float scale) implements UiCommand {
    @Override
    public UiCommandKind kind() {
        return UiCommandKind.ITEM;
    }
}
