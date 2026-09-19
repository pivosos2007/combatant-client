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
import combatant.client.render.engine.light.BlockLightChangeTracker;
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
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Renderer-owned colored block-light field.
 *
 * <p>Minecraft remains authoritative for scalar block-light visibility/intensity. This subsystem
 * propagates only explicit emitter chroma along that already-computed light field.</p>
 */
final class DeferredColoredBlockLightSource implements AutoCloseable {
    private static final int VOLUME_LOCAL_SIZE = 4;
    private static final int SCREEN_LOCAL_SIZE = 8;
    private static final Identifier SEED_SHADER = id("deferred/block_light_seed");
    private static final Identifier PROPAGATE_SHADER = id("deferred/block_light_propagate");
    private static final Identifier RESOLVE_SHADER = id("deferred/block_light_resolve");
    private static final Predicate<BlockState> EMITS_BLOCK_LIGHT = state -> state != null && state.getLightEmission() > 0;

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
    private long observedLightGeneration = Long.MIN_VALUE;

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
        boolean moved = nextOrigin[0] != originX || nextOrigin[1] != originY || nextOrigin[2] != originZ;
        long lightGeneration = BlockLightChangeTracker.generation();
        boolean lightChanged = observedLightGeneration != lightGeneration;
        boolean periodicRefresh = lastSeedRefreshFrame == Long.MIN_VALUE
                || context.frame().frameId() - lastSeedRefreshFrame >= config.seedRefreshIntervalFrames();
        boolean needsRebuild = finalRadiance == null || moved || lightChanged || periodicRefresh;

        if (needsRebuild) {
            // A block update can arrive before the client light engine has propagated its new DataLayer.
            // Keep the previous converged volume for that frame and retry until the authoritative field settles.
            boolean lightEngineBusy = level.getLightEngine().hasLightWork();
            if (finalRadiance == null || moved || !lightEngineBusy) {
                originX = nextOrigin[0];
                originY = nextOrigin[1];
                originZ = nextOrigin[2];
                uploadSeed(level);
                rebuildPropagation(context);
                lastSeedRefreshFrame = context.frame().frameId();
                observedLightGeneration = lightGeneration;
            }
        }

        if (finalRadiance != null) {
            context.resources().bindStorageImage(DeferredResource.BLOCK_LIGHT_VOLUME_SEED, seedVolume);
            context.resources().bindStorageImage(DeferredResource.BLOCK_LIGHT_VOLUME_RADIANCE, finalRadiance);
        }
    }

    private void uploadSeed(ClientLevel level) {
        ByteBuffer upload = seedUpload();
        MemoryUtil.memSet(MemoryUtil.memAddress(upload), 0, upload.capacity());

        LayerLightEventListener blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        BlockLightEmitterRegistry emitters = BlockLightEmitterRegistry.global();

        int minSectionX = SectionPos.blockToSectionCoord(originX);
        int maxSectionX = SectionPos.blockToSectionCoord(originX + config.sizeX() - 1);
        int minSectionY = SectionPos.blockToSectionCoord(originY);
        int maxSectionY = SectionPos.blockToSectionCoord(originY + config.sizeY() - 1);
        int minSectionZ = SectionPos.blockToSectionCoord(originZ);
        int maxSectionZ = SectionPos.blockToSectionCoord(originZ + config.sizeZ() - 1);

        for (int sectionZ = minSectionZ; sectionZ <= maxSectionZ; sectionZ++) {
            for (int sectionX = minSectionX; sectionX <= maxSectionX; sectionX++) {
                ChunkAccess chunk = level.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();

                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    int sectionIndex = level.getSectionIndexFromSectionY(sectionY);
                    if (sectionIndex < 0 || sectionIndex >= sections.length) continue;
                    LevelChunkSection section = sections[sectionIndex];
                    if (section == null) continue;

                    SectionPos sectionPos = SectionPos.of(sectionX, sectionY, sectionZ);
                    DataLayer lightData = blockLight.getDataLayerData(sectionPos);
                    boolean scanEmitters = !section.hasOnlyAir() && section.maybeHas(EMITS_BLOCK_LIGHT);

                    int baseX = SectionPos.sectionToBlockCoord(sectionX);
                    int baseY = SectionPos.sectionToBlockCoord(sectionY);
                    int baseZ = SectionPos.sectionToBlockCoord(sectionZ);
                    int localMinX = Math.max(0, originX - baseX);
                    int localMaxX = Math.min(15, originX + config.sizeX() - 1 - baseX);
                    int localMinY = Math.max(0, originY - baseY);
                    int localMaxY = Math.min(15, originY + config.sizeY() - 1 - baseY);
                    int localMinZ = Math.max(0, originZ - baseZ);
                    int localMaxZ = Math.min(15, originZ + config.sizeZ() - 1 - baseZ);
                    if (localMinX > localMaxX || localMinY > localMaxY || localMinZ > localMaxZ) continue;

                    for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                        int volumeZ = baseZ + localZ - originZ;
                        for (int localY = localMinY; localY <= localMaxY; localY++) {
                            int volumeY = baseY + localY - originY;
                            for (int localX = localMinX; localX <= localMaxX; localX++) {
                                int volumeX = baseX + localX - originX;
                                int offset = seedByteOffset(volumeX, volumeY, volumeZ);
                                int lightLevel = lightData != null ? lightData.get(localX, localY, localZ) : 0;
                                upload.put(offset + 3, (byte) (Math.max(0, Math.min(15, lightLevel)) * 17));

                                if (!scanEmitters) continue;
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.getLightEmission() <= 0) continue;
                                BlockLightEmitterRegistry.ResolvedEmitter emitter = emitters.resolve(state);
                                writeEmitterColor(upload, offset, emitter);
                                int sourceLevel = Math.max(lightLevel, emitter.vanillaLevel());
                                upload.put(offset + 3, (byte) (Math.max(0, Math.min(15, sourceLevel)) * 17));
                            }
                        }
                    }
                }
            }
        }

        ByteBuffer source = upload.duplicate().order(ByteOrder.nativeOrder());
        source.position(0).limit(upload.capacity());
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                seedVolume.view().texture(), source, 0, 0, 0, 0, config.atlasWidth(), config.atlasHeight()
        );
    }

    private void rebuildPropagation(DeferredPassContext context) {
        RhiStorageBuffer parameters = volumeData();
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        int groupsX = groups(config.sizeX(), VOLUME_LOCAL_SIZE);
        int groupsY = groups(config.sizeY(), VOLUME_LOCAL_SIZE);
        int groupsZ = groups(config.sizeZ(), VOLUME_LOCAL_SIZE);

        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.READ,
                List.of(), List.of(seedVolume)
        ));

        Std430Writer initialize = volumeWriter();
        parameters.upload(initialize.buffer(), 0L);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant block-light initialize",
                seedPipeline(), groupsX, groupsY, groupsZ,
                List.of(new StorageBinding(2, parameters, 0L, initialize.byteSize(), StorageAccess.READ_ONLY)),
                List.of(new SampledTextureBinding(0, seedVolume.view(), nearest)),
                List.of(new StorageImageBinding(1, radianceA, StorageAccess.WRITE_ONLY))
        ));
        finalRadiance = radianceA;

        for (int iteration = 0; iteration < config.propagationIterationsOnRebuild(); iteration++) {
            RhiStorageImage previous = finalRadiance;
            RhiStorageImage next = previous == radianceA ? radianceB : radianceA;
            computeReadBarrier(context, previous);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant block-light propagation " + iteration,
                    propagatePipeline(), groupsX, groupsY, groupsZ,
                    List.of(new StorageBinding(3, parameters, 0L, initialize.byteSize(), StorageAccess.READ_ONLY)),
                    List.of(
                            new SampledTextureBinding(0, seedVolume.view(), nearest),
                            new SampledTextureBinding(1, previous.view(), nearest)
                    ),
                    List.of(new StorageImageBinding(2, next, StorageAccess.WRITE_ONLY))
            ));
            finalRadiance = next;
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

    private Std430Writer volumeWriter() {
        return new Std430Writer(VOLUME_DATA_LAYOUT, 1)
                .putVec4(0, "volumeSizeAndColumns",
                        config.sizeX(), config.sizeY(), config.sizeZ(), config.atlasColumns())
                .putVec4(0, "atlasSizeAndRows",
                        config.atlasWidth(), config.atlasHeight(), config.atlasRows(),
                        config.propagationIterationsOnRebuild())
                .putVec4(0, "originAndAttenuation", 0.0f, 0.0f, 0.0f, config.chromaContourBlend())
                .putVec4(0, "previousOriginAndHistory", 0.0f, 0.0f, 0.0f, 0.0f);
    }

    private int seedByteOffset(int x, int y, int z) {
        int sliceColumn = z % config.atlasColumns();
        int sliceRow = z / config.atlasColumns();
        int atlasX = sliceColumn * config.sizeX() + x;
        int atlasY = sliceRow * config.sizeY() + y;
        return (atlasY * config.atlasWidth() + atlasX) * 4;
    }

    private static void writeEmitterColor(ByteBuffer target,
                                          int offset,
                                          BlockLightEmitterRegistry.ResolvedEmitter emitter) {
        float maximum = Math.max(emitter.red(), Math.max(emitter.green(), emitter.blue()));
        if (!(maximum > 1.0e-6f)) return;
        target.put(offset, encodeUnorm8(emitter.red() / maximum));
        target.put(offset + 1, encodeUnorm8(emitter.green() / maximum));
        target.put(offset + 2, encodeUnorm8(emitter.blue() / maximum));
    }

    private static byte encodeUnorm8(float value) {
        int encoded = Math.round(Math.max(0.0f, Math.min(1.0f, value)) * 255.0f);
        return (byte) encoded;
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
                "combatant-block-light-seed", width, height, GpuFormat.RGBA8_UNORM,
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
        seedUpload = MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width, height), 4))
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
        observedLightGeneration = Long.MIN_VALUE;
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
