/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.ElytraFlightPhysics.FlightModel;
import combatant.client.util.player.ElytraFlightPhysics.GlideHeading;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final double FLUID_FLOOR_CLEARANCE = 3.0;
    private static final double SHALLOW_FLUID_FLOOR_EXTRA_CLEARANCE = 1.75;
    private static final int FLUID_SCAN_DEPTH = 12;
    private static final double LAVA_IGNITION_CLEARANCE = 2.0;

    private ElytraEscapePlanner() {
    }

    /** Damage sums projected wall impacts; firstCollisionTick records unsafe contacts. */
    public record Trajectory(float damage, int impactTick, boolean unknown, boolean blockedSteering,
                             Vec3 endPosition, double nearestDistanceSquared, int firstCollisionTick,
                             int waterHazardTick, double waterRisk, int lavaHazardTick, double lavaRisk) {
        public boolean safe() {
            return !unknown && damage <= 0.0f && !blockedSteering && waterHazardTick == 0 && lavaHazardTick == 0;
        }
    }

    public record EscapeChoice(Rotation rotation, Trajectory trajectory) {
    }

    public interface CollisionWorld {
        boolean loaded(AABB bounds, Vec3 movement);

        Vec3 move(AABB bounds, Vec3 movement);

        default double waterRisk(AABB bounds, Vec3 movement) {
            return 0.0;
        }

        default double lavaRisk(AABB bounds, Vec3 movement) {
            return 0.0;
        }
    }

    public static CollisionWorld world(LocalPlayer player) {
        return world(player, false, false);
    }

    public static CollisionWorld world(LocalPlayer player, boolean fearLava) {
        return world(player, false, fearLava);
    }

    public static CollisionWorld world(LocalPlayer player, boolean avoidFluidFloor, boolean fearLava) {
        return new CachedCollisionWorld(player, avoidFluidFloor, fearLava);
    }

    public static CollisionWorld searchWorld(LocalPlayer player) {
        return searchWorld(player, false, false);
    }

    public static CollisionWorld searchWorld(LocalPlayer player, boolean fearLava) {
        return searchWorld(player, false, fearLava);
    }

    public static CollisionWorld searchWorld(LocalPlayer player, boolean avoidFluidFloor, boolean fearLava) {
        CachedCollisionWorld world = new CachedCollisionWorld(player, avoidFluidFloor, fearLava);
        world.probeEmptySpace = true;
        return world;
    }

    /** The collision cache is scoped to one synchronous evaluation on the client thread. */
    private static final class CachedCollisionWorld implements CollisionWorld {
        private static final int CELL_SIZE = 4;
        private static final int MAX_PROBES = 32;
        private final LocalPlayer player;
        private final Map<Long, Boolean> loadedChunks = new HashMap<>();
        private final Set<Cell> probed = new HashSet<>();
        private final List<AABB> emptyVolumes = new ArrayList<>();
        private final Map<FluidProbe, FluidMargins> fluidProbes = new HashMap<>();
        private final boolean avoidFluidFloor;
        private final boolean avoidLavaIgnition;
        private int probes;
        private boolean probeEmptySpace;

        private CachedCollisionWorld(LocalPlayer player, boolean avoidFluidFloor, boolean fearLava) {
            this.player = player;
            this.avoidFluidFloor = avoidFluidFloor;
            this.avoidLavaIgnition = fearLava && !player.hasEffect(MobEffects.FIRE_RESISTANCE);
        }

        @Override
        public boolean loaded(AABB bounds, Vec3 movement) {
            return loadedBox(bounds.expandTowards(movement));
        }

        @Override
        public Vec3 move(AABB bounds, Vec3 movement) {
            AABB swept = bounds.expandTowards(movement);
            if (probeEmptySpace && (insideKnownEmptyVolume(swept) || probeEmptyVolume(swept))) {
                return movement;
            }
            return ElytraImpactDamageUtil.allowedMovement(player, bounds, movement);
        }

        @Override
        public double waterRisk(AABB bounds, Vec3 movement) {
            return avoidFluidFloor ? fluidRisk(bounds, movement, false) : 0.0;
        }

        @Override
        public double lavaRisk(AABB bounds, Vec3 movement) {
            return avoidFluidFloor || avoidLavaIgnition ? fluidRisk(bounds, movement, true) : 0.0;
        }

        private double fluidRisk(AABB bounds, Vec3 movement, boolean lava) {
            AABB moved = bounds.move(movement);
            double feet = moved.minY;
            double centerX = (bounds.minX + bounds.maxX) * 0.5;
            double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            double dx = movement.x;
            double dz = movement.z;
            double risk = Math.max(fluidColumnRisk(centerX + dx * 0.5, centerZ + dz * 0.5, feet, lava),
                    fluidColumnRisk(centerX + dx, centerZ + dz, feet, lava));
            risk = Math.max(risk, fluidColumnRisk(moved.minX + 0.01, moved.minZ + 0.01, feet, lava));
            risk = Math.max(risk, fluidColumnRisk(moved.maxX - 0.01, moved.minZ + 0.01, feet, lava));
            risk = Math.max(risk, fluidColumnRisk(moved.minX + 0.01, moved.maxZ - 0.01, feet, lava));
            return Math.max(risk, fluidColumnRisk(moved.maxX - 0.01, moved.maxZ - 0.01, feet, lava));
        }

        private double fluidColumnRisk(double x, double z, double feet, boolean lava) {
            FluidMargins margins = fluidProbes.computeIfAbsent(
                    new FluidProbe(Mth.floor(x), Mth.floor(feet), Mth.floor(z)), this::scanFluids);
            double floorLimit = lava ? margins.lavaFloor() : margins.waterFloor();
            return lava ? lavaRiskAtFeet(feet, floorLimit, margins.lavaIgnition(),
                    avoidFluidFloor, avoidLavaIgnition) : clearanceRisk(feet, floorLimit);
        }

        private FluidMargins scanFluids(FluidProbe probe) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            double waterFloor = Double.NEGATIVE_INFINITY;
            double lavaFloor = Double.NEGATIVE_INFINITY;
            double lavaIgnition = Double.NEGATIVE_INFINITY;
            for (int y = probe.y() + 2; y >= probe.y() - FLUID_SCAN_DEPTH; --y) {
                pos.set(probe.x(), y, probe.z());
                FluidState fluid = player.level().getFluidState(pos);
                if (y <= probe.y() && !player.level().getBlockState(pos)
                        .getCollisionShape(player.level(), pos).isEmpty()) break;
                boolean water = fluid.is(FluidTags.WATER);
                boolean lava = fluid.is(FluidTags.LAVA);
                if (!water && !lava) continue;

                double surface = y + fluid.getHeight(player.level(), pos);
                if (lava && avoidLavaIgnition) {
                    lavaIgnition = Math.max(lavaIgnition, surface + LAVA_IGNITION_CLEARANCE);
                }
                if (!avoidFluidFloor || water && waterFloor != Double.NEGATIVE_INFINITY
                        || lava && lavaFloor != Double.NEGATIVE_INFINITY) continue;

                for (int depth = 1; depth <= FLUID_SCAN_DEPTH; ++depth) {
                    int bottomY = y - depth;
                    if (bottomY < probe.y() - FLUID_SCAN_DEPTH) break;
                    pos.set(probe.x(), bottomY, probe.z());
                    FluidState below = player.level().getFluidState(pos);
                    if (water ? below.is(FluidTags.WATER) : below.is(FluidTags.LAVA)) continue;
                    if (!player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty()) {
                        double floorY = bottomY + 1.0;
                        double depthFromSurface = surface - floorY;
                        double clearance = fluidFloorClearance(depthFromSurface);
                        if (water) waterFloor = floorY + clearance;
                        else lavaFloor = floorY + clearance;
                    }
                    break;
                }
            }
            return new FluidMargins(waterFloor, lavaFloor, lavaIgnition);
        }

        private record FluidProbe(int x, int y, int z) {
        }

        private record FluidMargins(double waterFloor, double lavaFloor, double lavaIgnition) {
        }

        private boolean loadedBox(AABB bounds) {
            int minX = Mth.floor(bounds.minX) >> 4;
            int maxX = Mth.floor(bounds.maxX) >> 4;
            int minZ = Mth.floor(bounds.minZ) >> 4;
            int maxZ = Mth.floor(bounds.maxZ) >> 4;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    long key = ((long) x << 32) | (z & 0xffffffffL);
                    Boolean loaded = loadedChunks.get(key);
                    if (loaded == null) {
                        loaded = player.level().hasChunkAt(new BlockPos(x << 4, Mth.floor(bounds.minY), z << 4));
                        loadedChunks.put(key, loaded);
                    }
                    if (!loaded) return false;
                }
            }
            return true;
        }

        private boolean insideKnownEmptyVolume(AABB query) {
            for (AABB region : emptyVolumes) {
                if (contains(region, query)) return true;
            }
            return false;
        }

        private boolean probeEmptyVolume(AABB query) {
            if (probes >= MAX_PROBES) return false;
            Cell cell = new Cell(Mth.floor(query.getCenter().x / CELL_SIZE),
                    Mth.floor(query.getCenter().y / CELL_SIZE),
                    Mth.floor(query.getCenter().z / CELL_SIZE));
            AABB region = new AABB(
                    cell.x() * CELL_SIZE - 1.0, cell.y() * CELL_SIZE - 1.0, cell.z() * CELL_SIZE - 1.0,
                    (cell.x() + 1) * CELL_SIZE + 1.0,
                    (cell.y() + 1) * CELL_SIZE + 1.0,
                    (cell.z() + 1) * CELL_SIZE + 1.0);
            if (!contains(region, query) || !probed.add(cell)) return false;
            probes++;
            if (!loadedBox(region) || !player.level().noCollision(player, region)) return false;
            emptyVolumes.add(region);
            return true;
        }

        private static boolean contains(AABB outer, AABB inner) {
            return inner.minX >= outer.minX && inner.maxX <= outer.maxX
                    && inner.minY >= outer.minY && inner.maxY <= outer.maxY
                    && inner.minZ >= outer.minZ && inner.maxZ <= outer.maxZ;
        }

        private record Cell(int x, int y, int z) {
        }
    }

    static double fluidFloorClearance(double depth) {
        return FLUID_FLOOR_CLEARANCE
                + (depth <= 4.0 ? SHALLOW_FLUID_FLOOR_EXTRA_CLEARANCE : 0.0);
    }

    private static double clearanceRisk(double feet, double limit) {
        return limit == Double.NEGATIVE_INFINITY ? 0.0 : Math.max(0.0, limit - feet);
    }

    static double lavaRiskAtFeet(double feet, double floorLimit, double ignitionLimit,
                                 boolean avoidFloor, boolean avoidIgnition) {
        double floorRisk = avoidFloor ? clearanceRisk(feet, floorLimit) : 0.0;
        return avoidIgnition ? Math.max(floorRisk, clearanceRisk(feet, ignitionLimit)) : floorRisk;
    }

    public static int horizon(Vec3 velocity, Rotation heading, double clearance, int minimum, FlightModel physics) {
        Vec3 forward = new Vec3(velocity.x, 0, velocity.z).normalize();
        Rotation reverse = forward.lengthSqr() > 0.0
                ? Rotation.fromRotationVec(forward.scale(-1)) : heading;
        int recovery = 1;
        Vec3 braking = velocity;
        GlideHeading reverseHeading = ElytraFlightPhysics.prepare(reverse);
        for (; recovery < MAX_HORIZON; recovery++) {
            braking = physics.glide(braking, reverseHeading);
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
        GlideHeading prepared = ElytraFlightPhysics.prepare(heading);
        Trajectory result = forecast(world, bounds, velocity, heading, prepared, ticks, physics,
                physics.guaranteedBoostTicks(), false, avoidPoint);
        if (physics.possibleBoostTicks() != physics.guaranteedBoostTicks()) {
            result = worst(result, forecast(world, bounds, velocity, heading, prepared, ticks, physics,
                    physics.possibleBoostTicks(), false, avoidPoint));
        }
        if (physics.possibleBoostTicks() > 0) {
            result = worst(result, forecast(world, bounds, velocity, heading, prepared, ticks, physics,
                    physics.possibleBoostTicks(), true, avoidPoint));
            if (physics.guaranteedBoostTicks() > 0
                    && physics.possibleBoostTicks() != physics.guaranteedBoostTicks()) {
                result = worst(result, forecast(world, bounds, velocity, heading, prepared, ticks, physics,
                        physics.guaranteedBoostTicks(), true, avoidPoint));
            }
        }
        return result;
    }

    private static Trajectory forecast(CollisionWorld world, AABB bounds, Vec3 velocity, Rotation heading,
                                       GlideHeading prepared, int ticks, FlightModel physics,
                                       int boostTicks, boolean boostBeforeMove,
                                       Vec3 avoidPoint) {
        float totalDamage = 0;
        int impactTick = 0;
        int firstCollisionTick = 0;
        boolean blockedSteering = false;
        double nearestDistance = Double.POSITIVE_INFINITY;
        int firstWaterHazardTick = 0;
        double maxWaterRisk = 0.0;
        int firstLavaHazardTick = 0;
        double maxLavaRisk = 0.0;
        for (int tick = 1; tick <= ticks; tick++) {
            if (boostBeforeMove) velocity = physics.afterMove(velocity, heading, tick, boostTicks);
            Vec3 attempted = physics.glide(velocity, prepared);
            if (!finite(attempted) || !world.loaded(bounds, attempted)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                        firstLavaHazardTick, maxLavaRisk);
            }
            Vec3 clipped = world.move(bounds, attempted);
            if (!finite(clipped)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                        firstLavaHazardTick, maxLavaRisk);
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
                        bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                        firstLavaHazardTick, maxLavaRisk);
            }
            Vec3 clearanceClipped = world.move(clearanceBounds, attempted);
            if (!finite(clearanceClipped)) {
                return new Trajectory(totalDamage, impactTick, true, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                        firstLavaHazardTick, maxLavaRisk);
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
            double waterRisk = world.waterRisk(bounds, clipped);
            if (waterRisk > 0.0) {
                if (firstWaterHazardTick == 0) firstWaterHazardTick = tick;
                maxWaterRisk = Math.max(maxWaterRisk, waterRisk);
            }
            double lavaRisk = world.lavaRisk(bounds, clipped);
            if (lavaRisk > 0.0) {
                if (firstLavaHazardTick == 0) firstLavaHazardTick = tick;
                maxLavaRisk = Math.max(maxLavaRisk, lavaRisk);
            }
            bounds = bounds.move(clipped);
            if (verticalContact && attempted.y < 0.0) {
                return new Trajectory(totalDamage, impactTick, false, blockedSteering,
                        bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                        firstLavaHazardTick, maxLavaRisk);
            }
            velocity = ElytraImpactDamageUtil.postCollisionVelocity(attempted, clipped);
            if (!boostBeforeMove) velocity = physics.afterMove(velocity, heading, tick, boostTicks);
        }
        return new Trajectory(totalDamage, impactTick, false, blockedSteering,
                bounds.getCenter(), nearestDistance, firstCollisionTick, firstWaterHazardTick, maxWaterRisk,
                firstLavaHazardTick, maxLavaRisk);
    }

    private static Trajectory worst(Trajectory first, Trajectory second) {
        Trajectory worse = compareRisk(first, second) >= 0 ? first : second;
        int firstContact = first.firstCollisionTick() == 0 ? second.firstCollisionTick()
                : second.firstCollisionTick() == 0 ? first.firstCollisionTick()
                : Math.min(first.firstCollisionTick(), second.firstCollisionTick());
        int waterContact = first.waterHazardTick() == 0 ? second.waterHazardTick()
                : second.waterHazardTick() == 0 ? first.waterHazardTick()
                : Math.min(first.waterHazardTick(), second.waterHazardTick());
        int lavaContact = first.lavaHazardTick() == 0 ? second.lavaHazardTick()
                : second.lavaHazardTick() == 0 ? first.lavaHazardTick()
                : Math.min(first.lavaHazardTick(), second.lavaHazardTick());
        return new Trajectory(Math.max(first.damage(), second.damage()), worse.impactTick(),
                first.unknown() || second.unknown(), first.blockedSteering() || second.blockedSteering(),
                worse.endPosition(), Math.min(first.nearestDistanceSquared(), second.nearestDistanceSquared()),
                firstContact, waterContact, Math.max(first.waterRisk(), second.waterRisk()),
                lavaContact, Math.max(first.lavaRisk(), second.lavaRisk()));
    }

    public static EscapeChoice findSaferEscape(CollisionWorld world, AABB bounds, Vec3 velocity,
                                               Rotation intended, Rotation committed, int horizon,
                                               FlightModel physics, Trajectory baseline,
                                               UnaryOperator<Rotation> normalize) {
        if (baseline.safe()) return null;
        if (world instanceof CachedCollisionWorld cached) cached.probeEmptySpace = true;
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
        if (first.lavaRisk() != second.lavaRisk()) return Double.compare(first.lavaRisk(), second.lavaRisk());
        if (first.waterRisk() != second.waterRisk()) return Double.compare(first.waterRisk(), second.waterRisk());
        int la = first.lavaHazardTick() == 0 ? Integer.MAX_VALUE : first.lavaHazardTick();
        int lb = second.lavaHazardTick() == 0 ? Integer.MAX_VALUE : second.lavaHazardTick();
        if (la != lb) return Integer.compare(lb, la);
        int wa = first.waterHazardTick() == 0 ? Integer.MAX_VALUE : first.waterHazardTick();
        int wb = second.waterHazardTick() == 0 ? Integer.MAX_VALUE : second.waterHazardTick();
        if (wa != wb) return Integer.compare(wb, wa);
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
