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
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.FullscreenDrawCommand;
import combatant.client.render.engine.rhi.shader.ComputeDispatchCommand;
import combatant.client.render.engine.rhi.shader.ComputePipelineDescriptor;
import combatant.client.render.engine.rhi.shader.RhiComputePipeline;
import combatant.client.render.engine.rhi.shader.RhiShaderStage;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
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
import combatant.client.render.engine.rhi.shader.StorageVolumeBinding;
import combatant.client.util.logging.DebugLog;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Generic typed debug decoder. It never mutates the resource being inspected. */
final class DeferredDebugCompositorSource implements AutoCloseable {
    private static final int LOCAL_SIZE = 8;
    private static final Identifier TEXTURE_SHADER = id("deferred/debug_texture");
    private static final Identifier UINT_TEXTURE_SHADER = id("deferred/debug_uint_texture");
    private static final Identifier VOLUME_RGBA16F_SHADER = id("deferred/debug_volume_rgba16f");
    private static final Identifier VOLUME_R32F_SHADER = id("deferred/debug_volume_r32f");
    private static final Identifier EXPOSURE_SHADER = id("deferred/debug_exposure");
    private static final Identifier HISTOGRAM_SHADER = id("deferred/debug_histogram");
    private static final Identifier SHARED_INPUT_SHADER = id("deferred/debug_shared_inputs");

    private static final Std430StructLayout PARAMS_LAYOUT = Std430StructLayout.builder()
            .member("decode", Std430Type.VEC4) // x=mode, y=channel, z=axis, w=slice
            .member("aux", Std430Type.VEC4)    // x=hist bins, y=far plane, zw=depth->NDC scale/bias
            .member("extent", Std430Type.VEC4) // xy=render/source extent, zw=debug output extent
            .member("inverseProjection", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .build();

    private static final ShaderResourceLayout TEXTURE_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout VOLUME_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_VOLUME, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout BUFFER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));
    private static final ShaderResourceLayout SHARED_INPUT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.STORAGE_IMAGE, StorageAccess.WRITE_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private CombatantRhi owner;
    private RhiComputePipeline texturePipeline;
    private RhiComputePipeline uintTexturePipeline;
    private RhiComputePipeline volumeRgba16fPipeline;
    private RhiComputePipeline volumeR32fPipeline;
    private RhiComputePipeline exposurePipeline;
    private RhiComputePipeline histogramPipeline;
    private RhiComputePipeline sharedInputPipeline;
    private RhiStorageBuffer params;

    void install(ArrayList<DeferredPassSpec> passes) {
        DeferredResource[] sources = debugResources();
        passes.add(DeferredPassSpec.builder("world.debug.compose", DeferredStage.FINAL_COMPOSITE)
                .priority(-100)
                .optionalRead(sources)
                .write(DeferredResource.DEBUG_PRESENTATION)
                .requires(RhiShaderStage.COMPUTE)
                .when(context -> DeferredSmokeTestState.global().frameSnapshot().debugView() != DeferredDebugView.OFF)
                .execute(this::compose)
                .build());
        passes.add(DeferredPassSpec.builder("world.debug.present", DeferredStage.FINAL_COMPOSITE)
                .priority(0)
                .read(DeferredResource.DEBUG_PRESENTATION)
                .readWrite(DeferredResource.SCENE_COLOR)
                .when(context -> DeferredSmokeTestState.global().frameSnapshot().debugView() != DeferredDebugView.OFF
                        && context.isValid(DeferredResource.DEBUG_PRESENTATION)
                        && context.resources().texture(DeferredResource.SCENE_COLOR) != null)
                .execute(this::present)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        texturePipeline();
        uintTexturePipeline();
        volumeRgba16fPipeline();
        volumeR32fPipeline();
        exposurePipeline();
        histogramPipeline();
        sharedInputPipeline();
        params();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private boolean available(DeferredPassContext context) {
        DeferredSmokeTestState state = DeferredSmokeTestState.global();
        DeferredDebugView view = state.frameSnapshot().debugView();
        if (view == DeferredDebugView.OFF) {
            state.setUnavailableDebugResourceReason("");
            return false;
        }
        DeferredFeature feature = view.feature();
        if (feature != null && !context.featureEnabled(feature)) {
            String reason = state.featureDisableReasonForFrame(feature, context.settings());
            return unavailable(state, view, "feature " + feature + " is disabled"
                    + (reason.isBlank() ? "" : " (" + reason + ")"));
        }
        if (view.sourceKind() == DeferredDebugView.SourceKind.SHARED_INPUTS) {
            boolean valid = context.primaryView().current() != null
                    && context.isValid(DeferredResource.GBUFFER_GEOMETRY)
                    && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                    && context.isValid(DeferredResource.GBUFFER_MATERIAL)
                    && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null
                    && context.isValid(DeferredResource.GBUFFER_DEPTH)
                    && context.resources().texture(DeferredResource.GBUFFER_DEPTH) != null
                    && context.isValid(DeferredResource.RESOLVED_DEPTH)
                    && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null;
            if (!valid) {
                return unavailable(state, view, "shared input set unavailable: geometry/material/gbuffer-depth/resolved-depth/view");
            }
            state.setUnavailableDebugResourceReason("");
            DeferredPrimaryViewSource.FrameView current = context.primaryView().current();
            String resolution = current == null ? "unknown"
                    : current.renderWidth() + "x" + current.renderHeight() + "->"
                    + current.outputWidth() + "x" + current.outputHeight();
            DebugLog.renderThreadOnChange(
                    "combatant.deferred.debug.ready",
                    view.name() + "|" + resolution + "|" + context.rhi().capabilities().zeroToOneDepth(),
                    "[Deferred][Debug] view=%s source=SHARED_INPUTS render/output=%s zeroToOneDepth=%s ready",
                    view, resolution, context.rhi().capabilities().zeroToOneDepth()
            );
            return true;
        }

        DeferredResource resource = view.resource();
        if (resource == null) {
            return unavailable(state, view, "debug view has no bound renderer resource");
        }
        boolean valid = switch (view.sourceKind()) {
            case TEXTURE, UINT_TEXTURE -> context.isValid(resource) && context.resources().texture(resource) != null;
            case VOLUME -> context.isValid(resource) && context.resources().storageVolume(resource) != null;
            case BUFFER -> context.isValid(resource) && context.resources().buffer(resource) != null;
            case SHARED_INPUTS, NONE -> false;
        };
        if (!valid) {
            return unavailable(state, view, "resource unavailable: " + resource.key().name());
        }
        DeferredResourceProvenance provenance = context.resources().provenance(resource);
        if (provenance != null && (provenance.status() == DeferredResourceStatus.FAILED
                || provenance.status() == DeferredResourceStatus.MISSING_INPUT
                || provenance.status() == DeferredResourceStatus.UNAVAILABLE)) {
            return unavailable(state, view, "resource " + provenance.status() + ": "
                    + resource.key().name() + (provenance.reasonCode().isBlank()
                    ? "" : " (" + provenance.reasonCode() + ")"));
        }
        if (provenance != null && provenance.status() == DeferredResourceStatus.FALLBACK) {
            DebugLog.renderThreadOnChange(
                    "combatant.deferred.debug.fallback." + resource.name(),
                    provenance.producerPassId() + "|" + provenance.reasonCode(),
                    "[Deferred][Debug] resource=%s is FALLBACK producer=%s reason=%s",
                    resource.key().name(), provenance.producerPassId(), provenance.reasonCode()
            );
        }
        state.setUnavailableDebugResourceReason("");
        DebugLog.renderThreadOnChange(
                "combatant.deferred.debug.ready",
                view.name(),
                "[Deferred][Debug] view=%s source=%s resource=%s ready",
                view, view.sourceKind(), resource.key().name()
        );
        return true;
    }

    private static boolean unavailable(DeferredSmokeTestState state, DeferredDebugView view, String reason) {
        state.setUnavailableDebugResourceReason(reason);
        DebugLog.warnOnChange(
                "combatant.deferred.debug.unavailable",
                view.name() + "|" + reason,
                "[Deferred][Debug] view=%s unavailable: %s",
                view, reason
        );
        return false;
    }

    private void compose(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredSmokeTestState.Snapshot state = DeferredSmokeTestState.global().frameSnapshot();
        DeferredDebugView view = state.debugView();
        RhiStorageImage output = requireImage(context, DeferredResource.DEBUG_PRESENTATION);
        if (!available(context)) {
            // DEBUG_PRESENTATION deliberately owns render-attachment usage so an unavailable source
            // can be visualized without imposing render-attachment usage on arbitrary debug inputs.
            String reason = DeferredSmokeTestState.global().unavailableDebugResourceReason();
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                    output.view().texture(), new org.joml.Vector4f(1.0f, 0.0f, 1.0f, 1.0f));
            context.resources().publishStatus(
                    DeferredResource.DEBUG_PRESENTATION, "world.debug.compose", DeferredResourceStatus.FALLBACK,
                    "debug_source_unavailable", reason, view.resource());
            return;
        }
        DeferredResource resource = view.resource();
        DeferredPrimaryViewSource.FrameView current = context.primaryView().current();

        int sourceWidth = output.descriptor().width();
        int sourceHeight = output.descriptor().height();
        if ((view.sourceKind() == DeferredDebugView.SourceKind.TEXTURE
                || view.sourceKind() == DeferredDebugView.SourceKind.UINT_TEXTURE) && resource != null) {
            GpuTextureView source = requireTexture(context, resource);
            sourceWidth = source.getWidth(0);
            sourceHeight = source.getHeight(0);
        } else if (view.sourceKind() == DeferredDebugView.SourceKind.SHARED_INPUTS && current != null) {
            sourceWidth = current.renderWidth() > 0 ? current.renderWidth() : sourceWidth;
            sourceHeight = current.renderHeight() > 0 ? current.renderHeight() : sourceHeight;
        }

        boolean zeroToOne = context.rhi().capabilities().zeroToOneDepth();
        float farPlane = current != null && current.farPlane() > 0.0f ? current.farPlane() : 1.0f;
        org.joml.Matrix4f inverseProjection = current != null
                ? current.inverseProjection() : new org.joml.Matrix4f();
        org.joml.Matrix4f projection = current != null
                ? current.projection() : new org.joml.Matrix4f();

        Std430Writer writer = new Std430Writer(PARAMS_LAYOUT, 1)
                .putVec4(0, "decode", view.decodeMode().shaderId(), view.channel(),
                        state.volumeAxis().shaderId(), state.volumeSlice())
                .putVec4(0, "aux", DeferredPostConfig.HISTOGRAM_BINS, farPlane,
                        zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f)
                .putVec4(0, "extent", sourceWidth, sourceHeight,
                        output.descriptor().width(), output.descriptor().height())
                .putMat4(0, "inverseProjection", inverseProjection)
                .putMat4(0, "projection", projection);
        RhiStorageBuffer parameterBuffer = params();
        parameterBuffer.upload(writer.buffer(), 0L);
        StorageBinding parameterBinding = new StorageBinding(
                2, parameterBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY);
        StorageBinding sharedParameterBinding = new StorageBinding(
                5, parameterBuffer, 0L, writer.byteSize(), StorageAccess.READ_ONLY);
        StorageImageBinding targetBinding = new StorageImageBinding(1, output, StorageAccess.WRITE_ONLY, 0);
        StorageImageBinding sharedTargetBinding = new StorageImageBinding(4, output, StorageAccess.WRITE_ONLY, 0);

        int groupsX = groups(output.descriptor().width());
        int groupsY = groups(output.descriptor().height());
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        switch (view.sourceKind()) {
            case TEXTURE -> {
                GpuTextureView source = requireTexture(context, resource);
                dispatchTexture(context, "Combatant debug texture " + view.name(), texturePipeline(),
                        groupsX, groupsY, source, nearest, parameterBinding, targetBinding);
            }
            case UINT_TEXTURE -> {
                GpuTextureView source = requireTexture(context, resource);
                dispatchTexture(context, "Combatant debug uint texture " + view.name(), uintTexturePipeline(),
                        groupsX, groupsY, source, nearest, parameterBinding, targetBinding);
            }
            case VOLUME -> {
                RhiStorageVolume source = requireVolume(context, resource);
                RhiComputePipeline pipeline = source.descriptor().format() == GpuFormat.R32_FLOAT
                        ? volumeR32fPipeline() : volumeRgba16fPipeline();
                context.advancedShaders().dispatch(new ComputeDispatchCommand(
                        "Combatant debug volume " + view.name(), pipeline, groupsX, groupsY, 1,
                        List.of(parameterBinding), List.of(), List.of(targetBinding),
                        List.of(new StorageVolumeBinding(0, source, StorageAccess.READ_ONLY)), List.of()
                ));
            }
            case BUFFER -> {
                RhiStorageBuffer source = requireBuffer(context, resource);
                RhiComputePipeline pipeline = view.decodeMode() == DeferredDebugDecodeMode.HISTOGRAM
                        ? histogramPipeline() : exposurePipeline();
                context.advancedShaders().dispatch(new ComputeDispatchCommand(
                        "Combatant debug buffer " + view.name(), pipeline, groupsX, groupsY, 1,
                        List.of(
                                new StorageBinding(0, source, 0L, source.descriptor().byteSize(), StorageAccess.READ_ONLY),
                                parameterBinding
                        ), List.of(), List.of(targetBinding)
                ));
            }
            case SHARED_INPUTS -> context.advancedShaders().dispatch(new ComputeDispatchCommand(
                    "Combatant shared-input debug " + view.name(), sharedInputPipeline(), groupsX, groupsY, 1,
                    List.of(sharedParameterBinding),
                    List.of(
                            new SampledTextureBinding(0, requireTexture(context, DeferredResource.GBUFFER_GEOMETRY), nearest),
                            new SampledTextureBinding(1, requireTexture(context, DeferredResource.GBUFFER_MATERIAL), nearest),
                            new SampledTextureBinding(2, requireTexture(context, DeferredResource.GBUFFER_DEPTH), nearest),
                            new SampledTextureBinding(3, requireTexture(context, DeferredResource.RESOLVED_DEPTH), nearest)
                    ),
                    List.of(sharedTargetBinding)
            ));
            case NONE -> { }
        }
    }

    private static void dispatchTexture(DeferredPassContext context,
                                        String label,
                                        RhiComputePipeline pipeline,
                                        int groupsX,
                                        int groupsY,
                                        GpuTextureView source,
                                        GpuSampler sampler,
                                        StorageBinding params,
                                        StorageImageBinding output) {
        context.advancedShaders().dispatch(new ComputeDispatchCommand(
                label, pipeline, groupsX, groupsY, 1,
                List.of(params), List.of(new SampledTextureBinding(0, source, sampler)), List.of(output)
        ));
    }

    private void present(DeferredPassContext context) {
        GpuTextureView source = requireTexture(context, DeferredResource.DEBUG_PRESENTATION);
        GpuTextureView target = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        context.rhi().drawFullscreen(
                FullscreenDrawCommand.builder("Combatant deferred debug presentation")
                        .colorAttachment(target)
                        .pipeline(DeferredRuntimeAssets.temporalPresent())
                        .sampler("u_Source", source, nearest)
                        .build()
        );
        DeferredDebugView view = DeferredSmokeTestState.global().frameSnapshot().debugView();
        DebugLog.renderThreadOnChange(
                "combatant.deferred.debug.presented",
                view.name() + "|" + source.getWidth(0) + "x" + source.getHeight(0)
                        + "->" + target.getWidth(0) + "x" + target.getHeight(0),
                "[Deferred][Debug] presented view=%s source=%dx%d target=%dx%d",
                view, source.getWidth(0), source.getHeight(0), target.getWidth(0), target.getHeight(0)
        );
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
    }

    private RhiComputePipeline texturePipeline() {
        return pipeline("combatant-debug-texture", TEXTURE_SHADER, TEXTURE_LAYOUT,
                texturePipeline, value -> texturePipeline = value);
    }
    private RhiComputePipeline uintTexturePipeline() {
        return pipeline("combatant-debug-uint-texture", UINT_TEXTURE_SHADER, TEXTURE_LAYOUT,
                uintTexturePipeline, value -> uintTexturePipeline = value);
    }
    private RhiComputePipeline volumeRgba16fPipeline() {
        return pipeline("combatant-debug-volume-rgba16f", VOLUME_RGBA16F_SHADER, VOLUME_LAYOUT,
                volumeRgba16fPipeline, value -> volumeRgba16fPipeline = value);
    }
    private RhiComputePipeline volumeR32fPipeline() {
        return pipeline("combatant-debug-volume-r32f", VOLUME_R32F_SHADER, VOLUME_LAYOUT,
                volumeR32fPipeline, value -> volumeR32fPipeline = value);
    }
    private RhiComputePipeline exposurePipeline() {
        return pipeline("combatant-debug-exposure", EXPOSURE_SHADER, BUFFER_LAYOUT,
                exposurePipeline, value -> exposurePipeline = value);
    }
    private RhiComputePipeline histogramPipeline() {
        return pipeline("combatant-debug-histogram", HISTOGRAM_SHADER, BUFFER_LAYOUT,
                histogramPipeline, value -> histogramPipeline = value);
    }
    private RhiComputePipeline sharedInputPipeline() {
        return pipeline("combatant-debug-shared-inputs", SHARED_INPUT_SHADER, SHARED_INPUT_LAYOUT,
                sharedInputPipeline, value -> sharedInputPipeline = value);
    }

    private RhiComputePipeline pipeline(String label, Identifier shader, ShaderResourceLayout layout,
                                        RhiComputePipeline existing,
                                        java.util.function.Consumer<RhiComputePipeline> setter) {
        if (owner == null) throw new IllegalStateException("Debug compositor has no RHI owner");
        if (existing != null) return existing;
        RhiComputePipeline created = owner.advancedShaders().createComputePipeline(
                new ComputePipelineDescriptor(label, shader, layout));
        setter.accept(created);
        return created;
    }

    private RhiStorageBuffer params() {
        if (owner == null) throw new IllegalStateException("Debug compositor has no RHI owner");
        if (params == null) {
            params = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-debug-params", PARAMS_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return params;
    }

    private static DeferredResource[] debugResources() {
        EnumSet<DeferredResource> resources = EnumSet.noneOf(DeferredResource.class);
        for (DeferredDebugView view : DeferredDebugView.values()) {
            if (view.resource() != null && view.resource() != DeferredResource.DEBUG_PRESENTATION) {
                resources.add(view.resource());
            }
            if (view.sourceKind() == DeferredDebugView.SourceKind.SHARED_INPUTS) {
                resources.add(DeferredResource.GBUFFER_GEOMETRY);
                resources.add(DeferredResource.GBUFFER_MATERIAL);
                resources.add(DeferredResource.GBUFFER_DEPTH);
                resources.add(DeferredResource.RESOLVED_DEPTH);
            }
        }
        return resources.toArray(DeferredResource[]::new);
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView value = context.resources().texture(resource);
        if (value == null) throw new IllegalStateException("Deferred debug texture is not bound: " + resource);
        return value;
    }
    private static RhiStorageImage requireImage(DeferredPassContext context, DeferredResource resource) {
        RhiStorageImage value = context.resources().storageImage(resource);
        if (value == null) throw new IllegalStateException("Deferred debug image is not bound: " + resource);
        return value;
    }
    private static RhiStorageVolume requireVolume(DeferredPassContext context, DeferredResource resource) {
        RhiStorageVolume value = context.resources().storageVolume(resource);
        if (value == null) throw new IllegalStateException("Deferred debug volume is not bound: " + resource);
        return value;
    }
    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer value = context.resources().buffer(resource);
        if (value == null) throw new IllegalStateException("Deferred debug buffer is not bound: " + resource);
        return value;
    }

    private static int groups(int size) { return Math.max(1, (size + LOCAL_SIZE - 1) / LOCAL_SIZE); }

    private void closeOwned() {
        texturePipeline = close(texturePipeline);
        uintTexturePipeline = close(uintTexturePipeline);
        volumeRgba16fPipeline = close(volumeRgba16fPipeline);
        volumeR32fPipeline = close(volumeR32fPipeline);
        exposurePipeline = close(exposurePipeline);
        histogramPipeline = close(histogramPipeline);
        sharedInputPipeline = close(sharedInputPipeline);
        params = close(params);
    }
    private static RhiComputePipeline close(RhiComputePipeline value) {
        if (value != null) try { value.close(); } catch (Throwable ignored) { }
        return null;
    }
    private static RhiStorageBuffer close(RhiStorageBuffer value) {
        if (value != null) try { value.close(); } catch (Throwable ignored) { }
        return null;
    }
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
    @Override public void close() { closeOwned(); owner = null; }
}
