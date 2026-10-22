/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.BreakBlockAction;
import combatant.client.automation.actions.MoveAction;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.anticheat.AntiCheatPresetDefaults;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import combatant.client.util.block.interaction.BlockInteractionPipeline.RotationPlan;
import combatant.client.util.finder.FinderAlerts;
import combatant.client.util.schematic.walk.LevelWalkTerrain;
import combatant.client.util.schematic.walk.TunnelPlanner;
import combatant.client.util.schematic.walk.TunnelPlanner.Hazard;
import combatant.client.util.schematic.walk.TunnelPlanner.Step;
import combatant.client.util.schematic.walk.WalkTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Digs a straight 1x2 tunnel in one compass direction through the shared automation runtime (BreakBlockAction for
 * each cell, MoveAction into the cleared column). Before every step it checks the next column on the live world:
 * lava, water that would flood the tunnel, a missing floor or the void pause the dig and the module resumes by
 * itself when the way is clear. A throttled scan of the loaded chunks' block entities alerts you to chests, barrels,
 * shulker boxes and spawners near the tunnel. Disabling the module or changing world cancels the dig.
 */
@ModuleInfo(
        id = "tunnelbasefinder",
        displayName = "TunnelBaseFinder",
        aliases = {"TunnelFinder", "BaseTunneler"},
        category = ModuleCategory.PLAYER,
        subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.tunnelbasefinder.description")
public final class TunnelBaseFinder extends Module {

    private static final int RECHECK_TICKS = 10;
    private static final int MAX_FAILED_STEPS = 5;
    private static final int LOOK_AHEAD = 4;
    private static final String HAZARD_KEY = "tunnelbasefinder.hazard";

    public enum Direction {
        FACING,
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final LevelWalkTerrain levelTerrain = new LevelWalkTerrain();
    private final WalkTerrain terrain = levelTerrain::cell;

    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.VANILLA);
    private final EnumValue<Direction> direction =
            enumSetting("tunnelDirection", "direction", Direction.FACING, Direction.values());
    private final NumberValue<Integer> maxLength = num("tunnelMaxLength", "max_length", 0, 0, 10000);
    private final NumberValue<Double> range = num("tunnelRange", "range", 4.5D, 1.0D, 6.0D);
    private final BooleanValue rotate = bool("tunnelRotate", "rotate", true);
    {
        AntiCheatPresetDefaults.bind(anticheat)
                .cap(range, AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE)
                .set(rotate, true, false);
    }
    private final NumberValue<Integer> maxDrop = num("tunnelMaxDrop", "max_drop", 1, 0, 4);
    private final BooleanValue scanStorage = bool("tunnelScanStorage", "scan_storage", true);
    private final NumberValue<Integer> scanInterval = num("tunnelScanInterval", "scan_interval", 40, 10, 200);
    private final NumberValue<Integer> scanRadius = num("tunnelScanRadius", "scan_radius", 48, 8, 128);
    private final BooleanValue findContainers = bool("tunnelFindContainers", "find_containers", true);
    private final BooleanValue findSpawners = bool("tunnelFindSpawners", "find_spawners", true);
    private final BooleanValue findEnderChests = bool("tunnelFindEnderChests", "find_ender_chests", false);
    private final BooleanValue alertChat = bool("tunnelAlertChat", "alert_chat", true);
    private final BooleanValue alertSound = bool("tunnelAlertSound", "alert_sound", true);
    private final BooleanValue stopOnFind = bool("tunnelStopOnFind", "stop_on_find", false);

    private ClientLevel level;
    private AutomationTask task;
    private int cardinal;
    private int dug;
    private int failedSteps;
    private int recheckIn;
    private boolean paused;
    private long ticks;
    private final Set<Long> announced = new HashSet<>();

    @Override
    public void onEnable() {
        level = mc.level;
        task = null;
        dug = 0;
        failedSteps = 0;
        recheckIn = 0;
        paused = false;
        ticks = 0;
        announced.clear();
        LocalPlayer player = mc.player;
        cardinal = switch (direction.get()) {
            case NORTH -> 2;
            case SOUTH -> 0;
            case EAST -> 3;
            case WEST -> 1;
            case FACING -> player == null ? 0 : TunnelPlanner.cardinalFromYaw(player.getYRot());
        };
    }

    @Override
    public void onDisable() {
        task = null;
        paused = false;
        announced.clear();
        Notifier.clear(HAZARD_KEY);
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        if (isEnabled()) stop("notification.tunnelbasefinder.stopped_world", false);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level != level) {
            stop("notification.tunnelbasefinder.stopped_world", false);
            return;
        }
        ticks++;
        if (scanStorage.get() && ticks % scanInterval.get() == 0 && scanForFinds(player)) return;
        if (task != null) return;
        if (maxLength.get() > 0 && dug >= maxLength.get()) {
            stop("notification.tunnelbasefinder.finished", true);
            return;
        }
        if (recheckIn > 0) {
            recheckIn--;
            return;
        }
        planNext(player);
    }

    private void planNext(LocalPlayer player) {
        int dx = TunnelPlanner.dirX(cardinal);
        int dz = TunnelPlanner.dirZ(cardinal);
        Step step = TunnelPlanner.plan(terrain, player.getBlockX(), player.getBlockY(), player.getBlockZ(),
                dx, dz, maxDrop.get(), LOOK_AHEAD);
        if (!step.safe()) {
            if (!paused) paused = true;
            Notifier.update(HAZARD_KEY, I18n.get("notification.tunnelbasefinder.hazard_" + step.hazard().name()
                    .toLowerCase(java.util.Locale.ROOT)), Notifier.Type.WARNING);
            recheckIn = RECHECK_TICKS;
            return;
        }
        if (paused) {
            paused = false;
            Notifier.clear(HAZARD_KEY);
        }

        RotationPlan rotation = rotate.get()
                ? new RotationPlan(true, List.of(), 2, 4.0f, false, MovementCorrection.SILENT, ActionPriority.UTILITY)
                : RotationPlan.disabled();
        MiningPlan plan = new MiningPlan(range.get(), 0.0, true, true, 5, rotation, false, false, false);

        List<Action> steps = new ArrayList<>(step.dig().length + 1);
        for (long cell : step.dig()) {
            BlockPos pos = new BlockPos(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell),
                    WalkTerrain.unpackZ(cell));
            steps.add(new BreakBlockAction(pos, null, plan, 2, 200, 40));
        }
        Vec3 center = new Vec3(step.x() + 0.5, player.getY(), step.z() + 0.5);
        steps.add(new MoveAction(center, MoveAction.Settings.DEFAULT.withArrivalRadius(0.25).withMaxStallTicks(30)));

        task = AutomationRuntime.INSTANCE.submit(AutomationTask
                .builder(this, "TunnelBaseFinder", Actions.sequence("TunnelStep", steps.toArray(new Action[0])))
                .priority(ActionPriority.UTILITY)
                .onFinish(this::onStepFinished)
                .build());
    }

    private void onStepFinished(AutomationTask finished) {
        if (finished != task) return;
        task = null;
        if (!isEnabled()) return;
        if (finished.result().isSuccess()) {
            dug++;
            failedSteps = 0;
            return;
        }
        if (++failedSteps >= MAX_FAILED_STEPS) {
            stop("notification.tunnelbasefinder.stuck", true);
            return;
        }
        recheckIn = RECHECK_TICKS;
    }

    /** @return true when the module was switched off because of a find. */
    private boolean scanForFinds(LocalPlayer player) {
        ClientLevel world = mc.level;
        int radius = scanRadius.get();
        double radiusSq = (double) radius * radius;
        int minCx = (player.getBlockX() - radius) >> 4;
        int maxCx = (player.getBlockX() + radius) >> 4;
        int minCz = (player.getBlockZ() - radius) >> 4;
        int maxCz = (player.getBlockZ() + radius) >> 4;
        boolean found = false;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!world.hasChunk(cx, cz)) continue;
                LevelChunk chunk = world.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.distSqr(player.blockPosition()) > radiusSq) continue;
                    String what = describe(entry.getValue());
                    if (what == null || !announced.add(pos.asLong())) continue;
                    FinderAlerts.found("TunnelBaseFinder", what, pos.getX(), pos.getY(), pos.getZ(),
                            alertChat.get(), alertSound.get());
                    found = true;
                }
            }
        }
        if (found && stopOnFind.get()) {
            stop("notification.tunnelbasefinder.found", true);
            return true;
        }
        return false;
    }

    private String describe(BlockEntity entity) {
        if (findSpawners.get() && entity instanceof SpawnerBlockEntity) return "Spawner";
        if (findContainers.get()) {
            if (entity instanceof ChestBlockEntity) return "Chest";
            if (entity instanceof BarrelBlockEntity) return "Barrel";
            if (entity instanceof ShulkerBoxBlockEntity) return "Shulker box";
        }
        if (findEnderChests.get() && entity instanceof EnderChestBlockEntity) return "Ender chest";
        return null;
    }

    private void stop(String messageKey, boolean success) {
        String text = I18n.get(messageKey, dug);
        if (success) Notifier.success(text);
        else Notifier.warning(text);
        AutomationRuntime.INSTANCE.cancelOwner(this, "tunnel stopped");
        setEnabled(false);
    }
}
