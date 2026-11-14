/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.combat;

import net.minecraft.util.Mth;
import combatant.client.util.player.ElytraEscapePlanner;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Predicts an unglided descent against a translating target box, in blocks and ticks. */
public final class SmashTrajectory {
    private SmashTrajectory() {}

    public enum Prediction { REACHABLE, BLOCKED, UNREACHABLE }

    public record ContactForecast(Prediction prediction, int contactTicks) {}

    public static boolean canReach(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth, Vec3 velocity,
                                   AABB target, Vec3 targetVelocity, double initialFall, double minimumFall,
                                   double minimumDownSpeed, double minimumApproachSpeed, int ticks,
                                   double maximumHorizontalGap, double reach, double gravity) {
        return predict(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed,
                ticks, maximumHorizontalGap, reach, gravity) == Prediction.REACHABLE;
    }

    public static Prediction predict(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth, Vec3 velocity,
                                     AABB target, Vec3 targetVelocity, double initialFall, double minimumFall,
                                     double minimumDownSpeed, double minimumApproachSpeed, int ticks,
                                     double maximumHorizontalGap, double reach, double gravity) {
        return forecast(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed, ticks,
                maximumHorizontalGap, reach, gravity).prediction();
    }

    public static Prediction predictReportable(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth,
                                               Vec3 velocity, AABB target, Vec3 targetVelocity, double initialFall,
                                               double minimumFall, double minimumDownSpeed, double minimumApproachSpeed,
                                               int ticks, double maximumHorizontalGap, double reach, double gravity) {
        return forecastReportable(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed, ticks,
                maximumHorizontalGap, reach, gravity).prediction();
    }

    public static ContactForecast forecast(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth,
                                          Vec3 velocity, AABB target, Vec3 targetVelocity, double initialFall,
                                          double minimumFall, double minimumDownSpeed, double minimumApproachSpeed,
                                          int ticks, double maximumHorizontalGap, double reach, double gravity) {
        return predictInternal(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed, ticks,
                maximumHorizontalGap, reach, gravity, false, 1, false);
    }

    public static ContactForecast forecastReportable(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth,
                                                    Vec3 velocity, AABB target, Vec3 targetVelocity, double initialFall,
                                                    double minimumFall, double minimumDownSpeed, double minimumApproachSpeed,
                                                    int ticks, double maximumHorizontalGap, double reach, double gravity) {
        return forecastReportable(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed, ticks,
                maximumHorizontalGap, reach, gravity, 1);
    }

    public static ContactForecast forecastReportable(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth,
                                                    Vec3 velocity, AABB target, Vec3 targetVelocity, double initialFall,
                                                    double minimumFall, double minimumDownSpeed, double minimumApproachSpeed,
                                                    int ticks, double maximumHorizontalGap, double reach, double gravity,
                                                    int earliestContactTick) {
        return predictInternal(player, feet, eyeHeight, halfWidth, velocity, target, targetVelocity,
                initialFall, minimumFall, minimumDownSpeed, minimumApproachSpeed, ticks,
                maximumHorizontalGap, reach, gravity, true, Math.max(1, earliestContactTick), earliestContactTick > 1);
    }

    private static ContactForecast predictInternal(LocalPlayer player, Vec3 feet, double eyeHeight, double halfWidth,
                                              Vec3 velocity, AABB target, Vec3 targetVelocity, double initialFall,
                                              double minimumFall, double minimumDownSpeed, double minimumApproachSpeed,
                                              int ticks, double maximumHorizontalGap, double reach, double gravity,
                                              boolean reportableContact, int earliestContactTick,
                                              boolean requireReportedContact) {
        if (velocity.y > -minimumDownSpeed || feet.y <= target.maxY) return new ContactForecast(Prediction.UNREACHABLE, -1);
        double dx = Mth.clamp(feet.x, target.minX, target.maxX) - feet.x;
        double dz = Mth.clamp(feet.z, target.minZ, target.maxZ) - feet.z;
        double gap = Math.sqrt(dx * dx + dz * dz);
        boolean overlapping = feet.x + halfWidth >= target.minX && feet.x - halfWidth <= target.maxX
                && feet.z + halfWidth >= target.minZ && feet.z - halfWidth <= target.maxZ;
        double horizontalApproach = gap > 0.0
                ? ((velocity.x - targetVelocity.x) * dx + (velocity.z - targetVelocity.z) * dz) / gap
                : 0.0;
        boolean slowHorizontalApproach = !overlapping && gap > 0.0
                && horizontalApproach < minimumApproachSpeed;
        boolean verticalContact = slowHorizontalApproach && reportableContact
                && withinVerticalContactRange(gap, maximumHorizontalGap, reach);
        if (slowHorizontalApproach && !verticalContact) return new ContactForecast(Prediction.UNREACHABLE, -1);

        // Removing the elytra restores the standing collision volume. Ray visibility alone
        // does not predict a fall onto leaves, fences, or a block overhanging the target.
        if (player == null) return new ContactForecast(Prediction.BLOCKED, -1);
        ElytraEscapePlanner.CollisionWorld fluidSafety = ElytraEscapePlanner.world(player, true);
        if (!clearStandingVolume(player, feet, halfWidth, eyeHeight, fluidSafety))
            return new ContactForecast(Prediction.BLOCKED, -1);
        Vec3 eye = feet.add(0, eyeHeight, 0);
        double fall = initialFall;
        // For a nearly vertical hit, only commit to an imminent contact. A long
        // prediction horizon would unequip the elytra far above the target.
        int horizon = verticalContact ? Math.min(ticks, 8) : ticks;
        for (int tick = 0; tick < horizon; tick++) {
            Vec3 next = eye.add(velocity);
            double drop = Math.max(0, -velocity.y);
            double contactFraction = Double.NaN;
            if (fall + drop >= minimumFall && velocity.y < targetVelocity.y) {
                double from = drop > 0 ? Mth.clamp((minimumFall - fall) / drop, 0, 1) : 0;
                Vec3 relativeStart = eye.subtract(targetVelocity.scale(tick));
                Vec3 relativeStep = velocity.subtract(targetVelocity);
                double low = from;
                double high = 1;
                // Squared distance to a convex box is convex along each tick's linear segment.
                for (int iteration = 0; iteration < 32; iteration++) {
                    double a = (2 * low + high) / 3;
                    double b = (low + 2 * high) / 3;
                    if (distanceSquared(target, relativeStart.add(relativeStep.scale(a)))
                            <= distanceSquared(target, relativeStart.add(relativeStep.scale(b)))) high = b;
                    else low = a;
                }
                double closestFraction = (low + high) / 2;
                Vec3 closest = relativeStart.add(relativeStep.scale(closestFraction));
                if (withinContact(target, closest, maximumHorizontalGap, reach)) {
                    // Pick the first legal contact, not the closest point later
                    // in the tick. A collision between those instants must not
                    // discard an attack that was already possible.
                    double entry = from;
                    double end = closestFraction;
                    if (!withinContact(target, relativeStart.add(relativeStep.scale(entry)),
                            maximumHorizontalGap, reach)) {
                        for (int iteration = 0; iteration < 19; iteration++) {
                            double middle = (entry + end) * 0.5;
                            if (withinContact(target, relativeStart.add(relativeStep.scale(middle)),
                                    maximumHorizontalGap, reach)) end = middle;
                            else entry = middle;
                        }
                        contactFraction = end;
                    } else {
                        contactFraction = entry;
                    }
                }
            }
            // Preflight requires a clear full-tick movement position from which the
            // attack can be sent; fractional contact alone is not reportable.
            double fraction = Double.isNaN(contactFraction) || reportableContact ? 1.0 : contactFraction;
            if (!clearDescentSegment(player, feet, velocity, fraction, halfWidth, eyeHeight, fluidSafety))
                return new ContactForecast(Prediction.BLOCKED, -1);
            if (!Double.isNaN(contactFraction)) {
                Vec3 impactEye = reportableContact ? next : eye.add(velocity.scale(contactFraction));
                AABB impactTarget = target.move(targetVelocity.scale(tick + (reportableContact ? 1.0 : contactFraction)));
                if (!reportableContact) {
                    return hasClearAttackLine(player, impactEye, impactTarget, reach)
                            ? new ContactForecast(Prediction.REACHABLE, tick + 1)
                            : new ContactForecast(Prediction.BLOCKED, -1);
                }
                if (tick + 1 >= earliestContactTick
                        && withinContact(impactTarget, impactEye, maximumHorizontalGap, reach)
                        && hasClearAttackLine(player, impactEye, impactTarget, reach)) {
                    // Combat attacks are checked against the previous reported
                    // position as well as the position reached this tick.
                    AABB previousTarget = target.move(targetVelocity.scale(tick));
                    if (!requireReportedContact
                            || withinContact(previousTarget, eye, maximumHorizontalGap, reach)
                            && hasClearAttackLine(player, eye, previousTarget, reach)) {
                        return new ContactForecast(Prediction.REACHABLE, tick + 1);
                    }
                }
            }
            fall += drop;
            feet = feet.add(velocity);
            eye = next;
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - gravity) * 0.98, velocity.z * 0.91);
        }
        return new ContactForecast(Prediction.UNREACHABLE, -1);
    }

    static boolean withinVerticalContactRange(double gap, double maximumHorizontalGap, double reach) {
        return gap <= Math.min(maximumHorizontalGap, Math.max(0.0, reach - 0.35));
    }

    private static boolean withinContact(AABB box, Vec3 point, double horizontalGap, double reach) {
        double x = point.x - Mth.clamp(point.x, box.minX, box.maxX);
        double z = point.z - Mth.clamp(point.z, box.minZ, box.maxZ);
        return x * x + z * z <= horizontalGap * horizontalGap
                && distanceSquared(box, point) <= reach * reach;
    }

    public static boolean hasClearAttackLine(LocalPlayer player, Vec3 eye, AABB target, double reach) {
        Vec3 nearest = new Vec3(Mth.clamp(eye.x, target.minX, target.maxX),
                Mth.clamp(eye.y, target.minY, target.maxY),
                Mth.clamp(eye.z, target.minZ, target.maxZ));
        Vec3 center = target.getCenter();
        Vec3 upper = new Vec3(center.x, target.maxY - 0.12, center.z);
        // The closest point may be hidden by a branch while the upper edge
        // is exposed. Preserve either genuinely reachable line of attack.
        for (Vec3 contact : new Vec3[]{nearest, upper, center}) {
            if (eye.distanceToSqr(contact) > reach * reach) continue;
            HitResult hit = player.level().clip(new ClipContext(eye, contact,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS
                    || hit.getLocation().distanceToSqr(eye) + 0.02 >= contact.distanceToSqr(eye)) return true;
        }
        return false;
    }

    private static boolean clearDescentSegment(LocalPlayer player, Vec3 start, Vec3 motion,
                                               double fraction, double halfWidth, double eyeHeight,
                                               ElytraEscapePlanner.CollisionWorld fluidSafety) {
        double length = motion.length() * fraction;
        int steps = Math.max(1, (int) Math.ceil(length / 0.45));
        for (int i = 1; i <= steps; i++) {
            Vec3 feet = start.add(motion.scale(fraction * i / steps));
            if (!clearStandingVolume(player, feet, halfWidth, eyeHeight, fluidSafety)) return false;
        }
        return true;
    }

    private static boolean clearStandingVolume(LocalPlayer player, Vec3 feet,
                                               double halfWidth, double eyeHeight,
                                               ElytraEscapePlanner.CollisionWorld fluidSafety) {
        // This is only a block-collision query: living targets must not be
        // interpreted as impassable terrain at the intended contact point.
        double width = halfWidth + 0.035;
        AABB volume = new AABB(feet.x - width, feet.y + 0.035, feet.z - width,
                feet.x + width, feet.y + Math.max(1.65, eyeHeight + 0.15), feet.z + width);
        if (!player.level().hasChunkAt(BlockPos.containing(feet))) return false;
        if (fluidSafety.waterRisk(volume, Vec3.ZERO) > 0.0
                || fluidSafety.lavaRisk(volume, Vec3.ZERO) > 0.0) return false;
        return !player.level().getBlockCollisions(player, volume).iterator().hasNext();
    }

    private static double distanceSquared(AABB box, Vec3 point) {
        return point.distanceToSqr(Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY), Mth.clamp(point.z, box.minZ, box.maxZ));
    }
}
