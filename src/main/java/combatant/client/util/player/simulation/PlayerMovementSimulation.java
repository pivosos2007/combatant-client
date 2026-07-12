/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.player.simulation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.mixins.accessors.EntityInvoker;
import combatant.client.util.player.effect.StatusEffectAccess;

import java.util.List;

abstract class PlayerMovementSimulation {
    private static final double STEP_HEIGHT = 0.5;

    private final Player player;
    private final SimulatedInput input;
    private final float yaw;
    private final float pitch;
    private final boolean sprinting;
    private Vec3 pos;
    private Vec3 velocity;
    private AABB boundingBox;
    private boolean onGround;
    private boolean jumping;
    private boolean gliding;
    private double fallDistance;
    private boolean horizontalCollision;
    private boolean clipLedged;
    private boolean touchingWater;
    private boolean submergedInWater;
    private boolean swimming;
    private double waterHeight;
    private double lavaHeight;
    private int jumpingCooldown;

    PlayerMovementSimulation(Player player, SimulatedInput input) {
        this.player = player;
        this.pos = player.position();
        this.velocity = this.pos.subtract(player.xo, player.yo, player.zo);
        this.boundingBox = player.getBoundingBox();
        this.yaw = player.getYRot();
        this.pitch = player.getXRot();
        this.sprinting = player.isSprinting();
        this.onGround = player.onGround();
        this.fallDistance = player.fallDistance;
        this.jumping = !player.onGround();
        this.gliding = player.isFallFlying();
        this.input = input;
    }

    void tick() {
        clipLedged = false;
        input.update();

        updateWaterState();
        updateSubmergedInWaterState();
        updateSwimming();
        applyBubbleColumnEffects();

        if (jumpingCooldown > 0) {
            jumpingCooldown--;
        }

        jumping = input.jump;

        double vx = Math.abs(velocity.x) < 0.003 ? 0.0 : velocity.x;
        double vy = Math.abs(velocity.y) < 0.003 ? 0.0 : velocity.y;
        double vz = Math.abs(velocity.z) < 0.003 ? 0.0 : velocity.z;
        velocity = new Vec3(vx, vy, vz);

        if (jumping && shouldSwimInFluids()) {
            double fluidHeight = isInLava() ? lavaHeight : waterHeight;
            boolean inWater = touchingWater && fluidHeight > 0.0;
            double swimHeight = getSwimHeight();

            if (!inWater || onGround && !(fluidHeight > swimHeight)) {
                if (!isInLava() || onGround && !(fluidHeight > swimHeight)) {
                    if ((onGround || inWater && fluidHeight <= swimHeight) && jumpingCooldown == 0) {
                        jump();
                        jumpingCooldown = 10;
                    }
                } else {
                    swimUpward();
                }
            } else {
                swimUpward();
            }
        } else {
            jumpingCooldown = 0;
        }

        if (StatusEffectAccess.has(player, MobEffects.SLOW_FALLING) || StatusEffectAccess.has(player, MobEffects.LEVITATION)) {
            onGround = false;
        }

        travel(new Vec3(input.movementSideways * 0.98f, 0.0, input.movementForward * 0.98f));
    }

    private void jump() {
        double jumpVelocity = player.getAttributeValue(Attributes.JUMP_STRENGTH) * getJumpVelocityMultiplier()
                + player.getJumpBoostPower();
        if (jumpVelocity <= 1.0E-5) {
            return;
        }

        velocity = new Vec3(velocity.x, Math.max(jumpVelocity, velocity.y), velocity.z);

        if (sprinting) {
            float yawRadians = yaw * Mth.DEG_TO_RAD;
            velocity = velocity.add(-Mth.sin(yawRadians) * 0.2f, 0.0, Mth.cos(yawRadians) * 0.2f);
        }
    }

    private double getJumpVelocityMultiplier() {
        BlockPos feetPos = BlockPos.containing(pos);
        float feet = player.level().getBlockState(feetPos).getBlock().getJumpFactor();
        float floor = player.level().getBlockState(getVelocityAffectingPos()).getBlock().getJumpFactor();
        return feet == 1.0f ? floor : feet;
    }

    private void travel(Vec3 movementInput) {
        if (swimming && !player.isPassenger()) {
            double lookY = getRotationVector().y;
            double climb = lookY < -0.2 ? 0.085 : 0.06;
            BlockPos fluidPos = BlockPos.containing(pos.x, pos.y + 0.9, pos.z);
            if (lookY <= 0.0 || jumping || !player.level().getFluidState(fluidPos).isEmpty()) {
                velocity = velocity.add(0.0, (lookY - velocity.y) * climb, 0.0);
            }
        }

        if ((touchingWater || isInLava()) && shouldSwimInFluids()) {
            travelInFluid(movementInput);
            return;
        }

        if (shouldUseRemoteFlightPhysics()) {
            travelRemoteFlight(movementInput);
            return;
        }

        if (gliding) {
            travelGliding(movementInput);
            return;
        }

        travelMidAir(movementInput);
    }

    protected boolean shouldUseRemoteFlightPhysics() {
        return false;
    }

    protected boolean shouldHoldVerticalMotion() {
        return false;
    }

    RemotePlayerObservation.MotionKind motionKind() {
        return RemotePlayerObservation.MotionKind.NORMAL;
    }

    private void travelRemoteFlight(Vec3 movementInput) {
        updateVelocity(0.045f, movementInput);

        if (shouldHoldVerticalMotion() && Math.abs(velocity.y) < 0.12) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        }

        move(velocity);

        double yDrag = shouldHoldVerticalMotion() ? 0.35 : 0.91;
        velocity = velocity.multiply(0.91, yDrag, 0.91);

        if (shouldHoldVerticalMotion() && Math.abs(velocity.y) < 0.015) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        }

        fallDistance = 0.0;
        onGround = false;
    }

    private void travelMidAir(Vec3 movementInput) {
        BlockPos blockPos = getVelocityAffectingPos();
        float slipperiness = player.level().getBlockState(blockPos).getBlock().getFriction();
        float friction = onGround ? slipperiness * 0.91f : 0.91f;

        Vec3 movedVelocity = applyMovementInput(movementInput, slipperiness);
        double y = movedVelocity.y;
        MobEffectInstance levitation = StatusEffectAccess.get(player, MobEffects.LEVITATION);
        if (levitation != null) {
            y += (0.05 * (levitation.getAmplifier() + 1) - movedVelocity.y) * 0.2;
        } else if (!player.level().isClientSide() || player.level().hasChunkAt(blockPos)) {
            y -= getEffectiveGravity();
        } else if (pos.y > player.level().getMinY()) {
            y = -0.1;
        } else {
            y = 0.0;
        }

        if (player.shouldDiscardFriction()) {
            velocity = new Vec3(movedVelocity.x, y, movedVelocity.z);
        } else {
            velocity = new Vec3(movedVelocity.x * friction, y * 0.9800000190734863, movedVelocity.z * friction);
        }
    }

    private void travelInFluid(Vec3 movementInput) {
        boolean falling = velocity.y <= 0.0;
        double y = pos.y;
        double gravity = getEffectiveGravity();

        if (touchingWater) {
            travelInWater(movementInput, gravity, falling, y);
        } else {
            travelInLava(movementInput, gravity, falling, y);
        }
    }

    private void travelGliding(Vec3 movementInput) {
        if (isClimbing()) {
            travelMidAir(movementInput);
            gliding = false;
            return;
        }

        Vec3 oldVelocity = velocity;
        double horizontalSpeed = oldVelocity.horizontalDistance();
        velocity = calcGlidingVelocity(oldVelocity);
        move(velocity);

        if (!player.level().isClientSide()) {
            double newHorizontalSpeed = velocity.horizontalDistance();
            if (horizontalCollision && oldVelocity.horizontalDistance() - newHorizontalSpeed > 0.0) {
                gliding = false;
            }
        }
    }

    private Vec3 calcGlidingVelocity(Vec3 oldVelocity) {
        Vec3 rotation = getRotationVector();
        float pitchRadians = pitch * Mth.DEG_TO_RAD;
        double horizontalLook = Math.sqrt(rotation.x * rotation.x + rotation.z * rotation.z);
        double horizontalVelocity = oldVelocity.horizontalDistance();
        double gravity = getEffectiveGravity();
        double lift = Mth.square(Math.cos(pitchRadians));

        oldVelocity = oldVelocity.add(0.0, gravity * (-1.0 + lift * 0.75), 0.0);
        if (oldVelocity.y < 0.0 && horizontalLook > 0.0) {
            double dive = oldVelocity.y * -0.1 * lift;
            oldVelocity = oldVelocity.add(rotation.x * dive / horizontalLook, dive, rotation.z * dive / horizontalLook);
        }

        if (pitchRadians < 0.0f && horizontalLook > 0.0) {
            double climb = horizontalVelocity * -Mth.sin(pitchRadians) * 0.04;
            oldVelocity = oldVelocity.add(-rotation.x * climb / horizontalLook, climb * 3.2, -rotation.z * climb / horizontalLook);
        }

        if (horizontalLook > 0.0) {
            oldVelocity = oldVelocity.add(
                    (rotation.x / horizontalLook * horizontalVelocity - oldVelocity.x) * 0.1,
                    0.0,
                    (rotation.z / horizontalLook * horizontalVelocity - oldVelocity.z) * 0.1
            );
        }

        return oldVelocity.multiply(0.99f, 0.98f, 0.99f);
    }

    private void travelInWater(Vec3 movementInput, double gravity, boolean falling, double y) {
        float movementMultiplier = sprinting ? 0.9f : 0.8f;
        float acceleration = 0.02f;
        float waterEfficiency = (float) player.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (!onGround) {
            waterEfficiency *= 0.5f;
        }

        if (waterEfficiency > 0.0f) {
            movementMultiplier += (0.54600006f - movementMultiplier) * waterEfficiency;
            acceleration += (player.getSpeed() - acceleration) * waterEfficiency;
        }

        if (StatusEffectAccess.has(player, MobEffects.DOLPHINS_GRACE)) {
            movementMultiplier = 0.96f;
        }

        updateVelocity(acceleration, movementInput);
        move(velocity);

        Vec3 movedVelocity = velocity;
        if (horizontalCollision && isClimbing()) {
            movedVelocity = new Vec3(movedVelocity.x, 0.2, movedVelocity.z);
        }

        movedVelocity = movedVelocity.multiply(movementMultiplier, 0.8f, movementMultiplier);
        velocity = applyFluidMovingSpeed(gravity, falling, movedVelocity);
        resetVerticalVelocityInFluid(y);
    }

    private void travelInLava(Vec3 movementInput, double gravity, boolean falling, double y) {
        updateVelocity(0.02f, movementInput);
        move(velocity);
        if (lavaHeight <= getSwimHeight()) {
            velocity = velocity.multiply(0.5, 0.8f, 0.5);
            velocity = applyFluidMovingSpeed(gravity, falling, velocity);
        } else {
            velocity = velocity.scale(0.5);
        }

        if (gravity != 0.0) {
            velocity = velocity.add(0.0, -gravity / 4.0, 0.0);
        }

        resetVerticalVelocityInFluid(y);
    }

    private float getMovementSpeed(float slipperiness) {
        if (onGround) {
            return player.getSpeed() * (0.21600002f / (slipperiness * slipperiness * slipperiness));
        }

        return sprinting ? 0.025999999f : 0.02f;
    }

    private void updateVelocity(float speed, Vec3 movementInput) {
        Vec3 added = EntityInvoker.combatant$movementInputToVelocity(movementInput, speed, yaw);
        velocity = velocity.add(added);
    }

    private Vec3 applyMovementInput(Vec3 movementInput, float slipperiness) {
        updateVelocity(getMovementSpeed(slipperiness), movementInput);
        velocity = applyClimbingSpeed(velocity);
        velocity = applyWebSpeed(velocity);
        move(adjustMovementForSneaking(velocity));
        Vec3 movedVelocity = velocity;
        if ((horizontalCollision || jumping) && isClimbing()) {
            movedVelocity = new Vec3(movedVelocity.x, 0.2, movedVelocity.z);
        }
        return movedVelocity;
    }

    private Vec3 adjustMovementForSneaking(Vec3 movement) {
        if (movement.y > 0.0 || !shouldConsiderLedgeClipping()) {
            return movement;
        }

        double x = movement.x;
        double z = movement.z;
        while (x != 0.0 && player.level().noCollision(player, boundingBox.move(x, -STEP_HEIGHT, 0.0))) {
            if (x < 0.05 && x >= -0.05) {
                x = 0.0;
            } else if (x > 0.0) {
                x -= 0.05;
            } else {
                x += 0.05;
            }
        }

        while (z != 0.0 && player.level().noCollision(player, boundingBox.move(0.0, -STEP_HEIGHT, z))) {
            if (z < 0.05 && z >= -0.05) {
                z = 0.0;
            } else if (z > 0.0) {
                z -= 0.05;
            } else {
                z += 0.05;
            }
        }

        while (x != 0.0 && z != 0.0 && player.level().noCollision(player, boundingBox.move(x, -STEP_HEIGHT, z))) {
            if (x < 0.05 && x >= -0.05) {
                x = 0.0;
            } else if (x > 0.0) {
                x -= 0.05;
            } else {
                x += 0.05;
            }

            if (z < 0.05 && z >= -0.05) {
                z = 0.0;
            } else if (z > 0.0) {
                z -= 0.05;
            } else {
                z += 0.05;
            }
        }

        if (movement.x != x || movement.z != z) {
            clipLedged = true;
        }

        if (shouldClipAtLedge()) {
            return new Vec3(x, movement.y, z);
        }

        return movement;
    }

    private boolean shouldClipAtLedge() {
        return !input.ignoreClippingAtLedge && (input.sneak || input.forceSafeWalk);
    }

    private boolean shouldConsiderLedgeClipping() {
        return onGround || fallDistance < STEP_HEIGHT && !player.level().noCollision(
                player,
                boundingBox.move(0.0, fallDistance - STEP_HEIGHT, 0.0)
        );
    }

    private Vec3 applyClimbingSpeed(Vec3 motion) {
        if (!isClimbing()) {
            return motion;
        }

        double x = Mth.clamp(motion.x, -0.15f, 0.15f);
        double z = Mth.clamp(motion.z, -0.15f, 0.15f);
        double y = Math.max(motion.y, -0.15f);
        if (y < 0.0 && player.isShiftKeyDown()) {
            y = 0.0;
        }
        return new Vec3(x, y, z);
    }

    private void move(Vec3 movement) {
        Vec3 adjusted = movement.lengthSqr() == 0.0
                ? movement
                : Entity.collideBoundingBox(player, movement, boundingBox, player.level(), List.of());

        if (adjusted.lengthSqr() > 1.0E-7 || movement.lengthSqr() - adjusted.lengthSqr() < 1.0E-7) {
            pos = pos.add(adjusted);
            EntityDimensions dimensions = player.getDimensions(player.getPose());
            boundingBox = dimensions.makeBoundingBox(pos);
        }

        boolean xCollision = movement.x != adjusted.x;
        boolean yCollision = movement.y != adjusted.y;
        boolean zCollision = movement.z != adjusted.z;
        horizontalCollision = xCollision || zCollision;
        onGround = yCollision && movement.y < 0.0;
        if (onGround) {
            fallDistance = 0.0;
        } else if (adjusted.y < 0.0) {
            fallDistance -= adjusted.y;
        }

        if (xCollision || zCollision || yCollision) {
            velocity = new Vec3(
                    xCollision ? 0.0 : velocity.x,
                    yCollision ? 0.0 : velocity.y,
                    zCollision ? 0.0 : velocity.z
            );
        }

        float velocityMultiplier = getVelocityMultiplier();
        velocity = new Vec3(velocity.x * velocityMultiplier, velocity.y, velocity.z * velocityMultiplier);
    }

    private BlockPos getVelocityAffectingPos() {
        return BlockPos.containing(pos.x, boundingBox.minY - 0.5000001, pos.z);
    }

    private double getEffectiveGravity() {
        double gravity = player.getGravity();
        if (velocity.y <= 0.0 && StatusEffectAccess.has(player, MobEffects.SLOW_FALLING)) {
            return Math.min(gravity, 0.01);
        }
        return gravity;
    }

    private boolean shouldSwimInFluids() {
        return !player.getAbilities().flying;
    }

    private boolean isInLava() {
        return lavaHeight > 0.0;
    }

    private double getSwimHeight() {
        return player.getEyeHeight(player.getPose()) < 0.4f ? 0.0 : 0.4;
    }

    private boolean isClimbing() {
        if (player.getAbilities().flying || player.isSpectator()) {
            return false;
        }

        BlockPos blockPos = BlockPos.containing(pos);
        BlockState blockState = player.level().getBlockState(blockPos);
        if (gliding && blockState.is(BlockTags.CAN_GLIDE_THROUGH)) {
            return false;
        }
        if (blockState.is(BlockTags.CLIMBABLE)) {
            return true;
        }
        return blockState.getBlock() instanceof TrapDoorBlock && canEnterTrapdoor(blockPos, blockState);
    }

    private Vec3 applyWebSpeed(Vec3 motion) {
        BlockState state = player.level().getBlockState(BlockPos.containing(pos));
        if (!state.is(Blocks.COBWEB)) {
            return motion;
        }

        if (StatusEffectAccess.has(player, MobEffects.WEAVING)) {
            return motion.multiply(0.5, 0.25, 0.5);
        }

        return motion.multiply(0.25, 0.05, 0.25);
    }

    private void swimUpward() {
        velocity = velocity.add(0.0, 0.04f, 0.0);
    }

    private Vec3 applyFluidMovingSpeed(double gravity, boolean falling, Vec3 motion) {
        if (gravity != 0.0 && !sprinting) {
            double y = falling && Math.abs(motion.y - 0.005) >= 0.003 && Math.abs(motion.y - gravity / 16.0) < 0.003
                    ? -0.003
                    : motion.y - gravity / 16.0;
            return new Vec3(motion.x, y, motion.z);
        }

        return motion;
    }

    private void resetVerticalVelocityInFluid(double y) {
        if (horizontalCollision && doesNotCollide(velocity.x, velocity.y + 0.6f - pos.y + y, velocity.z)) {
            velocity = new Vec3(velocity.x, 0.3f, velocity.z);
        }
    }

    private boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        AABB moved = boundingBox.move(offsetX, offsetY, offsetZ);
        return player.level().noCollision(player, moved) && !player.level().containsAnyLiquid(moved);
    }

    private void updateWaterState() {
        waterHeight = 0.0;
        lavaHeight = 0.0;
        touchingWater = updateMovementInFluid(true, 0.014);
        updateMovementInFluid(false, 0.0023333333333333335);
    }

    private void updateSubmergedInWaterState() {
        double eyeY = pos.y + player.getEyeHeight(player.getPose());
        BlockPos blockPos = BlockPos.containing(pos.x, eyeY, pos.z);
        FluidState fluidState = player.level().getFluidState(blockPos);
        double fluidY = blockPos.getY() + fluidState.getHeight(player.level(), blockPos);
        submergedInWater = fluidState.is(FluidTags.WATER) && fluidY > eyeY;
    }

    private void updateSwimming() {
        if (player.getAbilities().flying) {
            swimming = false;
            return;
        }

        if (swimming) {
            swimming = sprinting && touchingWater && !player.isPassenger();
        } else {
            FluidState state = player.level().getFluidState(BlockPos.containing(pos));
            swimming = sprinting && submergedInWater && !player.isPassenger() && state.is(FluidTags.WATER);
        }
    }

    private boolean updateMovementInFluid(boolean water, double speed) {
        Level world = player.level();
        if (isRegionUnloaded(world)) {
            return false;
        }

        AABB box = boundingBox.deflate(0.001);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);

        double maxHeight = 0.0;
        boolean found = false;
        Vec3 addedVelocity = Vec3.ZERO;
        int samples = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutable.set(x, y, z);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!(water ? fluidState.is(FluidTags.WATER) : fluidState.is(FluidTags.LAVA))) {
                        continue;
                    }

                    double height = y + fluidState.getHeight(world, mutable);
                    if (height < box.minY) {
                        continue;
                    }

                    found = true;
                    maxHeight = Math.max(height - box.minY, maxHeight);
                    Vec3 fluidVelocity = fluidState.getFlow(world, mutable);
                    if (maxHeight < 0.4) {
                        fluidVelocity = fluidVelocity.scale(maxHeight);
                    }
                    addedVelocity = addedVelocity.add(fluidVelocity);
                    samples++;
                }
            }
        }

        if (samples > 0) {
            addedVelocity = addedVelocity.scale(1.0 / samples).scale(speed);
            if (Math.abs(velocity.x) < 0.003 && Math.abs(velocity.z) < 0.003 && addedVelocity.length() < 0.0045) {
                addedVelocity = addedVelocity.normalize().scale(0.0045);
            }
            if (addedVelocity.lengthSqr() > 0.0) {
                velocity = velocity.add(addedVelocity);
            }
        }

        if (water) {
            waterHeight = maxHeight;
        } else {
            lavaHeight = maxHeight;
        }
        return found;
    }

    private boolean isRegionUnloaded(Level world) {
        AABB box = boundingBox.inflate(1.0);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);

        for (int x = minX >> 4; x <= (maxX - 1) >> 4; x++) {
            for (int z = minZ >> 4; z <= (maxZ - 1) >> 4; z++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void applyBubbleColumnEffects() {
        BlockPos pos = BlockPos.containing(this.pos);
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(Blocks.BUBBLE_COLUMN)) {
            return;
        }

        BlockState above = player.level().getBlockState(pos.above());
        boolean surface = above.getCollisionShape(player.level(), pos).isEmpty() && above.getFluidState().isEmpty();
        boolean drag = state.getValue(BubbleColumnBlock.DRAG_DOWN);

        double y;
        if (surface) {
            y = drag ? Math.max(-0.9, velocity.y - 0.03) : Math.min(1.8, velocity.y + 0.1);
        } else {
            y = drag ? Math.max(-0.3, velocity.y - 0.03) : Math.min(0.7, velocity.y + 0.06);
            onGround = true;
        }

        velocity = new Vec3(velocity.x, y, velocity.z);
    }

    private float getVelocityMultiplier() {
        if (player.getAbilities().flying || gliding) {
            return 1.0f;
        }

        BlockState state = player.level().getBlockState(BlockPos.containing(pos));
        float multiplier = state.getBlock().getSpeedFactor();
        if (!state.is(Blocks.WATER) && !state.is(Blocks.BUBBLE_COLUMN)) {
            if (multiplier == 1.0f) {
                multiplier = player.level().getBlockState(getVelocityAffectingPos()).getBlock().getSpeedFactor();
            }
        }
        return multiplier;
    }

    private boolean canEnterTrapdoor(BlockPos pos, BlockState state) {
        if (!(Boolean) state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        }

        BlockState belowState = player.level().getBlockState(pos.below());
        return belowState.is(Blocks.LADDER) && belowState.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
    }

    private Vec3 getRotationVector() {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    protected void setSimulatedVelocity(Vec3 velocity) {
        this.velocity = velocity;
    }

    protected void setSimulatedFallDistance(double fallDistance) {
        this.fallDistance = fallDistance;
    }

    protected void setSimulatedOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    protected void setSimulatedGliding(boolean gliding) {
        this.gliding = gliding;
    }


    Vec3 position() {
        return pos;
    }

    double simulatedFallDistance() {
        return fallDistance;
    }

    Vec3 simulatedVelocity() {
        return velocity;
    }

    boolean simulatedOnGround() {
        return onGround;
    }

    boolean simulatedClipLedged() {
        return clipLedged;
    }

}

