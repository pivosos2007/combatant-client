/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import combatant.client.render.CombatantEntityRenderTypes;

enum IrisRuntimeBridge {
    ;

    static IrisRuntimeSnapshot snapshot() {
        IrisApi api = IrisApi.getInstance();
        String packName = currentPackName();
        boolean shaderpackInUse = probe(api::isShaderPackInUse, packName != null && !packName.isBlank());
        boolean shadersEnabled = shadersEnabled(api, shaderpackInUse);
        boolean renderingShadowPass = probe(api::isRenderingShadowPass, false);
        IrisCompatibilityProfile profile = IrisCompatibilityProfiles.resolve(packName, shaderpackInUse);

        return new IrisRuntimeSnapshot(
                true,
                true,
                shadersEnabled,
                shaderpackInUse,
                renderingShadowPass,
                packName,
                profile,
                "ok"
        );
    }

    static void registerCombatantPipelines() {
        IrisApi api = IrisApi.getInstance();
        java.util.List<RenderPipeline> pipelines = CombatantEntityRenderTypes.irisMappedPipelines();
        for (RenderPipeline pipeline : pipelines) {
            assign(api, pipeline, IrisProgram.ENTITIES_TRANSLUCENT);
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

    private static void assign(IrisApi api, RenderPipeline pipeline, IrisProgram program) {
        if (api == null || pipeline == null || program == null) return;
        try {
            api.assignPipeline(pipeline, program);
        } catch (IllegalStateException ignored) {
            // Iris keeps pipeline assignments globally; resource reloads can call this more than once.
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
