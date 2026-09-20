/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
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
import combatant.client.render.engine.world.environment.ExposureProfile;
import combatant.client.render.engine.world.environment.ExposureProfileRegistry;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Foundational post-TAA HDR stages: log-luminance eye adaptation and an HDR bloom pyramid.
 *
 * <p>The canonical post input is {@link DeferredResource#TAA_RESOLVED_COLOR}. No render-resolution
 * publish is required for TAAU: both exposure and bloom operate directly in final output space.</p>
 */
final class DeferredHdrPostSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier HISTOGRAM = id("deferred/exposure_histogram");
    private static final Identifier EXPOSURE_REDUCE = id("deferred/exposure_reduce");
    private static final Identifier BLOOM_EXTRACT = id("deferred/bloom_extract");
    private static final Identifier BLOOM_DOWNSAMPLE = id("deferred/bloom_downsample");
    private static final Identifier BLOOM_COPY = id("deferred/bloom_copy");
    private static final Identifier BLOOM_UPSAMPLE = id("deferred/bloom_upsample");
    private static final Identifier BLOOM_FINALIZE = id("deferred/bloom_finalize");
    private static final Identifier NEUTRAL_COLOR = id("deferred/environment_zero_irradiance");

    private static final Std430StructLayout HISTOGRAM_ELEMENT = Std430StructLayout.builder()
            .member("count", Std430Type.UINT)
            .build();
    private static final Std430StructLayout EXPOSURE_STATE = Std430StructLayout.builder()
            .member("exposure", Std430Type.VEC4)
            .member("adaptation", Std430Type.VEC4)
            .member("percentiles", Std430Type.VEC4)
            .member("lifecycle", Std430Type.UVEC4)
            .build();
    private static final Std430StructLayout HISTOGRAM_PARAMS = Std430StructLayout.builder()
            .member("extentAndSampling", Std430Type.VEC4)
            .member("luminanceRange", Std430Type.VEC4)
            .member("weighting", Std430Type.VEC4)
            .build();
    private static final Std430StructLayout REDUCE_PARAMS = Std430StructLayout.builder()
            .member("histogram", Std430Type.VEC4)
            .member("percentiles", Std430Type.VEC4)
            .member("adaptation", Std430Type.VEC4)
            .member("rangeAndReset", Std430Type.VEC4)
            .member("historyAge", Std430Type.INT)
            .member("resetReason", Std430Type.INT)
            .member("resetPolicy", Std430Type.INT)
            .member("nextAge", Std430Type.INT)
            .build();
    private static final Std430StructLayout BLOOM_PARAMS = Std430StructLayout.builder()
            .member("params", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout NEUTRAL_COLOR_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout HISTOGRAM_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout REDUCE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_WRITE),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout BLOOM_EXTRACT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout IMAGE_COPY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout BLOOM_UPSAMPLE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY)
    ));
    private static final ShaderResourceLayout BLOOM_FINALIZE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline histogramPipeline;
    private RhiComputePipeline reducePipeline;
    private RhiComputePipeline bloomExtractPipeline;
    private RhiComputePipeline bloomDownsamplePipeline;
    private RhiComputePipeline bloomCopyPipeline;
    private RhiComputePipeline bloomUpsamplePipeline;
    private RhiComputePipeline bloomFinalizePipeline;
    private RhiComputePipeline neutralColorPipeline;

    private RhiStorageBuffer histogramBuffer;
    private RhiStorageBuffer exposureBuffer;
    private RhiStorageBuffer histogramParams;
    private RhiStorageBuffer reduceParams;
    private RhiStorageBuffer bloomParams;
    private boolean exposureBufferRecreated;
    private ExposureProfile lastExposureProfile;
    private long histogramProducedFrame = Long.MIN_VALUE;
    private long exposureProducedFrame = Long.MIN_VALUE;
    private Boolean lastExposureEnabled;

    void install(ArrayList<DeferredPassSpec> passes) {
        // Exposure smoke override uses an explicit neutral state rather than making downstream
        // bloom/post infer "missing exposure". Multiplier=1, EV=0 and lifecycle=valid.
        passes.add(DeferredPassSpec.builder("world.post.exposure.neutral", DeferredStage.PRE_POST_PROCESS)
                .priority(-500)
                .write(DeferredResource.EXPOSURE)
                .when(context -> !context.featureEnabled(DeferredFeature.EXPOSURE)
                        && context.isValid(DeferredPostHdrContract.HDR_INPUT))
                .execute(this::publishNeutralExposure)
                .build());
        passes.add(DeferredPassSpec.builder("world.post.bloom.neutral", DeferredStage.PRE_POST_PROCESS)
                .priority(-450)
                .write(DeferredResource.BLOOM_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> !context.featureEnabled(DeferredFeature.BLOOM)
                        && context.isValid(DeferredPostHdrContract.HDR_INPUT))
                .execute(this::publishNeutralBloom)
                .build());
        passes.add(DeferredPassSpec.builder("world.post.exposure.histogram", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.EXPOSURE)
                .priority(-400)
                .read(DeferredPostHdrContract.HDR_INPUT, DeferredResource.FINAL_RESOLVED_DEPTH)
                .write(DeferredResource.EXPOSURE_HISTOGRAM)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredPostHdrContract.HDR_INPUT)
                        && context.resources().texture(DeferredPostHdrContract.HDR_INPUT) != null
                        && context.resources().texture(DeferredResource.FINAL_RESOLVED_DEPTH) != null)
                .execute(this::buildHistogram)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.exposure.reduce", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.EXPOSURE)
                .priority(-300)
                .read(DeferredResource.EXPOSURE_HISTOGRAM)
                .readWrite(DeferredResource.EXPOSURE)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> histogramProducedFrame == context.frame().frameId())
                .execute(this::reduceExposure)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.bloom.extract", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.BLOOM)
                .priority(-200)
                .read(DeferredPostHdrContract.HDR_INPUT, DeferredResource.EXPOSURE)
                .write(DeferredResource.BLOOM_PYRAMID)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredPostHdrContract.HDR_INPUT)
                        && (!context.featureEnabled(DeferredFeature.EXPOSURE)
                            || exposureProducedFrame == context.frame().frameId()))
                .execute(this::extractBloom)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.bloom.downsample", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.BLOOM)
                .priority(-150)
                .readWrite(DeferredResource.BLOOM_PYRAMID)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.BLOOM_PYRAMID))
                .execute(this::downsampleBloom)
                .build());

        passes.add(DeferredPassSpec.builder("world.post.bloom.upsample", DeferredStage.PRE_POST_PROCESS)
                .feature(DeferredFeature.BLOOM)
                .priority(-100)
                .read(DeferredResource.BLOOM_PYRAMID)
                .write(DeferredResource.BLOOM_UPSAMPLE, DeferredResource.BLOOM_COLOR)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> context.isValid(DeferredResource.BLOOM_PYRAMID))
                .execute(this::upsampleBloom)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        histogramPipeline();
        reducePipeline();
        bloomExtractPipeline();
        bloomDownsamplePipeline();
        bloomCopyPipeline();
        bloomUpsamplePipeline();
        bloomFinalizePipeline();
        neutralColorPipeline();
        histogramBuffer();
        exposureBuffer();
        histogramParams();
        reduceParams();
        bloomParams();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private void publishNeutralBloom(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage output = requireImage(context, DeferredResource.BLOOM_COLOR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant neutral bloom fallback", neutralColorPipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(), List.of(),
                List.of(new StorageImageBinding(0, output, StorageAccess.WRITE_ONLY))
        ));
    }

    private void publishNeutralExposure(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageBuffer exposure = exposureBuffer();
        ByteBuffer neutral = ByteBuffer.allocateDirect(EXPOSURE_STATE.arrayStride()).order(ByteOrder.nativeOrder());
        int exposureOffset = EXPOSURE_STATE.member("exposure").offset();
        neutral.putFloat(exposureOffset, 1.0f);
        neutral.putFloat(exposureOffset + 4, 0.0f);
        neutral.putFloat(exposureOffset + 8, 0.0f);
        neutral.putFloat(exposureOffset + 12, 1.0f);
        int adaptationOffset = EXPOSURE_STATE.member("adaptation").offset();
        neutral.putFloat(adaptationOffset, 0.0f);
        neutral.putFloat(adaptationOffset + 4, 1.0f);
        int lifecycleOffset = EXPOSURE_STATE.member("lifecycle").offset();
        neutral.putInt(lifecycleOffset, 1);
        neutral.putInt(lifecycleOffset + 4, 0);
        neutral.putInt(lifecycleOffset + 8, DeferredHistoryResetReason.SUBSYSTEM_REENABLED.ordinal());
        neutral.putInt(lifecycleOffset + 12, 1);
        exposure.upload(neutral, 0L);
        context.resources().bindBuffer(DeferredResource.EXPOSURE, exposure);
        exposureProducedFrame = context.frame().frameId();
    }

    private void buildHistogram(DeferredPassContext context) {
        ensureOwner(context.rhi());
        syncExposurePolicy(context);
        RhiStorageBuffer histogram = histogramBuffer();
        exposureBuffer(); // Ensure the persistent state exists before lifecycle invalidation checks.
        context.resources().bindBuffer(DeferredResource.EXPOSURE_HISTOGRAM, histogram);

        ExposureProfile profile = ExposureProfileRegistry.resolve(context.worldState().exposureProfile());
        if (lastExposureProfile != null && !lastExposureProfile.equals(profile)) {
            context.temporalHistory().invalidate(DeferredTemporalHistoryId.EXPOSURE,
                    DeferredHistoryResetReason.POLICY_CHANGE);
        }
        lastExposureProfile = profile;
        if (exposureBufferRecreated) {
            context.temporalHistory().invalidate(DeferredTemporalHistoryId.EXPOSURE,
                    DeferredHistoryResetReason.PERSISTENT_RESOURCE_RECREATION);
            exposureBufferRecreated = false;
        }

        ByteBuffer zeroes = ByteBuffer.allocateDirect(DeferredPostConfig.HISTOGRAM_BINS * Integer.BYTES)
                .order(ByteOrder.nativeOrder());
        histogram.upload(zeroes, 0L);

        GpuTextureView hdr = requireTexture(context, DeferredPostHdrContract.HDR_INPUT);
        GpuTextureView depth = requireTexture(context, DeferredResource.FINAL_RESOLVED_DEPTH);
        DeferredPostConfig.Snapshot config = DeferredPostConfig.current();
        int width = Math.max(1, hdr.getWidth(0));
        int height = Math.max(1, hdr.getHeight(0));
        int stride = samplingStride(width, height, config.histogramMaxSamples());
        int sampleWidth = (width + stride - 1) / stride;
        int sampleHeight = (height + stride - 1) / stride;

        float centerEnabled = profile.weightingPolicy() == ExposureProfile.WeightingPolicy.UNIFORM ? 0.0f : 1.0f;
        float skyEnabled = profile.weightingPolicy() == ExposureProfile.WeightingPolicy.CENTER_AND_SKY ? 1.0f : 0.0f;
        Std430Writer writer = new Std430Writer(HISTOGRAM_PARAMS, 1)
                .putVec4(0, "extentAndSampling", width, height, stride, DeferredPostConfig.HISTOGRAM_BINS)
                .putVec4(0, "luminanceRange", config.histogramMinLogLuminance(),
                        config.histogramMaxLogLuminance(), profile.centerWeight(), profile.skyWeight())
                .putVec4(0, "weighting", centerEnabled, skyEnabled, 0.0f, 0.0f);
        RhiStorageBuffer params = histogramParams();
        params.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR luminance histogram",
                histogramPipeline(),
                groups(sampleWidth), groups(sampleHeight), 1,
                List.of(
                        new StorageBinding(2, histogram, 0L, histogram.descriptor().byteSize(), StorageAccess.READ_WRITE),
                        new StorageBinding(3, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(
                        new SampledTextureBinding(0, hdr, linear),
                        new SampledTextureBinding(1, depth, nearest)
                ),
                List.of()
        ));
        histogramProducedFrame = context.frame().frameId();
    }

    private void syncExposurePolicy(DeferredPassContext context) {
        boolean enabled = DeferredPostConfig.current().exposureEnabled();
        if (lastExposureEnabled != null && lastExposureEnabled != enabled) {
            context.temporalHistory().invalidate(DeferredTemporalHistoryId.EXPOSURE,
                    DeferredHistoryResetReason.POLICY_CHANGE);
        }
        lastExposureEnabled = enabled;
    }

    private void reduceExposure(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageBuffer histogram = requireBuffer(context, DeferredResource.EXPOSURE_HISTOGRAM);
        RhiStorageBuffer exposure = exposureBuffer();
        context.resources().bindBuffer(DeferredResource.EXPOSURE, exposure);

        ExposureProfile profile = ExposureProfileRegistry.resolve(context.worldState().exposureProfile());
        DeferredTemporalHistoryDescriptor history = context.history(DeferredTemporalHistoryId.EXPOSURE);
        DeferredPostConfig.Snapshot config = DeferredPostConfig.current();
        float dt = frameDeltaSeconds(context);
        int reset = history.valid() ? 0 : 1;
        int resetReason = history.valid() ? DeferredHistoryResetReason.NONE.ordinal() : history.resetReason().ordinal();
        int resetPolicy = profile.resetPolicy().ordinal();

        Std430Writer writer = new Std430Writer(REDUCE_PARAMS, 1)
                .putVec4(0, "histogram", DeferredPostConfig.HISTOGRAM_BINS,
                        config.histogramMinLogLuminance(), config.histogramMaxLogLuminance(), 0.18f)
                .putVec4(0, "percentiles", profile.lowPercentile(), profile.highPercentile(),
                        profile.targetPercentile(), profile.meteringPolicy().ordinal())
                .putVec4(0, "adaptation", profile.brightenRate(), profile.darkenRate(),
                        profile.exposureCompensation(), dt)
                .putVec4(0, "rangeAndReset", profile.minEv(), profile.maxEv(), profile.initialEv(), reset)
                .putInt(0, "historyAge", history.age())
                .putInt(0, "resetReason", resetReason)
                .putInt(0, "resetPolicy", resetPolicy)
                .putInt(0, "nextAge", Math.max(1, history.age() + 1));
        RhiStorageBuffer params = reduceParams();
        params.upload(writer.buffer(), 0L);

        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant eye adaptation reduction",
                reducePipeline(),
                1, 1, 1,
                List.of(
                        new StorageBinding(0, histogram, 0L, histogram.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(1, exposure, 0L, exposure.descriptor().byteSize(), StorageAccess.READ_WRITE),
                        new StorageBinding(2, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(),
                List.of()
        ));
        exposureProducedFrame = context.frame().frameId();
        context.temporalHistory().commit(DeferredTemporalHistoryId.EXPOSURE);
    }

    private void extractBloom(DeferredPassContext context) {
        ensureOwner(context.rhi());
        syncExposurePolicy(context);
        GpuTextureView hdr = requireTexture(context, DeferredPostHdrContract.HDR_INPUT);
        RhiStorageBuffer exposure;
        if (context.featureEnabled(DeferredFeature.EXPOSURE)) {
            exposure = requireBuffer(context, DeferredResource.EXPOSURE);
        } else {
            exposure = exposureBuffer();
            context.resources().bindBuffer(DeferredResource.EXPOSURE, exposure);
        }
        RhiStorageImage pyramid = requireImage(context, DeferredResource.BLOOM_PYRAMID);
        DeferredPostConfig.Snapshot config = DeferredPostConfig.current();

        Std430Writer writer = new Std430Writer(BLOOM_PARAMS, 1)
                .putVec4(0, "params", config.bloomThreshold(), config.bloomSoftKnee(),
                        config.bloomIntensity(), context.featureEnabled(DeferredFeature.EXPOSURE) ? 1.0f : 0.0f);
        RhiStorageBuffer params = bloomParams();
        params.upload(writer.buffer(), 0L);

        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR bloom extraction",
                bloomExtractPipeline(),
                groups(pyramid.descriptor().width()), groups(pyramid.descriptor().height()), 1,
                List.of(
                        new StorageBinding(1, exposure, 0L, exposure.descriptor().byteSize(), StorageAccess.READ_ONLY),
                        new StorageBinding(3, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)
                ),
                List.of(new SampledTextureBinding(0, hdr, linear)),
                List.of(new StorageImageBinding(2, pyramid, StorageAccess.WRITE_ONLY, 0))
        ));
    }

    private void downsampleBloom(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage pyramid = requireImage(context, DeferredResource.BLOOM_PYRAMID);
        int mipCount = bloomMipCount(pyramid);
        for (int mip = 1; mip < mipCount; mip++) {
            computeToComputeBarrier(context, pyramid);
            int width = Math.max(1, pyramid.descriptor().width() >> mip);
            int height = Math.max(1, pyramid.descriptor().height() >> mip);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant bloom downsample mip " + mip,
                    bloomDownsamplePipeline(),
                    groups(width), groups(height), 1,
                    List.of(), List.of(),
                    List.of(
                            new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, mip - 1),
                            new StorageImageBinding(1, pyramid, StorageAccess.WRITE_ONLY, mip)
                    )
            ));
        }
    }

    private void upsampleBloom(DeferredPassContext context) {
        ensureOwner(context.rhi());
        RhiStorageImage pyramid = requireImage(context, DeferredResource.BLOOM_PYRAMID);
        RhiStorageImage upsample = requireImage(context, DeferredResource.BLOOM_UPSAMPLE);
        RhiStorageImage output = requireImage(context, DeferredResource.BLOOM_COLOR);
        int mipCount = bloomMipCount(pyramid);

        if (mipCount > 1) {
            int coarsest = mipCount - 1;
            int coarseWidth = Math.max(1, pyramid.descriptor().width() >> coarsest);
            int coarseHeight = Math.max(1, pyramid.descriptor().height() >> coarsest);
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant bloom upsample seed",
                    bloomCopyPipeline(),
                    groups(coarseWidth), groups(coarseHeight), 1,
                    List.of(), List.of(),
                    List.of(
                            new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, coarsest),
                            new StorageImageBinding(1, upsample, StorageAccess.WRITE_ONLY, coarsest)
                    )
            ));

            for (int mip = coarsest - 1; mip >= 1; mip--) {
                computeToComputeBarrier(context, upsample);
                int width = Math.max(1, pyramid.descriptor().width() >> mip);
                int height = Math.max(1, pyramid.descriptor().height() >> mip);
                context.advancedShaders().dispatch(new ComputeDispatchCommand(
                        "Combatant bloom progressive upsample mip " + mip,
                        bloomUpsamplePipeline(),
                        groups(width), groups(height), 1,
                        List.of(), List.of(),
                        List.of(
                                new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, mip),
                                new StorageImageBinding(1, upsample, StorageAccess.READ_ONLY, mip + 1),
                                new StorageImageBinding(2, upsample, StorageAccess.WRITE_ONLY, mip)
                        )
                ));
            }
        } else {
            context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant bloom single-level seed",
                    bloomCopyPipeline(),
                    groups(upsample.descriptor().width()), groups(upsample.descriptor().height()), 1,
                    List.of(), List.of(),
                    List.of(
                            new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, 0),
                            new StorageImageBinding(1, upsample, StorageAccess.WRITE_ONLY, 0)
                    )
            ));
        }

        computeToComputeBarrier(context, pyramid, upsample);
        DeferredPostConfig.Snapshot config = DeferredPostConfig.current();
        Std430Writer writer = new Std430Writer(BLOOM_PARAMS, 1)
                .putVec4(0, "params", config.bloomThreshold(), config.bloomSoftKnee(),
                        config.bloomIntensity(), mipCount > 1 ? 1.0f : 0.0f);
        RhiStorageBuffer params = bloomParams();
        params.upload(writer.buffer(), 0L);
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                "Combatant HDR bloom finalize",
                bloomFinalizePipeline(),
                groups(output.descriptor().width()), groups(output.descriptor().height()), 1,
                List.of(new StorageBinding(3, params, 0L, writer.byteSize(), StorageAccess.READ_ONLY)),
                List.of(),
                List.of(
                        new StorageImageBinding(0, pyramid, StorageAccess.READ_ONLY, 0),
                        new StorageImageBinding(1, mipCount > 1 ? upsample : pyramid,
                                StorageAccess.READ_ONLY, mipCount > 1 ? 1 : 0),
                        new StorageImageBinding(2, output, StorageAccess.WRITE_ONLY, 0)
                )
        ));
    }

    private int bloomMipCount(RhiStorageImage image) {
        int available = Math.max(1, image.descriptor().mipLevels());
        return Math.min(available, DeferredPostConfig.current().bloomMaxMipCount());
    }

    private static int samplingStride(int width, int height, int maxSamples) {
        long pixels = Math.max(1L, (long) width * (long) height);
        if (pixels <= maxSamples) return 1;
        double ratio = Math.sqrt((double) pixels / (double) Math.max(1, maxSamples));
        return Math.max(1, (int) Math.ceil(ratio));
    }

    private static float frameDeltaSeconds(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
        DeferredPrimaryViewSource.FrameView previous = context.primaryView().previous();
        double value = current != null && previous != null
                ? current.frameTimeSeconds() - previous.frameTimeSeconds()
                : 1.0 / 60.0;
        if (!Double.isFinite(value) || value <= 0.0) value = 1.0 / 60.0;
        return (float) Math.max(1.0 / 240.0, Math.min(0.25, value));
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiStorageBuffer histogramBuffer() {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (histogramBuffer == null) {
            histogramBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-exposure-histogram", HISTOGRAM_ELEMENT,
                    DeferredPostConfig.HISTOGRAM_BINS, StorageAccess.READ_WRITE, false
            ));
        }
        return histogramBuffer;
    }

    private RhiStorageBuffer exposureBuffer() {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (exposureBuffer == null) {
            exposureBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-exposure-state", EXPOSURE_STATE, 1, StorageAccess.READ_WRITE, false
            ));
            exposureBuffer.upload(ByteBuffer.allocateDirect(EXPOSURE_STATE.arrayStride())
                    .order(ByteOrder.nativeOrder()), 0L);
            exposureBufferRecreated = true;
        }
        return exposureBuffer;
    }

    private RhiStorageBuffer histogramParams() {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (histogramParams == null) {
            histogramParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-exposure-histogram-params", HISTOGRAM_PARAMS, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return histogramParams;
    }

    private RhiStorageBuffer reduceParams() {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (reduceParams == null) {
            reduceParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-exposure-reduce-params", REDUCE_PARAMS, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return reduceParams;
    }

    private RhiStorageBuffer bloomParams() {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (bloomParams == null) {
            bloomParams = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-bloom-params", BLOOM_PARAMS, 1, StorageAccess.READ_ONLY, false
            ));
        }
        return bloomParams;
    }

    private RhiComputePipeline neutralColorPipeline() {
        return pipeline("combatant-post-neutral-color", NEUTRAL_COLOR, NEUTRAL_COLOR_LAYOUT, neutralColorPipeline,
                value -> neutralColorPipeline = value);
    }

    private RhiComputePipeline histogramPipeline() {
        return pipeline("combatant-exposure-histogram", HISTOGRAM, HISTOGRAM_LAYOUT, histogramPipeline,
                value -> histogramPipeline = value);
    }

    private RhiComputePipeline reducePipeline() {
        return pipeline("combatant-exposure-reduce", EXPOSURE_REDUCE, REDUCE_LAYOUT, reducePipeline,
                value -> reducePipeline = value);
    }

    private RhiComputePipeline bloomExtractPipeline() {
        return pipeline("combatant-bloom-extract", BLOOM_EXTRACT, BLOOM_EXTRACT_LAYOUT, bloomExtractPipeline,
                value -> bloomExtractPipeline = value);
    }

    private RhiComputePipeline bloomDownsamplePipeline() {
        return pipeline("combatant-bloom-downsample", BLOOM_DOWNSAMPLE, IMAGE_COPY_LAYOUT, bloomDownsamplePipeline,
                value -> bloomDownsamplePipeline = value);
    }

    private RhiComputePipeline bloomCopyPipeline() {
        return pipeline("combatant-bloom-copy", BLOOM_COPY, IMAGE_COPY_LAYOUT, bloomCopyPipeline,
                value -> bloomCopyPipeline = value);
    }

    private RhiComputePipeline bloomUpsamplePipeline() {
        return pipeline("combatant-bloom-upsample", BLOOM_UPSAMPLE, BLOOM_UPSAMPLE_LAYOUT, bloomUpsamplePipeline,
                value -> bloomUpsamplePipeline = value);
    }

    private RhiComputePipeline bloomFinalizePipeline() {
        return pipeline("combatant-bloom-finalize", BLOOM_FINALIZE, BLOOM_FINALIZE_LAYOUT, bloomFinalizePipeline,
                value -> bloomFinalizePipeline = value);
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout,
                                        RhiComputePipeline existing,
                                        java.util.function.Consumer<RhiComputePipeline> setter) {
        if (owner == null) throw new IllegalStateException("HDR post source has no RHI owner");
        if (existing != null) return existing;
        RhiComputePipeline created = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor(label, shader, layout));
        setter.accept(created);
        return created;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView texture = context.resources().texture(resource);
        if (texture == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return texture;
    }

    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage image = context.resources().storageImage(resource);
        if (image == null) throw new IllegalStateException("Deferred storage image is not bound: " + resource);
        return image;
    }

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer buffer = context.resources().buffer(resource);
        if (buffer == null) throw new IllegalStateException("Deferred storage buffer is not bound: " + resource);
        return buffer;
    }

    private static int groups(int size) {
        return Math.max(1, (size + LOCAL_SIZE - 1) / LOCAL_SIZE);
    }

    private static void computeToComputeBarrier(DeferredPassContext context, RhiStorageImage... images) {
        ArrayList<RhiStorageImage> list = new ArrayList<>();
        if (images != null) {
            for (RhiStorageImage image : images) if (image != null && !list.contains(image)) list.add(image);
        }
        context.advancedShaders().barrier(new RhiResourceBarrier(
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.WRITE,
                RhiResourceBarrier.Stage.COMPUTE, RhiResourceBarrier.Access.READ_WRITE,
                List.of(), list
        ));
    }

    private void closeOwned() {
        histogramPipeline = close(histogramPipeline);
        reducePipeline = close(reducePipeline);
        bloomExtractPipeline = close(bloomExtractPipeline);
        bloomDownsamplePipeline = close(bloomDownsamplePipeline);
        bloomCopyPipeline = close(bloomCopyPipeline);
        bloomUpsamplePipeline = close(bloomUpsamplePipeline);
        bloomFinalizePipeline = close(bloomFinalizePipeline);
        neutralColorPipeline = close(neutralColorPipeline);
        histogramBuffer = close(histogramBuffer);
        exposureBuffer = close(exposureBuffer);
        histogramParams = close(histogramParams);
        reduceParams = close(reduceParams);
        bloomParams = close(bloomParams);
        exposureBufferRecreated = false;
        lastExposureProfile = null;
        lastExposureEnabled = null;
        histogramProducedFrame = Long.MIN_VALUE;
        exposureProducedFrame = Long.MIN_VALUE;
    }

    private static RhiComputePipeline close(RhiComputePipeline value) {
        if (value == null) return null;
        try { value.close(); } catch (Throwable ignored) { }
        return null;
    }

    private static RhiStorageBuffer close(RhiStorageBuffer value) {
        if (value == null) return null;
        try { value.close(); } catch (Throwable ignored) { }
        return null;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }
}
