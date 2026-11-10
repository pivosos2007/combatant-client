/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

public final class Vulcan297LongJumpControllerTest {
    private static int checks;
    private static void check(boolean valid, String why) {
        checks++;
        if (!valid) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        Vulcan297LongJumpController c = new Vulcan297LongJumpController();
        check(c.beginShulkerLaunch(100), "GUI launch cannot be restricted by ground/velocity prechecks");
        check(c.isManaging(100), "shulker launch not active");
        check(!c.beginShulkerLaunch(101), "double impulse in same flight");
        c.onTick(103, false);
        check(c.isManaging(103), "airborne still active");
        c.onTick(105, true);
        check(!c.isManaging(105), "landed flight not cleared");
        check(!c.beginShulkerLaunch(106), "rearm not respected");
        check(c.beginShulkerLaunch(117), "rearmed launch blocked");
        c.onServerCorrection(120);
        check(!c.beginShulkerLaunch(179), "correction cooldown bypass");
        check(c.beginShulkerLaunch(180), "correction cooldown never released");
        System.out.println("Vulcan297LongJumpControllerTest: " + checks + " passed");
    }
}
