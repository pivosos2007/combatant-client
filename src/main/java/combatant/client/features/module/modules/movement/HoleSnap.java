/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.movement.holesnap.HoleLocator;
import combatant.client.features.module.modules.movement.holesnap.HoleLocator.Cell;
import combatant.client.features.module.modules.movement.holesnap.HoleLocator.Hole;
import combatant.client.features.module.modules.movement.holesnap.Steering;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Walks you to the nearest safe hole (bedrock or blast-proof floor and walls, three blocks of
 * headroom) and switches itself off once you stand in it. It presses the same keys you would, picked
 * from your current facing, so there is no velocity change and no view rotation; if no hole is in range
 * it tells you and stops. The hole shape matches what HoleESP draws.
 */
@ModuleInfo(
        id = "holesnap",
        displayName = "HoleSnap",
        aliases = {"HoleWalk", "GoToHole"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.holesnap.description")
public final class HoleSnap extends Module {

    private static final String TYPE_BEDROCK = "bedrock";
    private static final String TYPE_BLAST_PROOF = "blast_proof";
    private static final int RESCAN_TICKS = 4;
    private static final double ARRIVE_DISTANCE_SQ = 0.12 * 0.12;
    private static final double SPRINT_DISTANCE_SQ = 2.5 * 2.5;

    private final Minecraft mc = Minecraft.getInstance();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final HoleLocator.World world = this::cellAt;

    private final NumberValue<Integer> range = num("holeSnapRange", "range", 6, 1, 12);
    private final NumberValue<Integer> maxDrop = num("holeSnapMaxDrop", "max_drop", 3, 0, 8);
    private final NumberValue<Integer> maxRise = num("holeSnapMaxRise", "max_rise", 1, 0, 2);
    private final BooleanMapValue types = group("holeSnapTypes", "types", defaultTypes());
    private final BooleanValue sprint = bool("holeSnapSprint", "sprint", true);
    private final BooleanValue jumpObstacles = bool("holeSnapJumpObstacles", "jump_obstacles", true);
    private final NumberValue<Integer> timeoutTicks = num("holeSnapTimeoutTicks", "timeout_ticks", 120, 20, 400);

    private Hole target;
    private int ticks;
    private int rescanIn;

    @Override
    public void onEnable() {
        target = null;
        ticks = 0;
        rescanIn = 0;
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }
        if (++ticks > timeoutTicks.get()) {
            finish("notification.holesnap.timeout", true);
            return;
        }

        if (target == null || --rescanIn <= 0) {
            target = HoleLocator.nearest(world, player.getX(), player.getY(), player.getZ(),
                    range.get(), maxDrop.get(), maxRise.get(),
                    types.get(TYPE_BEDROCK), types.get(TYPE_BLAST_PROOF));
            rescanIn = RESCAN_TICKS;
        }
        if (target == null) {
            finish("notification.holesnap.no_hole", true);
            return;
        }

        BlockPos feet = player.blockPosition();
        if (feet.getX() == target.x() && feet.getY() == target.y() && feet.getZ() == target.z()) {
            finish("notification.holesnap.arrived", false);
        }
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || target == null) return;

        double dx = target.centerX() - player.getX();
        double dz = target.centerZ() - player.getZ();
        double distanceSq = dx * dx + dz * dz;
        int keys = distanceSq < ARRIVE_DISTANCE_SQ ? 0 : Steering.keysToward(player.getYRot(), dx, dz);

        // Direct input replaces the player's own keys while the module walks them to the hole.
        event.setForward((keys & Steering.FORWARD) != 0);
        event.setBackward((keys & Steering.BACKWARD) != 0);
        event.setLeft((keys & Steering.LEFT) != 0);
        event.setRight((keys & Steering.RIGHT) != 0);
        event.setSneak(false);
        event.setSprint(sprint.get() && (keys & Steering.FORWARD) != 0 && distanceSq > SPRINT_DISTANCE_SQ);
        event.setJump(jumpObstacles.get() && keys != 0 && player.horizontalCollision && player.onGround());
    }

    private Cell cellAt(int x, int y, int z) {
        ClientLevel level = mc.level;
        if (level == null || y < level.getMinY() || y >= level.getMaxY()) return Cell.OTHER;

        BlockState state = level.getBlockState(cursor.set(x, y, z));
        if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()) return Cell.AIR;

        Block block = state.getBlock();
        if (block == Blocks.BEDROCK) return Cell.BEDROCK;
        if (block == Blocks.OBSIDIAN || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.CRYING_OBSIDIAN || block == Blocks.RESPAWN_ANCHOR) {
            return Cell.BLAST_PROOF;
        }
        return Cell.OTHER;
    }

    private void finish(String messageKey, boolean warn) {
        String message = I18n.get(messageKey);
        if (warn) Notifier.warning(message);
        else Notifier.info(message);
        setEnabled(false);
    }

    private static Map<String, Boolean> defaultTypes() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TYPE_BEDROCK, true);
        defaults.put(TYPE_BLAST_PROOF, true);
        return defaults;
    }
}
