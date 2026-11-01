/* Shared projectile aiming for moving shooters, entities and static hitboxes. */
package combatant.client.util.projectile;

import combatant.client.util.aiming.data.Rotation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * All consumers use the SAME motion model to derive angles and confirm hits.
 * Velocity inherited from the shooter, future entity boxes, collidable blocks
 * and intervening entities are handled in the final simulation.
 */
public final class ProjectileAimSolver {
    private ProjectileAimSolver() { }

    public record Solution(Rotation rotation, int impactTicks, double impactSpeed) { }
    private record Proposal(Rotation rotation, double error, int tick) { }
    // Planning for a small TNT cart must not use the full 0.5-block expansion
    // intended for broad living-entity targeting. Conservatively test a narrow
    // contact corridor; it is better to reject a marginal shot than to miss.
    private static final double CART_CONTACT_MARGIN = 0.035;
    // BowItem.shootProjectile passes vanilla inaccuracy=1.0 in 26.3.
    // A deterministic ray grazing the outer cart AABB is not a reliable hit.
    private static final double CART_INTERIOR_INSET = 0.085;
    // The 0.5-block PERSISTENT visualization radius is NOT a safe hit margin
    // for a real arrow against a bat, baby mob, etc. Use a small, entity-size
    // aware contact margin in both the aim optimizer and release validation.
    private static double livingContactRadius(LivingEntity entity) {
        double width = Math.min(entity.getBbWidth(), entity.getBbHeight());
        return width < 0.65 ? 0.10 : width < 1.0 ? 0.15 : 0.20;
    }

    public static Solution forEntity(Player player, ItemStack weapon, LivingEntity entity, boolean fullBow) {
        return forEntity(player, weapon, entity, fullBow, 20);
    }

    /** While charging, use the intended release strength instead of either the
     * instantaneous first draw tick or a hardcoded full-power shot (rapid fire). */
    public static Solution forEntity(Player player, ItemStack weapon, LivingEntity entity,
                                     boolean fullBow, int plannedBowChargeTicks) {
        if (player == null || entity == null || weapon == null || weapon.isEmpty()
                || !(player.level() instanceof ClientLevel level)) return null;
        TrajectoryInfo info = aimedTrajectory(player, weapon, fullBow, plannedBowChargeTicks);
        if (info == null) return null;
        info = info.withHitboxRadius(livingContactRadius(entity));
        return solve(level, player, info, ProjectileTarget.forEntity(entity), entity, false, 1, null);
    }

    private static TrajectoryInfo aimedTrajectory(Player player, ItemStack weapon,
                                                  boolean fullBow, int plannedBowChargeTicks) {
        if (weapon.is(Items.BOW) && fullBow) {
            int charge = Math.min(20, Math.max(1, plannedBowChargeTicks));
            if (player.isUsingItem() && player.getUseItem().is(Items.BOW)) {
                charge = Math.min(20, Math.max(charge, player.getTicksUsingItem()));
            }
            return TrajectoryInfo.bowWithUsageDuration(player, charge);
        }
        TrajectoryInfo.Typed trajectory = TrajectoryData.getRenderedTrajectoryInfo(player, weapon, fullBow);
        return trajectory == null ? null : trajectory.info();
    }

    public static Solution forBox(ClientLevel level, Player shooter, AABB box, int bowChargeTicks,
                                  Entity cart, boolean requireDry) {
        return forBox(level, shooter, box, bowChargeTicks, cart, requireDry, 1, null);
    }

    /** The minimum impact window is part of trajectory search, not a post-filter.
     * Prefer the previously synchronized direction when it still hits at this charge. */
    public static Solution forBox(ClientLevel level, Player shooter, AABB box, int bowChargeTicks,
                                  Entity cart, boolean requireDry, int minimumImpactTicks,
                                  Rotation preferred) {
        if (level == null || shooter == null || box == null) return null;
        TrajectoryInfo bow = TrajectoryInfo.bowWithUsageDuration(shooter, bowChargeTicks);
        if (bow == null) return null;
        TrajectoryInfo info = bow.withHitboxRadius(CART_CONTACT_MARGIN);
        AABB interior = box.deflate(CART_INTERIOR_INSET);
        return solve(level, shooter, info, ProjectileTarget.constant(interior.getCenter(), interior),
                cart, requireDry, Math.max(1, minimumImpactTicks), preferred);
    }

    /** Validates the ACTUAL queued angle and charge; choosing a different optimal
     * arc here would invalidate the rotation that was already sent to the server. */
    public static Solution validateBoxShot(ClientLevel level, Player shooter, AABB box,
                                           int bowChargeTicks, Entity cart, boolean requireDry,
                                           Rotation rotation, int minimumImpactTicks) {
        if (level == null || shooter == null || box == null || rotation == null) return null;
        TrajectoryInfo bow = TrajectoryInfo.bowWithUsageDuration(shooter, bowChargeTicks);
        if (bow == null) return null;
        TrajectoryInfo info = bow.withHitboxRadius(CART_CONTACT_MARGIN);
        AABB interior = box.deflate(CART_INTERIOR_INSET);
        Solution hit = simulate(level, shooter, info, rotation,
                ProjectileTarget.constant(interior.getCenter(), interior), cart, requireDry);
        return hit != null && hit.impactTicks() >= Math.max(1, minimumImpactTicks) ? hit : null;
    }

    public static boolean hitsEntity(Player player, ItemStack weapon, Rotation rotation,
                                     LivingEntity target, boolean fullBow) {
        if (player == null || target == null || rotation == null || weapon == null
                || !(player.level() instanceof ClientLevel level)) return false;
        // At release the actual draw duration matters: rapid-fire arrows do not
        // inherit the 3.0 velocity of a fully charged bow.
        int charge = weapon.is(Items.BOW) && player.isUsingItem()
                ? player.getTicksUsingItem() : 20;
        TrajectoryInfo info = aimedTrajectory(player, weapon, fullBow, charge);
        return info != null && simulate(level, player,
                info.withHitboxRadius(livingContactRadius(target)), rotation,
                ProjectileTarget.forEntity(target), target, false) != null;
    }

    private static Solution solve(ClientLevel level, Player shooter, TrajectoryInfo info,
                                  ProjectileTarget target, Entity intendedEntity, boolean requireDry,
                                  int minimumImpactTicks, Rotation preferred) {
        if (info.initialVelocity() <= 0.0) return null;
        Vec3 origin = shooter.getEyePosition().add(0.0, -0.1, 0.0);
        Vec3 shooterVelocity = inheritedVelocity(shooter, info);
        // Check a previously aimed direction before asking the optimizer to find
        // another arc. This prevents high-arc / direct-arc oscillations on draw ticks.
        if (preferred != null) {
            Solution held = simulate(level, shooter, info, preferred, target, intendedEntity, requireDry);
            if (held != null && held.impactTicks() >= minimumImpactTicks) return held;
        }
        List<Proposal> proposals = new ArrayList<>();
        double drag = info.drag();
        // Discrete Euler coefficients exactly match SimulatedArrow.tick():
        // the initial velocity acts before drag and gravity are applied.
        for (int tick = 1; tick <= 40; tick++) {
            ProjectileFlightCoefficients.Sample sample = ProjectileFlightCoefficients.at(
                    tick, drag, info.gravity());
            double sum = sample.velocitySum();
            double verticalDrift = sample.gravityDisplacement();
            AABB futureBox = target.getBoxInTicks(tick);
            if (futureBox == null) continue;
            Vec3 correction = futureBox.getCenter().subtract(origin)
                    .subtract(shooterVelocity.scale(sum)).subtract(0.0, verticalDrift, 0.0);
            double desiredLength = info.initialVelocity() * sum;
            double ratio = correction.length() / desiredLength;
            // The target has nonzero volume, so a slightly short/long flight
            // can still enter it. Confirm using the actual simulated segment.
            if (ratio < 0.75 || ratio > 1.25) continue;
            Rotation r = Rotation.fromRotationVec(correction.normalize()).normalize();
            proposals.add(new Proposal(r, Math.abs(ratio - 1.0), tick));
        }
        // Point-blank shots can intersect a large box before reaching the
        // mathematically ideal integer flight time.
        Rotation direct = Rotation.fromRotationVec(target.getBoxInTicks(0).getCenter()
                .subtract(origin).normalize()).normalize();
        proposals.add(new Proposal(direct, 0.24, 1));
        proposals.sort((a, b) -> ProjectileArcOrder.compare(
                a.tick(), a.error(), a.rotation().yaw(), a.rotation().pitch(),
                b.tick(), b.error(), b.rotation().yaw(), b.rotation().pitch(),
                preferred != null, preferred == null ? 0 : preferred.yaw(),
                preferred == null ? 0 : preferred.pitch()));
        Solution best = searchCandidates(level, shooter, info, target, intendedEntity, requireDry,
                minimumImpactTicks, proposals);
        if (best != null || preferred == null) return best;
        // A no-longer-viable high arc must not hide a viable lower alternative.
        // Retry with short-flight ordering only when the continuity pass failed.
        proposals.sort((a, b) -> ProjectileArcOrder.compare(
                a.tick(), a.error(), a.rotation().yaw(), a.rotation().pitch(),
                b.tick(), b.error(), b.rotation().yaw(), b.rotation().pitch(), false, 0, 0));
        return searchCandidates(level, shooter, info, target, intendedEntity, requireDry,
                minimumImpactTicks, proposals);
    }

    private static Solution searchCandidates(ClientLevel level, Player shooter, TrajectoryInfo info,
                                             ProjectileTarget target, Entity intendedEntity,
                                             boolean requireDry, int minimumImpactTicks,
                                             List<Proposal> proposals) {
        int verified = 0;
        for (Proposal candidate : proposals) {
            if (candidate.tick() + 2 < minimumImpactTicks) continue;
            if (!roughlyHits(level, shooter, info, candidate.rotation(), target)) continue;
            Solution hit = simulate(level, shooter, info, candidate.rotation(), target,
                    intendedEntity, requireDry);
            if (hit != null && hit.impactTicks() >= minimumImpactTicks) return hit;
            if (++verified >= 20) break;
        }
        return null;
    }

    /** Cheap integration before expensive world collision checks. */
    private static boolean roughlyHits(ClientLevel level, Player shooter, TrajectoryInfo info, Rotation rotation,
                                       ProjectileTarget target) {
        Vec3 velocity = rotation.directionVector().normalize().scale(info.initialVelocity());
        velocity = velocity.add(inheritedVelocity(shooter, info));
        Vec3 pos = shooter.getEyePosition().add(0.0, -0.1, 0.0);
        for (int tick = 1; tick <= 40; tick++) {
            Vec3 next = pos.add(velocity);
            AABB box = target.getBoxInTicks(tick);
            if (box != null && box.inflate(info.hitboxRadius()).clip(pos, next).isPresent()) return true;
            pos = next;
            double stepDrag = level.getFluidState(net.minecraft.core.BlockPos.containing(pos))
                    .is(net.minecraft.tags.FluidTags.WATER) ? info.dragInWater() : info.drag();
            velocity = velocity.scale(stepDrag).add(0, -info.gravity(), 0);
        }
        return false;
    }

    private static Solution simulate(ClientLevel level, Player shooter, TrajectoryInfo info,
                                     Rotation rotation, ProjectileTarget target,
                                     Entity intendedEntity, boolean requireDry) {
        Vec3 direction = rotation.directionVector().normalize();
        Vec3 velocity = direction.scale(info.initialVelocity());
        velocity = velocity.add(inheritedVelocity(shooter, info));
        SimulatedArrow arrow = new SimulatedArrow(level, shooter.getEyePosition().add(0.0, -0.1, 0.0),
                velocity, false, info.gravity(), info.drag(), info.dragInWater());
        for (int tick = 1; tick <= 40; tick++) {
            Vec3 start = arrow.getPos();
            if (requireDry && wet(level, start)) return null;
            Vec3 currentVelocity = arrow.getVelocity();
            HitResult collision = arrow.tick();
            Vec3 end = arrow.getPos();
            AABB box = target.getBoxInTicks(tick);
            var contact = box == null ? java.util.Optional.<Vec3>empty()
                    : box.inflate(info.hitboxRadius()).clip(start, end);
            if (contact.isPresent()) {
                Vec3 point = contact.get();
                if (collision != null && collision.getType() == HitResult.Type.BLOCK
                        && start.distanceToSqr(collision.getLocation()) + 1e-6 < start.distanceToSqr(point)) return null;
                if (interveningEntity(level, shooter, intendedEntity, start, point)) return null;
                if (requireDry && wetSegment(level, start, point)) return null;
                return new Solution(rotation, tick, currentVelocity.length());
            }
            if (collision != null && collision.getType() == HitResult.Type.BLOCK) return null;
            if (interveningEntity(level, shooter, intendedEntity, start, end)) return null;
            if (requireDry && wetSegment(level, start, end)) return null;
        }
        return null;
    }

    /** Matches Projectile.shootFromRotation() in Minecraft 26.3: launch
     * inherits getKnownMovement(), suppressing vertical motion on ground.
     * getDeltaMovement() is not the same source and causes moving-shot misses. */
    private static Vec3 inheritedVelocity(Player shooter, TrajectoryInfo info) {
        if (!info.copiesPlayerVelocity()) return Vec3.ZERO;
        Vec3 known = shooter.getKnownMovement();
        return new Vec3(known.x, shooter.onGround() ? 0.0 : known.y, known.z);
    }

    private static boolean interveningEntity(ClientLevel level, Player shooter, Entity intended,
                                              Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(0.35);
        for (Entity e : level.getEntities(shooter, sweep,
                other -> other != shooter && other.isAlive() && other.isPickable())) {
            if (e == intended) continue;
            // A future TNT cart is not present yet, but existing carts ARE
            // obstacles and must not be ignored when predicting its impact.
            if (e.getBoundingBox().inflate(0.17).clip(from, to).isPresent()) return true;
        }
        return false;
    }

    private static boolean wet(ClientLevel level, Vec3 position) {
        var pos = net.minecraft.core.BlockPos.containing(position);
        // Rain does not impart water drag to a flying arrow. Only fluid
        // intersection should disqualify the dry-trajectory fast path.
        return level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER);
    }

    private static boolean wetSegment(ClientLevel level, Vec3 from, Vec3 to) {
        int count = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 2));
        for (int i = 1; i <= count; i++) if (wet(level, from.lerp(to, i / (double) count))) return true;
        return false;
    }
}
