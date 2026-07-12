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
import net.minecraft.world.entity.PortalProcessor;
import net.minecraft.world.level.block.Blocks;
import combatant.client.mixins.accessors.EntityAccessor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.HeatUniforms;
import combatant.client.runtime.RuntimeGate;

public final class NetherPortalRiftPass implements PostProcessPass {
    private static final float FADE_IN_SECONDS = 0.25f;
    private static final float FADE_OUT_SECONDS = 0.35f;

    private final Minecraft mc = Minecraft.getInstance();
    private float strength;
    private long lastUpdateMs;

    @Override
    public boolean isActive() {
        return !RuntimeGate.isPanic() && mc != null && mc.player != null && mc.level != null;
    }

    @Override
    public int getPriority() {
        return 11;
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

        PortalProcessor portalManager = null;
        if (mc.player instanceof EntityAccessor accessor) {
            portalManager = accessor.combatant$getPortalManager();
        }
        boolean active = portalManager != null && portalManager.isInsidePortalThisTick() && isInNetherPortalBlock();
        float t = smoothTowards(active ? 1.0f : 0.0f);
        if (t <= 0.001f) return false;

        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float intensity = 0.75f * t;
        float distortion = 0.65f * t;
        float scale = 1.25f;
        float speed = 1.65f;
        float vignetteStrength = 0.85f * t;
        float vignetteRadius = 0.38f;
        float vignetteSoftness = 0.28f;

        HeatUniforms.update(
                intensity,
                distortion,
                scale,
                speed,
                vignetteStrength,
                vignetteRadius,
                vignetteSoftness,
                time
        );

        FullScreenRenderer.ensureInit();
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.PORTAL_RIFT)
                .uniform("Heat", HeatUniforms.get())
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

    private boolean isInNetherPortalBlock() {
        if (mc == null || mc.level == null || mc.player == null) return false;
        var pos = mc.player.blockPosition();
        if (mc.level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return true;
        return mc.level.getBlockState(pos.above()).is(Blocks.NETHER_PORTAL);
    }
}


