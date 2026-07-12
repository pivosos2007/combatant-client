/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.addon;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import combatant.client.api.v0.render.CombatantPostProcessCallback;
import combatant.client.api.v0.render.CombatantPostProcessContext;
import combatant.client.api.v0.render.CombatantRenderCallback;
import combatant.client.api.v0.render.CombatantRenderContext;
import combatant.client.api.v0.render.CombatantRenderStage;
import combatant.client.features.module.HudPhase;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum AddonRenderPipelineManager {
    ;

    private static final Map<CombatantRenderStage, List<RegisteredRenderCallback>> CALLBACKS =
            new EnumMap<>(CombatantRenderStage.class);

    static {
        for (CombatantRenderStage stage : CombatantRenderStage.values()) {
            CALLBACKS.put(stage, new ArrayList<>());
        }
    }

    public static synchronized boolean register(String addonId,
                                                String callbackId,
                                                CombatantRenderStage stage,
                                                CombatantRenderCallback callback) {
        String normalizedAddonId = normalize(addonId);
        String normalizedCallbackId = normalize(callbackId);
        if (normalizedAddonId.isBlank() || normalizedCallbackId.isBlank() || stage == null || callback == null) {
            return false;
        }
        List<RegisteredRenderCallback> callbacks = CALLBACKS.get(stage);
        for (RegisteredRenderCallback registered : callbacks) {
            if (registered.addonId().equals(normalizedAddonId) && registered.callbackId().equals(normalizedCallbackId)) {
                return false;
            }
        }
        callbacks.add(new RegisteredRenderCallback(normalizedAddonId, normalizedCallbackId, callback));
        return true;
    }

    public static boolean hasActiveCallbacks(CombatantRenderStage stage) {
        if (stage == null) return false;
        List<RegisteredRenderCallback> callbacks;
        synchronized (AddonRenderPipelineManager.class) {
            callbacks = List.copyOf(CALLBACKS.get(stage));
        }
        for (RegisteredRenderCallback callback : callbacks) {
            if (AddonManager.isActive(callback.addonId())) return true;
        }
        return false;
    }

    public static boolean registerPostProcess(String addonId,
                                              String passId,
                                              PostProcessPass.Phase phase,
                                              int priority,
                                              CombatantPostProcessCallback callback) {
        String normalizedAddonId = normalize(addonId);
        String normalizedPassId = normalize(passId);
        if (normalizedAddonId.isBlank() || normalizedPassId.isBlank() || phase == null || callback == null) {
            return false;
        }
        PostProcessManager.register(new AddonPostProcessPass(normalizedAddonId, normalizedPassId, phase, priority, callback));
        return true;
    }

    public static void render2D(CombatantRenderStage stage,
                                HudPhase hudPhase,
                                Renderer2D renderer,
                                TextRenderer textRenderer,
                                GuiGraphicsExtractor guiContext,
                                float tickDelta) {
        render(stage, hudPhase, null, renderer, null, null, textRenderer, guiContext, null, tickDelta);
    }

    public static void render3D(CombatantRenderStage stage,
                                WorldPhase worldPhase,
                                Renderer3D renderer,
                                Renderer3D depthRenderer,
                                PoseStack poseStack,
                                float tickDelta) {
        render(stage, null, worldPhase, null, renderer, depthRenderer, null, null, poseStack, tickDelta);
    }

    public static synchronized void unregisterAddon(String addonId) {
        String normalizedAddonId = normalize(addonId);
        for (List<RegisteredRenderCallback> callbacks : CALLBACKS.values()) {
            callbacks.removeIf(callback -> callback.addonId().equals(normalizedAddonId));
        }
    }

    private static void render(CombatantRenderStage stage,
                               HudPhase hudPhase,
                               WorldPhase worldPhase,
                               Renderer2D renderer2D,
                               Renderer3D renderer3D,
                               Renderer3D depthRenderer3D,
                               TextRenderer textRenderer,
                               GuiGraphicsExtractor guiContext,
                               PoseStack poseStack,
                               float tickDelta) {
        List<RegisteredRenderCallback> callbacks;
        synchronized (AddonRenderPipelineManager.class) {
            callbacks = List.copyOf(CALLBACKS.get(stage));
        }
        for (RegisteredRenderCallback registered : callbacks) {
            if (!AddonManager.isActive(registered.addonId())) continue;
            try {
                registered.callback().render(new CombatantRenderContext(
                        registered.addonId(),
                        registered.callbackId(),
                        stage,
                        hudPhase,
                        worldPhase,
                        renderer2D,
                        renderer3D,
                        depthRenderer3D,
                        textRenderer,
                        guiContext,
                        poseStack,
                        tickDelta
                ));
            } catch (Throwable t) {
                DebugLog.error("[Addons] Render callback failed: addon=%s id=%s",
                        t, registered.addonId(), registered.callbackId());
            }
        }
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private record RegisteredRenderCallback(String addonId,
                                            String callbackId,
                                            CombatantRenderCallback callback) {
    }

    private record AddonPostProcessPass(String addonId,
                                        String passId,
                                        PostProcessPass.Phase phase,
                                        int priority,
                                        CombatantPostProcessCallback callback) implements PostProcessPass {
        @Override
        public boolean isActive() {
            return AddonManager.isActive(addonId);
        }

        @Override
        public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
            if (!isActive()) return false;
            try {
                return callback.render(new CombatantPostProcessContext(addonId, passId, phase, src, dst, tickDelta));
            } catch (Throwable t) {
                DebugLog.error("[Addons] Post-process callback failed: addon=%s id=%s", t, addonId, passId);
                return false;
            }
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public Phase getPhase() {
            return phase;
        }
    }
}
