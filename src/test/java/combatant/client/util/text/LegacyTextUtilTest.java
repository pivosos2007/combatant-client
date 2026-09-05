/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LegacyTextUtilTest {
    @Test
    void parsesLegacyCodeSplitAcrossComponentSiblings() {
        Component split = Component.literal("&").append(Component.literal("3Player"));
        assertEquals("Player", LegacyTextUtil.convertLegacyCodesRobust(split).getString());
    }

    @Test
    void keepsNativeStyledComponentsWhenNoRawLegacyMarkerLeaks() {
        Component styled = Component.literal("Player").withColor(0x55FFFF);
        Component converted = LegacyTextUtil.convertLegacyCodesRobust(styled);
        assertEquals("Player", converted.getString());
        assertEquals(styled, converted);
    }
}
