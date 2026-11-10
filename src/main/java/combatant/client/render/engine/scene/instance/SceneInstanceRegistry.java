/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.instance;

import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import combatant.client.render.engine.scene.lod.SceneLodResolver;
import combatant.client.render.engine.scene.lod.SceneLodSelection;
import combatant.client.render.engine.scene.spatial.SceneSpatialRecord;
import combatant.client.render.engine.scene.spatial.SceneSpatialRegistry;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.engine.scene.visibility.SceneVisibilityService;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;

/**
 * Maintains world-instance state and delegates visibility queries to shared scene services.
 * Immutable mesh and texture resources are not owned by this registry.
 */
public final class SceneInstanceRegistry {
    private static final SceneLodProfile SINGLE_LOD = new SceneLodProfile(new float[0], 0.10f);

    private final SceneSpatialRegistry spatial;
    private final SceneVisibilityService visibility;
    private final Long2ObjectOpenHashMap<SceneAssetInstance<?>> instances = new Long2ObjectOpenHashMap<>();

    private long nextInstanceId = 1L;
    private long frameId = Long.MIN_VALUE;
    private Object worldIdentity;
    private boolean frameOpen;
    private long visibleVisits;
    private long lodChanges;
    private long transformUpdates;
    private long spatialUpdates;
    private long discontinuities;

    public SceneInstanceRegistry(SceneSpatialRegistry spatial, SceneVisibilityService visibility) {
        if (spatial == null) throw new IllegalArgumentException("spatial registry is required");
        if (visibility == null) throw new IllegalArgumentException("visibility service is required");
        this.spatial = spatial;
        this.visibility = visibility;
    }

    /**
     * Switches world ownership synchronously. World identity is by reference, matching Minecraft's
     * level lifecycle. Stale instance references become closed and cannot silently re-register.
     */
    public void beginWorld(Object identity) {
        if (worldIdentity == identity) return;
        clearInstances();
        spatial.beginWorld(identity);
        worldIdentity = identity;
        frameId = Long.MIN_VALUE;
        frameOpen = false;
    }

    /** Advances previous transforms exactly once per rendered frame and clears per-view visibility. */
    public void beginFrame(long nextFrameId) {
        if (frameId == nextFrameId) return;
        frameId = nextFrameId;
        frameOpen = true;
        visibleVisits = 0L;
        lodChanges = 0L;
        transformUpdates = 0L;
        spatialUpdates = 0L;
        discontinuities = 0L;
        for (SceneAssetInstance<?> instance : instances.values()) instance.beginFrame(nextFrameId);
    }

    /** Marks the end of render ownership so tick/update work between frames cannot overwrite motion history. */
    public void endFrame(long completedFrameId) {
        if (frameId != completedFrameId) return;
        frameOpen = false;
        for (SceneAssetInstance<?> instance : instances.values()) instance.endFrame(completedFrameId);
    }

    public <T> SceneAssetInstance<T> register(T asset,
                                               AABB localBounds,
                                               Matrix4fc worldTransform,
                                               SceneDrawClass drawClass,
                                               SceneLodProfile lodProfile) {
        long id = allocateInstanceId();
        SceneAssetInstance<T> instance = new SceneAssetInstance<>(
                this, id, asset, localBounds, worldTransform, drawClass,
                lodProfile == null ? SINGLE_LOD : lodProfile
        );
        instance.attach(spatial);
        instances.put(id, instance);
        if (frameOpen) instance.beginFrame(frameId);
        return instance;
    }

    public <T> SceneAssetInstance<T> register(T asset, AABB localBounds, Matrix4fc worldTransform) {
        return register(asset, localBounds, worldTransform, SceneDrawClass.CUSTOM, SINGLE_LOD);
    }

    /**
     * Cheap candidate traversal. With SECTION_ONLY this performs only section/cell rejection and
     * payload filtering: no per-object frustum projection or LOD projection math is executed.
     */
    public boolean visitCandidates(SceneViewContext view,
                                   SceneVisibilityMode mode,
                                   SceneInstanceCandidateVisitor visitor) {
        if (view == null || visitor == null) return true;
        beginFrame(view.frameId());
        return visibility.visitVisible(view, mode, record -> {
            if (!(record.payload() instanceof SceneAssetInstance<?> instance)) return true;
            if (instances.get(instance.instanceId()) != instance || instance.isClosed()) return true;
            return visitor.visit(instance);
        });
    }

    /** Traverses visible asset instances and resolves screen-space LOD only for survivors. */
    public boolean visitVisible(SceneViewContext view,
                                SceneVisibilityMode mode,
                                SceneInstanceVisitor visitor) {
        if (view == null || visitor == null) return true;
        beginFrame(view.frameId());
        return visibility.visitVisible(view, mode, record -> visitSpatialRecord(view, record, visitor));
    }

    /** Traverses live instances of one semantic draw class without world-space culling. */
    public boolean visitDrawClass(SceneDrawClass drawClass, SceneInstanceCandidateVisitor visitor) {
        if (drawClass == null || visitor == null) return true;
        for (SceneAssetInstance<?> instance : instances.values()) {
            if (instance == null || instance.isClosed() || instance.drawClass() != drawClass) continue;
            if (!visitor.visit(instance)) return false;
        }
        return true;
    }

    public SceneAssetInstance<?> instance(long instanceId) {
        return instances.get(instanceId);
    }

    public boolean contains(long instanceId) {
        return instances.containsKey(instanceId);
    }

    public int size() { return instances.size(); }
    public long frameId() { return frameId; }
    public Object worldIdentity() { return worldIdentity; }

    public SceneInstanceStatsSnapshot statsSnapshot() {
        return new SceneInstanceStatsSnapshot(
                instances.size(), frameId, visibleVisits, lodChanges,
                transformUpdates, spatialUpdates, discontinuities
        );
    }

    public void clear() {
        clearInstances();
        worldIdentity = null;
        frameId = Long.MIN_VALUE;
        frameOpen = false;
    }

    boolean frameOpen() { return frameOpen; }

    void onTransformChanged(SceneAssetInstance<?> instance, boolean discontinuity) {
        if (instance == null || instance.isClosed() || !instances.containsKey(instance.instanceId())) return;
        transformUpdates++;
        if (discontinuity) discontinuities++;
        if (spatial.updateBounds(instance.spatialHandle(), instance.worldBounds())) spatialUpdates++;
    }

    void unregister(SceneAssetInstance<?> instance) {
        if (instance == null) return;
        SceneAssetInstance<?> owned = instances.remove(instance.instanceId());
        if (owned == null) {
            // An instance invalidated by world clear may still receive close() from feature code.
            instance.disposeWithoutOwnerCallback(spatial);
            return;
        }
        owned.disposeWithoutOwnerCallback(spatial);
    }

    private boolean visitSpatialRecord(SceneViewContext view,
                                       SceneSpatialRecord record,
                                       SceneInstanceVisitor visitor) {
        if (!(record.payload() instanceof SceneAssetInstance<?> instance)) return true;
        if (instances.get(instance.instanceId()) != instance || instance.isClosed()) return true;

        int previousLod = instance.previousLod(view.type());
        SceneLodSelection lod = SceneLodResolver.resolve(
                instance.lodProfile(), view, record, previousLod, instance.lodBiasStops()
        );
        instance.markVisible(view.type(), view.frameId(), lod);
        visibleVisits++;
        if (lod.changed()) lodChanges++;
        return visitor.visit(instance, instance.viewState(view.type()));
    }

    private void clearInstances() {
        if (instances.isEmpty()) return;
        SceneAssetInstance<?>[] copy = new SceneAssetInstance<?>[instances.size()];
        int cursor = 0;
        for (SceneAssetInstance<?> instance : instances.values()) copy[cursor++] = instance;
        instances.clear();
        for (SceneAssetInstance<?> instance : copy) instance.disposeWithoutOwnerCallback(spatial);
    }

    private long allocateInstanceId() {
        long start = nextInstanceId;
        do {
            long id = nextInstanceId++;
            if (nextInstanceId <= 0L) nextInstanceId = 1L;
            if (id > 0L && !instances.containsKey(id)) return id;
        } while (nextInstanceId != start);
        throw new IllegalStateException("Scene instance id space exhausted");
    }
}
