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
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.NukerTargeting;
import combatant.client.util.block.NukerTargeting.Candidate;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import combatant.client.util.block.interaction.BlockInteractionPipeline.RotationPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Breaks the blocks around the player one after another through {@link BreakBlockAction} in an
 * {@link AutomationTask}. The cube around the player is scanned on an interval (never while a batch is still
 * running), the candidates are filtered and ordered by {@link NukerTargeting}, and the best few become the next batch.
 */
@ModuleInfo(
        id = "nuker",
        displayName = "Nuker",
        aliases = {"AutoBreak", "AreaMiner"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.nuker.description")
public final class Nuker extends Module {

    private static final int FAILURE_COOLDOWN_TICKS = 60;
    private static final int BREAK_CONFIRM_TICKS = 1;
    private static final int BREAK_TIMEOUT_TICKS = 400;
    private static final int BREAK_STALL_TICKS = 20;
    private static final int TOOL_LEASE_TICKS = 5;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("nukerRange", "range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = num("nukerWallRange", "wall_range", 0.0D, 0.0D, 6.0D);
    private final EnumValue<NukerTargeting.Shape> shape = enumSetting(
            "nukerShape", "shape", NukerTargeting.Shape.ALL, NukerTargeting.Shape.values());
    private final EnumValue<NukerTargeting.Sort> sort = enumSetting(
            "nukerSort", "sort", NukerTargeting.Sort.CLOSEST, NukerTargeting.Sort.values());
    private final EnumValue<NukerTargeting.FilterMode> filterMode = enumSetting(
            "nukerFilterMode", "filter_mode", NukerTargeting.FilterMode.OFF, NukerTargeting.FilterMode.values());
    private final ItemIdSetValue filterBlocks = visibleWhen(
            itemList("nukerFilterBlocks", "filter_blocks", TextListSetting.PickerMode.BLOCKS),
            () -> filterMode.get() != NukerTargeting.FilterMode.OFF);
    private final NumberValue<Integer> maxTargets = num("nukerMaxTargets", "max_targets", 1, 1, 16);
    private final NumberValue<Integer> breakDelay = num("nukerBreakDelay", "break_delay", 0, 0, 20);
    private final NumberValue<Integer> scanInterval = num("nukerScanInterval", "scan_interval_ticks", 5, 1, 40);
    private final BooleanValue onlyExposed = bool("nukerOnlyExposed", "only_exposed", true);
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "nukerRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final BooleanValue autoTool = bool("nukerAutoTool", "auto_tool", true);
    private final BooleanValue requireSuitableTool = visibleWhen(
            bool("nukerRequireSuitableTool", "require_suitable_tool", true), autoTool::get);
    private final BooleanValue swing = bool("nukerSwing", "swing", true);
    private final BooleanValue pauseInScreens = bool("nukerPauseInScreens", "pause_in_screens", true);

    private final Map<Long, Integer> failedUntil = new HashMap<>();
    private AutomationTask task;
    private int scanTicker;
    private int tickCounter;

    @Override
    public void onEnable() {
        scanTicker = 0;
        failedUntil.clear();
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "nuker disabled");
        task = null;
        failedUntil.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            AutomationRuntime.INSTANCE.cancelOwner(this, "no world");
            task = null;
            return;
        }
        tickCounter++;
        if (task != null && !task.isDone()) return;
        task = null;
        if (pauseInScreens.get() && combatant.client.util.screen.ClientScreen.current() != null) return;
        if (++scanTicker < scanInterval.get()) return;
        scanTicker = 0;

        List<Candidate> picked = scan(player, level);
        if (picked.isEmpty()) return;
        submitBatch(picked);
    }

    private List<Candidate> scan(LocalPlayer player, ClientLevel level) {
        double reach = range.get();
        Vec3 eyes = player.getEyePosition();
        BlockPos origin = BlockPos.containing(eyes);
        int radius = (int) Math.ceil(reach);
        int feetY = player.blockPosition().getY();
        NukerTargeting.Shape shapeMode = shape.get();
        boolean exposedOnly = onlyExposed.get();
        Set<String> ids = filterBlocks.get();
        NukerTargeting.FilterMode filter = filterMode.get();
        double reachSq = reach * reach;

        List<Candidate> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int y = origin.getY() + dy;
                if (!NukerTargeting.allowedByShape(shapeMode, feetY, y)) continue;
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    double distSq = eyes.distanceToSqr(x + 0.5, y + 0.5, z + 0.5);
                    if (distSq > reachSq) continue;
                    pos.set(x, y, z);
                    if (isCoolingDown(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (!BlockInteractionPipeline.isMineable(level, pos, state)) continue;
                    if (exposedOnly && !isExposed(level, pos, neighbour)) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (!NukerTargeting.allowedByFilter(filter, ids, id)) continue;
                    found.add(new Candidate(x, y, z, distSq, id));
                }
            }
        }
        if (found.isEmpty()) return found;
        return NukerTargeting.select(found, reachSq, shapeMode, feetY, filter, ids, sort.get(), effMaxTargets());
    }

    private boolean isCoolingDown(BlockPos pos) {
        if (failedUntil.isEmpty()) return false;
        Integer until = failedUntil.get(pos.asLong());
        if (until == null) return false;
        if (until <= tickCounter) {
            failedUntil.remove(pos.asLong());
            return false;
        }
        return true;
    }

    private static boolean isExposed(ClientLevel level, BlockPos pos, BlockPos.MutableBlockPos scratch) {
        for (Direction direction : Direction.values()) {
            scratch.setWithOffset(pos, direction);
            if (!level.getBlockState(scratch).canOcclude()) return true;
        }
        return false;
    }

    private void submitBatch(List<Candidate> picked) {
        MiningPlan plan = miningPlan();
        List<Action> steps = new ArrayList<>(picked.size() * 2);
        for (Candidate candidate : picked) {
            BlockPos target = new BlockPos(candidate.x(), candidate.y(), candidate.z());
            long key = target.asLong();
            Action breakBlock = new BreakBlockAction(
                    target, null, plan, BREAK_CONFIRM_TICKS, BREAK_TIMEOUT_TICKS, BREAK_STALL_TICKS);
            Action skip = Actions.step("NukerSkip", () -> {
                failedUntil.put(key, tickCounter + FAILURE_COOLDOWN_TICKS);
                return ActionResult.SUCCESS;
            });
            steps.add(Actions.selector("NukerBlock", breakBlock, skip));
            if (breakDelay.get() > 0) steps.add(Actions.waitTicks(breakDelay.get()));
        }
        task = AutomationRuntime.INSTANCE.submit(
                AutomationTask.builder(this, "Nuker", Actions.sequence("NukerBatch", steps.toArray(new Action[0])))
                        .priority(ActionPriority.BUILD)
                        .build());
    }

    private MiningPlan miningPlan() {
        boolean rotate = rotation.get() == PlacementRotationMode.NORMAL;
        RotationPlan rotationPlan = rotate
                ? new RotationPlan(true, List.of(), 2, 4.0f, false, MovementCorrection.SILENT, ActionPriority.BUILD)
                : RotationPlan.disabled();
        return new MiningPlan(
                range.get(), wallRange.get(), swing.get(), autoTool.get(), TOOL_LEASE_TICKS, rotationPlan,
                false, false, autoTool.get() && requireSuitableTool.get());
    }

    private int effMaxTargets() {
        return maxTargets.get();
    }
}
