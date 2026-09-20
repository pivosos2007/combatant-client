/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.render.sodium.SodiumTerrainSubmission;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera-centered off-screen reflection hierarchy used when screen-space tracing has no hit.
 *
 * <p>Capture cadence/count/face resolution are runtime policy. The atlas survives skipped capture
 * frames and is re-published with metadata relative to the current primary camera, so lowering the
 * update rate never turns the graph resource into an invalid one. The source remains backend data;
 * denoise, roughness response and final blend policy belong to later passes.</p>
 */
final class DeferredReflectionCascadeSource implements AutoCloseable {
    static final int FACES_PER_CASCADE = 6;
    static final int MAX_CASCADE_COUNT = 4;
    private static final float CAPTURE_NEAR = 0.05f;
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    private static final Std430StructLayout FACE_LAYOUT = Std430StructLayout.builder()
            .member("viewProjection", Std430Type.MAT4)
            .member("atlasScaleBias", Std430Type.VEC4)
            .member("rangeFace", Std430Type.VEC4)
            .member("originDelta", Std430Type.VEC4)
            .build();

    private TextureTarget atlas;
    private int atlasWidth;
    private int atlasHeight;
    private TextureTarget captureScratch;
    private int captureScratchWidth;
    private int captureScratchHeight;
    private CombatantRhi bufferOwner;
    private RhiStorageBuffer faceData;
    private List<DeferredSecondaryView> capturedViews = List.of();
    private long lastCaptureFrame = Long.MIN_VALUE;
    private int capturedCascadeCount;
    private int capturedFaceResolution;
    private float capturedFarDistance;
    private Object captureWorld;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.reflection.cascade.prepare", DeferredStage.REFLECTION_CAPTURE_PREPARE)
                .feature(DeferredFeature.REFLECTIONS)
                .when(context -> context.primaryView().current() != null)
                .execute(this::prepareViews)
                .build());
        passes.add(DeferredPassSpec.builder("world.reflection.cascade.capture", DeferredStage.REFLECTION_CAPTURE)
                .feature(DeferredFeature.REFLECTIONS)
                .write(DeferredResource.REFLECTION_CASCADE_COLOR,
                        DeferredResource.REFLECTION_CASCADE_DEPTH,
                        DeferredResource.REFLECTION_CASCADE_DATA)
                .when(this::canPublish)
                .execute(this::renderOrPublish)
                .build());
    }

    private void prepareViews(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        if (primary == null) return;

        Object currentWorld = Minecraft.getInstance().level;
        if (captureWorld != currentWorld) {
            releaseCaptureOnly();
            captureWorld = currentWorld;
        }

        DeferredRuntimeConfig.Snapshot settings = context.settings();
        if (!settings.reflectionsEnabled()) {
            releaseCaptureOnly();
            return;
        }
        int cascadeCount = settings.reflectionCascadeCount();
        if (cascadeCount <= 0) {
            releaseCaptureOnly();
            return;
        }

        int resolution = settings.reflectionCascadeFaceResolution();
        float farDistance = captureFarDistance(primary, settings);
        long frameId = context.frame().frameId();
        boolean shapeChanged = capturedCascadeCount != cascadeCount
                || capturedFaceResolution != resolution
                || relativeDifference(capturedFarDistance, farDistance) > 0.01f;
        boolean cadenceElapsed = lastCaptureFrame == Long.MIN_VALUE
                || frameId - lastCaptureFrame >= settings.reflectionCascadeUpdateIntervalFrames();
        if (atlas != null && !shapeChanged && !cadenceElapsed) return;

        for (int cascade = 0; cascade < cascadeCount; cascade++) {
            float t = (float) (cascade + 1) / (float) cascadeCount;
            float cascadeFar = (float) (CAPTURE_NEAR * Math.pow(farDistance / CAPTURE_NEAR, t));
            for (int face = 0; face < FACES_PER_CASCADE; face++) {
                int index = cascade * FACES_PER_CASCADE + face;
                int tileX = face % 3;
                int tileY = face / 3 + cascade * 2;
                context.secondaryViews().register(buildFace(
                        primary.cameraPosition(), cascade, face, index,
                        cascadeFar, resolution,
                        tileX * resolution, tileY * resolution,
                        context.rhi().capabilities().zeroToOneDepth()
                ));
            }
        }
    }

    private static float captureFarDistance(DeferredPrimaryViewSource.FrameView primary,
                                            DeferredRuntimeConfig.Snapshot settings) {
        float farDistance = primary.farPlane();
        if (!(farDistance > CAPTURE_NEAR)) {
            Minecraft minecraft = Minecraft.getInstance();
            farDistance = minecraft != null && minecraft.options != null
                    ? Math.max(32.0f, minecraft.options.getEffectiveRenderDistance() * 16.0f)
                    : 128.0f;
        }
        return Math.max(16.0f, farDistance * settings.reflectionCascadeDistanceScale());
    }

    private static DeferredSecondaryView buildFace(Vec3 origin,
                                                    int cascade,
                                                    int face,
                                                    int index,
                                                    float farPlane,
                                                    int resolution,
                                                    int viewportX,
                                                    int viewportY,
                                                    boolean zeroToOneDepth) {
        Vector3f direction = direction(face);
        Vector3f up = up(face);
        Matrix4f view = new Matrix4f().lookAt(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new Vector3f(direction),
                up
        );
        // Minecraft's world path uses reversed-Z. Swapping the geometric near/far arguments keeps
        // secondary depth in the same GREATER/GEQUAL convention as the primary scene.
        Matrix4f projection = new Matrix4f().setPerspective(
                (float) (Math.PI * 0.5), 1.0f, farPlane, CAPTURE_NEAR, zeroToOneDepth
        );
        return new DeferredSecondaryView(
                DeferredViewFamily.REFLECTION_CASCADE,
                index,
                "reflection.cascade." + cascade + ".face." + face,
                view,
                projection,
                origin,
                0,
                viewportX,
                viewportY,
                resolution,
                resolution,
                CAPTURE_NEAR,
                farPlane
        );
    }

    private boolean canPublish(DeferredPassContext context) {
        if (context == null || !context.featureEnabled(DeferredFeature.REFLECTIONS)
                || context.settings().reflectionCascadeCount() <= 0) return false;
        if (atlas != null && faceData != null && !capturedViews.isEmpty()) return true;
        if (!context.secondaryViews().has(DeferredViewFamily.REFLECTION_CASCADE)) return false;
        return CombatantRenderSystem.sodium().terrainInterop().currentSubmission() != null
                && SodiumWorldRenderer.instanceNullable() != null;
    }

    private void renderOrPublish(DeferredPassContext context) {
        RenderSystem.assertOnRenderThread();
        List<DeferredSecondaryView> requested = context.secondaryViews().views(DeferredViewFamily.REFLECTION_CASCADE);
        SodiumTerrainSubmission primarySubmission = CombatantRenderSystem.sodium().terrainInterop().currentSubmission();
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();

        if (!requested.isEmpty() && primarySubmission != null && renderer != null) {
            capture(context, requested, primarySubmission, renderer);
        }
        if (atlas == null || capturedViews.isEmpty()) return;

        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        if (primary == null) return;
        RhiStorageBuffer metadata = uploadMetadata(
                context.rhi(), capturedViews, atlasWidth, atlasHeight, primary.cameraPosition()
        );
        context.resources().bindTexture(DeferredResource.REFLECTION_CASCADE_COLOR, atlas.getColorTextureView());
        context.resources().bindTexture(DeferredResource.REFLECTION_CASCADE_DEPTH, atlas.getDepthTextureView());
        context.resources().bindBuffer(DeferredResource.REFLECTION_CASCADE_DATA, metadata);
    }

    private void capture(DeferredPassContext context,
                         List<DeferredSecondaryView> views,
                         SodiumTerrainSubmission primarySubmission,
                         SodiumWorldRenderer renderer) {
        int requiredWidth = 1;
        int requiredHeight = 1;
        for (DeferredSecondaryView view : views) {
            if (!view.hasExplicitViewport()) {
                throw new IllegalStateException("Reflection capture has no atlas viewport: " + view.id());
            }
            requiredWidth = Math.max(requiredWidth, view.viewportX() + view.viewportWidth());
            requiredHeight = Math.max(requiredHeight, view.viewportY() + view.viewportHeight());
        }

        TextureTarget target = ensureAtlas(requiredWidth, requiredHeight);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(
                target.getColorTexture(), CLEAR_COLOR,
                target.getDepthTexture(), 0.0
        );

        for (DeferredSecondaryView view : views) {
            int width = Math.max(1, view.viewportWidth());
            int height = Math.max(1, view.viewportHeight());
            TextureTarget scratch = ensureCaptureScratch(width, height);
            CommandEncoder scratchEncoder = RenderSystem.getDevice().createCommandEncoder();
            scratchEncoder.clearColorAndDepthTextures(
                    scratch.getColorTexture(), CLEAR_COLOR,
                    scratch.getDepthTexture(), 0.0
            );

            DeferredSecondaryView localView = view.withViewport(0, 0, width, height);
            ChunkRenderMatrices matrices = new ChunkRenderMatrices(localView.projection(), localView.view());
            SodiumSecondaryTerrainContext.run(
                    SodiumSecondaryTerrainContext.Purpose.REFLECTION_CAPTURE,
                    localView,
                    scratch,
                    () -> {
                        renderer.renderLayer(
                                matrices,
                                DefaultTerrainRenderPasses.SOLID,
                                localView.origin().x, localView.origin().y, localView.origin().z,
                                primarySubmission.fog(), primarySubmission.sampler()
                        );
                        renderer.renderLayer(
                                matrices,
                                DefaultTerrainRenderPasses.CUTOUT,
                                localView.origin().x, localView.origin().y, localView.origin().z,
                                primarySubmission.fog(), primarySubmission.sampler()
                        );
                    }
            );

            CommandEncoder copyEncoder = RenderSystem.getDevice().createCommandEncoder();
            copyEncoder.copyTextureToTexture(
                    scratch.getColorTexture(), target.getColorTexture(),
                    0, view.viewportX(), view.viewportY(), 0, 0, width, height
            );
            copyEncoder.copyTextureToTexture(
                    scratch.getDepthTexture(), target.getDepthTexture(),
                    0, view.viewportX(), view.viewportY(), 0, 0, width, height
            );
        }

        capturedViews = List.copyOf(views);
        capturedCascadeCount = context.settings().reflectionCascadeCount();
        capturedFaceResolution = context.settings().reflectionCascadeFaceResolution();
        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        capturedFarDistance = primary == null ? 0.0f : captureFarDistance(primary, context.settings());
        lastCaptureFrame = context.frame().frameId();
    }

    private TextureTarget ensureAtlas(int width, int height) {
        if (atlas != null && atlasWidth == width && atlasHeight == height) return atlas;
        releaseAtlas();
        atlas = new TextureTarget(
                "combatant-deferred-reflection-cascade-atlas",
                Math.max(1, width), Math.max(1, height), true,
                GpuFormat.RGBA16_FLOAT
        );
        atlasWidth = Math.max(1, width);
        atlasHeight = Math.max(1, height);
        return atlas;
    }

    private TextureTarget ensureCaptureScratch(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (captureScratch != null && captureScratchWidth == width && captureScratchHeight == height) {
            return captureScratch;
        }
        releaseCaptureScratch();
        captureScratch = new TextureTarget(
                "combatant-deferred-reflection-cascade-scratch",
                width, height, true,
                GpuFormat.RGBA16_FLOAT
        );
        captureScratchWidth = width;
        captureScratchHeight = height;
        return captureScratch;
    }

    private RhiStorageBuffer uploadMetadata(CombatantRhi rhi,
                                             List<DeferredSecondaryView> views,
                                             int width,
                                             int height,
                                             Vec3 currentCameraOrigin) {
        if (bufferOwner != rhi) {
            releaseBuffer();
            bufferOwner = rhi;
        }
        int maxFaces = MAX_CASCADE_COUNT * FACES_PER_CASCADE;
        if (faceData == null) {
            faceData = rhi.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-reflection-cascade-data",
                    FACE_LAYOUT,
                    maxFaces,
                    StorageAccess.READ_ONLY,
                    false
            ));
        }

        Std430Writer writer = new Std430Writer(FACE_LAYOUT, maxFaces);
        int count = Math.min(views.size(), maxFaces);
        for (int i = 0; i < count; i++) {
            DeferredSecondaryView view = views.get(i);
            Vec3 delta = view.origin().subtract(currentCameraOrigin);
            writer.putMat4(i, "viewProjection", view.viewProjection());
            writer.putVec4(i, "atlasScaleBias",
                    (float) view.viewportWidth() / (float) width,
                    (float) view.viewportHeight() / (float) height,
                    (float) view.viewportX() / (float) width,
                    (float) view.viewportY() / (float) height
            );
            writer.putVec4(i, "rangeFace",
                    view.nearPlane(), view.farPlane(),
                    (float) (view.index() % FACES_PER_CASCADE),
                    (float) count
            );
            writer.putVec4(i, "originDelta", (float) delta.x, (float) delta.y, (float) delta.z, 0.0f);
        }
        faceData.upload(writer.buffer(), 0L);
        return faceData;
    }

    WaterCascadeSnapshot waterSnapshot(Vec3 currentCameraOrigin) {
        if (atlas == null || capturedViews.isEmpty() || currentCameraOrigin == null) {
            return WaterCascadeSnapshot.EMPTY;
        }
        int count = Math.min(capturedViews.size(), MAX_CASCADE_COUNT * FACES_PER_CASCADE);
        ArrayList<WaterCascadeFace> faces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            DeferredSecondaryView view = capturedViews.get(i);
            Vec3 delta = view.origin().subtract(currentCameraOrigin);
            faces.add(new WaterCascadeFace(
                    view.viewProjection(),
                    new float[]{
                            (float) view.viewportWidth() / (float) atlasWidth,
                            (float) view.viewportHeight() / (float) atlasHeight,
                            (float) view.viewportX() / (float) atlasWidth,
                            (float) view.viewportY() / (float) atlasHeight
                    },
                    new float[]{view.nearPlane(), view.farPlane(),
                            (float) (view.index() % FACES_PER_CASCADE), (float) count},
                    new float[]{(float) delta.x, (float) delta.y, (float) delta.z, 0.0f}
            ));
        }
        return new WaterCascadeSnapshot(faces);
    }

    record WaterCascadeFace(Matrix4f viewProjection, float[] atlasScaleBias, float[] rangeFace, float[] originDelta) {
        WaterCascadeFace {
            viewProjection = new Matrix4f(viewProjection);
            atlasScaleBias = atlasScaleBias.clone();
            rangeFace = rangeFace.clone();
            originDelta = originDelta.clone();
        }
    }

    record WaterCascadeSnapshot(List<WaterCascadeFace> faces) {
        static final WaterCascadeSnapshot EMPTY = new WaterCascadeSnapshot(List.of());

        WaterCascadeSnapshot {
            faces = faces == null ? List.of() : List.copyOf(faces);
        }

        boolean valid() {
            return !faces.isEmpty();
        }
    }

    private static Vector3f direction(int face) {
        return switch (face) {
            case 0 -> new Vector3f(1.0f, 0.0f, 0.0f);
            case 1 -> new Vector3f(-1.0f, 0.0f, 0.0f);
            case 2 -> new Vector3f(0.0f, 1.0f, 0.0f);
            case 3 -> new Vector3f(0.0f, -1.0f, 0.0f);
            case 4 -> new Vector3f(0.0f, 0.0f, 1.0f);
            case 5 -> new Vector3f(0.0f, 0.0f, -1.0f);
            default -> throw new IllegalArgumentException("face=" + face);
        };
    }

    private static Vector3f up(int face) {
        return switch (face) {
            case 0, 1, 4, 5 -> new Vector3f(0.0f, -1.0f, 0.0f);
            case 2 -> new Vector3f(0.0f, 0.0f, 1.0f);
            case 3 -> new Vector3f(0.0f, 0.0f, -1.0f);
            default -> throw new IllegalArgumentException("face=" + face);
        };
    }

    private static float relativeDifference(float a, float b) {
        if (!(a > 0.0f) || !(b > 0.0f)) return a == b ? 0.0f : 1.0f;
        return Math.abs(a - b) / Math.max(a, b);
    }

    void release(CombatantRhi owner) {
        if (owner != null && bufferOwner != null && owner != bufferOwner) return;
        releaseBuffer();
        releaseAtlas();
        releaseCaptureScratch();
        capturedViews = List.of();
        lastCaptureFrame = Long.MIN_VALUE;
        capturedCascadeCount = 0;
        capturedFaceResolution = 0;
        capturedFarDistance = 0.0f;
        captureWorld = null;
    }

    private void releaseCaptureOnly() {
        releaseBuffer();
        releaseAtlas();
        releaseCaptureScratch();
        capturedViews = List.of();
        lastCaptureFrame = Long.MIN_VALUE;
        capturedCascadeCount = 0;
        capturedFaceResolution = 0;
        capturedFarDistance = 0.0f;
    }

    private void releaseBuffer() {
        if (faceData != null) {
            try {
                faceData.close();
            } catch (Throwable ignored) {
                // Device teardown is authoritative when the native object can no longer close.
            }
            faceData = null;
        }
        bufferOwner = null;
    }

    private void releaseAtlas() {
        if (atlas != null) {
            atlas.destroyBuffers();
            atlas = null;
        }
        atlasWidth = 0;
        atlasHeight = 0;
    }

    private void releaseCaptureScratch() {
        if (captureScratch != null) {
            captureScratch.destroyBuffers();
            captureScratch = null;
        }
        captureScratchWidth = 0;
        captureScratchHeight = 0;
    }

    @Override
    public void close() {
        release(null);
    }
}
