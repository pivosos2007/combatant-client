/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.anchormacro;

/**
 * Pure decision rules of the anchor macros: given what one anchor looks like right now (placed or not, how many
 * charges) and what the player carries, which step comes next, and what a pre-built step of the sequence has to do
 * when the world is not in the state it expected.
 */
public final class AnchorMacroPlanner {
    /** Charges a respawn anchor can hold. */
    public static final int MAX_CHARGE = 4;

    private AnchorMacroPlanner() {
    }

    /** Ordered: a step of the sequence skips when the world is already further along. */
    public enum Step {
        PLACE,
        CHARGE,
        DETONATE,
        ABORT
    }

    public enum AbortReason {
        NONE,
        NO_ANCHOR_ITEM,
        NO_GLOWSTONE,
        NO_DETONATOR,
        /** Glowstone in the offhand would charge the anchor again instead of letting it explode. */
        OFFHAND_FUEL
    }

    public enum Disposition {
        /** The step has work to do. */
        RUN,
        /** The world is already past this step. */
        SKIP,
        /** The world went backwards (anchor vanished, charge lost): the sequence is stale. */
        DRIFT,
        /** A needed item is missing. */
        ABORT
    }

    /**
     * @param anchorPresent    a respawn anchor stands at the position
     * @param charge           charge of that anchor, 0 when absent
     * @param targetCharges    charges wanted before detonating (clamped to 1..4)
     * @param offhandHoldsFuel the offhand holds glowstone
     */
    public record State(boolean anchorPresent, int charge, int targetCharges, boolean hasAnchorItem,
                        boolean hasGlowstone, boolean hasDetonator, boolean offhandHoldsFuel) {
    }

    public record Decision(Step step, AbortReason reason) {
        static Decision of(Step step) {
            return new Decision(step, AbortReason.NONE);
        }

        static Decision abort(AbortReason reason) {
            return new Decision(Step.ABORT, reason);
        }
    }

    /** The step to take next for one anchor. */
    public static Decision next(State state) {
        if (!state.anchorPresent()) {
            return state.hasAnchorItem() ? Decision.of(Step.PLACE) : Decision.abort(AbortReason.NO_ANCHOR_ITEM);
        }
        int target = clampTarget(state.targetCharges());
        if (state.charge() < target) {
            return state.hasGlowstone() ? Decision.of(Step.CHARGE) : Decision.abort(AbortReason.NO_GLOWSTONE);
        }
        if (!state.hasDetonator()) return Decision.abort(AbortReason.NO_DETONATOR);
        if (state.offhandHoldsFuel() && state.charge() < MAX_CHARGE) return Decision.abort(AbortReason.OFFHAND_FUEL);
        return Decision.of(Step.DETONATE);
    }

    /** What the sequence step {@code expected} does when the world reports {@code decision}. */
    public static Disposition disposition(Step expected, Decision decision) {
        if (decision.step() == Step.ABORT) return Disposition.ABORT;
        if (decision.step() == expected) return Disposition.RUN;
        return decision.step().ordinal() > expected.ordinal() ? Disposition.SKIP : Disposition.DRIFT;
    }

    public static int clampTarget(int target) {
        return Math.max(1, Math.min(MAX_CHARGE, target));
    }
}
