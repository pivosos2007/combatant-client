/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.framegraph.FrameGraphAccess;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalPlan;
import combatant.client.render.engine.framegraph.FrameGraphResourceKind;
import combatant.client.render.engine.framegraph.FrameGraphResourceLifetime;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.resource.FrameGraphPhysicalResource;
import combatant.client.render.engine.rhi.resource.FrameGraphPhysicalResourcePool;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Non-owning logical bindings for deferred resources.
 *
 * <p>Explicit maps contain true external targets and the small set of legacy source-owned
 * resources. Graph-managed resources resolve directly through {@link FrameGraphPhysicalResourcePool};
 * consumers never observe physical allocation IDs.</p>
 */
public final class DeferredResourceBindings {
    private final EnumMap<DeferredResource, GpuTextureView> textures = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, RhiStorageBuffer> buffers = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, RhiStorageImage> images = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, RhiStorageVolume> volumes = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, DeferredResourceProvenance> provenance = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, Long> provenanceGenerations = new EnumMap<>(DeferredResource.class);
    private final Map<Integer, AliasState> activeAliases = new HashMap<>();
    private final EnumSet<DeferredResource> graphAccessedThisFrame = EnumSet.noneOf(DeferredResource.class);

    private @Nullable Object graphScope;
    private @Nullable CombatantRhi graphOwner;
    private FrameGraphPhysicalPlan graphPlan = FrameGraphPhysicalPlan.EMPTY;
    private long frameId = Long.MIN_VALUE;
    private long historyEpoch = Long.MIN_VALUE;
    private int outputWidth;
    private int outputHeight;

    public DeferredResourceBindings() {
    }

    /**
     * Source-compatibility constructor. DeferredResourceAllocator no longer owns production
     * resources; passing one here has no ownership effect.
     */
    @Deprecated(forRemoval = true)
    public DeferredResourceBindings(DeferredResourceAllocator ignored) {
        if (ignored == null) throw new IllegalArgumentException("allocator");
    }

    public void beginFrame(long frameId) {
        beginFrame(frameId, historyEpoch);
    }

    public void beginFrame(long frameId, long historyEpoch) {
        if (this.frameId == frameId && this.historyEpoch == historyEpoch) return;
        this.frameId = frameId;
        this.historyEpoch = historyEpoch;
        activeAliases.clear();
        graphAccessedThisFrame.clear();
        provenance.clear();
        clearBindings();
    }

    /** Temporal history semantics are tracked by DeferredTemporalHistoryRegistry, not allocation existence. */
    public void setHistoryEpoch(long historyEpoch) {
        if (this.historyEpoch == historyEpoch) return;
        this.historyEpoch = historyEpoch;
        for (DeferredResource resource : DeferredResource.values()) {
            if (resource.key().lifetime() != FrameGraphResourceLifetime.PERSISTENT) continue;
            textures.remove(resource);
            buffers.remove(resource);
            images.remove(resource);
            volumes.remove(resource);
        }
    }

    /** Drops every non-owning binding after runtime/device/world teardown. */
    public void reset() {
        frameId = Long.MIN_VALUE;
        historyEpoch = Long.MIN_VALUE;
        outputWidth = 0;
        outputHeight = 0;
        provenance.clear();
        provenanceGenerations.clear();
        clearBindings();
        detachFrameGraph();
    }

    /** Output extent used by OUTPUT-resolution temporal/post resources. */
    public void setOutputResolution(int width, int height) {
        outputWidth = Math.max(0, width);
        outputHeight = Math.max(0, height);
    }

    void attachFrameGraph(Object scope, CombatantRhi owner, FrameGraphPhysicalPlan plan) {
        if (scope == null) throw new IllegalArgumentException("scope");
        if (owner == null) throw new IllegalArgumentException("owner");
        if (plan == null) throw new IllegalArgumentException("plan");
        if (graphScope != scope || graphOwner != owner || graphPlan != plan) activeAliases.clear();
        graphScope = scope;
        graphOwner = owner;
        graphPlan = plan;
    }

    void detachFrameGraph() {
        graphScope = null;
        graphOwner = null;
        graphPlan = FrameGraphPhysicalPlan.EMPTY;
        activeAliases.clear();
        graphAccessedThisFrame.clear();
    }

    private void clearBindings() {
        textures.clear();
        buffers.clear();
        images.clear();
        volumes.clear();
    }

    public void bindTexture(DeferredResource resource, @Nullable GpuTextureView view) {
        requireTextureResource(resource);
        rejectDuplicateGraphOwnership(resource, view);
        if (view == null) textures.remove(resource); else textures.put(resource, view);
    }

    public void bindBuffer(DeferredResource resource, @Nullable RhiStorageBuffer buffer) {
        if (resource == null || resource.key().kind() != FrameGraphResourceKind.BUFFER) {
            throw new IllegalArgumentException("Not a buffer resource: " + resource);
        }
        rejectDuplicateGraphOwnership(resource, buffer);
        if (buffer == null) buffers.remove(resource); else buffers.put(resource, buffer);
    }

    public void bindStorageImage(DeferredResource resource, @Nullable RhiStorageImage image) {
        requireTextureResource(resource);
        rejectDuplicateGraphOwnership(resource, image);
        if (image == null) {
            images.remove(resource);
        } else {
            images.put(resource, image);
            textures.putIfAbsent(resource, image.view());
        }
    }

    public void bindStorageVolume(DeferredResource resource, @Nullable RhiStorageVolume volume) {
        requireVolumeResource(resource);
        rejectDuplicateGraphOwnership(resource, volume);
        if (volume == null) volumes.remove(resource); else volumes.put(resource, volume);
    }

    public @Nullable GpuTextureView texture(DeferredResource resource) {
        GpuTextureView explicit = textures.get(resource);
        if (explicit != null) return explicit;
        FrameGraphPhysicalResource physical = graphPhysical(resource);
        if (physical == null || physical.descriptor().kind() != FrameGraphResourceKind.TEXTURE) return null;
        return physical.textureView();
    }

    /** Explicit external/legacy texture only; descriptor planning must not depend on a graph allocation. */
    @Nullable GpuTextureView explicitTexture(DeferredResource resource) {
        return textures.get(resource);
    }

    public @Nullable RhiStorageBuffer buffer(DeferredResource resource) {
        RhiStorageBuffer explicit = buffers.get(resource);
        if (explicit != null) return explicit;
        FrameGraphPhysicalResource physical = graphPhysical(resource);
        return physical == null ? null : physical.storageBuffer();
    }

    public @Nullable RhiStorageImage storageImage(DeferredResource resource) {
        RhiStorageImage explicit = images.get(resource);
        if (explicit != null) return explicit;
        FrameGraphPhysicalResource physical = graphPhysical(resource);
        return physical == null ? null : physical.storageImage();
    }

    public @Nullable RhiStorageVolume storageVolume(DeferredResource resource) {
        RhiStorageVolume explicit = volumes.get(resource);
        if (explicit != null) return explicit;
        FrameGraphPhysicalResource physical = graphPhysical(resource);
        return physical == null ? null : physical.storageVolume();
    }

    public boolean isBound(DeferredResource resource) {
        if (resource == null) return false;
        return textures.containsKey(resource) || buffers.containsKey(resource)
                || images.containsKey(resource) || volumes.containsKey(resource)
                || graphPhysical(resource) != null;
    }

    /**
     * Logical validity means a producer has made this logical identity available. Merely having a
     * reusable GPU allocation is insufficient, which is essential when transient aliases reuse it.
     */
    public boolean isValid(DeferredResource resource) {
        if (resource == null) return false;
        FrameGraphPhysicalPlan.LogicalResourcePlan logical = graphLogical(resource);
        if (logical != null && logical.physicalAllocationId() >= 0) {
            FrameGraphPhysicalResourcePool pool = graphPool();
            return pool != null && pool.valid(graphScope, resource.key());
        }
        return textures.containsKey(resource) || buffers.containsKey(resource)
                || images.containsKey(resource) || volumes.containsKey(resource);
    }

    /** Graph persistent resources remain physically bound across frames without lazy re-allocation. */
    public boolean bindExisting(DeferredResource resource) {
        return bindExisting(resource, DeferredRuntimeConfig.current());
    }

    public boolean bindExisting(DeferredResource resource, DeferredRuntimeConfig.Snapshot settings) {
        if (resource == null) return false;
        if (graphLogical(resource) != null) return isBound(resource) && isValid(resource);
        return isBound(resource) && isValid(resource);
    }

    /** Semantic producer result for diagnostics. This is intentionally separate from logical validity. */
    public void publishStatus(DeferredResource resource,
                              String producerPassId,
                              DeferredResourceStatus status,
                              String reasonCode,
                              String message,
                              DeferredResource upstreamResource) {
        if (resource == null) return;
        long generation = provenanceGenerations.merge(resource, 1L, Long::sum);
        provenance.put(resource, new DeferredResourceProvenance(
                resource, producerPassId, frameId, generation, status, reasonCode, message, upstreamResource
        ));
    }

    /** Returns the current-frame semantic producer result, if one has been published. */
    public @Nullable DeferredResourceProvenance provenance(DeferredResource resource) {
        return resource == null ? null : provenance.get(resource);
    }

    public Map<DeferredResource, DeferredResourceProvenance> provenanceSnapshot() {
        return Map.copyOf(provenance);
    }

    /**
     * Publishes PRODUCED only when the pass itself did not already publish a more specific result
     * (for example FALLBACK after a provider exception).
     */
    void publishProducedIfAbsentForPass(DeferredResource resource, String producerPassId) {
        if (resource == null) return;
        DeferredResourceProvenance current = provenance.get(resource);
        if (current != null && current.frameId() == frameId
                && current.producerPassId().equals(producerPassId)) return;
        publishStatus(resource, producerPassId, DeferredResourceStatus.PRODUCED, "", "", null);
    }

    /** Called only after a producer completed successfully. */
    public void markWritten(DeferredResource resource) {
        FrameGraphPhysicalPlan.LogicalResourcePlan logical = graphLogical(resource);
        if (resource == null || logical == null || logical.physicalAllocationId() < 0) return;
        FrameGraphPhysicalResourcePool pool = graphPool();
        if (pool == null || graphScope == null) {
            throw new IllegalStateException("Deferred frame-graph resource is not attached: " + resource);
        }
        pool.markProduced(graphScope, resource.key());
    }

    /**
     * Compatibility name retained for pass sources. This no longer allocates anything: compile-time
     * descriptor lowering and the graph pool must already have materialized the physical texture.
     */
    public void ensureTexture(DeferredResource resource, CombatantRhi rhi) {
        ensureTexture(resource, rhi, DeferredRuntimeConfig.current());
    }

    public void ensureTexture(DeferredResource resource, CombatantRhi rhi, DeferredRuntimeConfig.Snapshot settings) {
        if (resource == null || resource.key().kind() != FrameGraphResourceKind.TEXTURE) {
            throw new IllegalArgumentException("Deferred resource is not a texture: " + resource);
        }
        ensurePhysicalResource(resource);
    }

    void ensurePhysicalResource(DeferredResource resource) {
        if (!isGraphManaged(resource)) {
            throw new IllegalArgumentException("Deferred resource has no graph-owned physical descriptor: " + resource);
        }
        if (isBound(resource)) return;
        throw new IllegalStateException("Graph-owned deferred resource was not materialized: " + resource.key().name());
    }

    boolean isGraphManaged(DeferredResource resource) {
        FrameGraphPhysicalPlan.LogicalResourcePlan logical = graphLogical(resource);
        return logical != null && logical.physicalAllocationId() >= 0;
    }

    /**
     * Activates an aliased logical identity immediately before its real GPU access. If the physical
     * allocation was previously used by another logical resource, order the reuse and invalidate
     * the retired logical identity before the new producer can write it.
     */
    void prepareGraphAccess(DeferredResource resource,
                            FrameGraphAccess access,
                            RhiResourceBarrier.Stage stage,
                            CombatantRhi rhi) {
        if (resource == null || access == null || stage == null || rhi == null) return;
        FrameGraphPhysicalPlan.LogicalResourcePlan logical = graphLogical(resource);
        if (logical == null || logical.physicalAllocationId() < 0) return;
        graphAccessedThisFrame.add(resource);
        FrameGraphPhysicalPlan.PhysicalAllocationPlan allocation = graphPlan.physical(logical.physicalAllocationId());
        if (allocation == null || allocation.logicalResources().size() < 2) return;
        FrameGraphPhysicalResource physical = graphPhysical(resource);
        if (physical == null) throw new IllegalStateException("Missing physical allocation for " + resource.key().name());

        RhiResourceBarrier.Access barrierAccess = barrierAccess(access);
        AliasState previous = activeAliases.get(logical.physicalAllocationId());
        if (previous != null && previous.resource() != resource) {
            if (!access.writes()) {
                throw new IllegalStateException("Aliased transient resource '" + resource.key().name()
                        + "' became readable before its producer activated the allocation");
            }
            emitAliasBarrier(previous, stage, barrierAccess, physical, rhi);
            FrameGraphPhysicalResourcePool pool = graphPool();
            if (pool != null && graphScope != null) pool.invalidateLogical(graphScope, previous.resource().key());
        }
        activeAliases.put(logical.physicalAllocationId(), new AliasState(resource, stage, barrierAccess));
    }

    public long frameId() {
        return frameId;
    }

    public long historyEpoch() {
        return historyEpoch;
    }

    public int outputWidth() {
        return outputWidth;
    }

    public int outputHeight() {
        return outputHeight;
    }

    FrameGraphPhysicalPlan physicalPlan() {
        return graphPlan;
    }

    boolean hasGraphAccessThisFrame() {
        return !graphAccessedThisFrame.isEmpty();
    }

    private @Nullable FrameGraphPhysicalPlan.LogicalResourcePlan graphLogical(DeferredResource resource) {
        if (resource == null || graphScope == null || graphOwner == null) return null;
        return graphPlan.logical(resource.key());
    }

    private @Nullable FrameGraphPhysicalResource graphPhysical(DeferredResource resource) {
        FrameGraphPhysicalPlan.LogicalResourcePlan logical = graphLogical(resource);
        FrameGraphPhysicalResourcePool pool = graphPool();
        if (logical == null || logical.physicalAllocationId() < 0 || pool == null || graphScope == null) return null;
        return pool.resolve(graphScope, resource.key());
    }

    private @Nullable FrameGraphPhysicalResourcePool graphPool() {
        return graphOwner == null ? null : graphOwner.resources().frameGraphResources();
    }

    private static void emitAliasBarrier(AliasState previous,
                                         RhiResourceBarrier.Stage destinationStage,
                                         RhiResourceBarrier.Access destinationAccess,
                                         FrameGraphPhysicalResource physical,
                                         CombatantRhi rhi) {
        RhiStorageBuffer buffer = physical.storageBuffer();
        RhiStorageImage image = physical.storageImage();
        RhiStorageVolume volume = physical.storageVolume();
        rhi.advancedShaders().barrier(new RhiResourceBarrier(
                previous.stage(), previous.access(), destinationStage, destinationAccess,
                buffer == null ? List.of() : List.of(buffer),
                image == null ? List.of() : List.of(image),
                volume == null ? List.of() : List.of(volume)
        ));
    }

    private static RhiResourceBarrier.Access barrierAccess(FrameGraphAccess access) {
        return switch (access) {
            case READ -> RhiResourceBarrier.Access.READ;
            case WRITE -> RhiResourceBarrier.Access.WRITE;
            case READ_WRITE -> RhiResourceBarrier.Access.READ_WRITE;
        };
    }

    private static void rejectDuplicateGraphOwnership(DeferredResource resource, @Nullable Object value) {
        if (value != null && resource != null && resource.graphManagedPhysicalResource()) {
            throw new IllegalStateException("Graph-managed deferred resource cannot be manually rebound: "
                    + resource.key().name());
        }
    }

    private static void requireTextureResource(DeferredResource resource) {
        if (resource == null || (resource.key().kind() != FrameGraphResourceKind.TEXTURE
                && resource.key().kind() != FrameGraphResourceKind.EXTERNAL)) {
            throw new IllegalArgumentException("Not a texture/external resource: " + resource);
        }
    }

    private static void requireVolumeResource(DeferredResource resource) {
        if (resource == null || resource.key().kind() != FrameGraphResourceKind.VOLUME) {
            throw new IllegalArgumentException("Not a volume resource: " + resource);
        }
    }

    private record AliasState(DeferredResource resource,
                              RhiResourceBarrier.Stage stage,
                              RhiResourceBarrier.Access access) {
    }
}
