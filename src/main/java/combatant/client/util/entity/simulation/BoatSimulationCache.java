/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.entity.simulation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class BoatSimulationCache {

    public static final BoatSimulationCache INSTANCE = new BoatSimulationCache();
    private static final double BOAT_GRAVITY = 0.04;

    private final Map<AbstractBoat, SimulatedBoatCache> cache = new WeakHashMap<>();

    private BoatSimulationCache() {
    }

    public static SimulatedBoatCache getSimulation(AbstractBoat boat) {
        return INSTANCE.cache.computeIfAbsent(boat, SimulatedBoatCache::new);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        cache.clear();
    }

    private enum Location {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR
    }

    public static final class SimulatedBoatCache {
        private final SimulatedBoat simulator;
        private final List<BoatSnapshot> snapshots = new ArrayList<>();
        private int simulatedTicks;

        private SimulatedBoatCache(AbstractBoat boat) {
            this.simulator = new SimulatedBoat(boat);
            this.snapshots.add(new BoatSnapshot(simulator.pos, simulator.yaw));
        }

        public BoatSnapshot getSnapshotAt(int ticks) {
            while (simulatedTicks < ticks) {
                simulator.tick();
                snapshots.add(new BoatSnapshot(simulator.pos, simulator.yaw));
                simulatedTicks++;
            }
            return snapshots.get(ticks);
        }
    }

    public record BoatSnapshot(Vec3 pos, float yaw) {
    }

    private static final class SimulatedBoat {
        private final AbstractBoat boat;
        private final float yaw;
        private Vec3 pos;
        private Vec3 velocity;
        private AABB boundingBox;
        private float yawVelocity;
        private double waterLevel;
        private float nearbySlipperiness;
        private Location location;
        private Location lastLocation;
        private double fallVelocity;

        private SimulatedBoat(AbstractBoat boat) {
            this.boat = boat;
            this.pos = boat.position();
            this.velocity = boat.getDeltaMovement();
            this.boundingBox = boat.getBoundingBox();
            this.yaw = boat.getYRot();
            this.yawVelocity = 0.0f;
            this.location = checkLocation();
            this.lastLocation = location;
        }

        private void tick() {
            lastLocation = location;
            location = checkLocation();
            updateVelocity();
            move();
        }

        private void move() {
            Vec3 adjusted = Entity.collideBoundingBox(boat, velocity, boundingBox, boat.level(), List.of());
            pos = pos.add(adjusted);
            boundingBox = boat.getBoundingBox().move(pos.subtract(boat.position()));
            velocity = adjusted;
        }

        private Location checkLocation() {
            Location underWater = getUnderWaterLocation();
            if (underWater != null) {
                waterLevel = boundingBox.maxY;
                return underWater;
            }
            if (checkBoatInWater()) {
                return Location.IN_WATER;
            }
            float slipperiness = getNearbySlipperiness();
            if (slipperiness > 0.0f) {
                nearbySlipperiness = slipperiness;
                return Location.ON_LAND;
            }
            return Location.IN_AIR;
        }

        private void updateVelocity() {
            double gravity = -boat.getGravity();
            double buoyancy = 0.0;
            float drag = 0.05f;

            if (lastLocation == Location.IN_AIR && location != Location.IN_AIR && location != Location.ON_LAND) {
                waterLevel = pos.y + boat.getBbHeight();
                double waterHeightBelow = getWaterHeightBelow() - boat.getBbHeight() + 0.101;
                AABB movedBox = boundingBox.move(0.0, waterHeightBelow - pos.y, 0.0);
                if (boat.level().noCollision(boat, movedBox)) {
                    pos = new Vec3(pos.x, waterHeightBelow, pos.z);
                    boundingBox = movedBox;
                    velocity = new Vec3(velocity.x, 0.0, velocity.z);
                    fallVelocity = 0.0;
                }
                location = Location.IN_WATER;
            } else {
                if (location == Location.IN_WATER) {
                    buoyancy = (waterLevel - pos.y) / boat.getBbHeight();
                    drag = 0.9f;
                } else if (location == Location.UNDER_FLOWING_WATER) {
                    gravity = -7.0E-4;
                    drag = 0.9f;
                } else if (location == Location.UNDER_WATER) {
                    buoyancy = 0.01f;
                    drag = 0.45f;
                } else if (location == Location.IN_AIR) {
                    drag = 0.9f;
                } else if (location == Location.ON_LAND) {
                    drag = nearbySlipperiness;
                    if (boat.getControllingPassenger() instanceof Player) {
                        nearbySlipperiness /= 2.0f;
                    }
                }

                velocity = new Vec3(velocity.x * drag, velocity.y + gravity, velocity.z * drag);
                yawVelocity *= drag;
                if (buoyancy > 0.0) {
                    velocity = new Vec3(
                            velocity.x,
                            (velocity.y + buoyancy * (BOAT_GRAVITY / 0.65)) * 0.75,
                            velocity.z
                    );
                }
            }
        }

        private float getWaterHeightBelow() {
            AABB box = boundingBox;
            int minX = Mth.floor(box.minX);
            int maxX = Mth.ceil(box.maxX);
            int minY = Mth.floor(box.maxY);
            int maxY = Mth.ceil(box.maxY - fallVelocity);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.ceil(box.maxZ);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int y = minY; y < maxY; y++) {
                float fluidHeight = 0.0f;
                for (int x = minX; x < maxX; x++) {
                    for (int z = minZ; z < maxZ; z++) {
                        mutable.set(x, y, z);
                        FluidState fluidState = boat.level().getFluidState(mutable);
                        if (fluidState.is(FluidTags.WATER)) {
                            fluidHeight = Math.max(fluidHeight, fluidState.getHeight(boat.level(), mutable));
                        }
                        if (fluidHeight >= 1.0f) {
                            break;
                        }
                    }
                }
                if (fluidHeight < 1.0f) {
                    return mutable.getY() + fluidHeight;
                }
            }

            return maxY + 1.0f;
        }

        private float getNearbySlipperiness() {
            AABB box = boundingBox;
            AABB sample = new AABB(box.minX, box.minY - 0.001, box.minZ, box.maxX, box.minY, box.maxZ);
            int minX = Mth.floor(sample.minX) - 1;
            int maxX = Mth.ceil(sample.maxX) + 1;
            int minY = Mth.floor(sample.minY) - 1;
            int maxY = Mth.ceil(sample.maxY) + 1;
            int minZ = Mth.floor(sample.minZ) - 1;
            int maxZ = Mth.ceil(sample.maxZ) + 1;
            VoxelShape shape = Shapes.create(sample);
            float slipperiness = 0.0f;
            int count = 0;
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    int edge = (x != minX && x != maxX - 1 ? 0 : 1) + (z != minZ && z != maxZ - 1 ? 0 : 1);
                    if (edge == 2) {
                        continue;
                    }
                    for (int y = minY; y < maxY; y++) {
                        if (edge <= 0 || y != minY && y != maxY - 1) {
                            mutable.set(x, y, z);
                            BlockState state = boat.level().getBlockState(mutable);
                            if (!(state.getBlock() instanceof LilyPadBlock)
                                    && Shapes.joinIsNotEmpty(state.getCollisionShape(boat.level(), mutable).move(mutable), shape, BooleanOp.AND)) {
                                slipperiness += state.getBlock().getFriction();
                                count++;
                            }
                        }
                    }
                }
            }

            return count == 0 ? 0.0f : slipperiness / count;
        }

        private boolean checkBoatInWater() {
            AABB box = boundingBox;
            int minX = Mth.floor(box.minX);
            int maxX = Mth.ceil(box.maxX);
            int minY = Mth.floor(box.minY);
            int maxY = Mth.ceil(box.minY + 0.001);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.ceil(box.maxZ);
            boolean inWater = false;
            waterLevel = -Double.MAX_VALUE;
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        mutable.set(x, y, z);
                        FluidState fluidState = boat.level().getFluidState(mutable);
                        if (fluidState.is(FluidTags.WATER)) {
                            float height = y + fluidState.getHeight(boat.level(), mutable);
                            waterLevel = Math.max(height, waterLevel);
                            inWater |= box.minY < height;
                        }
                    }
                }
            }
            return inWater;
        }

        private Location getUnderWaterLocation() {
            AABB box = boundingBox;
            double top = box.maxY + 0.001;
            int minX = Mth.floor(box.minX);
            int maxX = Mth.ceil(box.maxX);
            int minY = Mth.floor(box.maxY);
            int maxY = Mth.ceil(top);
            int minZ = Mth.floor(box.minZ);
            int maxZ = Mth.ceil(box.maxZ);
            boolean still = false;
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int x = minX; x < maxX; x++) {
                for (int y = minY; y < maxY; y++) {
                    for (int z = minZ; z < maxZ; z++) {
                        mutable.set(x, y, z);
                        FluidState fluidState = boat.level().getFluidState(mutable);
                        if (fluidState.is(FluidTags.WATER) && top < mutable.getY() + fluidState.getHeight(boat.level(), mutable)) {
                            if (!fluidState.isSource()) {
                                return Location.UNDER_FLOWING_WATER;
                            }
                            still = true;
                        }
                    }
                }
            }

            return still ? Location.UNDER_WATER : null;
        }
    }
}
