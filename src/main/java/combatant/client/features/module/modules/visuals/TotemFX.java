/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.impl.HeatUniforms;
import combatant.client.render.effects.CurrentTransientEffectBackend;
import combatant.client.render.effects.EffectBudget;
import combatant.client.render.effects.area.CurrentWorldAreaPreviewRenderer;
import combatant.client.render.effects.area.WorldAreaPreviewDescriptor;
import combatant.client.render.effects.kernels.TransientAttackKernels;
import net.minecraft.world.phys.Vec3;

//todo Description
@ModuleInfo(id = "totemfx", displayName = "TotemFX", category = ModuleCategory.VISUALS)
public class TotemFX extends Module implements PostProcessPass {

    private static final String SETTING_INTENSITY = "intensity";
    private static final String SETTING_DISTORTION = "distortion";
    private static final String SETTING_SCALE = "scale";
    private static final String SETTING_SPEED = "speed";
    private static final String SETTING_VIGNETTE = "vignette";
    private static final String SETTING_VIGNETTE_RADIUS = "vignette_radius";
    private static final String SETTING_VIGNETTE_SOFTNESS = "vignette_softness";
    private static final String SETTING_FADE_IN = "fade_in";
    private static final String SETTING_HOLD = "hold";
    private static final String SETTING_FADE_OUT = "fade_out";
    private static final String SETTING_PULSE_SPEED = "pulse_speed";
    private static final String SETTING_PULSE_AMOUNT = "pulse_amount";
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Float> intensity =
            num("totemFxIntensity", SETTING_INTENSITY, 0.65f, 0.0f, 2.0f);
    private final NumberValue<Float> distortion =
            num("totemFxDistortion", SETTING_DISTORTION, 0.7f, 0.0f, 2.0f);
    private final NumberValue<Float> scale =
            num("totemFxScale", SETTING_SCALE, 1.0f, 0.2f, 3.0f);
    private final NumberValue<Float> speed =
            num("totemFxSpeed", SETTING_SPEED, 1.2f, 0.0f, 4.0f);
    private final NumberValue<Float> vignette =
            num("totemFxVignette", SETTING_VIGNETTE, 0.45f, 0.0f, 1.0f);
    private final NumberValue<Float> vignetteRadius =
            num("totemFxVignetteRadius", SETTING_VIGNETTE_RADIUS, 0.45f, 0.1f, 0.9f);
    private final NumberValue<Float> vignetteSoftness =
            num("totemFxVignetteSoftness", SETTING_VIGNETTE_SOFTNESS, 0.35f, 0.05f, 0.9f);
    private final NumberValue<Float> fadeIn =
            num("totemFxFadeIn", SETTING_FADE_IN, 0.25f, 0.05f, 1.5f);
    private final NumberValue<Float> hold =
            num("totemFxHold", SETTING_HOLD, 0.9f, 0.0f, 4.0f);
    private final NumberValue<Float> fadeOut =
            num("totemFxFadeOut", SETTING_FADE_OUT, 0.6f, 0.05f, 2.5f);
    private final NumberValue<Float> pulseSpeed =
            num("totemFxPulseSpeed", SETTING_PULSE_SPEED, 1.6f, 0.1f, 6.0f);
    private final NumberValue<Float> pulseAmount =
            num("totemFxPulseAmount", SETTING_PULSE_AMOUNT, 0.35f, 0.0f, 1.0f);

    private static final long WORLD_RING_LIFETIME_MS = 620L;
    private static final int TOTEM_GOLD = 0xFFFFD36A;
    private static final int TOTEM_MINT = 0xFF68FFD5;

    private final CurrentTransientEffectBackend worldEffects =
            new CurrentTransientEffectBackend(new EffectBudget(64, 16, Integer.MIN_VALUE));
    private long startMs = -1L;
    private long nextEffectId = 1L;
    private volatile WorldBurst worldBurst;

    {
        TransientAttackKernels.register(worldEffects);
        PostProcessManager.register(this);
    }

    public void onTotemPop() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        startMs = now;
        if (mc.player == null) return;

        Vec3 center = mc.player.position().add(0.0, mc.player.getBbHeight() * 0.48, 0.0);
        Vec3 ground = mc.player.position().add(0.0, 0.035, 0.0);
        worldBurst = new WorldBurst(ground, now);
        worldEffects.beginTick(mc.player.tickCount);
        worldEffects.spawn(TransientAttackKernels.plasma(
                nextEffectId++, center, now, 540L, now ^ 0x6A09E667F3BCC909L,
                mc.player.getId(), TOTEM_GOLD, TOTEM_MINT, 1.55f, 1.2f, 100
        ));
        worldEffects.spawn(TransientAttackKernels.sparks(
                nextEffectId++, center, now, 680L, now ^ 0xBB67AE8584CAA73BL,
                mc.player.getId(), TOTEM_MINT, TOTEM_GOLD, 2.6f, 24, 1.15f, 100
        ));
    }

    @Override
    public boolean isActive() {
        return isEnabled()
                && mc.player != null
                && mc.level != null
                && startMs >= 0L;
    }


    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        worldEffects.render(renderer, tickDelta, now);

        WorldBurst burst = worldBurst;
        if (burst == null) return;
        float t = Math.max(0.0f, Math.min(1.0f, (now - burst.startedMs()) / (float) WORLD_RING_LIFETIME_MS));
        if (t >= 1.0f) {
            worldBurst = null;
            return;
        }
        float eased = t * t * (3.0f - 2.0f * t);
        float radius = 0.35f + eased * 3.25f;
        float thickness = 0.22f - eased * 0.12f;
        float alpha = (float) Math.pow(1.0f - t, 1.45);
        CurrentWorldAreaPreviewRenderer.render(renderer, new WorldAreaPreviewDescriptor(
                WorldAreaPreviewDescriptor.Shape.RING,
                burst.center(),
                radius,
                Math.max(0.0f, radius - thickness),
                0.0f,
                Vec3.ZERO,
                0.0f,
                (float) (Math.PI * 2.0),
                72,
                java.util.List.of(),
                TOTEM_MINT,
                TOTEM_GOLD,
                0.16f * alpha,
                0.92f * alpha,
                true
        ));
    }

    @Override
    public void onDisable() {
        startMs = -1L;
        worldBurst = null;
        worldEffects.clear();
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
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        if (src == null || dst == null) return false;

        float env = getEnvelope();
        if (env <= 0.001f) return false;

        float heat = intensity.get() * env;
        float dist = distortion.get() * env;
        float vig = vignette.get() * env;
        if (heat <= 0.001f && dist <= 0.001f && vig <= 0.001f) return false;

        float radius = Mth.clamp(vignetteRadius.get(), 0.05f, 0.95f);
        float softness = Mth.clamp(vignetteSoftness.get(), 0.01f, 1.0f);

        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float pulse = 1.0f + (Mth.sin(time * pulseSpeed.get() * 6.2831855f) * pulseAmount.get());
        pulse = Mth.clamp(pulse, 0.0f, 2.0f);
        vig *= pulse;
        HeatUniforms.update(
                heat,
                dist,
                scale.get(),
                speed.get(),
                vig,
                radius,
                softness,
                time
        );

        FullScreenRenderer.ensureInit();
        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.HEAT_FX)
                .uniform("Heat", HeatUniforms.get())
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .end();

        return true;
    }

    private record WorldBurst(Vec3 center, long startedMs) { }

    private float getEnvelope() {
        if (startMs < 0L) return 0.0f;
        float t = (System.currentTimeMillis() - startMs) / 1000.0f;
        float in = Math.max(0.01f, fadeIn.get());
        float mid = Math.max(0.0f, hold.get());
        float out = Math.max(0.01f, fadeOut.get());
        float total = in + mid + out;
        if (t >= total) {
            startMs = -1L;
            return 0.0f;
        }

        float env;
        if (t < in) {
            env = t / in;
        } else if (t < in + mid) {
            env = 1.0f;
        } else {
            float u = (t - in - mid) / out;
            env = 1.0f - u;
        }

        env = Mth.clamp(env, 0.0f, 1.0f);
        return env * env * (3.0f - 2.0f * env);
    }
}
