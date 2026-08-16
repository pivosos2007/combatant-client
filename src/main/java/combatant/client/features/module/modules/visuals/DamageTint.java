/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.util.Mth;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.modules.visuals.damage.DamageFeedbackState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.DamageTintUniforms;

//todo Description
@ModuleInfo(id = "damagetint", displayName = "DamageTint", category = ModuleCategory.VISUALS)
public class DamageTint extends Module implements PostProcessPass {

    private static final String SETTING_START_HP = "start_hp";
    private static final String SETTING_FULL_HP = "full_hp";
    private static final String SETTING_VIGNETTE = "vignette";
    private static final String SETTING_DESAT = "desat";
    private static final String SETTING_CONTRAST = "contrast";
    private static final String SETTING_PULSE = "pulse";
    private static final String SETTING_PULSE_SPEED = "pulse_speed";
    private static final String SETTING_PULSE_MIN = "pulse_min";
    private static final String SETTING_PULSE_MAX = "pulse_max";
    private static final String SETTING_FADE_IN = "fade_in";
    private static final String SETTING_FADE_OUT = "fade_out";
    private static final String SETTING_IMPACT = "impact";
    private static final String SETTING_IMPACT_STRENGTH = "impact_strength";
    private static final String SETTING_RED_INTENSITY = "red_intensity";
    private static final String SETTING_EDGE_FLASH = "edge_flash";
    private static final String SETTING_CHROMATIC = "chromatic";
    private static final String SETTING_DISTORTION = "distortion";

    private final Minecraft mc = Minecraft.getInstance();
    private final DamageFeedbackState feedbackState = new DamageFeedbackState();

    // Persistent low-health channel.
    private final NumberValue<Integer> startHearts =
            num("damageTintStartHearts", SETTING_START_HP, 20, 1, 80);
    private final NumberValue<Integer> fullHearts =
            num("damageTintFullHearts", SETTING_FULL_HP, 8, 1, 80);
    private final NumberValue<Float> vignetteStrength =
            num("damageTintVignette", SETTING_VIGNETTE, 0.55f, 0.0f, 1.0f);
    private final NumberValue<Float> desatStrength =
            num("damageTintDesat", SETTING_DESAT, 0.30f, 0.0f, 1.0f);
    private final NumberValue<Float> contrastStrength =
            num("damageTintContrast", SETTING_CONTRAST, 0.20f, 0.0f, 1.0f);
    private final BooleanValue pulse =
            bool("damageTintPulse", SETTING_PULSE, true);
    private final NumberValue<Float> pulseSpeed =
            visibleWhen(num("damageTintPulseSpeed", SETTING_PULSE_SPEED, 0.8f, 0.1f, 4.0f), pulse::get);
    private final NumberValue<Float> pulseMin =
            visibleWhen(num("damageTintPulseMin", SETTING_PULSE_MIN, 0.05f, 0.0f, 1.0f), pulse::get);
    private final NumberValue<Float> pulseMax =
            visibleWhen(num("damageTintPulseMax", SETTING_PULSE_MAX, 0.18f, 0.0f, 1.0f), pulse::get);
    private final NumberValue<Float> fadeInSeconds =
            num("damageTintFadeIn", SETTING_FADE_IN, 0.30f, 0.05f, 2.0f);
    private final NumberValue<Float> fadeOutSeconds =
            num("damageTintFadeOut", SETTING_FADE_OUT, 0.50f, 0.05f, 3.0f);

    // Event-driven damage impulse channel.
    private final BooleanValue impact =
            bool("damageTintImpact", SETTING_IMPACT, true);
    private final NumberValue<Float> impactStrength =
            visibleWhen(num("damageTintImpactStrength", SETTING_IMPACT_STRENGTH, 1.0f, 0.0f, 2.0f), impact::get);
    private final NumberValue<Float> redIntensity =
            visibleWhen(num("damageTintRedIntensity", SETTING_RED_INTENSITY, 0.90f, 0.0f, 2.0f), impact::get);
    private final NumberValue<Float> edgeFlash =
            visibleWhen(num("damageTintEdgeFlash", SETTING_EDGE_FLASH, 0.95f, 0.0f, 2.0f), impact::get);
    private final NumberValue<Float> chromatic =
            visibleWhen(num("damageTintChromatic", SETTING_CHROMATIC, 0.75f, 0.0f, 2.0f), impact::get);
    private final NumberValue<Float> distortion =
            visibleWhen(num("damageTintDistortion", SETTING_DISTORTION, 0.22f, 0.0f, 1.0f), impact::get);

    private float smoothLowHealth;
    private long lastLowHealthUpdateMs;

    {
        PostProcessManager.register(this);
    }

    @Override
    public boolean isActive() {
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        if (smoothLowHealth > 0.001f || getLowHealthTarget() > 0.001f) return true;
        return impact.get() && feedbackState.snapshot().strength() > 0.001f;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public Phase getPhase() {
        return Phase.POST_HAND;
    }

    @Override
    public void onEnable() {
        feedbackState.reset(mc.player);
        smoothLowHealth = 0.0f;
        lastLowHealthUpdateMs = 0L;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (!(event.getPacket() instanceof ClientboundDamageEventPacket damage)) return;

        Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
        feedbackState.onDamageSignal(damage, mc.player, mc.level, camera);
    }

    @EventHandler
    private void onPacketReceivePost(PacketEvent.ReceivePost event) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        Object packet = event.getPacket();
        boolean healthUpdate = packet instanceof ClientboundSetHealthPacket;
        boolean playerDataUpdate = packet instanceof ClientboundSetEntityDataPacket data && data.id() == mc.player.getId();
        if (!healthUpdate && !playerDataUpdate) return;

        feedbackState.sample(mc.player);
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;
        feedbackState.sample(mc.player);
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (!isEnabled() || mc.player == null || mc.level == null || src == null || dst == null) return false;

        float lowT = smoothLowHealthTowards(getLowHealthTarget());

        float lowStrength = lowT * vignetteStrength.get();
        float desat = lowT * desatStrength.get();
        float contrast = lowT * contrastStrength.get();
        float lowPulse = 0.0f;
        if (pulse.get() && lowT > 0.0f) {
            float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
            float wave01 = Mth.sin(time * pulseSpeed.get() * 6.2831855f) * 0.5f + 0.5f;
            float amplitude = Mth.lerp(lowT, pulseMin.get(), pulseMax.get());
            lowPulse = wave01 * amplitude;
            lowStrength *= 1.0f + lowPulse;
        }

        DamageFeedbackState.ImpactSnapshot hit = impact.get()
                ? feedbackState.snapshot()
                : DamageFeedbackState.ImpactSnapshot.NONE;

        float hitStrength = hit.strength() * impactStrength.get();
        float hitRed = hit.redPressure() * redIntensity.get();
        float hitEdge = hit.edgeFlash() * edgeFlash.get();
        float hitChroma = hit.chromatic() * chromatic.get();
        float hitDistortion = hit.distortion() * distortion.get();

        if (lowStrength <= 0.001f
                && desat <= 0.001f
                && contrast <= 0.001f
                && hitStrength <= 0.001f
                && hitEdge <= 0.001f
                && hitChroma <= 0.001f
                && hitRed <= 0.001f) {
            return false;
        }

        DamageTintUniforms.update(
                lowStrength,
                desat,
                contrast,
                lowPulse,
                hitStrength,
                hitRed,
                hitEdge,
                hitChroma,
                hit.directionX(),
                hit.directionY(),
                hit.directional(),
                hitDistortion
        );

        FullScreenRenderer.begin("Combatant Damage Feedback")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.DAMAGE_TINT)
                .uniform("DamageTint", DamageTintUniforms.get())
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .end();

        return true;
    }

    @Override
    public void onDisable() {
        feedbackState.reset(null);
        smoothLowHealth = 0.0f;
        lastLowHealthUpdateMs = 0L;
    }

    private float getLowHealthTarget() {
        if (mc.player == null) return 0.0f;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float hpGate = Mth.floor(hp * 2.0f) / 2.0f;
        float start = startHearts.get().floatValue();
        float full = fullHearts.get().floatValue();
        if (start < full) {
            float tmp = start;
            start = full;
            full = tmp;
        }
        float range = Math.max(0.1f, start - full);
        return Mth.clamp((start - hpGate) / range, 0.0f, 1.0f);
    }

    private float smoothLowHealthTowards(float target) {
        long now = System.currentTimeMillis();
        float dt = lastLowHealthUpdateMs == 0L ? 0.016f : (now - lastLowHealthUpdateMs) / 1000.0f;
        lastLowHealthUpdateMs = now;

        float in = Math.max(0.01f, fadeInSeconds.get());
        float out = Math.max(0.01f, fadeOutSeconds.get());
        float rate = target >= smoothLowHealth ? (1.0f / in) : (1.0f / out);
        float step = Mth.clamp(rate * dt, 0.0f, 1.0f);
        smoothLowHealth += (target - smoothLowHealth) * step;
        smoothLowHealth = Mth.clamp(smoothLowHealth, 0.0f, 1.0f);
        return smoothLowHealth;
    }
}
