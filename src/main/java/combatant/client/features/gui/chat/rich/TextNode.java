/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import net.minecraft.network.chat.Component;

import java.util.Objects;

public record TextNode(Component component) implements BetterChatNode {
    public TextNode {
        component = component == null ? Component.empty() : component.copy();
    }

    @Override
    public String plainText() {
        return component.getString();
    }

    public boolean semanticallyEquals(TextNode other) {
        return other != null && Objects.equals(component, other.component);
    }
}
