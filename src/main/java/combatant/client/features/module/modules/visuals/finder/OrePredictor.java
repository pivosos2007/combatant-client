/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals.finder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.finder.FinderRender;
import combatant.client.util.finder.LoadedChunkScanner;
import combatant.client.util.finder.OreSimulation;
import combatant.client.util.finder.ServerSeedCheck;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Predicts diamond, emerald, gold and ancient debris from the world seed by replaying vanilla
 * ore decoration ({@link OreSimulation}), so anti-xray cannot hide them. A second pass compares
 * the buried diamond and lapis veins the seed says must exist with what the client sees: spots
 * that read as dug-out tunnel air mean a player has been mining there.
 *
 * <p>Chunks are analyzed by a {@link LoadedChunkScanner} on the client thread with a per-tick
 * budget; only the one-off registry build for the simulations runs on a background thread.
 * Reading chunk sections off-thread races with chunk unloads, which the scanner avoids.</p>
 */
@ModuleInfo(
        id = "orepredictor",
        displayName = "OrePredictor",
        aliases = {"OreSim", "SeedOres"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.orepredictor.description")
public final class OrePredictor extends Module {

    private enum Kind { DIAMOND, EMERALD, GOLD, DEBRIS, HIDDEN }

    private record Ore(BlockPos pos, Kind kind) {
    }

    /** {@code minedCount} spots of buried veins read as dug out, the lowest at {@code minedY}. */
    private record ChunkResult(List<Ore> ores, int minedCount, int minedY) {
    }

    private static final int MAX_BOXES = 2048;
    private static final int FILL_ALPHA = 0x50;

    private static final Map<ResourceKey<PlacedFeature>, Kind> OVERWORLD_FEATURES = Map.ofEntries(
            Map.entry(OrePlacements.ORE_DIAMOND, Kind.DIAMOND),
            Map.entry(OrePlacements.ORE_DIAMOND_MEDIUM, Kind.DIAMOND),
            Map.entry(OrePlacements.ORE_DIAMOND_LARGE, Kind.DIAMOND),
            Map.entry(OrePlacements.ORE_DIAMOND_BURIED, Kind.DIAMOND),
            Map.entry(OrePlacements.ORE_EMERALD, Kind.EMERALD),
            Map.entry(OrePlacements.ORE_GOLD, Kind.GOLD),
            Map.entry(OrePlacements.ORE_GOLD_LOWER, Kind.GOLD),
            Map.entry(OrePlacements.ORE_GOLD_EXTRA, Kind.GOLD),
            // Only watched for dug-out spots; never drawn.
            Map.entry(OrePlacements.ORE_LAPIS_BURIED, Kind.HIDDEN));

    private static final Map<ResourceKey<PlacedFeature>, Kind> NETHER_FEATURES = Map.of(
            OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, Kind.DEBRIS,
            OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, Kind.DEBRIS);

    // Registry building is slow and the result never changes within a game session.
    private static volatile CompletableFuture<OreSimulation> overworldSimulation;
    private static volatile CompletableFuture<OreSimulation> netherSimulation;

    private final StringValue seed = text("seed", "");
    private final NumberValue<Integer> rangeChunks = num("range", 6, 1, 12);
    private final BooleanValue diamond = bool("diamond", true);
    private final RGBAColorValue diamondColor = color("diamond_color", "#FF3DE0E0");
    private final BooleanValue emerald = bool("emerald", true);
    private final RGBAColorValue emeraldColor = color("emerald_color", "#FF17DD62");
    private final BooleanValue gold = bool("gold", true);
    private final RGBAColorValue goldColor = color("gold_color", "#FFFCEE4B");
    private final BooleanValue ancientDebris = bool("ancient_debris", true);
    private final RGBAColorValue debrisColor = color("ancient_debris_color", "#FFD11BF5");
    private final BooleanValue highlightMinedChunks = bool("highlight_mined_chunks", true);
    private final NumberValue<Integer> minedThreshold = num("mined_threshold", 2, 1, 16);
    private final RGBAColorValue minedColor = color("mined_color", "#80FF4040");

    private final Minecraft mc = Minecraft.getInstance();
    // A vein reaches up to 4 blocks past its chunk and the air check looks 1 further, so the ring
    // of neighbours must be loaded: an unloaded chunk reads as air and would fake mined spots.
    private final LoadedChunkScanner<ChunkResult> scanner =
            new LoadedChunkScanner<>(this::predict, rangeChunks::get, 6, 3000L, 1);
    private String scannedSeed = "";
    private ClientLevel seedCheckedLevel;
    private String seedCheckedValue = "";
    private boolean missingSeedWarned;

    @Override
    public void onEnable() {
        scanner.clear();
        scannedSeed = "";
        seedCheckedLevel = null;
        missingSeedWarned = false;
        overworldSimulation();
        netherSimulation();
    }

    @Override
    public void onDisable() {
        scanner.clear();
    }

    private static CompletableFuture<OreSimulation> overworldSimulation() {
        CompletableFuture<OreSimulation> current = overworldSimulation;
        if (current == null || current.isCompletedExceptionally()) {
            synchronized (OrePredictor.class) {
                current = overworldSimulation;
                if (current == null || current.isCompletedExceptionally()) {
                    current = CompletableFuture.supplyAsync(() -> OreSimulation.forOverworld(
                            List.copyOf(OVERWORLD_FEATURES.keySet()), List.of()));
                    overworldSimulation = current;
                }
            }
        }
        return current;
    }

    private static CompletableFuture<OreSimulation> netherSimulation() {
        CompletableFuture<OreSimulation> current = netherSimulation;
        if (current == null || current.isCompletedExceptionally()) {
            synchronized (OrePredictor.class) {
                current = netherSimulation;
                if (current == null || current.isCompletedExceptionally()) {
                    current = CompletableFuture.supplyAsync(() -> OreSimulation.forNether(
                            List.copyOf(NETHER_FEATURES.keySet()), List.of()));
                    netherSimulation = current;
                }
            }
        }
        return current;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        scanner.onPacket(event.getPacket());
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) return;
        CompletableFuture<OreSimulation> future = simulationFor(mc.level.dimension());
        if (future == null) return;

        if (future.isCompletedExceptionally()) {
            Throwable cause = future.handle((ok, error) -> error).join();
            DebugLog.error("OrePredictor could not build the ore simulation", cause);
            Notifier.error("OrePredictor: could not load world-gen data, see log");
            setEnabledInternal(false);
            return;
        }
        if (!future.isDone()) return;

        if (seed.get().isBlank()) {
            if (!missingSeedWarned) {
                missingSeedWarned = true;
                Notifier.warning("OrePredictor: set the world seed in the module settings");
            }
            return;
        }
        checkSeedAgainstServer();
        if (!seed.get().equals(scannedSeed)) {
            scannedSeed = seed.get();
            scanner.rescanAll();
        }
        scanner.tick(mc);
    }

    private static CompletableFuture<OreSimulation> simulationFor(ResourceKey<Level> dimension) {
        if (dimension == Level.OVERWORLD) return overworldSimulation();
        if (dimension == Level.NETHER) return netherSimulation();
        return null;
    }

    // A wrong seed still draws plausible boxes, just in the wrong places, so say so once per
    // level and seed. A warning, not a stop: a server may send a fake seed hash.
    private void checkSeedAgainstServer() {
        String current = seed.get();
        if (mc.level == seedCheckedLevel && current.equals(seedCheckedValue)) return;
        seedCheckedLevel = mc.level;
        seedCheckedValue = current;
        Optional<Boolean> match = ServerSeedCheck.matches(mc.level, parseSeed(current));
        if (match.isEmpty()) return;
        if (match.get()) {
            Notifier.success("OrePredictor: seed matches this server");
        } else {
            Notifier.warning("OrePredictor: seed does NOT match this server, predictions will be in the "
                    + "wrong places. Set the server's real seed in the module settings.");
        }
    }

    private ChunkResult predict(ClientLevel level, LevelChunk chunk) {
        boolean overworld = level.dimension() == Level.OVERWORLD;
        Map<ResourceKey<PlacedFeature>, Kind> kinds = overworld ? OVERWORLD_FEATURES : NETHER_FEATURES;
        if (!overworld && level.dimension() != Level.NETHER) return null;
        OreSimulation sim = (overworld ? overworldSimulation() : netherSimulation()).getNow(null);
        if (sim == null) return null;

        ChunkPos pos = chunk.getPos();
        List<OreSimulation.Hit> hits = sim.simulateHits(level, parseSeed(scannedSeed), pos.x(), pos.z(), 0);
        List<Ore> ores = new ArrayList<>();
        // Several diamond or gold features can claim the same block; draw it once.
        Set<Long> seen = new HashSet<>();
        int mined = 0;
        int minedY = Integer.MAX_VALUE;
        for (OreSimulation.Hit hit : hits) {
            if (hit.mined()) {
                mined++;
                minedY = Math.min(minedY, hit.pos().getY());
            } else if (kinds.get(hit.key()) != Kind.HIDDEN && seen.add(hit.pos().asLong())) {
                ores.add(new Ore(hit.pos(), kinds.get(hit.key())));
            }
        }
        if (ores.isEmpty() && mined == 0) return null;
        return new ChunkResult(List.copyOf(ores), mined, minedY);
    }

    // Numeric seeds parse as-is; anything else goes through String.hashCode, the same rule
    // vanilla applies to a seed typed into the world creation screen.
    private static long parseSeed(String text) {
        String trimmed = text == null ? "" : text.trim();
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            return trimmed.hashCode();
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    private boolean enabledFor(Kind kind) {
        return switch (kind) {
            case DIAMOND -> diamond.get();
            case EMERALD -> emerald.get();
            case GOLD -> gold.get();
            case DEBRIS -> ancientDebris.get();
            case HIDDEN -> false;
        };
    }

    private int colorFor(Kind kind) {
        return switch (kind) {
            case DIAMOND -> diamondColor.getArgb();
            case EMERALD -> emeraldColor.getArgb();
            case GOLD -> goldColor.getArgb();
            case DEBRIS -> debrisColor.getArgb();
            case HIDDEN -> 0;
        };
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || renderer == null || player == null || mc.level == null) return;
        if (mc.level.dimension() != Level.OVERWORLD && mc.level.dimension() != Level.NETHER) return;

        FinderRender draw = FinderRender.begin(renderer, 1.0f);
        boolean showMined = highlightMinedChunks.get();
        int threshold = minedThreshold.get();
        int minedArgb = minedColor.getArgb();
        int range = rangeChunks.get();
        ChunkPos center = player.chunkPosition();
        Map<Long, ChunkResult> results = scanner.results();
        // Walk square rings outward from the player: when MAX_BOXES cuts the list, the ore that
        // gets dropped is the farthest, not whatever the hash map happened to put last.
        int drawn = 0;
        for (int ring = 0; ring <= range; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                boolean edgeColumn = dx == -ring || dx == ring;
                for (int dz = -ring; dz <= ring; dz += edgeColumn ? 1 : 2 * ring) {
                    int chunkX = center.x() + dx;
                    int chunkZ = center.z() + dz;
                    ChunkResult result = results.get(ChunkPos.pack(chunkX, chunkZ));
                    if (result == null) continue;
                    if (showMined && result.minedCount() >= threshold) {
                        draw.chunkPlate(chunkX, chunkZ, result.minedY(), (minedArgb & 0x00FFFFFF) | (FILL_ALPHA << 24),
                                minedArgb);
                    }
                    for (Ore ore : result.ores()) {
                        if (!enabledFor(ore.kind())) continue;
                        int line = colorFor(ore.kind());
                        int fill = (line & 0x00FFFFFF) | (FILL_ALPHA << 24);
                        draw.blockBox(ore.pos().getX(), ore.pos().getY(), ore.pos().getZ(), fill, line);
                        if (++drawn >= MAX_BOXES) return;
                    }
                }
            }
        }
    }
}
