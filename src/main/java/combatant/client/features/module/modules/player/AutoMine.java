/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.player;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.relations.CategoryService;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.RotationProcessor;
import combatant.client.util.aiming.features.processors.anglesmooth.AngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SigmoidAngleSmooth;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.SmartAngleSmooth;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.MiningPolicy;
import combatant.client.util.block.interaction.MiningViewPlanner;
import combatant.client.util.block.interaction.BlockAimResolver;
import combatant.client.util.block.interaction.MiningTargetLock;
import combatant.client.util.combat.AntiBotTracker;
import combatant.client.util.player.BreakSpeedUtil;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Target planner only. A target is held from acquisition through rotation, break start and
 * subsequent continue ticks. The shared pipeline owns rotation/interaction execution.
 */
@ModuleInfo(id = "automine", displayName = "AutoMine", aliases = {"AutoCity"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.automine.description")
public final class AutoMine extends Module {
    private static final int ROTATION_PRIORITY = 25;
    private static final int TOOL_LEASE_TICKS = 2;

    private static final String OPT_PAUSE = "Pause while using";
    private static final String OPT_TOOL = "Auto tool";
    private static final String OPT_SWING = "Swing";
    private static final String OPT_ROTATE = "Rotate";
    private static final String OPT_SKIP_SLOW = "Skip slow blocks";
    private static final String OPT_CORRECT_TOOL = "Require correct tool";
    private static final String OPT_SUITABLE_TOOL = "Require suitable tool";
    private static final String TOOL_PICKAXE = "Pickaxe blocks";
    private static final String TOOL_AXE = "Axe blocks";
    private static final String TOOL_SHOVEL = "Shovel blocks";
    private static final String TOOL_HOE = "Hoe blocks";
    private static final String TOOL_OTHER = "Other blocks";
    private static final int RESCAN_INTERVAL = 5;
    private static final int REJECT_TICKS = 20;
    private static final double SCAN_VIEW_DEGREES = 72.0;
    private static final double REFOCUS_ANGLE_DEGREES = 26.0;
    private static final double TUNNEL_LOOK_DEGREES = 72.0;
    private static final String CITY_BURROW = "Burrow";
    private static final String CITY_SURROUND = "Surround";
    private static final String CITY_STOP_OPEN = "Stop when open";
    private static final String CITY_CRYSTAL = "Prefer crystal";
    private static final String CITY_FRIENDS = "Ignore friends";

    private final EnumValue<Mode> mode = enumMode("mode", Mode.SECTION);
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);
    private final NumberValue<Double> range = visibleWhen(
            num("range", 4.0, 3.0, 6.0), () -> anticheat.get() != AntiCheatPreset.VANILLA);
    private final BooleanMapValue options = group("automineOptions", "options", options());
    private final BooleanMapValue toolTypes = group("automineToolTypes", "tool_types", toolTypes());
    private final NumberValue<Integer> maxBreakTicks = visibleWhen(
            num("max_break_ticks", 100, 5, 400), () -> options.get(OPT_SKIP_SLOW));
    // Existing picker: arbitrary block item registry IDs, no hard-coded ore whitelist.
    private final ItemIdSetValue selectedBlocks = visibleWhen(
            itemList("automineSelectedBlocks", "selected_blocks", TextListSetting.PickerMode.ITEMS),
            () -> mode.get() == Mode.SELECTED);

    private final NumberValue<Float> targetRange = visibleWhen(
            num("target_range", 6.0f, 2.0f, 12.0f), () -> mode.get() == Mode.CITY);
    private final BooleanMapValue cityOptions = visibleWhen(
            group("automineCity", "city_options", cityOptions()), () -> mode.get() == Mode.CITY);

    private final EnumValue<MiningViewPlanner.HeightMode> heightMode = visibleWhen(
            enumMode("height_mode", MiningViewPlanner.HeightMode.LINE),
            () -> mode.get() != Mode.CITY);
    private final NumberValue<Integer> tunnelWidth = visibleWhen(
            num("tunnel_width", 3, 1, 5), () -> mode.get() == Mode.TUNNEL);
    private final NumberValue<Integer> tunnelHeight = visibleWhen(
            num("tunnel_height", 3, 1, 5), () -> mode.get() == Mode.TUNNEL);
    private final NumberValue<Integer> sectionWidth = visibleWhen(
            num("section_width", 3, 1, 7), () -> mode.get() == Mode.SECTION);
    private final NumberValue<Integer> sectionHeight = visibleWhen(
            num("section_height", 3, 1, 7), () -> mode.get() == Mode.SECTION);

    // One set of speed controls shared by all general-purpose smoothers. No duplicated
    // 30+ Linear/Smart/Sigmoid tuning fields in the main module settings.
    private final EnumValue<RotationMode> rotationMode = visibleWhen(
            enumCommon("automineRotationMode", "rotation_mode", CommonSettingSchemas.ROTATION_MODE,
                    RotationMode.SMART, RotationMode.values()), this::rotates);
    private final EnumValue<MovementCorrection> movementCorrection = visibleWhen(
            enumCommon("automineMovementCorrection", "movement_correction",
                    CommonSettingSchemas.ROTATION_MOVEMENT_CORRECTION,
                    MovementCorrection.SILENT, MovementCorrection.values()), this::rotates);
    private final NumberValue<Float> yawMin = visibleWhen(
            num("rotation_yaw_min", 70f, 1f, 180f), this::smooths);
    private final NumberValue<Float> yawMax = visibleWhen(
            num("rotation_yaw_max", 100f, 1f, 180f), this::smooths);
    private final NumberValue<Float> pitchMin = visibleWhen(
            num("rotation_pitch_min", 50f, 1f, 180f), this::smooths);
    private final NumberValue<Float> pitchMax = visibleWhen(
            num("rotation_pitch_max", 80f, 1f, 180f), this::smooths);
    private final NumberValue<Float> jitter = visibleWhen(
            num("rotation_jitter", 0.25f, 0f, 3f),
            () -> rotates() && rotationMode.get() == RotationMode.SMART);

    private final LinearAngleSmooth linearSmooth = new LinearAngleSmooth(yawMin, yawMax, pitchMin, pitchMax);
    private final SigmoidAngleSmooth sigmoidSmooth = new SigmoidAngleSmooth(
            yawMin, yawMax, pitchMin, pitchMax,
            fixed("sigmoidSteepness", 10f), fixed("sigmoidMidpoint", 0.3f));
    private final SmartAngleSmooth smartSmooth = new SmartAngleSmooth(
            yawMin, yawMax, pitchMin, pitchMax,
            fixed("smartSnap", 1.2f), jitter, jitter,
            fixedBool("smartDecelerate", true), fixed("smartDecelerateAngle", 35f),
            fixed("smartDecelerateMinFactor", 0.22f));

    private final Minecraft mc = Minecraft.getInstance();
    private final BlockInteractionPipeline interactions = BlockInteractionPipeline.INSTANCE;
    private final List<BlockPos> sectionTargets = new ArrayList<>();
    private ClientLevel sessionLevel;
    private Mode lastMode;
    private final MiningTargetLock<Target> lock = new MiningTargetLock<>();
    private BlockPos sectionAnchor;
    private Direction sectionFace;
    private final Map<BlockPos, Long> rejectedUntil = new HashMap<>();
    private long ticks;
    private long breakStartTick = -1;
    private int scanCooldown;
    private BlockPos lastSectionAnchor;
    private int activationFeetY = Integer.MIN_VALUE;
    private Vec3 sectionLook;
    private Vec3 acquiredLook;
    private long acquiredTick = -1;
    private boolean sectionCompleted;

    private static Map<String, Boolean> options() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(OPT_PAUSE, true);
        defaults.put(OPT_TOOL, true);
        defaults.put(OPT_SWING, true);
        defaults.put(OPT_ROTATE, true);
        defaults.put(OPT_SKIP_SLOW, true);
        defaults.put(OPT_CORRECT_TOOL, true);
        defaults.put(OPT_SUITABLE_TOOL, true);
        return defaults;
    }

    private static Map<String, Boolean> toolTypes() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TOOL_PICKAXE, true);
        defaults.put(TOOL_AXE, true);
        defaults.put(TOOL_SHOVEL, true);
        defaults.put(TOOL_HOE, true);
        defaults.put(TOOL_OTHER, true);
        return defaults;
    }

    private static Map<String, Boolean> cityOptions() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(CITY_BURROW, true);
        defaults.put(CITY_SURROUND, true);
        defaults.put(CITY_STOP_OPEN, true);
        defaults.put(CITY_CRYSTAL, true);
        defaults.put(CITY_FRIENDS, true);
        return defaults;
    }

    private static NumberValue<Float> fixed(String name, float value) {
        return new NumberValue<>(name, value, value, value);
    }

    private static combatant.client.config.values.BooleanValue fixedBool(String name, boolean value) {
        return new combatant.client.config.values.BooleanValue(name, value);
    }

    @Override
    public void onDisable() {
        resetSession();
        lastMode = null;
        sessionLevel = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Mode currentMode = mode.get();
        ticks++;
        rejectedUntil.entrySet().removeIf(e -> e.getValue() <= ticks);
        if (lastMode != currentMode || sessionLevel != level) {
            resetSession();
            sessionLevel = level;
            lastMode = currentMode;
        }
        if (player == null || level == null || mc.gameMode == null || !player.isAlive()) {
            resetSession();
            return;
        }
        if (activationFeetY == Integer.MIN_VALUE) activationFeetY = player.blockPosition().getY();
        if (ClientScreen.current() != null || (options.get(OPT_PAUSE) && player.isUsingItem())) {
            // Pause vanilla destroy progress, but keep the acquired target for resumption.
            interactions.stopMining(this);
            breakStartTick = -1;
            return;
        }

        Target lockedTarget = lock.target();
        // Mouse movement can cancel an unstarted alignment, but never interrupt a block
        // for which a destroy transaction has already begun.
        if (lockedTarget != null && currentMode != Mode.CITY
                && lock.phase() == MiningTargetLock.Phase.ALIGNING && acquiredLook != null
                && MiningViewPlanner.viewAngle(acquiredLook.x, acquiredLook.y, acquiredLook.z,
                player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z) > REFOCUS_ANGLE_DEGREES) {
            releaseTarget();
            lockedTarget = null;
        }
        if (lockedTarget != null && lock.phase() == MiningTargetLock.Phase.ALIGNING
                && acquiredTick >= 0 && ticks - acquiredTick > 80) {
            reject(lockedTarget.pos());
            releaseTarget();
            lockedTarget = null;
        }
        if (lockedTarget != null && !validTarget(player, level, lockedTarget)) {
            // If the target is still solid but became unreachable, suppress immediate reacquisition.
            if (isMineable(level, lockedTarget.pos())) reject(lockedTarget.pos());
            releaseTarget();
            lockedTarget = null;
        }
        if (lockedTarget == null && scanCooldown-- <= 0) {
            scanCooldown = switch (currentMode) {
                case SELECTED -> RESCAN_INTERVAL;
                case SECTION, TUNNEL -> 3; // Avoid hundreds of voxel traces every empty tick.
                case CITY -> 1;
            };
            Target candidate = switch (currentMode) {
                case CITY -> findCityTarget(player, level);
                case TUNNEL -> findTunnelTarget(player, level);
                case SECTION -> findSectionTarget(player, level);
                case SELECTED -> findSelectedBlock(player, level);
            };
            if (lock.acquire(candidate)) {
                acquiredLook = player.getLookAngle();
                acquiredTick = ticks;
            }
            lockedTarget = lock.target();
        }
        if (lockedTarget == null) {
            interactions.stopMining(this);
            return;
        }

        BlockInteractionPipeline.MiningPlan plan = new BlockInteractionPipeline.MiningPlan(
                effectiveRange(player), 0.0, options.get(OPT_SWING),
                options.get(OPT_TOOL), TOOL_LEASE_TICKS, rotationPlan(),
                anticheat.get() == AntiCheatPreset.GRIM,
                options.get(OPT_CORRECT_TOOL),
                options.get(OPT_SUITABLE_TOOL));
        // Called with the SAME target on every tick until it is mined or invalidated.
        // No callback may be scheduled for a previous target.
        interactions.mine(this, lockedTarget.pos(), lockedTarget.face(), plan);
        if (interactions.isMining(this, lockedTarget.pos())) {
            if (breakStartTick < 0) breakStartTick = ticks;
            lock.markBreaking(lockedTarget);
            if (options.get(OPT_SKIP_SLOW)
                    && MiningPolicy.miningTimedOut(ticks - breakStartTick, maxBreakTicks.get())) {
                // Server ignored the destroy transaction or progress stalled: back off, don't spin forever.
                reject(lockedTarget.pos());
                releaseTarget();
            }
        } else {
            breakStartTick = -1;
        }
    }

    private void releaseTarget() {
        interactions.stopMining(this);
        lock.release();
        acquiredLook = null;
        acquiredTick = -1;
        breakStartTick = -1;
    }

    private void resetSession() {
        releaseTarget();
        sectionTargets.clear();
        sectionAnchor = null;
        sectionFace = null;
        lastSectionAnchor = null;
        sectionLook = null;
        sectionCompleted = false;
        activationFeetY = Integer.MIN_VALUE;
        rejectedUntil.clear();
        scanCooldown = 0;
    }

    private boolean validTarget(LocalPlayer player, ClientLevel level, Target target) {
        return level.getBlockState(target.pos()).getBlock() == target.originalBlock()
                && heightAllowed(player, target.pos())
                && allowedBlock(player, level, target.pos())
                && interactions.resolveMiningAim(this, player, level, target.pos(), target.face(), effectiveRange(player)) != null;
    }

    private void reject(BlockPos pos) {
        rejectedUntil.put(pos.immutable(), ticks + REJECT_TICKS);
    }

    private boolean isRejected(BlockPos pos) {
        return rejectedUntil.getOrDefault(pos, 0L) > ticks;
    }

    private boolean allowedBlock(LocalPlayer player, ClientLevel level, BlockPos pos) {
        if (!isMineable(level, pos) || isRejected(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!toolTypes.get(blockToolGroup(state))) return false;
        if (mode.get() == Mode.SELECTED && !selectedBlocks.get().contains(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) return false;

        int bestSlot = options.get(OPT_TOOL) ? BreakSpeedUtil.bestHotbarSlot(
                player, level, pos, state, options.get(OPT_CORRECT_TOOL)) : -1;
        ItemStack stack = options.get(OPT_TOOL) && bestSlot >= 0
                ? player.getInventory().getItem(bestSlot) : player.getMainHandItem();
        boolean correct = !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        boolean suitable = blockToolGroup(state).equals(TOOL_OTHER) || stack.getDestroySpeed(state) > 1.0f;
        float progress = BreakSpeedUtil.progressPerTick(player, level, pos, state,
                stack, player.getMainHandItem());
        return MiningPolicy.admissible(progress, maxBreakTicks.get(), options.get(OPT_SKIP_SLOW),
                options.get(OPT_CORRECT_TOOL), correct, options.get(OPT_SUITABLE_TOOL), suitable);
    }

    private static String blockToolGroup(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return TOOL_PICKAXE;
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return TOOL_AXE;
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return TOOL_SHOVEL;
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return TOOL_HOE;
        return TOOL_OTHER;
    }

    private boolean heightAllowed(LocalPlayer player, BlockPos pos) {
        if (mode.get() == Mode.CITY) return true; // City targets are defined by the enemy, not the corridor.
        int originY = activationFeetY == Integer.MIN_VALUE ? player.blockPosition().getY() : activationFeetY;
        boolean sneaking = mc.options.keyShift.isDown();
        return pos.getY() >= MiningViewPlanner.floorY(
                heightMode.get(), originY, player.blockPosition().getY(), sneaking, player.getXRot());
    }

    private double viewAngle(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        return MiningViewPlanner.viewAngle(direction.x, direction.y, direction.z,
                point.x - eye.x, point.y - eye.y, point.z - eye.z);
    }

    private RankedTarget rankedCandidate(LocalPlayer player, ClientLevel level,
                                         BlockPos pos, Direction face, double maxAngle) {
        if (!heightAllowed(player, pos) || !allowedBlock(player, level, pos)) return null;
        BlockAimResolver.Aim aim = interactions.resolveMiningAim(player, level, pos, face, effectiveRange(player));
        if (aim == null) return null;
        double angle = viewAngle(player, aim.point());
        if (!MiningViewPlanner.withinView(angle, maxAngle)) return null;
        double distance = player.getEyePosition().distanceTo(aim.point());
        return new RankedTarget(new Target(pos.immutable(), aim.face(), level.getBlockState(pos).getBlock()),
                MiningViewPlanner.score(angle, distance));
    }

    private Target candidate(LocalPlayer player, ClientLevel level, BlockPos pos, Direction face) {
        // City intentionally has no camera-cone restriction; its target is an enemy's surround.
        RankedTarget ranked = rankedCandidate(player, level, pos, face, 180.0);
        return ranked == null ? null : ranked.target();
    }

    private Target findCrosshairTarget(LocalPlayer player, ClientLevel level) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        Target selected = candidate(player, level, blockHit.getBlockPos(), blockHit.getDirection());
        // The section PLANE is the actual crosshair face, not a multipoint's alternative face.
        return selected == null ? null : new Target(selected.pos(), blockHit.getDirection(), selected.originalBlock());
    }

    /**
     * Build a camera-aligned corridor, including its upper and side cells. Compare view
     * angles and surface reach across near slices, penalizing additional forward distance.
     */
    private Target findTunnelTarget(LocalPlayer player, ClientLevel level) {
        Vec3 position = player.position();
        int floor = MiningViewPlanner.floorY(heightMode.get(), activationFeetY,
                player.blockPosition().getY(), mc.options.keyShift.isDown(), player.getXRot());
        int top = player.blockPosition().getY() + tunnelHeight.get() - 1;
        if (top < floor) return null;

        double yaw = player.getYRot();
        double fx = MiningViewPlanner.forwardX(yaw);
        double fz = MiningViewPlanner.forwardZ(yaw);
        double sideX = fz;
        double sideZ = -fx;
        double reach = effectiveRange(player);
        int width = tunnelWidth.get();

        Set<BlockPos> visited = new HashSet<>();
        RankedTarget best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        // Camera aim remains decisive even when an upper/side cell lies slightly deeper.
        // A proportional forward penalty keeps equally aligned near surfaces ahead of distant ones.
        for (double depth = 0.8; depth <= Math.min(4.3, reach + 0.5); depth += 0.65) {
            for (int column = 0; column < width; column++) {
                double sideways = column - (width - 1) * 0.5;
                int x = (int) Math.floor(position.x + fx * depth + sideX * sideways);
                int z = (int) Math.floor(position.z + fz * depth + sideZ * sideways);
                for (int y = floor; y <= top; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!visited.add(pos)) continue;
                    double dx = pos.getX() + 0.5 - position.x;
                    double dz = pos.getZ() + 0.5 - position.z;
                    if (!MiningViewPlanner.inCorridor(yaw, dx, dz, width, reach + 0.5)) continue;
                    // Cheap view-cone test prevents excessive shape/ray work for side blocks.
                    Vec3 center = Vec3.atCenterOf(pos);
                    if (viewAngle(player, center) > TUNNEL_LOOK_DEGREES + 10.0) continue;
                    RankedTarget candidate = rankedCandidate(player, level, pos, null, TUNNEL_LOOK_DEGREES);
                    if (candidate != null) {
                        double forward = MiningViewPlanner.forwardDistance(yaw, dx, dz);
                        double score = candidate.score() + Math.max(0.0, forward - 1.0) * 9.0;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best == null ? null : best.target();
    }

    private Target findSectionTarget(LocalPlayer player, ClientLevel level) {
        // Camera refocusing changes the plane. Do not re-anchor merely because the central
        // block was mined and the crosshair now passes through the resulting hole.
        if (sectionLook != null && MiningViewPlanner.viewAngle(
                sectionLook.x, sectionLook.y, sectionLook.z,
                player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z)
                > REFOCUS_ANGLE_DEGREES) {
            sectionTargets.clear();
            sectionAnchor = null;
            sectionFace = null;
            lastSectionAnchor = null;
            sectionLook = null;
            sectionCompleted = false;
        }
        sectionTargets.removeIf(pos -> !heightAllowed(player, pos) || !allowedBlock(player, level, pos));
        if (sectionAnchor != null && sectionFace != null && sectionTargets.isEmpty()) {
            lastSectionAnchor = sectionAnchor;
            sectionAnchor = null;
            sectionFace = null;
            sectionCompleted = true;
        }
        // A completed section never implicitly tunnels into the next plane through a hole.
        // Changing the camera direction is the explicit re-anchor gesture.
        if (sectionCompleted) return null;
        if (sectionAnchor == null) {
            Target anchor = findCrosshairTarget(player, level);
            if (anchor == null || anchor.pos().equals(lastSectionAnchor)) return null;
            sectionAnchor = anchor.pos();
            sectionFace = anchor.face();
            sectionLook = player.getLookAngle();
            buildSection(sectionAnchor, sectionFace, sectionWidth.get(), sectionHeight.get(), level, player);
        }

        RankedTarget best = null;
        // No unconditional center-first or Manhattan-first: include upper and side surface
        // candidates, and score them against the actual camera orientation and hit distance.
        for (BlockPos pos : sectionTargets) {
            RankedTarget ranked = rankedCandidate(player, level, pos, sectionFace, SCAN_VIEW_DEGREES);
            if (ranked != null && (best == null || ranked.score() < best.score())) best = ranked;
        }
        return best == null ? null : best.target();
    }

    private Target findSelectedBlock(LocalPlayer player, ClientLevel level) {
        if (selectedBlocks.get().isEmpty()) return null;
        BlockPos center = player.blockPosition();
        double reach = effectiveRange(player);
        int radius = (int) Math.ceil(reach) + 1;
        double outerRangeSq = Math.pow(reach + 0.87, 2);
        Vec3 eyes = player.getEyePosition();
        RankedTarget best = null;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!heightAllowed(player, pos)) continue;
                    Vec3 blockCenter = Vec3.atCenterOf(pos);
                    if (eyes.distanceToSqr(blockCenter) > outerRangeSq) continue;
                    // Cull behind/off-screen blocks before tool checks and expensive multipoint traces.
                    if (viewAngle(player, blockCenter) > SCAN_VIEW_DEGREES + 12.0) continue;
                    RankedTarget ranked = rankedCandidate(player, level, pos, null, SCAN_VIEW_DEGREES);
                    if (ranked != null && (best == null || ranked.score() < best.score())) best = ranked;
                }
            }
        }
        return best == null ? null : best.target();
    }

    private void buildSection(BlockPos anchor, Direction face, int width, int height,
                              ClientLevel level, LocalPlayer player) {
        sectionTargets.clear();
        int minU = -((width - 1) / 2);
        int maxU = width / 2;
        int minV = -((height - 1) / 2);
        int maxV = height / 2;
        for (int v = minV; v <= maxV; v++) {
            for (int u = minU; u <= maxU; u++) {
                BlockPos pos = sectionOffset(anchor, face, u, v);
                if (heightAllowed(player, pos) && isMineable(level, pos)) sectionTargets.add(pos.immutable());
            }
        }
    }

    private static BlockPos sectionOffset(BlockPos anchor, Direction face, int u, int v) {
        return switch (face.getAxis()) {
            case X -> anchor.offset(0, v, u);
            case Y -> anchor.offset(u, 0, v);
            case Z -> anchor.offset(u, v, 0);
        };
    }

    private record RankedTarget(Target target, double score) { }

    private Target findCityTarget(LocalPlayer player, ClientLevel level) {
        Player enemy = findPlayerTarget(player, level);
        if (enemy == null) return null;
        BlockPos feet = enemy.blockPosition();
        double reach = effectiveRange(player);
        if (cityOptions.get(CITY_BURROW)) {
            Target burrow = candidate(player, level, feet, BlockInteractionPipeline.faceFor(player, feet));
            if (burrow != null) return burrow;
        }
        if (!cityOptions.get(CITY_SURROUND)) return null;

        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        boolean bestCrystal = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(direction);
            BlockState state = level.getBlockState(side);
            if (state.canBeReplaced()) {
                if (cityOptions.get(CITY_STOP_OPEN)) return null;
                continue;
            }
            if (!allowedBlock(player, level, side)) continue;
            if (interactions.resolveMiningAim(player, level, side,
                    BlockInteractionPipeline.faceFor(player, side), reach) == null) continue;
            boolean crystal = cityOptions.get(CITY_CRYSTAL) && leavesCrystalSpot(level, side);
            double distSq = player.getEyePosition().distanceToSqr(Vec3.atCenterOf(side));
            if ((crystal && !bestCrystal) || (crystal == bestCrystal && distSq < bestSq)) {
                bestSq = distSq;
                best = side;
                bestCrystal = crystal;
            }
        }
        return best == null ? null : candidate(player, level, best,
                BlockInteractionPipeline.faceFor(player, best));
    }

    private Player findPlayerTarget(LocalPlayer player, ClientLevel level) {
        double bestSq = targetRange.get() * targetRange.get();
        Player best = null;
        for (AbstractClientPlayer other : level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (cityOptions.get(CITY_FRIENDS) && CategoryService.isFriend(other)) continue;
            if (AntiBotTracker.INSTANCE.isBot(other)) continue;
            double sq = player.distanceToSqr(other);
            if (sq < bestSq) {
                bestSq = sq;
                best = other;
            }
        }
        return best;
    }

    private boolean rotates() {
        return options.get(OPT_ROTATE);
    }

    private boolean smooths() {
        return rotates() && rotationMode.get() != RotationMode.INSTANT;
    }

    private BlockInteractionPipeline.RotationPlan rotationPlan() {
        if (!rotates()) return BlockInteractionPipeline.RotationPlan.disabled();
        AngleSmooth processor = switch (rotationMode.get()) {
            case LINEAR -> linearSmooth;
            case SIGMOID -> sigmoidSmooth;
            case SMART -> smartSmooth;
            case INSTANT -> null;
        };
        List<RotationProcessor> processors = processor == null ? List.of() : List.of(processor);
        return new BlockInteractionPipeline.RotationPlan(true, processors, 2, 2.0f,
                false, movementCorrection.get(), ROTATION_PRIORITY);
    }

    private double effectiveRange(LocalPlayer player) {
        double reach = player.blockInteractionRange();
        return switch (anticheat.get()) {
            case CUSTOM -> Math.min(range.get(), reach);
            case GRIM -> Math.min(range.get(), reach);
            case VANILLA -> reach;
        };
    }

    private static boolean isMineable(ClientLevel level, BlockPos pos) {
        return BlockInteractionPipeline.isMineable(level, pos, level.getBlockState(pos));
    }

    private static boolean leavesCrystalSpot(ClientLevel level, BlockPos side) {
        BlockState base = level.getBlockState(side.below());
        return (base.is(Blocks.OBSIDIAN) || base.is(Blocks.BEDROCK))
                && level.getBlockState(side.above()).isAir();
    }

    private record Target(BlockPos pos, Direction face, Block originalBlock) { }

    private enum Mode implements EnumValue.IdProvider {
        CITY("city"), TUNNEL("tunnel"), SECTION("section"), SELECTED("selected");
        private final String id;
        Mode(String id) { this.id = id; }
        @Override public String getId() { return id; }
    }

    private enum RotationMode implements EnumValue.IdProvider, EnumValue.AliasProvider {
        LINEAR("linear"), SIGMOID("sigmoid"), SMART("smart"), INSTANT("instant");
        private final String id;
        RotationMode(String id) { this.id = id; }
        @Override public String getId() { return id; }
        @Override public List<String> aliases() {
            return this == INSTANT ? List.of("none") : List.of();
        }
    }
}
