/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.block.scaffold;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class ScaffoldMovementPlanner {

    private static final int MAX_LAST_PLACED_BLOCKS = 4;
    private static final double[] OFFSETS_TO_TRY = {0.301, 0.0, -0.301};

    private final ArrayDeque<BlockPos> lastPlacedBlocks = new ArrayDeque<>(MAX_LAST_PLACED_BLOCKS);
    private BlockPos lastPosition;

    public ScaffoldLine getOptimalMovementLine(LocalPlayer player, float movementYaw) {
        if (player == null) return null;

        Vec3 direction = chooseDirection(movementYaw);
        BlockPos blockUnderPlayer = findBlockPlayerStandsOn(player);
        if (blockUnderPlayer == null) return null;

        ScaffoldLine lastBlocksLine = fitLineThroughLastPlacedBlocks();
        Vec3 lineBase = Vec3.atCenterOf(blockUnderPlayer);

        if (lastBlocksLine != null && !divergesTooMuchFromDirection(lastBlocksLine, direction)) {
            lineBase = lastBlocksLine.position();
        }

        return new ScaffoldLine(new Vec3(lineBase.x, player.getY(), lineBase.z), direction);
    }

    public void trackPlacedBlock(BlockPos target) {
        if (target == null) return;
        if (target.equals(lastPlacedBlocks.peekLast())) return;

        while (lastPlacedBlocks.size() >= MAX_LAST_PLACED_BLOCKS) {
            lastPlacedBlocks.removeFirst();
        }
        lastPlacedBlocks.addLast(target.immutable());
    }

    public void reset() {
        lastPlacedBlocks.clear();
        lastPosition = null;
    }

    private boolean divergesTooMuchFromDirection(ScaffoldLine line, Vec3 direction) {
        return line.direction().dot(direction.normalize()) < 0.5;
    }

    private ScaffoldLine fitLineThroughLastPlacedBlocks() {
        if (lastPlacedBlocks.size() < 2) return null;

        BlockPos[] blocks = lastPlacedBlocks.toArray(new BlockPos[0]);
        BlockPos last = blocks[blocks.length - 1];
        BlockPos second = blocks[blocks.length - 2];
        if (last == null || second == null || last.equals(second)) return null;

        Vec3 secondCenter = Vec3.atCenterOf(second);
        Vec3 lastCenter = Vec3.atCenterOf(last);
        Vec3 avg = secondCenter.add(lastCenter).scale(0.5);
        Vec3 dir = lastCenter.subtract(secondCenter);
        if (dir.lengthSqr() < 1.0E-7) return null;
        return new ScaffoldLine(avg, dir);
    }

    private BlockPos findBlockPlayerStandsOn(LocalPlayer player) {
        List<BlockPos> candidates = new ArrayList<>();
        for (double xOffset : OFFSETS_TO_TRY) {
            for (double zOffset : OFFSETS_TO_TRY) {
                BlockPos pos = BlockPos.containing(player.getX() + xOffset, player.getY() - 1.0, player.getZ() + zOffset);
                if (!player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty()) {
                    candidates.add(pos.immutable());
                }
            }
        }

        BlockPos lastPlaced = lastPlacedBlocks.peekLast();
        if (lastPlaced != null && candidates.contains(lastPlaced)) {
            return lastPlaced;
        }

        if (lastPosition != null && candidates.contains(lastPosition)) {
            return lastPosition;
        }

        BlockPos candidate = candidates.stream().findFirst().orElse(null);
        lastPosition = candidate;
        return candidate;
    }

    private Vec3 chooseDirection(float currentAngle) {
        float currentDirection = currentAngle / 180.0f * 4.0f + 4.0f;
        float newDirectionNumber = Math.round(currentDirection);
        float newDirectionAngle = Mth.wrapDegrees((newDirectionNumber - 4.0f) / 4.0f * 180.0f + 90.0f);
        double radians = Math.toRadians(newDirectionAngle);
        return new Vec3(Math.cos(radians), 0.0, Math.sin(radians));
    }
}
