/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.mixins.iris.IrisHandRendererAccessor;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import net.irisshaders.iris.pathways.HandRenderer;
import net.irisshaders.iris.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.world.item.ItemStack;
import combatant.client.render.CombatantEntityRenderTypes;
import combatant.client.render.iris.geometry.IrisImportedGeometryPipelines;
import combatant.client.render.iris.geometry.IrisImportedGeometryRenderer;
import combatant.client.render.iris.geometry.IrisImportedGeometryResidency;
import combatant.client.render.iris.patch.ShaderPatchEngine;

enum IrisRuntimeBridge {
    ;
    private static boolean shadowCallbackRegistered;

    static IrisRuntimeSnapshot snapshot() {
        IrisApi api = IrisApi.getInstance();
        String packName = currentPackName();
        boolean shaderpackInUse = probe(api::isShaderPackInUse, packName != null && !packName.isBlank());
        boolean shadersEnabled = shadersEnabled(api, shaderpackInUse);
        boolean renderingShadowPass = probe(api::isRenderingShadowPass, false);
        ShaderPatchEngine.ShaderpackProfile patchProfile = ShaderPatchEngine.profile(packName);
        IrisCompatibilityProfile profile = IrisCompatibilityProfiles.resolve(patchProfile, shaderpackInUse);

        return new IrisRuntimeSnapshot(
                true,
                true,
                shadersEnabled,
                shaderpackInUse,
                renderingShadowPass,
                packName,
                profile,
                patchProfile.manifestId(),
                patchProfile.features(),
                0L,
                "unstamped",
                "ok"
        );
    }

    static void registerCombatantPipelines() {
        IrisApi api = IrisApi.getInstance();
        java.util.List<RenderPipeline> pipelines = CombatantEntityRenderTypes.irisMappedPipelines();
        for (RenderPipeline pipeline : pipelines) {
            assign(api, pipeline, IrisProgram.ENTITIES_TRANSLUCENT);
        }
        assignImportedGeometry(api, IrisImportedGeometryPipelines.GBUFFER_CULL);
        assignImportedGeometry(api, IrisImportedGeometryPipelines.GBUFFER_DOUBLE_SIDED);
        assignImportedGeometry(api, IrisImportedGeometryPipelines.GBUFFER_NO_DEPTH_CULL);
        assignImportedGeometry(api, IrisImportedGeometryPipelines.GBUFFER_NO_DEPTH_DOUBLE_SIDED);
        assignImportedTranslucent(api, IrisImportedGeometryPipelines.TRANSLUCENT_BLEND_CULL);
        assignImportedTranslucent(api, IrisImportedGeometryPipelines.TRANSLUCENT_BLEND_DOUBLE_SIDED);
        assignImportedTranslucent(api, IrisImportedGeometryPipelines.TRANSLUCENT_NO_DEPTH_CULL);
        assignImportedTranslucent(api, IrisImportedGeometryPipelines.TRANSLUCENT_NO_DEPTH_DOUBLE_SIDED);
        assignImportedShadow(api, IrisImportedGeometryPipelines.SHADOW_CULL);
        assignImportedShadow(api, IrisImportedGeometryPipelines.SHADOW_DOUBLE_SIDED);
        if (!shadowCallbackRegistered) {
            api.registerShadowRenderCallback(IrisImportedGeometryRenderer::renderShadow);
            shadowCallbackRegistered = true;
        }
    }

    static boolean isHandRenderingSolid() {
        return HandRenderer.INSTANCE.isRenderingSolid();
    }

    static boolean isHeldItemTranslucent(ItemStack stack) {
        return stack != null && HandRenderer.INSTANCE.isHandTranslucent(stack);
    }

    static boolean hasAnySolidHand() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        return !HandRenderer.INSTANCE.isHandTranslucent(client.player.getMainHandItem())
                || !HandRenderer.INSTANCE.isHandTranslucent(client.player.getOffhandItem());
    }

    static boolean submitNativeHandScene(float tickDelta,
                                         PoseStack poseStack,
                                         SubmitNodeStorage storage) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameRenderer == null) return false;

        HandRenderer handRenderer = HandRenderer.INSTANCE;
        IrisHandRendererAccessor accessor = (IrisHandRendererAccessor) handRenderer;
        boolean previousSolid = handRenderer.isRenderingSolid();
        int light = client.getEntityRenderDispatcher().getPackedLightCoords(client.player, tickDelta);
        try {
            // Iris normally submits these into two Photon phases. Build both subsets into one
            // vanilla storage instead; the existing Iris item filter still selects each subset,
            // while no draw occurs until the native post-world pass.
            accessor.combatant$setRenderingSolid(true);
            client.gameRenderer.itemInHandRenderer.submitHandsWithItems(
                    tickDelta, poseStack, storage, client.player, light);
            accessor.combatant$setRenderingSolid(false);
            client.gameRenderer.itemInHandRenderer.submitHandsWithItems(
                    tickDelta, poseStack, storage, client.player, light);
            return true;
        } finally {
            accessor.combatant$setRenderingSolid(previousSolid);
        }
    }

    static boolean isImportedGeometrySubmission() {
        return ImmediateState.safeToMultiply;
    }

    static boolean beginNativeShaderBypass() {
        boolean previous = ImmediateState.bypass;
        ImmediateState.bypass = true;
        return previous;
    }

    static void restoreNativeShaderBypass(boolean previous) {
        ImmediateState.bypass = previous;
    }

    private static void assign(IrisApi api, RenderPipeline pipeline, IrisProgram program) {
        if (api == null || pipeline == null || program == null) return;

        // Iris' public program mapping resolves a ShaderKey using the pipeline's vertex format. Entity mappings
        // are defined for the vanilla ENTITY layout; assigning a Combatant rig pipeline here would let Iris
        // fall back to an entity shader whose attribute contract does not match the rig vertex format. Keep
        // custom rig pipelines Combatant-owned and only map pipelines that really use vanilla ENTITY vertices.
        if (pipeline.getVertexFormatBinding(0) != DefaultVertexFormat.ENTITY) return;

        try {
            api.assignPipeline(pipeline, program);
        } catch (IllegalStateException ignored) {
            // Iris keeps pipeline assignments globally; resource reloads can call this more than once.
        }
    }

    static void renderImportedGeometryPrimary() {
        IrisImportedGeometryRenderer.renderPrimary();
    }

    static void renderImportedGeometryTranslucent() {
        IrisImportedGeometryRenderer.renderTranslucent();
    }

    static void releaseImportedGeometryBackend(combatant.client.render.engine.rhi.CombatantRhi rhi) {
        IrisImportedGeometryRenderer.releaseBackend(rhi);
    }

    static void invalidateImportedGeometryResidency() {
        IrisImportedGeometryResidency.invalidateImportedIrisResidency();
    }

    static void applyImportedGeometryDrawUniforms() {
        IrisImportedGeometryRenderer.applyCurrentDrawUniforms();
    }

    private static void assignImportedGeometry(IrisApi api, RenderPipeline pipeline) {
        if (api == null || pipeline == null) return;
        try {
            api.assignPipeline(pipeline, IrisProgram.ENTITIES);
        } catch (IllegalStateException ignored) {
            // Global Iris assignments survive resource reloads.
        }
    }

    private static void assignImportedTranslucent(IrisApi api, RenderPipeline pipeline) {
        if (api == null || pipeline == null) return;
        try {
            api.assignPipeline(pipeline, IrisProgram.ENTITIES_TRANSLUCENT);
        } catch (IllegalStateException ignored) {
            // Global Iris assignments survive resource reloads.
        }
    }

    private static void assignImportedShadow(IrisApi api, RenderPipeline pipeline) {
        if (api == null || pipeline == null) return;
        try {
            api.assignPipelineShadow(pipeline, IrisShadowProgram.SHADOW_ENTITIES);
        } catch (IllegalStateException ignored) {
            // Global Iris assignments survive resource reloads.
        }
    }

    private static boolean shadersEnabled(IrisApi api, boolean shaderpackInUse) {
        try {
            if (api.getConfig() != null) {
                return api.getConfig().areShadersEnabled();
            }
        } catch (LinkageError | RuntimeException ignored) {
            // Iris can expose a not-yet-initialized config during early Combatant module/config loading.
        }
        return shaderpackInUse;
    }

    private static String currentPackName() {
        try {
            String value = Iris.getCurrentPackName();
            return value == null ? "" : value;
        } catch (LinkageError | RuntimeException ignored) {
            return "";
        }
    }

    private static boolean probe(BooleanProbe probe, boolean fallback) {
        try {
            return probe.getAsBoolean();
        } catch (LinkageError | RuntimeException ignored) {
            return fallback;
        }
    }

    @FunctionalInterface
    private interface BooleanProbe {
        boolean getAsBoolean();
    }
}
