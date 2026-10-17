/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.resource;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.render.engine.framegraph.FrameGraphBufferDescriptor;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalPlan;
import combatant.client.render.engine.framegraph.FrameGraphPhysicalResourceDescriptor;
import combatant.client.render.engine.framegraph.FrameGraphResourceKey;
import combatant.client.render.engine.framegraph.FrameGraphResourceLifetime;
import combatant.client.render.engine.framegraph.FrameGraphTextureDescriptor;
import combatant.client.render.engine.framegraph.FrameGraphVolumeDescriptor;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiTextureUsage;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Materializes compiler-selected physical allocations through the active Combatant RHI.
 * The pool owns no backend objects outside the existing RHI/retirement lifecycle.
 */
public final class FrameGraphPhysicalResourcePool implements AutoCloseable {
    private final FrameResourceRetirementQueue retirementQueue;
    private final IdentityHashMap<Object, Scope> scopes = new IdentityHashMap<>();

    FrameGraphPhysicalResourcePool(FrameResourceRetirementQueue retirementQueue) {
        this.retirementQueue = retirementQueue;
    }

    public synchronized void materialize(Object scopeToken, FrameGraphPhysicalPlan plan, CombatantRhi rhi) {
        if (scopeToken == null) throw new IllegalArgumentException("scopeToken");
        if (plan == null) throw new IllegalArgumentException("plan");
        if (rhi == null) throw new IllegalArgumentException("rhi");
        RenderSystem.assertOnRenderThread();
        Scope scope = scopes.computeIfAbsent(scopeToken, ignored -> new Scope());
        if (scope.owner != null && scope.owner != rhi) {
            retireAll(scope);
            scope = new Scope();
            scopes.put(scopeToken, scope);
        }
        scope.owner = rhi;

        HashMap<Integer, Entry> nextTransient = new HashMap<>();
        HashMap<FrameGraphResourceKey, Entry> nextPersistent = new HashMap<>();
        HashMap<FrameGraphResourceKey, Entry> nextLogical = new HashMap<>();
        HashSet<Entry> retained = new HashSet<>();

        for (FrameGraphPhysicalPlan.PhysicalAllocationPlan allocation : plan.physicalAllocations()) {
            Entry entry;
            if (allocation.lifetime() == FrameGraphResourceLifetime.PERSISTENT) {
                FrameGraphResourceKey logical = allocation.logicalResources().getFirst();
                entry = scope.persistent.get(logical);
                if (!compatible(entry, allocation.descriptor())) {
                    entry = create(allocation, rhi);
                    scope.valid.remove(logical);
                }
                nextPersistent.put(logical, entry);
            } else {
                entry = scope.transientAllocations.get(allocation.allocationId());
                if (!compatible(entry, allocation.descriptor())) {
                    entry = create(allocation, rhi);
                    for (FrameGraphResourceKey logical : allocation.logicalResources()) scope.valid.remove(logical);
                }
                nextTransient.put(allocation.allocationId(), entry);
            }
            retained.add(entry);
            for (FrameGraphResourceKey logical : allocation.logicalResources()) nextLogical.put(logical, entry);
        }

        retireMissing(scope.transientAllocations.values(), retained);
        retireMissing(scope.persistent.values(), retained);
        scope.transientAllocations = nextTransient;
        scope.persistent = nextPersistent;
        scope.logical = nextLogical;
        scope.plan = plan;
        scope.valid.retainAll(nextLogical.keySet());
    }

    /** True only while this logical graph currently has a materialized physical scope. */
    public synchronized boolean hasScope(Object scopeToken) {
        return scopeToken != null && scopes.containsKey(scopeToken);
    }

    public synchronized FrameGraphPhysicalResource resolve(Object scopeToken, FrameGraphResourceKey logical) {
        Scope scope = scopes.get(scopeToken);
        if (scope == null || logical == null) return null;
        Entry entry = scope.logical.get(logical);
        return entry == null ? null : entry.resource;
    }

    public synchronized boolean valid(Object scopeToken, FrameGraphResourceKey logical) {
        Scope scope = scopes.get(scopeToken);
        return scope != null && scope.valid.contains(logical);
    }

    public synchronized void markProduced(Object scopeToken, FrameGraphResourceKey logical) {
        Scope scope = scopes.get(scopeToken);
        if (scope == null || logical == null) return;
        if (logical.lifetime() != FrameGraphResourceLifetime.EXTERNAL && !scope.logical.containsKey(logical)) {
            throw new IllegalStateException("Logical resource has no materialized physical allocation: " + logical.name());
        }
        scope.valid.add(logical);
    }

    public synchronized void invalidateLogical(Object scopeToken, FrameGraphResourceKey logical) {
        Scope scope = scopes.get(scopeToken);
        if (scope != null && logical != null) scope.valid.remove(logical);
    }

    /** Frame-local transient contents become invalid; persistent/history contents remain valid. */
    public synchronized void beginFrame() {
        for (Scope scope : scopes.values()) {
            scope.valid.removeIf(resource -> resource.lifetime() == FrameGraphResourceLifetime.TRANSIENT);
        }
    }

    /** Resize/reload invalidates physical compatibility and history validity before the next plan is materialized. */
    public synchronized void invalidateAll() {
        for (Scope scope : scopes.values()) retireAll(scope);
        scopes.clear();
    }

    public synchronized void releaseScope(Object scopeToken) {
        Scope scope = scopes.remove(scopeToken);
        if (scope != null) retireAll(scope);
    }

    @Override
    public synchronized void close() {
        invalidateAll();
    }

    private Entry create(FrameGraphPhysicalPlan.PhysicalAllocationPlan allocation, CombatantRhi rhi) {
        String label = "combatant-framegraph-" + allocation.allocationId() + "-"
                + allocation.logicalResources().getFirst().name().replace('.', '-');
        FrameGraphPhysicalResourceDescriptor descriptor = allocation.descriptor();
        if (descriptor instanceof FrameGraphTextureDescriptor texture) {
            if (texture.storage()) {
                RhiStorageImage image = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                        label, texture.width(), texture.height(), texture.format(), texture.storageAccess(),
                        texture.sampled(), texture.renderAttachment(), texture.mipLevels()));
                return new Entry(new FrameGraphPhysicalResource(allocation.allocationId(), descriptor,
                        image.view(), image, null, null, image));
            }
            int nativeUsage = texture.usage() & ~RhiTextureUsage.STORAGE_IMAGE;
            GpuTexture gpuTexture = texture.samples() > 1
                    ? rhi.msaa().createTexture(label, nativeUsage, texture.format(), texture.width(), texture.height(), texture.samples())
                    : RenderSystem.getDevice().createTexture(label, nativeUsage, texture.format(),
                            texture.width(), texture.height(), 1, texture.mipLevels());
            GpuTextureView view = null;
            try {
                view = RenderSystem.getDevice().createTextureView(gpuTexture);
                OwnedTexture owned = new OwnedTexture(gpuTexture, view);
                return new Entry(new FrameGraphPhysicalResource(allocation.allocationId(), descriptor,
                        view, null, null, null, owned));
            } catch (RuntimeException | Error t) {
                if (view != null) view.close();
                gpuTexture.close();
                throw t;
            }
        }
        if (descriptor instanceof FrameGraphVolumeDescriptor volumeDescriptor) {
            StorageVolumeDescriptor volume = volumeDescriptor.volume();
            RhiStorageVolume created = rhi.advancedShaders().createStorageVolume(new StorageVolumeDescriptor(
                    label, volume.width(), volume.height(), volume.depth(), volume.format(), volume.access(),
                    volume.usage(), volume.mipLevels()));
            return new Entry(new FrameGraphPhysicalResource(allocation.allocationId(), descriptor,
                    null, null, null, created, created));
        }
        if (descriptor instanceof FrameGraphBufferDescriptor bufferDescriptor) {
            StorageBufferDescriptor buffer = bufferDescriptor.buffer();
            RhiStorageBuffer created = rhi.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    label, buffer.elementLayout(), buffer.elementCapacity(), buffer.access(), buffer.indirectSource()));
            return new Entry(new FrameGraphPhysicalResource(allocation.allocationId(), descriptor,
                    null, null, created, null, created));
        }
        throw new IllegalArgumentException("Unsupported frame-graph physical descriptor: " + descriptor.getClass().getName());
    }

    private static boolean compatible(Entry entry, FrameGraphPhysicalResourceDescriptor descriptor) {
        return entry != null && !entry.resource.isClosed() && entry.resource.descriptor().aliasCompatible(descriptor);
    }

    private void retireMissing(Iterable<Entry> oldEntries, Set<Entry> retained) {
        for (Entry entry : oldEntries) if (entry != null && !retained.contains(entry)) retire(entry);
    }

    private void retire(Entry entry) {
        if (entry != null && !entry.resource.isClosed()) retirementQueue.retire(entry.resource);
    }

    private void retireAll(Scope scope) {
        HashSet<Entry> unique = new HashSet<>();
        unique.addAll(scope.transientAllocations.values());
        unique.addAll(scope.persistent.values());
        for (Entry entry : unique) retire(entry);
        scope.transientAllocations.clear();
        scope.persistent.clear();
        scope.logical.clear();
        scope.valid.clear();
        scope.plan = FrameGraphPhysicalPlan.EMPTY;
        scope.owner = null;
    }

    private static final class Scope {
        CombatantRhi owner;
        FrameGraphPhysicalPlan plan = FrameGraphPhysicalPlan.EMPTY;
        Map<Integer, Entry> transientAllocations = new HashMap<>();
        Map<FrameGraphResourceKey, Entry> persistent = new HashMap<>();
        Map<FrameGraphResourceKey, Entry> logical = new HashMap<>();
        final Set<FrameGraphResourceKey> valid = new HashSet<>();
    }

    private record Entry(FrameGraphPhysicalResource resource) {
    }

    private record OwnedTexture(GpuTexture texture, GpuTextureView view) implements AutoCloseable {
        @Override
        public void close() {
            view.close();
            texture.close();
        }
    }
}
