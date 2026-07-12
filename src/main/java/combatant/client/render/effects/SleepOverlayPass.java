/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.PostProcessUniforms;
import combatant.client.runtime.RuntimeGate;

public final class SleepOverlayPass implements PostProcessPass {
    private static final float FADE_IN_SECONDS = 0.6f;
    private static final float FADE_OUT_SECONDS = 0.6f;

    private final Minecraft mc = Minecraft.getInstance();
    private float strength;
    private long lastUpdateMs;

    @Override
    public boolean isActive() {
        return !RuntimeGate.isPanic() && mc != null && mc.player != null && mc.level != null;
    }

    @Override
    public int getPriority() {
        return 12;
    }

    @Override
    public Phase getPhase() {
        return Phase.POST_HAND;
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (RuntimeGate.isPanic()) {
            strength = 0.0f;
            lastUpdateMs = 0L;
            return false;
        }
        if (mc == null || mc.player == null || mc.level == null) return false;
        if (src == null || dst == null) return false;

        boolean sleeping = mc.player.isSleeping();
        float t = smoothTowards(sleeping ? 1.0f : 0.0f);
        if (t <= 0.001f) return false;

        float vignette = 1.35f * t;
        float desat = 0.2f * t;
        float contrast = 0.0f;

        PostProcessUniforms.update(vignette, desat, contrast, 0.0f);

        FullScreenRenderer.ensureInit();
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.SLEEP_OVERLAY)
                .uniform("PostProcess", PostProcessUniforms.get())
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .end();

        return true;
    }

    private float smoothTowards(float target) {
        long now = System.currentTimeMillis();
        float dt = (lastUpdateMs == 0L) ? 0.016f : (now - lastUpdateMs) / 1000.0f;
        lastUpdateMs = now;

        float rate = target >= strength ? (1.0f / FADE_IN_SECONDS) : (1.0f / FADE_OUT_SECONDS);
        float step = Mth.clamp(rate * dt, 0.0f, 1.0f);
        strength += (target - strength) * step;
        strength = Mth.clamp(strength, 0.0f, 1.0f);
        return strength;
    }
}
