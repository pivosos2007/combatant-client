/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.combat.instacart;

import combatant.client.util.aiming.data.Rotation;
import combatant.client.features.module.modules.combat.autocrystal.AutoCrystalDamageRules;
import combatant.client.util.projectile.ProjectileAimSolver;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/** Pure planning/ballistics layer: never swaps items, shoots or sends an interaction. */
public final class InstaCartPlanner {
    // Minecraft 26.3 MinecartTNT.explode(): base + speedFactor * random[0,1) * 1.5 * min(arrowSpeed, 5).
    // Vanilla defaults are base=4, speedFactor=1. Target damage uses the lower bound;
    // self damage uses the upper bound to avoid accepting potentially lethal shots.
    private static final float MIN_EXPLOSION_POWER = 4.0f;
    private static final double CART_WIDTH = 0.98;
    private static final double CART_HEIGHT = 0.70;

    private InstaCartPlanner() { }

    public record Settings(double placeRange, float minDamage,
                           float maxSelfDamage, float faceplaceHealth, int predictTicks,
                           int selfPredictTicks, int maxChargeTicks, int fastExtraTicks) { }

    public record Shot(Rotation rotation, int chargeTicks, int impactTicks, double impactSpeed) { }

    public record Site(BlockPos rail, BlockHitResult interaction, boolean existingRail,
                       Vec3 explosionPoint, float targetDamage, float selfDamage,
                       Shot fastShot, double score) { }

    public record SearchResult(Site site, String reason) { }

    /** Provides the HUD with the *first actual blocking stage* instead of silently returning null. */
    public static SearchResult findBestDetailed(ClientLevel level, LocalPlayer player, LivingEntity target,
                                               Settings settings, BlockPos excluded) {
        return findBestDetailed(level, player, target, settings,
                excluded == null ? Set.of() : Set.of(excluded));
    }

    /** Skip every recently failed rail position, not only the last one. */
    public static SearchResult findBestDetailed(ClientLevel level, LocalPlayer player, LivingEntity target,
                                                Settings settings, Set<BlockPos> excluded) {
        if (level == null || player == null || target == null)
            return new SearchResult(null, "World or target unavailable");

        BlockPos origin = target.blockPosition();
        List<Site> candidates = new ArrayList<>();
        int unplaceable = 0, unreachable = 0, existingCart = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz > 10) continue;
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (excluded.contains(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    boolean rail = state.is(BlockTags.RAILS);
                    if (!rail && (!state.canBeReplaced()
                            || !Blocks.RAIL.defaultBlockState().canSurvive(level, pos)
                            || !level.isUnobstructed(Blocks.RAIL.defaultBlockState(), pos,
                                    net.minecraft.world.phys.shapes.CollisionContext.placementContext(player)))) {
                        unplaceable++;
                        continue;
                    }
                    BlockHitResult hit = interaction(level, player, pos, rail, settings.placeRange());
                    if (hit == null) {
                        unreachable++;
                        continue;
                    }
                    // The old 0.7-block inflation rejected neighbouring rails as
                    // "occupied" even when the cart stood on another block.
                    boolean occupied = level.getEntitiesOfClass(AbstractMinecart.class,
                            new AABB(pos).inflate(0.2, 0.6, 0.2)).stream().anyMatch(cart ->
                            Math.abs(cart.getX() - (pos.getX() + 0.5)) < 0.49
                            && Math.abs(cart.getZ() - (pos.getZ() + 0.5)) < 0.49);
                    if (occupied) {
                        existingCart++;
                        continue;
                    }
                    Vec3 center = cartSpawnPoint(level, pos).add(0.0, 0.35, 0.0);
                    float preliminaryDamage = ExplosionDamageUtil.getExplosionDamage(target, center,
                            MIN_EXPLOSION_POWER, settings.predictTicks(), false);
                    candidates.add(new Site(pos.immutable(), hit, rail, center,
                            preliminaryDamage, 0.0f, null, preliminaryDamage));
                }
            }
        }
        if (candidates.isEmpty()) {
            return new SearchResult(null, unreachable > 0 ? "Placement out of reach / blocked"
                    : existingCart > 0 ? "TNT cart occupies rail" : "No placeable rail support");
        }
        candidates.sort(Comparator.comparingDouble(Site::score).reversed());
        Site best = null;
        Site preparation = null;
        int noShot = 0, noWindow = 0, lowDamage = 0, unsafe = 0;
        int preparationLowDamage = 0, preparationUnsafe = 0;
        int preparationChecks = 0;
        // Preserve breadth without simulating several thousand arrow flights per
        // target: check the strongest 16 first and then evenly sample the rest.
        int budget = Math.min(48, candidates.size());
        for (int iteration = 0; iteration < budget; iteration++) {
            int i = iteration < 16 ? iteration
                    : 16 + (int) ((long) (iteration - 16) * Math.max(0, candidates.size() - 16)
                            / Math.max(1, budget - 16));
            if (i >= candidates.size()) break;
            Site candidate = candidates.get(i);
            Shot shot = null;
            boolean hasFastWindow = false;
            int maxCharge = Math.min(20, settings.maxChargeTicks());
            int minFlight = InstaCartCycleRules.minimumFastFlightTicks(
                    candidate.existingRail(), settings.fastExtraTicks());
            for (int charge = maxCharge; charge >= Math.min(10, maxCharge);
                 charge -= Math.max(1, (maxCharge - 10) / 4)) {
                Shot prediction = predict(level, player, cartBox(level, candidate.rail()),
                        charge, null, minFlight, null);
                if (prediction == null) continue;
                if (shot == null) shot = prediction;
                if (prediction.impactTicks() >= minFlight
                        && prediction.impactTicks() <= 16
                        && validateShot(level, player, cartBox(level, candidate.rail()),
                                Math.min(20, charge + 1), null, minFlight,
                                prediction.rotation()) != null) {
                    shot = prediction;
                    hasFastWindow = true;
                    break;
                }

            }
            if (shot == null || !hasFastWindow) {
                if (shot == null) noShot++; else noWindow++;
                // The highest-damage sites are also frequently the most unsafe
                // to the shooter. Evaluate some of the sampled alternatives,
                // not just the first eight, before declaring no plan.
                // Do not litter the world with TNT carts which cannot be
                // shot even along a direct line. A future movement window can
                // still change the exact arc, but the lane must be unobstructed.
                if (!hasDirectArrowLane(level, player, candidate.explosionPoint())) continue;
                if (preparationChecks++ >= 24) continue;
                // Preparing the rail/cart is useful precisely when an arrow
                // trajectory does NOT exist YET. The previous direct != null
                // prerequisite made the entire prepare-first state unreachable
                // for the "No clear, dry arrow trajectory" case in latest(9).
                // Placing an unprimed cart cannot detonate it; once present,
                // WAIT_SHOT must verify the real entity hitbox and damage safety
                // before drawing and releasing the Flame bow.
                // At planning time assess the minimum TNT explosion to avoid
                // positions with no useful damage even if a later shot becomes
                // available. Permission to FIRE is always checked again later.
                Shot baseline = new Shot(new Rotation(0.0f, 0.0f), 6, 1, 0.0);
                float dealt = estimatedTargetDamage(target, candidate.explosionPoint(),
                        baseline, settings.predictTicks());
                float self = estimatedSelfDamage(player, candidate.explosionPoint(),
                        baseline, settings.selfPredictTicks());
                boolean override = AutoCrystalDamageRules.shouldOverrideMaxSelfDamage(
                        player, target, dealt, self, settings.maxSelfDamage());
                boolean useful = dealt >= settings.minDamage()
                        || AutoCrystalDamageRules.shouldOverrideMinDamage(target, dealt, settings.faceplaceHealth());
                boolean safe = (self <= settings.maxSelfDamage() || override)
                        && AutoCrystalDamageRules.isSafe(player, self, override);
                if (!useful) preparationLowDamage++;
                else if (!safe) preparationUnsafe++;
                else {
                    Site prepared = new Site(candidate.rail(), candidate.interaction(),
                            candidate.existingRail(), candidate.explosionPoint(), dealt, self,
                            null, dealt);
                    if (isBetter(prepared, preparation)) preparation = prepared;
                }
                continue;
            }
            float dealt = estimatedTargetDamage(target, candidate.explosionPoint(), shot, settings.predictTicks());
            if (!(dealt >= settings.minDamage()
                    || AutoCrystalDamageRules.shouldOverrideMinDamage(target, dealt, settings.faceplaceHealth()))) {
                lowDamage++;
                continue;
            }
            float self = estimatedSelfDamage(player, candidate.explosionPoint(), shot, settings.selfPredictTicks());
            boolean override = AutoCrystalDamageRules.shouldOverrideMaxSelfDamage(player, target,
                    dealt, self, settings.maxSelfDamage());
            if ((self > settings.maxSelfDamage() && !override)
                    || !AutoCrystalDamageRules.isSafe(player, self, override)) {
                unsafe++;
                continue;
            }
            Site viable = new Site(candidate.rail(), candidate.interaction(), candidate.existingRail(),
                    candidate.explosionPoint(), dealt, self, shot, dealt);
            if (isBetter(viable, best)) best = viable;
        }
        if (best != null) return new SearchResult(best, "Ready");
        if (preparation != null) return new SearchResult(preparation, "Prepare cart before shot");
        // Give an actionable reason in TargetHUD, not a generic "no position".
        String reason = (unsafe > 0 || preparationUnsafe > 0) ? "Self damage exceeds limit"
                : (lowDamage > 0 || preparationLowDamage > 0) ? "Damage below Min Damage"
                : noWindow > 0 ? "Arrow arrives before placement window"
                : noShot > 0 ? "No clear, dry arrow trajectory"
                : "No viable rail position";
        return new SearchResult(null, reason);
    }

    /** Existing TNT carts are first-class opportunities; no placement window is needed. */
    public record ExistingCart(Site site, MinecartTNT cart) { }

    public static ExistingCart findExistingCart(ClientLevel level, LocalPlayer player,
                                                 LivingEntity target, Settings settings, double searchRange) {
        return findExistingCart(level, player, target, settings, searchRange, Set.of());
    }

    public static ExistingCart findExistingCart(ClientLevel level, LocalPlayer player,
                                                 LivingEntity target, Settings settings, double searchRange,
                                                 Set<Integer> ignoredCartIds) {
        if (level == null || player == null || target == null) return null;
        ExistingCart best = null;
        for (MinecartTNT cart : level.getEntitiesOfClass(MinecartTNT.class,
                player.getBoundingBox().inflate(searchRange))) {
            if (!cart.isAlive() || cart.isRemoved() || ignoredCartIds.contains(cart.getId())) continue;
            Shot shot = findSafePreparedShot(level, player, target, cart, settings, null);
            if (shot == null) continue;
            Vec3 explosion = cart.position().add(0, 0.35, 0);
            float damage = estimatedTargetDamage(target, explosion, shot, settings.predictTicks());
            float self = estimatedSelfDamage(player, explosion, shot, settings.selfPredictTicks());
            Site site = new Site(cart.blockPosition(), null, true, explosion, damage, self, shot, damage);
            if (best == null || isBetter(site, best.site())) best = new ExistingCart(site, cart);
        }
        return best;
    }

    public static Site findBest(ClientLevel level, LocalPlayer player, LivingEntity target,
                                Settings settings, BlockPos excluded) {
        return findBestDetailed(level, player, target, settings, excluded).site();
    }

    /** Near-equal damage favours the lower self-damage, as in AutoCrystal candidate selection. */
    public static boolean isBetter(Site candidate, Site current) {
        if (current == null) return true;
        // Prefer an executable single-pass shot to setup-first when both are
        // available, regardless of a small difference in estimated damage.
        if ((candidate.fastShot() != null) != (current.fastShot() != null))
            return candidate.fastShot() != null;
        if (Math.abs(candidate.targetDamage() - current.targetDamage()) < 1.0f)
            return candidate.selfDamage() < current.selfDamage();
        return candidate.targetDamage() > current.targetDamage();
    }

    public static float estimatedSelfDamage(LocalPlayer player, Vec3 explosion, Shot shot, int selfPredictTicks) {
        return ExplosionDamageUtil.getExplosionDamage(player, explosion,
                maxExplosionPower(shot), selfPredictTicks, false);
    }

    private static float maxExplosionPower(Shot shot) {
        return MIN_EXPLOSION_POWER + (float) (1.5 * Math.min(shot.impactSpeed(), 5.0));
    }

    private static float expectedExplosionPower(Shot shot) {
        // MinecartTNT uses nextDouble() in [0,1); the expected multiplier is 0.5.
        return MIN_EXPLOSION_POWER + (float) (0.75 * Math.min(shot.impactSpeed(), 5.0));
    }

    public static float estimatedTargetDamage(LivingEntity target, Vec3 explosion, Shot shot, int predictTicks) {
        return ExplosionDamageUtil.getExplosionDamage(target, explosion,
                expectedExplosionPower(shot), predictTicks, false);
    }

    public static boolean isDamageAcceptable(LocalPlayer player, LivingEntity target, Vec3 explosion,
                                             Shot shot, Settings settings) {
        if (player == null || target == null || explosion == null || shot == null) return false;
        float damage = estimatedTargetDamage(target, explosion, shot, settings.predictTicks());
        if (!(damage > settings.minDamage()
                || AutoCrystalDamageRules.shouldOverrideMinDamage(target, damage, settings.faceplaceHealth()))) return false;
        float self = estimatedSelfDamage(player, explosion, shot, settings.selfPredictTicks());
        boolean override = AutoCrystalDamageRules.shouldOverrideMaxSelfDamage(player, target,
                damage, self, settings.maxSelfDamage());
        return (self <= settings.maxSelfDamage() || override)
                && AutoCrystalDamageRules.isSafe(player, self, override);
    }

    private static boolean hasDirectArrowLane(ClientLevel level, LocalPlayer player, Vec3 point) {
        Vec3 from = player.getEyePosition().add(0.0, -0.1, 0.0);
        HitResult obstruction = level.clip(new ClipContext(from, point,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() != HitResult.Type.BLOCK;
    }

    /** A confirmed cart is always aimed at using its real hitbox. Charge
     * selection is shared by initial acquisition and the prepared-cart loop,
     * avoiding the old first-hit-at-six-ticks behaviour. */
    public static Shot findSafePreparedShot(ClientLevel level, LocalPlayer player,
                                            LivingEntity target, MinecartTNT cart,
                                            Settings settings, Rotation preferred) {
        if (level == null || player == null || target == null || cart == null) return null;
        Vec3 explosion = cart.position().add(0.0, 0.35, 0.0);
        double distance = player.getEyePosition().distanceTo(cart.getBoundingBox().getCenter());
        for (int charge : InstaCartCycleRules.chargeOrder(distance, settings.maxChargeTicks())) {
            Shot prediction = predict(level, player, cart.getBoundingBox(),
                    charge, cart, 1, preferred);
            if (prediction != null && isDamageAcceptable(
                    player, target, explosion, prediction, settings)) return prediction;
        }
        return null;
    }

    public static boolean stillPlaceable(ClientLevel level, LocalPlayer player, Site site, double range) {
        if (level == null || player == null || site == null) return false;
        BlockState state = level.getBlockState(site.rail());
        if (state.is(BlockTags.RAILS)) return interaction(level, player, site.rail(), true, range) != null;
        if (!state.canBeReplaced() || !Blocks.RAIL.defaultBlockState().canSurvive(level, site.rail()))
            return false;
        if (!level.isUnobstructed(Blocks.RAIL.defaultBlockState(), site.rail(),
                net.minecraft.world.phys.shapes.CollisionContext.placementContext(player))) return false;
        return interaction(level, player, site.rail(), false, range) != null;
    }

    /**
     * Vanilla MinecartItem.useOn() acts on the *clicked* rail, whereas BlockItem.place()
     * uses BlockPlaceContext to choose the block adjacent to the clicked support.
     * Use the actual world clip hit and face; never fabricate a BlockHitResult whose
     * direction does not match its hit location or whose shape cannot be targeted.
     */
    public static BlockHitResult interaction(ClientLevel level, LocalPlayer player, BlockPos rail,
                                             boolean existingRail, double range) {
        if (level == null || player == null) return null;
        BlockPos clicked = existingRail ? rail : rail.below();
        if (existingRail && !level.getBlockState(rail).is(BlockTags.RAILS)) return null;
        Vec3 eyes = player.getEyePosition();
        // A thin rail/slope can be occluded at its center. Test the actual voxel
        // shape at several offsets, and retain only true ray-trace contacts.
        double[] offsets = {0.5, 0.25, 0.75};
        double[] heights = existingRail ? new double[] {0.01, 0.055, 0.12, 0.42, 0.75}
                                        : new double[] {0.96};
        for (double dx : offsets) for (double dz : offsets) for (double dy : heights) {
            Vec3 sample = new Vec3(clicked.getX() + dx, clicked.getY() + dy, clicked.getZ() + dz);
            if (eyes.distanceToSqr(sample) > (range + 0.25) * (range + 0.25)) continue;
            Vec3 end = sample.add(sample.subtract(eyes).normalize().scale(0.3));
            BlockHitResult hit = level.clip(new ClipContext(eyes, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(clicked)
                    || eyes.distanceToSqr(hit.getLocation()) > range * range) continue;
            if (existingRail) {
                if (!level.getBlockState(hit.getBlockPos()).is(BlockTags.RAILS)) continue;
            } else {
                // Rail placement has to target the upper face of the support.
                if (hit.getDirection() != Direction.UP) continue;
                BlockPlaceContext ctx = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND,
                        Items.RAIL.getDefaultInstance(), hit);
                if (!ctx.canPlace() || !ctx.getClickedPos().equals(rail)) continue;
            }
            return hit; // first valid center-biased sample; avoid dozens of extra clips
        }
        return null;
    }

    /** Mirror MinecartItem.useOn() for existing rails; new rails are predicted flat
     * until the server confirms their final shape. */
    private static Vec3 cartSpawnPoint(ClientLevel level, BlockPos rail) {
        BlockState state = level.getBlockState(rail);
        double slope = state.getBlock() instanceof BaseRailBlock baseRail
                && state.getValue(baseRail.getShapeProperty()).isSlope() ? 0.5 : 0.0;
        return new Vec3(rail.getX() + 0.5, rail.getY() + 0.0625 + slope, rail.getZ() + 0.5);
    }

    public static AABB cartBox(ClientLevel level, BlockPos rail) {
        Vec3 pos = cartSpawnPoint(level, rail);
        return new AABB(pos.x - CART_WIDTH / 2.0, pos.y, pos.z - CART_WIDTH / 2.0,
                pos.x + CART_WIDTH / 2.0, pos.y + CART_HEIGHT, pos.z + CART_WIDTH / 2.0);
    }

    public static Shot predict(ClientLevel level, LocalPlayer player, AABB cartBox, int chargeTicks) {
        return predict(level, player, cartBox, chargeTicks, null);
    }

    public static Shot predict(ClientLevel level, LocalPlayer player, AABB cartBox,
                               int chargeTicks, Entity existingCart) {
        return predict(level, player, cartBox, chargeTicks, existingCart, 1, null);
    }

    public static Shot predict(ClientLevel level, LocalPlayer player, AABB cartBox,
                               int chargeTicks, Entity existingCart, int minFlight, Rotation preferred) {
        ProjectileAimSolver.Solution solution = ProjectileAimSolver.forBox(
                level, player, cartBox, chargeTicks, existingCart, true, minFlight, preferred);
        return solution == null || (existingCart != null && solution.impactTicks() > 16)
                ? null : new Shot(solution.rotation(), chargeTicks,
                solution.impactTicks(), solution.impactSpeed());
    }

    /** Do not substitute an unrelated ballistic solution for the angle already synchronized. */
    public static Shot validateShot(ClientLevel level, LocalPlayer player, AABB cartBox,
                                    int chargeTicks, Entity existingCart, int minFlight, Rotation rotation) {
        ProjectileAimSolver.Solution solution = ProjectileAimSolver.validateBoxShot(
                level, player, cartBox, chargeTicks, existingCart, true, rotation, minFlight);
        return solution == null || (existingCart != null && solution.impactTicks() > 16)
                ? null : new Shot(solution.rotation(), chargeTicks,
                solution.impactTicks(), solution.impactSpeed());
    }
}
