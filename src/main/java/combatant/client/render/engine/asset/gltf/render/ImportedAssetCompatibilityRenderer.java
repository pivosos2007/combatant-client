/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.asset.gltf.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuAssetResidency;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuPrimitive;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuResidencyManager;
import combatant.client.render.engine.asset.gltf.gpu.GltfGpuTexture;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.AlphaMode;
import combatant.client.render.engine.asset.gltf.runtime.GltfPrimitivePlacement;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstanceAsset;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.pipeline.RigRenderPipelines;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.engine.uniform.impl.AssetCompatibilityMaterialUniforms;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.ArrayList;

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

        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        int[] counters = new int[4]; // visible, submitted, skinned, translucent
        Vec3 camera = view.cameraPosition();

        CombatantRenderSystem.sceneInstances().visitVisible(
                view,
                SceneVisibilityMode.SECTION_AND_FRUSTUM,
                (instance, state) -> {
                    if (!(instance.asset() instanceof GltfSceneInstanceAsset source)) return true;
                    // First-person assets have their own hand/item submission and must never leak
                    // into the world entity layer.
                    if (instance.drawClass() == SceneDrawClass.HAND) return true;
                    counters[0]++;
                    submitInstance(commands, instance, source, camera, color, depth, counters);
                    return true;
                }
        );

        if (!commands.isEmpty()) CombatantRenderSystem.rhi().drawMeshes(commands);
        stats = new ImportedAssetCompatibilityRenderStats(view.frameId(), counters[0], counters[1], counters[2], counters[3]);
        diagnostic("draw", counters[0], counters[1]);
    }

    private static void diagnostic(String state, int visible, int submitted) {
        int registered = CombatantRenderSystem.sceneInstances().size();
        DebugLog.renderThreadOnChange(
                "gltf.compatibility.submit",
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
    public static void renderHand(RenderTarget target, Matrix4fc handPose, Matrix4fc projection) {
        if (target == null || handPose == null || projection == null) return;
        var color = target.getColorTextureView();
        var depth = target.getDepthTextureView();
        if (color == null || depth == null) return;

        ArrayList<RhiDrawCommand> commands = new ArrayList<>();
        CombatantRenderSystem.sceneInstances().visitDrawClass(SceneDrawClass.HAND, instance -> {
            if (instance.asset() instanceof GltfSceneInstanceAsset source) {
                submitHandInstance(commands, instance, source, color, depth);
            }
            return true;
        });
        if (commands.isEmpty()) return;

        Matrix4f previousProjection = MeshRenderer.projection();
        boolean previousRendering3D = RenderState.rendering3D;
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(handPose);
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
                                           SceneAssetInstance<?> instance,
                                           GltfSceneInstanceAsset source,
                                           com.mojang.renderpearl.api.textures.GpuTextureView color,
                                           com.mojang.renderpearl.api.textures.GpuTextureView depth) {
        GltfGpuAssetResidency residency = GltfGpuResidencyManager.global().acquire(
                CombatantRenderSystem.rhi(), source.asset());
        Matrix4f handLocal = new Matrix4f(instance.currentWorldTransform());

        for (GltfPrimitivePlacement placement : source.layout().primitives()) {
            GltfGpuPrimitive primitive = residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) continue;

            GltfMaterialBinding material = source.asset().material(primitive.material());
            Matrix4f model = new Matrix4f(handLocal).mul(placement.localToAsset());
            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            RenderPipeline pipeline = cull
                    ? RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_CULL
                    : RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_DOUBLE_SIDED;

            GltfGpuTexture base = residency.colorOrWhite(material.baseColorTexture());
            GltfGpuTexture mr = residency.dataOrWhite(material.metallicRoughnessTexture());
            GltfGpuTexture normal = residency.normalOrFlat(material.normalTexture());
            GltfGpuTexture ao = residency.dataOrWhite(material.occlusionTexture());
            GltfGpuTexture emissive = residency.emissiveOrBlack(material.emissiveTexture());

            commands.add(RhiDrawCommand.builder(
                            "Combatant imported hand asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .uniform("AssetMaterial", AssetCompatibilityMaterialUniforms.write(material))
                    .sampler("u_BaseColor", base.view(), base.sampler())
                    .sampler("u_MetallicRoughness", mr.view(), mr.sampler())
                    .sampler("u_Normal", normal.view(), normal.sampler())
                    .sampler("u_Occlusion", ao.view(), ao.sampler())
                    .sampler("u_Emissive", emissive.view(), emissive.sampler())
                    .build());
        }
    }

    private static void submitInstance(ArrayList<RhiDrawCommand> commands,
                                       SceneAssetInstance<?> instance,
                                       GltfSceneInstanceAsset source,
                                       Vec3 camera,
                                       com.mojang.renderpearl.api.textures.GpuTextureView color,
                                       com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                       int[] counters) {
        GltfGpuAssetResidency residency = GltfGpuResidencyManager.global().acquire(
                CombatantRenderSystem.rhi(), source.asset());
        Matrix4f cameraRelative = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z)
                .mul(instance.currentWorldTransform());

        for (GltfPrimitivePlacement placement : source.layout().primitives()) {
            GltfGpuPrimitive primitive = residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) {
                counters[2]++;
                continue;
            }
            GltfMaterialBinding material = source.asset().material(primitive.material());
            if (material.alphaMode() == AlphaMode.BLEND) {
                counters[3]++;
                continue;
            }

            Matrix4f model = new Matrix4f(cameraRelative).mul(placement.localToAsset());
            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            boolean noDepth = instance.drawClass() == SceneDrawClass.WORLD_OVERLAY;
            RenderPipeline pipeline = noDepth
                    ? (cull
                        ? RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_CULL
                        : RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED)
                    : (cull
                        ? RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_CULL
                        : RigRenderPipelines.ASSET_COMPATIBILITY_TRANSLUCENT_DOUBLE_SIDED);

            GltfGpuTexture base = residency.colorOrWhite(material.baseColorTexture());
            GltfGpuTexture mr = residency.dataOrWhite(material.metallicRoughnessTexture());
            GltfGpuTexture normal = residency.normalOrFlat(material.normalTexture());
            GltfGpuTexture ao = residency.dataOrWhite(material.occlusionTexture());
            GltfGpuTexture emissive = residency.emissiveOrBlack(material.emissiveTexture());

            RhiDrawCommand command = RhiDrawCommand.builder(
                            "Combatant imported asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .uniform("AssetMaterial", AssetCompatibilityMaterialUniforms.write(material))
                    .sampler("u_BaseColor", base.view(), base.sampler())
                    .sampler("u_MetallicRoughness", mr.view(), mr.sampler())
                    .sampler("u_Normal", normal.view(), normal.sampler())
                    .sampler("u_Occlusion", ao.view(), ao.sampler())
                    .sampler("u_Emissive", emissive.view(), emissive.sampler())
                    .build();
            commands.add(command);
            counters[1]++;
        }
    }
}
