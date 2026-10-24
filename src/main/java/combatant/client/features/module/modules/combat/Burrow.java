/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.automation.ActionPriority;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.actions.JumpAction;
import combatant.client.automation.placement.PlacementConstraints;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
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
import combatant.client.features.module.modules.combat.surround.BlockPriority;
import combatant.client.features.module.modules.combat.surround.ShellBlocks;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.helpers.PlacementPreviewRenderer;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a blast-resistant block into the cell the player stands in: jump, and the moment the hitbox has cleared the
 * cell, place the block there through the shared {@link BlockPlacer}. The player then lands on top of it, so
 * crystals can no longer be set off at the feet. Nothing is spoofed; the jump is the vanilla one, so the server
 * sees a legal jump and a legal placement.
 */
@ModuleInfo(
        id = "burrow",
        displayName = "Burrow",
        aliases = {"SelfFill", "FeetBlock"},
        category = ModuleCategory.COMBAT,
        subcategory = ModuleSubcategory.PROTECT,
        description = "module.burrow.description")
public final class Burrow extends Module {

    /** Ticks the whole jump-and-place may take before it is given up. */
    private static final int TIMEOUT_TICKS = 40;
    private static final int JUMP_TIMEOUT_TICKS = 20;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<BlockPriority> blockPriority = enumSetting(
            "burrowBlockPriority", "block_priority", BlockPriority.ANY, BlockPriority.values());
    private final EnumValue<PlacementRotationMode> rotation = enumSetting(
            "burrowRotation", "rotation", PlacementRotationMode.NORMAL, PlacementRotationMode.values());
    private final EnumValue<MovementCorrection> movementCorrection = enumSetting(
            "burrowMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());
    private final NumberValue<Double> range = num("burrowRange", "range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Integer> swapBackDelay = num("burrowSwapBackDelay", "swap_back_delay", 0, 0, 10);
    private final BooleanValue autoDisable = bool("burrowAutoDisable", "auto_disable", true);
    private final BooleanValue render = bool("burrowRender", "render", true);
    private final RGBAColorValue fillColor = color("burrowFillColor", "fill_color", "#285A9C44");
    private final RGBAColorValue lineColor = color("burrowLineColor", "line_color", "#55AAFFFF");
    private final NumberValue<Float> lineWidth = num("burrowLineWidth", "line_width", 2.0F, 1.0F, 6.0F);

    private final BlockPlacer blockPlacer = new BlockPlacer(
            this, 10,
            this::findPlacementSlot,
            this::placementConstraints,
            () -> 0,
            () -> 0,
            () -> 1,
            () -> true,
            () -> true
    );
    private final PlacementPreviewRenderer placementPreview = new PlacementPreviewRenderer();
    private final ArrayList<BlockPos> cells = new ArrayList<>(1);

    private BlockPos feet;
    private int ticks;

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            setEnabled(false);
            return;
        }
        String problem = problem(player, level);
        if (problem != null) {
            Notifier.warning(I18n.get(problem));
            setEnabled(false);
            return;
        }

        feet = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        ticks = 0;
        cells.clear();
        cells.add(feet);
        placementPreview.reset();
        blockPlacer.enable();
        AutomationRuntime.INSTANCE.submit(AutomationTask
                .builder(this, "BurrowJump", new JumpAction(JUMP_TIMEOUT_TICKS))
                .priority(ActionPriority.COMBAT)
                .build());
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "burrow disabled");
        blockPlacer.disable();
        placementPreview.reset();
        cells.clear();
        feet = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || feet == null) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            setEnabled(false);
            return;
        }

        if (!level.getBlockState(feet).canBeReplaced()) {
            if (autoDisable.get()) setEnabled(false);
            return;
        }
        if (++ticks > TIMEOUT_TICKS) {
            Notifier.warning(I18n.get("notification.burrow.failed"));
            setEnabled(false);
            return;
        }

        // The hitbox only leaves the cell once its bottom is at the next block level.
        if (player.getBoundingBox().minY >= feet.getY() + 1.0D) {
            blockPlacer.update(cells);
        }
        blockPlacer.tick();
        if (render.get()) placementPreview.tick(level, cells, previewBlockState(), lineColor.getArgb());
    }

    /** A key into the language files when the burrow cannot start, null when it can. */
    private String problem(LocalPlayer player, ClientLevel level) {
        if (ShellBlocks.find(player, blockPriority.get()) == null) return "notification.burrow.no_blocks";
        if (!player.onGround()) return "notification.burrow.not_grounded";
        BlockPos at = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        if (!level.getBlockState(at).canBeReplaced()) return "notification.burrow.already_burrowed";
        // Rising past the cell needs the cell two above the feet open as well.
        if (!level.getBlockState(at.above(2)).getCollisionShape(level, at.above(2)).isEmpty()) {
            return "notification.burrow.no_headroom";
        }
        return null;
    }

    private PlacementSlot findPlacementSlot(BlockPos ignored) {
        LocalPlayer player = mc.player;
        return player == null ? null : ShellBlocks.find(player, blockPriority.get());
    }

    private PlacementConstraints placementConstraints() {
        return new PlacementConstraints(
                range.get(), range.get(), rotation.get(), movementCorrection.get(),
                rotation.get() == PlacementRotationMode.NO_ROTATION, false, 1,
                swapBackDelay.get(), swapBackDelay.get(), true, 0);
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null || cells.isEmpty()) return;
        placementPreview.render(renderer, mc.level, List.copyOf(cells), previewBlockState(),
                fillColor.getArgb(), lineColor.getArgb(), lineWidth.get());
    }

    private BlockState previewBlockState() {
        LocalPlayer player = mc.player;
        PlacementSlot slot = player == null ? null : ShellBlocks.find(player, blockPriority.get());
        return slot != null && slot.stack().getItem() instanceof BlockItem item
                ? item.getBlock().defaultBlockState() : null;
    }
}
