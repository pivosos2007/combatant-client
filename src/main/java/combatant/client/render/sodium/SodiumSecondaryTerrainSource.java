/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import combatant.client.mixins.sodium.SodiumRenderSectionManagerAccessor;
import combatant.client.mixins.sodium.SodiumSortedRenderListsInvoker;
import combatant.client.render.engine.deferred.DeferredSecondaryView;
import combatant.client.render.engine.deferred.DeferredViewFamily;
import combatant.client.util.logging.DebugLog;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds view-local Sodium terrain lists from already uploaded section storage.
 *
 * <p>Secondary cameras must never reuse Sodium's primary occlusion tree: visibility from the
 * player camera is not visibility from a shadow/probe camera. Instead Combatant snapshots only
 * immutable section topology on the render thread, performs conservative region/section frustum
 * tests on a low-priority worker, and validates mutable section lifecycle state back on the render
 * thread before submission.</p>
 *
 * <p>The worker is strictly opportunistic. Rendering never waits for it. If an exact result for a
 * view is not ready, the same conservative cull is evaluated synchronously over the immutable
 * snapshot. Stable/snapped secondary views then reuse completed async results on following frames.</p>
 */
public final class SodiumSecondaryTerrainSource {
    private static final float SECTION_EXTENT = 16.0f;
    private static final float CULL_PADDING = 1.0f;
    private static final int MAX_CACHED_VIEWS = 64;
    private static final int MAX_PENDING_VIEWS = 32;

    private static final Object CACHE_LOCK = new Object();
    private static final AtomicLong CULL_EPOCH = new AtomicLong();
    private static final ExecutorService CULL_EXECUTOR = Executors.newSingleThreadExecutor(new CullThreadFactory());
    private static final LinkedHashMap<ViewKey, CullResult> READY = new LinkedHashMap<>(32, 0.75f, true);
    private static final LinkedHashMap<ViewKey, CompletableFuture<CullResult>> PENDING = new LinkedHashMap<>();

    private static volatile TopologySnapshot topology = TopologySnapshot.EMPTY;

    private SodiumSecondaryTerrainSource() {
    }

    public static SortedRenderLists buildRenderLists(RenderSectionManager manager, DeferredSecondaryView view) {
        if (manager == null) throw new IllegalArgumentException("manager");
        if (view == null) throw new IllegalArgumentException("view");

        SectionStorage storage = ((SodiumRenderSectionManagerAccessor) manager).combatant$getRenderSections();
        if (!(storage instanceof SodiumSectionStorageView storageView)) {
            throw new IllegalStateException("Unsupported Sodium section storage: "
                    + (storage == null ? "null" : storage.getClass().getName()));
        }

        TopologySnapshot snapshot = ensureTopology(storage, storageView);
        ViewKey key = ViewKey.of(view, snapshot.revision());
        CullResult result = ready(key);
        if (result == null) {
            schedule(key, snapshot, view);
            // Never block the render thread on async visibility. The fallback reads only immutable
            // snapshot data and is therefore deterministic and independent from Sodium mutation.
            result = cull(snapshot, view, CULL_EPOCH.get());
        }

        if (view.family() == DeferredViewFamily.SHADOW_CASCADE && result != null && !result.cancelled()) {
            String state = view.index() + ":" + result.visibleSections().length;
            DebugLog.infoOnChange(
                    "combatant.deferred.shadow-cull." + view.index(),
                    state,
                    "[Deferred][ShadowCull] cascade=%d sections=%d range=%.2f..%.2f",
                    view.index(), result.visibleSections().length, view.nearPlane(), view.farPlane()
            );
        }

        return buildLists(result, snapshot);
    }

    /**
     * Opportunistically queues exact culling without requiring an immediate render. Producers may
     * call this when a stable secondary view is registered earlier in the frame.
     */
    public static void prefetch(RenderSectionManager manager, DeferredSecondaryView view) {
        if (manager == null || view == null) return;
        SectionStorage storage = ((SodiumRenderSectionManagerAccessor) manager).combatant$getRenderSections();
        if (!(storage instanceof SodiumSectionStorageView storageView)) return;
        TopologySnapshot snapshot = ensureTopology(storage, storageView);
        ViewKey key = ViewKey.of(view, snapshot.revision());
        if (ready(key) == null) schedule(key, snapshot, view);
    }

    public static void clearCachedBatches(SortedRenderLists lists) {
        if (lists == null) return;
        var iterator = lists.iterator(false);
        while (iterator.hasNext()) {
            RenderRegion region = iterator.next().getRegion();
            region.clearCachedBatchFor(DefaultTerrainRenderPasses.SOLID);
            region.clearCachedBatchFor(DefaultTerrainRenderPasses.CUTOUT);
        }
    }

    /** Invalidate async work on world/backend reset without shutting down the shared daemon. */
    public static void invalidate() {
        CULL_EPOCH.incrementAndGet();
        topology = TopologySnapshot.EMPTY;
        synchronized (CACHE_LOCK) {
            for (CompletableFuture<CullResult> future : PENDING.values()) future.cancel(false);
            PENDING.clear();
            READY.clear();
        }
    }

    private static TopologySnapshot ensureTopology(SectionStorage storage, SodiumSectionStorageView storageView) {
        long revision = storageView.combatant$structuralRevision();
        TopologySnapshot current = topology;
        if (current.storage() == storage && current.revision() == revision) return current;

        // This method is called from Sodium's render path. Snapshot construction intentionally
        // stays on that thread; the worker never iterates or queries mutable Sodium section state.
        TopologySnapshot rebuilt = snapshot(storage, storageView, revision);
        topology = rebuilt;
        CULL_EPOCH.incrementAndGet();
        synchronized (CACHE_LOCK) {
            for (CompletableFuture<CullResult> future : PENDING.values()) future.cancel(false);
            PENDING.clear();
            READY.clear();
        }
        return rebuilt;
    }

    private static TopologySnapshot snapshot(SectionStorage storage,
                                             SodiumSectionStorageView storageView,
                                             long revision) {
        ArrayList<SectionEntry> sections = new ArrayList<>();
        IdentityHashMap<RenderRegion, RegionBuilder> regions = new IdentityHashMap<>();
        for (RenderSection section : storageView.combatant$sections()) {
            if (section == null) continue;
            RenderRegion region = section.getRegion();
            int flatIndex = sections.size();
            SectionEntry entry = new SectionEntry(
                    section,
                    region,
                    section.getSectionIndex(),
                    section.getOriginX(),
                    section.getOriginY(),
                    section.getOriginZ()
            );
            sections.add(entry);
            regions.computeIfAbsent(region, RegionBuilder::new).add(flatIndex, entry);
        }

        ArrayList<RegionSnapshot> regionSnapshots = new ArrayList<>(regions.size());
        for (RegionBuilder builder : regions.values()) regionSnapshots.add(builder.build());
        regionSnapshots.sort(Comparator
                .comparingInt(RegionSnapshot::chunkY)
                .thenComparingInt(RegionSnapshot::chunkZ)
                .thenComparingInt(RegionSnapshot::chunkX));
        return new TopologySnapshot(storage, revision, List.copyOf(sections), List.copyOf(regionSnapshots));
    }

    private static CullResult ready(ViewKey key) {
        synchronized (CACHE_LOCK) {
            return READY.get(key);
        }
    }

    private static void schedule(ViewKey key, TopologySnapshot snapshot, DeferredSecondaryView view) {
        synchronized (CACHE_LOCK) {
            if (READY.containsKey(key) || PENDING.containsKey(key)) return;
            if (PENDING.size() >= MAX_PENDING_VIEWS) return;

            long epoch = CULL_EPOCH.get();
            // DeferredSecondaryView is immutable-by-contract and its matrix accessors return copies,
            // so capture an independent copy for the worker as well.
            DeferredSecondaryView workerView = new DeferredSecondaryView(
                    view.family(), view.index(), view.id(), view.view(), view.projection(), view.origin(),
                    view.targetLayer(), view.viewportX(), view.viewportY(), view.viewportWidth(),
                    view.viewportHeight(), view.nearPlane(), view.farPlane()
            );
            CompletableFuture<CullResult> future = CompletableFuture.supplyAsync(
                    () -> cull(snapshot, workerView, epoch), CULL_EXECUTOR
            );
            PENDING.put(key, future);
            future.whenComplete((result, error) -> {
                synchronized (CACHE_LOCK) {
                    PENDING.remove(key);
                    if (error != null || result == null || result.cancelled()
                            || CULL_EPOCH.get() != result.epoch()) return;
                    READY.put(key, result);
                    trimReadyCache();
                }
            });
        }
    }

    private static CullResult cull(TopologySnapshot snapshot, DeferredSecondaryView view, long epoch) {
        Matrix4f viewProjection = view.viewProjection();
        boolean directionalShadow = view.family() == DeferredViewFamily.SHADOW_CASCADE;
        FrustumIntersection frustum = directionalShadow ? null : new FrustumIntersection(viewProjection);
        double cameraX = view.origin().x;
        double cameraY = view.origin().y;
        double cameraZ = view.origin().z;
        IntAccumulator visible = new IntAccumulator(Math.min(snapshot.sections().size(), 1024));

        for (RegionSnapshot region : snapshot.regions()) {
            if (CULL_EPOCH.get() != epoch) return CullResult.cancelled(epoch);
            boolean regionVisible = directionalShadow
                    ? testShadowCascadeAabbXY(viewProjection,
                            region.minX(), region.minY(), region.minZ(),
                            region.maxX(), region.maxY(), region.maxZ(),
                            cameraX, cameraY, cameraZ)
                    : testWorldAabb(frustum,
                            region.minX(), region.minY(), region.minZ(),
                            region.maxX(), region.maxY(), region.maxZ(),
                            cameraX, cameraY, cameraZ);
            if (!regionVisible) continue;

            for (int sectionIndex : region.sectionIndices()) {
                SectionEntry section = snapshot.sections().get(sectionIndex);
                double minX = section.originX() - CULL_PADDING;
                double minY = section.originY() - CULL_PADDING;
                double minZ = section.originZ() - CULL_PADDING;
                double maxX = section.originX() + SECTION_EXTENT + CULL_PADDING;
                double maxY = section.originY() + SECTION_EXTENT + CULL_PADDING;
                double maxZ = section.originZ() + SECTION_EXTENT + CULL_PADDING;
                boolean sectionVisible = directionalShadow
                        ? testShadowCascadeAabbXY(viewProjection,
                                minX, minY, minZ, maxX, maxY, maxZ,
                                cameraX, cameraY, cameraZ)
                        : testWorldAabb(frustum,
                                minX, minY, minZ, maxX, maxY, maxZ,
                                cameraX, cameraY, cameraZ);
                if (sectionVisible) visible.add(sectionIndex);
            }
        }
        return new CullResult(epoch, visible.toArray(), false);
    }

    private static boolean testWorldAabb(FrustumIntersection frustum,
                                         double minX, double minY, double minZ,
                                         double maxX, double maxY, double maxZ,
                                         double cameraX, double cameraY, double cameraZ) {
        return frustum.testAab(
                (float) (minX - cameraX), (float) (minY - cameraY), (float) (minZ - cameraZ),
                (float) (maxX - cameraX), (float) (maxY - cameraY), (float) (maxZ - cameraZ)
        );
    }

    /**
     * Conservative directional-shadow cull for orthographic cascades.
     *
     * <p>The generic JOML {@link FrustumIntersection} extractor assumes the conventional clip
     * depth layout. Combatant shadow projections are reversed-Z and may use zero-to-one clip
     * depth, so feeding those matrices into the generic extractor can reject valid terrain on
     * outer cascades. For directional shadow casters the critical finite coverage is the
     * orthographic light-plane XY rectangle; caster depth is deliberately conservative because
     * the projection already owns a bounded caster band.</p>
     */
    private static boolean testShadowCascadeAabbXY(Matrix4f viewProjection,
                                                   double minX, double minY, double minZ,
                                                   double maxX, double maxY, double maxZ,
                                                   double cameraX, double cameraY, double cameraZ) {
        float centerX = (float) (((minX + maxX) * 0.5) - cameraX);
        float centerY = (float) (((minY + maxY) * 0.5) - cameraY);
        float centerZ = (float) (((minZ + maxZ) * 0.5) - cameraZ);
        float extentX = (float) ((maxX - minX) * 0.5);
        float extentY = (float) ((maxY - minY) * 0.5);
        float extentZ = (float) ((maxZ - minZ) * 0.5);

        float clipCenterX = viewProjection.m00() * centerX
                + viewProjection.m10() * centerY
                + viewProjection.m20() * centerZ
                + viewProjection.m30();
        float clipCenterY = viewProjection.m01() * centerX
                + viewProjection.m11() * centerY
                + viewProjection.m21() * centerZ
                + viewProjection.m31();

        float clipExtentX = Math.abs(viewProjection.m00()) * extentX
                + Math.abs(viewProjection.m10()) * extentY
                + Math.abs(viewProjection.m20()) * extentZ;
        float clipExtentY = Math.abs(viewProjection.m01()) * extentX
                + Math.abs(viewProjection.m11()) * extentY
                + Math.abs(viewProjection.m21()) * extentZ;

        // One section of normalized padding prevents section-edge churn as a snapped cascade moves
        // by a texel. Z is intentionally not rejected here; reversed/zero-to-one depth extraction
        // was the source of false negatives, while the loaded Sodium topology already bounds work.
        final float ndcPadding = 0.02f;
        return clipCenterX + clipExtentX >= -1.0f - ndcPadding
                && clipCenterX - clipExtentX <= 1.0f + ndcPadding
                && clipCenterY + clipExtentY >= -1.0f - ndcPadding
                && clipCenterY - clipExtentY <= 1.0f + ndcPadding;
    }

    private static SortedRenderLists buildLists(CullResult result, TopologySnapshot snapshot) {
        Map<RenderRegion, ChunkRenderList> byRegion = new IdentityHashMap<>();
        if (result != null && !result.cancelled()) {
            for (int flatIndex : result.visibleSections()) {
                if (flatIndex < 0 || flatIndex >= snapshot.sections().size()) continue;
                SectionEntry entry = snapshot.sections().get(flatIndex);
                RenderSection section = entry.section();
                // Mutable lifecycle flags are checked only on the render thread.
                if (section == null || section.isDisposed() || !section.isBuilt()) continue;
                RenderRegion region = entry.region();
                ChunkRenderList list = byRegion.computeIfAbsent(region, ChunkRenderList::new);
                list.add(entry.sectionIndex());
            }
        }

        ArrayList<ChunkRenderList> ordered = new ArrayList<>(byRegion.values());
        ordered.sort(Comparator
                .comparingInt((ChunkRenderList list) -> list.getRegion().getChunkY())
                .thenComparingInt(list -> list.getRegion().getChunkZ())
                .thenComparingInt(list -> list.getRegion().getChunkX()));
        ObjectArrayList<ChunkRenderList> fast = new ObjectArrayList<>(ordered.size());
        fast.addAll(ordered);
        SortedRenderLists lists = SodiumSortedRenderListsInvoker.combatant$create(fast);

        // MultiDrawBatch is cached by RenderRegion + TerrainRenderPass, not by camera. Invalidate
        // before the first secondary draw and again when the secondary scope closes.
        clearCachedBatches(lists);
        return lists;
    }

    private static void trimReadyCache() {
        while (READY.size() > MAX_CACHED_VIEWS) {
            ViewKey eldest = READY.keySet().iterator().next();
            READY.remove(eldest);
        }
    }

    private record TopologySnapshot(SectionStorage storage,
                                    long revision,
                                    List<SectionEntry> sections,
                                    List<RegionSnapshot> regions) {
        private static final TopologySnapshot EMPTY = new TopologySnapshot(null, Long.MIN_VALUE, List.of(), List.of());
    }

    private record SectionEntry(RenderSection section,
                                RenderRegion region,
                                int sectionIndex,
                                int originX,
                                int originY,
                                int originZ) {
    }

    private record RegionSnapshot(int chunkX,
                                  int chunkY,
                                  int chunkZ,
                                  double minX,
                                  double minY,
                                  double minZ,
                                  double maxX,
                                  double maxY,
                                  double maxZ,
                                  int[] sectionIndices) {
    }

    private static final class RegionBuilder {
        private final RenderRegion region;
        private final IntAccumulator sectionIndices = new IntAccumulator(32);
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private RegionBuilder(RenderRegion region) {
            this.region = region;
        }

        private void add(int flatIndex, SectionEntry section) {
            sectionIndices.add(flatIndex);
            minX = Math.min(minX, section.originX() - CULL_PADDING);
            minY = Math.min(minY, section.originY() - CULL_PADDING);
            minZ = Math.min(minZ, section.originZ() - CULL_PADDING);
            maxX = Math.max(maxX, section.originX() + SECTION_EXTENT + CULL_PADDING);
            maxY = Math.max(maxY, section.originY() + SECTION_EXTENT + CULL_PADDING);
            maxZ = Math.max(maxZ, section.originZ() + SECTION_EXTENT + CULL_PADDING);
        }

        private RegionSnapshot build() {
            return new RegionSnapshot(
                    region.getChunkX(), region.getChunkY(), region.getChunkZ(),
                    minX, minY, minZ, maxX, maxY, maxZ, sectionIndices.toArray()
            );
        }
    }

    private record CullResult(long epoch, int[] visibleSections, boolean cancelled) {
        private static CullResult cancelled(long epoch) {
            return new CullResult(epoch, new int[0], true);
        }
    }

    private record ViewKey(String id, int family, long revision, long fingerprint) {
        private static ViewKey of(DeferredSecondaryView view, long revision) {
            long hash = 0xcbf29ce484222325L;
            hash = mix(hash, view.family().ordinal());
            hash = mix(hash, view.index());
            hash = mix(hash, Double.doubleToLongBits(view.origin().x));
            hash = mix(hash, Double.doubleToLongBits(view.origin().y));
            hash = mix(hash, Double.doubleToLongBits(view.origin().z));
            float[] matrix = new float[16];
            view.viewProjection().get(matrix);
            for (float value : matrix) hash = mix(hash, Float.floatToIntBits(value));
            return new ViewKey(view.id(), view.family().ordinal(), revision, hash);
        }

        private static long mix(long hash, long value) {
            hash ^= value;
            return hash * 0x100000001b3L;
        }
    }

    private static final class IntAccumulator {
        private int[] values;
        private int size;

        private IntAccumulator(int initialCapacity) {
            values = new int[Math.max(8, initialCapacity)];
        }

        private void add(int value) {
            if (size == values.length) values = Arrays.copyOf(values, values.length * 2);
            values[size++] = value;
        }

        private int[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }

    private static final class CullThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Combatant Secondary Culling");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 2));
            return thread;
        }
    }
}
