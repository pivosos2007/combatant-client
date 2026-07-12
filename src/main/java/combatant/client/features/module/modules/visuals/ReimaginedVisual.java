/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;


import combatant.client.features.theme.Theme;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.render.engine.postprocess.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.DepthOfFieldUniforms;
import combatant.client.render.helpers.SodiumMaterialFlags;
import combatant.client.render.iris.IrisSceneDepth;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;

import java.util.LinkedHashMap;
import java.util.Map;

//todo Description
@ModuleInfo(id = "reimaginedvisual", displayName = "ReimaginedVisual", category = ModuleCategory.VISUALS)
public class ReimaginedVisual extends Module implements PostProcessPass {

    private static final String SETTING_EFFECTS = "effects";
    private static final String EFFECT_SHADER_SKY = "shader_sky";
    private static final String EFFECT_WORLD_SUN = "world_sun";
    private static final String EFFECT_WAVY_VEGETATION = "wavy_vegetation";
    private static final String EFFECT_DEPTH_OF_FIELD = "depth_of_field";
    private static final String SETTING_SKYBOX_SHADER_SYNC_THEME = "skybox_shader_sync_theme";
    private static final String SETTING_SKYBOX_SHADER_COLOR = "skybox_shader_color";
    private static final String SETTING_SKYBOX_SHADER_MOTION_SPEED = "skybox_shader_motion_speed";
    private static final String SETTING_SKYBOX_SHADER_AURORA_ENABLED = "skybox_shader_aurora_enabled";
    private static final String SETTING_SKYBOX_SHADER_AURORA_INTENSITY = "skybox_shader_aurora_intensity";
    private static final String SETTING_SKYBOX_SHADER_AURORA_SPEED = "skybox_shader_aurora_speed";
    private static final String SETTING_SKYBOX_SHADER_SMALL_STARS = "skybox_shader_small_stars";
    private static final String SETTING_SKYBOX_SHADER_DUST_STARS = "skybox_shader_dust_stars";
    private static final String SETTING_SKYBOX_SHADER_MEDIUM_STARS = "skybox_shader_medium_stars";
    private static final String SETTING_SKYBOX_SHADER_LARGE_STARS = "skybox_shader_large_stars";
    private static final String SETTING_SKYBOX_SHADER_STAR_BRIGHTNESS = "skybox_shader_star_brightness";
    private static final String SETTING_SKYBOX_SHADER_TWINKLE_STRENGTH = "skybox_shader_twinkle_strength";
    private static final String SETTING_SKYBOX_SHADER_LAYERS = "skybox_shader_layers";
    private static final String SETTING_SKYBOX_SKY_FOG_BLEND = "skybox_sky_fog_blend";
    private static final String SETTING_WORLD_SUN_GLARE_ENABLED = "world_sun_glare_enabled";
    private static final String SETTING_WORLD_SUN_SIZE = "world_sun_size";
    private static final String SETTING_WORLD_SUN_GLOW = "world_sun_glow";
    private static final String SETTING_WORLD_SUN_INTENSITY = "world_sun_intensity";
    private static final String SETTING_WAVY_VEGETATION_ROOTED_HORIZONTAL_AMPLITUDE = "wavy_vegetation_rooted_horizontal_amplitude";
    private static final String SETTING_WAVY_VEGETATION_ROOTED_VERTICAL_AMPLITUDE = "wavy_vegetation_rooted_vertical_amplitude";
    private static final String SETTING_WAVY_VEGETATION_FREE_HORIZONTAL_AMPLITUDE = "wavy_vegetation_free_horizontal_amplitude";
    private static final String SETTING_WAVY_VEGETATION_FREE_VERTICAL_AMPLITUDE = "wavy_vegetation_free_vertical_amplitude";
    private static final String SETTING_WAVY_VEGETATION_SPEED = "wavy_vegetation_speed";
    private static final String SETTING_DOF_DEPTH_SOURCE = "dof_depth_source";
    private static final String SETTING_DOF_FAR_START = "dof_far_start";
    private static final String SETTING_DOF_FAR_TRANSITION = "dof_far_transition";
    private static final String SETTING_DOF_STRENGTH = "dof_strength";
    private static final String SETTING_DOF_MAX_RADIUS = "dof_max_radius";
    private static final String SETTING_DOF_QUALITY = "dof_quality";
    private static final String SETTING_DOF_DEBUG_COC = "dof_debug_coc";
    private static final String IRIS_SHADER_SKYBOX_REASON_KEY = "setting.reimaginedvisual.skybox_shader.iris_blocked";
    private static final String IRIS_SHADER_SKYBOX_REASON_FALLBACK = "Iris is loaded; ReimaginedVisual shader skybox is not used.";
    private static final Map<String, Boolean> DEFAULT_EFFECTS = createDefaultEffects();
    private static final Map<String, Boolean> DEFAULT_SKYBOX_SHADER_LAYERS = createDefaultSkyboxShaderLayers();
    private final Matrix4f dofProjection = new Matrix4f();
    private final BooleanMapValue effects = group(
            "reimaginedVisualEffects",
            SETTING_EFFECTS,
            DEFAULT_EFFECTS
    );
    private final BooleanValue skyboxShaderSyncTheme =
            shaderSkyboxNotAppliedWithIris(visibleWhen(bool("reimaginedVisualSkyboxShaderSyncTheme", SETTING_SKYBOX_SHADER_SYNC_THEME, true),
                    this::isShaderSkyboxSettingsVisible));
    private final RGBColorValue skyboxShaderColor =
            shaderSkyboxNotAppliedWithIris(visibleWhen(colorNoAlpha("reimaginedVisualSkyboxShaderColor", SETTING_SKYBOX_SHADER_COLOR, "#78A7FF"),
                    () -> isShaderSkyboxSettingsVisible() && !skyboxShaderSyncTheme.get()));
    private final NumberValue<Float> skyboxShaderMotionSpeed =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderMotionSpeed", SETTING_SKYBOX_SHADER_MOTION_SPEED, 0.45f, 0.0f, 2.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final BooleanValue skyboxShaderAuroraEnabled =
            shaderSkyboxNotAppliedWithIris(visibleWhen(bool("reimaginedVisualSkyboxShaderAuroraEnabled", SETTING_SKYBOX_SHADER_AURORA_ENABLED, false),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderAuroraIntensity =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderAuroraIntensity", SETTING_SKYBOX_SHADER_AURORA_INTENSITY, 0.70f, 0.0f, 1.5f),
                    () -> isShaderSkyboxSettingsVisible() && skyboxShaderAuroraEnabled.get()));
    private final NumberValue<Float> skyboxShaderAuroraSpeed =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderAuroraSpeed", SETTING_SKYBOX_SHADER_AURORA_SPEED, 0.38f, 0.0f, 2.0f),
                    () -> isShaderSkyboxSettingsVisible() && skyboxShaderAuroraEnabled.get()));
    private final NumberValue<Float> skyboxShaderSmallStars =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderSmallStars", SETTING_SKYBOX_SHADER_SMALL_STARS, 1.60f, 0.0f, 10.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderDustStars =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderDustStars", SETTING_SKYBOX_SHADER_DUST_STARS, 1.40f, 0.0f, 10.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderMediumStars =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderMediumStars", SETTING_SKYBOX_SHADER_MEDIUM_STARS, 1.30f, 0.0f, 10.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderLargeStars =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderLargeStars", SETTING_SKYBOX_SHADER_LARGE_STARS, 1.15f, 0.0f, 10.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderStarBrightness =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderStarBrightness", SETTING_SKYBOX_SHADER_STAR_BRIGHTNESS, 1.35f, 0.0f, 3.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxShaderTwinkleStrength =
            shaderSkyboxNotAppliedWithIris(visibleWhen(num("reimaginedVisualSkyboxShaderTwinkleStrength", SETTING_SKYBOX_SHADER_TWINKLE_STRENGTH, 1.35f, 0.0f, 3.0f),
                    this::isShaderSkyboxSettingsVisible));
    private final BooleanMapValue skyboxShaderLayers =
            shaderSkyboxNotAppliedWithIris(visibleWhen(group("reimaginedVisualSkyboxShaderLayers", SETTING_SKYBOX_SHADER_LAYERS, DEFAULT_SKYBOX_SHADER_LAYERS),
                    this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> skyboxSkyFogBlend =
            shaderSkyboxNotAppliedWithIris(visibleWhen(
                    num("reimaginedVisualSkyboxSkyFogBlend", SETTING_SKYBOX_SKY_FOG_BLEND, 0.35f, 0.0f, 1.0f),
                    this::isShaderSkyboxSettingsVisible
            ));
    private final BooleanValue worldSunGlareEnabled =
            visibleWhen(bool("reimaginedVisualWorldSunGlareEnabled", SETTING_WORLD_SUN_GLARE_ENABLED, true),
                    this::isWorldSunSettingsVisible);
    private final NumberValue<Float> worldSunSize =
            visibleWhen(num("reimaginedVisualWorldSunSize", SETTING_WORLD_SUN_SIZE, 0.06f, 0.01f, 0.20f),
                    this::isWorldSunSettingsVisible);
    private final NumberValue<Float> worldSunGlow =
            visibleWhen(num("reimaginedVisualWorldSunGlow", SETTING_WORLD_SUN_GLOW, 0.18f, 0.02f, 0.40f),
                    this::isWorldSunSettingsVisible);
    private final NumberValue<Float> worldSunIntensity =
            visibleWhen(num("reimaginedVisualWorldSunIntensity", SETTING_WORLD_SUN_INTENSITY, 1.0f, 0.0f, 3.0f),
                    this::isWorldSunSettingsVisible);
    private final NumberValue<Float> wavyVegetationRootedHorizontalAmplitude =
            visibleWhen(num("reimaginedVisualWavyVegetationRootedHorizontalAmplitude", SETTING_WAVY_VEGETATION_ROOTED_HORIZONTAL_AMPLITUDE, 1.0f, 0.0f, 3.0f), this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationRootedVerticalAmplitude =
            visibleWhen(num("reimaginedVisualWavyVegetationRootedVerticalAmplitude", SETTING_WAVY_VEGETATION_ROOTED_VERTICAL_AMPLITUDE, 1.0f, 0.0f, 3.0f), this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationFreeHorizontalAmplitude =
            visibleWhen(num("reimaginedVisualWavyVegetationFreeHorizontalAmplitude", SETTING_WAVY_VEGETATION_FREE_HORIZONTAL_AMPLITUDE, 1.0f, 0.0f, 3.0f), this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationFreeVerticalAmplitude =
            visibleWhen(num("reimaginedVisualWavyVegetationFreeVerticalAmplitude", SETTING_WAVY_VEGETATION_FREE_VERTICAL_AMPLITUDE, 1.0f, 0.0f, 3.0f), this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationSpeed =
            visibleWhen(num("reimaginedVisualWavyVegetationSpeed", SETTING_WAVY_VEGETATION_SPEED, 1.0f, 0.0f, 3.0f), this::isWavyVegetationSettingsVisible);
    private final Minecraft mc = Minecraft.getInstance();
    private boolean wavyVegetationStateInitialized;
    private boolean lastWavyVegetationActive;
    private int lastWavyVegetationSettingsPacked;
    private final EnumValue<DepthOfFieldDepthSource> dofDepthSource =
            visibleWhen(enumSetting("reimaginedVisualDofDepthSource", SETTING_DOF_DEPTH_SOURCE, DepthOfFieldDepthSource.WORLD_SCENE, DepthOfFieldDepthSource.values()),
                    this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofFarStart =
            visibleWhen(num("reimaginedVisualDofFarStart", SETTING_DOF_FAR_START, 4.0f, 0.0f, 512.0f),
                    this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofFarTransition =
            visibleWhen(num("reimaginedVisualDofFarTransition", SETTING_DOF_FAR_TRANSITION, 24.0f, 1.0f, 1024.0f),
                    this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofStrength =
            visibleWhen(num("reimaginedVisualDofStrength", SETTING_DOF_STRENGTH, 0.65f, 0.0f, 1.5f),
                    this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofMaxRadius =
            visibleWhen(num("reimaginedVisualDofMaxRadius", SETTING_DOF_MAX_RADIUS, 8.0f, 0.0f, 32.0f),
                    this::isDepthOfFieldSettingsVisible);
    private final EnumValue<DepthOfFieldQuality> dofQuality =
            visibleWhen(enumSetting("reimaginedVisualDofQuality", SETTING_DOF_QUALITY, DepthOfFieldQuality.MEDIUM, DepthOfFieldQuality.values()),
                    this::isDepthOfFieldSettingsVisible);
    private final BooleanValue dofDebugCoc =
            visibleWhen(bool("reimaginedVisualDofDebugCoc", SETTING_DOF_DEBUG_COC, false),
                    this::isDepthOfFieldSettingsVisible);
    private boolean depthSamplerSupported = true;

    {
        PostProcessManager.register(this);
    }

    private static float clampStarAmount(float value) {
        return Math.max(0.0f, Math.min(10.0f, value));
    }

    private static Map<String, Boolean> createDefaultEffects() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(EFFECT_SHADER_SKY, true);
        defaults.put(EFFECT_WORLD_SUN, true);
        defaults.put(EFFECT_WAVY_VEGETATION, false);
        defaults.put(EFFECT_DEPTH_OF_FIELD, false);
        return defaults;
    }

    private static Map<String, Boolean> createDefaultSkyboxShaderLayers() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put("waves", true);
        defaults.put("ribbons", true);
        defaults.put("sweeps", true);
        defaults.put("veil", true);
        defaults.put("nebula", true);
        defaults.put("detail_curtains", true);
        defaults.put("polar_arc", true);
        defaults.put("bursts", true);
        defaults.put("water_veil", true);
        defaults.put("caustics", true);
        defaults.put("refracted_aurora", true);
        defaults.put("northern_aurora", true);
        defaults.put("small_stars", true);
        defaults.put("dust_stars", true);
        defaults.put("medium_stars", true);
        defaults.put("large_stars", true);
        defaults.put("detail_stars", true);
        return defaults;
    }

    public static boolean isWorldSunEnabledStatic() {
        ReimaginedVisual module = module();
        return module != null && module.isEnabled() && module.isEffectSelected(EFFECT_WORLD_SUN);
    }

    public static float getWorldSunSizeStatic() {
        ReimaginedVisual module = module();
        return module != null ? module.worldSunSize.get() : 0.06f;
    }

    public static float getWorldSunGlowStatic() {
        ReimaginedVisual module = module();
        return module != null ? module.worldSunGlow.get() : 0.18f;
    }

    public static float getWorldSunIntensityStatic() {
        ReimaginedVisual module = module();
        return module != null ? module.worldSunIntensity.get() : 1.0f;
    }

    public static boolean isWorldSunGlareEnabledStatic() {
        ReimaginedVisual module = module();
        return module != null
                && module.isEnabled()
                && module.isEffectSelected(EFFECT_WORLD_SUN)
                && module.worldSunGlareEnabled.get();
    }

    public static boolean isWavyVegetationEnabledStatic() {
        ReimaginedVisual module = module();
        return module != null
                && module.isEnabled()
                && module.isEffectSelected(EFFECT_WAVY_VEGETATION);
    }

    public static int packWavyVegetationSettingsStatic() {
        ReimaginedVisual module = module();
        return module != null ? module.packWavyVegetationSettings() : packWavyVegetationSettings(1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static ReimaginedVisual module() {
        return Modules.get(ReimaginedVisual.class);
    }

    public boolean isShaderSkyboxEnabled() {
        return isEnabled() && isEffectSelected(EFFECT_SHADER_SKY);
    }

    public int getShaderSkyboxColor() {
        if (skyboxShaderSyncTheme.get()) {
            return Theme.theme().accent() & 0x00FFFFFF;
        }
        return skyboxShaderColor.getArgb() & 0x00FFFFFF;
    }

    public float getSkyboxSkyFogBlend() {
        return Math.max(0.0f, Math.min(1.0f, skyboxSkyFogBlend.get()));
    }

    public float getSkyboxShaderMotionSpeed() {
        return Math.max(0.0f, Math.min(2.0f, skyboxShaderMotionSpeed.get()));
    }

    public boolean isSkyboxShaderAuroraEnabled() {
        return skyboxShaderAuroraEnabled.get();
    }

    public float getSkyboxShaderAuroraIntensity() {
        return skyboxShaderAuroraEnabled.get() ? Math.max(0.0f, Math.min(1.5f, skyboxShaderAuroraIntensity.get())) : 0.0f;
    }

    public float getSkyboxShaderAuroraSpeed() {
        return Math.max(0.0f, Math.min(2.0f, skyboxShaderAuroraSpeed.get()));
    }

    public float getSkyboxShaderSmallStars() {
        return clampStarAmount(skyboxShaderSmallStars.get());
    }

    public float getSkyboxShaderDustStars() {
        return clampStarAmount(skyboxShaderDustStars.get());
    }

    public float getSkyboxShaderMediumStars() {
        return clampStarAmount(skyboxShaderMediumStars.get());
    }

    public float getSkyboxShaderLargeStars() {
        return clampStarAmount(skyboxShaderLargeStars.get());
    }

    public float getSkyboxShaderStarBrightness() {
        return Math.max(0.0f, Math.min(3.0f, skyboxShaderStarBrightness.get()));
    }

    public float getSkyboxShaderTwinkleStrength() {
        return Math.max(0.0f, Math.min(3.0f, skyboxShaderTwinkleStrength.get()));
    }

    public int getSkyboxShaderLayerMask() {
        int mask = 0;
        int bit = 0;
        for (String key : DEFAULT_SKYBOX_SHADER_LAYERS.keySet()) {
            if (skyboxShaderLayers.get(key)) {
                mask |= 1 << bit;
            }
            bit++;
        }
        return mask;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public boolean isActive() {
        return isEnabled()
                && isEffectSelected(EFFECT_DEPTH_OF_FIELD)
                && mc != null
                && mc.player != null
                && mc.level != null
                && !IrisRuntime.isShaderpackRendererActive()
                && depthSamplerSupported
                && dofDepthSource.get() != DepthOfFieldDepthSource.OFF;
    }

    public boolean needsWorldSceneDepthCapture() {
        return isActive() && dofDepthSource.get() == DepthOfFieldDepthSource.WORLD_SCENE;
    }

    public boolean needsPreTranslucentDepthCapture() {
        return isActive() && dofDepthSource.get() == DepthOfFieldDepthSource.PRE_TRANSLUCENT;
    }

    public boolean needsResolvedMainDepthCapture() {
        if (!isActive()) return false;
        DepthOfFieldDepthSource source = dofDepthSource.get();
        return source == DepthOfFieldDepthSource.MAIN;
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public Phase getPhase() {
        return Phase.PRE_HAND;
    }

    @Override
    public boolean render(GpuTextureView src, GpuTextureView dst, float tickDelta) {
        return false;
    }

    @Override
    public boolean render(PostProcessContext context, GpuTextureView src, GpuTextureView dst) {
        if (!isActive() || context == null || src == null || dst == null) {
            return false;
        }

        DepthBindings depth = resolveDepthBindings(context);
        if (!depth.hasAnyDepth()) {
            if (!dofDebugCoc.get()) {
                return false;
            }
            depth = DepthBindings.empty();
        }

        buildProjection();

        DepthOfFieldUniforms.update(
                dofProjection,
                context.width(),
                context.height(),
                0.0f,
                0.0f,
                dofFarStart.get(),
                dofFarTransition.get(),
                dofStrength.get(),
                dofMaxRadius.get(),
                dofQuality.get().taps(),
                0.85f,
                dofMsaaResolveFactor(),
                dofDebugCoc.get(),
                depth.hasMain(),
                depth.hasTranslucent(),
                depth.hasItemEntity(),
                depth.hasParticles(),
                depth.hasWeather(),
                depth.hasClouds()
        );

        try {
            FullScreenRenderer.ensureInit();
            FullScreenRenderer.begin("Combatant DepthOfField Pass")
                    .attachment(dst)
                    .pipeline(CombatantRenderPipelines.DEPTH_OF_FIELD)
                    .uniform("DepthOfField", DepthOfFieldUniforms.get())
                    .sampler("u_Texture", src, PostProcessManager.getSampler())
                    .sampler("u_MainDepth", depth.mainOr(src), PostProcessManager.getSampler())
                    .sampler("u_TranslucentDepth", depth.translucentOr(src), PostProcessManager.getSampler())
                    .sampler("u_ItemEntityDepth", depth.itemEntityOr(src), PostProcessManager.getSampler())
                    .sampler("u_ParticlesDepth", depth.particlesOr(src), PostProcessManager.getSampler())
                    .sampler("u_WeatherDepth", depth.weatherOr(src), PostProcessManager.getSampler())
                    .sampler("u_CloudsDepth", depth.cloudsOr(src), PostProcessManager.getSampler())
                    .end();
        } catch (Throwable t) {
            depthSamplerSupported = false;
            DebugLog.warn("[Combatant] DepthOfField depth sampler path failed; disabling DoF depth sampling for this session: " + t);
            return false;
        }

        return true;
    }

    private void buildProjection() {
        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (projection == null) {
            projection = RenderState.worldProjection;
        }
        Matrix4f view = CombatantWorldMatrices.positionMatrix();
        if (view != null) {
            projection.mul(view, dofProjection);
        } else {
            dofProjection.set(projection);
        }
        dofProjection.invert();
    }

    @Override
    public void onTick() {
        refreshWavyVegetationTerrainState();
    }

    @Override
    public void onEnable() {
        depthSamplerSupported = true;
        refreshWavyVegetationTerrainState();
    }

    @Override
    public void onDisable() {
        refreshWavyVegetationTerrainState(false);
    }

    private void refreshWavyVegetationTerrainState() {
        refreshWavyVegetationTerrainState(isEnabled() && isEffectSelected(EFFECT_WAVY_VEGETATION));
    }

    private void refreshWavyVegetationTerrainState(boolean active) {
        int settingsPacked = active ? packWavyVegetationSettings() : 0;
        if (!wavyVegetationStateInitialized) {
            wavyVegetationStateInitialized = true;
            lastWavyVegetationActive = false;
            lastWavyVegetationSettingsPacked = 0;
        }

        if (lastWavyVegetationActive == active && lastWavyVegetationSettingsPacked == settingsPacked) return;
        lastWavyVegetationActive = active;
        lastWavyVegetationSettingsPacked = settingsPacked;
        CombatantRenderSystem.sodium().reloadWorldRenderer();
    }

    private int packWavyVegetationSettings() {
        return packWavyVegetationSettings(
                wavyVegetationRootedHorizontalAmplitude.get(),
                wavyVegetationRootedVerticalAmplitude.get(),
                wavyVegetationFreeHorizontalAmplitude.get(),
                wavyVegetationFreeVerticalAmplitude.get(),
                wavyVegetationSpeed.get()
        );
    }

    private static int packWavyVegetationSettings(float rootedHorizontal, float rootedVertical, float freeHorizontal, float freeVertical, float speed) {
        return (encodeWavyVegetationSetting(rootedHorizontal) << SodiumMaterialFlags.WAVE_ROOTED_HORIZONTAL_SHIFT)
                | (encodeWavyVegetationSetting(rootedVertical) << SodiumMaterialFlags.WAVE_ROOTED_VERTICAL_SHIFT)
                | (encodeWavyVegetationSetting(freeHorizontal) << SodiumMaterialFlags.WAVE_FREE_HORIZONTAL_SHIFT)
                | (encodeWavyVegetationSetting(freeVertical) << SodiumMaterialFlags.WAVE_FREE_VERTICAL_SHIFT)
                | (encodeWavyVegetationSetting(speed) << SodiumMaterialFlags.WAVE_SPEED_SHIFT);
    }

    private static int encodeWavyVegetationSetting(float value) {
        float clamped = Math.max(0.0f, Math.min(SodiumMaterialFlags.WAVE_SETTING_MAX, value));
        return Math.round((clamped / SodiumMaterialFlags.WAVE_SETTING_MAX) * SodiumMaterialFlags.WAVE_SETTING_MASK);
    }

    private DepthBindings resolveDepthBindings(PostProcessContext context) {
        return switch (dofDepthSource.get()) {
            case WORLD_SCENE -> resolveWorldSceneDepth();
            case MAIN -> DepthBindings.single(context.mainDepth());
            case PRE_TRANSLUCENT -> DepthBindings.single(context.preTranslucentDepth());
            case OFF -> DepthBindings.empty();
        };
    }

    private static float dofMsaaResolveFactor() {
        int samples = MsaaWorldTarget.getSamples();
        return samples > 1 ? Math.min(1.0f, 1.0f - (1.0f / samples)) : 0.0f;
    }

    private DepthBindings resolveWorldSceneDepth() {
        if (IrisSceneDepth.isValid()) {
            return new DepthBindings(
                    IrisSceneDepth.mainDepthView(),
                    IrisSceneDepth.preTranslucentDepthView(),
                    IrisSceneDepth.preHandDepthView(),
                    null,
                    null,
                    null
            );
        }
        if (!WorldSceneDepth.isValid() || !WorldSceneDepth.hasMain()) {
            return DepthBindings.empty();
        }
        return new DepthBindings(
                WorldSceneDepth.mainDepthView(),
                WorldSceneDepth.translucentDepthView(),
                WorldSceneDepth.itemEntityDepthView(),
                WorldSceneDepth.particlesDepthView(),
                WorldSceneDepth.weatherDepthView(),
                null
        );
    }

    private boolean isWorldSunSettingsVisible() {
        return isEffectSelected(EFFECT_WORLD_SUN);
    }

    private boolean isShaderSkyboxSettingsVisible() {
        return isEffectSelected(EFFECT_SHADER_SKY);
    }

    private boolean isShaderSkyboxBlockedByIris() {
        return isEffectSelected(EFFECT_SHADER_SKY) && IrisRuntime.isModLoaded();
    }

    private String shaderSkyboxIrisReason() {
        String translated = I18n.get(IRIS_SHADER_SKYBOX_REASON_KEY);
        return IRIS_SHADER_SKYBOX_REASON_KEY.equals(translated) ? IRIS_SHADER_SKYBOX_REASON_FALLBACK : translated;
    }

    private <V extends ConfigValue<?>> V shaderSkyboxNotAppliedWithIris(V value) {
        return notAppliedWhen(value, this::isShaderSkyboxBlockedByIris, this::shaderSkyboxIrisReason);
    }

    private boolean isDepthOfFieldSettingsVisible() {
        return isEffectSelected(EFFECT_DEPTH_OF_FIELD);
    }

    private boolean isWavyVegetationSettingsVisible() {
        return isEffectSelected(EFFECT_WAVY_VEGETATION);
    }

    private boolean isEffectSelected(String effect) {
        return effects.get(effect);
    }

    private record DepthBindings(
            GpuTextureView main,
            GpuTextureView translucent,
            GpuTextureView itemEntity,
            GpuTextureView particles,
            GpuTextureView weather,
            GpuTextureView clouds
    ) {
        static DepthBindings empty() {
            return new DepthBindings(null, null, null, null, null, null);
        }

        static DepthBindings single(GpuTextureView depth) {
            return new DepthBindings(depth, null, null, null, null, null);
        }

        boolean hasAnyDepth() {
            return primary() != null;
        }

        boolean hasMain() {
            return main != null;
        }

        boolean hasTranslucent() {
            return translucent != null;
        }

        boolean hasItemEntity() {
            return itemEntity != null;
        }

        boolean hasParticles() {
            return particles != null;
        }

        boolean hasWeather() {
            return weather != null;
        }

        boolean hasClouds() {
            return clouds != null;
        }

        GpuTextureView primary() {
            if (main != null) return main;
            if (translucent != null) return translucent;
            if (itemEntity != null) return itemEntity;
            if (particles != null) return particles;
            if (weather != null) return weather;
            return clouds;
        }

        GpuTextureView mainOr(GpuTextureView placeholder) {
            return main != null ? main : firstOr(placeholder);
        }

        GpuTextureView translucentOr(GpuTextureView placeholder) {
            return translucent != null ? translucent : firstOr(placeholder);
        }

        GpuTextureView itemEntityOr(GpuTextureView placeholder) {
            return itemEntity != null ? itemEntity : firstOr(placeholder);
        }

        GpuTextureView particlesOr(GpuTextureView placeholder) {
            return particles != null ? particles : firstOr(placeholder);
        }

        GpuTextureView weatherOr(GpuTextureView placeholder) {
            return weather != null ? weather : firstOr(placeholder);
        }

        GpuTextureView cloudsOr(GpuTextureView placeholder) {
            return clouds != null ? clouds : firstOr(placeholder);
        }

        private GpuTextureView firstOr(GpuTextureView placeholder) {
            GpuTextureView primary = primary();
            return primary != null ? primary : placeholder;
        }
    }
}
