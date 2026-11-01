/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.asset.model.ImportedModelRepository;
import combatant.client.render.engine.asset.model.ModelRuntimeAsset;
import combatant.client.render.engine.asset.model.geometry.ModelCompiledPrimitive;
import combatant.client.render.engine.asset.model.geometry.ModelTriangleIndices;
import combatant.client.render.engine.asset.model.material.ModelMaterialBinding;
import combatant.client.render.engine.asset.model.data.ModelMesh;
import combatant.client.render.engine.asset.model.data.ModelPrimitive;
import combatant.client.render.engine.asset.model.runtime.ModelSceneInstanceAsset;
import combatant.client.render.engine.asset.model.ImportedModelInstances;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.material.MaterialTextureSemantic;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import combatant.client.runtime.CombatantBuild;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.io.IOException;

/** Dev acceptance harness for imported geometry and shaderpack-owned material rendering. */
@ModuleInfo(id = "gltfassettest", displayName = "Imported Model Test", category = ModuleCategory.MISC)
public final class ImportedModelTest extends Module {
    private final StringValue assetLocation = text(
            "gltfTestAsset", "asset", "combatant:models/dev/boombox.gltf");
    private final BooleanValue invalidateBeforeLoad = bool(
            "gltfTestInvalidate", "invalidate_cache", false);
    private final NumberValue<Double> distance = num(
            "gltfTestDistance", "distance", 4.0, 1.0, 32.0);
    private final NumberValue<Double> scale = num(
            "gltfTestScale", "scale", 100.0, 0.01, 500.0);
    private final BooleanValue animateRotation = bool(
            "gltfTestRotate", "animate_rotation", true);
    private final BooleanValue depthTest = bool(
            "gltfTestDepthTest", "depth_test", true);

    private SceneAssetInstance<ModelSceneInstanceAsset> instance;
    private ModelRuntimeAsset loadedAsset;
    private boolean appliedDepthTest;
    private Vec3 anchor = Vec3.ZERO;
    private float angle;

    public ImportedModelTest() {
        if (!CombatantBuild.isDevelopmentBuild()) {
            throw new IllegalStateException("ImportedModelTest requires a Combatant dev build");
        }
    }

    @Override
    public void onEnable() {
        closeInstance();
        loadedAsset = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.getResourceManager() == null || mc.gameRenderer == null) {
            CommandOutput.error("Model test: world/resource manager is unavailable");
            return;
        }

        Identifier id = Identifier.tryParse(assetLocation.get().trim());
        if (id == null) {
            CommandOutput.error("Model test: invalid resource id: " + assetLocation.get());
            return;
        }

        ImportedModelRepository repository = ImportedModelRepository.global();
        if (invalidateBeforeLoad.get()) repository.invalidateAll();

        try {
            ModelRuntimeAsset runtime = repository.load(mc.getResourceManager(), id);
            Diagnostics diagnostics = inspect(runtime);

            // Spawn directly in front of the camera yaw and place the imported bounds on the
            // player's foot plane. BoomBox is authored in centimetre-scale coordinates, hence the
            // larger default scale; bounds-based placement keeps this valid for alternate assets.
            Vec3 cameraForward = Vec3.directionFromRotation(0.0f, mc.gameRenderer.mainCamera().yRot());
            Vec3 horizontalForward = new Vec3(cameraForward.x, 0.0, cameraForward.z);
            if (horizontalForward.lengthSqr() < 1.0e-8) horizontalForward = new Vec3(0.0, 0.0, 1.0);
            horizontalForward = horizontalForward.normalize();
            Vec3 horizontalAnchor = mc.player.position().add(horizontalForward.scale(distance.get()));
            var sceneBounds = runtime.sceneLayout(runtime.asset().defaultScene()).bounds();
            double originY = mc.player.getY() - sceneBounds.minY() * scale.get();
            anchor = new Vec3(horizontalAnchor.x, originY, horizontalAnchor.z);
            angle = 0.0f;

            loadedAsset = runtime;
            spawnInstance();

            String message = "Model test spawned " + id
                    + " at " + formatPosition(anchor)
                    + " groundY=" + String.format(java.util.Locale.ROOT, "%.1f", mc.player.getY())
                    + " | distance=" + String.format(java.util.Locale.ROOT, "%.1f", distance.get())
                    + " depthTest=" + depthTest.get()
                    + " scenes=" + runtime.asset().scenes().size()
                    + " nodes=" + runtime.asset().nodes().size()
                    + " meshes=" + runtime.asset().meshes().size()
                    + " materials=" + runtime.materials().size()
                    + " textures=" + runtime.asset().textures().size()
                    + " surfacePrimitives=" + diagnostics.surfacePrimitives
                    + " triangles=" + diagnostics.triangles
                    + " generatedNormals=" + diagnostics.generatedNormals
                    + " generatedTangents=" + diagnostics.generatedTangents
                    + " PBR[n=" + diagnostics.normalMaterials
                    + ",mr=" + diagnostics.metallicRoughnessMaterials
                    + ",e=" + diagnostics.emissiveMaterials + "]";
            CommandOutput.success(message);
            DebugLog.info(message);
        } catch (IOException | RuntimeException exception) {
            closeInstance();
            loadedAsset = null;
            CommandOutput.error("glTF test failed for " + id + ": " + concise(exception));
            DebugLog.error("glTF dev asset test failed for {}", id, exception);
        }
    }

    @Override
    public void onDisable() {
        closeInstance();
        loadedAsset = null;
    }

    @Override
    public void onFrame(float tickDelta) {
        if (loadedAsset != null && appliedDepthTest != depthTest.get()) {
            spawnInstance();
        }
        if (instance == null || instance.isClosed() || !animateRotation.get()) return;
        angle += Math.max(0.0f, tickDelta) * 0.025f;
        instance.setWorldTransform(transform());
    }

    private Matrix4f transform() {
        float s = scale.get().floatValue();
        return new Matrix4f()
                .translation((float) anchor.x, (float) anchor.y, (float) anchor.z)
                .rotateY(angle)
                .scale(s);
    }

    private void spawnInstance() {
        if (loadedAsset == null) return;
        closeInstance();
        appliedDepthTest = depthTest.get();
        instance = ImportedModelInstances.spawnDefault(
                CombatantRenderSystem.sceneInstances(),
                loadedAsset,
                transform(),
                appliedDepthTest ? SceneDrawClass.ENTITY : SceneDrawClass.WORLD_OVERLAY,
                new SceneLodProfile(new float[0], 0.10f)
        );
    }

    private void closeInstance() {
        if (instance == null) return;
        try {
            instance.close();
        } catch (Throwable ignored) {
        }
        instance = null;
    }

    private static String formatPosition(Vec3 position) {
        return String.format(java.util.Locale.ROOT, "[%.1f, %.1f, %.1f]", position.x, position.y, position.z);
    }

    private static Diagnostics inspect(ModelRuntimeAsset runtime) {
        int surfacePrimitives = 0;
        int triangles = 0;
        int generatedNormals = 0;
        int generatedTangents = 0;

        for (int meshIndex = 0; meshIndex < runtime.asset().meshes().size(); meshIndex++) {
            ModelMesh mesh = runtime.asset().meshes().get(meshIndex);
            for (int primitiveIndex = 0; primitiveIndex < mesh.primitives().size(); primitiveIndex++) {
                ModelPrimitive primitive = mesh.primitives().get(primitiveIndex);
                if (!ModelTriangleIndices.supports(primitive.mode())) continue;
                ModelCompiledPrimitive compiled = runtime.compilePrimitive(meshIndex, primitiveIndex);
                surfacePrimitives++;
                triangles += compiled.triangleCount();
                if (compiled.generatedNormals()) generatedNormals++;
                if (compiled.generatedTangents()) generatedTangents++;
            }
        }

        int normalMaterials = 0;
        int metallicRoughnessMaterials = 0;
        int emissiveMaterials = 0;
        for (ModelMaterialBinding material : runtime.materials()) {
            if (material.surface().textures().has(MaterialTextureSemantic.NORMAL)) normalMaterials++;
            if (material.surface().textures().has(MaterialTextureSemantic.METALLIC_ROUGHNESS)) metallicRoughnessMaterials++;
            if (material.surface().textures().has(MaterialTextureSemantic.EMISSIVE)) emissiveMaterials++;
        }
        return new Diagnostics(surfacePrimitives, triangles, generatedNormals, generatedTangents,
                normalMaterials, metallicRoughnessMaterials, emissiveMaterials);
    }

    private static String concise(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private record Diagnostics(int surfacePrimitives, int triangles, int generatedNormals,
                               int generatedTangents, int normalMaterials,
                               int metallicRoughnessMaterials, int emissiveMaterials) {}
}
