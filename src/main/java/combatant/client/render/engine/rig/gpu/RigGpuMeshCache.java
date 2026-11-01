/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rig.gpu;

import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import combatant.client.render.engine.rig.mesh.RigMeshCompiler;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.util.resources.asset.AssetLoad;
import combatant.client.util.resources.asset.AssetLoadPhase;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shared GPU residency for immutable skinned model geometry. The vertex/index stream is uploaded
 * once per rig mesh and RHI owner, not recreated from its triangles on every frame. Unlike the
 * render-state palette, meshes are independent of the actor pose and safe to reuse for instances.
 * All operations are render-thread confined (as is the underlying Mojang device).
 */
public final class RigGpuMeshCache {
    private static final RigGpuMeshCache GLOBAL = new RigGpuMeshCache();
    private final Map<RigMeshData, PersistentGpuMesh> resident = new IdentityHashMap<>();
    private CombatantRhi owner;

    public static RigGpuMeshCache global() { return GLOBAL; }

    public synchronized PersistentGpuMesh acquire(CombatantRhi rhi, RigMeshData mesh) {
        if (rhi == null || mesh == null) throw new IllegalArgumentException("RHI and mesh required");
        if (owner != rhi) {
            // The previous backend owns its physical buffers and destroys them on shutdown.
            // Retire handles through that backend if still alive.
            retireAll();
            owner = rhi;
        }
        PersistentGpuMesh allocation = resident.get(mesh);
        if (allocation != null && !allocation.isClosed()) return allocation;
        try (var compiled = RigMeshCompiler.compile(mesh)) {
            allocation = rhi.persistentMeshes().upload("Combatant persistent skinned rig", compiled);
        }
        resident.put(mesh, allocation);
        return allocation;
    }

    /** Must run at the texture/model resource epoch boundary. */
    public synchronized void invalidate() {
        retireAll();
    }

    public synchronized void releaseBackend(CombatantRhi rhi) {
        if (owner != rhi) return;
        retireAll();
        owner = null;
    }

    private void retireAll() {
        if (owner != null) {
            for (PersistentGpuMesh mesh : resident.values()) {
                if (!mesh.isClosed()) owner.resources().retire(mesh);
            }
        } else {
            for (PersistentGpuMesh mesh : resident.values()) mesh.close();
        }
        resident.clear();
    }

    @AssetLoad(value = {AssetLoadPhase.INITIALIZE, AssetLoadPhase.RELOAD}, order = 70)
    public static void invalidateRigMeshes() { GLOBAL.invalidate(); }
}
