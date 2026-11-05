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
import combatant.client.render.engine.material.MaterialRegistry;
import combatant.client.render.engine.material.MaterialWeatherResponse;
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
import combatant.client.render.engine.world.environment.SurfaceDepositionKind;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherSample;
import combatant.client.render.engine.world.environment.WeatherState;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persistent weather/surface coupling foundation.
 *
 * <p>The CPU provides exact/producer-owned facts: precipitation occluder height and the selected
 * weather field. A camera-centered world-space load field accumulates liquid/snow/particulates and
 * survives camera rotation/motion by re-addressing history in world coordinates. A screen resolve
 * then applies exact material weather-response descriptors from the material ID G-buffer.</p>
 */
final class DeferredSurfaceWeatherSource implements AutoCloseable {
    private static final int GRID_SIZE = 128;
    private static final int GRID_SPACING = 2;
    private static final int GRID_LOCAL_SIZE = 8;
    private static final int SCREEN_LOCAL_SIZE = 8;
    private static final int SEED_REFRESH_FRAMES = 20;
    private static final float ENCODE_SCALE = 0.999f;
    private static final float INVALID_HEIGHT = -65536.0f;

    private static final int RESPONSE_TABLE_SIZE = 8192;
    private static final int RESPONSE_TABLE_MASK = RESPONSE_TABLE_SIZE - 1;
    private static final int RESPONSE_MAX_PROBES = 32;

    private static final Identifier UPDATE_SHADER = id("deferred/surface_weather_update");
    private static final Identifier RESOLVE_SHADER = id("deferred/surface_weather_resolve");

    private static final Std430StructLayout UPDATE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("currentPreviousOrigin", Std430Type.VEC4)
            .member("gridTime", Std430Type.VEC4)
            .member("accumulationRates", Std430Type.VEC4)
            .member("decayRates", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout RESOLVE_DATA_LAYOUT = Std430StructLayout.builder()
            .member("inverseProjection", Std430Type.MAT4)
            .member("inverseView", Std430Type.MAT4)
            .member("cameraDepth", Std430Type.VEC4)
            .member("grid", Std430Type.VEC4)
            .member("exposure", Std430Type.VEC4)
            .build();

    private static final Std430StructLayout RESPONSE_LAYOUT = Std430StructLayout.builder()
            .member("materialId", Std430Type.UINT)
            .member("occupied", Std430Type.UINT)
            .member("pad0", Std430Type.UINT)
            .member("pad1", Std430Type.UINT)
            .member("responseA", Std430Type.VEC4)
            .member("responseB", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout UPDATE_LAYOUT = new ShaderResourceLayout(List.of(
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
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(10, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private Object worldOwner;
    private RhiComputePipeline updatePipeline;
    private RhiComputePipeline resolvePipeline;
    private RhiStorageBuffer updateData;
    private RhiStorageBuffer resolveData;
    private RhiStorageBuffer responseTable;
    private RhiStorageImage seed;
    private RhiStorageImage loadA;
    private RhiStorageImage loadB;
    private RhiStorageImage currentLoad;
    private ByteBuffer seedUpload;

    private int originX = Integer.MIN_VALUE;
    private int originZ = Integer.MIN_VALUE;
    private int loadOriginX = Integer.MIN_VALUE;
    private int loadOriginZ = Integer.MIN_VALUE;
    private long lastSeedFrame = Long.MIN_VALUE;
    private long lastWeatherRevision = Long.MIN_VALUE;
    private boolean loadValid;
    private int responseHash = Integer.MIN_VALUE;

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.surface-weather.update", DeferredStage.PRE_LIGHTING)
                .priority(300)
                .feature(DeferredFeature.WEATHER)
                .read(DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_MATERIAL_ID,
                        DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.SKY_VISIBILITY,
                        DeferredResource.PRECIPITATION_EXPOSURE,
                        DeferredResource.SURFACE_WEATHER_STATE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH)
                        && context.isValid(DeferredResource.GBUFFER_DEPTH)
                        && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                        && context.resources().texture(DeferredResource.GBUFFER_MATERIAL_ID) != null)
                .execute(this::updateAndResolve)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        updatePipeline();
        resolvePipeline();
        updateData();
        resolveData();
        responseTable();
        ensureGridImages();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void updateAndResolve(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView view = context.primaryView().current();
        ClientLevel level = Minecraft.getInstance().level;
        if (view == null || level == null) return;

        ensureGridImages();
        if (worldOwner != level) {
            worldOwner = level;
            originX = originZ = loadOriginX = loadOriginZ = Integer.MIN_VALUE;
            lastSeedFrame = Long.MIN_VALUE;
            lastWeatherRevision = Long.MIN_VALUE;
            loadValid = false;
        }

        int nextOriginX = alignedOrigin(view.cameraPosition().x);
        int nextOriginZ = alignedOrigin(view.cameraPosition().z);
        WeatherState weather = context.worldState().weatherState();
        long weatherRevision = weather != null && weather.field() != null ? weather.field().revision() : Long.MIN_VALUE;
        boolean moved = nextOriginX != originX || nextOriginZ != originZ;
        boolean periodic = lastSeedFrame == Long.MIN_VALUE
                || context.frame().frameId() - lastSeedFrame >= SEED_REFRESH_FRAMES;
        if (moved || periodic || weatherRevision != lastWeatherRevision) {
            originX = nextOriginX;
            originZ = nextOriginZ;
            uploadSeed(level, weather);
            lastSeedFrame = context.frame().frameId();
            lastWeatherRevision = weatherRevision;
        }

        uploadMaterialResponses();
        updateWorldLoad(context);
        resolveToScreen(context, view);
    }

    private void uploadSeed(ClientLevel level, WeatherState weather) {
        ByteBuffer upload = seedUpload();
        for (int i = 0; i < upload.capacity(); i += 4) upload.putFloat(i, 0.0f);

        WeatherSample fallback = weather != null && weather.camera() != null ? weather.camera() : WeatherSample.UNKNOWN;
        WeatherFieldState field = weather != null && weather.field() != null ? weather.field() : WeatherFieldState.EMPTY;
        for (int z = 0; z < GRID_SIZE; z++) {
            int worldZ = originZ + z * GRID_SPACING;
            for (int x = 0; x < GRID_SIZE; x++) {
                int worldX = originX + x * GRID_SPACING;
                int offset = (z * GRID_SIZE + x) * 16;
                if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
                    upload.putFloat(offset, INVALID_HEIGHT);
                    continue;
                }

                int topHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
                WeatherSample sample = nearestWeather(field, fallback, worldX, worldZ);
                float temperature = sample.valid() ? sample.biomeTemperature() : 0.0f;
                float precipitation = sample.valid()
                        ? encode(sample.precipitation().gpuCode(), sample.precipitationIntensity()) : 0.0f;
                SurfaceDepositionKind depositionKind = sample.valid() ? sample.deposition() : SurfaceDepositionKind.NONE;
                float deposition = sample.valid()
                        ? encode(depositionKind.gpuCode(), sample.depositionIntensity()) : 0.0f;

                upload.putFloat(offset, topHeight);
                upload.putFloat(offset + 4, temperature);
                upload.putFloat(offset + 8, precipitation);
                upload.putFloat(offset + 12, deposition);
            }
        }

        ByteBuffer source = upload.duplicate().order(ByteOrder.nativeOrder());
        source.position(0).limit(upload.capacity());
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
                seed.view().texture(), source, 0, 0, 0, 0, GRID_SIZE, GRID_SIZE
        );
    }

    private void updateWorldLoad(DeferredPassContext context) {
        float deltaSeconds = Math.max(0.0f, Math.min(context.frame().frameDeltaSeconds(), 0.25f));
        RhiStorageImage previous = currentLoad != null ? currentLoad : loadA;
        RhiStorageImage next = previous == loadA ? loadB : loadA;
        int previousOriginX = loadValid ? loadOriginX : originX;
        int previousOriginZ = loadValid ? loadOriginZ : originZ;

        Std430Writer writer = new Std430Writer(UPDATE_DATA_LAYOUT, 1)
                .putVec4(0, "currentPreviousOrigin", originX, originZ, previousOriginX, previousOriginZ)
                .putVec4(0, "gridTime", GRID_SIZE, GRID_SPACING, deltaSeconds, loadValid ? 1.0f : 0.0f)
                .putVec4(0, "accumulationRates", 0.35f, 0.12f, 0.08f, ENCODE_SCALE)
                .putVec4(0, "decayRates", 0.25f, 0.20f, 0.02f, 0.15f);
        RhiStorageBuffer data = updateData();
        data.upload(writer.buffer(), 0L);

        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.ALL, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of(seed, previous)
        ));
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant surface-weather world update",
                updatePipeline(), groups(GRID_SIZE, GRID_LOCAL_SIZE), groups(GRID_SIZE, GRID_LOCAL_SIZE), 1,
                List.of(new StorageBinding(3, data, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(
                        new SampledTextureBinding(0, seed.view(), nearest),
                        new SampledTextureBinding(1, previous.view(), nearest)
                ),
                List.of(new StorageImageBinding(2, next, StorageAccess.WRITE_ONLY))
        ));
        currentLoad = next;
        loadOriginX = originX;
        loadOriginZ = originZ;
        loadValid = true;
    }

    private void resolveToScreen(DeferredPassContext context, DeferredPrimaryViewSource.FrameView view) {
        GpuTextureView geometry = requireTexture(context, DeferredResource.GBUFFER_GEOMETRY);
        GpuTextureView materialId = requireTexture(context, DeferredResource.GBUFFER_MATERIAL_ID);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView gbufferDepth = requireTexture(context, DeferredResource.GBUFFER_DEPTH);
        RhiStorageImage skyVisibility = requireImage(context, DeferredResource.SKY_VISIBILITY);
        RhiStorageImage precipitationExposure = requireImage(context, DeferredResource.PRECIPITATION_EXPOSURE);
        RhiStorageImage surfaceState = requireImage(context, DeferredResource.SURFACE_WEATHER_STATE);
        if (currentLoad == null) return;

        Vec3 camera = view.cameraPosition();
        boolean zeroToOne = zeroToOneDepth(context);
        Std430Writer writer = new Std430Writer(RESOLVE_DATA_LAYOUT, 1)
                .putMat4(0, "inverseProjection", view.inverseProjection())
                .putMat4(0, "inverseView", view.inverseView())
                .putVec4(0, "cameraDepth", (float) camera.x, (float) camera.y, (float) camera.z, zeroToOne ? 1.0f : 0.0f)
                .putVec4(0, "grid", originX, originZ, GRID_SPACING, GRID_SIZE)
                .putVec4(0, "exposure", 0.65f, ENCODE_SCALE, RESPONSE_TABLE_MASK, RESPONSE_MAX_PROBES);
        RhiStorageBuffer params = resolveData();
        params.upload(writer.buffer(), 0L);

        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ,
                List.of(), List.of(currentLoad)
        ));
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant surface-weather resolve",
                resolvePipeline(),
                groups(surfaceState.descriptor().width(), SCREEN_LOCAL_SIZE),
                groups(surfaceState.descriptor().height(), SCREEN_LOCAL_SIZE), 1,
                List.of(
                        new StorageBinding(9, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(10, responseTable(), 0L,
                                RESPONSE_LAYOUT.arrayStride() * RESPONSE_TABLE_SIZE, StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, seed.view(), nearest),
                        new SampledTextureBinding(1, currentLoad.view(), nearest),
                        new SampledTextureBinding(2, geometry, nearest),
                        new SampledTextureBinding(3, resolvedDepth, nearest),
                        new SampledTextureBinding(4, gbufferDepth, nearest),
                        new SampledTextureBinding(5, materialId, nearest)
                ),
                List.of(
                        new StorageImageBinding(6, skyVisibility, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(7, precipitationExposure, StorageAccess.WRITE_ONLY),
                        new StorageImageBinding(8, surfaceState, StorageAccess.WRITE_ONLY)
                )
        ));
    }

    private void uploadMaterialResponses() {
        Map<Integer, MaterialWeatherResponse> responses = MaterialRegistry.global().weatherResponsesSnapshot();
        int hash = responses.hashCode();
        if (hash == responseHash) return;

        Std430Writer writer = new Std430Writer(RESPONSE_LAYOUT, RESPONSE_TABLE_SIZE);
        for (int i = 0; i < RESPONSE_TABLE_SIZE; i++) {
            writer.putInt(i, "materialId", 0).putInt(i, "occupied", 0)
                    .putInt(i, "pad0", 0).putInt(i, "pad1", 0)
                    .putVec4(i, "responseA", 0, 0, 0, 0)
                    .putVec4(i, "responseB", 0, 0, 0, 0);
        }

        int dropped = 0;
        for (Map.Entry<Integer, MaterialWeatherResponse> entry : responses.entrySet()) {
            int materialId = entry.getKey();
            MaterialWeatherResponse response = entry.getValue();
            int slot = hashSlot(materialId);
            boolean inserted = false;
            for (int probe = 0; probe < RESPONSE_MAX_PROBES; probe++) {
                int index = (slot + probe) & RESPONSE_TABLE_MASK;
                // We know which slots this build has filled by tracking an auxiliary boolean locally.
                // Using response entries sorted through an occupancy array avoids depending on direct-buffer zeroing.
                // The second phase below rewrites exact occupied entries.
                if (tableOccupied[index] == 0) {
                    tableOccupied[index] = 1;
                    tableIds[index] = materialId;
                    tableResponses[index] = response;
                    inserted = true;
                    break;
                }
            }
            if (!inserted) dropped++;
        }
        for (int i = 0; i < RESPONSE_TABLE_SIZE; i++) {
            if (tableOccupied[i] == 0) continue;
            MaterialWeatherResponse response = tableResponses[i];
            writer.putInt(i, "materialId", tableIds[i]).putInt(i, "occupied", 1)
                    .putVec4(i, "responseA", response.wetLayerStrength(), response.absorptionRate(),
                            response.dryingRate(), response.runoffRate())
                    .putVec4(i, "responseB", response.puddleCapacity(), response.snowRetention(),
                            response.particulateRetention(), response.particulateWashOffRate());
            tableOccupied[i] = 0;
            tableResponses[i] = null;
        }
        responseTable().upload(writer.buffer(), 0L);
        responseHash = hash;
        if (dropped > 0) {
            DebugLog.warnOnChange("combatant.surface-weather.material-table", Integer.toString(dropped),
                    "[Deferred] surface-weather response table dropped %d materials after bounded probing", dropped);
        }
    }

    private final byte[] tableOccupied = new byte[RESPONSE_TABLE_SIZE];
    private final int[] tableIds = new int[RESPONSE_TABLE_SIZE];
    private final MaterialWeatherResponse[] tableResponses = new MaterialWeatherResponse[RESPONSE_TABLE_SIZE];

    private static WeatherSample nearestWeather(WeatherFieldState field, WeatherSample fallback, int x, int z) {
        if (field == null || !field.valid() || field.gridWidth() <= 0 || field.gridDepth() <= 0
                || field.spacingBlocks() <= 0 || field.samples().isEmpty()) {
            return fallback != null ? fallback : WeatherSample.UNKNOWN;
        }
        int ix = Math.round((x - field.originBlockX()) / (float) field.spacingBlocks());
        int iz = Math.round((z - field.originBlockZ()) / (float) field.spacingBlocks());
        ix = Math.max(0, Math.min(field.gridWidth() - 1, ix));
        iz = Math.max(0, Math.min(field.gridDepth() - 1, iz));
        int index = iz * field.gridWidth() + ix;
        if (index < 0 || index >= field.samples().size()) return fallback != null ? fallback : WeatherSample.UNKNOWN;
        WeatherSample sample = field.samples().get(index);
        return sample != null && sample.valid() ? sample : (fallback != null ? fallback : WeatherSample.UNKNOWN);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        responseHash = Integer.MIN_VALUE;
    }

    private void ensureGridImages() {
        if (owner == null) throw new IllegalStateException("Surface-weather source has no RHI owner");
        if (seed != null && loadA != null && loadB != null) return;
        seed = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-surface-weather-seed", GRID_SIZE, GRID_SIZE, GpuFormat.RGBA32_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        loadA = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-surface-weather-load-a", GRID_SIZE, GRID_SIZE, GpuFormat.RGBA16_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        loadB = owner.advancedShaders().createStorageImage(new StorageImageDescriptor(
                "combatant-surface-weather-load-b", GRID_SIZE, GRID_SIZE, GpuFormat.RGBA16_FLOAT,
                StorageAccess.READ_WRITE, true, false
        ));
        seedUpload = MemoryUtil.memAlloc(GRID_SIZE * GRID_SIZE * 16).order(ByteOrder.nativeOrder());
    }

    private RhiComputePipeline updatePipeline() {
        if (updatePipeline == null) updatePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-surface-weather-update", UPDATE_SHADER, UPDATE_LAYOUT));
        return updatePipeline;
    }

    private RhiComputePipeline resolvePipeline() {
        if (resolvePipeline == null) resolvePipeline = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor("combatant-surface-weather-resolve", RESOLVE_SHADER, RESOLVE_LAYOUT));
        return resolvePipeline;
    }

    private RhiStorageBuffer updateData() {
        if (updateData == null) updateData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-surface-weather-update-data", UPDATE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        return updateData;
    }

    private RhiStorageBuffer resolveData() {
        if (resolveData == null) resolveData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-surface-weather-resolve-data", RESOLVE_DATA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        return resolveData;
    }

    private RhiStorageBuffer responseTable() {
        if (responseTable == null) responseTable = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-surface-weather-material-responses", RESPONSE_LAYOUT, RESPONSE_TABLE_SIZE,
                StorageAccess.READ_ONLY, false));
        return responseTable;
    }

    private ByteBuffer seedUpload() {
        ensureGridImages();
        return seedUpload;
    }

    private void closeOwned() {
        close(updatePipeline); updatePipeline = null;
        close(resolvePipeline); resolvePipeline = null;
        close(updateData); updateData = null;
        close(resolveData); resolveData = null;
        close(responseTable); responseTable = null;
        close(seed); seed = null;
        close(loadA); loadA = null;
        close(loadB); loadB = null;
        currentLoad = null;
        if (seedUpload != null) MemoryUtil.memFree(seedUpload);
        seedUpload = null;
        worldOwner = null;
        loadValid = false;
        responseHash = Integer.MIN_VALUE;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static int alignedOrigin(double cameraCoordinate) {
        int block = (int) Math.floor(cameraCoordinate);
        int alignedCenter = Math.floorDiv(block, GRID_SPACING) * GRID_SPACING;
        return alignedCenter - (GRID_SIZE / 2) * GRID_SPACING;
    }

    private static float encode(int semantic, float intensity) {
        float safe = Float.isFinite(intensity) ? Math.max(0.0f, Math.min(1.0f, intensity)) : 0.0f;
        return semantic + safe * ENCODE_SCALE;
    }

    private static int hashSlot(int materialId) {
        return (materialId * 0x9E3779B9) & RESPONSE_TABLE_MASK;
    }

    private static int groups(int extent, int localSize) {
        return (Math.max(1, extent) + localSize - 1) / localSize;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView view = context.resources().texture(resource);
        if (view == null) throw new IllegalStateException("Missing deferred texture: " + resource);
        return view;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage image = context.resources().storageImage(resource);
        if (image == null) throw new IllegalStateException("Missing deferred storage image: " + resource);
        return image;
    }

    private static void close(AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Throwable ignored) { }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
