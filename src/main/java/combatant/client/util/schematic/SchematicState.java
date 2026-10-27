/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one active schematic shared by the menu, the projection and the builder: which file, where it is placed,
 * which layer is selected and how the world compares to it. Main-thread only.
 */
public final class SchematicState {

    public static final SchematicState INSTANCE = new SchematicState();

    public enum LayerMode {
        ALL,
        CURRENT_LAYER,
        LAYER_RANGE,
        BELOW_AND_CURRENT
    }

    private Schematic schematic;
    private LayerStats stats;
    private SchematicPlacement placement;
    private SchematicDiff diff;
    private Level diffLevel;
    private int layer;
    private LayerMode layerMode = LayerMode.CURRENT_LAYER;
    private int rangeSize = 3;
    private int revision;
    private long lastDiffTick = Long.MIN_VALUE;
    private List<SchematicMaterials.Entry> missingMaterials = List.of();
    private final Map<Long, SchematicAttention.Entry> attention = new LinkedHashMap<>();
    private int attentionRevision;

    private SchematicState() {
    }

    public Schematic schematic() {
        return schematic;
    }

    public LayerStats stats() {
        return stats;
    }

    public SchematicPlacement placement() {
        return placement;
    }

    public boolean hasSchematic() {
        return schematic != null;
    }

    public boolean isPlaced() {
        return schematic != null && placement != null;
    }

    public int layer() {
        return layer;
    }

    public LayerMode layerMode() {
        return layerMode;
    }

    public int rangeSize() {
        return rangeSize;
    }

    /** Bumped on every change to the schematic, placement, layer or layer mode. */
    public int revision() {
        return revision;
    }

    public List<SchematicMaterials.Entry> missingMaterials() {
        return missingMaterials;
    }

    public void setMissingMaterials(List<SchematicMaterials.Entry> missing) {
        missingMaterials = missing == null ? List.of() : List.copyOf(missing);
    }

    /** Cells the builder could not create exactly, oldest first. */
    public List<SchematicAttention.Entry> attention() {
        return List.copyOf(attention.values());
    }

    public int attentionCount() {
        return attention.size();
    }

    /** Bumped whenever an entry is added or removed. */
    public int attentionRevision() {
        return attentionRevision;
    }

    public boolean flagged(BlockPos pos) {
        return attention.containsKey(pos.asLong());
    }

    /** Records why the builder could not make the state at {@code pos}; a newer reason replaces an older one. */
    public void flag(BlockPos pos, BlockState expected, SchematicAttention.Reason reason) {
        SchematicAttention.Entry previous = attention.get(pos.asLong());
        if (previous != null && previous.reason() == reason) return;
        attention.put(pos.asLong(), new SchematicAttention.Entry(pos.immutable(), expected, reason, 1));
        attentionRevision++;
    }

    public void unflag(BlockPos pos) {
        if (attention.remove(pos.asLong()) != null) attentionRevision++;
    }

    public void clearAttention() {
        if (attention.isEmpty()) return;
        attention.clear();
        attentionRevision++;
    }

    public void setSchematic(Schematic next, LayerStats nextStats) {
        schematic = next;
        stats = next == null ? null : (nextStats != null ? nextStats : LayerStats.compute(next));
        layer = 0;
        diff = null;
        missingMaterials = List.of();
        clearAttention();
        if (next == null) placement = null;
        changed();
    }

    public void clear() {
        setSchematic(null, null);
    }

    public void setPlacement(SchematicPlacement next) {
        placement = next;
        diff = null;
        clearAttention();
        changed();
    }

    public void setOrigin(BlockPos origin) {
        setPlacement(placement == null ? SchematicPlacement.at(origin) : placement.withOrigin(origin));
    }

    public void rotateClockwise() {
        if (placement != null) setPlacement(placement.rotatedClockwise());
    }

    public void cycleMirror() {
        if (placement != null) setPlacement(placement.withNextMirror());
    }

    public int layerCount() {
        return schematic == null ? 0 : schematic.sizeY();
    }

    public void setLayer(int next) {
        int clamped = schematic == null ? 0 : Math.max(0, Math.min(next, schematic.sizeY() - 1));
        if (clamped == layer) return;
        layer = clamped;
        changed();
    }

    public void stepLayer(int delta) {
        setLayer(layer + delta);
    }

    public void setLayerMode(LayerMode mode) {
        if (mode == null || mode == layerMode) return;
        layerMode = mode;
        changed();
    }

    public void setRangeSize(int size) {
        int clamped = Math.max(1, size);
        if (clamped == rangeSize) return;
        rangeSize = clamped;
        changed();
    }

    public int visibleLow() {
        return switch (layerMode) {
            case ALL, BELOW_AND_CURRENT -> 0;
            case CURRENT_LAYER, LAYER_RANGE -> layer;
        };
    }

    public int visibleHigh() {
        int top = Math.max(0, layerCount() - 1);
        return switch (layerMode) {
            case ALL -> top;
            case CURRENT_LAYER, BELOW_AND_CURRENT -> layer;
            case LAYER_RANGE -> Math.min(top, layer + rangeSize - 1);
        };
    }

    /** The diff for the current placement in the current level, rebuilt when either changes. */
    public SchematicDiff diff(Level level) {
        if (!isPlaced() || level == null) return null;
        if (diff == null || diffLevel != level) {
            diff = new SchematicDiff(schematic, placement);
            diffLevel = level;
        }
        return diff;
    }

    /** Current diff without creating one. */
    public SchematicDiff currentDiff() {
        return diff;
    }

    /** Advances the world comparison once per client tick no matter how many callers ask. */
    public void tickDiff(Minecraft mc, int focusBudget, int globalBudget) {
        if (mc == null || mc.level == null || mc.player == null) return;
        long tick = mc.level.getGameTime() ^ ((long) mc.player.tickCount << 40);
        if (tick == lastDiffTick) return;
        lastDiffTick = tick;
        SchematicDiff current = diff(mc.level);
        if (current == null) return;
        current.setFocus(visibleLow(), visibleHigh());
        current.tick(mc.level, focusBudget, globalBudget);
    }

    private void changed() {
        revision++;
    }
}
