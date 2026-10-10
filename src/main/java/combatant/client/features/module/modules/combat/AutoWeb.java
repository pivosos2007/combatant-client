/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
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
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.helpers.PlacementPreviewRenderer;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.target.TargetingUtil;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.CombatTargetProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@CombatTargetProvider(value = "autoweb", priority = 15)
@ModuleInfo(
        id = "autoweb",
        displayName = "AutoWeb",
        aliases = {"FastWeb", "WebEnemy"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.ATTACK,
        description = "module.autoweb.description")
public final class AutoWeb extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Double> range = numCommon(
            "autoWebRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5D, 1.0D, 6.0D);
    private final NumberValue<Double> wallRange = numCommon(
            "autoWebWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0D, 0.0D, 6.0D);
    private final NumberValue<Double> enemyRange = num("autoWebEnemyRange", "enemy_range", 5.0D, 2.0D, 10.0D);
    private final BooleanMapValue targetFilters = groupCommon(
            "autoWebTargets", "targets", "target", targetDefaults());
    private final NumberValue<Double> predictTicks = num("autoWebPredictTicks", "predict_ticks", 1.0D, 0.0D, 5.0D);
    private final BooleanValue placeAtHead = bool("autoWebPlaceAtHead", "place_at_head", true);
    private final BooleanValue autoDisable = bool("autoWebAutoDisable", "auto_disable", false);
    private final EnumValue<BlockPlacer.RotationMode> rotation = enumSetting(
            "autoWebRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());
    private final NumberValue<Integer> delay = num("autoWebDelay", "delay", 0, 0, 10);
    private final BooleanValue render = bool("autoWebRender", "render", true);
    private final RGBAColorValue fillColor = color("autoWebFillColor", "fill_color", "#FFFFFF44");
    private final RGBAColorValue lineColor = color("autoWebLineColor", "line_color", "#CCCCCCFF");
    private final NumberValue<Float> lineWidth = numCommon(
            "autoWebLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, this, 10,
            this::findPlacementSlot,
            range::get,
            wallRange::get,
            delay::get,
            delay::get,
            () -> 0,
            () -> 0,
            () -> 1,
            () -> false,
            () -> true,
            () -> false,
            () -> true,
            rotation::get,
            () -> MovementCorrection.SILENT
    );
    public enum State { IDLE, NO_WEBS, SEEKING, PLANNING, PLACING, CONFIRMING, COMPLETE }
    private State state = State.IDLE;
    private Player currentTarget;
    private boolean attemptedPlacement;
    private boolean placementConfirmed;
    private final List<BlockPos> requestedPositions = new ArrayList<>();
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private final PlacementPreviewRenderer placementPreview = new PlacementPreviewRenderer();

    @Override
    public void onEnable() {
        placementPreview.reset();
        state = State.IDLE;
        attemptedPlacement = false;
        placementConfirmed = false;
        requestedPositions.clear();
        blockPlacer.enable();
        updateTargets();
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        placementPreview.reset();
        currentTargets.clear();
        requestedPositions.clear();
        placementConfirmed = false;
        currentTarget = null;
        state = State.IDLE;
        TargetManager.clear(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        updateTargets();
        if (state == State.PLANNING || state == State.PLACING || state == State.CONFIRMING) {
            blockPlacer.tick();
        }
        if (render.get()) placementPreview.tick(mc.level, currentTargets, previewBlockState(), lineColor.getArgb());
        if (autoDisable.get() && attemptedPlacement && state == State.COMPLETE) setEnabled(false);
    }

    private void updateTargets() {
        if (mc.level == null || mc.player == null) {
            suspend(State.IDLE);
            return;
        }
        if (findPlacementSlot(BlockPos.ZERO) == null) {
            suspend(State.NO_WEBS);
            return;
        }
        Player target = findTarget();
        if (target == null) {
            suspend(State.SEEKING);
            return;
        }
        if (target != currentTarget) {
            attemptedPlacement = false;
            placementConfirmed = false;
            requestedPositions.clear();
        }
        if (attemptedPlacement && !placementConfirmed) {
            placementConfirmed = requestedPositions.stream()
                    .anyMatch(pos -> mc.level.getBlockState(pos).is(Blocks.COBWEB));
        }
        currentTarget = target;
        Vec3 velocity = target.getDeltaMovement();
        double ticks = predictTicks.get();
        BlockPos feet = BlockPos.containing(
                target.getX() + velocity.x * ticks,
                target.getY() + velocity.y * ticks,
                target.getZ() + velocity.z * ticks);
        List<BlockPos> needed = new ArrayList<>(2);
        if (mc.level.getBlockState(feet).canBeReplaced() && !mc.level.getBlockState(feet).is(Blocks.COBWEB)) {
            needed.add(feet);
        }
        BlockPos head = feet.above();
        if (placeAtHead.get() && mc.level.getBlockState(head).canBeReplaced()
                && !mc.level.getBlockState(head).is(Blocks.COBWEB)) {
            needed.add(head);
        }
        currentTargets.clear();
        currentTargets.addAll(needed);
        if (needed.isEmpty()) {
            blockPlacer.clear();
            state = placementConfirmed ? State.COMPLETE : State.SEEKING;
        } else {
            boolean reachable = needed.stream().anyMatch(pos ->
                    mc.player.position().distanceToSqr(Vec3.atCenterOf(pos)) <= range.get() * range.get());
            if (!reachable) {
                blockPlacer.clear();
                state = State.PLANNING;
            } else {
                blockPlacer.update(needed);
                attemptedPlacement = true;
                requestedPositions.clear();
                requestedPositions.addAll(needed);
                state = State.PLACING;
            }
        }
        TargetManager.publish(this, target, state.name().toLowerCase(java.util.Locale.ROOT),
                state == State.PLACING, currentTargets.size());
    }

    private void suspend(State next) {
        state = next;
        currentTarget = null;
        attemptedPlacement = false;
        placementConfirmed = false;
        requestedPositions.clear();
        currentTargets.clear();
        blockPlacer.clear();
        TargetManager.clear(this);
    }

    public State currentState() { return state; }

    private Player findTarget() {
        TargetingUtil.TargetingSettings settings = new TargetingUtil.TargetingSettings(
                enemyRange.get(), 180.0F, true,
                targetFilters.get(TargetFilters.IGNORE_FRIENDS),
                targetFilters.get(TargetFilters.IGNORE_STAFF),
                targetFilters.get(TargetFilters.IGNORE_ENEMIES),
                targetFilters.get(TargetFilters.IGNORE_NAKED), true,
                targetFilters.get(TargetFilters.VISIBLE_ONLY), TargetingUtil.TargetPriority.DISTANCE);
        List<LivingEntity> eligible = TargetingUtil.findTargets(mc, settings);
        LivingEntity shared = TargetManager.getTarget(false);
        if (shared instanceof Player player && eligible.contains(player)) return player;
        return eligible.isEmpty() ? null : eligible.get(0) instanceof Player player ? player : null;
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        ItemStack offhand = player.getOffhandItem();
        if (isWeb(offhand)) return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isWeb(stack)) return new BlockPlacer.PlacementSlot(slot, InteractionHand.MAIN_HAND, stack);
        }
        return null;
    }

    private static boolean isWeb(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() == Blocks.COBWEB;
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
        if (!isEnabled() || !render.get() || mc.level == null) return;
        placementPreview.render(renderer, mc.level, currentTargets, previewBlockState(),
                fillColor.getArgb(), lineColor.getArgb(), lineWidth.get());
    }

    private net.minecraft.world.level.block.state.BlockState previewBlockState() {
        return Blocks.COBWEB.defaultBlockState();
    }

}
