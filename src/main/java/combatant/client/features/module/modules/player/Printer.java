/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.automation.ActionPriority;
import combatant.client.automation.ActionResource;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.InventoryAction;
import combatant.client.automation.actions.NavigateAction;
import combatant.client.automation.actions.PlaceBlockAction;
import combatant.client.automation.compat.litematica.LitematicaCompat;
import combatant.client.automation.compat.litematica.SchematicScanner;
import combatant.client.automation.compat.litematica.SchematicSource;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.automation.placement.PlacementBackoff;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.automation.placement.PlacementTarget;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.modules.player.printer.PlacementOrientation;
import combatant.client.features.module.modules.player.printer.PlacementOrientation.Requirement;
import combatant.client.features.module.modules.player.printer.PrinterClickPlanner;
import combatant.client.features.module.modules.player.printer.PrinterTargets;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.helpers.PlacementPreviewRenderer;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.scaffold.ScaffoldPlacementTarget;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapPolicy;
import combatant.client.util.schematic.StateAdjustments;
import combatant.client.util.schematic.StateComparison;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the schematics of the Litematica mod (optional): reads its placements, transformed schematic world and layer
 * range through an isolated adapter, finds the blocks that are missing in reach and places them through the shared
 * automation runtime (PlaceBlockAction, optional NavigateAction, InventoryAction restock). Directional blocks are
 * clicked on a face and with a look that reproduce their state; blocks placement cannot produce are skipped and
 * reported, rejected cells back off. Without Litematica the module tells so and switches itself off.
 */
@ModuleInfo(
        id = "printer",
        displayName = "Printer",
        aliases = {"LitematicaPrinter", "SchematicPrinter"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.printer.description")
public final class Printer extends Module {

    private static final int PLANS_PER_SCAN = 10;
    private static final int CHECK_BUDGET = 6000;
    private static final int MAX_MISSING = 96;
    private static final int PREVIEW_CELLS = 8;
    private static final long SKIP_REPORT_TICKS = 200L;

    private record Key(Item item, boolean rotate) {
    }

    private record Planned(BlockPos pos, BlockState state, PlacementTarget target, Key key) {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private final NumberValue<Double> range = num("printerRange", "range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = num("printerWallRange", "wall_range", 0.0D, 0.0D, 6.0D);
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "printerRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "printerMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Integer> blocksPerTick = num("printerBlocksPerTick", "blocks_per_tick", 1, 1, 6);
    private final NumberValue<Integer> delay = num("printerDelay", "delay", 1, 0, 20);
    private final NumberValue<Integer> swapBackDelay = num("printerSwapBackDelay", "swap_back_delay", 2, 0, 10);
    private final NumberValue<Integer> scanInterval = num("printerScanInterval", "scan_interval", 4, 1, 40);
    private final BooleanValue navigate = bool("printerNavigate", "navigate", false);
    private final NumberValue<Integer> searchRadius = num("printerSearchRadius", "search_radius", 16, 6, 32);
    private final BooleanValue restock = bool("printerRestock", "restock", true);
    private final BooleanValue render = bool("printerRender", "render", true);
    private final RGBAColorValue fillColor = color("printerFillColor", "fill_color", "#2A9C5A44");
    private final RGBAColorValue lineColor = color("printerLineColor", "line_color", "#55FFAAFF");
    private final NumberValue<Float> lineWidth = num("printerLineWidth", "line_width", 2.0F, 1.0F, 6.0F);

    private final PlacementBackoff<Long> backoff = new PlacementBackoff<>(3, 100, 200);
    private final Map<BlockState, Requirement> requirements = new HashMap<>();
    private final List<Preview> preview = new ArrayList<>();

    private record Preview(BlockPos pos, BlockState state) {
    }

    private SchematicSource source;
    private SchematicScanner scanner;
    private ClientLevel lastLevel;
    private AutomationTask task;
    private long ticks;
    private long lastScan;
    private long nextBatch;
    private long lastSkipReport;
    private int skippedLast;
    private int skippedCurrent;
    private int wrongLast;
    private int missingLast;

    @Override
    public void onEnable() {
        source = LitematicaCompat.source();
        if (source == null || mc.player == null || mc.level == null) {
            if (source == null) Notifier.warning(I18n.get("notification.printer.no_litematica"));
            setEnabled(false);
            return;
        }
        scanner = new SchematicScanner(source);
        resetState();
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "printer disabled");
        InventorySwap.INSTANCE.releaseHotbar(this);
        resetState();
        scanner = null;
        source = null;
    }

    private void resetState() {
        backoff.clear();
        requirements.clear();
        preview.clear();
        if (scanner != null) scanner.clear();
        task = null;
        ticks = 0L;
        lastScan = Long.MIN_VALUE / 2;
        nextBatch = 0L;
        lastSkipReport = 0L;
        skippedLast = 0;
        wrongLast = 0;
        missingLast = 0;
        lastLevel = mc.level;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || scanner == null) {
            setEnabled(false);
            return;
        }
        if (level != lastLevel) {
            scanner.clear();
            backoff.clear();
            preview.clear();
            lastLevel = level;
        }
        ticks++;
        try {
            if (!source.isReady()) {
                preview.clear();
                return;
            }
            if (task != null || ticks < nextBatch || ticks - lastScan < scanInterval.get()) return;
            lastScan = ticks;
            step(player, level);
        } catch (LinkageError e) {
            Notifier.warning(I18n.get("notification.printer.incompatible"));
            setEnabled(false);
        }
    }

    private void step(LocalPlayer player, ClientLevel level) {
        double reach = Math.max(range.get(), wallRange.get());
        double radius = navigate.get() ? Math.max(searchRadius.get(), reach + 1.0) : reach + 1.0;
        var eyes = player.getEyePosition();
        skippedCurrent = 0;
        SchematicScanner.Result result = scanner.scan(this::judge, eyes.x, eyes.y, eyes.z, radius,
                CHECK_BUDGET, MAX_MISSING, ticks);
        skippedLast = skippedCurrent;
        wrongLast = result.wrong();
        missingLast = result.missing().size();
        reportSkipped();

        List<SchematicScanner.Cell> cells = new ArrayList<>(result.missing());
        PrinterTargets.order(cells, SchematicScanner.Cell::pos, eyes.x, eyes.y, eyes.z);
        preview.clear();
        for (int i = 0; i < Math.min(PREVIEW_CELLS, cells.size()); i++) {
            preview.add(new Preview(cells.get(i).pos(), cells.get(i).expected()));
        }
        if (cells.isEmpty()) return;

        List<Planned> planned = new ArrayList<>();
        Item missingItem = null;
        SchematicScanner.Cell nearestOutOfReach = null;
        int plans = 0;
        for (SchematicScanner.Cell cell : cells) {
            if (plans >= PLANS_PER_SCAN) break;
            if (TargetsInReach.farther(cell.pos(), eyes.x, eyes.y, eyes.z, reach)) {
                if (nearestOutOfReach == null) nearestOutOfReach = cell;
                continue;
            }
            plans++;
            Item item = cell.expected().getBlock().asItem();
            ItemStack stack = new ItemStack(item);
            PlacementSlot slot = PlacementSlot.find(player, s -> ItemStack.isSameItem(s, stack));
            if (slot == null) {
                if (missingItem == null) missingItem = item;
                continue;
            }
            Planned plan = plan(player, level, cell, slot, stack);
            if (plan != null) planned.add(plan);
            else if (nearestOutOfReach == null) nearestOutOfReach = cell;
        }

        if (!planned.isEmpty()) {
            submitBatch(planned);
        } else if (missingItem != null && restock.get() && restock(player, missingItem)) {
            return;
        } else if (nearestOutOfReach != null && navigate.get()) {
            navigateTo(nearestOutOfReach.pos());
        }
    }

    /** Verdict for one expected cell against the client world. */
    private SchematicScanner.Verdict judge(BlockState expected, int x, int y, int z) {
        ClientLevel level = mc.level;
        if (level == null || !level.getChunkSource().hasChunk(x >> 4, z >> 4)) return null;
        BlockState actual = level.getBlockState(cursor.set(x, y, z));
        if (StateComparison.matches(actual, expected)
                || actual.getBlock() == expected.getBlock()
                && StateAdjustments.clicksNeeded(actual, expected) != StateAdjustments.IMPOSSIBLE) {
            return SchematicScanner.Verdict.OK;
        }
        if (!actual.canBeReplaced()) return SchematicScanner.Verdict.WRONG;
        Requirement need = requirement(expected);
        if (!need.placeable()) {
            skippedCurrent++;
            return SchematicScanner.Verdict.OK;
        }
        if (backoff.isBackedOff(cursor.asLong(), ticks)) return SchematicScanner.Verdict.OK;
        return SchematicScanner.Verdict.MISSING;
    }

    private Requirement requirement(BlockState state) {
        return requirements.computeIfAbsent(state, PlacementOrientation::analyze);
    }

    private Planned plan(LocalPlayer player, ClientLevel level, SchematicScanner.Cell cell, PlacementSlot slot,
                         ItemStack stack) {
        BlockPos pos = cell.pos();
        BlockState wanted = cell.expected();
        Requirement need = requirement(wanted);
        boolean forcedRotation = need.rotationDecides();
        boolean aim = forcedRotation || rotation.get() == PlacementRotationMode.NORMAL;
        ScaffoldPlacementTarget click = PrinterClickPlanner.find(player, level, pos, wanted, stack, need, aim,
                range.get());
        if (click == null) return null;
        boolean constrained = forcedRotation || need.half() != null || need.clickFace() != null
                || need.clickAxis() != null;
        PlacementTarget.ClickCheck check = constrained
                ? (p, rot, hit) -> PrinterClickPlanner.accepts(p, level, stack, pos, wanted, rot, hit)
                : null;
        PlacementTarget target = new PlacementTarget(pos, click, slot, constrained, check);
        return new Planned(pos, wanted, target, new Key(stack.getItem(), forcedRotation));
    }

    private void submitBatch(List<Planned> planned) {
        List<Planned> batch = PrinterTargets.batch(planned, Planned::key, blocksPerTick.get());
        Key key = batch.get(0).key();
        List<PlacementTarget> targets = new ArrayList<>();
        for (Planned p : batch) targets.add(p.target());
        PlaceBlockAction action = new PlaceBlockAction(this, targets, constraints(key.rotate()), blocksPerTick.get());
        submit("Printer", action, finished -> onBatchFinished(action, batch));
    }

    private void onBatchFinished(PlaceBlockAction action, List<Planned> batch) {
        task = null;
        nextBatch = ticks + delay.get();
        ClientLevel level = mc.level;
        for (Planned p : batch) {
            PlaceBlockAction.Outcome outcome = action.outcomeOf(p.pos());
            long key = p.pos().asLong();
            if (outcome == null || outcome == PlaceBlockAction.Outcome.SKIPPED) continue;
            boolean built = outcome == PlaceBlockAction.Outcome.CONFIRMED && level != null
                    && judgeNow(level, p.state(), p.pos());
            if (built) backoff.recordSuccess(key);
            else backoff.recordFailure(key, ticks);
        }
    }

    private boolean judgeNow(ClientLevel level, BlockState wanted, BlockPos pos) {
        BlockState actual = level.getBlockState(pos);
        return StateComparison.matches(actual, wanted) || actual.getBlock() == wanted.getBlock()
                && StateAdjustments.clicksNeeded(actual, wanted) != StateAdjustments.IMPOSSIBLE;
    }

    private boolean restock(LocalPlayer player, Item item) {
        int from = -1;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                from = slot;
                break;
            }
        }
        if (from < 0) return false;
        int to = player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) {
                to = slot;
                break;
            }
        }
        submit("PrinterRestock", InventoryAction.moveToHotbar(from, to, InventorySwapPolicy.LEGIT, 60),
                finished -> {
                    task = null;
                    nextBatch = ticks + delay.get();
                });
        return true;
    }

    private void navigateTo(BlockPos pos) {
        NavigateAction walk = new NavigateAction(
                NavigationRequest.reach(pos.getX(), pos.getY(), pos.getZ(), Math.max(1.5, range.get() - 0.75)));
        submit("PrinterNavigate", walk, finished -> {
            task = null;
            nextBatch = ticks + delay.get();
            if (!finished.result().isSuccess()) backoff.recordFailure(pos.asLong(), ticks);
        });
    }

    private void submit(String name, combatant.client.automation.Action root,
                        java.util.function.Consumer<AutomationTask> onFinish) {
        task = AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, name, root)
                .priority(ActionPriority.BUILD)
                .yieldToManualInput(EnumSet.of(ActionResource.ROTATION, ActionResource.MOVEMENT,
                        ActionResource.JUMP))
                .onFinish(onFinish)
                .build());
    }

    private void reportSkipped() {
        if (skippedLast == 0 || ticks - lastSkipReport < SKIP_REPORT_TICKS) return;
        lastSkipReport = ticks;
        Notifier.info(I18n.get("notification.printer.skipped", skippedLast));
    }

    /** Blocks whose state follows the look always rotate, whatever the rotation setting says. */
    private PlacementConstraints constraints(boolean forceRotate) {
        PlacementRotationMode mode = forceRotate ? PlacementRotationMode.NORMAL : rotation.get();
        return new PlacementConstraints(range.get(), wallRange.get(), mode, movementCorrection.get(),
                mode == PlacementRotationMode.NO_ROTATION, false, 1, swapBackDelay.get(), swapBackDelay.get(), true, 0);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        ClientLevel level = mc.level;
        if (!isEnabled() || !render.get() || level == null || preview.isEmpty()) return;
        int ghostTint = (Math.min(170, Math.round((fillColor.getArgb() >>> 24) * 2.15f)) << 24)
                | (fillColor.getArgb() & 0x00FFFFFF);
        for (Preview cell : preview) {
            if (!level.getBlockState(cell.pos()).canBeReplaced()) continue;
            PlacementPreviewRenderer.renderModel(renderer, cell.state(), cell.pos(), ghostTint);
            for (AABB shape : cell.state().getShape(level, cell.pos()).toAabbs()) {
                renderer.outlineBox(shape.move(cell.pos()), lineColor.getArgb(), Math.max(0.7f, lineWidth.get() * 0.7f),
                        Renderer3D.DepthMode.MAIN);
            }
        }
    }

    /** Distance test kept out of the loop body for readability. */
    private static final class TargetsInReach {
        static boolean farther(BlockPos pos, double x, double y, double z, double reach) {
            return PrinterTargets.distanceSq(pos, x, y, z) > (reach + 1.0) * (reach + 1.0);
        }
    }
}
