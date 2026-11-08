/*
 * Adapted from Accurate Block Placement: Reborn (MIT).
 * Copyright (c) 2023 schwar, 2023 KadTheHunter, 2022 Flourick, 2019 Clayborn.
 * See THIRD_PARTY_LICENSES/AccurateBlockPlacement-Reborn-MIT.txt.
 */
package combatant.client.features.module.modules.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

final class AccuratePlacementController<T> {
    private static final int MAX_BACKFILL = 8;

    private BlockPos lastSeenBlockPos;
    private BlockPos lastPlacedBlockPos;
    private Vec3 lastPlayerPlacedBlockPos;
    private boolean autoRepeatWaitingOnCooldown = true;
    private Vec3 lastFreshPressMouseRatio;
    private final LinkedHashMap<Site, T> backFillList = new LinkedHashMap<>();
    private final Set<Site> attemptedSites = new HashSet<>();
    private Site lastBackwardAttempt;
    private Vec3 lastBackwardAttemptPosition;

    void freshPress(Vec3 mouseRatio) {
        reset();
        lastFreshPressMouseRatio = mouseRatio;
    }

    Decision<T> select(T target, BlockPos support, Direction face, BlockPos destination,
                       int clicks, boolean held, int cooldown, Vec3 playerPosition, Vec3 mouseRatio) {
        if (clicks == 0 && !held) return new Decision<>(List.of(), 0, "released");

        Site site = new Site(support.immutable(), face, destination.immutable());
        boolean backward = buildingBackward(support, face, playerPosition)
                && (!site.equals(lastBackwardAttempt) || lastBackwardAttemptPosition == null
                || Math.abs(playerPosition.get(face.getAxis())
                - lastBackwardAttemptPosition.get(face.getAxis())) >= 0.99);
        // ABP:R's automatic-placement bias is based on the clicked support block,
        // not on the face/destination. After placing a block, looking at its top
        // must not trigger an additional placement while the key is held.
        boolean fresh = (isNewTarget(support) && canAutoPlace(support, destination))
                || (backward && !wouldStackVertically(destination));
        boolean repeat = attemptedSites.contains(site) && !backward;
        boolean mouseMoved = mouseRatio != null && lastFreshPressMouseRatio != null
                && lastFreshPressMouseRatio.distanceTo(mouseRatio) >= 0.1;
        boolean onCooldown = autoRepeatWaitingOnCooldown && cooldown > 0 && !mouseMoved;
        Decision<T> decision;

        if (clicks > 0 || (fresh && !repeat && !onCooldown)) {
            List<T> backfill = List.of();
            if (autoRepeatWaitingOnCooldown && clicks == 0) {
                autoRepeatWaitingOnCooldown = false;
                backfill = backFillList.entrySet().stream()
                        .filter(entry -> canAutoPlace(entry.getKey().support(), entry.getKey().destination()))
                        .map(java.util.Map.Entry::getValue).toList();
                backFillList.clear();
            }
            attemptedSites.add(site);
            if (backward) {
                lastBackwardAttempt = site;
                lastBackwardAttemptPosition = playerPosition;
            }
            decision = new Decision<>(backfill, Math.max(1, clicks), clicks > 0 ? "fresh-press" : "fresh-target");
        } else if (fresh && !repeat && onCooldown) {
            if (backFillList.size() >= MAX_BACKFILL && !backFillList.containsKey(site)) {
                backFillList.remove(backFillList.keySet().iterator().next());
            }
            backFillList.putIfAbsent(site, target);
            decision = new Decision<>(List.of(), 0, "backfill-cooldown");
        } else {
            decision = new Decision<>(List.of(), 0, repeat ? "already-attempted" : "same-target");
        }

        lastSeenBlockPos = support.immutable();
        return decision;
    }

    private boolean isNewTarget(BlockPos support) {
        return (lastSeenBlockPos == null || !lastSeenBlockPos.equals(support))
                && (lastPlacedBlockPos == null || !lastPlacedBlockPos.equals(support));
    }

    boolean canAutoPlace(BlockPos support, BlockPos destination) {
        if (lastPlacedBlockPos == null) return true;
        if (lastPlacedBlockPos.equals(support)) return false;
        // Bias held-key placement toward extending a row. A block immediately
        // above the previous placement requires an actual new use-key press.
        return !wouldStackVertically(destination);
    }

    private boolean wouldStackVertically(BlockPos destination) {
        return lastPlacedBlockPos != null
                && destination.getX() == lastPlacedBlockPos.getX()
                && destination.getZ() == lastPlacedBlockPos.getZ()
                && destination.getY() == lastPlacedBlockPos.getY() + 1;
    }

    private boolean buildingBackward(BlockPos support, Direction face, Vec3 playerPosition) {
        if (lastPlacedBlockPos == null || lastPlayerPlacedBlockPos == null
                || !lastPlacedBlockPos.equals(support)) return false;
        Direction.Axis axis = face.getAxis();
        double previous = lastPlayerPlacedBlockPos.get(axis);
        double current = playerPosition.get(axis);
        double boundary = Vec3.atLowerCornerOf(lastPlacedBlockPos).get(axis);
        if (face == Direction.WEST || face == Direction.NORTH) boundary += 1.0;
        return Math.abs(previous - current) >= 0.99
                && Math.abs(previous - boundary) < Math.abs(current - boundary);
    }

    void placed(BlockPos destination, Direction face, Vec3 playerPosition) {
        lastPlacedBlockPos = destination.immutable();
        if (lastPlayerPlacedBlockPos == null) {
            lastPlayerPlacedBlockPos = playerPosition;
            return;
        }
        Vec3 summed = lastPlayerPlacedBlockPos.add(face.getStepX(), face.getStepY(), face.getStepZ());
        lastPlayerPlacedBlockPos = switch (face.getAxis()) {
            case X -> new Vec3(summed.x, playerPosition.y, playerPosition.z);
            case Y -> new Vec3(playerPosition.x, summed.y, playerPosition.z);
            case Z -> new Vec3(playerPosition.x, playerPosition.y, summed.z);
        };
    }

    void reset() {
        lastSeenBlockPos = null;
        lastPlacedBlockPos = null;
        lastPlayerPlacedBlockPos = null;
        autoRepeatWaitingOnCooldown = true;
        lastFreshPressMouseRatio = null;
        backFillList.clear();
        attemptedSites.clear();
        lastBackwardAttempt = null;
        lastBackwardAttemptPosition = null;
    }

    State state() {
        return new State(lastSeenBlockPos, lastPlacedBlockPos, lastPlayerPlacedBlockPos,
                autoRepeatWaitingOnCooldown, backFillList.size());
    }

    private record Site(BlockPos support, Direction face, BlockPos destination) { }
    record Decision<T>(List<T> backfill, int uses, String reason) { }
    record State(BlockPos seen, BlockPos placed, Vec3 player, boolean waiting, int backfillSize) { }
}
