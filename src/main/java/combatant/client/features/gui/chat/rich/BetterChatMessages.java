/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public enum BetterChatMessages {
    ;

    public static Builder builder() {
        return new Builder();
    }

    public static BetterChatMessage text(Component component) {
        return BetterChatMessage.text(component);
    }

    public static final class Builder {
        private final List<BetterChatNode> nodes = new ArrayList<>();

        public Builder text(String text) {
            return text(Component.literal(text == null ? "" : text));
        }

        public Builder text(Component text) {
            nodes.add(new TextNode(text));
            return this;
        }

        public Builder item(ItemStack stack) {
            nodes.add(new ItemNode(stack));
            return this;
        }

        public BetterChatMessage build() {
            return new BetterChatMessage(nodes);
        }
    }
}
