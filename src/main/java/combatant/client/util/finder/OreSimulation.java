/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.finder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/**
 * Replays vanilla ore decoration for one chunk from the world seed, the "OreSim" technique.
 *
 * <p>Vanilla decorates each chunk with a {@link WorldgenRandom} seeded from the world seed and
 * the chunk origin, then re-seeds it per feature from that feature's global index within its
 * generation step. With the seed and those indices, every random call can be reproduced, so
 * ore positions are known without seeing the blocks (anti-xray hides them).</p>
 *
 * <p>The random sequence must match vanilla call for call: {@link #simulate} walks the placed
 * feature's modifiers in order and gives up on any modifier type it does not model, because a
 * single missed call shifts every later prediction.</p>
 *
 * <p>A replay is only right when the server generated its chunks exactly like this client's version with the
 * given seed. {@link #calibrate} checks that against the world: ores that generate next to air (nether quartz and
 * gold) are never hidden by anti-xray, so their predicted air-exposed blocks must show the ore.</p>
 */
public final class OreSimulation {

    /** One placed feature, resolved against the vanilla registries. */
    public record OreSpec(ResourceKey<PlacedFeature> key, PlacedFeature placed, int step, int index,
                          List<PlacementModifier> modifiers, int size, float discardChanceOnAirExposure,
                          boolean scattered, List<BlockReplacement> targets, boolean calibration) {

        /** Buried ores (discard chance 1) are never next to air, which makes the air check draw-free. */
        public boolean buried() {
            return discardChanceOnAirExposure >= 1.0f;
        }

        boolean isOre(BlockState state) {
            for (BlockReplacement target : targets) {
                if (state.is(target.state().getBlock())) return true;
            }
            return false;
        }
    }

    /** One predicted ore block; {@code mined} marks a spot of a buried ore that now reads as air. */
    public record Hit(BlockPos pos, ResourceKey<PlacedFeature> key, boolean mined) {
    }

    /** Calibration samples of one chunk: predicted air-exposed blocks of the calibration ores, and how many hold them. */
    public record Calibration(int exposed, int matched) {
    }

    private final List<OreSpec> specs;
    private final List<OreSpec> calibrationSpecs;
    private final ChunkGenerator generator;
    // The client never receives biome generation settings, so the biome filter reads the vanilla biome by key.
    private final HolderGetter<Biome> vanillaBiomes;

    private OreSimulation(List<OreSpec> specs, List<OreSpec> calibrationSpecs, ChunkGenerator generator,
                          HolderGetter<Biome> vanillaBiomes) {
        this.specs = specs;
        this.calibrationSpecs = calibrationSpecs;
        this.generator = generator;
        this.vanillaBiomes = vanillaBiomes;
    }

    /**
     * Builds the vanilla registries and resolves the given nether features; {@code calibration} lists features used
     * only by {@link #calibrate}. Slow (hundreds of milliseconds), so callers run it off the client thread once and
     * cache the result.
     */
    public static OreSimulation forNether(List<ResourceKey<PlacedFeature>> ores,
                                          List<ResourceKey<PlacedFeature>> calibration) {
        return build(LevelStem.NETHER, ores, calibration);
    }

    /** Same as {@link #forNether} for the overworld. */
    public static OreSimulation forOverworld(List<ResourceKey<PlacedFeature>> ores,
                                             List<ResourceKey<PlacedFeature>> calibration) {
        return build(LevelStem.OVERWORLD, ores, calibration);
    }

    private static OreSimulation build(ResourceKey<LevelStem> stem, List<ResourceKey<PlacedFeature>> ores,
                                       List<ResourceKey<PlacedFeature>> calibration) {
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        ChunkGenerator generator = WorldPresets.createNormalWorldDimensions(lookup)
                .get(stem)
                .orElseThrow(() -> new IllegalStateException("vanilla preset has no " + stem.identifier()))
                .generator();

        // Same call the server makes in ChunkGenerator, with the same biome order, so the
        // feature indices come out identical.
        List<Holder<Biome>> biomes = List.copyOf(generator.getBiomeSource().possibleBiomes());
        List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
                biomes, biome -> biome.value().getGenerationSettings().features(), true);

        HolderGetter<PlacedFeature> placedFeatures = lookup.lookupOrThrow(Registries.PLACED_FEATURE);
        return new OreSimulation(resolve(steps, placedFeatures, ores, false),
                resolve(steps, placedFeatures, calibration, true), generator, lookup.lookupOrThrow(Registries.BIOME));
    }

    private static List<OreSpec> resolve(List<FeatureSorter.StepFeatureData> steps,
                                         HolderGetter<PlacedFeature> placedFeatures,
                                         List<ResourceKey<PlacedFeature>> keys, boolean calibration) {
        List<OreSpec> specs = new ArrayList<>();
        for (ResourceKey<PlacedFeature> key : keys) {
            PlacedFeature placed = placedFeatures.getOrThrow(key).value();
            Feature feature = placed.feature().value();
            if (!(feature instanceof AbstractOreFeature ore)) {
                throw new IllegalStateException(key.identifier() + " is not an ore feature");
            }
            int step = findStep(steps, placed);
            if (step < 0) continue; // not generated in this dimension
            specs.add(new OreSpec(key, placed, step, steps.get(step).indexMapping().applyAsInt(placed),
                    List.copyOf(placed.placement()), ore.size(), ore.discardChanceOnAirExposure(),
                    feature instanceof ScatteredOreFeature, List.copyOf(ore.targetStates()), calibration));
        }
        return List.copyOf(specs);
    }

    private static int findStep(List<FeatureSorter.StepFeatureData> steps, PlacedFeature placed) {
        for (int step = 0; step < steps.size(); step++) {
            if (steps.get(step).features().contains(placed)) return step;
        }
        return -1;
    }

    public List<OreSpec> specs() {
        return specs;
    }

    public List<OreSpec> calibrationSpecs() {
        return calibrationSpecs;
    }

    /**
     * Predicted ore blocks in one chunk, filtered against what the client can see: a spot
     * must still hold base stone (or the ore itself) and pass the air-exposure rule.
     *
     * @param indexOffset added to every feature index, for servers whose chunks were generated by a version with a
     *                    different feature list (see {@link #calibrate})
     */
    public List<BlockPos> simulate(Level level, long worldSeed, int chunkX, int chunkZ, int indexOffset) {
        List<BlockPos> out = new ArrayList<>();
        for (Hit hit : simulateHits(level, worldSeed, chunkX, chunkZ, indexOffset)) {
            if (!hit.mined()) out.add(hit.pos());
        }
        return out;
    }

    /** Like {@link #simulate} but keeps the source feature and the mined-spot flag of each hit. */
    public List<Hit> simulateHits(Level level, long worldSeed, int chunkX, int chunkZ, int indexOffset) {
        return run(level, worldSeed, chunkX, chunkZ, specs, indexOffset);
    }

    /**
     * Checks the replay against the chunk: the calibration ores' predicted blocks that touch air are visible through
     * any anti-xray, so with the right seed, index offset and generation they show the ore. Otherwise they are
     * mostly plain stone.
     */
    public Calibration calibrate(Level level, long worldSeed, int chunkX, int chunkZ, int indexOffset) {
        int exposed = 0;
        int matched = 0;
        for (Hit hit : run(level, worldSeed, chunkX, chunkZ, calibrationSpecs, indexOffset)) {
            if (hit.mined() || !touchesAir(level, hit.pos())) continue;
            exposed++;
            OreSpec spec = specFor(calibrationSpecs, hit.key());
            if (spec != null && spec.isOre(level.getBlockState(hit.pos()))) matched++;
        }
        return new Calibration(exposed, matched);
    }

    private static OreSpec specFor(List<OreSpec> from, ResourceKey<PlacedFeature> key) {
        for (OreSpec spec : from) {
            if (spec.key().equals(key)) return spec;
        }
        return null;
    }

    private static boolean touchesAir(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) return true;
        }
        return false;
    }

    /**
     * Every block the buried ores' veins (ancient debris) claim in one chunk, before any check against the world.
     * Checks the debris replay on its own, apart from {@link #calibrate}: a server can generate one feature from a
     * separate seed (Paper {@code feature-seeds}), so quartz may line up while debris does not. Debris generates
     * buried, but lava does not stop it and players' tunnels expose it, and an exposed block is never hidden by
     * anti-xray; such a block lies in this set with the right replay and almost never otherwise.
     */
    public List<BlockPos> veinBlocks(Level level, long worldSeed, int chunkX, int chunkZ, int indexOffset) {
        RawHits raw = new RawHits();
        run(level, worldSeed, chunkX, chunkZ, specs, indexOffset, raw);
        List<BlockPos> out = new ArrayList<>(raw.size());
        for (Hit hit : raw) out.add(hit.pos());
        return out;
    }

    private List<Hit> run(Level level, long worldSeed, int chunkX, int chunkZ, List<OreSpec> which, int indexOffset) {
        List<Hit> out = new ArrayList<>();
        run(level, worldSeed, chunkX, chunkZ, which, indexOffset, out);
        return out;
    }

    private void run(Level level, long worldSeed, int chunkX, int chunkZ, List<OreSpec> which, int indexOffset,
                     List<Hit> out) {
        WorldGenerationContext context = new WorldGenerationContext(generator, level);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        long decorationSeed = random.setDecorationSeed(worldSeed, minX, minZ);

        for (OreSpec spec : which) {
            if (out instanceof RawHits && !spec.buried()) {
                throw new IllegalArgumentException(spec.key().identifier() + " reads randoms in its world checks");
            }
            random.setFeatureSeed(decorationSeed, spec.index() + indexOffset, spec.step());
            // An unknown modifier leaves the random stream untrustworthy for this spec only.
            placeAll(level, random, context, spec, new BlockPos(minX, 0, minZ), out);
        }
    }

    /**
     * Collects every block a buried ore's vein would claim, before any check against the world. Buried ores read no
     * random in those checks, so skipping them leaves the stream unchanged.
     */
    private static final class RawHits extends ArrayList<Hit> {
    }

    // Vanilla streams positions through the modifiers lazily, so each position runs through
    // every remaining modifier and the ore placement before the next one starts. Recursion
    // over the modifier list reproduces that call order.
    private boolean placeAll(Level level, WorldgenRandom random, WorldGenerationContext context,
                             OreSpec spec, BlockPos origin, List<Hit> out) {
        return apply(level, random, context, spec, 0, origin, out);
    }

    private boolean apply(Level level, WorldgenRandom random, WorldGenerationContext context,
                          OreSpec spec, int modifierIndex, BlockPos pos, List<Hit> out) {
        if (modifierIndex == spec.modifiers().size()) {
            if (spec.scattered()) {
                placeScattered(level, random, spec, pos, out);
            } else {
                placeVein(level, random, spec, pos, out);
            }
            return true;
        }

        PlacementModifier modifier = spec.modifiers().get(modifierIndex);
        int next = modifierIndex + 1;
        if (modifier instanceof CountPlacement count) {
            // Same call vanilla makes; ConstantInt.sample draws nothing from the random.
            IntProvider provider = count.count();
            int n = provider.sample(random);
            for (int i = 0; i < n; i++) {
                if (!apply(level, random, context, spec, next, pos, out)) return false;
            }
            return true;
        }
        if (modifier instanceof RarityFilter rarity) {
            int chance = rarity.chance();
            return random.nextFloat() >= 1.0f / chance || apply(level, random, context, spec, next, pos, out);
        }
        if (modifier instanceof InSquarePlacement) {
            int x = random.nextInt(16) + pos.getX();
            int z = random.nextInt(16) + pos.getZ();
            return apply(level, random, context, spec, next, new BlockPos(x, pos.getY(), z), out);
        }
        if (modifier instanceof HeightRangePlacement range) {
            HeightProvider height = range.height();
            int y = height.sample(random, context);
            return apply(level, random, context, spec, next, new BlockPos(pos.getX(), y, pos.getZ()), out);
        }
        if (modifier instanceof BiomeFilter) {
            // Vanilla's filter: the feature must belong to the biome at the position (in the nether this skips the
            // plain quartz/gold features in basalt deltas, which carry their own variants). The client only knows
            // the biome's key, so the vanilla biome decides; an unknown (custom) biome is let through.
            var biome = level.getBiome(pos).unwrapKey().flatMap(vanillaBiomes::get);
            if (biome.isPresent() && !biome.get().value().getGenerationSettings().hasFeature(spec.placed())) {
                return true;
            }
            return apply(level, random, context, spec, next, pos, out);
        }
        return false;
    }

    /** ScatteredOreFeature.place. */
    private void placeScattered(Level level, WorldgenRandom random, OreSpec spec, BlockPos origin,
                                List<Hit> out) {
        int attempts = random.nextInt(spec.size() + 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < attempts; i++) {
            int range = Math.min(i, 7);
            int dx = Math.round((random.nextFloat() - random.nextFloat()) * range);
            int dy = Math.round((random.nextFloat() - random.nextFloat()) * range);
            int dz = Math.round((random.nextFloat() - random.nextFloat()) * range);
            cursor.setWithOffset(origin, dx, dy, dz);
            tryPlace(level, random, spec, cursor, out);
        }
    }

    /** OreFeature.place + doPlace. */
    private void placeVein(Level level, WorldgenRandom random, OreSpec spec, BlockPos origin,
                           List<Hit> out) {
        int size = spec.size();
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = size / 8.0f;
        int radius = Mth.ceil((size / 16.0f * 2.0f + 1.0f) / 2.0f);
        double x1 = origin.getX() + Math.sin(angle) * spread;
        double x2 = origin.getX() - Math.sin(angle) * spread;
        double z1 = origin.getZ() + Math.cos(angle) * spread;
        double z2 = origin.getZ() - Math.cos(angle) * spread;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        double y2 = origin.getY() + random.nextInt(3) - 2;
        int minX = origin.getX() - Mth.ceil(spread) - radius;
        int minY = origin.getY() - 2 - radius;
        int minZ = origin.getZ() - Mth.ceil(spread) - radius;
        int horizontal = 2 * (Mth.ceil(spread) + radius);
        int vertical = 2 * (2 + radius);
        // Vanilla also requires minY <= ocean-floor heightmap somewhere in the box. The nether
        // is capped by a bedrock roof, so that check always passes there and is skipped.

        double[] blobs = new double[size * 4];
        for (int i = 0; i < size; i++) {
            float t = (float) i / size;
            blobs[i * 4] = Mth.lerp(t, x1, x2);
            blobs[i * 4 + 1] = Mth.lerp(t, y1, y2);
            blobs[i * 4 + 2] = Mth.lerp(t, z1, z2);
            double scale = random.nextDouble() * size / 16.0;
            blobs[i * 4 + 3] = ((Mth.sin((float) Math.PI * t) + 1.0f) * scale + 1.0) / 2.0;
        }
        for (int i = 0; i < size - 1; i++) {
            if (blobs[i * 4 + 3] <= 0.0) continue;
            for (int j = i + 1; j < size; j++) {
                if (blobs[j * 4 + 3] <= 0.0) continue;
                double dx = blobs[i * 4] - blobs[j * 4];
                double dy = blobs[i * 4 + 1] - blobs[j * 4 + 1];
                double dz = blobs[i * 4 + 2] - blobs[j * 4 + 2];
                double dr = blobs[i * 4 + 3] - blobs[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) blobs[j * 4 + 3] = -1.0;
                    else blobs[i * 4 + 3] = -1.0;
                }
            }
        }

        BitSet visited = new BitSet(horizontal * vertical * horizontal);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++) {
            double r = blobs[i * 4 + 3];
            if (r < 0.0) continue;
            double cx = blobs[i * 4];
            double cy = blobs[i * 4 + 1];
            double cz = blobs[i * 4 + 2];
            int fromX = Math.max(Mth.floor(cx - r), minX);
            int fromY = Math.max(Mth.floor(cy - r), minY);
            int fromZ = Math.max(Mth.floor(cz - r), minZ);
            int toX = Math.max(Mth.floor(cx + r), fromX);
            int toY = Math.max(Mth.floor(cy + r), fromY);
            int toZ = Math.max(Mth.floor(cz + r), fromZ);
            for (int x = fromX; x <= toX; x++) {
                double nx = (x + 0.5 - cx) / r;
                if (nx * nx >= 1.0) continue;
                for (int y = fromY; y <= toY; y++) {
                    double ny = (y + 0.5 - cy) / r;
                    if (nx * nx + ny * ny >= 1.0) continue;
                    for (int z = fromZ; z <= toZ; z++) {
                        double nz = (z + 0.5 - cz) / r;
                        if (nx * nx + ny * ny + nz * nz >= 1.0 || level.isOutsideBuildHeight(y)) continue;
                        int bit = x - minX + (y - minY) * horizontal + (z - minZ) * horizontal * vertical;
                        if (visited.get(bit)) continue;
                        visited.set(bit);
                        cursor.set(x, y, z);
                        tryPlace(level, random, spec, cursor, out);
                    }
                }
            }
        }
    }

    /**
     * OreFeature.canPlaceOre against the final world: the feature's own target rule (netherrack, the
     * nether base-stone tag, ...) or the ore itself, since the client sees the finished chunk. The
     * rules used by vanilla ores read no random. A buried ore draws no randoms for its air check, so
     * a spot that now reads as an open, tunnel-like air pocket can be reported as mined without
     * shifting the stream.
     *
     * <p>A buried spot no player can see may show an anti-xray block instead of the real one (Paper hides
     * ores with stone in engine mode 1 and fills hidden spots with random ores in modes 2 and 3), so there
     * any block anti-xray may put is accepted.</p>
     */
    private void tryPlace(Level level, WorldgenRandom random, OreSpec spec, BlockPos.MutableBlockPos pos,
                          List<Hit> out) {
        if (out instanceof RawHits) {
            out.add(new Hit(pos.immutable(), spec.key(), false));
            return;
        }
        BlockState state = level.getBlockState(pos);
        boolean accepted = spec.isOre(state) || matchesTarget(spec, state, pos, random)
                || spec.buried() && ANTI_XRAY_BLOCKS.contains(state.getBlock()) && !touchesAir(level, pos);
        if (!accepted) {
            if (spec.buried() && state.isAir() && looksTunnelled(level, pos)) {
                out.add(new Hit(pos.immutable(), spec.key(), true));
            }
            return;
        }

        float discard = spec.discardChanceOnAirExposure();
        // shouldSkipAirCheck: draws a random only for chances strictly between 0 and 1.
        boolean skipAirCheck = discard <= 0.0f || (discard < 1.0f && random.nextFloat() >= discard);
        if (!skipAirCheck) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockState(pos.relative(direction)).isAir()) return;
            }
        }
        out.add(new Hit(pos.immutable(), spec.key(), false));
    }

    private static boolean matchesTarget(OreSpec spec, BlockState state, BlockPos pos, WorldgenRandom random) {
        for (BlockReplacement target : spec.targets()) {
            if (target.target().test(state, pos, random)) return true;
        }
        return false;
    }

    /**
     * Blocks Paper's default anti-xray settings may show at hidden spots instead of the real block (its hidden-blocks
     * lists; base stone already passes the target rule). Blocks that also lie buried naturally (magma, gravel, soul
     * sand/soil, clay) are left out: accepting them would draw boxes where generation never put the ore.
     */
    private static final Set<Block> ANTI_XRAY_BLOCKS = Set.of(
            Blocks.ANCIENT_DEBRIS, Blocks.BONE_BLOCK, Blocks.GLOWSTONE, Blocks.NETHER_BRICKS,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.RAW_COPPER_BLOCK, Blocks.RAW_IRON_BLOCK, Blocks.OBSIDIAN, Blocks.MOSSY_COBBLESTONE,
            Blocks.CHEST, Blocks.ENDER_CHEST);

    // Natural caves leave open pockets (5-6 air neighbours) while a dug tunnel is 1 block wide.
    private static boolean looksTunnelled(Level level, BlockPos pos) {
        int air = 0;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) air++;
        }
        return air >= 1 && air <= 3;
    }
}
