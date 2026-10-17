/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.asset.gltf.material.GltfMaterialBinding;
import combatant.client.render.engine.asset.gltf.model.AlphaMode;
import combatant.client.render.engine.asset.gltf.runtime.GltfPrimitivePlacement;
import combatant.client.render.engine.asset.gltf.runtime.GltfSceneInstanceAsset;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.instance.SceneAssetInstance;
import combatant.client.render.engine.scene.visibility.SceneViewContext;
import combatant.client.render.engine.scene.visibility.SceneViewType;
import combatant.client.render.engine.scene.visibility.SceneVisibilityMode;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.iris.IrisRuntimeSnapshot;
import combatant.client.render.iris.IrisSceneDepth;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.util.logging.DebugLog;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Generic Iris geometry submission; shaderpack interpretation is supplied only by patch manifests. */
public final class IrisImportedGeometryRenderer {
    public static final String GBUFFER_CAPABILITY = "custom_geometry_gbuffer";
    public static final String TRANSLUCENT_CAPABILITY = "custom_geometry_translucent";
    public static final String SHADOW_CAPABILITY = "custom_geometry_shadows";
    private static final ThreadLocal<Integer> DRAW_SCOPE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<ImportedLighting> CURRENT_DRAW_LIGHTING = new ThreadLocal<>();
    private static final Map<ProgramKey, DrawUniformLocations> DRAW_UNIFORM_LOCATIONS = new ConcurrentHashMap<>();
    private static final Set<ProgramKey> CUSTOM_PROGRAMS_DIRTY = ConcurrentHashMap.newKeySet();

    private IrisImportedGeometryRenderer() {}

    public static boolean ownsPipeline(RenderPipeline pipeline) {
        return IrisImportedGeometryPipelines.owns(pipeline);
    }

    public static boolean isDrawingImportedGeometry() {
        return DRAW_SCOPE_DEPTH.get() > 0;
    }

    /**
     * Called after Iris/Blaze3D has selected the concrete GL program and immediately before the
     * imported draw. Program reuse therefore cannot leak lighting from the preceding instance.
     */
    public static void applyCurrentDrawUniforms() {
        ImportedLighting lighting = CURRENT_DRAW_LIGHTING.get();
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program <= 0) return;

        long epoch = IrisRuntime.integrationEpoch();
        ProgramKey key = new ProgramKey(epoch, program);
        boolean imported = lighting != null && isDrawingImportedGeometry();

        if (!imported) {
            // Native draws only need a write when this exact linked program previously carried the
            // imported selector. Avoid glGetUniformLocation on every ordinary Minecraft draw.
            if (!CUSTOM_PROGRAMS_DIRTY.remove(key)) return;
            DrawUniformLocations locations = drawUniformLocations(key);
            if (locations.geometry() >= 0) GL20C.glUniform1i(locations.geometry(), 0);
            return;
        }

        DrawUniformLocations locations = drawUniformLocations(key);
        if (locations.geometry() >= 0) {
            GL20C.glUniform1i(locations.geometry(), 1);
            CUSTOM_PROGRAMS_DIRTY.add(key);
        }
        if (locations.lightBoundsMin() >= 0) {
            GL20C.glUniform3f(locations.lightBoundsMin(), lighting.minX(), lighting.minY(), lighting.minZ());
        }
        if (locations.lightBoundsMax() >= 0) {
            GL20C.glUniform3f(locations.lightBoundsMax(), lighting.maxX(), lighting.maxY(), lighting.maxZ());
        }
        applyCornerUniform(locations.blockLightA(), lighting.blockLight(), 0);
        applyCornerUniform(locations.blockLightB(), lighting.blockLight(), 4);
        applyCornerUniform(locations.skyLightA(), lighting.skyLight(), 0);
        applyCornerUniform(locations.skyLightB(), lighting.skyLight(), 4);
        applyCornerUniform(locations.skyVisibilityA(), lighting.skyVisibility(), 0);
        applyCornerUniform(locations.skyVisibilityB(), lighting.skyVisibility(), 4);
    }

    private static void applyCornerUniform(int location, float[] values, int offset) {
        if (location < 0 || values == null || values.length < offset + 4) return;
        GL20C.glUniform4f(location,
                values[offset], values[offset + 1], values[offset + 2], values[offset + 3]);
    }

    private static DrawUniformLocations drawUniformLocations(ProgramKey key) {
        return DRAW_UNIFORM_LOCATIONS.computeIfAbsent(key, ignored -> new DrawUniformLocations(
                GL20C.glGetUniformLocation(key.program(), "combatantCustomGeometry"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomLightBoundsMin"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomLightBoundsMax"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomBlockLightCornersA"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomBlockLightCornersB"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomSkyLightCornersA"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomSkyLightCornersB"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomSkyVisibilityCornersA"),
                GL20C.glGetUniformLocation(key.program(), "combatantCustomSkyVisibilityCornersB")
        ));
    }

    public static void renderPrimary() {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, GBUFFER_CAPABILITY) || iris.renderingShadowPass()) return;
        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null || view.type() != SceneViewType.PRIMARY) return;
        submit(view, SceneVisibilityMode.SECTION_AND_FRUSTUM, Route.GBUFFER);
    }

    public static void renderTranslucent() {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, TRANSLUCENT_CAPABILITY) || iris.renderingShadowPass()) return;
        SceneViewContext view = CombatantRenderSystem.currentSceneView();
        if (view == null || view.type() != SceneViewType.PRIMARY) return;
        submit(view, SceneVisibilityMode.SECTION_AND_FRUSTUM, Route.TRANSLUCENT);
    }

    public static void renderShadow(Matrix4f modelView, Matrix4f projection,
                                    double cameraX, double cameraY, double cameraZ, float tickDelta) {
        IrisRuntimeSnapshot iris = IrisRuntime.snapshot();
        if (!active(iris, SHADOW_CAPABILITY)) return;
        long frameId = CombatantRenderSystem.currentContext() != null
                ? CombatantRenderSystem.currentContext().frameId() : 0L;
        int resolution = Math.max(1, ShadowRenderer.RESOLUTION);
        SceneViewContext view = SceneViewContext.secondary(
                frameId, SceneViewType.SHADOW, new Vec3(cameraX, cameraY, cameraZ),
                modelView, projection, resolution, resolution, false);
        submit(view, SceneVisibilityMode.FRUSTUM_ONLY, Route.SHADOW);
    }

    public static void releaseBackend(CombatantRhi rhi) {
        IrisImportedGeometryResidency.releaseBackend(rhi);
    }

    private static void submit(SceneViewContext view, SceneVisibilityMode visibility, Route route) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc != null ? mc.gameRenderer : null;
        if (renderer == null || renderer.mainRenderTarget() == null) return;
        var target = renderer.mainRenderTarget();
        var color = target.getColorTextureView();
        // The color view is only the Blaze3D descriptor anchor: ExtendedShader binds the actual
        // shaderpack MRT. Depth is not a dummy attachment, though. Point it at Iris' current scene
        // depth so fixed-function testing/writes and shaderpack depthtex* consumers share storage.
        var depth = route == Route.SHADOW
                ? target.getDepthTextureView()
                : IrisSceneDepth.importedGeometryDepthAttachment();
        if (color == null || depth == null) return;

        ArrayList<VisibleInstance> visible = new ArrayList<>();
        CombatantRenderSystem.sceneInstances().visitVisible(view, visibility, (instance, state) -> {
            if (instance.asset() instanceof GltfSceneInstanceAsset source) {
                visible.add(new VisibleInstance(instance, source, distanceSquared(instance, view.cameraPosition())));
            }
            return true;
        });
        if (route == Route.TRANSLUCENT) {
            visible.sort(Comparator.comparingDouble(VisibleInstance::distanceSquared).reversed());
        }

        ArrayList<DrawBatch> batches = new ArrayList<>();
        int commandCount = 0;
        for (VisibleInstance entry : visible) {
            ArrayList<RhiDrawCommand> instanceCommands = new ArrayList<>();
            submitInstance(instanceCommands, entry.instance(), entry.source(), view.cameraPosition(), color, depth, route);
            if (instanceCommands.isEmpty()) continue;
            ImportedLighting lighting = route == Route.SHADOW || mc.level == null
                    ? ImportedLighting.DEFAULT
                    : sampleLighting(mc.level, entry.instance().worldBounds());
            batches.add(new DrawBatch(instanceCommands, lighting));
            commandCount += instanceCommands.size();
        }

        Matrix4f worldView = route == Route.SHADOW ? view.viewMatrix() : CombatantWorldMatrices.positionMatrix();
        boolean installedWorldView = false;
        if (!batches.isEmpty()) {
            /*
             * The imported command transform is camera-relative translation * object transform.
             * Iris' ExtendedShader reads the current RenderSystem model-view and multiplies the
             * per-command transform into it. This callback runs from Iris beginTranslucents(), not
             * from Combatant's ordinary WORLD scope, so the global model-view is not guaranteed to
             * contain the captured camera rotation. Without that base view, the same mesh that is
             * visible on the compatibility path is projected in the wrong camera space.
             *
             * Keep Iris authoritative for the actual shaderpack framebuffer. safeToMultiply only
             * prevents the temporary Blaze3D RenderPass descriptor from rebinding mainRenderTarget;
             * ExtendedShader then binds its own before/after-translucent framebuffer as usual.
             */
            var modelView = RenderSystem.getModelViewStack();
            boolean previousSafeToMultiply = ImmediateState.safeToMultiply;
            boolean previousRendering3D = RenderState.rendering3D;
            modelView.pushMatrix();
            try {
                if (worldView != null) {
                    modelView.identity();
                    modelView.mul(worldView);
                    installedWorldView = true;
                }
                RenderState.rendering3D = true;
                ImmediateState.safeToMultiply = true;
                DRAW_SCOPE_DEPTH.set(DRAW_SCOPE_DEPTH.get() + 1);
                try {
                    for (DrawBatch batch : batches) {
                        CURRENT_DRAW_LIGHTING.set(batch.lighting());
                        try {
                            CombatantRenderSystem.rhi().drawMeshes(batch.commands());
                        } finally {
                            CURRENT_DRAW_LIGHTING.remove();
                        }
                    }
                } finally {
                    int scopeDepth = DRAW_SCOPE_DEPTH.get() - 1;
                    if (scopeDepth <= 0) DRAW_SCOPE_DEPTH.remove();
                    else DRAW_SCOPE_DEPTH.set(scopeDepth);
                }
            } finally {
                CURRENT_DRAW_LIGHTING.remove();
                ImmediateState.safeToMultiply = previousSafeToMultiply;
                RenderState.rendering3D = previousRendering3D;
                modelView.popMatrix();
            }
        }
        DebugLog.infoOnChange(
                "iris.imported.geometry.submit." + route.name().toLowerCase(java.util.Locale.ROOT),
                route + "|" + visible.size() + "|" + commandCount + "|" + installedWorldView,
                "[IrisGeometry] route=%s visible=%d commands=%d worldView=%s rendering3D=%s",
                route, visible.size(), commandCount, installedWorldView, RenderState.rendering3D
        );
    }

    private static void submitInstance(ArrayList<RhiDrawCommand> commands,
                                       SceneAssetInstance<?> instance,
                                       GltfSceneInstanceAsset source,
                                       Vec3 camera,
                                       com.mojang.renderpearl.api.textures.GpuTextureView color,
                                       com.mojang.renderpearl.api.textures.GpuTextureView depth,
                                       Route route) {
        SceneDrawClass drawClass = instance.drawClass();
        if (drawClass == SceneDrawClass.HAND) return;

        // OPAQUE/MASK participate in Photon's real G-buffer before deferredRenderer.renderAll().
        // Only canonical BLEND remains in ENTITIES_TRANSLUCENT. WORLD_OVERLAY keeps its no-depth
        // semantics but is still mapped through the G-buffer entity program when it is OPAQUE/MASK.
        boolean noDepth = drawClass == SceneDrawClass.WORLD_OVERLAY;
        if (route == Route.SHADOW && noDepth) return;

        IrisImportedGeometryResidency residency = IrisImportedGeometryResidency.acquire(
                CombatantRenderSystem.rhi(), source.asset());
        Matrix4f cameraRelative = new Matrix4f().translation(
                (float) -camera.x, (float) -camera.y, (float) -camera.z)
                .mul(instance.currentWorldTransform());
        for (GltfPrimitivePlacement placement : source.layout().primitives()) {
            IrisImportedGeometryResidency.Primitive primitive =
                    residency.primitive(placement.meshIndex(), placement.primitiveIndex());
            if (primitive.skinned()) continue;
            GltfMaterialBinding material = source.asset().material(primitive.material());
            if (route == Route.GBUFFER && material.alphaMode() == AlphaMode.BLEND) continue;
            if (route == Route.TRANSLUCENT && material.alphaMode() != AlphaMode.BLEND) continue;

            boolean mirrored = instance.mirroredTransform() ^ placement.mirroredWinding();
            boolean cull = !material.doubleSided() && !mirrored;
            RenderPipeline pipeline = switch (route) {
                case GBUFFER -> noDepth
                        ? (cull ? IrisImportedGeometryPipelines.GBUFFER_NO_DEPTH_CULL
                                : IrisImportedGeometryPipelines.GBUFFER_NO_DEPTH_DOUBLE_SIDED)
                        : (cull ? IrisImportedGeometryPipelines.GBUFFER_CULL
                                : IrisImportedGeometryPipelines.GBUFFER_DOUBLE_SIDED);
                case TRANSLUCENT -> noDepth
                        ? (cull ? IrisImportedGeometryPipelines.TRANSLUCENT_NO_DEPTH_CULL
                                : IrisImportedGeometryPipelines.TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED)
                        : (cull ? IrisImportedGeometryPipelines.TRANSLUCENT_BLEND_CULL
                                : IrisImportedGeometryPipelines.TRANSLUCENT_BLEND_DOUBLE_SIDED);
                case SHADOW -> cull ? IrisImportedGeometryPipelines.SHADOW_CULL
                        : IrisImportedGeometryPipelines.SHADOW_DOUBLE_SIDED;
            };
            Matrix4f model = new Matrix4f(cameraRelative).mul(placement.localToAsset());
            IrisCanonicalMaterialAtlas atlas = residency.material(primitive.material());
            commands.add(RhiDrawCommand.builder("Iris imported asset " + source.asset().asset().id())
                    .pipeline(pipeline)
                    .colorAttachment(color)
                    .depthAttachment(depth)
                    .mesh(primitive.mesh())
                    .transform(model)
                    .sampler("Sampler0", atlas.view(), atlas.sampler())
                    .sampler("Sampler1", atlas.view(), atlas.sampler())
                    .sampler("Sampler2", atlas.view(), atlas.sampler())
                    .build());
        }
    }

    private static ImportedLighting sampleLighting(net.minecraft.client.multiplayer.ClientLevel level, AABB bounds) {
        // Sample inside the instance AABB rather than exactly on its faces. Exact min/max points
        // frequently quantize into the supporting terrain block and make one half of a large mesh
        // inherit ground light while the opposite half sees air/sky. Eight stable corner samples
        // are then trilinearly interpolated per vertex by the Photon adapter.
        double sx0 = insetLow(bounds.minX, bounds.maxX);
        double sx1 = insetHigh(bounds.minX, bounds.maxX);
        double sy0 = insetLow(bounds.minY, bounds.maxY);
        double sy1 = insetHigh(bounds.minY, bounds.maxY);
        double sz0 = insetLow(bounds.minZ, bounds.maxZ);
        double sz1 = insetHigh(bounds.minZ, bounds.maxZ);

        float[] block = new float[8];
        float[] sky = new float[8];
        float[] visibility = new float[8];
        for (int z = 0; z < 2; z++) {
            double wz = z == 0 ? sz0 : sz1;
            for (int y = 0; y < 2; y++) {
                double wy = y == 0 ? sy0 : sy1;
                for (int x = 0; x < 2; x++) {
                    double wx = x == 0 ? sx0 : sx1;
                    int index = x | (y << 1) | (z << 2);
                    BlockPos pos = BlockPos.containing(wx, wy, wz);
                    block[index] = clamp01(level.getBrightness(LightLayer.BLOCK, pos) / 15.0f);
                    sky[index] = clamp01(level.getBrightness(LightLayer.SKY, pos) / 15.0f);
                    visibility[index] = level.canSeeSky(pos) ? 1.0f : 0.0f;
                }
            }
        }

        return new ImportedLighting(
                (float) bounds.minX, (float) bounds.minY, (float) bounds.minZ,
                (float) bounds.maxX, (float) bounds.maxY, (float) bounds.maxZ,
                block, sky, visibility
        );
    }

    private static double insetLow(double min, double max) {
        double extent = Math.max(0.0, max - min);
        double inset = Math.min(0.35, Math.max(0.025, extent * 0.12));
        return extent <= inset * 2.0 ? (min + max) * 0.5 : min + inset;
    }

    private static double insetHigh(double min, double max) {
        double extent = Math.max(0.0, max - min);
        double inset = Math.min(0.35, Math.max(0.025, extent * 0.12));
        return extent <= inset * 2.0 ? (min + max) * 0.5 : max - inset;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static double distanceSquared(SceneAssetInstance<?> instance, Vec3 camera) {
        var bounds = instance.worldBounds();
        double x = (bounds.minX + bounds.maxX) * 0.5 - camera.x;
        double y = (bounds.minY + bounds.maxY) * 0.5 - camera.y;
        double z = (bounds.minZ + bounds.maxZ) * 0.5 - camera.z;
        return x * x + y * y + z * z;
    }

    private static boolean active(IrisRuntimeSnapshot snapshot, String capability) {
        if (!snapshot.modLoaded() || !snapshot.apiAvailable() || !snapshot.shadersEnabled()
                || !snapshot.shaderpackInUse() || !snapshot.patchFeatures().contains(capability)) return false;
        return ShaderPatchEngine.applicationState(snapshot.patchManifestId()).preflightAccepted();
    }

    private enum Route {
        GBUFFER,
        TRANSLUCENT,
        SHADOW
    }

    private record ProgramKey(long epoch, int program) {}

    private record DrawUniformLocations(int geometry,
                                        int lightBoundsMin,
                                        int lightBoundsMax,
                                        int blockLightA,
                                        int blockLightB,
                                        int skyLightA,
                                        int skyLightB,
                                        int skyVisibilityA,
                                        int skyVisibilityB) {}

    private record ImportedLighting(float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ,
                                    float[] blockLight,
                                    float[] skyLight,
                                    float[] skyVisibility) {
        private static final ImportedLighting DEFAULT = new ImportedLighting(
                0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
                new float[] {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
                new float[] {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f},
                new float[] {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f}
        );
    }

    private record DrawBatch(ArrayList<RhiDrawCommand> commands, ImportedLighting lighting) {}

    private record VisibleInstance(SceneAssetInstance<?> instance,
                                   GltfSceneInstanceAsset source,
                                   double distanceSquared) {}
}
