/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.ElytraFlightPhysics.FlightModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.UnaryOperator;

/** Evaluates reachable flight paths through collision shapes from the player's current momentum. */
public final class ElytraEscapePlanner {
    private static final int MAX_HORIZON = 60;
    private static final float[] YAWS = {0, -10, 10, -20, 20, -45, 45, -75, 75, -110, 110, -145, 145, 180};
    private static final float[] PITCHES = {0, -5, 5, -15, -35, -60, -85, 20, 45, 75};

    private ElytraEscapePlanner() {
    }

    public record Trajectory(float damage, int impactTick, boolean unknown, boolean blockedSteering,
                             Vec3 endPosition, double nearestDistanceSquared, int firstCollisionTick) {
        public boolean safe() {
            return !unknown && damage <= 0.0f && !blockedSteering;
        }
    }

    public record EscapeChoice(Rotation rotation, Trajectory trajectory) {
    }

    public interface CollisionWorld {
        boolean loaded(AABB bounds, Vec3 movement);

        Vec3 move(AABB bounds, Vec3 movement);
    }

    public static CollisionWorld world(LocalPlayer player) {
        return new CollisionWorld() {
            @Override
            public boolean loaded(AABB bounds, Vec3 movement) {
                return ElytraImpactDamageUtil.hasLoadedSweep(player, bounds, movement);
            }

            @Override
            public Vec3 move(AABB bounds, Vec3 movement) {
                return ElytraImpactDamageUtil.allowedMovement(player, bounds, movement);
            }
        };
    }

    /** Includes the time to shed dangerous forward momentum and travel the requested clearance. */
    public static int horizon(Vec3 velocity, Rotation heading, double clearance, int minimum, FlightModel physics) {
        Vec3 forward = new Vec3(velocity.x, 0, velocity.z).normalize();
        Rotation reverse = forward.lengthSqr() > 0.0
                ? Rotation.fromRotationVec(forward.scale(-1)) : heading;
        int recovery = 1;
        Vec3 braking = velocity;
        // A rocket can expire or hit the obstacle, so recovery must also be possible while coasting.
        for (; recovery < MAX_HORIZON; recovery++) {
            braking = physics.glide(braking, reverse);
            if (braking.dot(forward) <= 0.3) break;
        }
        double speed = Math.max(0.3, Math.max(velocity.length(),
                physics.afterMove(velocity, heading, 1, physics.possibleBoostTicks()).length()));
        return Mth.clamp(Math.max(minimum, recovery + (int) Math.ceil(clearance / speed)), 1, MAX_HORIZON);
    }

    public static Trajectory forecast(CollisionWorld world, AABB bounds, Vec3 velocity, Rotation heading,
                                      int ticks, FlightModel physics) {
        return forecast(world, bounds, velocity, heading, ticks, physics, null);
    }

    public static Trajectory forecast(CollisionWorld world, AABB bounds, Vec3 velocity, Rotation heading,
                                      int ticks, FlightModel physics, Vec3 avoidPoint) {
        Trajectory shortBoost = forecast(world, bounds, velocity, heading, ticks, physics,
                physics.guaranteedBoostTicks(), avoidPoint);
        if (physics.guaranteedBoostTicks() == physics.possibleBoostTicks()) return shortBoost;
        Trajectory longBoost = forecast(world, bounds, velocity, heading, ticks, physics,
                physics.possibleBoostTicks(), avoidPoint);
        Trajectory worse = longBoost.damage() > shortBoost.damage() ? longBoost : shortBoost;
        int firstCollisionTick = shortBoost.firstCollisionTick() == 0 ? longBoost.firstCollisionTick()
                : longBoost.firstCollisionTick() == 0 ? shortBoost.firstCollisionTick()
                : Math.min(shortBoost.firstCollisionTick(), longBoost.firstCollisionTick());
        return new Trajectory(worse.damage(), worse.impactTick(), shortBoost.unknown() || longBoost.unknown(),
                shortBoost.blockedSteering() || longBoost.blockedSteering(), worse.endPosition(),
                Math.min(shortBoost.nearestDistanceSquared(), longBoost.nearestDistanceSquared()), firstCollisionTick);
    }

    private static Trajectory forecast(CollisionWorld world, AABB bounds, Vec3 velocity, Rotation heading,
                                       int ticks, FlightModel physics, int boostTicks, Vec3 avoidPoint) {
        float damage = 0;
        int impactTick = 0;
        int firstCollisionTick = 0;
        boolean blockedSteering = false;
        double nearestDistance = Double.POSITIVE_INFINITY;
        Vec3 look = heading.directionVector();
        for (int tick = 1; tick <= ticks; tick++) {
            Vec3 attempted = physics.glide(velocity, heading);
            if (!finite(attempted) || !world.loaded(bounds, attempted)) {
                return new Trajectory(damage, impactTick, true, blockedSteering, bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            Vec3 clipped = world.move(bounds, attempted);
            boolean blocked = !Mth.equal(attempted.x, clipped.x)
                    || !Mth.equal(attempted.z, clipped.z)
                    || attempted.y > 0.0 && !Mth.equal(attempted.y, clipped.y) && look.y > 0.0;
            if (blocked && firstCollisionTick == 0) firstCollisionTick = tick;
            blockedSteering |= blocked;
            float impact = ElytraImpactDamageUtil.projectedFlightDamage(velocity, attempted, clipped);
            if (impact > damage) {
                damage = impact;
                impactTick = tick;
            }
            if (avoidPoint != null) {
                Vec3 start = bounds.getCenter();
                double fraction = clipped.lengthSqr() > 0.0
                        ? Mth.clamp(avoidPoint.subtract(start).dot(clipped) / clipped.lengthSqr(), 0.0, 1.0) : 0.0;
                nearestDistance = Math.min(nearestDistance, start.add(clipped.scale(fraction)).distanceToSqr(avoidPoint));
            }
            bounds = bounds.move(clipped);
            if (attempted.y < 0.0 && attempted.y != clipped.y) {
                return new Trajectory(damage, impactTick, false, blockedSteering, bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            velocity = ElytraImpactDamageUtil.postCollisionVelocity(attempted, clipped);
            velocity = physics.afterMove(velocity, heading, tick, boostTicks);
        }
        return new Trajectory(damage, impactTick, false, blockedSteering, bounds.getCenter(), nearestDistance, firstCollisionTick);
    }

    public static EscapeChoice findSaferEscape(CollisionWorld world, AABB bounds, Vec3 velocity,
                                               Rotation intended, Rotation committed, int horizon,
                                               FlightModel physics, Trajectory baseline,
                                               UnaryOperator<Rotation> normalize) {
        if (baseline.safe()) return null;
        EscapeChoice best = null;
        if (committed != null) {
            Trajectory held = forecast(world, bounds, velocity, committed, horizon, physics);
            if (better(held, baseline)) {
                best = new EscapeChoice(committed, held);
                if (held.safe()) return best;
            }
        }
        Vec3 forward = new Vec3(velocity.x, 0, velocity.z);
        float yaw = forward.lengthSqr() > 1.0E-8 ? Rotation.fromRotationVec(forward).yaw() : intended.yaw();
        for (float yawOffset : YAWS) {
            for (int pitchIndex = -1; pitchIndex < PITCHES.length; pitchIndex++) {
                float pitch = pitchIndex < 0 ? intended.pitch() : PITCHES[pitchIndex];
                Rotation candidate = normalize.apply(new Rotation(yaw + yawOffset, pitch));
                Trajectory outcome = forecast(world, bounds, velocity, candidate, horizon, physics);
                if (!better(outcome, baseline)) continue;
                if (best == null || better(outcome, best.trajectory())
                        || (outcome.damage() == best.trajectory().damage()
                        && outcome.blockedSteering() == best.trajectory().blockedSteering()
                        && outcome.firstCollisionTick() == best.trajectory().firstCollisionTick()
                        && committed == null && deviation(candidate, intended) < deviation(best.rotation(), intended))) {
                    best = new EscapeChoice(candidate, outcome);
                }
            }
        }
        return best;
    }

    private static boolean better(Trajectory alternative, Trajectory original) {
        if (alternative.unknown()) return false;
        if (original.unknown()) return true;
        if (meaningfulReduction(original.damage(), alternative.damage())) return true;
        if (meaningfulReduction(alternative.damage(), original.damage())) return false;
        if (original.blockedSteering() && !alternative.blockedSteering()) return true;
        return original.blockedSteering() && alternative.blockedSteering()
                && alternative.firstCollisionTick() > original.firstCollisionTick();
    }

    public static boolean meaningfulReduction(float originalDamage, float replacementDamage) {
        return Float.isFinite(originalDamage) && Float.isFinite(replacementDamage)
                && originalDamage - replacementDamage > 1.0E-4f;
    }

    private static double deviation(Rotation candidate, Rotation intended) {
        return Math.hypot(Mth.wrapDegrees(candidate.yaw() - intended.yaw()), candidate.pitch() - intended.pitch());
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
