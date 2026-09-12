/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.emitter;

import combatant.client.render.engine.rig.core.RigInstance;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigMeshPart;
import combatant.client.render.engine.rig.mesh.RigVertex;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/** Cached area-weighted sampler for CPU-side rig meshes. */
public final class RigSurfaceEmitter {
    private static final Map<RigMeshData, SurfaceCache> CACHE = new WeakHashMap<>();
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private RigSurfaceEmitter() {
    }

    public static SurfaceEmitterSample sample(RigMeshData mesh, RigInstance rig, long seed, int sampleIndex) {
        if (mesh == null || rig == null) {
            return new SurfaceEmitterSample(net.minecraft.world.phys.Vec3.ZERO,
                    net.minecraft.world.phys.Vec3.ZERO, -1, -1);
        }
        SurfaceCache cache;
        synchronized (CACHE) {
            cache = CACHE.computeIfAbsent(mesh, SurfaceCache::compile);
        }
        if (!(cache.totalArea > 1.0e-8f) || cache.triangles.length == 0) {
            return new SurfaceEmitterSample(net.minecraft.world.phys.Vec3.ZERO,
                    net.minecraft.world.phys.Vec3.ZERO, -1, -1);
        }

        double pick = unit(seed, sampleIndex, 0) * cache.totalArea;
        int triangleIndex = cache.find((float) pick);
        TriangleRef triangle = cache.triangles[triangleIndex];
        RigMeshPart part = mesh.part(triangle.partIndex);
        RigVertex a = part.vertex(triangle.a);
        RigVertex b = part.vertex(triangle.b);
        RigVertex c = part.vertex(triangle.c);

        float r1 = (float) Math.sqrt(unit(seed, sampleIndex, 1));
        float r2 = (float) unit(seed, sampleIndex, 2);
        float wa = 1.0f - r1;
        float wb = r1 * (1.0f - r2);
        float wc = r1 * r2;

        Scratch scratch = SCRATCH.get();
        skin(a, rig, scratch.aPos, scratch.aNormal, scratch);
        skin(b, rig, scratch.bPos, scratch.bNormal, scratch);
        skin(c, rig, scratch.cPos, scratch.cNormal, scratch);

        scratch.outPos.set(scratch.aPos).mul(wa).fma(wb, scratch.bPos).fma(wc, scratch.cPos);
        scratch.outNormal.set(scratch.aNormal).mul(wa).fma(wb, scratch.bNormal).fma(wc, scratch.cNormal);
        if (scratch.outNormal.lengthSquared() > 1.0e-8f) scratch.outNormal.normalize();

        return new SurfaceEmitterSample(
                new net.minecraft.world.phys.Vec3(scratch.outPos.x, scratch.outPos.y, scratch.outPos.z),
                new net.minecraft.world.phys.Vec3(scratch.outNormal.x, scratch.outNormal.y, scratch.outNormal.z),
                triangle.partIndex,
                triangleIndex
        );
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static void skin(RigVertex vertex, RigInstance rig, Vector3f outPos, Vector3f outNormal, Scratch scratch) {
        outPos.zero();
        outNormal.zero();
        apply(vertex.bone0(), vertex.weight0(), vertex, rig, outPos, outNormal, scratch);
        apply(vertex.bone1(), vertex.weight1(), vertex, rig, outPos, outNormal, scratch);
        apply(vertex.bone2(), vertex.weight2(), vertex, rig, outPos, outNormal, scratch);
        apply(vertex.bone3(), vertex.weight3(), vertex, rig, outPos, outNormal, scratch);
        if (outNormal.lengthSquared() > 1.0e-8f) outNormal.normalize();
    }

    private static void apply(int bone, float weight, RigVertex vertex, RigInstance rig,
                              Vector3f outPos, Vector3f outNormal, Scratch scratch) {
        if (bone < 0 || weight <= 0.0f) return;
        Matrix4fc matrix = rig.skinMatrixRef(bone);
        scratch.localPos.set(vertex.x(), vertex.y(), vertex.z());
        scratch.localNormal.set(vertex.normalX(), vertex.normalY(), vertex.normalZ());
        matrix.transformPosition(scratch.localPos, scratch.transformedPos);
        matrix.transformDirection(scratch.localNormal, scratch.transformedNormal);
        outPos.fma(weight, scratch.transformedPos);
        outNormal.fma(weight, scratch.transformedNormal);
    }

    private static double unit(long seed, int index, int lane) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L) + 0xD1B54A32D192ED03L * (lane + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    private static float triangleArea(RigVertex a, RigVertex b, RigVertex c) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float acx = c.x() - a.x();
        float acy = c.y() - a.y();
        float acz = c.z() - a.z();
        float cx = aby * acz - abz * acy;
        float cy = abz * acx - abx * acz;
        float cz = abx * acy - aby * acx;
        return 0.5f * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    private record TriangleRef(int partIndex, int a, int b, int c) {
    }

    private static final class SurfaceCache {
        private final TriangleRef[] triangles;
        private final float[] cumulativeArea;
        private final float totalArea;

        private SurfaceCache(TriangleRef[] triangles, float[] cumulativeArea, float totalArea) {
            this.triangles = triangles;
            this.cumulativeArea = cumulativeArea;
            this.totalArea = totalArea;
        }

        private static SurfaceCache compile(RigMeshData mesh) {
            int triangleCount = mesh.indexCount() / 3;
            TriangleRef[] triangles = new TriangleRef[triangleCount];
            float[] cumulative = new float[triangleCount];
            int cursor = 0;
            float area = 0.0f;
            for (int partIndex = 0; partIndex < mesh.partCount(); partIndex++) {
                RigMeshPart part = mesh.part(partIndex);
                for (int i = 0; i < part.indexCount(); i += 3) {
                    int ia = part.index(i);
                    int ib = part.index(i + 1);
                    int ic = part.index(i + 2);
                    float triangleArea = triangleArea(part.vertex(ia), part.vertex(ib), part.vertex(ic));
                    if (!(triangleArea > 1.0e-8f)) continue;
                    area += triangleArea;
                    triangles[cursor] = new TriangleRef(partIndex, ia, ib, ic);
                    cumulative[cursor] = area;
                    cursor++;
                }
            }
            if (cursor != triangleCount) {
                triangles = java.util.Arrays.copyOf(triangles, cursor);
                cumulative = java.util.Arrays.copyOf(cumulative, cursor);
            }
            return new SurfaceCache(triangles, cumulative, area);
        }

        private int find(float area) {
            int low = 0;
            int high = cumulativeArea.length - 1;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (area <= cumulativeArea[mid]) high = mid;
                else low = mid + 1;
            }
            return low;
        }
    }

    private static final class Scratch {
        private final Vector3f localPos = new Vector3f();
        private final Vector3f localNormal = new Vector3f();
        private final Vector3f transformedPos = new Vector3f();
        private final Vector3f transformedNormal = new Vector3f();
        private final Vector3f aPos = new Vector3f();
        private final Vector3f bPos = new Vector3f();
        private final Vector3f cPos = new Vector3f();
        private final Vector3f aNormal = new Vector3f();
        private final Vector3f bNormal = new Vector3f();
        private final Vector3f cNormal = new Vector3f();
        private final Vector3f outPos = new Vector3f();
        private final Vector3f outNormal = new Vector3f();
    }
}
