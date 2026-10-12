/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.finder.ChunkBlocks;
import combatant.client.util.finder.FinderRender;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ModuleInfo(
        id = "stashfinder",
        displayName = "StashFinder",
        aliases = {"BaseFinder", "StorageFinder"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.stashfinder.description")
public final class StashFinder extends ChunkFinderModule<StashFinder.Stash> {

    private static final String STORAGE = "storage";
    private static final String FARMS = "farms";
    private static final String REDSTONE = "redstone";
    private static final String PISTONS = "pistons";

    private final NumberValue<Integer> minY = num("min_y", -60, -64, 320);
    private final NumberValue<Integer> maxY = num("max_y", 0, -64, 320);
    private final NumberValue<Integer> minStorage = num("min_storage", 12, 2, 128);
    private final NumberValue<Integer> minChests = num("min_chests", 4, 1, 128);
    private final NumberValue<Integer> minBarrels = num("min_barrels", 4, 1, 128);
    private final NumberValue<Integer> minShulkers = num("min_shulkers", 4, 1, 128);
    private final NumberValue<Integer> minFarmBlocks = num("min_farm_blocks", 64, 8, 4096);
    private final NumberValue<Integer> minRedstone = num("min_redstone", 12, 2, 512);
    private final NumberValue<Integer> minPistons = num("min_pistons", 4, 1, 128);
    private final NumberValue<Integer> cubeSize = num("cube_size", 32, 16, 64);
    private final BooleanValue ignoreRaided = bool("ignore_raided", true);
    private final BooleanValue logoutOnEntry = bool("logout_on_entry", false);
    private final BooleanMapValue signals = group("signals", defaultSignals());
    private final Set<Long> entered = ConcurrentHashMap.newKeySet();
    private final Set<Long> logoutHandled = ConcurrentHashMap.newKeySet();

    public StashFinder() {
        super(16, "#406A27B0", "#FFB76CFF", 4, 4000L);
    }

    private static Map<String, Boolean> defaultSignals() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(STORAGE, true);
        defaults.put(FARMS, true);
        defaults.put(REDSTONE, true);
        defaults.put(PISTONS, true);
        return defaults;
    }

    @Override
    protected String analysisSignature() {
        return minStorage.get() + "|" + minChests.get() + "|" + minBarrels.get() + "|" + minShulkers.get()
            + "|" + minY.get() + "|" + maxY.get() + "|" + minFarmBlocks.get()
            + "|" + minRedstone.get() + "|" + minPistons.get()
                + "|" + ignoreRaided.get() + "|" + signals.get(STORAGE) + "|" + signals.get(FARMS)
                + "|" + signals.get(REDSTONE) + "|" + signals.get(PISTONS);
    }

    @Override
    protected Stash analyzeChunk(ClientLevel level, LevelChunk chunk) {
        Map<Cell, StorageCounts> storageCells = new HashMap<>();
        int[] farms = {0};
        BlockPos[] farmEvidence = {null};
        int scanMinY = Math.min(minY.get(), maxY.get());
        int scanMaxY = Math.max(minY.get(), maxY.get());
        ChunkBlocks.forEach(chunk, scanMinY, scanMaxY, state ->
                isStorage(state) || isRedstone(state) || isPiston(state)
                        || (signals.get(FARMS) && isFarm(state)),
                (x, y, z, state) -> {
                    if (isStorage(state) || isRedstone(state) || isPiston(state)) {
                        StorageCounts counts = storageCells.computeIfAbsent(new Cell(x >> 3, z >> 3), ignored -> new StorageCounts());
                        if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof EnderChestBlock) counts.chests++;
                        if (state.getBlock() instanceof BarrelBlock) counts.barrels++;
                        if (state.getBlock() instanceof ShulkerBoxBlock) counts.shulkers++;
                        if (signals.get(REDSTONE) && isRedstone(state)) counts.redstone++;
                        if (signals.get(PISTONS) && isPiston(state)) counts.pistons++;
                        if (counts.evidence == null) counts.evidence = new BlockPos(x, y, z);
                    }
                    if (signals.get(FARMS) && isFarm(state)) {
                        farms[0]++;
                        if (farmEvidence[0] == null) farmEvidence[0] = new BlockPos(x, y, z);
                    }
                    return true;
                });

        StorageCounts cluster = densestStorageCluster(storageCells);
        int storage = cluster.total();
        boolean typeThresholdMet = cluster.chests >= minChests.get()
                || cluster.barrels >= minBarrels.get()
                || cluster.shulkers >= minShulkers.get();
        boolean storageHit = signals.get(STORAGE) && storage >= minStorage.get() && typeThresholdMet;
        boolean farmHit = signals.get(FARMS) && farms[0] >= minFarmBlocks.get() && storage >= 2;
        boolean buildSupport = storage >= 2 || farms[0] >= minFarmBlocks.get();
        boolean redstoneHit = signals.get(REDSTONE) && cluster.redstone >= minRedstone.get() && buildSupport;
        boolean pistonHit = signals.get(PISTONS) && cluster.pistons >= minPistons.get() && buildSupport;
        BlockPos evidence = cluster.evidence != null ? cluster.evidence : farmEvidence[0];
        if ((!storageHit && !farmHit && !redstoneHit && !pistonHit) || evidence == null) return null;
        if (ignoreRaided.get() && appearsRaided(chunk, storage)) return null;

        return new Stash(storage, cluster.chests, cluster.barrels, cluster.shulkers,
            cluster.redstone, cluster.pistons, farms[0], evidence);
    }

    private static StorageCounts densestStorageCluster(Map<Cell, StorageCounts> cells) {
        StorageCounts best = new StorageCounts();
        for (Cell center : cells.keySet()) {
            StorageCounts cluster = new StorageCounts();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    StorageCounts cell = cells.get(new Cell(center.x + dx, center.z + dz));
                    if (cell == null) continue;
                    cluster.chests += cell.chests;
                    cluster.barrels += cell.barrels;
                    cluster.shulkers += cell.shulkers;
                    cluster.redstone += cell.redstone;
                    cluster.pistons += cell.pistons;
                    if (cluster.evidence == null) cluster.evidence = cell.evidence;
                }
            }
            if (cluster.score() > best.score()) best = cluster;
        }
        return best;
    }

    private boolean appearsRaided(LevelChunk chunk, int storageBlocks) {
        int containers = 0;
        int emptyContainers = 0;
        for (var entry : chunk.getBlockEntities().entrySet()) {
            int y = entry.getKey().getY();
            if (y < Math.min(minY.get(), maxY.get()) || y > Math.max(minY.get(), maxY.get())) continue;
            if (!isStorage(chunk.getBlockState(entry.getKey()))) continue;
            if (entry.getValue() instanceof Container container) {
                containers++;
                if (container.isEmpty()) emptyContainers++;
            }
        }
        int requiredContainers = Math.min(storageBlocks, Math.max(2, minStorage.get() / 2));
        return containers >= requiredContainers && emptyContainers * 4 >= containers * 3;
    }

    private static boolean isStorage(BlockState state) {
        return state.getBlock() instanceof ChestBlock
            || state.getBlock() instanceof BarrelBlock
            || state.getBlock() instanceof EnderChestBlock
            || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isFarm(BlockState state) {
        return state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES)
                || state.is(Blocks.BEETROOTS) || state.is(Blocks.NETHER_WART) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.PUMPKIN_STEM) || state.is(Blocks.MELON_STEM)
                || state.is(Blocks.COCOA) || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    private static boolean isRedstone(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.is(Blocks.REPEATER) || state.is(Blocks.COMPARATOR)
                || state.is(Blocks.OBSERVER) || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH)
                || state.is(Blocks.REDSTONE_LAMP) || state.is(Blocks.REDSTONE_BLOCK) || state.is(Blocks.DISPENSER)
                || state.is(Blocks.DROPPER) || state.is(Blocks.HOPPER) || state.is(Blocks.TRIPWIRE)
                || state.is(Blocks.TRIPWIRE_HOOK);
    }

    private static boolean isPiston(BlockState state) {
        return state.is(Blocks.PISTON) || state.is(Blocks.STICKY_PISTON);
    }

    private record Cell(int x, int z) {
    }

    private static final class StorageCounts {
        private int chests;
        private int barrels;
        private int shulkers;
        private int redstone;
        private int pistons;
        private BlockPos evidence;

        private int total() {
            return chests + barrels + shulkers;
        }

        private int score() {
            return total() * 4 + redstone + pistons;
        }
    }

    @Override
    public void onEnable() {
        entered.clear();
        logoutHandled.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        entered.clear();
        logoutHandled.clear();
        super.onDisable();
    }

    @Override
    public void onTick() {
        super.onTick();
        if (mc.player == null || mc.level == null) return;

        for (Map.Entry<Long, Stash> entry : new LinkedHashMap<>(accepted).entrySet()) {
            long key = entry.getKey();
            if (logoutOnEntry.get() && logoutHandled.add(key)) {
                Notifier.info("STASH FOUND!");
                if (mc.getConnection() != null) {
                    mc.getConnection().getConnection().disconnect(Component.literal("STASH FOUND!"));
                }
                return;
            }
            if (!entered.contains(key) && markerBox(entry.getValue()).intersects(mc.player.getBoundingBox())) {
                entered.add(key);
                if (!logoutOnEntry.get()) Notifier.info("STASH FOUND!");
            }
        }
        entered.retainAll(accepted.keySet());
    }

    @Override
    protected void renderMarker(FinderRender draw, long key, Stash hit, double y) {
        if (entered.contains(key)) return;
        draw.box(markerBox(hit), fillFor(hit), lineFor(hit));
    }

    private AABB markerBox(Stash hit) {
        double size = cubeSize.get();
        BlockPos pos = hit.evidence();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        return new AABB(x - size / 2, y - size / 2, z - size / 2,
                x + size / 2, y + size / 2, z + size / 2);
    }

    public record Stash(int storageCount, int chestCount, int barrelCount, int shulkerCount,
                        int redstoneCount, int pistonCount, int farmCount,
                        BlockPos evidence) implements ChunkFinderModule.Hit {
        @Override
        public String reason() {
            return chestCount + " chests, " + barrelCount + " barrels, " + shulkerCount + " shulkers, "
                    + redstoneCount + " redstone, " + pistonCount + " pistons";
        }
    }
}