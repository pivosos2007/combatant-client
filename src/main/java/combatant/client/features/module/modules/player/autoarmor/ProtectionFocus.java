/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.autoarmor;

/**
 * Enchantment weighting for AutoArmor's piece scoring. {@link #BALANCED} reproduces the weights the
 * module shipped with; the other entries lean the choice toward one damage source when two pieces of
 * the same tier differ only by enchantments.
 *
 * <p>Base stats still carry most of the score (one armor point is worth {@value #ARMOR_WEIGHT}, a
 * whole Protection IV about 6), so a focus mostly breaks ties between pieces of one tier.</p>
 */
public enum ProtectionFocus {
    BALANCED(1.4, 0.6, 0.4, 0.3),
    BLAST(0.6, 1.8, 0.2, 0.2),
    PROJECTILE(0.6, 0.2, 1.8, 0.2),
    FIRE(0.6, 0.2, 0.2, 1.8);

    public static final double ARMOR_WEIGHT = 10.0;
    private static final double TOUGHNESS_WEIGHT = 4.0;
    private static final double KNOCKBACK_WEIGHT = 2.0;
    private static final double UNBREAKING_WEIGHT = 0.1;
    private static final double MENDING_WEIGHT = 0.2;

    private final double protection;
    private final double blast;
    private final double projectile;
    private final double fire;

    ProtectionFocus(double protection, double blast, double projectile, double fire) {
        this.protection = protection;
        this.blast = blast;
        this.projectile = projectile;
        this.fire = fire;
    }

    public double score(double armor, double toughness, double knockbackResistance,
                        int protectionLevel, int blastLevel, int projectileLevel, int fireLevel,
                        int unbreakingLevel, int mendingLevel) {
        return armor * ARMOR_WEIGHT
                + toughness * TOUGHNESS_WEIGHT
                + knockbackResistance * KNOCKBACK_WEIGHT
                + protectionLevel * protection
                + blastLevel * blast
                + projectileLevel * projectile
                + fireLevel * fire
                + unbreakingLevel * UNBREAKING_WEIGHT
                + mendingLevel * MENDING_WEIGHT;
    }

    /** True when more than {@code percent} of the piece's durability is used up. */
    public static boolean isWorn(int damage, int maxDamage, int percent) {
        if (maxDamage <= 0) return false;
        return (double) damage / maxDamage > percent / 100.0;
    }
}
