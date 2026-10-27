/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.ActionResult;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.BreakBlockAction;
import combatant.client.automation.actions.NavigateAction;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import combatant.client.util.block.interaction.BlockInteractionPipeline.RotationPlan;
import combatant.client.util.player.BreakSpeedUtil;
import combatant.client.util.player.ToolSelection;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Clears a box between two selected corners layer by layer and lane by lane. Each layer is split into lanes (strips
 * of {@code lane_width} blocks) mined in a serpentine order that starts at the corner nearest the player; layers go
 * top-down (or bottom-up) and {@code depth} caps how many are mined. Blocks are broken through
 * {@link BreakBlockAction}, the player walks with {@link NavigateAction} only when the next block is out of reach,
 * and cells that would open lava or water, drop a falling block or leave a pit/void are skipped and listed.
 *
 * <p>The corners come from the looked-at block (two keybinds) or the {@code @areaminer} command. A sweep ends with a
 * verification pass that re-checks the whole box, so blocks that fell back in are mined too.
 */
@ModuleInfo(
        id = "areaminer",
        displayName = "AreaMiner",
        aliases = {"AutoAreaMine", "AreaDig", "Excavator"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.areaminer.description")
public final class AreaMiner extends Module {

    public enum LayerOrder {
        TOP_DOWN,
        BOTTOM_UP
    }

    private enum Verdict {
        DONE,
        MINE,
        DEFER,
        SKIP
    }

    /** Why a cell is never mined; the ordinal indexes {@link #skippedBy}. */
    private enum Hazard {
        NONE,
        LAVA,
        WATER,
        FALLING,
        VOID,
        UNREACHABLE,
        /** Not a skip: wait for the situation to change (chunk loading, a falling block that is mined first). */
        PENDING
    }

    private static final String ACTION_CORNER = "set_corner";
    private static final String ACTION_PAUSE = "pause_resume";
    private static final String ACTION_CLEAR = "clear_selection";
    private static final String STATUS_KEY = "areaminer.status";
    private static final String HAZARD_KEY = "areaminer.warning";

    private static final int SCAN_BUDGET = 4096;
    private static final int LOOKAHEAD = 192;
    private static final int STATUS_INTERVAL = 20;
    private static final int RECHECK_TICKS = 10;
    private static final int MAX_SWEEPS = 4;
    private static final int MAX_CELL_FAILURES = 2;
    private static final int MAX_SKIPPED_RENDERED = 300;
    private static final double SKIPPED_RENDER_DISTANCE_SQ = 64.0 * 64.0;
    private static final double REACH_MARGIN = 0.5;
    private static final int ARRIVAL_TIMEOUT_TICKS = 600;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> depth = num("areaMinerDepth", "depth", 0, 0, 256);
    private final EnumValue<LayerOrder> layerOrder =
            enumSetting("areaMinerLayerOrder", "layer_order", LayerOrder.TOP_DOWN, LayerOrder.values());
    private final NumberValue<Integer> laneWidth = num("areaMinerLaneWidth", "lane_width", 3, 1, 5);
    private final NumberValue<Double> range = num("areaMinerRange", "range", 4.5D, 2.0D, 6.0D);
    private final NumberValue<Integer> batchSize = num("areaMinerBatchSize", "batch_size", 3, 1, 8);
    private final NumberValue<Integer> selectRange = num("areaMinerSelectRange", "select_range", 64, 8, 128);
    private final BooleanValue autoStart = bool("areaMinerAutoStart", "auto_start", true);
    private final BooleanValue disableWhenDone = bool("areaMinerDisableWhenDone", "disable_when_done", true);
    private final BooleanValue rotate = bool("areaMinerRotate", "rotate", true);
    private final BooleanValue swing = bool("areaMinerSwing", "swing", true);
    private final BooleanValue requireSuitableTool =
            bool("areaMinerRequireSuitableTool", "require_suitable_tool", true);
    private final NumberValue<Integer> durabilityGuard =
            num("areaMinerDurabilityGuard", "durability_guard", 5, 0, 90);
    private final BooleanValue avoidLava = bool("areaMinerAvoidLava", "avoid_lava", true);
    private final BooleanValue avoidWater = bool("areaMinerAvoidWater", "avoid_water", true);
    private final BooleanValue avoidFalling = bool("areaMinerAvoidFalling", "avoid_falling", true);
    private final BooleanValue avoidVoid = bool("areaMinerAvoidVoid", "avoid_void", true);
    private final NumberValue<Integer> maxDrop = visibleWhen(
            num("areaMinerMaxDrop", "max_drop", 2, 0, 3), avoidVoid::get);
    private final BooleanValue pauseInScreens = bool("areaMinerPauseInScreens", "pause_in_screens", true);
    private final BooleanValue showStatus = bool("areaMinerShowStatus", "show_status", true);
    private final BooleanValue render = bool("areaMinerRender", "render", true);
    private final BooleanValue renderSkipped = visibleWhen(
            bool("areaMinerRenderSkipped", "render_skipped", true), render::get);
    private final RGBAColorValue boxFill = visibleWhen(
            color("areaMinerBoxFill", "box_fill_color", "#3388FF18"), render::get);
    private final RGBAColorValue boxLine = visibleWhen(
            color("areaMinerBoxLine", "box_line_color", "#55AAFFFF"), render::get);
    private final RGBAColorValue layerFill = visibleWhen(
            color("areaMinerLayerFill", "layer_fill_color", "#44FF9930"), render::get);
    private final RGBAColorValue targetFill = visibleWhen(
            color("areaMinerTargetFill", "target_fill_color", "#FFAA0055"), render::get);
    private final RGBAColorValue targetLine = visibleWhen(
            color("areaMinerTargetLine", "target_line_color", "#FFCC33FF"), render::get);
    private final RGBAColorValue skippedLine = visibleWhen(
            color("areaMinerSkippedLine", "skipped_line_color", "#FF3333FF"), render::get);

    // Selection and plan geometry.
    private BlockPos corner1;
    private BlockPos corner2;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int layers;
    private boolean alongX;
    private int lenA;
    private int lenB;
    private int width;
    private int lanes;
    private boolean topDown;
    private int planDepth = -1;
    private int planWidth = -1;
    private LayerOrder planOrder;

    // Run state.
    private ClientLevel level;
    private final Cursor cursor = new Cursor();
    private final Set<Long> skipped = new HashSet<>();
    private final Map<Long, Integer> failures = new HashMap<>();
    private final int[] skippedBy = new int[Hazard.values().length];
    private final List<BlockPos> activeTargets = new ArrayList<>();
    private AutomationTask task;
    private boolean paused;
    private boolean finished;
    private int mined;
    private int sweeps;
    private int sweepMined;
    private int sweepDeferred;
    private int recheckIn;
    private long ticks;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos lookScratch = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos floorProbe = new BlockPos.MutableBlockPos();

    public AreaMiner() {
        action(ACTION_CORNER, "NONE");
        action(ACTION_PAUSE, "NONE");
        action(ACTION_CLEAR, "NONE");
    }

    // ------------------------------------------------------------------ public control (keys, command)

    public boolean hasSelection() {
        return corner1 != null && corner2 != null;
    }

    public BlockPos corner(int index) {
        return index == 0 ? corner1 : corner2;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isFinished() {
        return finished;
    }

    public void setDepthLimit(int layersToMine) {
        depth.set(depth.castToType(Math.max(0, Math.min(depth.getMax(), layersToMine))));
    }

    /** Sets corner 0 or 1. Setting corner 0 starts a new selection; corner 1 completes it. */
    public void setCorner(int index, BlockPos pos) {
        if (pos == null) return;
        cancelTask("selection changed");
        if (index == 0) {
            corner1 = pos.immutable();
            corner2 = null;
        } else {
            if (corner1 == null) corner1 = pos.immutable();
            else corner2 = pos.immutable();
        }
        resetRun();
        if (hasSelection()) {
            rebuildPlan();
            paused = !autoStart.get();
            Notifier.info(I18n.get("notification.areaminer.selected", boxSize(), layers));
        } else {
            paused = false;
        }
    }

    /** Looked-at block as the next corner; true when a block was in range. */
    public boolean selectLooked() {
        BlockPos pos = lookedBlock();
        if (pos == null) {
            Notifier.warning(I18n.get("notification.areaminer.no_block"));
            return false;
        }
        boolean first = corner1 == null || hasSelection();
        setCorner(first ? 0 : 1, pos);
        if (first) Notifier.info(I18n.get("notification.areaminer.corner1", pos.getX(), pos.getY(), pos.getZ()));
        return true;
    }

    /** Looked-at block as corner {@code index} (0 or 1); true when a block was in range. */
    public boolean selectLookedAs(int index) {
        BlockPos pos = lookedBlock();
        if (pos == null) return false;
        setCorner(index, pos);
        return true;
    }

    public void setPaused(boolean value) {
        if (!hasSelection() || finished) return;
        if (paused == value) return;
        paused = value;
        Notifier.clear(HAZARD_KEY);
        if (paused) {
            cancelTask("paused");
            Notifier.info(I18n.get("notification.areaminer.paused"));
        } else {
            recheckIn = 0;
            Notifier.info(I18n.get("notification.areaminer.resumed"));
        }
    }

    public void clearSelection() {
        cancelTask("selection cleared");
        corner1 = null;
        corner2 = null;
        layers = 0;
        resetRun();
        paused = false;
        Notifier.clear(STATUS_KEY);
        Notifier.clear(HAZARD_KEY);
    }

    /** Restarts mining from the first layer, forgetting skipped cells. */
    public void restart() {
        if (!hasSelection()) return;
        cancelTask("restart");
        resetRun();
        rebuildPlan();
        paused = false;
    }

    public String statusText() {
        if (!hasSelection()) {
            return corner1 == null
                    ? I18n.get("notification.areaminer.status_none")
                    : I18n.get("notification.areaminer.status_corner", corner1.getX(), corner1.getY(), corner1.getZ());
        }
        String state = finished ? I18n.get("notification.areaminer.state_done")
                : paused ? I18n.get("notification.areaminer.state_paused")
                : I18n.get("notification.areaminer.state_mining");
        return I18n.get("notification.areaminer.status", state, Math.min(cursor.layer + 1, layers), layers,
                percent(), mined, skipped.size());
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        level = mc.level;
        task = null;
        finished = false;
        recheckIn = 0;
        ticks = 0;
        sweeps = 0;
        resetRun();
        paused = false;
        if (hasSelection()) {
            rebuildPlan();
            paused = !autoStart.get();
        }
    }

    @Override
    public void onDisable() {
        cancelTask("areaminer disabled");
        paused = false;
        Notifier.clear(STATUS_KEY);
        Notifier.clear(HAZARD_KEY);
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        corner1 = null;
        corner2 = null;
        resetRun();
        task = null;
        level = null;
        if (isEnabled()) Notifier.clear(STATUS_KEY);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel world = mc.level;
        if (player == null || world == null || mc.gameMode == null) {
            cancelTask("no world");
            return;
        }
        if (level != world) {
            level = world;
            corner1 = null;
            corner2 = null;
            resetRun();
            return;
        }
        ticks++;
        handleKeys();
        if (!hasSelection()) return;
        if (planDepth != depth.get() || planWidth != laneWidth.get() || planOrder != layerOrder.get()) {
            cancelTask("settings changed");
            resetRun();
            rebuildPlan();
        }
        if (showStatus.get() && ticks % STATUS_INTERVAL == 0) {
            Notifier.update(STATUS_KEY, statusText(), finished ? Notifier.Type.SUCCESS : Notifier.Type.INFO);
        }
        if (finished || paused || task != null) return;
        if (recheckIn > 0) {
            recheckIn--;
            return;
        }
        if (pauseInScreens.get() && ClientScreen.current() != null) return;
        planNext(player, world);
    }

    private void handleKeys() {
        if (isActionPressedOnce(ACTION_CORNER)) selectLooked();
        if (isActionPressedOnce(ACTION_PAUSE)) {
            if (hasSelection()) setPaused(!paused);
        }
        if (isActionPressedOnce(ACTION_CLEAR)) {
            clearSelection();
            Notifier.info(I18n.get("notification.areaminer.cleared"));
        }
    }

    // ------------------------------------------------------------------ plan geometry

    private void rebuildPlan() {
        minX = Math.min(corner1.getX(), corner2.getX());
        maxX = Math.max(corner1.getX(), corner2.getX());
        minY = Math.min(corner1.getY(), corner2.getY());
        maxY = Math.max(corner1.getY(), corner2.getY());
        minZ = Math.min(corner1.getZ(), corner2.getZ());
        maxZ = Math.max(corner1.getZ(), corner2.getZ());
        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;
        int sizeY = maxY - minY + 1;
        planDepth = depth.get();
        planWidth = laneWidth.get();
        planOrder = layerOrder.get();
        layers = planDepth == 0 ? sizeY : Math.min(planDepth, sizeY);
        topDown = planOrder == LayerOrder.TOP_DOWN;
        alongX = sizeX >= sizeZ;
        lenA = alongX ? sizeX : sizeZ;
        lenB = alongX ? sizeZ : sizeX;
        width = Math.max(1, Math.min(planWidth, lenB));
        lanes = (lenB + width - 1) / width;
        cursor.reset();
        orient(cursor);
    }

    private String boxSize() {
        return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }

    private int layerY(int layer) {
        return topDown ? maxY - layer : minY + layer;
    }

    private int percent() {
        long perLayer = (long) lenA * lenB;
        long total = perLayer * layers;
        if (total <= 0) return 0;
        if (finished) return 100;
        long done = cursor.layer * perLayer + (long) cursor.lane * width * lenA + cursor.idx;
        return (int) Math.min(100, done * 100 / total);
    }

    private boolean inBox(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    /** Orders the lanes and the direction inside lane 0 so a layer starts at the corner nearest the player. */
    private void orient(Cursor c) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        double a = alongX ? player.getX() : player.getZ();
        double b = alongX ? player.getZ() : player.getX();
        double minA = alongX ? minX : minZ;
        double minB = alongX ? minZ : minX;
        c.flipA = a > minA + lenA / 2.0;
        c.flipB = b > minB + lenB / 2.0;
    }

    private int laneWidthAt(int lane) {
        return Math.min(width, lenB - lane * width);
    }

    private BlockPos.MutableBlockPos cellPos(Cursor c, BlockPos.MutableBlockPos out) {
        int laneW = laneWidthAt(c.lane);
        int a = c.idx / laneW;
        int b = c.lane * width + c.idx % laneW;
        if (((c.lane & 1) == 1) != c.flipA) a = lenA - 1 - a;
        if (c.flipB) b = lenB - 1 - b;
        int x = alongX ? minX + a : minX + b;
        int z = alongX ? minZ + b : minZ + a;
        return out.set(x, layerY(c.layer), z);
    }

    private void advance(Cursor c) {
        c.idx++;
        if (c.idx < lenA * laneWidthAt(c.lane)) return;
        c.idx = 0;
        c.lane++;
        if (c.lane < lanes) return;
        c.lane = 0;
        c.layer++;
        orient(c);
    }

    private final class Cursor {
        int layer;
        int lane;
        int idx;
        boolean flipA;
        boolean flipB;

        void reset() {
            layer = 0;
            lane = 0;
            idx = 0;
        }

        Cursor copy() {
            Cursor c = new Cursor();
            c.layer = layer;
            c.lane = lane;
            c.idx = idx;
            c.flipA = flipA;
            c.flipB = flipB;
            return c;
        }
    }

    // ------------------------------------------------------------------ planning

    private void planNext(LocalPlayer player, ClientLevel world) {
        int budget = SCAN_BUDGET;
        while (budget-- > 0) {
            if (cursor.layer >= layers) {
                if (!endOfSweep()) return;
                continue;
            }
            BlockPos.MutableBlockPos pos = cellPos(cursor, scratch);
            Verdict verdict = evaluate(world, pos);
            if (verdict == Verdict.MINE) {
                startCell(player, world, pos.immutable());
                return;
            }
            if (verdict == Verdict.DEFER) sweepDeferred++;
            advance(cursor);
        }
    }

    /** @return true when another sweep started and scanning should continue. */
    private boolean endOfSweep() {
        boolean again = (sweepMined > 0 || sweepDeferred > 0) && sweeps < MAX_SWEEPS;
        if (!again) {
            finish();
            return false;
        }
        boolean onlyDeferred = sweepMined == 0;
        sweeps++;
        sweepMined = 0;
        sweepDeferred = 0;
        cursor.reset();
        orient(cursor);
        if (onlyDeferred) {
            recheckIn = RECHECK_TICKS * 4;
            return false;
        }
        return true;
    }

    private void finish() {
        finished = true;
        activeTargets.clear();
        Notifier.clear(HAZARD_KEY);
        Notifier.clear(STATUS_KEY);
        Notifier.success(I18n.get("notification.areaminer.finished", mined, skipped.size()));
        if (!skipped.isEmpty()) {
            Notifier.info(I18n.get("notification.areaminer.skipped_detail", skippedBy[Hazard.LAVA.ordinal()],
                    skippedBy[Hazard.WATER.ordinal()], skippedBy[Hazard.FALLING.ordinal()],
                    skippedBy[Hazard.VOID.ordinal()], skippedBy[Hazard.UNREACHABLE.ordinal()]));
        }
        if (disableWhenDone.get()) setEnabled(false);
    }

    private void startCell(LocalPlayer player, ClientLevel world, BlockPos first) {
        BlockState state = world.getBlockState(first);
        String toolProblem = toolProblem(player, world, first, state);
        if (toolProblem != null) {
            paused = true;
            cancelTask("tool problem");
            Notifier.update(HAZARD_KEY, I18n.get(toolProblem), Notifier.Type.WARNING);
            return;
        }
        Notifier.clear(HAZARD_KEY);

        double reach = range.get();
        if (!inReach(player, first, reach)) {
            navigateTo(first, reach);
            return;
        }
        mineBatch(player, world, first, reach);
    }

    private void navigateTo(BlockPos target, double reach) {
        long key = target.asLong();
        NavigationRequest request = NavigationRequest.reach(target.getX(), target.getY(), target.getZ(),
                Math.max(1.5, reach - 1.25))
                .withOptions(NavigationRequest.Options.DEFAULT
                        .withMaxDrop(avoidVoid.get() ? maxDrop.get() : 3)
                        .withTimeout(ARRIVAL_TIMEOUT_TICKS));
        activeTargets.clear();
        activeTargets.add(target);
        submit("AreaMinerWalk", new NavigateAction(request), finished -> {
            if (finished.result().isFailure()) recordFailure(key, Hazard.UNREACHABLE);
        });
    }

    private void mineBatch(LocalPlayer player, ClientLevel world, BlockPos first, double reach) {
        MiningPlan plan = miningPlan(reach);
        List<BlockPos> batch = new ArrayList<>(batchSize.get());
        Cursor look = cursor.copy();
        int layer = look.layer;
        for (int scanned = 0; scanned < LOOKAHEAD && batch.size() < batchSize.get() && look.layer == layer; scanned++) {
            BlockPos.MutableBlockPos pos = cellPos(look, lookScratch);
            Verdict verdict = evaluate(world, pos);
            if (verdict == Verdict.MINE) {
                if (!batch.isEmpty() && !inReach(player, pos, reach)) break;
                batch.add(pos.immutable());
            }
            advance(look);
        }
        if (batch.isEmpty()) batch.add(first);

        List<Action> steps = new ArrayList<>(batch.size());
        for (BlockPos target : batch) {
            long key = target.asLong();
            Action breakBlock = new BreakBlockAction(target, null, plan, 2, 200, 40);
            Action count = Actions.step("AreaMinerMined", () -> {
                mined++;
                sweepMined++;
                failures.remove(key);
                return ActionResult.SUCCESS;
            });
            Action fail = Actions.step("AreaMinerFail", () -> {
                recordFailure(key, Hazard.UNREACHABLE);
                return ActionResult.SUCCESS;
            });
            steps.add(Actions.selector("AreaMinerBlock", Actions.sequence("AreaMinerBreak", breakBlock, count), fail));
        }
        activeTargets.clear();
        activeTargets.addAll(batch);
        submit("AreaMinerBatch", Actions.sequence("AreaMinerBatch", steps.toArray(new Action[0])), finished -> { });
    }

    private void submit(String name, Action root, java.util.function.Consumer<AutomationTask> extra) {
        task = AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, name, root)
                .priority(ActionPriority.BUILD)
                .onFinish(finished -> {
                    if (finished != task) return;
                    task = null;
                    activeTargets.clear();
                    if (!isEnabled()) return;
                    extra.accept(finished);
                    if (finished.result().isFailure()) recheckIn = 2;
                    else if (!finished.result().isSuccess()) recheckIn = RECHECK_TICKS;
                })
                .build());
    }

    private MiningPlan miningPlan(double reach) {
        RotationPlan rotation = rotate.get()
                ? new RotationPlan(true, List.of(), 2, 4.0f, false, MovementCorrection.SILENT, ActionPriority.BUILD)
                : RotationPlan.disabled();
        return new MiningPlan(reach, 0.0, swing.get(), true, 5, rotation, false, false, requireSuitableTool.get());
    }

    private static boolean inReach(LocalPlayer player, BlockPos pos, double reach) {
        double r = Math.max(1.0, reach - REACH_MARGIN);
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= r * r;
    }

    private void recordFailure(long key, Hazard hazard) {
        int count = failures.merge(key, 1, Integer::sum);
        if (count >= MAX_CELL_FAILURES) skip(key, hazard);
    }

    private void skip(long key, Hazard hazard) {
        if (skipped.add(key)) skippedBy[hazard.ordinal()]++;
    }

    // ------------------------------------------------------------------ safety checks

    private Verdict evaluate(ClientLevel world, BlockPos pos) {
        if (!world.hasChunkAt(pos)) return Verdict.DEFER;
        BlockState state = world.getBlockState(pos);
        if (!BlockInteractionPipeline.isMineable(world, pos, state)) return Verdict.DONE;
        long key = pos.asLong();
        if (skipped.contains(key)) return Verdict.DONE;
        Hazard hazard = hazard(world, pos, state);
        if (hazard == Hazard.NONE) return Verdict.MINE;
        if (hazard == Hazard.PENDING) return Verdict.DEFER;
        skip(key, hazard);
        return Verdict.SKIP;
    }

    private Hazard hazard(ClientLevel world, BlockPos pos, BlockState state) {
        FluidState own = state.getFluidState();
        if (!own.isEmpty()) {
            Hazard h = fluidHazard(own);
            if (h != Hazard.NONE) return h;
        }
        for (Direction direction : Direction.values()) {
            probe.setWithOffset(pos, direction);
            if (!world.hasChunkAt(probe)) return Hazard.PENDING;
            BlockState neighbour = world.getBlockState(probe);
            Hazard h = fluidHazard(neighbour.getFluidState());
            if (h != Hazard.NONE) return h;
            if (avoidLava.get() && neighbour.getBlock() instanceof BaseFireBlock) return Hazard.LAVA;
        }
        if (avoidFalling.get()) {
            BlockPos above = pos.above();
            BlockState aboveState = world.getBlockState(above);
            if (aboveState.getBlock() instanceof Fallable) {
                boolean willBeMined = inBox(above) && !skipped.contains(above.asLong());
                return willBeMined ? Hazard.PENDING : Hazard.FALLING;
            }
        }
        if (avoidVoid.get()) return floorHazard(world, pos);
        return Hazard.NONE;
    }

    private Hazard fluidHazard(FluidState fluid) {
        if (fluid.isEmpty()) return Hazard.NONE;
        if (fluid.is(FluidTags.LAVA)) return avoidLava.get() ? Hazard.LAVA : Hazard.NONE;
        return avoidWater.get() ? Hazard.WATER : Hazard.NONE;
    }

    /**
     * Walks down from the cell. Cells inside the box count as free (they are mined or about to be); below the box an
     * open fall deeper than {@code max_drop}, the world bottom or a liquid floor makes the cell a hazard.
     */
    private Hazard floorHazard(ClientLevel world, BlockPos pos) {
        int drop = 0;
        int limit = maxDrop.get();
        BlockPos.MutableBlockPos cell = floorProbe;
        for (int y = pos.getY() - 1; ; y--) {
            if (y < world.getMinY()) return Hazard.VOID;
            cell.set(pos.getX(), y, pos.getZ());
            if (!world.hasChunkAt(cell)) return Hazard.PENDING;
            BlockState below = world.getBlockState(cell);
            FluidState fluid = below.getFluidState();
            if (!fluid.isEmpty()) {
                Hazard h = fluidHazard(fluid);
                if (h != Hazard.NONE) return h;
                return Hazard.NONE;
            }
            if (!below.getCollisionShape(world, cell).isEmpty()) return Hazard.NONE;
            if (y >= minY && y <= maxY) continue;
            if (++drop > limit) return Hazard.VOID;
        }
    }

    /** Translation key of the reason the best tool is unusable, or null when mining can go on. */
    private String toolProblem(LocalPlayer player, ClientLevel world, BlockPos pos, BlockState state) {
        int slot = BreakSpeedUtil.bestHotbarSlot(player, world, pos, state, false);
        ItemStack stack = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
        if (requireSuitableTool.get() && hasToolTag(state)
                && (slot < 0 || stack.getDestroySpeed(state) <= 1.0f)) {
            return "notification.areaminer.no_tool";
        }
        if (durabilityGuard.get() > 0 && !stack.isEmpty() && stack.isDamageableItem()
                && ToolSelection.durabilityLeft(stack.getDamageValue(), stack.getMaxDamage())
                <= durabilityGuard.get() / 100.0f) {
            return "notification.areaminer.low_durability";
        }
        return null;
    }

    private static boolean hasToolTag(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(BlockTags.MINEABLE_WITH_HOE);
    }

    // ------------------------------------------------------------------ helpers

    private BlockPos lookedBlock() {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        HitResult hit = player.pick(selectRange.get(), 1.0f, false);
        if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) return block.getBlockPos();
        return null;
    }

    private void cancelTask(String reason) {
        task = null;
        activeTargets.clear();
        AutomationRuntime.INSTANCE.cancelOwner(this, reason);
    }

    private void resetRun() {
        cursor.reset();
        skipped.clear();
        failures.clear();
        java.util.Arrays.fill(skippedBy, 0);
        activeTargets.clear();
        finished = false;
        mined = 0;
        sweeps = 0;
        sweepMined = 0;
        sweepDeferred = 0;
        recheckIn = 0;
        if (hasSelection() && layers > 0) orient(cursor);
    }

    // ------------------------------------------------------------------ render

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || corner1 == null || mc.player == null) return;
        if (!hasSelection()) {
            AABB single = new AABB(corner1).inflate(0.003);
            renderer.filledBox(single, boxFill.getArgb(), Renderer3D.DepthMode.NONE);
            renderer.outlineBox(single, boxLine.getArgb(), 2.0f, Renderer3D.DepthMode.NONE);
            return;
        }
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1).inflate(0.003);
        renderer.filledBox(box, boxFill.getArgb(), Renderer3D.DepthMode.NONE);
        renderer.outlineBox(box, boxLine.getArgb(), 2.0f, Renderer3D.DepthMode.NONE);
        if (finished) return;

        if (cursor.layer < layers) {
            int y = layerY(cursor.layer);
            AABB slab = new AABB(minX, y, minZ, maxX + 1, y + 1, maxZ + 1).inflate(0.006);
            renderer.filledBox(slab, layerFill.getArgb(), Renderer3D.DepthMode.NONE);
        }
        List<BlockPos> targets = activeTargets;
        for (int i = 0; i < targets.size(); i++) {
            AABB cell = new AABB(targets.get(i)).inflate(0.004);
            renderer.filledBox(cell, targetFill.getArgb(), Renderer3D.DepthMode.NONE);
            renderer.outlineBox(cell, targetLine.getArgb(), 2.0f, Renderer3D.DepthMode.NONE);
        }
        if (renderSkipped.get() && !skipped.isEmpty()) renderSkippedCells(renderer);
    }

    private void renderSkippedCells(Renderer3D renderer) {
        Vec3 eyes = mc.player.getEyePosition();
        int drawn = 0;
        for (long packed : skipped) {
            if (drawn >= MAX_SKIPPED_RENDERED) break;
            BlockPos pos = BlockPos.of(packed);
            if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > SKIPPED_RENDER_DISTANCE_SQ) continue;
            AABB cell = new AABB(pos).deflate(0.15);
            if (!Renderer3D.Culling.isInFrustum(cell)) continue;
            renderer.outlineBox(cell, skippedLine.getArgb(), 1.5f, Renderer3D.DepthMode.NONE);
            drawn++;
        }
    }
}
