/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import combatant.client.render.engine.pipeline.DepthTestFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.render.engine.material.MaterialAtlasManager;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.pipeline.ExtendedRenderPipelineBuilder;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.pipeline.PipelineDomain;
import combatant.client.render.engine.rhi.pipeline.TransformPolicy;
import combatant.client.render.engine.rhi.pipeline.VertexLayoutSpec;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.WaterFrameUniforms;
import combatant.client.render.engine.uniform.impl.WaterReflectionTraceUniforms;
import combatant.client.render.engine.vertex.CombatantVertexFormats;
import combatant.client.render.engine.water.CameraMediumState;
import combatant.client.render.engine.water.WaterDeformationState;
import combatant.client.render.engine.water.WaterForwardProfile;
import combatant.client.render.sodium.fluid.HeightSurfacePatchRouting;
import combatant.client.render.sodium.fluid.SurfacePatchRouting;
import combatant.client.render.sodium.fluid.WaterSurfaceExtractor;
import combatant.client.render.sodium.fluid.WaterSurfacePatchRouting;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Native patch consumer for explicit height-displacement terrain and extracted water surfaces. */
final class DeferredPatchSurfaceSource implements AutoCloseable {
    private static final Identifier PATCH_VERTEX = id("deferred/patch_surface");
    private static final Identifier PATCH_TESS_CONTROL = id("deferred/patch_surface");
    private static final Identifier WATER_PATCH_VERTEX = id("deferred/water_patch");
    private static final Identifier WATER_PATCH_TESS_CONTROL = id("deferred/water_patch");
    private static final Identifier HEIGHT_TESS_EVALUATION = id("deferred/height_surface");
    private static final Identifier WATER_TESS_EVALUATION = id("deferred/water_surface");
    private static final Identifier HEIGHT_FRAGMENT = id("deferred/height_surface");
    private static final Identifier WATER_FRAGMENT = id("deferred/water_surface");
    private static final Identifier WATER_BOUNDARY_FRAGMENT = id("deferred/water_medium_boundary");
    private static final Identifier WATER_REFLECTION_FRAGMENT = id("deferred/water_reflection_trace");
    private static final Identifier WATER_FALLBACK_VERTEX = id("shaders/deferred/water_surface_fallback.vert");
    private static final Identifier WATER_FALLBACK_FRAGMENT = id("shaders/deferred/water_surface_fallback.frag");
    private static final Identifier WATER_BOUNDARY_FALLBACK_FRAGMENT = id("shaders/deferred/water_medium_boundary_fallback.frag");
    private static final Identifier WATER_REFLECTION_FALLBACK_FRAGMENT = id("shaders/deferred/water_reflection_trace_fallback.frag");

    private static final VertexLayoutSpec LAYOUT = VertexLayoutSpec.of(
            "combatant:water_patch", CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);
    private static final VertexLayoutSpec WATER_LAYOUT_SPEC = VertexLayoutSpec.of(
            "combatant:water_forward_patch", CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.QUADS);

    private static final Std430StructLayout CAMERA_LAYOUT = Std430StructLayout.builder()
            .member("viewRotation", Std430Type.MAT4)
            .member("projection", Std430Type.MAT4)
            .member("cameraTime", Std430Type.VEC4)
            .member("viewport", Std430Type.VEC4)
            .build();

    /** mat4/vec4 only so this std430 layout is byte-identical to WaterFrameUniforms std140. */
    private static final Std430StructLayout WATER_FRAME_LAYOUT = Std430StructLayout.builder()
            .member("currentView", Std430Type.MAT4)
            .member("currentProjection", Std430Type.MAT4)
            .member("currentInverseProjection", Std430Type.MAT4)
            .member("currentInverseView", Std430Type.MAT4)
            .member("previousView", Std430Type.MAT4)
            .member("previousProjection", Std430Type.MAT4)
            .member("currentCameraTime", Std430Type.VEC4)
            .member("previousCameraTime", Std430Type.VEC4)
            .member("viewport", Std430Type.VEC4)
            .member("depthTransform", Std430Type.VEC4)
            .member("deformation0", Std430Type.VEC4)
            .member("deformation1", Std430Type.VEC4)
            .member("mediumReflection", Std430Type.VEC4)
            .member("mediumBoundary", Std430Type.VEC4)
            .member("opticalAbsorption", Std430Type.VEC4)
            .member("opticalScattering", Std430Type.VEC4)
            .member("reflectionMeta", Std430Type.VEC4)
            .build();

    private static final ShaderResourceLayout HEIGHT_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(1, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(2, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(3, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(4, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(5, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout WATER_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout WATER_BOUNDARY_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(0, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private static final ShaderResourceLayout WATER_REFLECTION_LAYOUT = new ShaderResourceLayout(List.of(
            new ShaderResourceSlot(5, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(6, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(7, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(8, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(9, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(12, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(13, ShaderResourceKind.SAMPLED_TEXTURE, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(11, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(14, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY),
            new ShaderResourceSlot(15, ShaderResourceKind.STORAGE_BUFFER, StorageAccess.READ_ONLY)
    ));

    private final DeferredReflectionCascadeSource reflectionCascades;
    private CombatantRhi owner;
    private RhiPatchPipeline heightPipeline;
    private RhiPatchPipeline waterPipeline;
    private RhiPatchPipeline waterBoundaryPipeline;
    private RhiPatchPipeline waterReflectionPipeline;
    private PipelineKey heightKey;
    private PipelineKey waterKey;
    private BoundaryPipelineKey waterBoundaryKey;
    private WaterReflectionPipelineKey waterReflectionKey;
    private RhiStorageBuffer cameraBuffer;
    private RhiStorageBuffer waterFrameBuffer;
    private RenderPipeline waterFallbackPipeline;
    private FallbackPipelineKey waterFallbackKey;
    private RenderPipeline waterBoundaryFallbackPipeline;
    private GpuFormat waterBoundaryFallbackFormat;
    private RenderPipeline waterReflectionFallbackPipeline;
    private WaterReflectionPipelineKey waterReflectionFallbackKey;
    private long heightActivationGeneration = Long.MIN_VALUE;
    private boolean heightNativeFailed;
    private boolean waterNativeFailed;
    private boolean waterBoundaryNativeFailed;
    private boolean waterReflectionNativeFailed;
    private boolean waterFallbackFailed;
    private final MeshBuilder heightMesh = new MeshBuilder(CombatantVertexFormats.WATER_PATCH, PrimitiveTopology.QUADS);
    private final MeshBuilder waterMesh = new MeshBuilder(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.QUADS);
    private final MeshBuilder waterFallbackMesh = new MeshBuilder(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES);
    private final WaterDeformationState waterDeformation = new WaterDeformationState();
    // Dynamic arena handles are frame-local. Reuse them across boundary/surface passes in the same frame
    // so the full extracted water mesh is uploaded once instead of once per water pass.
    private long waterMeshCacheFrameId = Long.MIN_VALUE;
    private long waterMeshCacheGeneration = Long.MIN_VALUE;
    private int waterMeshCachePatchCount = -1;
    private long waterMeshCacheSignature = Long.MIN_VALUE;
    private GpuMeshHandle waterPatchMeshCache;
    private GpuMeshHandle waterTriangleMeshCache;

    DeferredPatchSurfaceSource(DeferredReflectionCascadeSource reflectionCascades) {
        if (reflectionCascades == null) throw new IllegalArgumentException("reflectionCascades");
        this.reflectionCascades = reflectionCascades;
    }

    void install(ArrayList<DeferredPassSpec> passes) {
        passes.add(DeferredPassSpec.builder("world.water.routing-policy", DeferredStage.WATER_SURFACE)
                .priority(-100)
                .when(context -> !context.featureEnabled(DeferredFeature.WATER))
                .execute(context -> disableWaterReplacement())
                .build());

        passes.add(DeferredPassSpec.builder("world.height-surface", DeferredStage.HEIGHT_SURFACE)
                .read(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.SCENE_COLOR,
                        DeferredResource.GBUFFER_SURFACE,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.GBUFFER_AUXILIARY,
                        DeferredResource.GBUFFER_MATERIAL,
                        DeferredResource.GBUFFER_MATERIAL_ID,
                        DeferredResource.MAIN_DEPTH)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.TESS_CONTROL,
                        RhiShaderStage.TESS_EVALUATION, RhiShaderStage.FRAGMENT)
                .when(this::heightAvailable)
                .execute(this::drawHeight)
                .build());

        passes.add(DeferredPassSpec.builder("world.water.medium-boundary", DeferredStage.WATER_MEDIUM_BOUNDARY)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.RESOLVED_DEPTH)
                .write(DeferredResource.WATER_MEDIUM_BOUNDARY)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT)
                .when(context -> context.primaryView().current() != null
                        && context.isValid(DeferredResource.RESOLVED_DEPTH))
                .execute(this::drawWaterMediumBoundary)
                .build());

        passes.add(DeferredPassSpec.builder("world.water.reflection-trace", DeferredStage.WATER_REFLECTION_TRACE)
                .feature(DeferredFeature.WATER)
                .read(DeferredResource.SCENE_RADIANCE,
                        DeferredResource.RESOLVED_DEPTH,
                        DeferredResource.GBUFFER_DEPTH,
                        DeferredResource.DEPTH_PYRAMID,
                        DeferredResource.GBUFFER_GEOMETRY,
                        DeferredResource.REFLECTION_TRACE_DATA)
                .optionalRead(DeferredResource.REFLECTION_CASCADE_COLOR,
                        DeferredResource.REFLECTION_CASCADE_DEPTH,
                        DeferredResource.REFLECTION_CASCADE_DATA)
                .write(DeferredResource.WATER_REFLECTION_TRACE_COLOR,
                        DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE,
                        DeferredResource.WATER_REFLECTION_REPROJECTION,
                        DeferredResource.WATER_REFLECTION_GEOMETRY,
                        DeferredResource.WATER_REFLECTION_DEPTHS,
                        DeferredResource.WATER_REFLECTION_CASCADE_COLOR,
                        DeferredResource.WATER_REFLECTION_CASCADE_CONFIDENCE)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT)
                .when(this::waterReflectionAvailable)
                .execute(this::drawWaterReflection)
                .build());

        passes.add(DeferredPassSpec.builder("world.water-surface", DeferredStage.WATER_SURFACE)
                .read(DeferredResource.RESOLVED_DEPTH, DeferredResource.SCENE_RADIANCE)
                .readWrite(DeferredResource.MAIN_DEPTH)
                .write(DeferredResource.SCENE_COLOR,
                        DeferredResource.RASTER_MOTION_VELOCITY,
                        DeferredResource.RASTER_MOTION_VALIDITY,
                        DeferredResource.RASTER_TEMPORAL_COVERAGE,
                        DeferredResource.RASTER_REACTIVE_MASK)
                .requires(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT)
                .when(this::waterAvailable)
                .execute(this::drawWater)
                .build());
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        heightNativeFailed = false;
        waterNativeFailed = false;
        waterBoundaryNativeFailed = false;
        waterReflectionNativeFailed = false;
        waterFallbackFailed = false;
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
        resetRoutingState();
    }

    private boolean heightAvailable(DeferredPassContext context) {
        return !heightNativeFailed
                && baseAvailable(context)
                && context.resources().texture(DeferredResource.GBUFFER_SURFACE) != null
                && context.resources().texture(DeferredResource.GBUFFER_GEOMETRY) != null
                && context.resources().texture(DeferredResource.GBUFFER_AUXILIARY) != null
                && context.resources().texture(DeferredResource.GBUFFER_MATERIAL) != null
                && context.resources().texture(DeferredResource.GBUFFER_MATERIAL_ID) != null
                && WaterSurfaceExtractor.hasHeightPatches();
    }

    private boolean waterReflectionAvailable(DeferredPassContext context) {
        // Reflection hierarchy is deliberately deferred until the scene/sky producers are complete.
        return false;
    }

    private boolean waterAvailable(DeferredPassContext context) {
        boolean wantsReplacement = context.featureEnabled(DeferredFeature.WATER);
        boolean ownsCompatibilityGap = WaterSurfaceExtractor.hasReplacementOwnedWaterPatches();
        return context.primaryView().current() != null
                && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                && context.resources().texture(DeferredResource.MAIN_DEPTH) != null
                && context.resources().texture(DeferredResource.RESOLVED_DEPTH) != null
                && context.resources().texture(DeferredResource.SCENE_RADIANCE) != null
                && (ownsCompatibilityGap || (wantsReplacement && WaterSurfaceExtractor.hasWaterPatches()));
    }

    private boolean baseAvailable(DeferredPassContext context) {
        return context.rhi().capabilities().nativeTessellationSubmission()
                && context.primaryView().current() != null
                && context.resources().texture(DeferredResource.SCENE_COLOR) != null
                && context.resources().texture(DeferredResource.MAIN_DEPTH) != null
                && MaterialAtlasManager.global().ready()
                && blockAtlasTexture() != null;
    }

    private void drawHeight(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        if (!HeightSurfacePatchRouting.replacementActive()) {
            heightActivationGeneration = WaterSurfaceExtractor.generation();
            HeightSurfacePatchRouting.setReplacementActive(true);
            return;
        }

        List<WaterSurfaceExtractor.HeightPatch> patches = collectHeightPatches(heightActivationGeneration);
        if (patches.isEmpty()) return;

        GpuTextureView scene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.MAIN_DEPTH);
        List<GpuTextureView> colors = List.of(
                scene,
                requireTexture(context, DeferredResource.GBUFFER_SURFACE),
                requireTexture(context, DeferredResource.GBUFFER_GEOMETRY),
                requireTexture(context, DeferredResource.GBUFFER_AUXILIARY),
                requireTexture(context, DeferredResource.GBUFFER_MATERIAL),
                requireTexture(context, DeferredResource.GBUFFER_MATERIAL_ID)
        );

        RhiPatchPipeline pipeline;
        try {
            pipeline = heightPipeline(colors, depth);
            uploadCamera(context, camera, scene);
            GpuMeshHandle mesh = buildHeightMesh(patches, camera.cameraPosition());
            context.advancedShaders().drawPatches(new PatchDrawCommand(
                    "Combatant height-displacement surfaces", pipeline, colors, depth, mesh,
                    List.of(new StorageBinding(5, cameraBuffer(), 0L, CAMERA_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                    heightTextures(), List.of()
            ));
            HeightSurfacePatchRouting.setReplacementActive(true);
        } catch (Throwable error) {
            heightNativeFailed = true;
            heightActivationGeneration = Long.MIN_VALUE;
            HeightSurfacePatchRouting.setReplacementActive(false);
            DebugLog.warnOnChange(
                    "deferred.height-surface.native.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] native height-surface replacement failed; Sodium fallback restored: %s: %s",
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    private void drawWaterMediumBoundary(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        GpuTextureView boundary = requireTexture(context, DeferredResource.WATER_MEDIUM_BOUNDARY);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(
                boundary.texture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        List<WaterSurfaceExtractor.WaterPatch> patches = collectReplacementOwnedWaterPatches(camera);
        if (patches.isEmpty()) return;

        WaterFrameUniforms.Frame waterFrame = buildWaterFrame(context, camera, boundary, false);
        uploadWaterFrame(waterFrame);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        if (foundationUsesNativeWaterTessellation() && context.rhi().capabilities().nativeTessellationSubmission() && !waterBoundaryNativeFailed) {
            try {
                RhiPatchPipeline pipeline = waterBoundaryPipeline(boundary);
                GpuMeshHandle mesh = buildWaterMesh(patches, camera);
                context.advancedShaders().drawPatches(new PatchDrawCommand(
                        "Combatant water medium boundary", pipeline, boundary, null, mesh,
                        List.of(new StorageBinding(11, waterFrameBuffer(), 0L,
                                WATER_FRAME_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                        List.of(new SampledTextureBinding(0, resolvedDepth, nearest)), List.of()
                ));
                return;
            } catch (Throwable error) {
                waterBoundaryNativeFailed = true;
                closeWaterBoundaryPipeline();
                DebugLog.warnOnChange(
                        "deferred.water-medium-boundary.tessellation.failed",
                        error.getClass().getSimpleName() + "|" + error.getMessage(),
                        "[Deferred] tessellated water-medium boundary failed; using graphics fallback: %s: %s",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        }

        GpuMeshHandle mesh = buildWaterFallbackMesh(patches, camera);
        GpuBufferSlice frameUniform = WaterFrameUniforms.write(waterFrame);
        context.rhi().drawMesh(RhiDrawCommand.builder("Combatant water medium boundary (triangle fallback)")
                .pipeline(waterBoundaryFallbackPipeline(boundary))
                .colorAttachment(boundary)
                .mesh(mesh)
                .uniform("WaterFrame", frameUniform)
                .sampler("u_OpaqueDepth", resolvedDepth, nearest)
                .build());
    }

    private void drawWaterReflection(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        GpuTextureView reflection = requireTexture(context, DeferredResource.WATER_REFLECTION_TRACE_COLOR);
        GpuTextureView confidence = requireTexture(context, DeferredResource.WATER_REFLECTION_TRACE_CONFIDENCE);
        GpuTextureView reprojection = requireTexture(context, DeferredResource.WATER_REFLECTION_REPROJECTION);
        GpuTextureView geometry = requireTexture(context, DeferredResource.WATER_REFLECTION_GEOMETRY);
        GpuTextureView depths = requireTexture(context, DeferredResource.WATER_REFLECTION_DEPTHS);
        GpuTextureView cascadeReflection = requireTexture(context, DeferredResource.WATER_REFLECTION_CASCADE_COLOR);
        GpuTextureView cascadeConfidence = requireTexture(context, DeferredResource.WATER_REFLECTION_CASCADE_CONFIDENCE);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        Vector4f clear = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
        encoder.clearColorTexture(reflection.texture(), clear);
        encoder.clearColorTexture(confidence.texture(), clear);
        encoder.clearColorTexture(reprojection.texture(), clear);
        encoder.clearColorTexture(geometry.texture(), clear);
        encoder.clearColorTexture(depths.texture(), clear);
        encoder.clearColorTexture(cascadeReflection.texture(), clear);
        encoder.clearColorTexture(cascadeConfidence.texture(), clear);

        List<WaterSurfaceExtractor.WaterPatch> patches = collectReplacementOwnedWaterPatches(camera);
        if (patches.isEmpty()) return;

        WaterFrameUniforms.Frame waterFrame = buildWaterFrame(context, camera, reflection, false);
        uploadWaterFrame(waterFrame);
        RhiStorageBuffer traceData = requireBuffer(context, DeferredResource.REFLECTION_TRACE_DATA);
        boolean hasCascade = hasReflectionCascade(context);
        RhiStorageBuffer cascadeData = hasCascade
                ? requireBuffer(context, DeferredResource.REFLECTION_CASCADE_DATA) : traceData;

        if (context.rhi().capabilities().nativeTessellationSubmission() && !waterReflectionNativeFailed) {
            try {
                RhiPatchPipeline pipeline = waterReflectionPipeline(
                        reflection, confidence, reprojection, geometry, depths, cascadeReflection, cascadeConfidence);
                GpuMeshHandle mesh = buildWaterMesh(patches, camera);
                context.advancedShaders().drawPatches(new PatchDrawCommand(
                        "Combatant water reflection trace", pipeline,
                        List.of(reflection, confidence, reprojection, geometry, depths, cascadeReflection, cascadeConfidence),
                        null, mesh,
                        List.of(
                                new StorageBinding(11, waterFrameBuffer(), 0L,
                                        WATER_FRAME_LAYOUT.arrayStride(), StorageAccess.READ_ONLY),
                                new StorageBinding(14, traceData, 0L,
                                        traceData.descriptor().byteSize(), StorageAccess.READ_ONLY),
                                new StorageBinding(15, cascadeData, 0L,
                                        cascadeData.descriptor().byteSize(), StorageAccess.READ_ONLY)
                        ),
                        waterReflectionTextures(context, hasCascade), List.of()
                ));
                return;
            } catch (Throwable error) {
                waterReflectionNativeFailed = true;
                closeWaterReflectionPipeline();
                DebugLog.warnOnChange(
                        "deferred.water-reflection.tessellation.failed",
                        error.getClass().getSimpleName() + "|" + error.getMessage(),
                        "[Deferred] tessellated water reflection trace failed; using graphics fallback: %s: %s",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        }

        GpuMeshHandle mesh = buildWaterFallbackMesh(patches, camera);
        GpuBufferSlice frameUniform = WaterFrameUniforms.write(waterFrame);
        GpuBufferSlice traceUniform = WaterReflectionTraceUniforms.write(
                waterReflectionFallbackFrame(context, camera, hasCascade));
        RhiDrawCommand.Builder draw = RhiDrawCommand.builder("Combatant water reflection trace (triangle fallback)")
                .pipeline(waterReflectionFallbackPipeline(
                        reflection, confidence, reprojection, geometry, depths, cascadeReflection, cascadeConfidence))
                .colorAttachment(0, reflection)
                .colorAttachment(1, confidence)
                .colorAttachment(2, reprojection)
                .colorAttachment(3, geometry)
                .colorAttachment(4, depths)
                .colorAttachment(5, cascadeReflection)
                .colorAttachment(6, cascadeConfidence)
                .mesh(mesh)
                .uniform("WaterFrame", frameUniform)
                .uniform("WaterReflectionTrace", traceUniform);
        bindWaterReflectionFallbackTextures(draw, context, hasCascade);
        context.rhi().drawMesh(draw.build());
    }

    private void drawWater(DeferredPassContext context) {
        ensureOwner(context.rhi());
        DeferredPrimaryViewSource.FrameView camera = context.primaryView().current();
        if (camera == null) return;

        boolean waterEnabled = context.featureEnabled(DeferredFeature.WATER);
        if (!waterEnabled && WaterSurfacePatchRouting.replacementActive()) {
            WaterSurfacePatchRouting.setReplacementActive(false);
        }

        GpuTextureView scene = requireTexture(context, DeferredResource.SCENE_COLOR);
        GpuTextureView depth = requireTexture(context, DeferredResource.MAIN_DEPTH);
        GpuTextureView velocity = context.resources().texture(DeferredResource.RASTER_MOTION_VELOCITY);
        GpuTextureView motionValidity = context.resources().texture(DeferredResource.RASTER_MOTION_VALIDITY);
        GpuTextureView temporalCoverage = context.resources().texture(DeferredResource.RASTER_TEMPORAL_COVERAGE);
        GpuTextureView reactiveMask = context.resources().texture(DeferredResource.RASTER_REACTIVE_MASK);
        boolean motionMrt = velocity != null
                && motionValidity != null
                && temporalCoverage != null
                && reactiveMask != null
                && velocity.getWidth(0) == scene.getWidth(0)
                && velocity.getHeight(0) == scene.getHeight(0)
                && motionValidity.getWidth(0) == scene.getWidth(0)
                && motionValidity.getHeight(0) == scene.getHeight(0)
                && temporalCoverage.getWidth(0) == scene.getWidth(0)
                && temporalCoverage.getHeight(0) == scene.getHeight(0)
                && reactiveMask.getWidth(0) == scene.getWidth(0)
                && reactiveMask.getHeight(0) == scene.getHeight(0)
                && samples(velocity) == samples(scene)
                && samples(motionValidity) == samples(scene)
                && samples(temporalCoverage) == samples(scene)
                && samples(reactiveMask) == samples(scene);
        List<GpuTextureView> colors = motionMrt
                ? List.of(scene, velocity, motionValidity, temporalCoverage, reactiveMask) : List.of(scene);

        if (waterEnabled && !WaterSurfacePatchRouting.replacementActive()) {
            if (waterFallbackFailed) {
                if (WaterSurfaceExtractor.hasReplacementOwnedWaterPatches()) {
                    publishWaterDiagnostics("FAILED_RESTORING_SODIUM", "SODIUM_RESTORE",
                            "triangle_fallback_failed", 0L, motionMrt, context);
                    return;
                }
                // Recovery is safe only after no published section is still missing Sodium water.
                waterFallbackFailed = false;
            }
            armWaterReplacement(context, camera, scene, depth, colors, motionMrt);
            return;
        }

        List<WaterSurfaceExtractor.WaterPatch> patches = collectReplacementOwnedWaterPatches(camera);
        if (patches.isEmpty()) {
            publishWaterDiagnostics(
                    waterEnabled ? "WAITING_SECTION_REBUILD" : "COMPATIBILITY",
                    waterEnabled ? "READY" : "SODIUM", "", 0L, motionMrt, context);
            return;
        }

        WaterFrameUniforms.Frame waterFrame = buildWaterFrame(context, camera, scene, motionMrt);
        uploadWaterFrame(waterFrame);

        Throwable nativeFailure = null;
        if (foundationUsesNativeWaterTessellation() && context.rhi().capabilities().nativeTessellationSubmission() && !waterNativeFailed) {
            try {
                RhiPatchPipeline pipeline = waterPipeline(colors, depth);
                GpuMeshHandle mesh = buildWaterMesh(patches, camera);
                context.advancedShaders().drawPatches(new PatchDrawCommand(
                        "Combatant water surfaces (tessellated)", pipeline, colors, depth, mesh,
                        List.of(new StorageBinding(11, waterFrameBuffer(), 0L,
                                WATER_FRAME_LAYOUT.arrayStride(), StorageAccess.READ_ONLY)),
                        waterTextures(context), List.of()
                ));
                publishWaterDiagnostics(
                        waterEnabled ? "ACTIVE" : "RESTORING_SODIUM",
                        "NATIVE_TESSELLATION", "", patches.size(), motionMrt, context);
                return;
            } catch (Throwable error) {
                nativeFailure = error;
                waterNativeFailed = true;
                closeWaterPipeline();
                DebugLog.warnOnChange(
                        "deferred.water-surface.tessellation.failed",
                        error.getClass().getSimpleName() + "|" + error.getMessage(),
                        "[Deferred] tessellated water path failed; using declared graphics fallback: %s: %s",
                        error.getClass().getSimpleName(), error.getMessage());
            }
        }

        try {
            GpuMeshHandle mesh = buildWaterFallbackMesh(patches, camera);
            RenderPipeline pipeline = waterFallbackPipeline(scene, motionMrt ? velocity : null,
                    motionMrt ? motionValidity : null, motionMrt ? temporalCoverage : null,
                    motionMrt ? reactiveMask : null, depth);
            GpuBufferSlice frameUniform = WaterFrameUniforms.write(waterFrame);
            RhiDrawCommand.Builder draw = RhiDrawCommand.builder("Combatant water surfaces (triangle fallback)")
                    .pipeline(pipeline)
                    .colorAttachment(0, scene)
                    .depthAttachment(depth)
                    .mesh(mesh)
                    .uniform("WaterFrame", frameUniform);
            if (motionMrt) {
                draw.colorAttachment(1, velocity);
                draw.colorAttachment(2, motionValidity);
                draw.colorAttachment(3, temporalCoverage);
                draw.colorAttachment(4, reactiveMask);
            }
            bindWaterFallbackTextures(draw, context);
            context.rhi().drawMesh(draw.build());
            String fallbackReason = nativeFailure == null ? "" : "native_tessellation_failed";
            publishWaterDiagnostics(
                    waterEnabled ? "ACTIVE" : "RESTORING_SODIUM",
                    "TRIANGLE_FALLBACK", fallbackReason, patches.size(), motionMrt, context);
        } catch (Throwable error) {
            waterFallbackFailed = true;
            WaterSurfacePatchRouting.setReplacementActive(false);
            String nativeSuffix = nativeFailure == null ? "" : " (tessellation had already failed)";
            String reason = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            publishWaterDiagnostics("FAILED", "SODIUM_RESTORE", reason, 0L, false, context);
            DebugLog.warnOnChange(
                    "deferred.water-surface.fallback.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] water graphics fallback failed%s; Sodium fallback restored: %s: %s",
                    nativeSuffix, error.getClass().getSimpleName(), error.getMessage());
        }
    }

    /**
     * Two-phase replacement activation. The triangle path is created and a real extracted mesh is
     * uploaded while Sodium still owns every currently-published section. Only after that succeeds
     * do new section builds snapshot Combatant ownership and suppress their Sodium water quads.
     */
    private void armWaterReplacement(DeferredPassContext context,
                                     DeferredPrimaryViewSource.FrameView camera,
                                     GpuTextureView scene,
                                     GpuTextureView depth,
                                     List<GpuTextureView> colors,
                                     boolean motionMrt) {
        List<WaterSurfaceExtractor.WaterPatch> patches = collectAllWaterPatches(camera);
        if (patches.isEmpty()) {
            publishWaterDiagnostics("WAITING_EXTRACTION", "SODIUM", "no_extracted_water", 0L, motionMrt, context);
            return;
        }

        GpuTextureView velocity = motionMrt ? context.resources().texture(DeferredResource.RASTER_MOTION_VELOCITY) : null;
        GpuTextureView motionValidity = motionMrt ? context.resources().texture(DeferredResource.RASTER_MOTION_VALIDITY) : null;
        GpuTextureView temporalCoverage = motionMrt ? context.resources().texture(DeferredResource.RASTER_TEMPORAL_COVERAGE) : null;
        GpuTextureView reactiveMask = motionMrt ? context.resources().texture(DeferredResource.RASTER_REACTIVE_MASK) : null;
        try {
            // The fallback is mandatory. Do not suppress Sodium until this path and its mesh upload exist.
            waterFallbackPipeline(scene, velocity, motionValidity, temporalCoverage, reactiveMask, depth);
            buildWaterFallbackMesh(patches, camera);
            WaterFrameUniforms.write(buildWaterFrame(context, camera, scene, motionMrt));

            String readyPath = "TRIANGLE_FALLBACK_READY";
            if (foundationUsesNativeWaterTessellation() && context.rhi().capabilities().nativeTessellationSubmission() && !waterNativeFailed) {
                try {
                    waterPipeline(colors, depth);
                    buildWaterMesh(patches, camera);
                    readyPath = "NATIVE_TESSELLATION_READY";
                } catch (Throwable nativeError) {
                    waterNativeFailed = true;
                    closeWaterPipeline();
                    DebugLog.warnOnChange(
                            "deferred.water-surface.preflight.tessellation.failed",
                            nativeError.getClass().getSimpleName() + "|" + nativeError.getMessage(),
                            "[Deferred] water tessellation preflight failed; replacement will use triangle fallback: %s: %s",
                            nativeError.getClass().getSimpleName(), nativeError.getMessage());
                }
            }

            WaterSurfacePatchRouting.setReplacementActive(true);
            publishWaterDiagnostics("WAITING_SECTION_REBUILD", readyPath, "", 0L, motionMrt, context);
        } catch (Throwable error) {
            waterFallbackFailed = true;
            WaterSurfacePatchRouting.setReplacementActive(false);
            String reason = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
            publishWaterDiagnostics("FAILED", "SODIUM", reason, 0L, false, context);
            DebugLog.warnOnChange(
                    "deferred.water-surface.preflight.failed",
                    error.getClass().getSimpleName() + "|" + error.getMessage(),
                    "[Deferred] water replacement preflight failed; Sodium water remains authoritative: %s: %s",
                    error.getClass().getSimpleName(), error.getMessage());
        }
    }

    private RhiPatchPipeline heightPipeline(List<GpuTextureView> colors, GpuTextureView depth) {
        PipelineKey key = new PipelineKey(colors.stream().map(v -> v.texture().getFormat()).toList(),
                depth.texture().getFormat(), samples(colors.get(0)));
        if (heightPipeline != null && key.equals(heightKey)) return heightPipeline;
        closeHeightPipeline();
        heightKey = key;
        heightPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-height-surface", PATCH_VERTEX, PATCH_TESS_CONTROL, HEIGHT_TESS_EVALUATION,
                null, HEIGHT_FRAGMENT, LAYOUT, 4, HEIGHT_LAYOUT,
                AdvancedBlendMode.OPAQUE, AdvancedDepthMode.READ_WRITE_GREATER_EQUAL, AdvancedCullMode.NONE,
                key.colorFormats(), key.depthFormat(), key.samples()
        ));
        return heightPipeline;
    }

    private RhiPatchPipeline waterPipeline(List<GpuTextureView> colors, GpuTextureView depth) {
        PipelineKey key = new PipelineKey(colors.stream().map(v -> v.texture().getFormat()).toList(),
                depth.texture().getFormat(), samples(colors.getFirst()));
        if (waterPipeline != null && key.equals(waterKey)) return waterPipeline;
        closeWaterPipeline();
        waterKey = key;
        waterPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-surface", WATER_PATCH_VERTEX, WATER_PATCH_TESS_CONTROL, WATER_TESS_EVALUATION,
                null, WATER_FRAGMENT, WATER_LAYOUT_SPEC, 4, WATER_LAYOUT,
                AdvancedBlendMode.OPAQUE, AdvancedDepthMode.READ_WRITE_GREATER_EQUAL, AdvancedCullMode.NONE,
                key.colorFormats(), key.depthFormat(), key.samples()
        ));
        return waterPipeline;
    }

    private RhiPatchPipeline waterReflectionPipeline(GpuTextureView color,
                                                       GpuTextureView confidence,
                                                       GpuTextureView reprojection,
                                                       GpuTextureView geometry,
                                                       GpuTextureView depths,
                                                       GpuTextureView cascadeColor,
                                                       GpuTextureView cascadeConfidence) {
        WaterReflectionPipelineKey key = waterReflectionKey(
                color, confidence, reprojection, geometry, depths, cascadeColor, cascadeConfidence);
        if (waterReflectionPipeline != null && key.equals(waterReflectionKey)) return waterReflectionPipeline;
        closeWaterReflectionPipeline();
        waterReflectionKey = key;
        waterReflectionPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-reflection-trace", WATER_PATCH_VERTEX, WATER_PATCH_TESS_CONTROL,
                WATER_TESS_EVALUATION, null, WATER_REFLECTION_FRAGMENT, WATER_LAYOUT_SPEC, 4,
                WATER_REFLECTION_LAYOUT, AdvancedBlendMode.OPAQUE, AdvancedDepthMode.DISABLED,
                AdvancedCullMode.NONE, key.colorFormats(), null, 1
        ));
        return waterReflectionPipeline;
    }

    private RhiPatchPipeline waterBoundaryPipeline(GpuTextureView boundary) {
        BoundaryPipelineKey key = new BoundaryPipelineKey(boundary.texture().getFormat());
        if (waterBoundaryPipeline != null && key.equals(waterBoundaryKey)) return waterBoundaryPipeline;
        closeWaterBoundaryPipeline();
        waterBoundaryKey = key;
        waterBoundaryPipeline = owner.advancedShaders().createPatchPipeline(new PatchPipelineDescriptor(
                "combatant-water-medium-boundary", WATER_PATCH_VERTEX, WATER_PATCH_TESS_CONTROL, WATER_TESS_EVALUATION,
                null, WATER_BOUNDARY_FRAGMENT, WATER_LAYOUT_SPEC, 4, WATER_BOUNDARY_LAYOUT,
                AdvancedBlendMode.OPAQUE, AdvancedDepthMode.DISABLED, AdvancedCullMode.NONE,
                key.colorFormat(), null, 1
        ));
        return waterBoundaryPipeline;
    }

    private RenderPipeline waterBoundaryFallbackPipeline(GpuTextureView boundary) {
        GpuFormat format = boundary.texture().getFormat();
        if (waterBoundaryFallbackPipeline != null && format == waterBoundaryFallbackFormat) {
            return waterBoundaryFallbackPipeline;
        }
        waterBoundaryFallbackFormat = format;
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                .withLocation(id("pipeline/world_water_medium_boundary_" + format.name().toLowerCase(Locale.ROOT)))
                .withVertexFormat(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES)
                .withVertexShader(WATER_FALLBACK_VERTEX)
                .withFragmentShader(WATER_BOUNDARY_FALLBACK_FRAGMENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withColorTarget(0, format)
                .withSampler("u_OpaqueDepth")
                .withUniform("WaterFrame", UniformType.UNIFORM_BUFFER)
                .withDomain(PipelineDomain.WORLD)
                .withTransformPolicy(TransformPolicy.NONE);
        waterBoundaryFallbackPipeline = CombatantRenderPipelines.registerAddonPipeline(builder.build());
        return waterBoundaryFallbackPipeline;
    }

    private RenderPipeline waterReflectionFallbackPipeline(GpuTextureView color,
                                                             GpuTextureView confidence,
                                                             GpuTextureView reprojection,
                                                             GpuTextureView geometry,
                                                             GpuTextureView depths,
                                                             GpuTextureView cascadeColor,
                                                             GpuTextureView cascadeConfidence) {
        WaterReflectionPipelineKey key = waterReflectionKey(
                color, confidence, reprojection, geometry, depths, cascadeColor, cascadeConfidence);
        if (waterReflectionFallbackPipeline != null && key.equals(waterReflectionFallbackKey)) {
            return waterReflectionFallbackPipeline;
        }
        waterReflectionFallbackKey = key;
        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                .withLocation(id("pipeline/world_water_reflection_trace_" + Integer.toHexString(key.hashCode())))
                .withVertexFormat(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES)
                .withVertexShader(WATER_FALLBACK_VERTEX)
                .withFragmentShader(WATER_REFLECTION_FALLBACK_FRAGMENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false)
                .withColorTarget(0, key.traceColorFormat())
                .withColorTarget(1, key.traceConfidenceFormat())
                .withColorTarget(2, key.reprojectionFormat())
                .withColorTarget(3, key.geometryFormat())
                .withColorTarget(4, key.depthsFormat())
                .withColorTarget(5, key.cascadeColorFormat())
                .withColorTarget(6, key.cascadeConfidenceFormat())
                .withSampler("u_SceneRadiance")
                .withSampler("u_ResolvedDepth")
                .withSampler("u_GbufferDepth")
                .withSampler("u_DepthPyramid")
                .withSampler("u_GbufferGeometry")
                .withSampler("u_CascadeColor")
                .withSampler("u_CascadeDepth")
                .withUniform("WaterFrame", UniformType.UNIFORM_BUFFER)
                .withUniform("WaterReflectionTrace", UniformType.UNIFORM_BUFFER)
                .withDomain(PipelineDomain.WORLD)
                .withTransformPolicy(TransformPolicy.NONE);
        waterReflectionFallbackPipeline = CombatantRenderPipelines.registerAddonPipeline(builder.build());
        return waterReflectionFallbackPipeline;
    }

    private RenderPipeline waterFallbackPipeline(GpuTextureView scene,
                                                 GpuTextureView velocity,
                                                 GpuTextureView motionValidity,
                                                 GpuTextureView temporalCoverage,
                                                 GpuTextureView reactiveMask,
                                                 GpuTextureView depth) {
        FallbackPipelineKey key = new FallbackPipelineKey(
                scene.texture().getFormat(),
                velocity != null ? velocity.texture().getFormat() : null,
                motionValidity != null ? motionValidity.texture().getFormat() : null,
                temporalCoverage != null ? temporalCoverage.texture().getFormat() : null,
                reactiveMask != null ? reactiveMask.texture().getFormat() : null,
                depth.texture().getFormat());
        if (waterFallbackPipeline != null && key.equals(waterFallbackKey)) return waterFallbackPipeline;
        waterFallbackKey = key;

        ExtendedRenderPipelineBuilder builder = new ExtendedRenderPipelineBuilder(CombatantRenderPipelines.meshUniforms())
                .withLocation(id("pipeline/world_water_surface_fallback_" + Integer.toHexString(key.hashCode())))
                .withVertexFormat(CombatantVertexFormats.WATER_FORWARD_PATCH, PrimitiveTopology.TRIANGLES)
                .withVertexShader(WATER_FALLBACK_VERTEX)
                .withFragmentShader(WATER_FALLBACK_FRAGMENT)
                .withDepthTestFunction(DepthTestFunction.GEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withCull(false)
                .withColorTarget(0, key.sceneFormat())
                .withSampler("u_SceneRadiance")
                .withSampler("u_ResolvedDepth")
                .withUniform("WaterFrame", UniformType.UNIFORM_BUFFER)
                .withDomain(PipelineDomain.WORLD)
                .withTransformPolicy(TransformPolicy.NONE);
        if (key.velocityFormat() != null) {
            builder.withColorTarget(1, key.velocityFormat());
        }
        if (key.motionValidityFormat() != null) {
            builder.withColorTarget(2, key.motionValidityFormat());
        }
        if (key.temporalCoverageFormat() != null) {
            builder.withColorTarget(3, key.temporalCoverageFormat());
        }
        if (key.reactiveMaskFormat() != null) {
            builder.withColorTarget(4, key.reactiveMaskFormat());
        }
        waterFallbackPipeline = CombatantRenderPipelines.registerAddonPipeline(builder.build());
        return waterFallbackPipeline;
    }

    private WaterFrameUniforms.Frame buildWaterFrame(DeferredPassContext context,
                                                      DeferredPrimaryViewSource.FrameView current,
                                                      GpuTextureView target,
                                                      boolean velocityMrt) {
        DeferredHistoryDescriptor history = context.history();
        DeferredPrimaryViewSource.FrameView previous = history.valid() ? context.primaryView().previous() : null;
        if (previous == null) previous = current;

        Matrix4f currentView = current.view();
        currentView.m30(0.0f).m31(0.0f).m32(0.0f);
        Matrix4f previousView = previous.view();
        previousView.m30(0.0f).m31(0.0f).m32(0.0f);
        Matrix4f currentProjection = current.projection();
        Matrix4f previousProjection = previous.projection();
        Matrix4f currentInverseProjection = new Matrix4f(currentProjection).invert();
        Matrix4f currentInverseView = new Matrix4f(currentView).invert();

        float time = 0.0f;
        float rain = 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.level != null) {
            time = (minecraft.level.getGameTime() + context.frame().tickProgress()) / 20.0f;
            rain = Math.max(0.0f, Math.min(1.0f, minecraft.level.getRainLevel(context.frame().tickProgress())));
        }
        WaterDeformationState.FrameTimes deformationTimes =
                waterDeformation.update(current.frameId(), time, history.valid());
        WaterForwardProfile profile = WaterForwardProfile.FOUNDATION;
        CameraMediumState medium = CameraMediumState.capture();

        // Foundation water intentionally has no reflection/sky dependency yet.
        boolean hasWaterReflection = false;
        boolean hasCascade = false;
        boolean hasSky = false;
        int skyMipCount = 1;
        boolean zeroToOne = zeroToOneDepth(context);

        return new WaterFrameUniforms.Frame(
                currentView, currentProjection, currentInverseProjection, currentInverseView,
                previousView, previousProjection,
                vec4(current.cameraPosition(), deformationTimes.currentTime()),
                vec4(previous.cameraPosition(), deformationTimes.previousTime()),
                new float[]{target.getWidth(0), target.getHeight(0),
                        1.0f / Math.max(1, target.getWidth(0)), 1.0f / Math.max(1, target.getHeight(0))},
                new float[]{zeroToOne ? 1.0f : 2.0f, zeroToOne ? 0.0f : -1.0f,
                        zeroToOne ? 1.0f : 0.5f, zeroToOne ? 0.0f : 0.5f},
                new float[]{profile.displacementAmplitudeFactor(), profile.displacementSpatialFrequency(),
                        profile.displacementTemporalFrequency(), profile.flowCoupling()},
                new float[]{profile.windX(), profile.windZ(), profile.windCoupling(), profile.rainRippleContribution()},
                new float[]{medium.medium().gpuCode(), medium.insideWater() ? 1.0f : 0.0f,
                        hasWaterReflection ? 1.0f : 0.0f, hasSky ? 1.0f : 0.0f},
                new float[]{medium.boundarySurfaceY(), medium.boundarySource().gpuCode(),
                        medium.fluidTypeId(), medium.boundarySurfaceValid() ? 1.0f : 0.0f},
                new float[]{profile.absorptionR(), profile.absorptionG(), profile.absorptionB(),
                        profile.refractionIntensity()},
                new float[]{profile.scatteringR(), profile.scatteringG(), profile.scatteringB(), rain},
                new float[]{skyMipCount, hasCascade ? 1.0f : 0.0f, velocityMrt ? 1.0f : 0.0f,
                        deformationTimes.historyValid() ? 1.0f : 0.0f}
        );
    }

    private void uploadWaterFrame(WaterFrameUniforms.Frame frame) {
        float[][] vectors = {
                frame.currentCameraTime(), frame.previousCameraTime(), frame.viewport(), frame.depthTransform(),
                frame.deformation0(), frame.deformation1(), frame.mediumReflection(), frame.mediumBoundary(),
                frame.opticalAbsorption(), frame.opticalScattering(), frame.reflectionMeta()
        };
        Std430Writer writer = new Std430Writer(WATER_FRAME_LAYOUT, 1)
                .putMat4(0, "currentView", frame.currentView())
                .putMat4(0, "currentProjection", frame.currentProjection())
                .putMat4(0, "currentInverseProjection", frame.currentInverseProjection())
                .putMat4(0, "currentInverseView", frame.currentInverseView())
                .putMat4(0, "previousView", frame.previousView())
                .putMat4(0, "previousProjection", frame.previousProjection());
        String[] names = {"currentCameraTime", "previousCameraTime", "viewport", "depthTransform", "deformation0",
                "deformation1", "mediumReflection", "mediumBoundary", "opticalAbsorption", "opticalScattering",
                "reflectionMeta"};
        for (int i = 0; i < names.length; i++) {
            float[] value = vectors[i];
            writer.putVec4(0, names[i], value[0], value[1], value[2], value[3]);
        }
        waterFrameBuffer().upload(writer.buffer(), 0L);
    }

    private static float[] vec4(Vec3 position, float w) {
        Vec3 value = position == null ? Vec3.ZERO : position;
        return new float[]{(float) value.x, (float) value.y, (float) value.z, w};
    }

    private void uploadCamera(DeferredPassContext context,
                              DeferredPrimaryViewSource.FrameView camera,
                              GpuTextureView target) {
        Matrix4f viewRotation = camera.view();
        viewRotation.m30(0.0f).m31(0.0f).m32(0.0f);
        float time = 0.0f;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            time = (minecraft.level.getGameTime() + context.frame().tickProgress()) / 20.0f;
        }
        Std430Writer writer = new Std430Writer(CAMERA_LAYOUT, 1)
                .putMat4(0, "viewRotation", viewRotation)
                .putMat4(0, "projection", camera.projection())
                .putVec4(0, "cameraTime", (float) camera.cameraPosition().x, (float) camera.cameraPosition().y,
                        (float) camera.cameraPosition().z, time)
                .putVec4(0, "viewport", target.getWidth(0), target.getHeight(0),
                        1.0f / Math.max(1, target.getWidth(0)), 1.0f / Math.max(1, target.getHeight(0)));
        cameraBuffer().upload(writer.buffer(), 0L);
    }

    private GpuMeshHandle buildWaterMesh(List<WaterSurfaceExtractor.WaterPatch> patches,
                                         DeferredPrimaryViewSource.FrameView camera) {
        prepareWaterMeshCache(camera.frameId(), patches);
        if (waterPatchMeshCache != null) return waterPatchMeshCache;
        Vec3 cameraPosition = camera.cameraPosition();
        waterMesh.reserve(patches.size() * 4, patches.size() * 4);
        waterMesh.beginLocal();
        for (WaterSurfaceExtractor.WaterPatch patch : patches) {
            int base = waterMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writeWaterVertex(waterMesh, patch, i, cameraPosition);
            waterMesh.patch4(base, base + 1, base + 2, base + 3);
        }
        waterMesh.end();
        waterPatchMeshCache = owner.dynamicMeshes().upload(waterMesh);
        return waterPatchMeshCache;
    }

    private GpuMeshHandle buildWaterFallbackMesh(List<WaterSurfaceExtractor.WaterPatch> patches,
                                                 DeferredPrimaryViewSource.FrameView camera) {
        prepareWaterMeshCache(camera.frameId(), patches);
        if (waterTriangleMeshCache != null) return waterTriangleMeshCache;
        Vec3 cameraPosition = camera.cameraPosition();
        waterFallbackMesh.reserve(patches.size() * 4, patches.size() * 6);
        waterFallbackMesh.beginLocal();
        for (WaterSurfaceExtractor.WaterPatch patch : patches) {
            int base = waterFallbackMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writeWaterVertex(waterFallbackMesh, patch, i, cameraPosition);
            waterFallbackMesh.quad(base, base + 1, base + 2, base + 3);
        }
        waterFallbackMesh.end();
        waterTriangleMeshCache = owner.dynamicMeshes().upload(waterFallbackMesh);
        return waterTriangleMeshCache;
    }

    private void prepareWaterMeshCache(long frameId, List<WaterSurfaceExtractor.WaterPatch> patches) {
        long generation = WaterSurfaceExtractor.generation();
        int patchCount = patches.size();
        long signature = waterPatchSignature(patches);
        if (waterMeshCacheFrameId == frameId
                && waterMeshCacheGeneration == generation
                && waterMeshCachePatchCount == patchCount
                && waterMeshCacheSignature == signature) return;
        waterMeshCacheFrameId = frameId;
        waterMeshCacheGeneration = generation;
        waterMeshCachePatchCount = patchCount;
        waterMeshCacheSignature = signature;
        waterPatchMeshCache = null;
        waterTriangleMeshCache = null;
    }

    private static long waterPatchSignature(List<WaterSurfaceExtractor.WaterPatch> patches) {
        long hash = 0xcbf29ce484222325L;
        for (WaterSurfaceExtractor.WaterPatch patch : patches) {
            hash ^= patch.blockPos();
            hash *= 0x100000001b3L;
            hash ^= ((long) patch.surfaceFlags() << 32) ^ Integer.toUnsignedLong(patch.packedSurface());
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static boolean foundationUsesNativeWaterTessellation() {
        return WaterForwardProfile.FOUNDATION.displacementAmplitudeFactor() > 1.0e-6f;
    }

    private GpuMeshHandle buildHeightMesh(List<WaterSurfaceExtractor.HeightPatch> patches, Vec3 camera) {
        heightMesh.reserve(patches.size() * 4, patches.size() * 4);
        heightMesh.beginLocal();
        for (WaterSurfaceExtractor.HeightPatch patch : patches) {
            int base = heightMesh.getVertexCount();
            for (int i = 0; i < 4; i++) writePatchVertex(heightMesh, patch.positions(), patch.uvs(), patch.color(),
                    patch.ao(), patch.light(), i, camera, 0.0f, 0.0f, patch.displacementScale(),
                    patch.minTessFactor(), patch.maxTessFactor(), patch.distanceFadeStart(), patch.distanceFadeEnd(),
                    patch.materialId(), patch.mapMask(), patch.packedSurface());
            heightMesh.patch4(base, base + 1, base + 2, base + 3);
        }
        heightMesh.end();
        return owner.dynamicMeshes().upload(heightMesh);
    }

    private static void writePatchVertex(MeshBuilder mesh,
                                         float[] positions,
                                         float[] uvs,
                                         int[] colors,
                                         float[] ao,
                                         int[] light,
                                         int i,
                                         Vec3 camera,
                                         float flowX,
                                         float flowZ,
                                         float displacement,
                                         float minFactor,
                                         float maxFactor,
                                         float fadeStart,
                                         float fadeEnd,
                                         int materialId,
                                         int mapMask,
                                         int packedSurface) {
        int packedColor = colors[i];
        int r = packedColor & 0xFF;
        int g = (packedColor >>> 8) & 0xFF;
        int b = (packedColor >>> 16) & 0xFF;
        int a = (packedColor >>> 24) & 0xFF;
        int packedLight = light[i];
        float blockLight = Math.max(0.0f, Math.min(1.0f, (packedLight & 0xFFFF) / 240.0f));
        float skyLight = Math.max(0.0f, Math.min(1.0f, ((packedLight >>> 16) & 0xFFFF) / 240.0f));

        mesh.vec3(positions[i * 3] - camera.x, positions[i * 3 + 1] - camera.y, positions[i * 3 + 2] - camera.z)
                .vec2(uvs[i * 2], uvs[i * 2 + 1])
                .color(r, g, b, a)
                .vec4(flowX, flowZ, ao[i], blockLight)
                .vec4(displacement, minFactor, maxFactor, fadeStart)
                .vec4(fadeEnd, skyLight, 0.0f, 0.0f)
                .uint(materialId)
                .uint(mapMask)
                .uint(packedSurface)
                .next();
    }

    private static void writeWaterVertex(MeshBuilder mesh,
                                         WaterSurfaceExtractor.WaterPatch patch,
                                         int i,
                                         Vec3 camera) {
        int packedColor = patch.color()[i];
        int r = packedColor & 0xFF;
        int g = (packedColor >>> 8) & 0xFF;
        int b = (packedColor >>> 16) & 0xFF;
        int a = (packedColor >>> 24) & 0xFF;
        int packedLight = patch.light()[i];
        float blockLight = Math.max(0.0f, Math.min(1.0f, (packedLight & 0xFFFF) / 240.0f));
        float skyLight = Math.max(0.0f, Math.min(1.0f, ((packedLight >>> 16) & 0xFFFF) / 240.0f));

        BlockPos blockPos = BlockPos.of(patch.blockPos());
        mesh.vec3(blockPos.getX() + (double) patch.positions()[i * 3] - camera.x,
                        blockPos.getY() + (double) patch.positions()[i * 3 + 1] - camera.y,
                        blockPos.getZ() + (double) patch.positions()[i * 3 + 2] - camera.z)
                .vec2(patch.uvs()[i * 2], patch.uvs()[i * 2 + 1])
                .vec2(patch.localSurfaceCoordinates()[i * 2], patch.localSurfaceCoordinates()[i * 2 + 1])
                .color(r, g, b, a)
                .vec4(patch.flowX(), patch.flowZ(), patch.ao()[i], blockLight)
                .vec4(patch.displacementScale(), patch.minTessFactor(), patch.maxTessFactor(), patch.distanceFadeStart())
                .vec4(patch.distanceFadeEnd(), skyLight, patch.flowStrength(), patch.surfaceNormalX())
                .vec4(patch.transmission(), patch.fallbackThickness(), patch.surfaceNormalY(), patch.surfaceNormalZ())
                .uint(patch.materialId())
                .uint(patch.fluidTypeId())
                .uint(patch.mapMask())
                .uint(patch.featureMask())
                .uint(patch.surfaceFlags())
                .uint(patch.packedSurface())
                .next();
    }

    private List<SampledTextureBinding> heightTextures() {
        return materialTextures();
    }

    private List<SampledTextureBinding> waterReflectionTextures(DeferredPassContext context, boolean hasCascade) {
        List<SampledTextureBinding> textures = new ArrayList<>();
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView radiance = requireTexture(context, DeferredResource.SCENE_RADIANCE);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        textures.add(new SampledTextureBinding(5, radiance, linear));
        textures.add(new SampledTextureBinding(6, resolvedDepth, nearest));
        textures.add(new SampledTextureBinding(7, requireTexture(context, DeferredResource.GBUFFER_DEPTH), nearest));
        textures.add(new SampledTextureBinding(8, requireTexture(context, DeferredResource.DEPTH_PYRAMID), nearest));
        textures.add(new SampledTextureBinding(9, requireTexture(context, DeferredResource.GBUFFER_GEOMETRY), nearest));
        GpuTextureView cascadeColor = hasCascade
                ? requireTexture(context, DeferredResource.REFLECTION_CASCADE_COLOR) : radiance;
        GpuTextureView cascadeDepth = hasCascade
                ? requireTexture(context, DeferredResource.REFLECTION_CASCADE_DEPTH) : resolvedDepth;
        textures.add(new SampledTextureBinding(12, cascadeColor, linear));
        textures.add(new SampledTextureBinding(13, cascadeDepth, nearest));
        return textures;
    }

    private void bindWaterReflectionFallbackTextures(RhiDrawCommand.Builder draw,
                                                      DeferredPassContext context,
                                                      boolean hasCascade) {
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        GpuTextureView radiance = requireTexture(context, DeferredResource.SCENE_RADIANCE);
        GpuTextureView resolvedDepth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        GpuTextureView cascadeColor = hasCascade
                ? requireTexture(context, DeferredResource.REFLECTION_CASCADE_COLOR) : radiance;
        GpuTextureView cascadeDepth = hasCascade
                ? requireTexture(context, DeferredResource.REFLECTION_CASCADE_DEPTH) : resolvedDepth;
        draw.sampler("u_SceneRadiance", radiance, linear)
                .sampler("u_ResolvedDepth", resolvedDepth, nearest)
                .sampler("u_GbufferDepth", requireTexture(context, DeferredResource.GBUFFER_DEPTH), nearest)
                .sampler("u_DepthPyramid", requireTexture(context, DeferredResource.DEPTH_PYRAMID), nearest)
                .sampler("u_GbufferGeometry", requireTexture(context, DeferredResource.GBUFFER_GEOMETRY), nearest)
                .sampler("u_CascadeColor", cascadeColor, linear)
                .sampler("u_CascadeDepth", cascadeDepth, nearest);
    }

    private WaterReflectionTraceUniforms.Frame waterReflectionFallbackFrame(
            DeferredPassContext context,
            DeferredPrimaryViewSource.FrameView camera,
            boolean hasCascade) {
        GpuTextureView depth = requireTexture(context, DeferredResource.RESOLVED_DEPTH);
        DeferredRuntimeConfig.Snapshot settings = context.settings();
        List<WaterReflectionTraceUniforms.Face> faces = List.of();
        if (hasCascade) {
            DeferredReflectionCascadeSource.WaterCascadeSnapshot snapshot =
                    reflectionCascades.waterSnapshot(camera.cameraPosition());
            faces = snapshot.faces().stream()
                    .map(face -> new WaterReflectionTraceUniforms.Face(
                            face.viewProjection(), face.atlasScaleBias(), face.rangeFace(), face.originDelta()))
                    .toList();
        }
        return new WaterReflectionTraceUniforms.Frame(
                new float[]{depth.getWidth(0), depth.getHeight(0), camera.farPlane(),
                        settings.reflectionTraceMaxDistanceScale()},
                new float[]{settings.reflectionTraceMaxSteps(), settings.reflectionTraceMinStep(),
                        settings.reflectionTraceDepthStepScale(), settings.reflectionTraceStepGrowth()},
                new float[]{settings.reflectionTraceThickness(), settings.reflectionTraceNormalBias(),
                        settings.reflectionTraceEdgeMargin(), settings.reflectionTraceMipStepScale()},
                new float[]{settings.reflectionScreenConfidenceThreshold(),
                        settings.reflectionCascadeConfidence(), 0.0f, 0.0f},
                faces
        );
    }

    private boolean hasReflectionCascade(DeferredPassContext context) {
        return context.isValid(DeferredResource.REFLECTION_CASCADE_COLOR)
                && context.isValid(DeferredResource.REFLECTION_CASCADE_DEPTH)
                && context.isValid(DeferredResource.REFLECTION_CASCADE_DATA)
                && context.resources().texture(DeferredResource.REFLECTION_CASCADE_COLOR) != null
                && context.resources().texture(DeferredResource.REFLECTION_CASCADE_DEPTH) != null
                && context.resources().buffer(DeferredResource.REFLECTION_CASCADE_DATA) != null;
    }

    private List<SampledTextureBinding> waterTextures(DeferredPassContext context) {
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        return List.of(
                new SampledTextureBinding(7, requireTexture(context, DeferredResource.SCENE_RADIANCE), linear),
                new SampledTextureBinding(8, requireTexture(context, DeferredResource.RESOLVED_DEPTH), nearest)
        );
    }

    private void bindWaterFallbackTextures(RhiDrawCommand.Builder draw, DeferredPassContext context) {
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        draw.sampler("u_SceneRadiance", requireTexture(context, DeferredResource.SCENE_RADIANCE), linear)
                .sampler("u_ResolvedDepth", requireTexture(context, DeferredResource.RESOLVED_DEPTH), nearest);
    }

    private List<SampledTextureBinding> materialTextures() {
        AbstractTexture block = blockAtlasTexture();
        if (block == null) throw new IllegalStateException("Minecraft block atlas is unavailable");
        MaterialAtlasManager atlases = MaterialAtlasManager.global();
        GpuSampler atlasSampler = block.getSampler();
        return List.of(
                new SampledTextureBinding(0, block.getTextureView(), atlasSampler),
                new SampledTextureBinding(1, requireView(atlases.albedoView(), "material albedo atlas"), atlasSampler),
                new SampledTextureBinding(2, requireView(atlases.normalHeightView(), "material normal-height atlas"), atlasSampler),
                new SampledTextureBinding(3, requireView(atlases.surfaceView(), "material surface atlas"), atlasSampler),
                new SampledTextureBinding(4, requireView(atlases.specularView(), "material specular atlas"), atlasSampler)
        );
    }

    private static AbstractTexture blockAtlasTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getTextureManager() == null
                ? null : minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
    }

    private static GpuTextureView requireView(GpuTextureView view, String label) {
        if (view == null) throw new IllegalStateException(label + " is unavailable");
        return view;
    }

    private void publishWaterDiagnostics(String renderStatus,
                                         String drawPath,
                                         String fallbackReason,
                                         long submittedPatches,
                                         boolean motionMrt,
                                         DeferredPassContext context) {
        WaterSurfaceExtractor.ExtractionStats stats = WaterSurfaceExtractor.stats();
        String reflectionPath = "DISABLED_FOUNDATION";
        String environment = "SCENE_TRANSMISSION_ONLY";
        boolean replacement = WaterSurfacePatchRouting.replacementActive();
        DeferredPrimaryViewSource.FrameView camera = context == null ? null : context.primaryView().current();
        int visibleSections = visibleWaterSectionCount(false, camera);
        int visibleOwnedSections = visibleWaterSectionCount(true, camera);
        WaterSurfacePatchRouting.Diagnostics diagnostics = new WaterSurfacePatchRouting.Diagnostics(
                renderStatus, drawPath, fallbackReason, reflectionPath, environment,
                stats.sectionsWithWater(), stats.replacementOwnedSections(), visibleSections, visibleOwnedSections,
                stats.waterPatches(), stats.replacementOwnedPatches(), submittedPatches, replacement, replacement, motionMrt
        );
        WaterSurfacePatchRouting.publishDiagnostics(diagnostics);
        String state = renderStatus + "|" + drawPath + "|" + fallbackReason + "|" + reflectionPath + "|"
                + environment + "|" + stats.sectionsWithWater() + "|" + stats.replacementOwnedSections() + "|"
                + visibleSections + "|" + visibleOwnedSections + "|"
                + stats.waterPatches() + "|" + stats.replacementOwnedPatches() + "|" + submittedPatches + "|"
                + replacement + "|" + motionMrt;
        DebugLog.renderThreadOnChange(
                "deferred.water.status", state,
                "[Deferred][Water] status=%s path=%s fallback=%s sections=%d ownedSections=%d visibleSections=%d visibleOwnedSections=%d patches=%d ownedPatches=%d submitted=%d replacementActive=%s sodiumSuppressedForNewBuilds=%s motionMrt=%s reflections=%s environment=%s",
                renderStatus, drawPath, fallbackReason == null || fallbackReason.isBlank() ? "none" : fallbackReason,
                stats.sectionsWithWater(), stats.replacementOwnedSections(), visibleSections, visibleOwnedSections, stats.waterPatches(),
                stats.replacementOwnedPatches(), submittedPatches, replacement, replacement, motionMrt,
                reflectionPath, environment);
    }

    private static List<WaterSurfaceExtractor.WaterPatch> collectAllWaterPatches(
            DeferredPrimaryViewSource.FrameView camera) {
        WaterClipCuller culler = WaterClipCuller.from(camera);
        return WaterSurfaceExtractor.snapshot().values().stream()
                .filter(section -> culler.sectionVisible(section.sectionKey()))
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.waterPatches().stream())
                .toList();
    }

    private static List<WaterSurfaceExtractor.WaterPatch> collectReplacementOwnedWaterPatches(
            DeferredPrimaryViewSource.FrameView camera) {
        WaterClipCuller culler = WaterClipCuller.from(camera);
        return WaterSurfaceExtractor.snapshot().values().stream()
                .filter(WaterSurfaceExtractor.SectionPatchMesh::waterReplacementOwned)
                .filter(section -> culler.sectionVisible(section.sectionKey()))
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.waterPatches().stream())
                .toList();
    }

    private static int visibleWaterSectionCount(boolean replacementOwnedOnly,
                                                DeferredPrimaryViewSource.FrameView camera) {
        WaterClipCuller culler = WaterClipCuller.from(camera);
        int visible = 0;
        for (WaterSurfaceExtractor.SectionPatchMesh section : WaterSurfaceExtractor.snapshot().values()) {
            if (section.waterPatches().isEmpty()) continue;
            if (replacementOwnedOnly && !section.waterReplacementOwned()) continue;
            if (culler.sectionVisible(section.sectionKey())) visible++;
        }
        return visible;
    }

    /**
     * Conservative water-only side-plane culler.
     *
     * <p>Minecraft's generic {@code Frustum} also derives near/far planes from the projection.
     * Combatant's world projection is reversed-Z and its clip-depth convention is backend-owned;
     * using that helper for separately-owned water produced false section rejects near the screen
     * boundary on OpenGL. CPU culling here only rejects boxes wholly outside left/right/top/bottom.
     * Near/far clipping stays authoritative on the GPU.</p>
     */
    private record WaterClipCuller(Matrix4f viewProjection, Vec3 cameraPosition, boolean valid) {
        private static final float CLIP_MARGIN = 0.035f;

        static WaterClipCuller from(DeferredPrimaryViewSource.FrameView camera) {
            if (camera == null) return new WaterClipCuller(new Matrix4f(), Vec3.ZERO, false);
            Matrix4f view = camera.view();
            // Water vertices are explicitly camera-relative before this same view rotation reaches
            // TES/fallback VS. Strip translation exactly like buildWaterFrame().
            view.m30(0.0f).m31(0.0f).m32(0.0f);
            Matrix4f vp = camera.unjitteredProjection().mul(view);
            return new WaterClipCuller(vp, camera.cameraPosition(), true);
        }

        boolean sectionVisible(long sectionKey) {
            if (!valid) return true;

            double minX = (SectionPos.x(sectionKey) << 4) - 1.0;
            double minY = (SectionPos.y(sectionKey) << 4) - 0.5;
            double minZ = (SectionPos.z(sectionKey) << 4) - 1.0;
            double maxX = (SectionPos.x(sectionKey) << 4) + 17.0;
            double maxY = (SectionPos.y(sectionKey) << 4) + 16.5;
            double maxZ = (SectionPos.z(sectionKey) << 4) + 17.0;

            boolean allLeft = true;
            boolean allRight = true;
            boolean allBottom = true;
            boolean allTop = true;
            boolean anyInFront = false;

            for (int corner = 0; corner < 8; corner++) {
                double wx = (corner & 1) == 0 ? minX : maxX;
                double wy = (corner & 2) == 0 ? minY : maxY;
                double wz = (corner & 4) == 0 ? minZ : maxZ;
                Vector4f clip = new Vector4f(
                        (float) (wx - cameraPosition.x),
                        (float) (wy - cameraPosition.y),
                        (float) (wz - cameraPosition.z),
                        1.0f);
                viewProjection.transform(clip);

                // Anything intersecting the camera plane fails open. GPU clipping is exact here.
                if (clip.w > 1.0e-5f) anyInFront = true;
                float w = Math.abs(clip.w);
                float margin = Math.max(1.0e-4f, w * CLIP_MARGIN);
                allLeft &= clip.x < -w - margin;
                allRight &= clip.x > w + margin;
                allBottom &= clip.y < -w - margin;
                allTop &= clip.y > w + margin;
            }

            if (!anyInFront) return true;
            return !(allLeft || allRight || allBottom || allTop);
        }
    }

    private static List<WaterSurfaceExtractor.HeightPatch> collectHeightPatches(long minGenerationExclusive) {
        return WaterSurfaceExtractor.snapshot().values().stream()
                .filter(section -> section.generation() > minGenerationExclusive)
                .sorted(Comparator.comparingLong(WaterSurfaceExtractor.SectionPatchMesh::sectionKey))
                .flatMap(section -> section.heightPatches().stream())
                .toList();
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        if (owner != null) resetRoutingState();
        closeOwned();
        owner = rhi;
    }

    private RhiStorageBuffer cameraBuffer() {
        if (owner == null) throw new IllegalStateException("Patch surface source has no RHI owner");
        if (cameraBuffer == null) {
            cameraBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-patch-camera", CAMERA_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return cameraBuffer;
    }

    private RhiStorageBuffer waterFrameBuffer() {
        if (owner == null) throw new IllegalStateException("Patch surface source has no RHI owner");
        if (waterFrameBuffer == null) {
            waterFrameBuffer = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                    "combatant-water-frame", WATER_FRAME_LAYOUT, 1, StorageAccess.READ_ONLY, false));
        }
        return waterFrameBuffer;
    }

    private void disableWaterReplacement() {
        waterFallbackFailed = false;
        WaterSurfacePatchRouting.setReplacementActive(false);
        if (!WaterSurfaceExtractor.hasReplacementOwnedWaterPatches()) {
            WaterSurfacePatchRouting.publishDiagnostics(WaterSurfacePatchRouting.Diagnostics.compatibility("water_disabled"));
        }
    }

    private void resetRoutingState() {
        heightActivationGeneration = Long.MIN_VALUE;
        heightNativeFailed = false;
        waterNativeFailed = false;
        waterBoundaryNativeFailed = false;
        waterReflectionNativeFailed = false;
        waterFallbackFailed = false;
        waterDeformation.reset();
        waterMeshCacheFrameId = Long.MIN_VALUE;
        waterMeshCacheGeneration = Long.MIN_VALUE;
        waterMeshCachePatchCount = -1;
        waterMeshCacheSignature = Long.MIN_VALUE;
        waterPatchMeshCache = null;
        waterTriangleMeshCache = null;
        SurfacePatchRouting.reset();
        WaterSurfacePatchRouting.publishDiagnostics(WaterSurfacePatchRouting.Diagnostics.compatibility("routing_reset"));
    }

    private void closeHeightPipeline() {
        if (heightPipeline != null) {
            try { heightPipeline.close(); } catch (Throwable ignored) { }
            heightPipeline = null;
        }
        heightKey = null;
    }

    private void closeWaterBoundaryPipeline() {
        if (waterBoundaryPipeline != null) {
            try { waterBoundaryPipeline.close(); } catch (Throwable ignored) { }
            waterBoundaryPipeline = null;
        }
        waterBoundaryKey = null;
        waterBoundaryFallbackPipeline = null;
        waterBoundaryFallbackFormat = null;
    }

    private void closeWaterReflectionPipeline() {
        if (waterReflectionPipeline != null) {
            try { waterReflectionPipeline.close(); } catch (Throwable ignored) { }
            waterReflectionPipeline = null;
        }
        waterReflectionKey = null;
        waterReflectionFallbackPipeline = null;
        waterReflectionFallbackKey = null;
    }

    private void closeWaterPipeline() {
        if (waterPipeline != null) {
            try { waterPipeline.close(); } catch (Throwable ignored) { }
            waterPipeline = null;
        }
        waterKey = null;
        waterFallbackPipeline = null;
        waterFallbackKey = null;
    }

    private void closeOwned() {
        closeHeightPipeline();
        closeWaterBoundaryPipeline();
        closeWaterReflectionPipeline();
        closeWaterPipeline();
        if (cameraBuffer != null) {
            try { cameraBuffer.close(); } catch (Throwable ignored) { }
            cameraBuffer = null;
        }
        if (waterFrameBuffer != null) {
            try { waterFrameBuffer.close(); } catch (Throwable ignored) { }
            waterFrameBuffer = null;
        }
    }

    @Override
    public void close() {
        closeOwned();
        heightMesh.close();
        waterMesh.close();
        waterFallbackMesh.close();
        resetRoutingState();
        owner = null;
    }

    private static GpuTextureView requireTexture(DeferredPassContext context, DeferredResource resource) {
        GpuTextureView view = context.resources().texture(resource);
        if (view == null) throw new IllegalStateException("Deferred texture is not bound: " + resource);
        return view;
    }

    private static RhiStorageBuffer requireBuffer(DeferredPassContext context, DeferredResource resource) {
        RhiStorageBuffer buffer = context.resources().buffer(resource);
        if (buffer == null) throw new IllegalStateException("Deferred buffer is not bound: " + resource);
        return buffer;
    }

    private static int samples(GpuTextureView view) {
        return view.texture() instanceof IMsaaTexture msaa ? Math.max(1, msaa.combatant$getSamples()) : 1;
    }

    private static boolean zeroToOneDepth(DeferredPassContext context) {
        return context.rhi().capabilities().zeroToOneDepth();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }

    private record PipelineKey(List<GpuFormat> colorFormats, GpuFormat depthFormat, int samples) {
        PipelineKey {
            colorFormats = List.copyOf(colorFormats);
        }
    }

    private record BoundaryPipelineKey(GpuFormat colorFormat) {
    }

    private static WaterReflectionPipelineKey waterReflectionKey(GpuTextureView color,
                                                                  GpuTextureView confidence,
                                                                  GpuTextureView reprojection,
                                                                  GpuTextureView geometry,
                                                                  GpuTextureView depths,
                                                                  GpuTextureView cascadeColor,
                                                                  GpuTextureView cascadeConfidence) {
        return new WaterReflectionPipelineKey(
                color.texture().getFormat(), confidence.texture().getFormat(),
                reprojection.texture().getFormat(), geometry.texture().getFormat(), depths.texture().getFormat(),
                cascadeColor.texture().getFormat(), cascadeConfidence.texture().getFormat());
    }

    private record WaterReflectionPipelineKey(GpuFormat traceColorFormat,
                                              GpuFormat traceConfidenceFormat,
                                              GpuFormat reprojectionFormat,
                                              GpuFormat geometryFormat,
                                              GpuFormat depthsFormat,
                                              GpuFormat cascadeColorFormat,
                                              GpuFormat cascadeConfidenceFormat) {
        private List<GpuFormat> colorFormats() {
            return List.of(traceColorFormat, traceConfidenceFormat, reprojectionFormat, geometryFormat,
                    depthsFormat, cascadeColorFormat, cascadeConfidenceFormat);
        }
    }

    private record FallbackPipelineKey(GpuFormat sceneFormat, GpuFormat velocityFormat,
                                       GpuFormat motionValidityFormat, GpuFormat temporalCoverageFormat,
                                       GpuFormat reactiveMaskFormat, GpuFormat depthFormat) {
    }
}
