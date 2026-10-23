/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.BreakBlockAction;
import combatant.client.automation.actions.InteractBlockAction;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.config.common.impl.TargetFilters;
import combatant.client.config.values.BooleanMapValue;
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
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import combatant.client.util.block.interaction.BlockInteractionPipeline.RotationPlan;
import combatant.client.util.combat.ExplosionDamageRules;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Opens an enemy's surround. In every hole the module looks at the four blocks around the target and keeps the ones
 * where a crystal could be set on the obsidian below once the block is gone, scores each by the blast the target would
 * take (through the opened cell) against what the blast would cost you, and mines the best one with the shared
 * {@link BreakBlockAction}. Optionally a crystal is placed into the opening afterwards; detonating it is left to
 * {@link AutoCrystal}, which sees the exposed target on its own.
 */
@ModuleInfo(
        id = "autocity",
        displayName = "AutoCity",
        aliases = {"CityMiner", "SurroundBreaker", "AutoBreakSurround"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.autocity.description")
public final class AutoCity extends Module {

    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int BREAK_CONFIRM_TICKS = 1;
    private static final int BREAK_TIMEOUT_TICKS = 600;
    private static final int BREAK_STALL_TICKS = 40;
    private static final int TOOL_LEASE_TICKS = 5;
    private static final float SELF_DAMAGE_WEIGHT = 0.5F;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range = num("autoCityRange", "range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> enemyRange = num("autoCityEnemyRange", "enemy_range", 6.0D, 2.0D, 10.0D);
    private final BooleanMapValue targetFilters = groupCommon(
            "autoCityTargets", "targets", "target", targetDefaults());
    private final BooleanValue onlyInHole = bool("autoCityOnlyInHole", "only_in_hole", true);
    private final NumberValue<Float> minDamage = num("autoCityMinDamage", "min_damage", 6.0F, 0.0F, 36.0F);
    private final NumberValue<Float> maxSelfDamage = num("autoCityMaxSelfDamage", "max_self_damage", 6.0F, 0.0F, 36.0F);
    private final BooleanValue placeCrystal = bool("autoCityPlaceCrystal", "place_crystal", false);
    private final BooleanValue autoTool = bool("autoCityAutoTool", "auto_tool", true);
    private final BooleanValue autoDisable = bool("autoCityAutoDisable", "auto_disable", true);
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "autoCityRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "autoCityMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final BooleanValue render = bool("autoCityRender", "render", true);
    private final RGBAColorValue fillColor = color("autoCityFillColor", "fill_color", "#2AFF5A5A");
    private final RGBAColorValue lineColor = color("autoCityLineColor", "line_color", "#FFFF5A5A");
    private final NumberValue<Float> lineWidth = num("autoCityLineWidth", "line_width", 2.0F, 1.0F, 6.0F);

    private AutomationTask task;
    private Candidate current;
    private boolean opened;
    private int scanTicker;

    @Override
    public void onEnable() {
        scanTicker = SCAN_INTERVAL_TICKS;
        clearCurrent();
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "autocity disabled");
        clearCurrent();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.isDeadOrDying()) {
            setEnabled(false);
            return;
        }

        if (task != null) {
            if (!task.isDone()) {
                if (!stillValid(level)) {
                    AutomationRuntime.INSTANCE.cancelOwner(this, "target changed");
                    clearCurrent();
                }
                return;
            }
            finish();
            if (!isEnabled()) return;
        }

        if (ClientScreen.current() != null || ++scanTicker < SCAN_INTERVAL_TICKS) return;
        scanTicker = 0;

        LivingEntity target = findTarget();
        if (!(target instanceof Player enemy)) return;
        Candidate best = pick(player, level, enemy);
        if (best != null) submit(best);
    }

    /** The target still stands where the plan was made and the block is still in the way. */
    private boolean stillValid(ClientLevel level) {
        Candidate candidate = current;
        if (candidate == null) return false;
        Player enemy = candidate.target();
        if (!enemy.isAlive() || !enemy.blockPosition().equals(candidate.feet())) return false;
        // Once it is open the sequence may only be placing the crystal; a dead block is no reason to stop.
        return opened || !level.getBlockState(candidate.block()).canBeReplaced();
    }

    private void finish() {
        AutomationTask finished = task;
        boolean wasOpened = opened;
        Candidate candidate = current;
        clearCurrent();
        if (candidate == null) return;

        if (wasOpened) {
            Notifier.success(I18n.get("notification.autocity.opened", candidate.target().getName().getString()));
            if (finished.result().isFailure()) Notifier.warning(I18n.get("notification.autocity.crystal_failed"));
            if (autoDisable.get()) setEnabled(false);
        } else if (finished.result().isFailure()) {
            Notifier.warning(I18n.get("notification.autocity.failed"));
        }
    }

    private Candidate pick(LocalPlayer player, ClientLevel level, Player enemy) {
        if (!enemy.onGround() && onlyInHole.get()) return null;
        BlockPos feet = enemy.blockPosition();

        if (onlyInHole.get()) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(feet.relative(side)).canBeReplaced()) return null;
            }
        }

        Vec3 eye = player.getEyePosition();
        double reachSq = range.get() * range.get();
        Candidate best = null;
        float bestScore = 0.0F;

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos block = feet.relative(side);
            BlockPos base = block.below();
            BlockState state = level.getBlockState(block);
            if (!BlockInteractionPipeline.isMineable(level, block, state)) continue;
            if (!isCrystalBase(level.getBlockState(base))) continue;
            if (!level.getBlockState(block.above()).canBeReplaced()) continue;
            if (eye.distanceToSqr(Vec3.atCenterOf(block)) > reachSq) continue;
            if (!player.isWithinBlockInteractionRange(block, 0.0)) continue;

            Vec3 blast = new Vec3(block.getX() + 0.5D, base.getY() + 1.0D, block.getZ() + 0.5D);
            float damage = ExplosionDamageUtil.getExplosionDamageThroughClearedCell(enemy, blast, 6.0F, 0, block);
            float selfDamage = ExplosionDamageUtil.getExplosionDamageThroughClearedCell(player, blast, 6.0F, 0, block);
            if (damage < minDamage.get()) continue;
            if (selfDamage > maxSelfDamage.get() || !ExplosionDamageRules.isSafe(player, selfDamage, false)) continue;

            float score = damage - SELF_DAMAGE_WEIGHT * selfDamage;
            if (best == null || score > bestScore) {
                best = new Candidate(enemy, feet, block, base);
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isCrystalBase(BlockState state) {
        return state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
    }

    private void submit(Candidate candidate) {
        opened = false;
        current = candidate;

        List<Action> steps = new ArrayList<>(3);
        steps.add(new BreakBlockAction(candidate.block(), null, miningPlan(),
                BREAK_CONFIRM_TICKS, BREAK_TIMEOUT_TICKS, BREAK_STALL_TICKS));
        steps.add(Actions.run("AutoCityOpened", () -> opened = true));
        if (placeCrystal.get()) {
            steps.add(Actions.defer("AutoCityCrystal", () -> crystalStep(candidate)));
        }

        task = AutomationRuntime.INSTANCE.submit(AutomationTask
                .builder(this, "AutoCity", Actions.sequence("AutoCity", steps.toArray(new Action[0])))
                .priority(ActionPriority.COMBAT)
                .build());
    }

    /** The crystal click, or null (skip the step) when the cell is not free or already holds a crystal. */
    private Action crystalStep(Candidate candidate) {
        ClientLevel level = mc.level;
        if (level == null || !level.getBlockState(candidate.block()).canBeReplaced()) return null;
        if (crystalAt(level, candidate.block())) return null;

        InteractBlockAction.Settings settings = new InteractBlockAction.Settings(
                range.get(), range.get(), rotation.get(), movementCorrection.get(), 2, true, 10);
        return new InteractBlockAction(this, "AutoCityCrystal", candidate.base(),
                player -> PlacementSlot.find(player, stack -> stack.is(Items.END_CRYSTAL)), settings,
                () -> mc.level != null && crystalAt(mc.level, candidate.block()));
    }

    private static boolean crystalAt(ClientLevel level, BlockPos cell) {
        return !level.getEntitiesOfClass(EndCrystal.class, new AABB(cell).inflate(0.1D)).isEmpty();
    }

    private MiningPlan miningPlan() {
        boolean rotate = rotation.get() == PlacementRotationMode.NORMAL;
        RotationPlan rotationPlan = rotate
                ? new RotationPlan(true, List.of(), 2, 4.0f, false, movementCorrection.get(), ActionPriority.COMBAT)
                : RotationPlan.disabled();
        return new MiningPlan(
                range.get(), 0.0D, true, autoTool.get(), TOOL_LEASE_TICKS, rotationPlan,
                false, false, autoTool.get());
    }

    private LivingEntity findTarget() {
        return TargetingUtil.findBestTarget(mc, new TargetingUtil.TargetingSettings(
                enemyRange.get(),
                180.0F,
                true,
                targetFilters.get(TargetFilters.IGNORE_FRIENDS),
                targetFilters.get(TargetFilters.IGNORE_STAFF),
                targetFilters.get(TargetFilters.IGNORE_ENEMIES),
                targetFilters.get(TargetFilters.IGNORE_NAKED),
                true,
                targetFilters.get(TargetFilters.VISIBLE_ONLY),
                TargetingUtil.TargetPriority.DISTANCE));
    }

    private void clearCurrent() {
        task = null;
        current = null;
        opened = false;
    }

    private static LinkedHashMap<String, Boolean> targetDefaults() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TargetFilters.IGNORE_FRIENDS, true);
        defaults.put(TargetFilters.IGNORE_STAFF, true);
        defaults.put(TargetFilters.IGNORE_ENEMIES, false);
        defaults.put(TargetFilters.IGNORE_NAKED, false);
        defaults.put(TargetFilters.VISIBLE_ONLY, false);
        return defaults;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        Candidate candidate = current;
        if (!isEnabled() || !render.get() || candidate == null) return;
        AABB box = new AABB(candidate.block());
        if (!Renderer3D.Culling.isInFrustum(box)) return;
        renderer.filledBox(box, fillColor.getArgb());
        renderer.outlineBox(box, lineColor.getArgb(), lineWidth.get());
    }

    private record Candidate(Player target, BlockPos feet, BlockPos block, BlockPos base) {
    }
}
