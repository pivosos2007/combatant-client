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
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.SampledTextureBinding;
import combatant.client.render.engine.rhi.shader.ShaderResourceKind;
import combatant.client.render.engine.rhi.shader.ShaderResourceLayout;
import combatant.client.render.engine.rhi.shader.ShaderResourceSlot;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBinding;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.rhi.shader.StorageImageBinding;
import combatant.client.render.engine.world.DynamicLightProvider;
import combatant.client.render.engine.world.DynamicLightRegistry;
import combatant.client.render.engine.world.LightDescriptor;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import combatant.client.render.sodium.SodiumTerrainSubmission;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explicit analytic-light GPU path: provider descriptors -> stable local shadow atlas -> tiled lists
 * -> material-aware local GGX radiance.
 */
final class DeferredDynamicLightSource implements AutoCloseable {
    static final int MAX_LIGHTS = 256;
    static final int TILE_SIZE = 16;
    static final int MAX_LIGHTS_PER_TILE = 64;
    private static final int LOCAL_SIZE = 8;
    private static final int POINT_SHADOW_FACE_COUNT = 6;
    private static final Vector4f CLEAR_COLOR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private static final Identifier CULL_SHADER = id("deferred/local_light_cull");
    private static final Identifier SHADE_SHADER = id("deferred/local_light_shade");

    private static final Std430StructLayout LIGHT_LAYOUT = Std430StructLayout.builder()
            .member("positionRadius", Std430Type.VEC4)
            .member("radianceType", Std430Type.VEC4)
            .member("directionOuter", Std430Type.VEC4)
            .member("coneArea", Std430Type.VEC4)
            .member("shadowInfo", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout CULL_DATA_LAYOUT = Std430StructLayout.builder()
            .member("projection", Std430Type.MAT4)
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("viewportLightCount", Std430Type.VEC4)
            .member("tileGrid", Std430Type.VEC4)
            .member("shadowParams", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout SHADOW_VIEW_LAYOUT = Std430StructLayout.builder()
            .member("viewProjection", Std430Type.MAT4)
            .member("atlasScaleBias", Std430Type.VEC4)
            .member("originNear", Std430Type.VEC4)
            .member("rangeTexel", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout UINT_LAYOUT = Std430StructLayout.builder()
            .member("value", Std430Type.UINT)
            .build();

    private static final ShaderResourceLayout CULL_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout SHADE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private final DeferredSecondaryShadowCasterSource secondaryCasters;
    private RhiComputePipeline cullPipeline;
    private RhiComputePipeline shadePipeline;
    private RhiStorageBuffer lightData;
    private RhiStorageBuffer cullData;
    private RhiStorageBuffer tileCounts;
    private RhiStorageBuffer tileIndices;
    private RhiStorageBuffer shadowData;
    private TextureTarget shadowAtlas;
    private int shadowAtlasWidth;
    private int shadowAtlasHeight;
    private int tileCountX;
    private int tileCountY;
    private int uploadedLightCount;

    private long collectedFrameId = Long.MIN_VALUE;
    private List<LightDescriptor> frameLights = List.of();
    private Map<Long, ShadowAllocation> shadowAllocations = Map.of();
    private Map<Long, ShadowAllocation> publishedShadowAllocations = Map.of();
    private long publishedShadowFrameId = Long.MIN_VALUE;

    DeferredDynamicLightSource(DeferredSecondaryShadowCasterSource secondaryCasters) {
        this.secondaryCasters = secondaryCasters;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.local-light.collect", DeferredStage.SHADOW_PREPARE)
                .feature(DeferredFeature.DYNAMIC_LIGHTS)
                .priority(100)
                .when(context -> context.primaryView().current() != null)
                .execute(this::collectFrame)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.shadow-map", DeferredStage.SHADOW_MAP)
                .feature(DeferredFeature.DYNAMIC_LIGHTS)
                .priority(100)
                .write(DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH, DeferredResource.LOCAL_LIGHT_SHADOW_DATA)
                .when(context -> context.primaryView().current() != null
                        && collectedFrameId == context.frame().frameId())
                .execute(this::renderShadowAtlas)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.prepare", DeferredStage.POST_LIGHTING)
                .feature(DeferredFeature.DYNAMIC_LIGHTS)
                .priority(0)
                .read(DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH, DeferredResource.LOCAL_LIGHT_SHADOW_DATA)
                .write(DeferredResource.LOCAL_LIGHT_DATA, DeferredResource.LOCAL_LIGHT_CULL_DATA)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null)
                .execute(this::prepareFrame)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.cull", DeferredStage.POST_LIGHTING)
                .feature(DeferredFeature.DYNAMIC_LIGHTS)
                .priority(10)
                .read(DeferredResource.LOCAL_LIGHT_DATA, DeferredResource.LOCAL_LIGHT_CULL_DATA)
                .write(DeferredResource.LOCAL_LIGHT_TILE_COUNTS, DeferredResource.LOCAL_LIGHT_TILE_INDICES)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.LOCAL_LIGHT_DATA)
                        && context.isValid(DeferredResource.LOCAL_LIGHT_CULL_DATA))
                .execute(this::cull)
                .build());
        passes.add(DeferredPassSpec.builder("world.local-light.shade", DeferredStage.POST_LIGHTING)
                .feature(DeferredFeature.DYNAMIC_LIGHTS)
                .priority(20)
                .read(DeferredResource.GBUFFER_SURFACE, DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL, DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH, DeferredResource.LOCAL_LIGHT_DATA,
                        DeferredResource.LOCAL_LIGHT_CULL_DATA, DeferredResource.LOCAL_LIGHT_TILE_COUNTS,
                        DeferredResource.LOCAL_LIGHT_TILE_INDICES, DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH,
                        DeferredResource.LOCAL_LIGHT_SHADOW_DATA)
                .write(DeferredResource.LOCAL_LIGHTING_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_SURFACE) != null
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                        && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null
                        && context.isValid(DeferredResource.LOCAL_LIGHT_TILE_COUNTS)
                        && context.isValid(DeferredResource.LOCAL_LIGHT_TILE_INDICES))
                .execute(this::shade)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        ensureStaticBuffers();
        cullPipeline();
        shadePipeline();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void collectFrame(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;
        long frameId = context.frame().frameId();
        if (collectedFrameId == frameId) return;

        ArrayList<LightDescriptor> collected = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft == null ? null : minecraft.level;
        DynamicLightRegistry.collect(new DynamicLightProvider.Context(
                level, context.worldState(), view.cameraPosition(), frameId, context.frame().tickProgress()
        ), light -> {
            if (light != null && light.valid() && intersectsPrimaryFrustum(light, view)) collected.add(light);
        });

        Vec3 camera = view.cameraPosition();
        collected.sort(Comparator
                .comparingDouble((LightDescriptor light) -> -priority(light, camera))
                .thenComparingLong(LightDescriptor::stableId));
        if (collected.size() > MAX_LIGHTS) collected.subList(MAX_LIGHTS, collected.size()).clear();

        frameLights = List.copyOf(collected);
        collectedFrameId = frameId;
        selectLocalShadowViews(context, camera);
    }

    private void selectLocalShadowViews(DeferredPassContext context, Vec3 camera) {
        DeferredLocalShadowConfig config = DeferredLocalShadowConfig.current();
        if (!config.enabled() || !context.featureEnabled(DeferredFeature.SHADOWS) || frameLights.isEmpty()) {
            shadowAllocations = Map.of();
            return;
        }

        Map<Long, ShadowAllocation> previous = shadowAllocations;
        ArrayList<ShadowCandidate> candidates = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        for (LightDescriptor light : frameLights) {
            if (!light.castsShadow() || light.stableId() == 0L || !seenIds.add(light.stableId())) continue;
            int viewCost = shadowViewCount(light);
            if (viewCost <= 0 || viewCost > config.atlasViewBudget()) continue;
            double score = priority(light, camera);
            if (previous.containsKey(light.stableId())) score *= config.retentionBoost();
            candidates.add(new ShadowCandidate(light, viewCost, score));
        }
        candidates.sort(Comparator
                .comparingDouble((ShadowCandidate candidate) -> -candidate.score())
                .thenComparingLong(candidate -> candidate.light().stableId()));

        ArrayList<ShadowCandidate> selected = new ArrayList<>();
        int remaining = config.atlasViewBudget();
        for (ShadowCandidate candidate : candidates) {
            if (candidate.viewCount() > remaining) continue;
            selected.add(candidate);
            remaining -= candidate.viewCount();
        }

        boolean[] occupied = new boolean[config.atlasViewBudget()];
        LinkedHashMap<Long, ShadowAllocation> next = new LinkedHashMap<>();

        // Retain a previous contiguous span whenever it still fits. Slot identity is therefore
        // stable across small score changes and does not flicker merely because list order changed.
        for (ShadowCandidate candidate : selected) {
            ShadowAllocation old = previous.get(candidate.light().stableId());
            if (old == null || old.viewCount() != candidate.viewCount()
                    || !spanFree(occupied, old.baseView(), old.viewCount())) continue;
            occupy(occupied, old.baseView(), old.viewCount());
            next.put(candidate.light().stableId(), old);
        }
        for (ShadowCandidate candidate : selected) {
            long id = candidate.light().stableId();
            if (next.containsKey(id)) continue;
            int base = findSpan(occupied, candidate.viewCount());
            if (base < 0) continue;
            occupy(occupied, base, candidate.viewCount());
            next.put(id, new ShadowAllocation(base, candidate.viewCount()));
        }
        shadowAllocations = Map.copyOf(next);

        ArrayList<Map.Entry<Long, ShadowAllocation>> ordered = new ArrayList<>(next.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> entry.getValue().baseView()));
        Map<Long, LightDescriptor> lightsById = new HashMap<>();
        for (LightDescriptor light : frameLights) lightsById.putIfAbsent(light.stableId(), light);
        for (Map.Entry<Long, ShadowAllocation> entry : ordered) {
            LightDescriptor light = lightsById.get(entry.getKey());
            if (light == null) continue;
            registerShadowViews(context, light, entry.getValue(), config);
        }
    }

    private void registerShadowViews(DeferredPassContext context,
                                     LightDescriptor light,
                                     ShadowAllocation allocation,
                                     DeferredLocalShadowConfig config) {
        int faceCount = allocation.viewCount();
        for (int face = 0; face < faceCount; face++) {
            int slot = allocation.baseView() + face;
            int viewportX = (slot % config.atlasColumns()) * config.faceResolution();
            int viewportY = (slot / config.atlasColumns()) * config.faceResolution();
            context.secondaryViews().register(buildShadowView(
                    light, face, slot, config.faceResolution(), viewportX, viewportY, config.nearPlane(),
                    config.pointFaceFovDegrees(), context.rhi().capabilities().zeroToOneDepth()
            ));
        }
    }

    private static DeferredSecondaryView buildShadowView(LightDescriptor light,
                                                          int face,
                                                          int slot,
                                                          int resolution,
                                                          int viewportX,
                                                          int viewportY,
                                                          float configuredNearPlane,
                                                          float pointFaceFovDegrees,
                                                          boolean zeroToOneDepth) {
        Vector3f direction;
        Vector3f up;
        float fov;
        if (light.type() == LightDescriptor.Type.SPOT) {
            direction = new Vector3f(light.directionX(), light.directionY(), light.directionZ());
            if (direction.lengthSquared() <= 1.0e-8f) direction.set(0.0f, -1.0f, 0.0f);
            direction.normalize();
            up = Math.abs(direction.y) > 0.95f
                    ? new Vector3f(0.0f, 0.0f, 1.0f)
                    : new Vector3f(0.0f, 1.0f, 0.0f);
            float outer = Math.max(-0.999f, Math.min(0.9999f, light.outerConeCos()));
            fov = Math.max((float) Math.toRadians(1.0), Math.min((float) Math.toRadians(175.0), 2.0f * (float) Math.acos(outer)));
        } else {
            direction = cubeDirection(face);
            up = cubeUp(face);
            fov = (float) Math.toRadians(pointFaceFovDegrees);
        }

        float nearPlane = Math.min(configuredNearPlane, Math.max(0.001f, light.radius() * 0.001f));
        float farPadding = Math.max(0.25f, light.radius() * 0.05f);
        float farPlane = Math.max(nearPlane + 0.01f, light.radius() + farPadding);
        Matrix4f view = new Matrix4f().lookAt(new Vector3f(), new Vector3f(direction), up);
        // World rendering is reversed-Z; swapping geometric near/far keeps the shadow target in the
        // same GREATER/GEQUAL convention as the primary/deferred depth resources.
        Matrix4f projection = new Matrix4f().setPerspective(
                fov, 1.0f, farPlane, nearPlane, zeroToOneDepth
        );
        Vec3 origin = new Vec3(light.x(), light.y(), light.z());
        return new DeferredSecondaryView(
                DeferredViewFamily.LOCAL_LIGHT_SHADOW,
                slot,
                "local-light.shadow." + Long.toUnsignedString(light.stableId()) + ".face." + face,
                view,
                projection,
                origin,
                0,
                viewportX,
                viewportY,
                resolution,
                resolution,
                nearPlane,
                farPlane
        );
    }

    private void renderShadowAtlas(DeferredPassContext context) {
        RenderSystem.assertOnRenderThread();
        ensureOwner(context.rhi());
        ensureStaticBuffers();
        if (collectedFrameId != context.frame().frameId()) collectFrame(context);

        DeferredLocalShadowConfig config = DeferredLocalShadowConfig.current();
        List<DeferredSecondaryView> views = context.secondaryViews().views(DeferredViewFamily.LOCAL_LIGHT_SHADOW);
        SodiumTerrainSubmission primarySubmission = CombatantRenderSystem.sodium().terrainInterop().currentSubmission();
        SodiumWorldRenderer renderer = SodiumWorldRenderer.instanceNullable();
        boolean canRender = config.enabled() && context.featureEnabled(DeferredFeature.SHADOWS)
                && !views.isEmpty() && primarySubmission != null && renderer != null;

        TextureTarget target = canRender
                ? ensureShadowAtlas(config.atlasWidth(), config.atlasHeight())
                : (shadowAtlas != null ? shadowAtlas : ensureShadowAtlas(1, 1));

        if (canRender) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(
                    target.getColorTexture(), CLEAR_COLOR,
                    target.getDepthTexture(), 0.0
            );
            for (DeferredSecondaryView view : views) {
                LightDescriptor shadowLight = shadowLightForView(view.index());
                secondaryCasters.render(
                        context,
                        SodiumSecondaryTerrainContext.Purpose.LOCAL_LIGHT_SHADOW,
                        view,
                        target,
                        renderer,
                        primarySubmission,
                        shadowLight == null ? -1 : shadowLight.shadowCasterExclusionEntityId()
                );
            }
            publishedShadowAllocations = shadowAllocations;
        } else {
            publishedShadowAllocations = Map.of();
        }
        publishedShadowFrameId = context.frame().frameId();

        uploadShadowMetadata(context, canRender ? views : List.of(), shadowAtlasWidth, shadowAtlasHeight);
        context.resources().bindTexture(DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH, target.getDepthTextureView());
        context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_SHADOW_DATA, shadowData);
    }

    private void uploadShadowMetadata(DeferredPassContext context,
                                      List<DeferredSecondaryView> views,
                                      int atlasWidth,
                                      int atlasHeight) {
        DeferredLocalShadowConfig config = DeferredLocalShadowConfig.current();
        Std430Writer writer = new Std430Writer(SHADOW_VIEW_LAYOUT, config.atlasViewBudget());
        Vec3 camera = context.primaryView().current() == null
                ? Vec3.ZERO : context.primaryView().current().cameraPosition();
        for (DeferredSecondaryView view : views) {
            int slot = view.index();
            if (slot < 0 || slot >= config.atlasViewBudget()) continue;
            Vec3 delta = view.origin().subtract(camera);
            float tanHalfFov = perspectiveTanHalfFov(view.projection());
            writer.putMat4(slot, "viewProjection", view.viewProjection())
                    .putVec4(slot, "atlasScaleBias",
                            (float) view.viewportWidth() / Math.max(1.0f, atlasWidth),
                            (float) view.viewportHeight() / Math.max(1.0f, atlasHeight),
                            (float) view.viewportX() / Math.max(1.0f, atlasWidth),
                            (float) view.viewportY() / Math.max(1.0f, atlasHeight))
                    .putVec4(slot, "originNear",
                            (float) delta.x, (float) delta.y, (float) delta.z, view.nearPlane())
                    .putVec4(slot, "rangeTexel",
                            view.farPlane(),
                            1.0f / Math.max(1.0f, atlasWidth),
                            1.0f / Math.max(1.0f, atlasHeight),
                            tanHalfFov);
        }
        shadowData.upload(writer.buffer(), 0L);
    }

    private void prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureStaticBuffers();
        if (collectedFrameId != context.frame().frameId()) collectFrame(context);
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null) return;

        GpuTextureView reference = context.resources().texture(DeferredResource.SCENE_COLOR);
        if (reference == null) reference = context.resources().texture(DeferredResource.RESOLVED_DEPTH);
        if (reference == null) return;
        int width = reference.getWidth(0);
        int height = reference.getHeight(0);
        ensureTileBuffers(width, height);

        uploadedLightCount = Math.min(MAX_LIGHTS, frameLights.size());
        boolean shadowFrameValid = publishedShadowFrameId == context.frame().frameId();
        Map<Long, ShadowAllocation> activeShadows = shadowFrameValid
                ? publishedShadowAllocations : Map.of();

        Std430Writer lightWriter = new Std430Writer(LIGHT_LAYOUT, MAX_LIGHTS);
        Vec3 camera = view.cameraPosition();
        Matrix4f viewMatrix = view.view();
        for (int i = 0; i < uploadedLightCount; i++) {
            LightDescriptor light = frameLights.get(i);
            Vector3f position = new Vector3f(
                    (float) (light.x() - camera.x),
                    (float) (light.y() - camera.y),
                    (float) (light.z() - camera.z)
            );
            viewMatrix.transformPosition(position);
            Vector3f direction = new Vector3f(light.directionX(), light.directionY(), light.directionZ());
            if (direction.lengthSquared() <= 1e-8f) direction.set(0.0f, -1.0f, 0.0f);
            direction.normalize();
            viewMatrix.transformDirection(direction).normalize();

            ShadowAllocation shadow = activeShadows.get(light.stableId());
            float shadowBase = shadow == null ? -1.0f : shadow.baseView();
            float shadowCount = shadow == null ? 0.0f : shadow.viewCount();
            lightWriter.putVec4(i, "positionRadius", position.x, position.y, position.z, light.radius())
                    .putVec4(i, "radianceType", light.red(), light.green(), light.blue(), light.type().ordinal())
                    .putVec4(i, "directionOuter", direction.x, direction.y, direction.z, light.outerConeCos())
                    .putVec4(i, "coneArea", light.innerConeCos(), light.areaRadius(), 0.0f, 0.0f)
                    .putVec4(i, "shadowInfo", shadowBase, shadowCount, light.castsShadow() ? 1.0f : 0.0f, 0.0f);
        }
        lightData.upload(lightWriter.buffer(), 0L);

        DeferredLocalShadowConfig shadowConfig = DeferredLocalShadowConfig.current();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer cullWriter = new Std430Writer(CULL_DATA_LAYOUT, 1)
                .putMat4(0, "projection", view.projection())
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "viewportLightCount", width, height, uploadedLightCount, TILE_SIZE)
                .putVec4(0, "tileGrid", tileCountX, tileCountY, MAX_LIGHTS_PER_TILE, zeroToOne ? 1.0f : 0.0f)
                .putVec4(0, "shadowParams",
                        shadowConfig.normalOffsetTexels(),
                        shadowConfig.receiverBiasTexels(),
                        shadowConfig.filterRadiusTexels(),
                        shadowConfig.faceResolution());
        cullData.upload(cullWriter.buffer(), 0L);

        bindBuffers(context);
    }

    private void cull(DeferredPassContext context) {
        ensureOwner(context.rhi());
        bindBuffers(context);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant tiled local-light culling",
                cullPipeline(), Math.max(1, tileCountX), Math.max(1, tileCountY), 1,
                List.of(
                        new StorageBinding(0, lightData, 0L, lightData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(1, cullData, 0L, cullData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(2, tileCounts, 0L, tileCounts.descriptor().byteSize(), StorageAccess.WRITE_ONLY),
                        new StorageBinding(3, tileIndices, 0L, tileIndices.descriptor().byteSize(), StorageAccess.WRITE_ONLY)
                ),
                List.of(), List.of()
        ));
    }

    private void shade(DeferredPassContext context) {
        ensureOwner(context.rhi());
        bindBuffers(context);
        GpuTextureView surface = requireTexture(context, DeferredResource.GBUFFER_SURFACE);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView material = requireTexture(context, DeferredResource.GBUFFER_MATERIAL);
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView shadowDepth = context.resources().texture(DeferredResource.LOCAL_LIGHT_SHADOW_DEPTH);
        if (shadowDepth == null) shadowDepth = depth;
        RhiStorageImage output = requireImage(context, DeferredResource.LOCAL_LIGHTING_COLOR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant deferred local-light shading",
                shadePipeline(), groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(
                        new StorageBinding(6, lightData, 0L, lightData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(7, tileCounts, 0L, tileCounts.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(8, tileIndices, 0L, tileIndices.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(9, cullData, 0L, cullData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(11, shadowData, 0L, shadowData.descriptor().byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, surface, nearest),
                        new SampledTextureBinding(1, geometry, nearest),
                        new SampledTextureBinding(2, material, nearest),
                        new SampledTextureBinding(3, depth, nearest),
                        new SampledTextureBinding(4, gbufferDepth, nearest),
                        new SampledTextureBinding(10, shadowDepth, nearest)
                ),
                List.of(new StorageImageBinding(5, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void bindBuffers(DeferredPassContext context) {
        if (lightData != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_DATA, lightData);
        if (cullData != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_CULL_DATA, cullData);
        if (tileCounts != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_TILE_COUNTS, tileCounts);
        if (tileIndices != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_TILE_INDICES, tileIndices);
        if (shadowData != null) context.resources().bindBuffer(DeferredResource.LOCAL_LIGHT_SHADOW_DATA, shadowData);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureStaticBuffers() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (lightData == null) {
            lightData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-local-light-data", LIGHT_LAYOUT, MAX_LIGHTS, StorageAccess.READ_ONLY, false
            ));
        }
        if (cullData == null) {
            cullData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-local-light-cull-data", CULL_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        if (shadowData == null) {
            shadowData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-local-light-shadow-data",
                    SHADOW_VIEW_LAYOUT,
                    DeferredLocalShadowConfig.current().atlasViewBudget(),
                    StorageAccess.READ_ONLY,
                    false
            ));
        }
    }

    private void ensureTileBuffers(int width, int height) {
        int nextX = Math.max(1, (Math.max(width, 1) + TILE_SIZE - 1) / TILE_SIZE);
        int nextY = Math.max(1, (Math.max(height, 1) + TILE_SIZE - 1) / TILE_SIZE);
        if (tileCounts != null && tileIndices != null && nextX == tileCountX && nextY == tileCountY) return;
        close(tileCounts); tileCounts = null;
        close(tileIndices); tileIndices = null;
        tileCountX = nextX;
        tileCountY = nextY;
        int tiles = Math.multiplyExact(tileCountX, tileCountY);
        tileCounts = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-local-light-tile-counts", UINT_LAYOUT, tiles, StorageAccess.READ_WRITE, false
        ));
        tileIndices = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-local-light-tile-indices", UINT_LAYOUT,
                Math.multiplyExact(tiles, MAX_LIGHTS_PER_TILE), StorageAccess.READ_WRITE, false
        ));
    }

    private TextureTarget ensureShadowAtlas(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (shadowAtlas != null && shadowAtlasWidth == width && shadowAtlasHeight == height) return shadowAtlas;
        releaseShadowAtlas();
        shadowAtlas = new TextureTarget(
                "combatant-local-light-shadow-atlas",
                width, height, true,
                GpuFormat.RGBA8_UNORM
        );
        shadowAtlasWidth = width;
        shadowAtlasHeight = height;
        return shadowAtlas;
    }

    private RhiComputePipeline cullPipeline() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (cullPipeline == null) {
            cullPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-local-light-cull", CULL_SHADER, CULL_LAYOUT
            ));
        }
        return cullPipeline;
    }

    private RhiComputePipeline shadePipeline() {
        if (owner == null) throw new IllegalStateException("Dynamic-light source has no RHI owner");
        if (shadePipeline == null) {
            shadePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-local-light-shade", SHADE_SHADER, SHADE_LAYOUT
            ));
        }
        return shadePipeline;
    }

    private void closeOwned() {
        close(cullPipeline); cullPipeline = null;
        close(shadePipeline); shadePipeline = null;
        close(lightData); lightData = null;
        close(cullData); cullData = null;
        close(tileCounts); tileCounts = null;
        close(tileIndices); tileIndices = null;
        close(shadowData); shadowData = null;
        releaseShadowAtlas();
        tileCountX = 0;
        tileCountY = 0;
        uploadedLightCount = 0;
        collectedFrameId = Long.MIN_VALUE;
        publishedShadowFrameId = Long.MIN_VALUE;
        frameLights = List.of();
        shadowAllocations = Map.of();
        publishedShadowAllocations = Map.of();
    }

    private void releaseShadowAtlas() {
        if (shadowAtlas != null) {
            shadowAtlas.destroyBuffers();
            shadowAtlas = null;
        }
        shadowAtlasWidth = 0;
        shadowAtlasHeight = 0;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private LightDescriptor shadowLightForView(int slot) {
        for (LightDescriptor light : frameLights) {
            ShadowAllocation allocation = shadowAllocations.get(light.stableId());
            if (allocation == null) continue;
            if (slot >= allocation.baseView() && slot < allocation.baseView() + allocation.viewCount()) return light;
        }
        return null;
    }

    private static boolean intersectsPrimaryFrustum(LightDescriptor light, DeferredPrimaryViewSource.FrameView view) {
        Vec3 camera = view.cameraPosition();
        double dx = light.x() - camera.x;
        double dy = light.y() - camera.y;
        double dz = light.z() - camera.z;
        float radius = light.radius();
        if (dx * dx + dy * dy + dz * dz <= (double) radius * radius) return true;

        Vector3f position = new Vector3f((float) dx, (float) dy, (float) dz);
        view.view().transformPosition(position);
        float depth = -position.z;
        if (depth + radius <= 1.0e-4f) return false;
        if (view.farPlane() > 0.0f && depth - radius > view.farPlane()) return false;

        Matrix4f projection = view.unjitteredProjection();
        Vector4f clip = new Vector4f(position, 1.0f);
        projection.transform(clip);
        if (Math.abs(clip.w) <= 1.0e-6f) return false;
        float centerX = clip.x / clip.w;
        float centerY = clip.y / clip.w;
        float safeDepth = Math.max(depth, 1.0e-4f);
        float extentX = Math.abs(projection.m00()) * radius / safeDepth;
        float extentY = Math.abs(projection.m11()) * radius / safeDepth;
        return centerX + extentX >= -1.0f && centerX - extentX <= 1.0f
                && centerY + extentY >= -1.0f && centerY - extentY <= 1.0f;
    }

    private static int shadowViewCount(LightDescriptor light) {
        if (light == null) return 0;
        return light.type() == LightDescriptor.Type.SPOT ? 1 : POINT_SHADOW_FACE_COUNT;
    }

    private static double priority(LightDescriptor light, Vec3 camera) {
        double dx = light.x() - camera.x;
        double dy = light.y() - camera.y;
        double dz = light.z() - camera.z;
        double distanceSquared = Math.max(1.0, dx * dx + dy * dy + dz * dz);
        double luminance = 0.2126 * light.red() + 0.7152 * light.green() + 0.0722 * light.blue();
        return luminance * light.radius() * light.radius() / distanceSquared;
    }

    private static boolean spanFree(boolean[] occupied, int start, int count) {
        if (start < 0 || count <= 0 || start + count > occupied.length) return false;
        for (int i = start; i < start + count; i++) if (occupied[i]) return false;
        return true;
    }

    private static void occupy(boolean[] occupied, int start, int count) {
        for (int i = start; i < start + count; i++) occupied[i] = true;
    }

    private static int findSpan(boolean[] occupied, int count) {
        for (int start = 0; start + count <= occupied.length; start++) {
            if (spanFree(occupied, start, count)) return start;
        }
        return -1;
    }

    private static Vector3f cubeDirection(int face) {
        return switch (face) {
            case 0 -> new Vector3f(1.0f, 0.0f, 0.0f);
            case 1 -> new Vector3f(-1.0f, 0.0f, 0.0f);
            case 2 -> new Vector3f(0.0f, 1.0f, 0.0f);
            case 3 -> new Vector3f(0.0f, -1.0f, 0.0f);
            case 4 -> new Vector3f(0.0f, 0.0f, 1.0f);
            default -> new Vector3f(0.0f, 0.0f, -1.0f);
        };
    }

    private static Vector3f cubeUp(int face) {
        return switch (face) {
            case 2 -> new Vector3f(0.0f, 0.0f, 1.0f);
            case 3 -> new Vector3f(0.0f, 0.0f, -1.0f);
            default -> new Vector3f(0.0f, -1.0f, 0.0f);
        };
    }

    private static float perspectiveTanHalfFov(Matrix4f projection) {
        float m00 = Math.abs(projection.m00());
        return m00 > 1.0e-6f ? 1.0f / m00 : 1.0f;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return value;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return value;
    }

    private static int groups(int extent) {
        return Math.max(1, (Math.max(1, extent) + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private record ShadowAllocation(int baseView, int viewCount) {
    }

    private record ShadowCandidate(LightDescriptor light, int viewCount, double score) {
    }
}
