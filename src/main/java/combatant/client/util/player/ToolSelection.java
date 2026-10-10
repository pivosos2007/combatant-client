/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

/**
 * Picks the best hotbar slot from numbers the caller already extracted, so the choice rules can be
 * unit tested without a Minecraft bootstrap.
 *
 * <p>Usage per decision: {@link #reset}, one {@link #offer} per candidate slot in ascending order,
 * then {@link #result}. The instance holds no allocations between decisions; keep one per module and
 * call it from the client thread only.</p>
 *
 * <p>Rules: a candidate must beat the baseline strictly (bare hand for tools, bare hand attack for
 * weapons); the first strictly better candidate wins over later equal ones; the held slot wins any
 * tie so equal tools never cause a swap; a durability guard drops worn tools outright.</p>
 */
public final class ToolSelection {

    public static final int NONE = -1;

    /** Attack damage and attack speed of a bare hand, before item modifiers. */
    public static final double HAND_DAMAGE = 1.0;
    public static final double HAND_ATTACK_SPEED = 4.0;

    private static final float EPSILON = 1.0e-4f;

    private int selectedSlot;
    private float minDurability;
    private float preferredBonus;

    private int bestSlot;
    private float bestScore;
    private boolean selectedQualifies;
    private float selectedScore;

    /**
     * @param baseline       a candidate must score strictly above this to be worth a swap
     * @param selectedSlot   slot currently held; it wins ties
     * @param minDurability  fraction in [0, 1]; candidates at or below it are skipped, 0 disables the guard
     * @param preferredBonus fraction added to the score of preferred candidates (0.5 = +50%)
     */
    public void reset(float baseline, int selectedSlot, float minDurability, float preferredBonus) {
        this.selectedSlot = selectedSlot;
        this.minDurability = minDurability;
        this.preferredBonus = preferredBonus;
        this.bestSlot = NONE;
        this.bestScore = baseline;
        this.selectedQualifies = false;
        this.selectedScore = 0.0f;
    }

    /** @param durabilityLeft fraction in [0, 1]; use 1 for items that cannot break */
    public void offer(int slot, float score, float durabilityLeft, boolean preferred) {
        if (minDurability > 0.0f && durabilityLeft <= minDurability) return;
        float effective = preferred ? score * (1.0f + preferredBonus) : score;

        if (slot == selectedSlot) {
            selectedQualifies = true;
            selectedScore = effective;
        }
        if (effective > bestScore + EPSILON) {
            bestScore = effective;
            bestSlot = slot;
        }
    }

    /** Slot to hold, or {@link #NONE} when no candidate beats the baseline. */
    public int result() {
        if (bestSlot == NONE) return NONE;
        if (selectedQualifies && selectedScore >= bestScore - EPSILON) return selectedSlot;
        return bestSlot;
    }

    /** Melee damage per second proxy: total damage times total attack speed. */
    public static float weaponScore(double bonusDamage, double bonusAttackSpeed) {
        double damage = HAND_DAMAGE + bonusDamage;
        double speed = Math.max(0.0, HAND_ATTACK_SPEED + bonusAttackSpeed);
        return (float) (damage * speed);
    }

    /** Fraction of durability left; items without durability count as fully intact. */
    public static float durabilityLeft(int damage, int maxDamage) {
        if (maxDamage <= 0) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, (maxDamage - damage) / (float) maxDamage));
    }
}
