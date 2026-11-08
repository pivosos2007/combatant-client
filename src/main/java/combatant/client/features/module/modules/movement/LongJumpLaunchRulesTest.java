package combatant.client.features.module.modules.movement;

public final class LongJumpLaunchRulesTest {
    private static int checks;
    private static void test(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        test(LongJumpLaunchRules.boatImpulse(0) == 1.0, "Boat Y regressed");
        test(LongJumpLaunchRules.boatImpulse(1.1) == 1.1, "Boat overwritten upward velocity");
        test(LongJumpLaunchRules.waterImpulse(0) == 0.56, "Water Y regressed");
        test(LongJumpLaunchRules.waterImpulse(0.7) == 0.7, "Water overwritten upward velocity");
        test(LongJumpLaunchRules.shulkerImpulse(0) == 0.9, "Shulker Y regressed");
        test(LongJumpLaunchRules.shulkerImpulse(1.0) == 1.0, "Shulker overwritten upward velocity");
        test(LongJumpLaunchRules.vulcanSlimeBounceVelocity(0.8) == 1.42, "Slime Y regressed");
        test(LongJumpLaunchRules.vulcanSlimeBounceVelocity(-0.2) == -0.2, "False slime bounce");
        System.out.println("LongJumpLaunchRulesTest: " + checks + " passed");
    }
}
