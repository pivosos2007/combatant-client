/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.mixins.accessors.LevelRendererAccessor;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.render.sodium.SodiumTerrainSubmission;
import combatant.client.util.logging.DebugLog;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Optional non-terrain shadow-caster coverage shared by directional and local-light shadows.
 *
 * <p>Terrain remains Sodium-owned. Entity/block-entity candidates are obtained from Minecraft's
 * typed scene structures, culled before render-state extraction where possible, and rendered with
 * a dedicated {@link FeatureRenderDispatcher}. The primary LevelRenderer prepared frame is never
 * re-entered from deferred secondary passes.</p>
 */
final class DeferredSecondaryShadowCasterSource implements AutoCloseable {
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f);

    private RenderBuffers renderBuffers;
    private FeatureRenderDispatcher featureDispatcher;
    private SubmitNodeStorage submitStorage;
    private ProjectionMatrixBuffer projectionBuffer;
    private TextureTarget scratch;
    private int scratchWidth;
    private int scratchHeight;

    private long cachedEntityFrame = Long.MIN_VALUE;
    private final IdentityHashMap<Entity, EntityRenderState> entityStateCache = new IdentityHashMap<>();

    boolean enabledFor(DeferredSecondaryView view) {
        if (view == null) return false;
        DeferredSecondaryCasterConfig config = DeferredSecondaryCasterConfig.current();
        return config.entitiesFor(view.family()) || config.blockEntitiesFor(view.family());
    }

    /**
     * Render one shadow view through a view-sized scratch target, then copy the resulting depth
     * into the destination atlas tile. Blaze3D renderArea is a scissor, not a viewport; rendering
     * directly into an atlas tile would therefore project the view against the complete atlas.
     */
    void render(DeferredPassContext context,
                SodiumSecondaryTerrainContext.Purpose purpose,
                DeferredSecondaryView view,
                TextureTarget destination,
                SodiumWorldRenderer renderer,
                SodiumTerrainSubmission primarySubmission,
                int excludedEntityId) {
        int width = view.hasExplicitViewport() ? view.viewportWidth() : destination.width;
        int height = view.hasExplicitViewport() ? view.viewportHeight() : destination.height;
        TextureTarget localTarget = ensureScratch(width, height);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(
                localTarget.getColorTexture(), CLEAR_COLOR,
                localTarget.getDepthTexture(), 0.0
        );

        DeferredSecondaryView localView = view.withViewport(0, 0, width, height);
        renderTerrain(purpose, localView, localTarget, renderer, primarySubmission);

        CasterBatch batch = collect(context, localView, excludedEntityId);
        if (!batch.empty()) {
            renderFeatures(context, localView, localTarget, batch);
        }

        int dstX = view.hasExplicitViewport() ? view.viewportX() : 0;
        int dstY = view.hasExplicitViewport() ? view.viewportY() : 0;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                localTarget.getDepthTexture(),
                destination.getDepthTexture(),
                0,
                dstX, dstY,
                0, 0,
                width, height
        );
    }

    private static void renderTerrain(SodiumSecondaryTerrainContext.Purpose purpose,
                                      DeferredSecondaryView view,
                                      TextureTarget target,
                                      SodiumWorldRenderer renderer,
                                      SodiumTerrainSubmission primarySubmission) {
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(view.projection(), view.view());
        SodiumSecondaryTerrainContext.run(purpose, view, target, () -> {
            renderer.renderLayer(
                    matrices,
                    DefaultTerrainRenderPasses.SOLID,
                    view.origin().x, view.origin().y, view.origin().z,
                    primarySubmission.fog(), primarySubmission.sampler()
            );
            renderer.renderLayer(
                    matrices,
                    DefaultTerrainRenderPasses.CUTOUT,
                    view.origin().x, view.origin().y, view.origin().z,
                    primarySubmission.fog(), primarySubmission.sampler()
            );
        });
    }

    private CasterBatch collect(DeferredPassContext context, DeferredSecondaryView view, int excludedEntityId) {
        DeferredSecondaryCasterConfig config = DeferredSecondaryCasterConfig.current();
        boolean entitiesEnabled = config.entitiesFor(view.family());
        boolean blockEntitiesEnabled = config.blockEntitiesFor(view.family());
        if (!entitiesEnabled && !blockEntitiesEnabled) return CasterBatch.EMPTY;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        if (minecraft == null || level == null) return CasterBatch.EMPTY;

        AABB worldBounds = worldBounds(context, view, config.boundsPadding());
        FrustumIntersection frustum = view.family() == DeferredViewFamily.LOCAL_LIGHT_SHADOW
                ? null : new FrustumIntersection(view.viewProjection());
        Vec3 eye = secondaryEye(view);

        List<EntityRenderState> entityStates = entitiesEnabled
                ? collectEntities(context, minecraft, level, view, eye, worldBounds, frustum, config, excludedEntityId)
                : List.of();
        List<BlockEntityRenderState> blockEntityStates = blockEntitiesEnabled
                ? collectBlockEntities(context, minecraft, level, view, eye, worldBounds, frustum, config)
                : List.of();
        if (entityStates.isEmpty() && blockEntityStates.isEmpty()) return CasterBatch.EMPTY;
        return new CasterBatch(entityStates, blockEntityStates);
    }

    private List<EntityRenderState> collectEntities(DeferredPassContext context,
                                                    Minecraft minecraft,
                                                    ClientLevel level,
                                                    DeferredSecondaryView view,
                                                    Vec3 eye,
                                                    AABB worldBounds,
                                                    FrustumIntersection frustum,
                                                    DeferredSecondaryCasterConfig config,
                                                    int excludedEntityId) {
        long frameId = context.frame().frameId();
        if (cachedEntityFrame != frameId) {
            cachedEntityFrame = frameId;
            entityStateCache.clear();
        }

        ArrayList<Entity> queried = new ArrayList<>(Math.min(256, config.maxEntityQueryCandidates()));
        level.getEntities(
                EntityTypeTest.forClass(Entity.class),
                worldBounds,
                entity -> !entity.isRemoved() && entity.getId() != excludedEntityId,
                queried,
                config.maxEntityQueryCandidates()
        );
        if (queried.isEmpty()) return List.of();

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        Frustum rendererFrustum = view.family() == DeferredViewFamily.LOCAL_LIGHT_SHADOW
                ? null : new Frustum(view.view(), view.projection());
        if (rendererFrustum != null) {
            rendererFrustum.prepare(view.origin().x, view.origin().y, view.origin().z);
        }
        ArrayList<EntityCandidate> visible = new ArrayList<>(queried.size());
        for (Entity entity : queried) {
            AABB box = entity.getBoundingBox().inflate(config.boundsPadding());
            if (!testAabb(view, frustum, box)) continue;
            try {
                if (rendererFrustum != null && !dispatcher.shouldRender(entity, rendererFrustum, eye.x, eye.y, eye.z)) continue;
            } catch (Throwable error) {
                // A renderer-specific visibility failure must not turn into false-negative culling.
                DebugLog.warnOnChange("deferred-secondary-entity-visibility:" + entity.getType(), error.toString(),
                        "[Deferred] entity secondary visibility check failed open: %s", error.toString());
            }
            visible.add(new EntityCandidate(entity, influence(box, eye, view.family() != DeferredViewFamily.SHADOW_CASCADE)));
        }
        if (visible.isEmpty()) return List.of();
        visible.sort(Comparator
                .comparingDouble((EntityCandidate candidate) -> -candidate.influence())
                .thenComparingInt(candidate -> candidate.entity().getId()));
        if (visible.size() > config.maxEntityCastersPerView()) {
            visible.subList(config.maxEntityCastersPerView(), visible.size()).clear();
        }

        float tickProgress = context.frame().tickProgress();
        ArrayList<EntityRenderState> states = new ArrayList<>(visible.size());
        for (EntityCandidate candidate : visible) {
            Entity entity = candidate.entity();
            try {
                EntityRenderState state = entityStateCache.get(entity);
                if (state == null) {
                    state = dispatcher.extractEntity(entity, tickProgress);
                    sanitizeEntityState(state);
                    entityStateCache.put(entity, state);
                }
                if (state == null) continue;
                double dx = state.x - eye.x;
                double dy = state.y - eye.y;
                double dz = state.z - eye.z;
                state.distanceToCameraSq = dx * dx + dy * dy + dz * dz;
                states.add(state);
            } catch (Throwable error) {
                DebugLog.warnOnChange("deferred-secondary-entity-caster:" + entity.getType(), error.toString(),
                        "[Deferred] entity shadow caster skipped after renderer failure: %s", error.toString());
            }
        }
        return states;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<BlockEntityRenderState> collectBlockEntities(DeferredPassContext context,
                                                               Minecraft minecraft,
                                                               ClientLevel level,
                                                               DeferredSecondaryView view,
                                                               Vec3 eye,
                                                               AABB worldBounds,
                                                               FrustumIntersection frustum,
                                                               DeferredSecondaryCasterConfig config) {
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        if (dispatcher == null) return List.of();

        Set<BlockEntity> candidates = new LinkedHashSet<>();
        Vec3 chunkPriorityOrigin = view.family() == DeferredViewFamily.SHADOW_CASCADE
                ? worldBounds.getCenter() : eye;
        for (ChunkCandidate chunk : nearestChunks(level, worldBounds, chunkPriorityOrigin, config.maxBlockEntityChunksPerView())) {
            try {
                LevelChunk levelChunk = level.getChunk(chunk.x(), chunk.z());
                candidates.addAll(levelChunk.getBlockEntities().values());
            } catch (Throwable ignored) {
                // Chunk lifetime can change between hasChunk and access. Culling is fail-soft.
            }
        }
        try {
            candidates.addAll(level.getGloballyRenderedBlockEntities());
        } catch (Throwable ignored) {
        }
        if (candidates.isEmpty()) return List.of();

        ArrayList<BlockEntityCandidate> visible = new ArrayList<>();
        for (BlockEntity blockEntity : candidates) {
            try {
                BlockEntityRenderer renderer = dispatcher.getRenderer(blockEntity);
                if (renderer == null) continue;
                boolean offscreen = renderer.shouldRenderOffScreen();
                BlockPos pos = blockEntity.getBlockPos();
                if (!offscreen) {
                    AABB box = new AABB(pos).inflate(config.boundsPadding());
                    if (!testAabb(view, frustum, box)) continue;
                }
                double dx = pos.getX() + 0.5 - eye.x;
                double dy = pos.getY() + 0.5 - eye.y;
                double dz = pos.getZ() + 0.5 - eye.z;
                visible.add(new BlockEntityCandidate(blockEntity, renderer, offscreen, dx * dx + dy * dy + dz * dz));
            } catch (Throwable error) {
                DebugLog.warnOnChange("deferred-secondary-block-entity-candidate:" + blockEntity.getType(), error.toString(),
                        "[Deferred] block-entity shadow candidate skipped: %s", error.toString());
            }
        }
        visible.sort(Comparator
                .comparingDouble(BlockEntityCandidate::distanceSq)
                .thenComparingLong(candidate -> candidate.blockEntity().getBlockPos().asLong()));
        if (visible.size() > config.maxBlockEntityCastersPerView()) {
            visible.subList(config.maxBlockEntityCastersPerView(), visible.size()).clear();
        }

        Vec3 restoreCamera = primaryCameraPosition(minecraft);
        float tickProgress = context.frame().tickProgress();
        ArrayList<BlockEntityRenderState> states = new ArrayList<>(visible.size());
        try {
            dispatcher.prepare(eye);
            for (BlockEntityCandidate candidate : visible) {
                try {
                    BlockEntityRenderState state = dispatcher.tryExtractRenderState(
                            candidate.blockEntity(), tickProgress, null, candidate.offscreen()
                    );
                    if (state != null) states.add(state);
                } catch (Throwable error) {
                    DebugLog.warnOnChange("deferred-secondary-block-entity-caster:" + candidate.blockEntity().getType(), error.toString(),
                            "[Deferred] block-entity shadow caster skipped after renderer failure: %s", error.toString());
                }
            }
        } finally {
            dispatcher.prepare(restoreCamera);
        }
        return states;
    }

    private void renderFeatures(DeferredPassContext context,
                                DeferredSecondaryView view,
                                TextureTarget target,
                                CasterBatch batch) {
        Minecraft minecraft = Minecraft.getInstance();
        FeatureRenderDispatcher dispatcher = featureDispatcher(minecraft);
        if (dispatcher == null || submitStorage == null) return;

        CameraRenderState camera = secondaryCameraState(minecraft, view);
        if (camera == null) return;

        submitStorage.getSubmitsPerOrder().clear();
        PoseStack poses = new PoseStack();
        EntityRenderDispatcher entityDispatcher = minecraft.getEntityRenderDispatcher();
        BlockEntityRenderDispatcher blockDispatcher = minecraft.getBlockEntityRenderDispatcher();

        for (EntityRenderState state : batch.entities()) {
            try {
                entityDispatcher.submit(
                        state,
                        camera,
                        state.x - view.origin().x,
                        state.y - view.origin().y,
                        state.z - view.origin().z,
                        poses,
                        submitStorage
                );
            } catch (Throwable error) {
                DebugLog.warnOnChange("deferred-secondary-entity-submit:" + state.entityType, error.toString(),
                        "[Deferred] entity shadow submit failed softly: %s", error.toString());
            } finally {
                while (!poses.isEmpty()) poses.popPose();
            }
        }
        for (BlockEntityRenderState state : batch.blockEntities()) {
            BlockPos pos = state.blockPos;
            if (pos == null) continue;
            try {
                poses.pushPose();
                poses.translate(
                        pos.getX() - view.origin().x,
                        pos.getY() - view.origin().y,
                        pos.getZ() - view.origin().z
                );
                blockDispatcher.submit(state, poses, submitStorage, camera);
            } catch (Throwable error) {
                DebugLog.warnOnChange("deferred-secondary-block-entity-submit:" + state.blockEntityType, error.toString(),
                        "[Deferred] block-entity shadow submit failed softly: %s", error.toString());
            } finally {
                while (!poses.isEmpty()) poses.popPose();
            }
        }

        if (submitStorage.getSubmitsPerOrder().isEmpty()) return;

        GpuBufferSlice previousProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType previousProjectionType = RenderSystem.getProjectionType();
        Matrix4f previousModelView = RenderSystem.getModelViewMatrixCopy();
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        Matrix4f previousMeshProjection = MeshRenderer.projection();
        Matrix4f previousWorldProjection = new Matrix4f(RenderState.worldProjection);
        boolean previousRendering3D = RenderState.rendering3D;

        try {
            RenderSystem.outputColorTextureOverride = target.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
            RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(view.projection()), projectionType(view));
            RenderSystem.getModelViewStack().set(view.view());
            MeshRenderer.setProjection(view.projection());
            RenderState.worldProjection.set(view.projection());
            RenderState.rendering3D = true;

            try (FeatureRenderDispatcher.PreparedFrame prepared = dispatcher.prepareFrame(submitStorage)) {
                prepared.executeSolid();
            } catch (Throwable error) {
                // Terrain depth already exists in scratch. A modded feature failure degrades only
                // optional caster coverage; the caller still copies valid terrain depth to atlas.
                DebugLog.warnOnChange("deferred-secondary-feature-shadow", error.toString(),
                        "[Deferred] optional feature shadow pass failed softly: %s", error.toString());
            } finally {
                renderBuffers.endFrame();
            }
        } finally {
            RenderSystem.getModelViewStack().set(previousModelView);
            MeshRenderer.setProjection(previousMeshProjection);
            RenderState.worldProjection.set(previousWorldProjection);
            RenderState.rendering3D = previousRendering3D;
            if (previousProjection != null && previousProjectionType != null) {
                RenderSystem.setProjectionMatrix(previousProjection, previousProjectionType);
            }
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            submitStorage.getSubmitsPerOrder().clear();
        }
    }

    private FeatureRenderDispatcher featureDispatcher(Minecraft minecraft) {
        if (featureDispatcher != null) return featureDispatcher;
        if (minecraft == null || minecraft.gameRenderer == null || minecraft.getModelManager() == null
                || minecraft.getAtlasManager() == null || minecraft.font == null) return null;
        renderBuffers = new RenderBuffers(1);
        featureDispatcher = new FeatureRenderDispatcher(
                renderBuffers,
                minecraft.getModelManager(),
                minecraft.getAtlasManager(),
                minecraft.font,
                minecraft.gameRenderer.gameRenderState()
        );
        submitStorage = new SubmitNodeStorage();
        projectionBuffer = new ProjectionMatrixBuffer("combatant-secondary-shadow-projection");
        return featureDispatcher;
    }

    private TextureTarget ensureScratch(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (scratch != null && scratchWidth == width && scratchHeight == height) return scratch;
        closeScratch();
        scratch = new TextureTarget(
                "combatant-secondary-shadow-features",
                width, height, true,
                GpuFormat.RGBA8_UNORM
        );
        scratchWidth = width;
        scratchHeight = height;
        return scratch;
    }

    private static CameraRenderState secondaryCameraState(Minecraft minecraft, DeferredSecondaryView view) {
        CameraRenderState base = null;
        if (minecraft.levelRenderer instanceof LevelRendererAccessor accessor) {
            LevelRenderState levelState = accessor.combatant$getWorldRenderState();
            if (levelState != null) base = levelState.cameraRenderState;
        }

        CameraRenderState state = new CameraRenderState();
        Vec3 eye = secondaryEye(view);
        state.pos = eye;
        state.blockPos = BlockPos.containing(eye);
        state.initialized = true;
        state.isPanoramicMode = base != null && base.isPanoramicMode;
        state.isFrustumCaptured = false;
        state.smartCull = false;
        Quaternionf orientation = new Quaternionf();
        view.view().getNormalizedRotation(orientation).conjugate().normalize();
        state.orientation = orientation;
        state.projectionMatrix = view.projection();
        Matrix4f rotation = view.view();
        rotation.m30(0.0f).m31(0.0f).m32(0.0f);
        state.viewRotationMatrix = rotation;
        state.depthFar = view.farPlane();
        if (base != null) {
            state.fogType = base.fogType;
            state.fogData = base.fogData;
            state.hudFov = base.hudFov;
            state.entityRenderState = base.entityRenderState;
        }
        return state;
    }

    private static Vec3 primaryCameraPosition(Minecraft minecraft) {
        if (minecraft != null && minecraft.levelRenderer instanceof LevelRendererAccessor accessor) {
            LevelRenderState levelState = accessor.combatant$getWorldRenderState();
            if (levelState != null && levelState.cameraRenderState != null && levelState.cameraRenderState.pos != null) {
                return levelState.cameraRenderState.pos;
            }
        }
        if (minecraft != null && minecraft.gameRenderer != null && minecraft.gameRenderer.mainCamera() != null) {
            return minecraft.gameRenderer.mainCamera().position();
        }
        return Vec3.ZERO;
    }

    private static void sanitizeEntityState(EntityRenderState state) {
        if (state == null) return;
        state.displayFireAnimation = false;
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        state.nameTag = null;
        state.scoreText = null;
        state.nameTagAttachment = null;
        state.shadowRadius = 0.0f;
        state.shadowPieces.clear();
    }

    private static List<ChunkCandidate> nearestChunks(ClientLevel level,
                                                       AABB bounds,
                                                       Vec3 eye,
                                                       int budget) {
        int minX = floorChunk(bounds.minX);
        int maxX = floorChunk(bounds.maxX);
        int minZ = floorChunk(bounds.minZ);
        int maxZ = floorChunk(bounds.maxZ);
        ArrayList<ChunkCandidate> chunks = new ArrayList<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                if (!level.hasChunk(x, z)) continue;
                double cx = x * 16.0 + 8.0;
                double cz = z * 16.0 + 8.0;
                double dx = cx - eye.x;
                double dz = cz - eye.z;
                chunks.add(new ChunkCandidate(x, z, dx * dx + dz * dz));
            }
        }
        chunks.sort(Comparator.comparingDouble(ChunkCandidate::distanceSq));
        if (chunks.size() > budget) chunks.subList(budget, chunks.size()).clear();
        return chunks;
    }

    private static int floorChunk(double coordinate) {
        return ((int) Math.floor(coordinate)) >> 4;
    }

    private static ProjectionType projectionType(DeferredSecondaryView view) {
        return view.family() == DeferredViewFamily.SHADOW_CASCADE
                ? ProjectionType.ORTHOGRAPHIC
                : ProjectionType.PERSPECTIVE;
    }

    private static AABB worldBounds(DeferredPassContext context, DeferredSecondaryView view, double padding) {
        if (view.family() == DeferredViewFamily.LOCAL_LIGHT_SHADOW) {
            return DeferredSecondaryViewCulling.perspectiveRangeBounds(view, padding);
        }
        Matrix4f inverse = view.viewProjection().invert();
        float nearClipZ = context.rhi().capabilities().zeroToOneDepth() ? 0.0f : -1.0f;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int z = 0; z < 2; z++) {
            float ndcZ = z == 0 ? nearClipZ : 1.0f;
            for (int y = 0; y < 2; y++) {
                float ndcY = y == 0 ? -1.0f : 1.0f;
                for (int x = 0; x < 2; x++) {
                    float ndcX = x == 0 ? -1.0f : 1.0f;
                    Vector4f corner = new Vector4f(ndcX, ndcY, ndcZ, 1.0f).mul(inverse);
                    if (Math.abs(corner.w) > 1.0e-6f) corner.div(corner.w);
                    double wx = corner.x + view.origin().x;
                    double wy = corner.y + view.origin().y;
                    double wz = corner.z + view.origin().z;
                    minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
                    maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
                }
            }
        }
        return new AABB(minX - padding, minY - padding, minZ - padding,
                maxX + padding, maxY + padding, maxZ + padding);
    }

    private static Vec3 secondaryEye(DeferredSecondaryView view) {
        Vector3f relativeEye = new Vector3f();
        view.view().invert().transformPosition(relativeEye);
        return new Vec3(
                view.origin().x + relativeEye.x,
                view.origin().y + relativeEye.y,
                view.origin().z + relativeEye.z
        );
    }

    private static boolean testAabb(DeferredSecondaryView view, FrustumIntersection frustum, AABB box) {
        if (view.family() == DeferredViewFamily.LOCAL_LIGHT_SHADOW) {
            return DeferredSecondaryViewCulling.testPerspectiveAabb(view, box);
        }
        if (frustum == null) return true;
        Vec3 origin = view.origin();
        return frustum.testAab(
                (float) (box.minX - origin.x),
                (float) (box.minY - origin.y),
                (float) (box.minZ - origin.z),
                (float) (box.maxX - origin.x),
                (float) (box.maxY - origin.y),
                (float) (box.maxZ - origin.z)
        );
    }

    private static double influence(AABB box, Vec3 eye, boolean perspective) {
        double sx = Math.max(0.05, box.getXsize());
        double sy = Math.max(0.05, box.getYsize());
        double sz = Math.max(0.05, box.getZsize());
        double radiusSq = sx * sx + sy * sy + sz * sz;
        if (!perspective) return radiusSq;
        double cx = (box.minX + box.maxX) * 0.5;
        double cy = (box.minY + box.maxY) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double dx = cx - eye.x;
        double dy = cy - eye.y;
        double dz = cz - eye.z;
        return radiusSq / Math.max(1.0, dx * dx + dy * dy + dz * dz);
    }

    @Override
    public void close() {
        closeScratch();
        if (featureDispatcher != null) {
            featureDispatcher.close();
            featureDispatcher = null;
        }
        if (renderBuffers != null) {
            renderBuffers.close();
            renderBuffers = null;
        }
        if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
        submitStorage = null;
        entityStateCache.clear();
        cachedEntityFrame = Long.MIN_VALUE;
    }

    private void closeScratch() {
        if (scratch != null) {
            scratch.destroyBuffers();
            scratch = null;
        }
        scratchWidth = 0;
        scratchHeight = 0;
    }

    private record CasterBatch(List<EntityRenderState> entities,
                               List<BlockEntityRenderState> blockEntities) {
        private static final CasterBatch EMPTY = new CasterBatch(List.of(), List.of());

        private boolean empty() {
            return entities.isEmpty() && blockEntities.isEmpty();
        }
    }

    private record EntityCandidate(Entity entity, double influence) {
    }

    @SuppressWarnings("rawtypes")
    private record BlockEntityCandidate(BlockEntity blockEntity,
                                        BlockEntityRenderer renderer,
                                        boolean offscreen,
                                        double distanceSq) {
    }

    private record ChunkCandidate(int x, int z, double distanceSq) {
    }
}
