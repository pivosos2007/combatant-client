/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.simulation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import combatant.client.features.relations.StaffTracker;
import combatant.client.util.player.effect.StatusEffectAccess;

final class RemotePlayerObservation {

    private static final int HOVER_CONFIRM_TICKS = 3;
    private static final int FLIGHT_CONFIRM_TICKS = 4;
    private static final double STABLE_VERTICAL_SPEED = 0.018;
    private static final double FROZEN_HORIZONTAL_SPEED = 0.035;
    private static final double CONTROLLED_HORIZONTAL_SPEED = 0.065;
    private Vec3 lastPos;
    private Vec3 measuredVelocity = Vec3.ZERO;
    private int lastAge = Integer.MIN_VALUE;
    private int hoverTicks;
    private int flightLikeTicks;
    private MotionKind motionKind = MotionKind.NORMAL;
    private boolean serverFlightAllowed;
    private boolean survivalFlightHeuristic;
    private boolean holdVerticalMotion;
    RemotePlayerObservation(Player player) {
        this.lastPos = player.position();
    }

    private static boolean hasActiveFlight(Player player) {
        Abilities abilities = player.getAbilities();
        return abilities != null && abilities.flying;
    }

    private static boolean hasServerFlightRight(Player player) {
        Abilities abilities = player.getAbilities();
        if (abilities != null && (abilities.mayfly || abilities.instabuild || abilities.flying)) {
            return true;
        }

        GameType gameMode = resolveGameMode(player);
        return gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR;
    }

    private static GameType resolveGameMode(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getConnection() != null) {
            PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
            if (entry != null && entry.getGameMode() != null) {
                return entry.getGameMode();
            }
        }

        StaffTracker.StaffInfo info = StaffTracker.all().get(player.getUUID());
        if (info != null && info.gm != null) {
            return info.gm;
        }

        return GameType.DEFAULT_MODE;
    }

    void update(Player player) {
        int age = player.tickCount;
        if (age == lastAge) {
            return;
        }
        lastAge = age;

        Vec3 current = player.position();
        measuredVelocity = lastPos == null
                ? current.subtract(player.xo, player.yo, player.zo)
                : current.subtract(lastPos);
        lastPos = current;

        boolean gliding = player.isFallFlying();
        boolean airborne = !player.onGround() && !player.isPassenger();
        boolean fluid = player.isInWater() || player.isInLava();
        boolean hasLevitation = StatusEffectAccess.has(player, MobEffects.LEVITATION);
        boolean hasSlowFalling = StatusEffectAccess.has(player, MobEffects.SLOW_FALLING);
        boolean vanillaSpecialVertical = fluid || hasLevitation || hasSlowFalling;

        double horizontalSpeed = Math.sqrt(measuredVelocity.x * measuredVelocity.x + measuredVelocity.z * measuredVelocity.z);
        boolean stableVertical = Math.abs(measuredVelocity.y) <= STABLE_VERTICAL_SPEED;
        boolean frozenHorizontal = horizontalSpeed <= FROZEN_HORIZONTAL_SPEED;
        boolean controlledHorizontal = horizontalSpeed >= CONTROLLED_HORIZONTAL_SPEED;

        serverFlightAllowed = hasServerFlightRight(player);
        boolean activeAbilityFlight = hasActiveFlight(player);

        if (airborne && !gliding && !vanillaSpecialVertical && stableVertical) {
            hoverTicks++;
        } else {
            hoverTicks = 0;
        }

        survivalFlightHeuristic = airborne
                && !gliding
                && !vanillaSpecialVertical
                && stableVertical
                && controlledHorizontal
                && !serverFlightAllowed;

        if (activeAbilityFlight || serverFlightAllowed && airborne && stableVertical || survivalFlightHeuristic) {
            flightLikeTicks++;
        } else if (!airborne || gliding || vanillaSpecialVertical) {
            flightLikeTicks = 0;
        } else {
            flightLikeTicks = Math.max(0, flightLikeTicks - 1);
        }

        motionKind = resolveMotionKind(player, gliding, airborne, stableVertical, frozenHorizontal, activeAbilityFlight);
        holdVerticalMotion = motionKind == MotionKind.FROZEN_AIR
                || motionKind == MotionKind.HOVER
                || stableVertical && (motionKind == MotionKind.SERVER_FLIGHT || motionKind == MotionKind.SURVIVAL_FLIGHT);
    }

    MotionKind motionKind() {
        return motionKind;
    }

    Vec3 measuredVelocity() {
        return measuredVelocity;
    }

    boolean isFlightLike() {
        return motionKind == MotionKind.SERVER_FLIGHT
                || motionKind == MotionKind.SURVIVAL_FLIGHT
                || motionKind == MotionKind.HOVER
                || motionKind == MotionKind.FROZEN_AIR;
    }

    boolean shouldHoldVerticalMotion() {
        return holdVerticalMotion;
    }

    private MotionKind resolveMotionKind(Player player,
                                         boolean gliding,
                                         boolean airborne,
                                         boolean stableVertical,
                                         boolean frozenHorizontal,
                                         boolean activeAbilityFlight) {
        if (gliding) {
            return MotionKind.GLIDING;
        }

        if (!airborne) {
            return MotionKind.NORMAL;
        }

        if (hoverTicks >= HOVER_CONFIRM_TICKS && frozenHorizontal) {
            return MotionKind.FROZEN_AIR;
        }

        if (activeAbilityFlight || serverFlightAllowed && flightLikeTicks >= 1) {
            return MotionKind.SERVER_FLIGHT;
        }

        if (flightLikeTicks >= FLIGHT_CONFIRM_TICKS && survivalFlightHeuristic) {
            return MotionKind.SURVIVAL_FLIGHT;
        }

        if (hoverTicks >= HOVER_CONFIRM_TICKS && stableVertical) {
            return MotionKind.HOVER;
        }

        return MotionKind.NORMAL;
    }

    enum MotionKind {
        NORMAL,
        GLIDING,
        SERVER_FLIGHT,
        SURVIVAL_FLIGHT,
        HOVER,
        FROZEN_AIR
    }
}
