/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.core.BlockPos;

import java.util.Locale;

public final class AutoCrystalPlaceScanDebug {
    private int ok;
    private int targetDistance;
    private int canPlace;
    private int invalidBase;
    private int blockedAir;
    private int blockedEntity;
    private int interact;
    private int damage;
    private int selfDamage;
    private float maxRawDamage;
    private float maxRawSelfDamage;
    private BlockPos maxRawDamagePos;
    private ExplosionDamageUtil.DamageDebug maxRawDamageDebug;

    void recordOk() {
        ok++;
    }

    void recordTargetDistance() {
        targetDistance++;
    }

    void recordInteractFail() {
        interact++;
    }

    void recordDamageReject() {
        damage++;
    }

    void recordSelfDamageReject() {
        selfDamage++;
    }

    void recordPlaceValidation(AutoCrystalPlaceValidationResult result) {
        canPlace++;
        switch (result) {
            case INVALID_BASE -> invalidBase++;
            case BLOCKED_AIR -> blockedAir++;
            case BLOCKED_ENTITY -> blockedEntity++;
            default -> {
            }
        }
    }

    boolean recordRawDamage(BlockPos pos, float damage, float selfDamage) {
        if (damage >= maxRawDamage) {
            maxRawDamage = damage;
            maxRawSelfDamage = selfDamage;
            maxRawDamagePos = pos;
            maxRawDamageDebug = null;
            return true;
        }
        return false;
    }

    void recordRawDamageDebug(ExplosionDamageUtil.DamageDebug damageDebug) {
        maxRawDamageDebug = damageDebug;
    }

    public int selfDamageRejects() {
        return selfDamage;
    }

    @Override
    public String toString() {
        return "ok=" + ok
                + " targetDist=" + targetDistance
                + " canPlace=" + canPlace
                + " base=" + invalidBase
                + " air=" + blockedAir
                + " entity=" + blockedEntity
                + " interact=" + interact
                + " damage=" + damage
                + " self=" + selfDamage
                + " maxDmg=" + String.format(Locale.ROOT, "%.2f", maxRawDamage)
                + " maxSelf=" + String.format(Locale.ROOT, "%.2f", maxRawSelfDamage)
                + " maxPos=" + maxRawDamagePos
                + " dmgDbg=" + (maxRawDamageDebug == null
                ? "null"
                : "reason=" + maxRawDamageDebug.reason()
                + " exp=" + String.format(Locale.ROOT, "%.3f", maxRawDamageDebug.exposure())
                + " dist=" + String.format(Locale.ROOT, "%.3f", maxRawDamageDebug.distanceFactor())
                + " dSq=" + String.format(Locale.ROOT, "%.3f", maxRawDamageDebug.distanceSq())
                + " imp=" + String.format(Locale.ROOT, "%.3f", maxRawDamageDebug.impact())
                + " pre=" + String.format(Locale.ROOT, "%.2f", maxRawDamageDebug.preDamage())
                + " armor=" + String.format(Locale.ROOT, "%.2f", maxRawDamageDebug.postArmorDamage())
                + " resist=" + String.format(Locale.ROOT, "%.2f", maxRawDamageDebug.postResistanceDamage())
                + " final=" + String.format(Locale.ROOT, "%.2f", maxRawDamageDebug.finalDamage())
                + " box=" + maxRawDamageDebug.box()
                + " rays=" + maxRawDamageDebug.clearRays() + "/" + maxRawDamageDebug.totalRays()
                + " blockerPos=" + maxRawDamageDebug.blockerPos()
                + " blockerBlock=" + maxRawDamageDebug.blockerBlock()
                + " blockerSample=" + maxRawDamageDebug.blockerSample()
                + " blockerAtSample=" + maxRawDamageDebug.blockerAtSampleCell()
                + " blockerAtExplosion=" + maxRawDamageDebug.blockerAtExplosionCell());
    }
}
