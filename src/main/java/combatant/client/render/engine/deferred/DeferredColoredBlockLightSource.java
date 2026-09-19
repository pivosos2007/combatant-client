/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.light.BlockLightEmitterRegistry;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiResourceBarrier;
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
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Renderer-owned Minecraft block-light propagation.
 *
 * <p>The CPU injects facts it already knows exactly: block emission and block light dampening.
 * Compute performs the spatial propagation in a camera-centered volume. This is not
 * scene-radiance GI and does not infer emitters from rendered color/material pixels.</p>
 */
final class DeferredColoredBlockLightSource implements AutoCloseable {
    private static final int VOLUME_LOCAL_SIZE = 4;
    private static final int SCREEN_LOCAL_SIZE = 8;
    private static final Identifier SEED_SHADER = id("deferred/block_light_seed");
    private static final Identifier PROPAGATE_SHADER = id("deferred/block_light_propagate");
    private static final Identifier RESOLVE_SHADER = id("deferred/block_light_resolve");

    private static final Std430StructLayout VOLUME_DATA_LAYOUT = Std430StructLayout.builder()
            .member("volumeSizeAndColumns", Std430Type.VEC4)
            .member("atlasSizeAndRows", Std430Type.VEC4)
            .member("originAndAttenuation", Std430Type.VEC4)
            .member("previousOriginAndHistory", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout RESOLVE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("depthTransform", Std430Type.VEC4)
            .member("volumeSizeAndColumns", Std430Type.VEC4)
            .member("atlasSizeAndRows", Std430Type.VEC4)
            .member("originAndSurfaceOffset", Std430Type.VEC4)
            .member("edgeFade", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout SEED_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout PROPAGATE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout RESOLVE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private final DeferredBlockLightConfig config = DeferredBlockLightConfig.current();

    private CombatantRhi owner;
    private Object worldOwner;
    private RhiComputePipeline seedPipeline;
    private RhiComputePipeline propagatePipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiStorageBuffer volumeData;
    private RhiStorageBuffer resolveData;
    private RhiStorageImage seedVolume;
    private RhiStorageImage radianceA;
    private RhiStorageImage radianceB;
    private RhiStorageImage finalRadiance;
    private ByteBuffer seedUpload;
    private long lastSeedRefreshFrame = Long.MIN_VALUE;
    private int originX = Integer.MIN_VALUE;
    private int originY = Integer.MIN_VALUE;
    private int originZ = Integer.MIN_VALUE;
    private int radianceOriginX = Integer.MIN_VALUE;
    private int radianceOriginY = Integer.MIN_VALUE;
    private int radianceOriginZ = Integer.MIN_VALUE;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.block-light.volume", DeferredStage.PRE_LIGHTING)
                .feature(DeferredFeature.COLORED_BLOCK_LIGHT)
                .priority(800)
                .write(DeferredResource.BLOCK_LIGHT_VOLUME_SEED, DeferredResource.BLOCK_LIGHT_VOLUME_RADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.COLORED_BLOCK_LIGHT) && context.primaryView().current() != null)
                .execute(this::updateVolume)
                .build());
        passes.add(DeferredPassSpec.builder("world.block-light.resolve", DeferredStage.PRE_LIGHTING)
                .feature(DeferredFeature.COLORED_BLOCK_LIGHT)
                .priority(875)
                .read(DeferredResource.BLOCK_LIGHT_VOLUME_RADIANCE,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.GBUFFER_GEOMETRY)
                .write(DeferredResource.BLOCK_LIGHT_IRRADIANCE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.featureEnabled(DeferredFeature.COLORED_BLOCK_LIGHT)
                        && context.primaryView().current() != null
                        && context.isValid(DeferredResource.BLOCK_LIGHT_VOLUME_RADIANCE)
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null)
                .execute(this::resolveToScreen)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        seedPipeline();
        propagatePipeline();
        resolvePipeline();
        volumeData();
        resolveData();
        ensureVolumeImages();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void updateVolume(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        ClientLevel level = Minecraft.getInstance().level;
        if (view == null || level == null) return;

        ensureVolumeImages();
        if (worldOwner != level) {
            worldOwner = level;
            resetVolumeState();
        }

        int[] nextOrigin = selectVolumeOrigin(view);
        int nextOriginX = nextOrigin[0];
        int nextOriginY = nextOrigin[1];
        int nextOriginZ = nextOrigin[2];
        boolean moved = nextOriginX != originX || nextOriginY != originY || nextOriginZ != originZ;
        boolean periodicRefresh = lastSeedRefreshFrame == Long.MIN_VALUE
                || context.frame().frameId() - lastSeedRefreshFrame >= config.seedRefreshIntervalFrames();

        if (moved || periodicRefresh) {
            originX = nextOriginX;
            originY = nextOriginY;
            originZ = nextOriginZ;
            uploadSeed(level);
            lastSeedRefreshFrame = context.frame().frameId();
        }

        // Persistent flood fill: keep the previous volume alive and advance only a
        // bounded number of iterations each frame. When the camera-centered volume moves, the
        // shader reprojects previous voxels by the exact integer origin delta instead of resetting.
        advancePropagation(context);

        if (finalRadiance != null) {
            context.resources().bindStorageImage(DeferredResource.BLOCK_LIGHT_VOLUME_SEED, seedVolume);
            context.resources().bindStorageImage(DeferredResource.BLOCK_LIGHT_VOLUME_RADIANCE, finalRadiance);
        }
    }

    private void uploadSeed(ClientLevel level) {
        ByteBuffer upload = seedUpload();
        int atlasWidth = config.atlasWidth();
        int atlasHeight = config.atlasHeight();
        int pixelCount = Math.multiplyExact(atlasWidth, atlasHeight);

        // Unused atlas texels and unavailable chunks behave as fully blocking, zero-emission cells.
        for (int pixel = 0; pixel < pixelCount; pixel++) {
            int offset = pixel * 16;
            upload.putFloat(offset, 0.0f);
            upload.putFloat(offset + 4, 0.0f);
            upload.putFloat(offset + 8, 0.0f);
            upload.putFloat(offset + 12, 1.0f);
        }

        BlockLightEmitterRegistry emitters = BlockLightEmitterRegistry.global();
        int columns = config.atlasColumns();
        for (int z = 0; z < config.sizeZ(); z++) {
            int worldZ = originZ + z;
            int chunkZ = worldZ >> 4;
            int localZ = worldZ & 15;
            int sliceColumn = z % columns;
            int sliceRow = z / columns;
            int cachedChunkX = Integer.MIN_VALUE;
            ChunkAccess chunk = null;
            for (int x = 0; x < config.sizeX(); x++) {
                int worldX = originX + x;
                int chunkX = worldX >> 4;
                int localX = worldX & 15;
                if (chunkX != cachedChunkX) {
                    cachedChunkX = chunkX;
                    chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                }
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                int cachedSectionY = Integer.MIN_VALUE;
                LevelChunkSection section = null;
                for (int y = 0; y < config.sizeY(); y++) {
                    int worldY = originY + y;
                    int atlasX = sliceColumn * config.sizeX() + x;
                    int atlasY = sliceRow * config.sizeY() + y;
                    int offset = (atlasY * atlasWidth + atlasX) * 16;
                    if (level.isOutsideBuildHeight(worldY)) continue;

                    int sectionY = SectionPos.blockToSectionCoord(worldY);
                    if (sectionY != cachedSectionY) {
                        cachedSectionY = sectionY;
                        int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
                        section = sectionIndex >= 0 && sectionIndex < sections.length
                                ? sections[sectionIndex] : null;
                    }
                    if (section == null) continue;

                    BlockState state = section.getBlockState(localX, worldY & 15, localZ);
                    BlockLightEmitterRegistry.ResolvedEmitter emitter = emitters.resolve(state);
                    float dampening = Math.max(0.0f, Math.min(1.0f, state.getLightDampening() / 15.0f));
                    upload.putFloat(offset, emitter.red());
                    upload.putFloat(offset + 4, emitter.green());
                    upload.putFloat(offset + 8, emitter.blue());
                    upload.putFloat(offset + 12, dampening);
                }
            }
        }

        ByteBuffer source = upload.duplicate().order(ByteOrder.nativeOrder());
        source.position(0).limit(upload.capacity());
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                seedVolume.view().texture(), source, 0, 0, 0, 0, atlasWidth, atlasHeight
        );
    }

    private void advancePropagation(DeferredPassContext context) {
        RhiStorageBuffer parameters = volumeData();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        int groupsX = groups(config.sizeX(), VOLUME_LOCAL_SIZE);
        int groupsY = groups(config.sizeY(), VOLUME_LOCAL_SIZE);
        int groupsZ = groups(config.sizeZ(), VOLUME_LOCAL_SIZE);

        // CPU transfer may have refreshed the occupancy/emission seed. Make it visible before any
        // compute consumer. This barrier is intentionally cheap compared to rebuilding 15 complete
        // flood-fill passes on every refresh.
        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                List.of(), List.of(seedVolume)
        ));

        if (finalRadiance == null) {
            Std430Writer initialize = volumeWriter(originX, originY, originZ, false);
            parameters.upload(initialize.buffer(), 0L);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant block-light initialize",
                    seedPipeline(), groupsX, groupsY, groupsZ,
                    List.of(new StorageBinding(2, parameters, 0L, initialize.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(new SampledTextureBinding(0, seedVolume.view(), nearest)),
                    List.of(new StorageImageBinding(1, radianceA, StorageAccess.WRITE_ONLY))
            ));
            finalRadiance = radianceA;
            radianceOriginX = originX;
            radianceOriginY = originY;
            radianceOriginZ = originZ;
        }

        for (int iteration = 0; iteration < config.propagationIterationsPerFrame(); iteration++) {
            RhiStorageImage previous = finalRadiance;
            RhiStorageImage next = previous == radianceA ? radianceB : radianceA;
            boolean historyValid = radianceOriginX != Integer.MIN_VALUE;
            int previousOriginX = historyValid ? radianceOriginX : originX;
            int previousOriginY = historyValid ? radianceOriginY : originY;
            int previousOriginZ = historyValid ? radianceOriginZ : originZ;

            Std430Writer writer = volumeWriter(previousOriginX, previousOriginY, previousOriginZ, historyValid);
            parameters.upload(writer.buffer(), 0L);
            computeReadBarrier(context, previous);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant block-light propagation " + iteration,
                    propagatePipeline(), groupsX, groupsY, groupsZ,
                    List.of(new StorageBinding(3, parameters, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(
                            new SampledTextureBinding(0, seedVolume.view(), nearest),
                            new SampledTextureBinding(1, previous.view(), nearest)
                    ),
                    List.of(new StorageImageBinding(2, next, StorageAccess.WRITE_ONLY))
            ));
            finalRadiance = next;
            radianceOriginX = originX;
            radianceOriginY = originY;
            radianceOriginZ = originZ;
        }
    }

    private void resolveToScreen(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        if (view == null || finalRadiance == null) return;

        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        RhiStorageImage output = requireImage(context, DeferredResource.BLOCK_LIGHT_IRRADIANCE);

        Vec3 camera = view.cameraPosition();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(RESOLVE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "depthTransform", zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f, 0.0f, 0.0f)
                .putVec4(0, "volumeSizeAndColumns",
                        config.sizeX(), config.sizeY(), config.sizeZ(), config.atlasColumns())
                .putVec4(0, "atlasSizeAndRows",
                        config.atlasWidth(), config.atlasHeight(), config.atlasRows(), 0.0f)
                .putVec4(0, "originAndSurfaceOffset",
                        (float) (originX - camera.x),
                        (float) (originY - camera.y),
                        (float) (originZ - camera.z),
                        config.surfaceSampleOffset())
                .putVec4(0, "edgeFade",
                        config.edgeFadeStart(), config.edgeFadeEnd(),
                        0.5f, 0.0f);
        RhiStorageBuffer data = resolveData();
        data.upload(writer.buffer(), 0L);

        computeReadBarrier(context, finalRadiance);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant block-light screen resolve",
                resolvePipeline(),
                groups(output.descriptor().width(), SCREEN_LOCAL_SIZE),
                groups(output.descriptor().height(), SCREEN_LOCAL_SIZE), 1,
                List.of(new StorageBinding(5, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, finalRadiance.view(), nearest),
                        new SampledTextureBinding(1, resolvedDepth, nearest),
                        new SampledTextureBinding(2, gbufferDepth, nearest),
                        new SampledTextureBinding(3, geometry, nearest)
                ),
                List.of(new StorageImageBinding(4, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private Std430Writer volumeWriter(int previousOriginX,
                                      int previousOriginY,
                                      int previousOriginZ,
                                      boolean historyValid) {
        return new Std430Writer(VOLUME_DATA_LAYOUT, 1)
                .putVec4(0, "volumeSizeAndColumns",
                        config.sizeX(), config.sizeY(), config.sizeZ(), config.atlasColumns())
                .putVec4(0, "atlasSizeAndRows",
                        config.atlasWidth(), config.atlasHeight(), config.atlasRows(),
                        config.propagationIterationsPerFrame())
                .putVec4(0, "originAndAttenuation",
                        originX, originY, originZ, 1.0f / 15.0f)
                .putVec4(0, "previousOriginAndHistory",
                        previousOriginX, previousOriginY, previousOriginZ, historyValid ? 1.0f : 0.0f);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private void ensureVolumeImages() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (seedVolume != null && radianceA != null && radianceB != null) return;
        int width = config.atlasWidth();
        int height = config.atlasHeight();
        seedVolume = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-block-light-seed", width, height, GpuFormat.RGBA32_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        radianceA = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-block-light-radiance-a", width, height, GpuFormat.RGBA16_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        radianceB = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-block-light-radiance-b", width, height, GpuFormat.RGBA16_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        seedUpload = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 16))
                .order(ByteOrder.nativeOrder());
    }

    private RhiComputePipeline seedPipeline() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (seedPipeline == null) {
            seedPipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-block-light-seed", SEED_SHADER, SEED_LAYOUT
            ));
        }
        return seedPipeline;
    }

    private RhiComputePipeline propagatePipeline() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (propagatePipeline == null) {
            propagatePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-block-light-propagate", PROPAGATE_SHADER, PROPAGATE_LAYOUT
            ));
        }
        return propagatePipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (resolvePipeline == null) {
            resolvePipeline = owner.advancedShaders().createComputePipeline(new ComputePipelineDescriptor(
                    "combatant-block-light-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT
            ));
        }
        return resolvePipeline;
    }

    private RhiStorageBuffer volumeData() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (volumeData == null) {
            volumeData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-block-light-volume-data", VOLUME_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return volumeData;
    }

    private RhiStorageBuffer resolveData() {
        if (owner == null) throw new IllegalStateException("Block-light source has no RHI owner");
        if (resolveData == null) {
            resolveData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-block-light-resolve-data", RESOLVE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return resolveData;
    }

    private ByteBuffer seedUpload() {
        if (seedUpload == null) throw new IllegalStateException("Block-light seed upload is unavailable");
        return seedUpload;
    }

    private void closeOwned() {
        close(seedPipeline); seedPipeline = null;
        close(propagatePipeline); propagatePipeline = null;
        close(resolvePipeline); resolvePipeline = null;
        close(volumeData); volumeData = null;
        close(resolveData); resolveData = null;
        close(seedVolume); seedVolume = null;
        close(radianceA); radianceA = null;
        close(radianceB); radianceB = null;
        finalRadiance = null;
        if (seedUpload != null) {
            MemoryUtil.memFree(seedUpload);
            seedUpload = null;
        }
        worldOwner = null;
        resetVolumeState();
    }

    private static void computeReadBarrier(DeferredPassContext context, RhiStorageImage image) {
        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of(image)
        ));
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

    private void resetVolumeState() {
        lastSeedRefreshFrame = Long.MIN_VALUE;
        originX = originY = originZ = Integer.MIN_VALUE;
        radianceOriginX = radianceOriginY = radianceOriginZ = Integer.MIN_VALUE;
        finalRadiance = null;
    }

    private int[] selectVolumeOrigin(DeferredPrimaryViewSource.FrameView view) {
        Vec3 camera = view.cameraPosition();

        // Block-light propagation is authoritative world-space state. Its coverage must not depend on
        // camera yaw/pitch: rotating a stationary camera must never move cells into or out of the volume.
        // Keep the bounded volume centered on camera position and move it only when the camera crosses
        // the block-aligned origin hysteresis. View/frustum bias belongs to analytic dynamic-light culling,
        // not to the persistent Minecraft-style block-light field.
        return new int[]{
                alignOrigin(floorBlock(camera.x), config.sizeX()),
                alignOrigin(floorBlock(camera.y), config.sizeY()),
                alignOrigin(floorBlock(camera.z), config.sizeZ())
        };
    }

    private int alignOrigin(int center, int size) {
        int desired = center - size / 2;
        return Math.floorDiv(desired, config.originAlignment()) * config.originAlignment();
    }

    private static int floorBlock(double value) {
        return (int) Math.floor(value);
    }

    private static int groups(int extent, int localSize) {
        return Math.max(1, (Math.max(1, extent) + localSize - 1) / localSize);
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
