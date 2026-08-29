/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BetterChatItemResolverTest {
    @BeforeAll
    static void bootstrapRegistries() {
        Bootstrap.bootStrap();
    }

    @Test
    void showItemKeepsComponentsUsedByModelAndGlint() {
        ItemStack source = new ItemStack(Items.DIAMOND_SWORD);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Cached weapon"));
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowItem(
                ItemStackTemplate.fromNonEmptyStack(source)
        ));
        ItemStack resolved = BetterChatRenderer.resolveItemFromStyle(style);

        assertFalse(resolved.isEmpty());
        assertEquals(source.getItem(), resolved.getItem());
        assertEquals("Cached weapon", resolved.getHoverName().getString());
        assertEquals(Boolean.TRUE, resolved.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        assertTrue(resolved.hasFoil());
    }
}
