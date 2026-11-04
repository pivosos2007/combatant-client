/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.model.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.asset.model.gpu.ModelGpuAssetResidency;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.gpu.ModelGpuPrimitive;
import combatant.client.render.engine.asset.model.gpu.ModelGpuResidencyManager;
import combatant.client.render.engine.asset.model.gpu.ModelGpuTexture;
import combatant.client.render.engine.asset.model.material.ModelMaterialBinding;
import combatant.client.render.engine.asset.model.data.AlphaMode;
import combatant.client.render.engine.asset.model.runtime.ModelPrimitivePlacement;
import combatant.client.render.engine.asset.model.runtime.ModelSceneInstanceAsset;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.RigRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.pipeline.RenderPipelineRegistry;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.engine.uniform.impl.AssetCompatibilityMaterialUniforms;
import combatant.client.render.engine.uniform.impl.AssetInstanceUniforms;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;

/**
 * Shaderpack-off world-entity path for imported static assets.
 *
 * <p>This is deliberately not the Photon renderer. It proves geometry/material/texture residency,
 * depth, transforms and visibility before pack-specific G-buffer insertion exists. World instances
 * stay off when Iris owns the world instead of pretending to reproduce the active shaderpack. HAND
 * instances use a separate native first-person submission after the world/post pass, so the same
 * semantic route works with both vanilla/Sodium and Iris.</p>
 */
public final class ImportedAssetCompatibilityRenderer {
    private static volatile ImportedAssetCompatibilityRenderStats stats = ImportedAssetCompatibilityRenderStats.EMPTY;

    private ImportedAssetCompatibilityRenderer() {}

    /** Submits opaque imported assets in vanilla's solid-entity section of the main world pass. */
    public static void renderWorldEntities(RenderTarget worldTarget) {
        if (IrisRuntime.isShaderpackRendererActive()) {
            diagnostic("shaderpack-active", 0, 0);
            return;
        }
        if (worldTarget == null) {
            diagnostic("no-world-target", 0, 0);
            return;
        }

        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null) {
            diagnostic("no-scene-view", 0, 0);
            return;
        }
        var color = worldTarget.getColorTextureView();
        var depth = worldTarget.getDepthTextureView();
        if (color == null || depth == null) {
            diagnostic("missing-world-color-or-depth", 0, 0);
            return;
        }

        LinkedHashMap<BatchKey, Batch> batches = new LinkedHashMap<>();
        ArrayList<Batch> overlays = new ArrayList<>();
        PassResources resources = new PassResources();
        int[] counters = new int[4]; // visible, submitted primitives, skinned, translucent
        Vec3 camera = view.cameraPosition();

        CombatantRenderSystem.sceneInstances().visitVisible(
                view,
                SceneVisibilityMode.SECTION_AND_FRUSTUM,
                (instance, state) -> {
                    if (!(instance.asset() instanceof ModelSceneInstanceAsset source)) return true;
                    // First-person assets have their own hand/item submission and must never leak
                    // into the world entity layer.
                    if (instance.drawClass() == SceneDrawClass.HAND) return true;
                    counters[0]++;
                    submitInstance(batches, overlays, resources, instance, source, camera, counters);
                    return true;
                }
        );

        ArrayList<RhiDrawCommand> commands = compileWorldBatches(batches, overlays, color, depth);
        if (!commands.isEmpty()) CombatantRenderSystem.rhi().drawMeshes(commands);
        stats = new ImportedAssetCompatibilityRenderStats(view.frameId(), counters[0], counters[1], counters[2], counters[3],
                commands.size(), counters[1] - commands.size());
        diagnostic("draw", counters[0], counters[1]);
    }

    private static void diagnostic(String state, int visible, int submitted) {
        int registered = CombatantRenderSystem.sceneInstances().size();
        DebugLog.renderThreadOnChange(
                "model.compatibility.submit",
                state + "|" + registered + "|" + visible + "|" + submitted,
                "[GltfCompat] state=%s registered=%d visible=%d submitted=%d rendering3D=%s",
                state, registered, visible, submitted,
                combatant.client.render.engine.RenderState.rendering3D
        );
    }

    public static ImportedAssetCompatibilityRenderStats statsSnapshot() {
        return stats;
    }

    /**
     * Draws imported {@link SceneDrawClass#HAND} instances in the live first-person hand phase.
     * HAND transforms are interpreted as hand-local transforms and are multiplied after the
     * current Minecraft hand pose. The normal reversed-Z depth test/write is kept so imported
     * first-person geometry self-occludes correctly while remaining in front of the world.
     */
    public static void renderHand(GpuTextureView color, GpuTextureView depth,
                                  Matrix4fc handPose, Matrix4fc projection) {
        if (color == null || depth == null || handPose == null || projection == null) return;

        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        PassResources resources = new PassResources();
        CombatantRenderSystem.sceneInstances().visitDrawClass(SceneDrawClass.HAND, instance -> {
            if (instance.asset() instanceof ModelSceneInstanceAsset source) {
                submitHandInstance(commands, resources, instance, source, color, depth);
            }
            return true;
        });
        if (commands.isEmpty()) return;

        Matrix4f previousProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        // renderItemInHand has already multiplied the camera rotation into the global
        // ModelView. Vanilla hands supply its inverse via handPose. Compose both instead
        // of replacing ModelView with handPose, which causes rotation-dependent drift.
        modelView.mul(handPose);
        MeshRenderer.setProjection(new Matrix4f(projection));
        RenderState.rendering3D = true;
        try {
            if (IrisRuntime.isShaderpackRendererActive()) {
                IrisRuntime.runWithNativeShaderBypass(() -> CombatantRenderSystem.rhi().drawMeshes(commands));
            } else {
                CombatantRenderSystem.rhi().drawMeshes(commands);
            }
        } finally {
            RenderState.rendering3D = previousRendering3D;
            MeshRenderer.setProjection(previousProjection);
            modelView.popMatrix();
        }
    }

    private static void submitHandInstance(ArrayList<RhiDrawCommand> commands,
                                           PassResources resources,
                                           SceneAssetInstance<?> instance,
                                           ModelSceneInstanceAsset source,
                                           com.mojang.renderpearl.api.textures.GpuTextureView color,
                                           com.mojang.renderpearl.api.textures.GpuTextureView depth) {
        ModelGpuAssetResidency residency = resources.residency(source.asset());
        Matrix4f handLocal = new Matrix4f(instance.currentWorldTransform());

        for (ModelPrimitivePlacement placement : source.layout().primitives()) {
            ModelGpuPrimitive primitive = residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) continue;

            ModelMaterialBinding material = source.asset().material(primitive.material());
            Matrix4f model = new Matrix4f(handLocal).mul(placement.localToAsset());
            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            RenderPipeline pipeline = cull
                    ? RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_CULL
                    : RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_DOUBLE_SIDED;

            MaterialState materialState = resources.material(residency, material);

            commands.add(RhiDrawCommand.builder(
                            "Combatant imported hand asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .pipelineSpec(RenderPipelineRegistry.global().require(pipeline))
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .uniform("AssetMaterial", materialState.uniform())
                    .sampler("u_BaseColor", materialState.base().view(), materialState.base().sampler())
                    .sampler("u_MetallicRoughness", materialState.metallic().view(), materialState.metallic().sampler())
                    .sampler("u_Normal", materialState.normal().view(), materialState.normal().sampler())
                    .sampler("u_Occlusion", materialState.occlusion().view(), materialState.occlusion().sampler())
                    .sampler("u_Emissive", materialState.emissive().view(), materialState.emissive().sampler())
                    .build());
        }
    }

    /**
     * Collect geometry by immutable primitive/material/raster state before encoding draws.
     * Only opaque and alpha-masked primitives reach this path; BLEND is deliberately
     * left out because transparent geometry requires back-to-front ordering.
     */
    private static void submitInstance(LinkedHashMap<BatchKey, Batch> batches,
                                       ArrayList<Batch> overlays,
                                       PassResources resources,
                                       SceneAssetInstance<?> instance,
                                       ModelSceneInstanceAsset source,
                                       Vec3 camera,
                                       int[] counters) {
        ModelGpuAssetResidency residency = resources.residency(source.asset());
        Matrix4f cameraRelative = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z)
                .mul(instance.currentWorldTransform());

        for (ModelPrimitivePlacement placement : source.layout().primitives()) {
            ModelGpuPrimitive primitive = residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) {
                counters[2]++;
                continue;
            }
            ModelMaterialBinding material = source.asset().material(primitive.material());
            if (material.alphaMode() == AlphaMode.BLEND) {
                counters[3]++;
                continue;
            }

            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            boolean depthTest = instance.drawClass() != SceneDrawClass.WORLD_OVERLAY;
            MaterialState materialState = resources.material(residency, material);
            BatchKey key = new BatchKey(primitive, materialState, cull, depthTest);
            Matrix4f model = new Matrix4f(cameraRelative).mul(placement.localToAsset());
            if (depthTest) {
                batches.computeIfAbsent(key, Batch::new).models.add(model);
            } else {
                // Overlay passes have no depth test. Preserve their original primitive order;
                // only consecutive compatible overlays can share an instanced draw.
                Batch last = overlays.isEmpty() ? null : overlays.getLast();
                if (last == null || !last.key.equals(key)
                        || last.models.size() >= AssetInstanceUniforms.MAX_INSTANCES) {
                    last = new Batch(key);
                    overlays.add(last);
                }
                last.models.add(model);
            }
            counters[1]++;
        }
    }

    /** One indexed draw for up to 64 independent transforms, on both GL and Vulkan. */
    private static ArrayList<RhiDrawCommand> compileWorldBatches(
            LinkedHashMap<BatchKey, Batch> batches, ArrayList<Batch> overlays,
            GpuTextureView color, GpuTextureView depth) {
        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        // Depth-tested opaque scene first, then depth-free overlays in submission order.
        ArrayList<Batch> ordered = new ArrayList<>(batches.values());
        ordered.addAll(overlays);
        for (Batch batch : ordered) {
            BatchKey key = batch.key;
            int size = batch.models.size();
            for (int offset = 0; offset < size;) {
                int count = Math.min(AssetInstanceUniforms.MAX_INSTANCES, size - offset);
                boolean instanced = count >= 2;
                RenderPipeline pipeline = RigRenderPipelines.assetOpaquePipeline(
                        key.cull(), key.depthTest(), instanced);
                RhiDrawCommand.Builder command = RhiDrawCommand.builder(
                                "Combatant imported static PBR batch")
                        .pipeline(pipeline)
                        .pipelineSpec(RenderPipelineRegistry.global().require(pipeline))
                        .colorAttachment(color)
                        .depthAttachment(depth)
                        .mesh(key.primitive().mesh())
                        .instanceCount(count)
                        .uniform("AssetMaterial", key.material().uniform())
                        .sampler("u_BaseColor", key.material().base().view(), key.material().base().sampler())
                        .sampler("u_MetallicRoughness", key.material().metallic().view(), key.material().metallic().sampler())
                        .sampler("u_Normal", key.material().normal().view(), key.material().normal().sampler())
                        .sampler("u_Occlusion", key.material().occlusion().view(), key.material().occlusion().sampler())
                        .sampler("u_Emissive", key.material().emissive().view(), key.material().emissive().sampler());
                if (instanced) {
                    command.uniform("AssetInstances", AssetInstanceUniforms.write(batch.models, offset, count));
                } else {
                    // A singleton avoids allocating a 4 KiB instance palette.
                    command.transform(batch.models.get(offset));
                }
                commands.add(command.build());
                offset += count;
            }
        }
        return commands;
    }

    private record BatchKey(ModelGpuPrimitive primitive, MaterialState material,
                            boolean cull, boolean depthTest) {}

    private static final class Batch {
        private final BatchKey key;
        private final ArrayList<Matrix4f> models = new ArrayList<>();
        private Batch(BatchKey key) { this.key = key; }
    }

    /**
     * Per-physical-pass resource cache: hundreds of scene instances may share an
     * immutable GPU asset and its materials. Upload each material UBO once per pass,
     * and avoid a synchronized residency lookup for every instance. Model transforms
     * are collected into a GPU palette for hardware-instanced opaque draw calls.
     */
    private static final class PassResources {
        private final IdentityHashMap<ModelRuntimeAsset, ModelGpuAssetResidency> residency = new IdentityHashMap<>();
        private final IdentityHashMap<ModelGpuAssetResidency, IdentityHashMap<ModelMaterialBinding, MaterialState>> materials =
                new IdentityHashMap<>();

        ModelGpuAssetResidency residency(ModelRuntimeAsset asset) {
            return residency.computeIfAbsent(asset,
                    key -> ModelGpuResidencyManager.global().acquire(CombatantRenderSystem.rhi(), key));
        }

        MaterialState material(ModelGpuAssetResidency owner, ModelMaterialBinding binding) {
            return materials.computeIfAbsent(owner, ignored -> new IdentityHashMap<>())
                    .computeIfAbsent(binding, material -> new MaterialState(
                            AssetCompatibilityMaterialUniforms.write(material),
                            owner.colorOrWhite(material.baseColorTexture()),
                            owner.dataOrWhite(material.metallicRoughnessTexture()),
                            owner.normalOrFlat(material.normalTexture()),
                            owner.dataOrWhite(material.occlusionTexture()),
                            owner.emissiveOrBlack(material.emissiveTexture())));
        }
    }

    private record MaterialState(GpuBufferSlice uniform, ModelGpuTexture base, ModelGpuTexture metallic,
                                 ModelGpuTexture normal, ModelGpuTexture occlusion, ModelGpuTexture emissive) {}

}
