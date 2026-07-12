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

package combatant.client.features.module.modules.movement;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.Scaffold;
import combatant.client.mixins.accessors.PersistentProjectileEntityAccessor;
import combatant.client.util.player.simulation.PlayerSimulationCache;
import combatant.client.util.projectile.SimulatedArrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//todo Description
@ModuleInfo(
        id = "autododge",
        displayName = "AutoDodge",
        category = ModuleCategory.MOVEMENT
)
public final class AutoDodge extends Module {

    private static final String THREAT_ARROW = "arrow";
    private static final String THREAT_TRIDENT = "trident";
    private static final String THREAT_FIREBALL = "fireball";
    private static final String THREAT_WIND_CHARGE = "wind_charge";
    private static final String THREAT_SHULKER_BULLET = "shulker_bullet";
    private static final String THREAT_POTION = "potion";

    private static final double SAFE_DISTANCE = 1.5 * 0.3 + 1.5 * 0.5;
    private static final double DEFAULT_DANGER_PADDING = 1.5;
    private static final double INPUT_DEAD_DOT = 0.25;
    private static final double PLAYER_HALF_WIDTH = 0.3;
    private static final double PATH_STEP_SPEED = 0.13;
    private static final double COLLISION_MARGIN = 0.03;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanMapValue threats = group("autododgeThreats", defaultThreats());
    private final NumberValue<Double> range = num("autododgeRange", 24.0, 4.0, 96.0);
    private final NumberValue<Integer> maxTicks = num("autododgeMaxTicks", 80, 10, 120);
    private final NumberValue<Integer> maxThreats = num("autododgeMaxThreats", 24, 1, 96);
    private final NumberValue<Double> dangerPadding =
            num("autododgeDangerPadding", DEFAULT_DANGER_PADDING, 0.45, 3.0);
    private final BooleanValue allowRotationChange = bool("autododgeAllowRotationChange", false);
    private final BooleanValue allowJump =
            visibleWhen(bool("autododgeAllowJump", true), allowRotationChange::get);
    private final BooleanValue allowTimer = bool("autododgeAllowTimer", false);
    private final NumberValue<Float> timerSpeed =
            visibleWhen(num("autododgeTimerSpeed", 2.0f, 1.0f, 10.0f), allowTimer::get);
    private final NumberValue<Integer> pathCheckTicks =
            num("autododgePathCheckTicks", 5, 1, 12);
    private final BooleanValue fallFailsafe = bool("autododgeFallFailsafe", true);
    private final BooleanValue edgeSafeWalk =
            visibleWhen(bool("autododgeEdgeSafeWalk", true), fallFailsafe::get);
    private final NumberValue<Double> maxPredictedFallDamage =
            visibleWhen(num("autododgeMaxPredictedFallDamage", 4.0, 0.0, 20.0), fallFailsafe::get);
    private final BooleanValue ignoreOpenInventory = bool("autododgeIgnoreOpenInventory", true);
    private final BooleanValue ignoreUsingItem = bool("autododgeIgnoreUsingItem", true);
    private final BooleanValue ignoreScaffold = bool("autododgeIgnoreScaffold", true);

    private boolean timerActive;
    private boolean dodgedThisTick;
    private int lastDodgeAge = -1;

    private static Map<String, Boolean> defaultThreats() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(THREAT_ARROW, true);
        defaults.put(THREAT_TRIDENT, true);
        defaults.put(THREAT_FIREBALL, true);
        defaults.put(THREAT_WIND_CHARGE, true);
        defaults.put(THREAT_SHULKER_BULLET, true);
        defaults.put(THREAT_POTION, false);
        return defaults;
    }

    @Override
    public void onDisable() {
        clearTimer();
        dodgedThisTick = false;
    }

    @Override
    public void onTick() {
        if (timerActive && !dodgedThisTick) {
            clearTimer();
        }
        dodgedThisTick = false;
    }

    @EventHandler(priority = -100)
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = mc.player;
        if (!canOperate(player)) {
            return;
        }

        List<Entity> candidates = findThreats(player);
        if (candidates.isEmpty()) {
            return;
        }

        HitInfo hit = getInflictedHit(candidates);
        if (hit == null) {
            return;
        }

        DodgePlan plan = planEvasion(player, hit);
        if (plan == null) {
            return;
        }

        applyInput(event, plan.input());

        if (plan.yawChange() != null) {
            player.setYRot(plan.yawChange());
        }

        if (plan.shouldJump() && allowJump.get() && player.onGround()) {
            event.setJump(true);
        }

        boolean edgeRecovered = applyEdgeSafeWalk(player, event);

        if (!edgeRecovered && allowTimer.get() && plan.useTimer()) {
            Timer.setExternalTickTimer(timerSpeed.get());
            timerActive = true;
        }

        dodgedThisTick = true;
        lastDodgeAge = player.tickCount;
    }

    public boolean hasDodgedRecently(LocalPlayer player, int ticks) {
        if (!isEnabled() || player == null || lastDodgeAge < 0) {
            return false;
        }

        int delta = player.tickCount - lastDodgeAge;
        return delta >= 0 && delta <= ticks;
    }

    public boolean isTimerActive() {
        return timerActive;
    }

    private boolean canOperate(LocalPlayer player) {
        if (!isEnabled() || player == null || mc.level == null) {
            return false;
        }

        if (!ignoreOpenInventory.get()
                && (ClientScreen.current() instanceof AbstractContainerScreen<?>)) {
            return false;
        }

        if (!ignoreUsingItem.get() && player.isUsingItem()) {
            return false;
        }

        Scaffold scaffold = Modules.get(Scaffold.class);
        return ignoreScaffold.get() || scaffold == null || !scaffold.isEnabled();
    }

    private List<Entity> findThreats(LocalPlayer player) {
        List<Entity> out = new ArrayList<>();
        double maxRangeSq = range.get() * range.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == player || !entity.isAlive()) {
                continue;
            }
            if (entity.distanceToSqr(player) > maxRangeSq) {
                continue;
            }
            if (entity instanceof Projectile projectile && projectile.getOwner() == player) {
                continue;
            }
            if (!isEnabledThreat(entity)) {
                continue;
            }
            if (entity.getDeltaMovement().lengthSqr() < 1.0E-7) {
                continue;
            }

            out.add(entity);
        }

        out.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        int limit = Math.max(1, maxThreats.get());
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private boolean isEnabledThreat(Entity entity) {
        if (entity instanceof ThrownTrident trident) {
            return threats.get(THREAT_TRIDENT) && !trident.isNoPhysics() && !isInGround(trident);
        }

        if (entity instanceof AbstractArrow projectile) {
            return threats.get(THREAT_ARROW) && !projectile.isNoPhysics() && !isInGround(projectile);
        }

        if (entity instanceof Fireball) {
            return threats.get(THREAT_FIREBALL);
        }

        if (entity instanceof WindCharge) {
            return threats.get(THREAT_WIND_CHARGE);
        }

        if (entity instanceof ShulkerBullet) {
            return threats.get(THREAT_SHULKER_BULLET);
        }

        if (entity instanceof AbstractThrownPotion || entity instanceof Snowball) {
            return threats.get(THREAT_POTION);
        }

        return false;
    }

    private HitInfo getInflictedHit(List<Entity> threats) {
        PlayerSimulationCache.SimulatedPlayerCache playerSimulation =
                PlayerSimulationCache.getSimulationForLocalPlayer();
        if (playerSimulation == null) {
            return null;
        }

        List<ThreatSimulation> simulations = new ArrayList<>(threats.size());
        for (Entity threat : threats) {
            ThreatSimulation simulation = createSimulation(threat);
            if (simulation != null) {
                simulations.add(simulation);
            }
        }

        double expansion = Math.max(SAFE_DISTANCE, dangerPadding.get());
        int ticks = Math.max(1, maxTicks.get());
        for (int tick = 0; tick < ticks; tick++) {
            PlayerSimulationCache.SimulatedPlayerSnapshot snapshot =
                    playerSimulation.getSnapshotAt(tick + 1);
            if (snapshot == null) {
                break;
            }

            AABB playerBox = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)
                    .inflate(expansion)
                    .move(snapshot.pos());

            for (ThreatSimulation simulation : simulations) {
                if (!simulation.tick()) {
                    continue;
                }

                Vec3 hitPos = playerBox.clip(simulation.previousPos(), simulation.pos()).orElse(null);
                if (hitPos != null) {
                    return new HitInfo(
                            tick,
                            simulation.entity(),
                            hitPos,
                            simulation.previousPos(),
                            simulation.velocity()
                    );
                }
            }
        }

        return null;
    }

    private ThreatSimulation createSimulation(Entity entity) {
        if (entity instanceof AbstractArrow
                || entity instanceof ThrownTrident
                || entity instanceof AbstractThrownPotion
                || entity instanceof Snowball) {
            return new ArrowLikeThreatSimulation(entity);
        }

        return new LinearThreatSimulation(entity);
    }

    private DodgePlan planEvasion(LocalPlayer player, HitInfo hit) {
        Vec3 threatVelocity = horizontal(hit.velocity());
        if (threatVelocity.lengthSqr() < 1.0E-7) {
            threatVelocity = horizontal(hit.hitPos().subtract(hit.previousThreatPos()));
        }
        if (threatVelocity.lengthSqr() < 1.0E-7) {
            return null;
        }

        Line threatLine = new Line(horizontal(hit.previousThreatPos()), threatVelocity.normalize());
        Vec3 playerPos2d = horizontal(player.position());
        Vec3 nearestPoint = threatLine.nearestPoint(playerPos2d);
        double distanceToThreatLine = nearestPoint.distanceTo(playerPos2d);
        double safeDistanceWithPadding = Math.max(SAFE_DISTANCE, dangerPadding.get());

        if (distanceToThreatLine > safeDistanceWithPadding) {
            return null;
        }

        CandidatePlan candidate = findBestDodgeCandidate(player, hit, threatLine, safeDistanceWithPadding);
        if (candidate == null) {
            return null;
        }

        Vec3 relativeDodge = candidate.relativeDodge();
        DodgePlan basePlan = new DodgePlan(candidate.input(), false, null, false);
        if (distanceToThreatLine > SAFE_DISTANCE) {
            return basePlan;
        }

        DodgePlan escalated = escalateIfNeeded(player, hit, relativeDodge, basePlan);
        return escalated != null ? escalated : basePlan;
    }

    private DodgePlan escalateIfNeeded(LocalPlayer player,
                                       HitInfo hit,
                                       Vec3 relativeDodge,
                                       DodgePlan basePlan) {
        Vec3 actualDirection = inputDirection(player.getYRot(), basePlan.input());
        double effectiveness = similarity(actualDirection, relativeDodge);
        if (effectiveness <= 0.05) {
            effectiveness = 0.05;
        }

        double safeDistanceWithPadding = Math.max(SAFE_DISTANCE, dangerPadding.get());
        double distanceToTravel = Math.max(0.0, relativeDodge.length() - (safeDistanceWithPadding - SAFE_DISTANCE));
        double travelTime = distanceToTravel / (effectiveness * 0.11);
        int ticksToImpact = hit.tickDelta() + 1;

        if (ticksToImpact > travelTime) {
            return null;
        }

        boolean useTimer = shouldUseTimer(distanceToTravel, ticksToImpact);
        if (!allowRotationChange.get()) {
            return new DodgePlan(basePlan.input(), false, null, useTimer);
        }

        float yaw = yawTowards(relativeDodge);
        double effectiveVelocity = player.getDeltaMovement().horizontalDistance()
                * Math.max(0.0, similarity(horizontal(player.getDeltaMovement()), relativeDodge));
        double travelTimeWithRotation = distanceToTravel / 0.13;
        boolean shouldJump = ticksToImpact < travelTimeWithRotation && effectiveVelocity > 0.11;

        return new DodgePlan(InputPlan.FORWARD, shouldJump, yaw, useTimer);
    }

    private boolean shouldUseTimer(double distanceToTravel, int ticksToImpact) {
        if (!allowTimer.get()) {
            return false;
        }

        double baseSpeed = allowRotationChange.get() ? 0.155 : 0.11;
        return (distanceToTravel / baseSpeed) / (ticksToImpact + 1.0) > 1.6;
    }

    private CandidatePlan findBestDodgeCandidate(LocalPlayer player,
                                                 HitInfo hit,
                                                 Line threatLine,
                                                 double safeDistanceWithPadding) {
        Vec3 playerPos2d = horizontal(player.position());
        Vec3 optimalDodgePosition = findOptimalDodgePosition(player, threatLine, safeDistanceWithPadding);
        Vec3 preferredRelative = optimalDodgePosition.subtract(playerPos2d);
        if (preferredRelative.lengthSqr() < 1.0E-7) {
            return null;
        }

        double currentDistance = threatLine.nearestPoint(playerPos2d).distanceTo(playerPos2d);
        int horizon = Mth.clamp(Math.min(hit.tickDelta() + 1, pathCheckTicks.get()), 1, 12);

        CandidatePlan best = null;
        double bestScore = -Double.MAX_VALUE;
        for (InputPlan input : InputPlan.CANDIDATES) {
            Vec3 direction = inputDirection(player.getYRot(), input);
            if (direction.lengthSqr() < 1.0E-7) {
                continue;
            }

            double preference = similarity(direction, preferredRelative);
            if (preference < -0.15) {
                continue;
            }

            CandidateSafety safety = evaluateCandidateSafety(player, direction, horizon);
            if (safety.blocked()) {
                continue;
            }
            if (fallFailsafe.get() && !safety.fallSafe()) {
                continue;
            }

            Vec3 projected = playerPos2d.add(direction.scale(PATH_STEP_SPEED * horizon));
            double distanceAfter = threatLine.nearestPoint(projected).distanceTo(projected);
            if (distanceAfter <= currentDistance + 0.015) {
                continue;
            }

            double score = distanceAfter * 3.0
                    + preference
                    - safety.predictedFallDamage() * 0.35
                    - (safety.unsupported() ? 0.8 : 0.0);
            if (score > bestScore) {
                Vec3 relative = projected.subtract(playerPos2d);
                bestScore = score;
                best = new CandidatePlan(input, relative);
            }
        }

        if (best != null) {
            return best;
        }

        InputPlan fallback = chooseInputFor(player.getYRot(), preferredRelative);
        if (fallback == null) {
            return null;
        }

        Vec3 direction = inputDirection(player.getYRot(), fallback);
        CandidateSafety safety = evaluateCandidateSafety(player, direction, horizon);
        if (safety.blocked() || (fallFailsafe.get() && !safety.fallSafe())) {
            return null;
        }
        return new CandidatePlan(fallback, preferredRelative);
    }

    private CandidateSafety evaluateCandidateSafety(LocalPlayer player, Vec3 direction, int horizon) {
        AABB baseBox = player.getBoundingBox();
        Vec3 basePos = player.position();
        boolean unsupported = false;
        double predictedFallDamage = 0.0;

        int safeHorizon = Math.max(1, horizon);
        for (int tick = 1; tick <= safeHorizon; tick++) {
            Vec3 offset = direction.scale(PATH_STEP_SPEED * tick);
            AABB pathBox = baseBox.move(offset.x, 0.0, offset.z).deflate(COLLISION_MARGIN, 0.0, COLLISION_MARGIN);
            if (!mc.level.noCollision(player, pathBox)) {
                return new CandidateSafety(true, false, true, Double.POSITIVE_INFINITY);
            }

            if (!hasGroundSupport(player, pathBox) && !player.isInWater() && !player.isFallFlying()) {
                unsupported = true;
            }
        }

        if (unsupported && fallFailsafe.get()) {
            Vec3 finalOffset = direction.scale(PATH_STEP_SPEED * safeHorizon);
            Vec3 finalPos = basePos.add(finalOffset.x, 0.0, finalOffset.z);
            predictedFallDamage = predictFallDamage(player, finalPos);
        }

        boolean fallSafe = !unsupported
                || !fallFailsafe.get()
                || predictedFallDamage <= maxPredictedFallDamage.get()
                && predictedFallDamage < player.getHealth() + player.getAbsorptionAmount() - 0.5f;
        return new CandidateSafety(false, unsupported, fallSafe, predictedFallDamage);
    }

    private boolean hasGroundSupport(LocalPlayer player, AABB box) {
        AABB supportBox = new AABB(
                box.minX + 0.05,
                box.minY - 0.10,
                box.minZ + 0.05,
                box.maxX - 0.05,
                box.minY - 0.01,
                box.maxZ - 0.05
        );
        return !mc.level.noCollision(player, supportBox);
    }

    private double predictFallDamage(LocalPlayer player, Vec3 horizontalLandingPos) {
        if (mc.level == null) {
            return Double.POSITIVE_INFINITY;
        }

        double landingY = findLandingY(player, horizontalLandingPos);
        if (!Double.isFinite(landingY)) {
            return Double.POSITIVE_INFINITY;
        }

        double fallDistance = Math.max(0.0, player.fallDistance + player.getY() - landingY);
        if (fallDistance <= player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)) {
            return 0.0;
        }

        float raw = Mth.floor((fallDistance + 1.0E-6 - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE))
                * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
        if (raw <= 0.0f) {
            return 0.0;
        }

        return applyFallReductions(player, raw);
    }

    private double findLandingY(LocalPlayer player, Vec3 horizontalLandingPos) {
        double startY = player.getY() + 0.2;
        double endY = mc.level.getMinY();
        double[][] samples = {
                {0.0, 0.0},
                {PLAYER_HALF_WIDTH - 0.04, PLAYER_HALF_WIDTH - 0.04},
                {PLAYER_HALF_WIDTH - 0.04, -PLAYER_HALF_WIDTH + 0.04},
                {-PLAYER_HALF_WIDTH + 0.04, PLAYER_HALF_WIDTH - 0.04},
                {-PLAYER_HALF_WIDTH + 0.04, -PLAYER_HALF_WIDTH + 0.04}
        };

        double bestY = Double.NEGATIVE_INFINITY;
        for (double[] sample : samples) {
            Vec3 from = new Vec3(horizontalLandingPos.x + sample[0], startY, horizontalLandingPos.z + sample[1]);
            Vec3 to = new Vec3(from.x, endY, from.z);
            HitResult hit = mc.level.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.ANY,
                    player
            ));
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                bestY = Math.max(bestY, hit.getLocation().y);
            }
        }

        return bestY;
    }

    private float applyFallReductions(LocalPlayer player, float damage) {
        DamageSource source = player.damageSources().fall();
        float armor = player.getArmorValue();
        float toughness = (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float out = CombatRules.getDamageAfterAbsorb(player, damage, source, armor, toughness);

        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int reduced = 25 - (resistance.getAmplifier() + 1) * 5;
            out = Math.max(out * reduced / 25.0f, 0.0f);
        }

        float protection = getFallProtectionAmount(player);
        return protection > 0.0f ? CombatRules.getDamageAfterMagicAbsorb(out, protection) : out;
    }

    private float getFallProtectionAmount(LocalPlayer player) {
        float total = 0.0f;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            total += getEnchantmentLevel(Enchantments.PROTECTION, stack);
            if (slot == EquipmentSlot.FEET) {
                total += getEnchantmentLevel(Enchantments.FEATHER_FALLING, stack) * 3.0f;
            }
        }
        return total;
    }

    private int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        Holder<Enchantment> entry = getEnchantmentEntry(key);
        return entry != null ? EnchantmentHelper.getItemEnchantmentLevel(entry, stack) : 0;
    }

    private Holder<Enchantment> getEnchantmentEntry(ResourceKey<Enchantment> key) {
        if (mc.level == null || key == null) {
            return null;
        }

        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.getValue(key);
        return enchantment != null ? registry.wrapAsHolder(enchantment) : null;
    }

    private Vec3 findOptimalDodgePosition(LocalPlayer player, Line threatLine, double safeDistanceWithPadding) {
        Vec3 playerPos2d = horizontal(player.position());
        Vec3 velocity = horizontal(player.getDeltaMovement());
        Vec3 freeMovementPos = playerPos2d.add(velocity.scale(2.0));
        Vec3 nearestPosToLine = threatLine.nearestPoint(playerPos2d);

        Line[] borders = dangerZoneBorders(threatLine, safeDistanceWithPadding);
        Vec3 first = borders[0].nearestPoint(freeMovementPos);
        Vec3 second = borders[1].nearestPoint(freeMovementPos);

        if (walkableDistance(player, nearestPosToLine, first) < SAFE_DISTANCE) {
            return second;
        }

        if (walkableDistance(player, nearestPosToLine, second) < SAFE_DISTANCE) {
            return first;
        }

        return first.distanceTo(freeMovementPos) < second.distanceTo(freeMovementPos) - 0.05 ? first : second;
    }

    private double walkableDistance(LocalPlayer player, Vec3 basePos, Vec3 dodgePos) {
        double bestSq = Double.POSITIVE_INFINITY;
        double[] rayHeights = {0.6, 1.6};
        for (double height : rayHeights) {
            Vec3 from = new Vec3(basePos.x, player.getY() + height, basePos.z);
            Vec3 to = new Vec3(dodgePos.x, player.getY() + height, dodgePos.z);
            HitResult hit = mc.level.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ));
            Vec3 realTo = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;
            bestSq = Math.min(bestSq, from.distanceToSqr(realTo));
        }

        return Math.sqrt(bestSq);
    }

    private Line[] dangerZoneBorders(Line baseLine, double distance) {
        Vec3 ortho = new Vec3(-baseLine.direction().z, 0.0, baseLine.direction().x).normalize();
        Vec3 offset = ortho.scale(distance);
        return new Line[]{
                new Line(baseLine.position().subtract(offset), baseLine.direction()),
                new Line(baseLine.position().add(offset), baseLine.direction())
        };
    }

    private InputPlan chooseInputFor(float yaw, Vec3 desiredDirection) {
        Vec3 desired = horizontal(desiredDirection);
        if (desired.lengthSqr() < 1.0E-7) {
            return null;
        }
        desired = desired.normalize();

        InputPlan best = null;
        double bestDot = -Double.MAX_VALUE;
        for (InputPlan candidate : InputPlan.CANDIDATES) {
            Vec3 direction = inputDirection(yaw, candidate);
            double dot = direction.dot(desired);
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }

        return bestDot >= INPUT_DEAD_DOT ? best : null;
    }

    private Vec3 inputDirection(float yaw, InputPlan input) {
        float forward = input.forward() == input.backward() ? 0.0f : (input.forward() ? 1.0f : -1.0f);
        float sideways = input.left() == input.right() ? 0.0f : (input.left() ? 1.0f : -1.0f);
        if (forward == 0.0f && sideways == 0.0f) {
            return Vec3.ZERO;
        }

        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        Vec3 direction = new Vec3(
                forward * cos + sideways * sin,
                0.0,
                forward * sin - sideways * cos
        );

        return direction.lengthSqr() > 1.0 ? direction.normalize() : direction;
    }

    private void applyInput(MovementInputEvent event, InputPlan input) {
        event.setForward(input.forward());
        event.setBackward(input.backward());
        event.setLeft(input.left());
        event.setRight(input.right());
        if (input.forward()) {
            event.setSprint(true);
        }
    }

    private boolean applyEdgeSafeWalk(LocalPlayer player, MovementInputEvent event) {
        if (!fallFailsafe.get() || !edgeSafeWalk.get()) {
            return false;
        }

        SafeWalk safeWalk = Modules.get(SafeWalk.class);
        return safeWalk != null && safeWalk.handleExternalOnEdge(player, event);
    }

    private float yawTowards(Vec3 direction) {
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0f);
    }

    private double similarity(Vec3 a, Vec3 b) {
        Vec3 ah = horizontal(a);
        Vec3 bh = horizontal(b);
        double len = ah.length() * bh.length();
        if (len < 1.0E-7) {
            return 0.0;
        }
        return ah.dot(bh) / len;
    }

    private Vec3 horizontal(Vec3 vec) {
        if (vec == null) {
            return Vec3.ZERO;
        }
        return new Vec3(vec.x, 0.0, vec.z);
    }

    private boolean isInGround(AbstractArrow projectile) {
        try {
            return ((PersistentProjectileEntityAccessor) projectile).combatant$isInGround();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void clearTimer() {
        if (timerActive) {
            Timer.clearExternalTickTimer();
            timerActive = false;
        }
    }

    private interface ThreatSimulation {
        Entity entity();

        Vec3 previousPos();

        Vec3 pos();

        Vec3 velocity();

        boolean tick();
    }

    private record HitInfo(int tickDelta, Entity threat, Vec3 hitPos, Vec3 previousThreatPos, Vec3 velocity) {
    }

    private record DodgePlan(InputPlan input, boolean shouldJump, Float yawChange, boolean useTimer) {
    }

    private record CandidatePlan(InputPlan input, Vec3 relativeDodge) {
    }

    private record CandidateSafety(boolean blocked,
                                   boolean unsupported,
                                   boolean fallSafe,
                                   double predictedFallDamage) {
    }

    private record InputPlan(boolean forward, boolean backward, boolean left, boolean right) {
        private static final InputPlan FORWARD = new InputPlan(true, false, false, false);
        private static final InputPlan[] CANDIDATES = {
                FORWARD,
                new InputPlan(true, false, true, false),
                new InputPlan(false, false, true, false),
                new InputPlan(false, true, true, false),
                new InputPlan(false, true, false, false),
                new InputPlan(false, true, false, true),
                new InputPlan(false, false, false, true),
                new InputPlan(true, false, false, true)
        };
    }

    private record Line(Vec3 position, Vec3 direction) {
        private Vec3 nearestPoint(Vec3 point) {
            double lengthSq = direction.lengthSqr();
            if (lengthSq < 1.0E-7) {
                return position;
            }
            double t = point.subtract(position).dot(direction) / lengthSq;
            return position.add(direction.scale(t));
        }
    }

    private final class ArrowLikeThreatSimulation implements ThreatSimulation {
        private final Entity entity;
        private final SimulatedArrow arrow;
        private Vec3 previousPos;
        private Vec3 pos;
        private Vec3 velocity;
        private boolean done;

        private ArrowLikeThreatSimulation(Entity entity) {
            this.entity = entity;
            this.arrow = new SimulatedArrow(mc.level, entity.position(), entity.getDeltaMovement(), false);
            this.previousPos = entity.position();
            this.pos = entity.position();
            this.velocity = entity.getDeltaMovement();
        }

        @Override
        public Entity entity() {
            return entity;
        }

        @Override
        public Vec3 previousPos() {
            return previousPos;
        }

        @Override
        public Vec3 pos() {
            return pos;
        }

        @Override
        public Vec3 velocity() {
            return velocity;
        }

        @Override
        public boolean tick() {
            if (done) {
                return false;
            }

            previousPos = arrow.getPos();
            HitResult hit = arrow.tick();
            pos = arrow.getPos();
            velocity = pos.subtract(previousPos);
            if (hit != null) {
                done = true;
            }

            return velocity.lengthSqr() > 1.0E-9;
        }
    }

    private final class LinearThreatSimulation implements ThreatSimulation {
        private final Entity entity;
        private Vec3 previousPos;
        private Vec3 pos;
        private Vec3 velocity;
        private boolean done;

        private LinearThreatSimulation(Entity entity) {
            this.entity = entity;
            this.previousPos = entity.position();
            this.pos = entity.position();
            this.velocity = entity.getDeltaMovement();
        }

        @Override
        public Entity entity() {
            return entity;
        }

        @Override
        public Vec3 previousPos() {
            return previousPos;
        }

        @Override
        public Vec3 pos() {
            return pos;
        }

        @Override
        public Vec3 velocity() {
            return velocity;
        }

        @Override
        public boolean tick() {
            if (done || velocity.lengthSqr() < 1.0E-9) {
                return false;
            }

            previousPos = pos;
            Vec3 nextPos = pos.add(velocity);
            HitResult hit = mc.level.clip(new ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity
            ));

            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                pos = hit.getLocation();
                velocity = pos.subtract(previousPos);
                done = true;
                return velocity.lengthSqr() > 1.0E-9;
            }

            pos = nextPos;
            velocity = entity instanceof ShulkerBullet ? velocity.scale(0.95) : velocity;
            return true;
        }
    }
}
