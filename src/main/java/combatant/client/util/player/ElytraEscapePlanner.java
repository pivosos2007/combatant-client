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

/** Evaluates swept collision paths under gliding and attached-firework acceleration. */
public final class ElytraEscapePlanner {
    private static final int MAX_HORIZON = 40;
    private static final float[] YAWS = {0, -12, 12, -28, 28, -55, 55, -90, 90, 180};
    private static final float[] PITCHES = {0, -12, -30, -60, 12, 35, 60};
    private static final double MIN_CLEARANCE = 0.12;
    private static final double MAX_CLEARANCE = 0.4;
    private static final double HARD_LANDING_VERTICAL_SPEED = 0.7;
    private static final double FAST_GROUND_APPROACH_EXPOSURE = 0.30;
    private static final double RAPID_ASCENT_SPEED = 0.17;

    private ElytraEscapePlanner() {
    }

    /** Damage sums projected wall impacts; firstCollisionTick records unsafe contacts. */
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

    public static int horizon(Vec3 velocity, Rotation heading, double clearance, int minimum, FlightModel physics) {
        Vec3 forward = new Vec3(velocity.x, 0, velocity.z).normalize();
        Rotation reverse = forward.lengthSqr() > 0.0
                ? Rotation.fromRotationVec(forward.scale(-1)) : heading;
        int recovery = 1;
        Vec3 braking = velocity;
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
        Trajectory result = forecast(world, bounds, velocity, heading, ticks, physics,
                physics.guaranteedBoostTicks(), false, avoidPoint);
        if (physics.possibleBoostTicks() != physics.guaranteedBoostTicks()) {
            result = worst(result, forecast(world, bounds, velocity, heading, ticks, physics,
                    physics.possibleBoostTicks(), false, avoidPoint));
        }
        if (physics.possibleBoostTicks() > 0) {
            result = worst(result, forecast(world, bounds, velocity, heading, ticks, physics,
                    physics.possibleBoostTicks(), true, avoidPoint));
            if (physics.guaranteedBoostTicks() > 0
                    && physics.possibleBoostTicks() != physics.guaranteedBoostTicks()) {
                result = worst(result, forecast(world, bounds, velocity, heading, ticks, physics,
                        physics.guaranteedBoostTicks(), true, avoidPoint));
            }
        }
        return result;
    }

    private static Trajectory forecast(CollisionWorld world, AABB bounds, Vec3 velocity, Rotation heading,
                                       int ticks, FlightModel physics, int boostTicks, boolean boostBeforeMove,
                                       Vec3 avoidPoint) {
        float totalDamage = 0;
        int impactTick = 0;
        int firstCollisionTick = 0;
        boolean blockedSteering = false;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int tick = 1; tick <= ticks; tick++) {
            if (boostBeforeMove) velocity = physics.afterMove(velocity, heading, tick, boostTicks);
            Vec3 attempted = physics.glide(velocity, heading);
            if (!finite(attempted) || !world.loaded(bounds, attempted)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            Vec3 clipped = world.move(bounds, attempted);
            if (!finite(clipped)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            boolean horizontalContact = !Mth.equal(attempted.x, clipped.x) || !Mth.equal(attempted.z, clipped.z);
            boolean verticalContact = !Mth.equal(attempted.y, clipped.y);
            double margin = Mth.clamp(MIN_CLEARANCE + attempted.horizontalDistance() * 0.03,
                    MIN_CLEARANCE, MAX_CLEARANCE);
            boolean fastGroundApproach = attempted.y < 0.0
                    && (attempted.y <= -HARD_LANDING_VERTICAL_SPEED
                    || -attempted.y * attempted.horizontalDistance() >= FAST_GROUND_APPROACH_EXPOSURE);
            double lowerClearance = fastGroundApproach ? MIN_CLEARANCE : 0.0;
            double upperClearance = attempted.y >= RAPID_ASCENT_SPEED ? MIN_CLEARANCE : 0.0;
            AABB clearanceBounds = new AABB(
                    bounds.minX - margin, bounds.minY - lowerClearance, bounds.minZ - margin,
                    bounds.maxX + margin, bounds.maxY + upperClearance, bounds.maxZ + margin
            );
            if (!world.loaded(clearanceBounds, attempted)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            Vec3 clearanceClipped = world.move(clearanceBounds, attempted);
            if (!finite(clearanceClipped)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            boolean horizontalClearanceContact = !Mth.equal(attempted.x, clearanceClipped.x)
                    || !Mth.equal(attempted.z, clearanceClipped.z);
            boolean verticalClearanceContact = (lowerClearance > 0.0 || upperClearance > 0.0)
                    && !Mth.equal(attempted.y, clearanceClipped.y);
            boolean unsafeLanding = verticalContact && fastGroundApproach;
            boolean contact = horizontalContact || horizontalClearanceContact
                    || verticalClearanceContact || unsafeLanding;
            if (contact && firstCollisionTick == 0) firstCollisionTick = tick;
            blockedSteering |= contact;

            float impact = ElytraImpactDamageUtil.projectedFlightDamage(velocity, attempted, clipped);
            if (impact > 0.0f) {
                totalDamage += impact;
                if (impactTick == 0) impactTick = tick;
            }
            if (avoidPoint != null) {
                Vec3 start = bounds.getCenter();
                double fraction = clipped.lengthSqr() > 0.0
                        ? Mth.clamp(avoidPoint.subtract(start).dot(clipped) / clipped.lengthSqr(), 0.0, 1.0) : 0.0;
                nearestDistance = Math.min(nearestDistance, start.add(clipped.scale(fraction)).distanceToSqr(avoidPoint));
            }
            bounds = bounds.move(clipped);
            if (verticalContact && attempted.y < 0.0) {
                return new Trajectory(totalDamage, impactTick, false, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick);
            }
            velocity = ElytraImpactDamageUtil.postCollisionVelocity(attempted, clipped);
            if (!boostBeforeMove) velocity = physics.afterMove(velocity, heading, tick, boostTicks);
        }
        return new Trajectory(totalDamage, impactTick, false, blockedSteering,
                bounds.getCenter(), nearestDistance, firstCollisionTick);
    }

    private static Trajectory worst(Trajectory first, Trajectory second) {
        Trajectory worse = compareRisk(first, second) >= 0 ? first : second;
        int firstContact = first.firstCollisionTick() == 0 ? second.firstCollisionTick()
                : second.firstCollisionTick() == 0 ? first.firstCollisionTick()
                : Math.min(first.firstCollisionTick(), second.firstCollisionTick());
        return new Trajectory(Math.max(first.damage(), second.damage()), worse.impactTick(),
                first.unknown() || second.unknown(), first.blockedSteering() || second.blockedSteering(),
                worse.endPosition(), Math.min(first.nearestDistanceSquared(), second.nearestDistanceSquared()),
                firstContact);
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
                if (best == null || compareRisk(outcome, best.trajectory()) < 0
                        || (compareRisk(outcome, best.trajectory()) == 0
                        && deviation(candidate, intended) < deviation(best.rotation(), intended))) {
                    best = new EscapeChoice(candidate, outcome);
                    if (outcome.safe() && deviation(candidate, intended) <= 12.0) return best;
                }
            }
        }
        return best;
    }

    private static boolean better(Trajectory alternative, Trajectory original) {
        if (alternative.unknown()) return false;
        if (original.unknown()) return alternative.safe();
        return compareRisk(alternative, original) < 0;
    }

    private static int compareRisk(Trajectory first, Trajectory second) {
        if (first.unknown() != second.unknown()) return first.unknown() ? 1 : -1;
        if (first.safe() != second.safe()) return first.safe() ? -1 : 1;
        if (meaningfulReduction(first.damage(), second.damage())) return 1;
        if (meaningfulReduction(second.damage(), first.damage())) return -1;
        if (first.blockedSteering() != second.blockedSteering()) return first.blockedSteering() ? 1 : -1;
        int a = first.firstCollisionTick() == 0 ? Integer.MAX_VALUE : first.firstCollisionTick();
        int b = second.firstCollisionTick() == 0 ? Integer.MAX_VALUE : second.firstCollisionTick();
        return Integer.compare(b, a);
    }

    public static boolean meaningfulReduction(float originalDamage, float replacementDamage) {
        return Float.isFinite(originalDamage) && Float.isFinite(replacementDamage)
                && originalDamage - replacementDamage > 1.0E-4f;
    }

    private static double deviation(Rotation candidate, Rotation intended) {
        return Math.hypot(Mth.wrapDegrees(candidate.yaw() - intended.yaw()), candidate.pitch() - intended.pitch());
    }

    private static boolean finite(Vec3 vector) {
        return vector != null && Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
