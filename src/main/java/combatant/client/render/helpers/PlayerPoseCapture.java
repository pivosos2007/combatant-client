/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.mixins.accessors.ModelPartAccessor;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Snapshots the vanilla {@link PlayerModel} part poses of every player the game submits for rendering, so visual
 * modules (Skeleton, PopChams) can draw from the exact animated pose without touching the renderer.
 *
 * <p>Capture is a no-op until a module {@link #retain()}s it. Each {@link Pose} stores per-part frames relative to the
 * entity origin in float precision plus the entity origin in doubles, so far-from-origin worlds stay accurate.</p>
 */
public final class PlayerPoseCapture {
    /** Feet origin of the model in model space ({@code translate(0, -1.501, 0)} in LivingEntityRenderer.submit). */
    private static final float FEET_MODEL_Y = 1.501f;
    private static final long STALE_NANOS = 2_000_000_000L;
    private static final int PRUNE_THRESHOLD = 256;

    private static final AtomicInteger CONSUMERS = new AtomicInteger();
    private static final ConcurrentHashMap<Integer, Pose> LATEST = new ConcurrentHashMap<>();

    private PlayerPoseCapture() {
    }

    public enum PartId {
        HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG
    }

    /**
     * One model part: {@code frame} maps part-local blocks (pixels / 16) to entity-relative space; bounds are the
     * union of the part's cubes in the same local blocks.
     */
    public record Part(PartId id, Matrix4f frame,
                       float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public float centerX() {
            return (minX + maxX) * 0.5f;
        }

        public float centerZ() {
            return (minZ + maxZ) * 0.5f;
        }
    }

    public record Pose(int entityId, Vec3 origin, long captureNanos, Part[] parts) {
        public Part part(PartId id) {
            return parts[id.ordinal()];
        }

        /** World position of a part-local point (blocks). */
        public Vec3 world(Part part, float lx, float ly, float lz) {
            Vector3f v = part.frame().transformPosition(lx, ly, lz, new Vector3f());
            return new Vec3(origin.x + v.x, origin.y + v.y, origin.z + v.z);
        }

        /**
         * Writes the 8 world-space corners of the part's box into {@code out} (24 doubles). Corner index bits:
         * 1 = x max, 2 = y max, 4 = z max.
         */
        public void corners(Part part, double[] out) {
            Vector3f v = new Vector3f();
            for (int i = 0; i < 8; i++) {
                float x = (i & 1) == 0 ? part.minX() : part.maxX();
                float y = (i & 2) == 0 ? part.minY() : part.maxY();
                float z = (i & 4) == 0 ? part.minZ() : part.maxZ();
                part.frame().transformPosition(x, y, z, v);
                out[i * 3] = origin.x + v.x;
                out[i * 3 + 1] = origin.y + v.y;
                out[i * 3 + 2] = origin.z + v.z;
            }
        }
    }

    public static void retain() {
        CONSUMERS.incrementAndGet();
    }

    public static void release() {
        if (CONSUMERS.decrementAndGet() <= 0) {
            CONSUMERS.set(0);
            LATEST.clear();
        }
    }

    public static boolean active() {
        return CONSUMERS.get() > 0;
    }

    public static void clear() {
        LATEST.clear();
    }

    /** Latest captured pose of the entity, or {@code null} when none was captured within the last two seconds. */
    public static Pose latest(int entityId) {
        Pose pose = LATEST.get(entityId);
        if (pose == null) return null;
        if (System.nanoTime() - pose.captureNanos() > STALE_NANOS) {
            LATEST.remove(entityId, pose);
            return null;
        }
        return pose;
    }

    /**
     * Called by the living-entity renderer right before it submits a model. {@code matrices} must hold the final
     * model-space transform (after rotation, scale and the {@code -1.501} feet offset).
     */
    public static void capture(Model<?> model, Object modelState, PoseStack matrices) {
        if (CONSUMERS.get() <= 0) return;
        if (!(model instanceof PlayerModel playerModel) || !(modelState instanceof AvatarRenderState state)) return;

        // The vanilla pipeline animates the shared model lazily at draw time; animate now so the parts hold this
        // player's pose. Re-running setupAnim is idempotent for the same state.
        playerModel.setupAnim(state);

        Matrix4f pose = matrices.last().pose();
        Vector3f feet = pose.transformPosition(0.0f, FEET_MODEL_Y, 0.0f, new Vector3f());
        Matrix4f base = new Matrix4f(pose).translateLocal(-feet.x, -feet.y, -feet.z);

        Part[] parts = new Part[PartId.values().length];
        parts[PartId.HEAD.ordinal()] = part(PartId.HEAD, playerModel.head, base);
        parts[PartId.BODY.ordinal()] = part(PartId.BODY, playerModel.body, base);
        parts[PartId.RIGHT_ARM.ordinal()] = part(PartId.RIGHT_ARM, playerModel.rightArm, base);
        parts[PartId.LEFT_ARM.ordinal()] = part(PartId.LEFT_ARM, playerModel.leftArm, base);
        parts[PartId.RIGHT_LEG.ordinal()] = part(PartId.RIGHT_LEG, playerModel.rightLeg, base);
        parts[PartId.LEFT_LEG.ordinal()] = part(PartId.LEFT_LEG, playerModel.leftLeg, base);

        long now = System.nanoTime();
        LATEST.put(state.id, new Pose(state.id, new Vec3(state.x, state.y, state.z), now, parts));
        if (LATEST.size() > PRUNE_THRESHOLD) {
            LATEST.values().removeIf(p -> now - p.captureNanos() > STALE_NANOS);
        }
    }

    private static Part part(PartId id, ModelPart modelPart, Matrix4f base) {
        Matrix4f frame = new Matrix4f(base)
                .translate(modelPart.x / 16.0f, modelPart.y / 16.0f, modelPart.z / 16.0f);
        if (modelPart.xRot != 0.0f || modelPart.yRot != 0.0f || modelPart.zRot != 0.0f) {
            frame.rotateZYX(modelPart.zRot, modelPart.yRot, modelPart.xRot);
        }
        if (modelPart.xScale != 1.0f || modelPart.yScale != 1.0f || modelPart.zScale != 1.0f) {
            frame.scale(modelPart.xScale, modelPart.yScale, modelPart.zScale);
        }

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) modelPart).combatant$getCubes();
        if (cubes != null) {
            for (ModelPart.Cube cube : cubes) {
                minX = Math.min(minX, cube.minX);
                minY = Math.min(minY, cube.minY);
                minZ = Math.min(minZ, cube.minZ);
                maxX = Math.max(maxX, cube.maxX);
                maxY = Math.max(maxY, cube.maxY);
                maxZ = Math.max(maxZ, cube.maxZ);
            }
        }
        if (minX > maxX) {
            minX = minY = minZ = maxX = maxY = maxZ = 0.0f;
        }
        return new Part(id, frame, minX / 16.0f, minY / 16.0f, minZ / 16.0f,
                maxX / 16.0f, maxY / 16.0f, maxZ / 16.0f);
    }
}
