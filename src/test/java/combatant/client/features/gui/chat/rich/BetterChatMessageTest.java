/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BetterChatMessageTest {
    @Test
    void builderPreservesOrderedNodeTypesAndAccessibleText() {
        BetterChatMessage message = BetterChatMessages.builder()
                .text(Component.literal("Picked up "))
                .item(ItemStack.EMPTY)
                .text(" x3")
                .build();

        assertEquals(3, message.nodes().size());
        assertInstanceOf(TextNode.class, message.nodes().get(0));
        assertInstanceOf(ItemNode.class, message.nodes().get(1));
        assertInstanceOf(TextNode.class, message.nodes().get(2));
        assertTrue(message.plainText().startsWith("Picked up "));
        assertTrue(message.plainText().endsWith(" x3"));
    }

    @Test
    void semanticEqualityIncludesComponentStyle() {
        BetterChatMessage left = BetterChatMessages.builder()
                .text(Component.literal("same").withStyle(ChatFormatting.RED))
                .build();
        BetterChatMessage right = BetterChatMessages.builder()
                .text(Component.literal("same").withStyle(ChatFormatting.RED))
                .build();
        BetterChatMessage different = BetterChatMessages.builder()
                .text(Component.literal("same").withStyle(ChatFormatting.BLUE))
                .build();

        assertTrue(left.semanticallyEquals(right));
        assertFalse(left.semanticallyEquals(different));
    }
}
