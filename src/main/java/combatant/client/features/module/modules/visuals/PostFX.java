/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.postprocess.PostProcessManager;
import combatant.client.render.engine.postprocess.PostProcessPass;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.PostFXUniforms;

//todo Description
@ModuleInfo(id = "postfx", displayName = "PostFX", category = ModuleCategory.VISUALS)
public class PostFX extends Module implements PostProcessPass {

    private static final String SETTING_PRESET = "preset";
    private static final String SETTING_LUT_PRESET = "lut_preset";
    private static final String SETTING_LUT_STRENGTH = "lut_strength";
    private static final String SETTING_TONE_MAP = "tone_map";
    private static final String SETTING_EXPOSURE = "exposure";
    private static final String SETTING_GAMMA = "gamma";
    private static final String SETTING_CONTRAST = "contrast";
    private static final String SETTING_SATURATION = "saturation";
    private static final String SETTING_VIBRANCE = "vibrance";
    private static final String SETTING_TEMPERATURE = "temperature";
    private static final String SETTING_TINT = "tint";
    private static final String SETTING_VIGNETTE_STRENGTH = "vignette_strength";
    private static final String SETTING_VIGNETTE_RADIUS = "vignette_radius";
    private static final String SETTING_VIGNETTE_SOFTNESS = "vignette_softness";
    private static final String SETTING_GRAIN_AMOUNT = "grain_amount";
    private static final String SETTING_GRAIN_SIZE = "grain_size";
    private static final String SETTING_SHARPEN = "sharpen";
    private static final String SETTING_CHROMA = "chromatic";
    private static final String SETTING_SHADOW_TINT = "shadow_tint";
    private static final String SETTING_SHADOW_STRENGTH = "shadow_strength";
    private static final String SETTING_MID_TINT = "mid_tint";
    private static final String SETTING_MID_STRENGTH = "mid_strength";
    private static final String SETTING_HIGH_TINT = "high_tint";
    private static final String SETTING_HIGH_STRENGTH = "high_strength";
    private final Minecraft mc = Minecraft.getInstance();

    private final ModeValue preset =
            modeSetting("postFxPreset", SETTING_PRESET, "Vanilla", "Vanilla", "Soft", "Warm", "Cool", "Custom");

    private final ModeValue lutPreset =
            visibleWhen(modeSetting("postFxLutPreset", SETTING_LUT_PRESET, "Identity", "Identity", "Warm", "Cool"), this::isCustomPreset);

    private final NumberValue<Float> lutStrength =
            visibleWhen(num("postFxLutStrength", SETTING_LUT_STRENGTH, 0.15f, 0.0f, 1.0f), this::isCustomPreset);

    private final ModeValue toneMap =
            visibleWhen(modeSetting("postFxToneMap", SETTING_TONE_MAP, "None", "None", "Filmic"), this::isCustomPreset);

    private final NumberValue<Float> exposure =
            visibleWhen(num("postFxExposure", SETTING_EXPOSURE, 0.0f, -1.0f, 1.0f), this::isCustomPreset);
    private final NumberValue<Float> gamma =
            visibleWhen(num("postFxGamma", SETTING_GAMMA, 1.0f, 0.5f, 2.5f), this::isCustomPreset);
    private final NumberValue<Float> contrast =
            visibleWhen(num("postFxContrast", SETTING_CONTRAST, 1.0f, 0.0f, 2.0f), this::isCustomPreset);
    private final NumberValue<Float> saturation =
            visibleWhen(num("postFxSaturation", SETTING_SATURATION, 1.0f, 0.0f, 2.0f), this::isCustomPreset);
    private final NumberValue<Float> vibrance =
            visibleWhen(num("postFxVibrance", SETTING_VIBRANCE, 1.0f, 0.0f, 2.0f), this::isCustomPreset);
    private final NumberValue<Float> temperature =
            visibleWhen(num("postFxTemperature", SETTING_TEMPERATURE, 0.0f, -1.0f, 1.0f), this::isCustomPreset);
    private final NumberValue<Float> tint =
            visibleWhen(num("postFxTint", SETTING_TINT, 0.0f, -1.0f, 1.0f), this::isCustomPreset);

    private final NumberValue<Float> vignetteStrength =
            visibleWhen(num("postFxVignetteStrength", SETTING_VIGNETTE_STRENGTH, 0.08f, 0.0f, 1.0f), this::isCustomPreset);
    private final NumberValue<Float> vignetteRadius =
            visibleWhen(num("postFxVignetteRadius", SETTING_VIGNETTE_RADIUS, 0.55f, 0.0f, 1.0f), this::isCustomPreset);
    private final NumberValue<Float> vignetteSoftness =
            visibleWhen(num("postFxVignetteSoftness", SETTING_VIGNETTE_SOFTNESS, 0.35f, 0.01f, 1.0f), this::isCustomPreset);

    private final NumberValue<Float> grainAmount =
            visibleWhen(num("postFxGrainAmount", SETTING_GRAIN_AMOUNT, 0.0f, 0.0f, 0.25f), this::isCustomPreset);
    private final NumberValue<Float> grainSize =
            visibleWhen(num("postFxGrainSize", SETTING_GRAIN_SIZE, 1.0f, 0.25f, 2.0f), this::isCustomPreset);
    private final NumberValue<Float> sharpen =
            num("postFxSharpen", SETTING_SHARPEN, 0.05f, -0.5f, 1.0f);
    private final NumberValue<Float> chromatic =
            num("postFxChromatic", SETTING_CHROMA, 0.0f, 0.0f, 0.5f);

    private final RGBColorValue shadowTint =
            visibleWhen(colorNoAlpha("postFxShadowTint", SETTING_SHADOW_TINT, "#FFFFFF"), this::isCustomPreset);
    private final NumberValue<Float> shadowStrength =
            visibleWhen(num("postFxShadowStrength", SETTING_SHADOW_STRENGTH, 0.0f, 0.0f, 1.0f), this::isCustomPreset);
    private final RGBColorValue midTint =
            visibleWhen(colorNoAlpha("postFxMidTint", SETTING_MID_TINT, "#FFFFFF"), this::isCustomPreset);
    private final NumberValue<Float> midStrength =
            visibleWhen(num("postFxMidStrength", SETTING_MID_STRENGTH, 0.0f, 0.0f, 1.0f), this::isCustomPreset);
    private final RGBColorValue highTint =
            visibleWhen(colorNoAlpha("postFxHighTint", SETTING_HIGH_TINT, "#FFFFFF"), this::isCustomPreset);
    private final NumberValue<Float> highStrength =
            visibleWhen(num("postFxHighStrength", SETTING_HIGH_STRENGTH, 0.0f, 0.0f, 1.0f), this::isCustomPreset);

    private final float[] ubo = new float[PostFXUniforms.FLOAT_COUNT];

    {
        PostProcessManager.register(this);
    }

    @Override
    public boolean isActive() {
        return isEnabled() && mc != null && mc.player != null && mc.level != null;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public Phase getPhase() {
        return Phase.POST_HAND;
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        if (!isActive()) return false;
        if (src == null || dst == null) return false;

        FullScreenRenderer.ensureInit();

        String presetName = preset.get();
        boolean isCustom = isCustomPreset(presetName);

        float lutMode = resolveLutMode(presetName, isCustom);
        GpuTextureView lutView = src;

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        float invW = w > 0 ? 1.0f / w : 0.0f;
        float invH = h > 0 ? 1.0f / h : 0.0f;
        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;

        boolean lutAvailable = lutMode > 0.0f;
        fillUniforms(invW, invH, time, presetName, isCustom, lutAvailable);
        ubo[31] = lutMode;
        PostFXUniforms.update(ubo);

        FullScreenRenderer.begin("Combatant Fullscreen Pass")
                .attachment(dst)
                .pipeline(CombatantRenderPipelines.POST_FX)
                .uniform("PostFX", PostFXUniforms.get())
                .sampler("u_Texture", src, PostProcessManager.getSampler())
                .sampler("u_Lut", lutView, PostProcessManager.getSampler())
                .end();

        return true;
    }

    private void fillUniforms(float invW, float invH, float time, String presetName, boolean isCustom, boolean lutAvailable) {
        if (isCustom) {
            ubo[0] = exposure.get();
            ubo[1] = gamma.get();
            ubo[2] = contrast.get();
            ubo[3] = saturation.get();

            ubo[4] = vibrance.get();
            ubo[5] = temperature.get();
            ubo[6] = tint.get();
            ubo[7] = lutStrength.get();

            ubo[8] = vignetteStrength.get();
            ubo[9] = vignetteRadius.get();
            ubo[10] = vignetteSoftness.get();
            ubo[11] = chromatic.get();

            ubo[12] = grainAmount.get();
            ubo[13] = grainSize.get();
            ubo[14] = sharpen.get();
            ubo[15] = "Filmic".equalsIgnoreCase(toneMap.get()) ? 1.0f : 0.0f;

            fillColor(ubo, 16, shadowTint.getArgb(), shadowStrength.get());
            fillColor(ubo, 20, midTint.getArgb(), midStrength.get());
            fillColor(ubo, 24, highTint.getArgb(), highStrength.get());
        } else {
            applyPreset(presetName);
        }

        // Always allow user tuning for these, even on presets.
        ubo[11] = chromatic.get();
        ubo[14] = sharpen.get();

        if (!lutAvailable) {
            ubo[7] = 0.0f;
        }

        ubo[28] = invW;
        ubo[29] = invH;
        ubo[30] = time;
        ubo[31] = 0.0f;
    }

    private void fillColor(float[] out, int offset, int argb, float strength) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        out[offset] = r;
        out[offset + 1] = g;
        out[offset + 2] = b;
        out[offset + 3] = strength;
    }

    private float resolveLutMode(String presetName, boolean isCustom) {
        String pick = isCustom ? lutPreset.get() : presetName;
        return switch (pick) {
            case "Warm" -> 1.0f;
            case "Cool" -> 2.0f;
            default -> 0.0f;
        };
    }

    private boolean isCustomPreset() {
        return isCustomPreset(preset.get());
    }

    private boolean isCustomPreset(String name) {
        return "Custom".equalsIgnoreCase(name);
    }

    private void applyPreset(String name) {
        String presetName = name == null ? "Vanilla" : name;
        switch (presetName) {
            case "Soft" -> fillPresetSoft();
            case "Warm" -> fillPresetWarm();
            case "Cool" -> fillPresetCool();
            default -> fillPresetVanilla();
        }
    }

    private void fillPresetVanilla() {
        ubo[0] = 0.0f;   // exposure
        ubo[1] = 1.0f;   // gamma
        ubo[2] = 1.0f;   // contrast
        ubo[3] = 1.0f;   // saturation
        ubo[4] = 1.0f;   // vibrance
        ubo[5] = 0.0f;   // temp
        ubo[6] = 0.0f;   // tint
        ubo[7] = 0.0f;   // lut strength
        ubo[8] = 0.08f;  // vignette strength
        ubo[9] = 0.55f;  // vignette radius
        ubo[10] = 0.35f; // vignette softness
        ubo[11] = 0.0f;  // chroma
        ubo[12] = 0.0f;  // grain amount
        ubo[13] = 1.0f;  // grain size
        ubo[14] = 0.05f; // sharpen
        ubo[15] = 0.0f;  // tone map none
        fillColor(ubo, 16, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 20, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 24, 0xFFFFFFFF, 0.0f);
    }

    private void fillPresetSoft() {
        ubo[0] = 0.0f;
        ubo[1] = 1.0f;
        ubo[2] = 1.0f;
        ubo[3] = 1.01f;
        ubo[4] = 1.0f;
        ubo[5] = 0.0f;
        ubo[6] = 0.0f;
        ubo[7] = 0.18f;
        ubo[8] = 0.08f;
        ubo[9] = 0.55f;
        ubo[10] = 0.35f;
        ubo[11] = 0.01f;
        ubo[12] = 0.0f;
        ubo[13] = 1.0f;
        ubo[14] = 0.05f;
        ubo[15] = 0.0f;
        fillColor(ubo, 16, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 20, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 24, 0xFFFFFFFF, 0.0f);
    }

    private void fillPresetWarm() {
        ubo[0] = 0.0f;
        ubo[1] = 1.0f;
        ubo[2] = 1.01f;
        ubo[3] = 1.02f;
        ubo[4] = 1.01f;
        ubo[5] = 0.03f;
        ubo[6] = 0.01f;
        ubo[7] = 0.18f;
        ubo[8] = 0.08f;
        ubo[9] = 0.55f;
        ubo[10] = 0.35f;
        ubo[11] = 0.01f;
        ubo[12] = 0.0f;
        ubo[13] = 1.0f;
        ubo[14] = 0.05f;
        ubo[15] = 0.0f;
        fillColor(ubo, 16, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 20, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 24, 0xFFFFFFFF, 0.0f);
    }

    private void fillPresetCool() {
        ubo[0] = 0.0f;
        ubo[1] = 1.0f;
        ubo[2] = 1.01f;
        ubo[3] = 1.0f;
        ubo[4] = 1.01f;
        ubo[5] = -0.04f;
        ubo[6] = 0.0f;
        ubo[7] = 0.18f;
        ubo[8] = 0.08f;
        ubo[9] = 0.55f;
        ubo[10] = 0.35f;
        ubo[11] = 0.01f;
        ubo[12] = 0.0f;
        ubo[13] = 1.0f;
        ubo[14] = 0.05f;
        ubo[15] = 0.0f;
        fillColor(ubo, 16, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 20, 0xFFFFFFFF, 0.0f);
        fillColor(ubo, 24, 0xFFFFFFFF, 0.0f);
    }
}
