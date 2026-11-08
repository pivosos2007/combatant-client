/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.mixins.accessors.ServerboundMovePlayerPacketAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.scaffold.ScaffoldFacePositionFactory;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.block.scaffold.ScaffoldTargetFinder;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.simulation.PlayerSimulationCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ModuleInfo(
        id = "nofall",
        displayName = "NoFall",
        aliases = {"AutoMLG", "MLG"},
        category = ModuleCategory.MOVEMENT, subcategory = ModuleSubcategory.BASIC,
        description = "module.nofall.description")
public class NoFall extends Module {

    private static final int MLG_COLLISION_PREDICTION_TICKS = 32;
    private static final double MLG_PREDICTION_FOOTPRINT_EPSILON = 0.02;
    // Aim can begin earlier, but a distant predicted X/Z landing point is too unstable for a click.
    private static final int MLG_MAX_COMMIT_TICKS = 4;
    private static final int SLIME_MLG_RETRY_TICKS = 10;
    private static final int MLG_SCAFFOLDING_SNEAK_TICKS = 3;
    private static final int MLG_HOTBAR_RESET_TICKS = 1;
    private static final long MLG_PICKUP_WATER_MIN_DELAY_MS = 200L;
    private static final long MLG_PICKUP_WATER_MAX_DELAY_MS = 1000L;
    private static final Set<Item> NETHER_MLG_ITEMS = Set.of(
            Items.SCAFFOLDING,
            Items.COBWEB,
            Items.POWDER_SNOW_BUCKET,
            Items.HAY_BLOCK,
            Items.SLIME_BLOCK,
            Items.HONEY_BLOCK,
            Items.TWISTING_VINES
    );
    private static final Set<Item> NORMAL_MLG_ITEMS;

    static {
        java.util.LinkedHashSet<Item> items = new java.util.LinkedHashSet<>(NETHER_MLG_ITEMS);
        items.add(Items.WATER_BUCKET);
        NORMAL_MLG_ITEMS = Set.copyOf(items);
    }

    private final EnumValue<Mode> mode =
            enumCommon("mode", "mode", CommonSettingSchemas.MODE, Mode.GROUD_SPOOF, Mode.values());
    private final NumberValue<Float> packetJumpFallDistance =
            visibleWhen(num("packet_jump_fall_distance", 3.0f, 0.0f, 20.0f), () -> mode.get() == Mode.PACKET_JUMP);
    private final EnumValue<PacketJumpTiming> packetJumpTiming =
            visibleWhen(enumSetting("packet_jump_timing", "packet_jump_timing", PacketJumpTiming.LANDING, PacketJumpTiming.values()),
                    () -> mode.get() == Mode.PACKET_JUMP);
    private final BooleanValue packetJumpResetFallDistance =
            visibleWhen(bool("packet_jump_reset_fall_distance", true),
                    () -> mode.get() == Mode.PACKET_JUMP && packetJumpTiming.get() == PacketJumpTiming.FALLING);
    private final NumberValue<Float> vulcanFallDistance =
            visibleWhen(num("vulcan_fall_distance", 7.0f, 3.0f, 40.0f), () -> mode.get() == Mode.VULCAN);
    private final NumberValue<Float> mlgMinFallDistance =
            visibleWhen(num("mlg_min_fall_distance", 4.0f, 3.0f, 50.0f), () -> mode.get() == Mode.MLG);
    private final NumberValue<Float> slimeMlgMinHeight =
            visibleWhen(num("slime_mlg_min_height", 1.0f, 0.0f, 20.0f), () -> mode.get() == Mode.SLIME_MLG);
    private final EnumValue<MlgRotationMode> mlgRotationMode =
            visibleWhen(enumSetting("mlg_rotation_mode", "mlg_rotation_mode", MlgRotationMode.ROTATIONS,
                    MlgRotationMode.values()), this::isMlgMode);
    private final EnumValue<MovementCorrection> mlgMovementCorrection =
            visibleWhen(enumCommon("mlg_movement_correction", "movement_correction",
                    CommonSettingSchemas.PLACEMENT_MOVEMENT_CORRECTION,
                    MovementCorrection.SILENT, MovementCorrection.values()),
                    () -> isMlgMode() && mlgRotationMode.get() == MlgRotationMode.ROTATIONS);
    private final NumberValue<Integer> mlgRotationResetTicks =
            visibleWhen(numCommon("mlg_rotation_reset_ticks", "rotation_reset_ticks",
                    CommonSettingSchemas.ROTATION_RESET_TICKS, 1, 1, 20),
                    () -> isMlgMode() && mlgRotationMode.get() == MlgRotationMode.ROTATIONS);
    private final NumberValue<Float> mlgRotationResetThreshold =
            visibleWhen(numCommon("mlg_rotation_reset_threshold", "rotation_reset_threshold",
                    CommonSettingSchemas.ROTATION_RESET_THRESHOLD, 2.0f, .1f, 15.0f),
                    () -> isMlgMode() && mlgRotationMode.get() == MlgRotationMode.ROTATIONS);
    private final List<PlacedWater> lastWaterPlacements = new ArrayList<>();
    private boolean packetJumpFalling;
    private boolean packetJumpInternalSend;
    private int lastPacketJumpTick = Integer.MIN_VALUE;
    private double lastMoveX = Double.NaN;
    private double lastMoveY = Double.NaN;
    private double lastMoveZ = Double.NaN;
    private MlgPlan currentMlgPlan;
    private int mlgSneakTicks;
    private int lastMlgActionTick = Integer.MIN_VALUE;
    private int lastSlimePlacementTick = Integer.MIN_VALUE;
    private BlockPos lastSlimePlacementPos;
    // The simulation starts at the current tick; once descent begins it no longer knows the jump apex.
    private double observedSlimePeakY = Double.NaN;

    {
        setDefaultBind("GRAVE_ACCENT");
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) return;

        LocalPlayer player = client.player;
        if (isMlgMode()) {
            if (mode.get() == Mode.SLIME_MLG) updateSlimePeak(player);
            tickMlg(client, player);
            return;
        }

        if (player.onGround()) {
            packetJumpFalling = false;
        }
        if (player.isShiftKeyDown()) return;

        switch (mode.get()) {
            case GROUD_SPOOF -> {
                if (isDangerousFall(player)) {
                    client.getConnection().send(
                            new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision)
                    );
                }
            }
            case PACKET_JUMP -> {
                packetJumpFalling = !player.onGround() && player.fallDistance > packetJumpFallDistance.get();
                if (packetJumpTiming.get() == PacketJumpTiming.FALLING
                        && packetJumpFalling
                        && player.tickCount != lastPacketJumpTick) {
                    sendPacketJumpSpoof(client, player, player.getX(), player.getY() + 1.0E-9, player.getZ());
                    lastPacketJumpTick = player.tickCount;
                    if (packetJumpResetFallDistance.get()) {
                        player.fallDistance = 0.0f;
                    }
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) {
            return;
        }

        if (!isMlgMode()) {
            currentMlgPlan = null;
            RotationManager.INSTANCE.clear(this);
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.gameMode == null) {
            currentMlgPlan = null;
            return;
        }

        if (mode.get() == Mode.SLIME_MLG) updateSlimePeak(client.player);
        currentMlgPlan = getCurrentGoal(client.player);
        if (currentMlgPlan == null || mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS) {
            RotationManager.INSTANCE.clear(this);
            return;
        }

        RotationTarget target = new RotationTarget(
                currentMlgPlan.rotation(),
                client.player,
                List.of(),
                mlgRotationResetTicks.get(),
                mlgRotationResetThreshold.get(),
                false,
                mlgMovementCorrection.get(),
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, 100, this);
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        if (!isMlgMode() || mlgSneakTicks <= 0) {
            return;
        }

        mlgSneakTicks--;
        event.setSneak(true);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (packetJumpInternalSend) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        LocalPlayer player = client.player;
        if (player.isShiftKeyDown()) {
            rememberMovePacket(packet, player);
            return;
        }

        switch (mode.get()) {
            case PACKET_JUMP -> {
                packetJumpFalling = !player.onGround() && player.fallDistance > packetJumpFallDistance.get();
                if (packetJumpTiming.get() == PacketJumpTiming.LANDING
                        && packet.isOnGround()
                        && packetJumpFalling) {
                    packetJumpFalling = false;
                    sendPacketJumpSpoof(
                            client,
                            player,
                            resolveTracked(lastMoveX, player.getX()),
                            resolveTracked(lastMoveY, player.getY()) + 1.0E-9,
                            resolveTracked(lastMoveZ, player.getZ())
                    );
                }
            }
            case VULCAN -> {
                if (player.fallDistance > vulcanFallDistance.get()) {
                    ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(true);
                    player.fallDistance = 0.0f;
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
                }
            }
            case NO_GROUND -> ((ServerboundMovePlayerPacketAccessor) packet).combatant$setOnGround(false);
            default -> {
            }
        }

        rememberMovePacket(packet, player);
    }

    private boolean isDangerousFall(LocalPlayer player) {
        return player != null
                && player.getDeltaMovement().y < -0.5
                && player.fallDistance > 2.0f;
    }

    private void sendPacketJumpSpoof(Minecraft client, LocalPlayer player, double x, double y, double z) {
        if (client.getConnection() == null || player == null) return;

        packetJumpInternalSend = true;
        try {
            client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    x,
                    y,
                    z,
                    false,
                    player.horizontalCollision
            ));
        } finally {
            packetJumpInternalSend = false;
        }
    }

    private void rememberMovePacket(ServerboundMovePlayerPacket packet, LocalPlayer player) {
        lastMoveX = packet.getX(player.getX());
        lastMoveY = packet.getY(player.getY());
        lastMoveZ = packet.getZ(player.getZ());
    }

    private double resolveTracked(double value, double fallback) {
        return Double.isNaN(value) ? fallback : value;
    }

    private void resetState() {
        packetJumpFalling = false;
        packetJumpInternalSend = false;
        lastPacketJumpTick = Integer.MIN_VALUE;
        lastMoveX = Double.NaN;
        lastMoveY = Double.NaN;
        lastMoveZ = Double.NaN;
        currentMlgPlan = null;
        mlgSneakTicks = 0;
        lastMlgActionTick = Integer.MIN_VALUE;
        lastSlimePlacementTick = Integer.MIN_VALUE;
        lastSlimePlacementPos = null;
        observedSlimePeakY = Double.NaN;
        RotationManager.INSTANCE.clear(this);
        lastWaterPlacements.clear();
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void tickMlg(Minecraft client, LocalPlayer player) {
        if (player == null || client.gameMode == null) {
            currentMlgPlan = null;
            return;
        }

        if (player.tickCount == lastMlgActionTick) return;
        List<MlgPlan> slimeAlternatives = mode.get() == Mode.SLIME_MLG
                && mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS
                ? getSlimeMlgPlacementPlans(player, findClosestSpecificItemSource(player, Items.SLIME_BLOCK))
                : List.of();
        MlgPlan fresh = slimeAlternatives.isEmpty() ? getCurrentGoal(player) : slimeAlternatives.get(0);
        if (fresh == null) {
            currentMlgPlan = null;
            RotationManager.INSTANCE.clear(this);
            return;
        }
        MlgPlan planned = currentMlgPlan;
        if (planned != null && !sameMlgTarget(planned, fresh)) {
            currentMlgPlan = fresh;
        }
        MlgPlan plan = fresh;
        int commitTicks = mode.get() == Mode.SLIME_MLG
                ? SlimeMlgTrajectoryPlanner.commitWindow(player.getDeltaMovement().x,
                        player.getDeltaMovement().z) : MLG_MAX_COMMIT_TICKS;
        if (plan.type() == MlgPlanType.PLACE && plan.ticksToLanding() > commitTicks
                && !(mode.get() == Mode.SLIME_MLG
                && mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS)) return;

        if (plan.type() == MlgPlanType.PLACE
                && ((player.onGround() && mode.get() != Mode.SLIME_MLG)
                || player.isInWater()
                || player.isInLava()
                || player.onClimbable()
                || player.getAbilities().flying)) {
            currentMlgPlan = null;
            return;
        }

        if (mode.get() == Mode.SLIME_MLG && plan.type() == MlgPlanType.PLACE
                && mlgRotationMode.get() != MlgRotationMode.NO_ROTATIONS
                && !SlimeMlgTrajectoryPlanner.clearForPlacement(player.getBoundingBox().minY,
                        plan.targetPos().getY() - 1)) return;

        BlockHitResult hitResult;
        Rotation useRotation;
        if (mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS) {
            if (plan.type() == MlgPlanType.PICKUP_WATER) {
                Rotation playerRotation = new Rotation(player.getYRot(), player.getXRot(), true).normalize();
                hitResult = traceTarget(player, playerRotation, ClipContext.Fluid.SOURCE_ONLY);
                if (!plan.matches(hitResult)) {
                    return;
                }
                useRotation = playerRotation;
            } else {
                // No Rotations means packet placement at a planned support face, independent
                // of the player's camera yaw/pitch. Just as in Scaffold and ordinary MLG,
                // the clicked block, face and reach are validated by the placement plan.
                hitResult = plan.target().toHitResult();
                useRotation = plan.rotation();
            }
        } else {
            // For slime a prepared aim isn't a confirmed server-facing rotation.
            Rotation currentRotation = mode.get() == Mode.SLIME_MLG
                    ? RotationManager.INSTANCE.getServerRotation()
                    : RotationManager.INSTANCE.getCurrentRotation();
            if (currentRotation == null) {
                return;
            }

            hitResult = traceTarget(
                    player,
                    currentRotation,
                    plan.type() == MlgPlanType.PICKUP_WATER ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
            );
            if (!plan.matches(hitResult)) {
                return;
            }
            useRotation = currentRotation;
        }

        if (mode.get() == Mode.SLIME_MLG && mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS) {
            for (MlgPlan alternative : slimeAlternatives) {
                if (alternative.type() != MlgPlanType.PLACE
                        || !SlimeMlgTrajectoryPlanner.clearForPlacement(
                        player.getBoundingBox().minY, alternative.targetPos().getY() - 1)) continue;
                if (executeMlgPlacement(player, alternative, alternative.target().toHitResult(),
                        alternative.rotation())) {
                    currentMlgPlan = null;
                    return;
                }
            }
            return;
        }
        if (executeMlgPlacement(player, plan, hitResult, useRotation)) currentMlgPlan = null;
    }

    private boolean isMlgMode() {
        return mode.get() == Mode.MLG || mode.get() == Mode.SLIME_MLG;
    }

    private void updateSlimePeak(LocalPlayer player) {
        if (player == null) return;
        double feetY = player.getY();
        if (player.onGround()) {
            observedSlimePeakY = feetY;
        } else {
            // fallDistance also covers enabling NoFall midway through the descent.
            double estimatedPeak = feetY + Math.max(0.0f, player.fallDistance);
            observedSlimePeakY = Double.isFinite(observedSlimePeakY)
                    ? Math.max(observedSlimePeakY, estimatedPeak)
                    : estimatedPeak;
        }
    }

    private static boolean sameMlgTarget(MlgPlan a, MlgPlan b) {
        return a.type() == b.type() && a.targetPos().equals(b.targetPos())
                && a.item() == b.item() && a.hand() == b.hand()
                && a.hotbarSlot() == b.hotbarSlot();
    }

    private MlgPlan getCurrentGoal(LocalPlayer player) {
        MlgPlan placement = getCurrentMlgPlacementPlan(player);
        if (placement != null) {
            return placement;
        }
        return mode.get() == Mode.SLIME_MLG ? null : getCurrentPickupPlan(player);
    }

    private MlgPlan getCurrentMlgPlacementPlan(LocalPlayer player) {
        if (player == null) return null;
        boolean slime = mode.get() == Mode.SLIME_MLG;
        if (!slime && player.fallDistance <= mlgMinFallDistance.get()) return null;
        if (slime && (player.isPassenger() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying)) return null;

        MlgSource source = mode.get() == Mode.SLIME_MLG
                ? findClosestSpecificItemSource(player, Items.SLIME_BLOCK)
                : findClosestMlgSource(player);
        if (source == null) {
            return null;
        }

        if (slime) return getSlimeMlgPlacementPlan(player, source);

        MlgLanding landing = findMlgCollision(player);
        if (landing == null || isFallDamageBlocking(landing.support())) return null;
        BlockPos targetPos = landing.support().above();
        if (!player.level().getBlockState(targetPos).canBeReplaced()) return null;
        ScaffoldPlacementTarget target = findMlgPlacementTarget(player, source, targetPos, null);
        if (target == null || !isWithinMlgBlockRange(player, target.getHitVec())) return null;
        return new MlgPlan(MlgPlanType.PLACE, targetPos, target, target.getRotation(),
                source.hand(), source.hotbarSlot(), source.item(), landing.ticksToLanding());
    }

    private MlgPlan getSlimeMlgPlacementPlan(LocalPlayer player, MlgSource source) {
        List<MlgPlan> plans = getSlimeMlgPlacementPlans(player, source);
        return plans.isEmpty() ? null : plans.get(0);
    }

    private List<MlgPlan> getSlimeMlgPlacementPlans(LocalPlayer player, MlgSource source) {
        if (player == null || source == null) return List.of();
        List<SlimeMlgTrajectoryPlanner.Frame> path = slimePrediction(player);
        var candidates = SlimeMlgTrajectoryPlanner.candidates(path,
                player.getBbWidth(), slimeMlgMinHeight.get(), observedSlimePeakY);
        if (candidates.isEmpty()) return List.of();

        var world = player.level();
        Rotation serverRotation = mlgRotationMode.get() == MlgRotationMode.ROTATIONS
                ? RotationManager.INSTANCE.getServerRotation() : null;
        BlockHitResult serverHit = serverRotation == null ? null
                : traceTarget(player, serverRotation, ClipContext.Fluid.NONE);
        SlimeMlgTrajectoryPlanner.Candidate previous = null;
        SlimeMlgTrajectoryPlanner.Candidate aligned = null;
        List<SlimeMlgTrajectoryPlanner.Candidate> viable = new ArrayList<>();
        Map<SlimeMlgTrajectoryPlanner.Candidate, MlgPlan> plans = new HashMap<>();
        for (var candidate : candidates) {
            BlockPos support = new BlockPos(candidate.x(), candidate.supportY(), candidate.z());
            BlockPos targetPos = support.above();
            if (lastSlimePlacementPos != null && lastSlimePlacementPos.equals(targetPos)
                    && player.tickCount >= lastSlimePlacementTick
                    && player.tickCount - lastSlimePlacementTick < SLIME_MLG_RETRY_TICKS) continue;
            if (isFallDamageBlocking(support) || !world.getBlockState(targetPos).canBeReplaced()) continue;
            var shape = world.getBlockState(support).getCollisionShape(world, support);
            if (shape.isEmpty() || Math.abs(shape.max(Direction.Axis.Y) - 1.0) > 0.025) continue;
            ScaffoldPlacementTarget placement;
            if (mlgRotationMode.get() == MlgRotationMode.NO_ROTATIONS) {
                Vec3 eye = player.getEyePosition();
                double hitX = Math.max(support.getX() + 0.06, Math.min(support.getX() + 0.94, eye.x));
                double hitZ = Math.max(support.getZ() + 0.06, Math.min(support.getZ() + 0.94, eye.z));
                Vec3 hit = new Vec3(hitX, support.getY() + 1.0, hitZ);
                placement = new ScaffoldPlacementTarget(support, targetPos, Direction.UP,
                        hit, support.getY() + 1.0,
                        Rotation.lookingAt(hit, eye).normalize());
            } else {
                placement = findMlgPlacementTarget(player, source, targetPos,
                        (supportPos, face) -> face == Direction.UP && supportPos.equals(support));
            }
            if (placement == null || !isWithinMlgBlockRange(player, placement.getHitVec())) continue;
            MlgPlan plan = new MlgPlan(MlgPlanType.PLACE, targetPos, placement, placement.getRotation(),
                    source.hand(), source.hotbarSlot(), source.item(), candidate.contactTick());
            plans.put(candidate, plan);
            viable.add(candidate);
            if (currentMlgPlan != null && sameMlgTarget(currentMlgPlan, plan)) previous = candidate;
            if (serverHit != null && plan.matches(serverHit)) aligned = candidate;
        }
        if (viable.isEmpty()) return List.of();
        SlimeMlgTrajectoryPlanner.Candidate chosen = SlimeMlgTrajectoryPlanner.choose(viable, previous, aligned);
        viable.sort(java.util.Comparator.comparingInt(SlimeMlgTrajectoryPlanner.Candidate::contactTick)
                .thenComparing(java.util.Comparator.comparingDouble(
                        SlimeMlgTrajectoryPlanner.Candidate::coverage).reversed()));
        List<MlgPlan> ordered = new ArrayList<>(viable.size());
        if (chosen != null) ordered.add(plans.get(chosen));
        for (var candidate : viable) {
            if (!candidate.equals(chosen)) ordered.add(plans.get(candidate));
        }
        return ordered;
    }

    private List<SlimeMlgTrajectoryPlanner.Frame> slimePrediction(LocalPlayer player) {
        List<SlimeMlgTrajectoryPlanner.Frame> path = new ArrayList<>(MLG_COLLISION_PREDICTION_TICKS + 1);
        PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForLocalPlayer();
        if (simulation != null) {
            try {
                for (int tick = 0; tick <= MLG_COLLISION_PREDICTION_TICKS; tick++) {
                    var snapshot = simulation.getSnapshotAt(tick);
                    if (snapshot == null) break;
                    path.add(new SlimeMlgTrajectoryPlanner.Frame(
                            snapshot.pos().x, snapshot.pos().y, snapshot.pos().z, snapshot.onGround()));
                }
            } catch (RuntimeException ignored) {
                path.clear();
            }
        }
        if (path.size() >= 2) return path;
        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        path.clear();
        if (player.onGround() && velocity.y <= 0.0) return path;
        for (int tick = 0; tick <= MLG_COLLISION_PREDICTION_TICKS; tick++) {
            path.add(new SlimeMlgTrajectoryPlanner.Frame(position.x, position.y, position.z,
                    tick == 0 && player.onGround()));
            position = position.add(velocity);
            velocity = new Vec3(velocity.x * 0.91, (velocity.y - 0.08) * 0.98,
                    velocity.z * 0.91);
        }
        return path;
    }

    private ScaffoldPlacementTarget findMlgPlacementTarget(LocalPlayer player, MlgSource source,
                                                           BlockPos targetPos,
                                                           java.util.function.BiPredicate<BlockPos, Direction> faceFilter) {
        return ScaffoldTargetFinder.findTarget(
                player, targetPos, source.stack(), List.of(BlockPos.ZERO), null, false,
                new ScaffoldFacePositionFactory(player, player.getEyePosition(),
                        ScaffoldFacePositionFactory.Mode.CENTER, null, 0.0),
                player.getEyePosition(), faceFilter);
    }

    private MlgPlan getCurrentPickupPlan(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        MlgSource source = findClosestSpecificItemSource(player, Items.BUCKET);
        if (source == null) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        lastWaterPlacements.removeIf(placement ->
                now - placement.timeMs() > MLG_PICKUP_WATER_MAX_DELAY_MS
                        || !client.level.getBlockState(placement.pos()).is(Blocks.WATER));

        for (PlacedWater placement : lastWaterPlacements) {
            if (now - placement.timeMs() < MLG_PICKUP_WATER_MIN_DELAY_MS) {
                continue;
            }

            Vec3 hitVec = Vec3.atCenterOf(placement.pos()).add(0.0, 0.45, 0.0);
            Rotation rotation = Rotation.lookingAt(hitVec, player.getEyePosition()).normalize();
            if (!isWithinMlgBlockRange(player, hitVec)) {
                continue;
            }

            return new MlgPlan(MlgPlanType.PICKUP_WATER, placement.pos(), null, rotation,
                    source.hand(), source.hotbarSlot(), source.item(), 0);
        }

        return null;
    }

    private MlgSource findClosestMlgSource(LocalPlayer player) {
        if (player == null) {
            return null;
        }

        Set<Item> allowedItems = player.level().dimension() == Level.NETHER
                ? NETHER_MLG_ITEMS
                : NORMAL_MLG_ITEMS;
        ItemStack offhand = player.getOffhandItem();
        if (isAllowedMlgItem(offhand, allowedItems)) {
            return new MlgSource(InteractionHand.OFF_HAND, -1, offhand, offhand.getItem());
        }

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        int bestSlot = -1;
        int bestDistance = Integer.MAX_VALUE;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isAllowedMlgItem(stack, allowedItems)) {
                continue;
            }

            int distance = hotbarDistance(selectedSlot, slot);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
                bestStack = stack;
            }
        }

        if (bestSlot == -1) {
            return null;
        }

        return new MlgSource(InteractionHand.MAIN_HAND, bestSlot, bestStack, bestStack.getItem());
    }

    private MlgSource findClosestSpecificItemSource(LocalPlayer player, Item item) {
        if (player == null || item == null) {
            return null;
        }

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.is(item)) {
            return new MlgSource(InteractionHand.OFF_HAND, -1, offhand, item);
        }

        int selectedSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
        int bestSlot = -1;
        int bestDistance = Integer.MAX_VALUE;
        ItemStack bestStack = ItemStack.EMPTY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) {
                continue;
            }

            int distance = hotbarDistance(selectedSlot, slot);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSlot = slot;
                bestStack = stack;
            }
        }

        if (bestSlot == -1) {
            return null;
        }

        return new MlgSource(InteractionHand.MAIN_HAND, bestSlot, bestStack, item);
    }

    private boolean isAllowedMlgItem(ItemStack stack, Set<Item> allowedItems) {
        return stack != null && !stack.isEmpty() && allowedItems.contains(stack.getItem());
    }

    private int hotbarDistance(int from, int to) {
        int diff = Math.abs(from - to);
        return Math.min(diff, 9 - diff);
    }

    private MlgLanding findMlgCollision(LocalPlayer player) {
        PlayerSimulationCache.SimulatedPlayerCache simulation = PlayerSimulationCache.getSimulationForLocalPlayer();
        if (simulation == null) {
            return null;
        }

        PlayerSimulationCache.SimulatedPlayerSnapshot previous = simulation.getSnapshotAt(0);
        double peakY = previous.pos().y;
        for (int tick = 1; tick <= MLG_COLLISION_PREDICTION_TICKS; tick++) {
            PlayerSimulationCache.SimulatedPlayerSnapshot snapshot = simulation.getSnapshotAt(tick);
            peakY = Math.max(peakY, snapshot.pos().y);
            if (snapshot.onGround() && !previous.onGround()) {
                Vec3 pos = snapshot.pos();
                Vec3 before = previous.pos();
                int supportY = (int) Math.floor(pos.y - 0.2);
                double halfWidth = player.getBbWidth() * .5 + MLG_PREDICTION_FOOTPRINT_EPSILON;
                for (MlgLandingSelector.Column column : MlgLandingSelector.rank(
                        before.x, before.z, pos.x, pos.z, halfWidth)) {
                    BlockPos candidate = new BlockPos(column.x(), supportY, column.z());
                    // The support must really collide, not just occupy the sampled X/Z column.
                    if (!player.level().getBlockState(candidate).getCollisionShape(player.level(), candidate).isEmpty()) {
                        return new MlgLanding(candidate, tick, peakY);
                    }
                }
                return null;
            }
            previous = snapshot;
        }
        return null;
    }

    private boolean isFallDamageBlocking(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return false;
        }

        var state = client.level.getBlockState(pos);
        return state.is(Blocks.WATER)
                || state.is(Blocks.COBWEB)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.SLIME_BLOCK);
    }

    private BlockHitResult traceTarget(LocalPlayer player, Rotation rotation, ClipContext.Fluid fluidHandling) {
        if (player == null || rotation == null || player.level() == null) {
            return null;
        }

        double range = getMlgBlockRange(player);
        Vec3 eyes = player.getEyePosition();
        Vec3 end = eyes.add(rotation.directionVector().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(
                eyes,
                end,
                ClipContext.Block.OUTLINE,
                fluidHandling,
                player
        ));
        return hitResult instanceof BlockHitResult blockHitResult ? blockHitResult : null;
    }

    private boolean executeMlgPlacement(LocalPlayer player, MlgPlan plan, BlockHitResult hitResult, Rotation useRotation) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameMode == null || player == null || plan == null || hitResult == null || useRotation == null) {
            return false;
        }
        if (!canReachMlgTarget(player, plan, hitResult)) {
            return false;
        }

        boolean slime = mode.get() == Mode.SLIME_MLG && plan.type() == MlgPlanType.PLACE;
        int previousSlot = -1;
        if (plan.hotbarSlot() >= 0) {
            previousSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
            if (slime && !InventorySwap.INSTANCE.leaseHotbar(this, plan.hotbarSlot(), MLG_HOTBAR_RESET_TICKS)) return false;
            if (previousSlot != plan.hotbarSlot()
                    && !InventorySwap.INSTANCE.selectHotbar(plan.hotbarSlot())) return false;
        }

        boolean success;
        try {
            if (plan.type() == MlgPlanType.PICKUP_WATER) {
                InteractionResult useResult = interactItemWithRotation(player, plan.hand(), useRotation);
                success = useResult != null && useResult.consumesAction();
            } else {
                InteractionResult result = BlockInteractionPipeline.INSTANCE.useOnBlock(
                        this,
                        player,
                        plan.hand(),
                        slime ? plan.hotbarSlot() : -1,
                        MLG_HOTBAR_RESET_TICKS,
                        hitResult,
                        false
                );
                success = result != null && result.consumesAction();

                if (!success && !slime && result == InteractionResult.PASS) {
                    InteractionResult useResult = interactItemWithRotation(player, plan.hand(), useRotation);
                    success = useResult != null && useResult.consumesAction();
                }
            }
        } finally {
            if (previousSlot >= 0 && previousSlot != plan.hotbarSlot()) {
                InventorySwap.INSTANCE.restoreHotbar(previousSlot);
            }
        }

        if (success) {
            lastMlgActionTick = player.tickCount;
            if (mode.get() == Mode.SLIME_MLG && plan.type() == MlgPlanType.PLACE) {
                lastSlimePlacementTick = player.tickCount;
                lastSlimePlacementPos = plan.targetPos();
            }
            player.swing(plan.hand(), player.getItemInHand(plan.hand()).getInteractAnimation(), false);
            if (plan.item() == Items.SCAFFOLDING) {
                mlgSneakTicks = MLG_SCAFFOLDING_SNEAK_TICKS;
            }
            if (plan.type() == MlgPlanType.PLACE && plan.item() == Items.WATER_BUCKET) {
                lastWaterPlacements.add(new PlacedWater(plan.targetPos(), System.currentTimeMillis()));
            } else if (plan.type() == MlgPlanType.PICKUP_WATER && plan.item() == Items.BUCKET) {
                lastWaterPlacements.removeIf(placement -> placement.pos().equals(plan.targetPos()));
            }
        }
        return success;
    }

    private boolean canReachMlgTarget(LocalPlayer player, MlgPlan plan, BlockHitResult hitResult) {
        if (player == null || plan == null || hitResult == null) {
            return false;
        }
        if (!plan.matches(hitResult)) {
            return false;
        }

        return isWithinMlgBlockRange(player, hitResult.getLocation());
    }

    private boolean isWithinMlgBlockRange(LocalPlayer player, Vec3 point) {
        if (player == null || point == null) {
            return false;
        }
        double range = getMlgBlockRange(player);
        return player.getEyePosition().distanceToSqr(point) <= range * range;
    }

    private double getMlgBlockRange(LocalPlayer player) {
        return player != null ? player.blockInteractionRange() : 0.0;
    }

    private InteractionResult interactItemWithRotation(LocalPlayer player, InteractionHand hand, Rotation rotation) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameMode == null || player == null || rotation == null) {
            return InteractionResult.FAIL;
        }
        Rotation normalized = rotation.normalize();
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        final InteractionResult[] resultHolder = {InteractionResult.PASS};
        InteractionUtil.sendSequencedPacket(id ->
                new ServerboundUseItemPacket(hand, id, normalized.yaw(), normalized.pitch()));

        InteractionResult result = stack.use(client.level, player, hand);
        if (result instanceof InteractionResult.Success success) {
            ItemStack newStack = Objects.requireNonNullElseGet(success.heldItemTransformedTo(), () -> player.getItemInHand(hand));
            if (newStack != stack) {
                player.setItemInHand(hand, newStack);
            }
        }
        resultHolder[0] = result;
        return resultHolder[0];
    }

    public enum Mode {
        GROUD_SPOOF,
        PACKET_JUMP,
        VULCAN,
        NO_GROUND,
        MLG,
        SLIME_MLG
    }

    public enum MlgRotationMode { ROTATIONS, NO_ROTATIONS }

    public enum PacketJumpTiming {
        LANDING,
        FALLING
    }

    private enum MlgPlanType {
        PLACE,
        PICKUP_WATER
    }

    private record MlgLanding(BlockPos support, int ticksToLanding, double peakY) { }

    private record MlgSource(InteractionHand hand, int hotbarSlot, ItemStack stack, Item item) {
    }

    private record MlgPlan(
            MlgPlanType type,
            BlockPos targetPos,
            ScaffoldPlacementTarget target,
            Rotation rotation,
            InteractionHand hand,
            int hotbarSlot,
            Item item,
            int ticksToLanding
    ) {
        private boolean matches(BlockHitResult hitResult) {
            if (hitResult == null) {
                return false;
            }

            return switch (type) {
                case PLACE -> target != null && target.matches(hitResult);
                case PICKUP_WATER -> targetPos.equals(hitResult.getBlockPos());
            };
        }
    }

    private record PlacedWater(BlockPos pos, long timeMs) {
    }
}
