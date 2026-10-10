/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.autoarmor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionFocusTest {

    /** The formula AutoArmor used before ProtectionFocus existed; BALANCED must keep matching it. */
    private static double originalScore(double armor, double toughness, double kb,
                                        int prot, int blast, int proj, int fire, int unbreaking, int mending) {
        return armor * 10.0
                + toughness * 4.0
                + kb * 2.0
                + prot * 1.4
                + blast * 0.6
                + proj * 0.4
                + fire * 0.3
                + unbreaking * 0.1
                + mending * 0.2;
    }

    @Test
    void balancedMatchesTheOriginalFormulaOnManyInputs() {
        for (int armor = 0; armor <= 8; armor++) {
            for (int level = 0; level <= 5; level++) {
                double expected = originalScore(armor, 2.0, 0.1, level, 4 - level % 5, level, level / 2, 3, level % 2);
                double actual = ProtectionFocus.BALANCED.score(armor, 2.0, 0.1, level, 4 - level % 5, level, level / 2, 3, level % 2);
                assertEquals(expected, actual, 1.0e-9, "armor=" + armor + " level=" + level);
            }
        }
    }

    @Test
    void blastFocusPrefersBlastProtectionOverProtectionOfTheSameTier() {
        double protection = ProtectionFocus.BLAST.score(8, 3, 0.1, 4, 0, 0, 0, 3, 1);
        double blast = ProtectionFocus.BLAST.score(8, 3, 0.1, 0, 4, 0, 0, 3, 1);
        assertTrue(blast > protection, "blast " + blast + " vs protection " + protection);
    }

    @Test
    void balancedStillPrefersPlainProtectionOverBlast() {
        double protection = ProtectionFocus.BALANCED.score(8, 3, 0.1, 4, 0, 0, 0, 3, 1);
        double blast = ProtectionFocus.BALANCED.score(8, 3, 0.1, 0, 4, 0, 0, 3, 1);
        assertTrue(protection > blast);
    }

    @Test
    void projectileAndFireFocusEachPickTheirOwnEnchantment() {
        assertTrue(ProtectionFocus.PROJECTILE.score(8, 3, 0, 0, 0, 4, 0, 0, 0)
                > ProtectionFocus.PROJECTILE.score(8, 3, 0, 4, 0, 0, 0, 0, 0));
        assertTrue(ProtectionFocus.FIRE.score(8, 3, 0, 0, 0, 0, 4, 0, 0)
                > ProtectionFocus.FIRE.score(8, 3, 0, 4, 0, 0, 0, 0, 0));
    }

    @Test
    void aWholeArmorPointStillOutweighsAnyFocusedEnchantment() {
        // 1 armor point = 10; the best single-enchantment swing for any focus stays under that.
        for (ProtectionFocus focus : ProtectionFocus.values()) {
            double plainHigherTier = focus.score(9, 0, 0, 0, 0, 0, 0, 0, 0);
            double enchantedLowerTier = focus.score(8, 0, 0, 4, 4, 0, 0, 0, 0);
            assertTrue(plainHigherTier > enchantedLowerTier, focus + " must keep armor points ahead of one enchantment");
        }
    }

    @Test
    void wornCheckKeepsTheOriginalNinetyEightPercentDefault() {
        assertFalse(ProtectionFocus.isWorn(980, 1000, 98), "exactly 98% used is not past the line");
        assertTrue(ProtectionFocus.isWorn(981, 1000, 98));
    }

    @Test
    void lowerThresholdsReplaceSooner() {
        assertTrue(ProtectionFocus.isWorn(600, 1000, 50));
        assertFalse(ProtectionFocus.isWorn(400, 1000, 50));
    }

    @Test
    void itemsWithoutDurabilityAreNeverWorn() {
        assertFalse(ProtectionFocus.isWorn(0, 0, 50));
        assertFalse(ProtectionFocus.isWorn(5, -1, 50));
    }
}
