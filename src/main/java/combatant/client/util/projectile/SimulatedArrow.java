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

package combatant.client.util.projectile;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SimulatedArrow {

    private final ClientLevel world;
    private final boolean collideEntities;
    private final double gravity;
    private final double drag;
    private final double dragInWater;
    private final AbstractArrow collisionProbe;
    private Vec3 pos;
    private Vec3 velocity;
    private boolean inGround;

    public SimulatedArrow(ClientLevel world, Vec3 pos, Vec3 velocity, boolean collideEntities) {
        this(world, pos, velocity, collideEntities, 0.05, 0.99, 0.6);
    }

    /** Uses the trajectory data for the actual weapon, not hardcoded bow physics. */
    public SimulatedArrow(ClientLevel world, Vec3 pos, Vec3 velocity, boolean collideEntities,
                          double gravity, double drag, double dragInWater) {
        this.world = world;
        this.pos = pos;
        this.velocity = velocity;
        this.collideEntities = collideEntities;
        this.gravity = gravity;
        this.drag = drag;
        this.dragInWater = dragInWater;
        // Reuse one unattached collision probe per simulated projectile instead
        // of allocating an AbstractArrow for every simulated flight step.
        this.collisionProbe = new AbstractArrow(net.minecraft.world.entity.EntityTypes.ARROW,
                pos.x, pos.y, pos.z, world, ItemStack.EMPTY, ItemStack.EMPTY) {
            @Override protected ItemStack getDefaultPickupItem() {
                return new ItemStack(Items.ARROW);
            }
        };
    }

    public Vec3 getPos() {
        return pos;
    }

    /** Velocity before the next collision step, used for TNT minecart blast-power bounds. */
    public Vec3 getVelocity() {
        return velocity;
    }

    public HitResult tick() {
        if (inGround) {
            return null;
        }

        Vec3 previousPos = pos;
        Vec3 newPos = pos.add(velocity);
        double stepDrag = isTouchingWater() ? dragInWater : drag;

        velocity = velocity.scale(stepDrag);
        velocity = velocity.add(0.0, -gravity, 0.0);

        HitResult collision = updateCollision(previousPos, newPos);
        if (collision != null) {
            pos = collision.getLocation();
            inGround = true;
            return collision;
        }

        pos = newPos;
        return null;
    }

    private HitResult updateCollision(Vec3 start, Vec3 end) {
        collisionProbe.setPos(start.x, start.y, start.z);

        HitResult blockHit = world.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                collisionProbe
        ));

        if (collideEntities) {
            double size = 0.45;
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    collisionProbe,
                    start,
                    end,
                    new AABB(-size, -size, -size, size, size, size)
                            .move(start)
                            .expandTowards(end.subtract(start))
                            .inflate(1.0),
                    entity -> !entity.isSpectator() && entity.isAlive() && entity.isAttackable(),
                    end.distanceToSqr(start)
            );
            if (entityHit != null && entityHit.getType() != HitResult.Type.MISS) {
                return entityHit;
            }
        }

        return blockHit != null && blockHit.getType() != HitResult.Type.MISS ? blockHit : null;
    }

    private boolean isTouchingWater() {
        return world.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
    }
}
