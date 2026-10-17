/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;


import combatant.client.features.theme.Theme;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.render.engine.postprocess.*;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.depth.WorldSceneDepth;
import combatant.client.render.engine.pipeline.PostProcessRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.profiler.TracyGpuProfiler;
import combatant.client.render.engine.uniform.impl.DepthOfFieldUniforms;
import combatant.client.render.iris.IrisSceneDepth;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;

import java.util.LinkedHashMap;
import java.util.Map;

@ModuleInfo(id = "reimaginedvisual", displayName = "ReimaginedVisual", category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.COSMETIC,
        description = "module.reimaginedvisual.description")
public class ReimaginedVisual extends Module implements PostProcessPass, PostProcessBackendResourceOwner {

    private static final String SETTING_EFFECTS = "effects";
    private static final String EFFECT_SHADER_SKY = "shader_sky";
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
    private static final String SETTING_DOF_DEPTH_SOURCE = "dof_depth_source";
    private static final String SETTING_DOF_FAR_START = "dof_far_start";
    private static final String SETTING_DOF_FAR_TRANSITION = "dof_far_transition";
    private static final String SETTING_DOF_STRENGTH = "dof_strength";
    private static final String SETTING_DOF_MAX_RADIUS = "dof_max_radius";
    private static final String SETTING_DOF_QUALITY = "dof_quality";
    private static final String SETTING_DOF_DEBUG_COC = "dof_debug_coc";
    private static final String IRIS_SHADER_SKYBOX_REASON_KEY = "setting.reimaginedvisual.skybox_shader.iris_blocked";
    private static final String IRIS_SHADER_SKYBOX_REASON_FALLBACK = "Iris is loaded; ReimaginedVisual shader skybox is not used.";
    private static final String IRIS_DOF_REASON_KEY = "setting.reimaginedvisual.depth_of_field.iris_blocked";
    private static final String IRIS_DOF_REASON_FALLBACK = "Iris shaderpack pipeline is active.";
    private static final Map<String, Boolean> DEFAULT_EFFECTS = createDefaultEffects();
    private static final Map<String, Boolean> DEFAULT_SKYBOX_SHADER_LAYERS = createDefaultSkyboxShaderLayers();
    private final Matrix4f dofProjection = new Matrix4f();
    private TextureTarget dofFocusTarget;
    private final DepthOfFieldComputeBackend dofComputeBackend = new DepthOfFieldComputeBackend();
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
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Float> dofFarStart =
            depthOfFieldNotAppliedWithIris(visibleWhen(num("reimaginedVisualDofFarStart", SETTING_DOF_FAR_START, 4.0f, 0.0f, 512.0f),
                    this::isDepthOfFieldSettingsVisible));
    private final NumberValue<Float> dofFarTransition =
            depthOfFieldNotAppliedWithIris(visibleWhen(num("reimaginedVisualDofFarTransition", SETTING_DOF_FAR_TRANSITION, 24.0f, 1.0f, 1024.0f),
                    this::isDepthOfFieldSettingsVisible));
    private final NumberValue<Float> dofStrength =
            depthOfFieldNotAppliedWithIris(visibleWhen(num("reimaginedVisualDofStrength", SETTING_DOF_STRENGTH, 0.65f, 0.0f, 1.5f),
                    this::isDepthOfFieldSettingsVisible));
    private final NumberValue<Float> dofMaxRadius =
            depthOfFieldNotAppliedWithIris(visibleWhen(num("reimaginedVisualDofMaxRadius", SETTING_DOF_MAX_RADIUS, 8.0f, 0.0f, 32.0f),
                    this::isDepthOfFieldSettingsVisible));
    private final EnumValue<DepthOfFieldQuality> dofQuality =
            depthOfFieldNotAppliedWithIris(visibleWhen(enumSetting("reimaginedVisualDofQuality", SETTING_DOF_QUALITY, DepthOfFieldQuality.MEDIUM, DepthOfFieldQuality.values()),
                    this::isDepthOfFieldSettingsVisible));
    private final BooleanValue dofDebugCoc =
            depthOfFieldNotAppliedWithIris(visibleWhen(bool("reimaginedVisualDofDebugCoc", SETTING_DOF_DEBUG_COC, false),
                    this::isDepthOfFieldSettingsVisible));
    private boolean depthSamplerSupported = true;
    private boolean dofFocusResolveSupported = true;
    private boolean dofComputeSupported = true;

    {
        PostProcessManager.register(this);
    }

    private static float clampStarAmount(float value) {
        return Math.max(0.0f, Math.min(10.0f, value));
    }

    private static Map<String, Boolean> createDefaultEffects() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(EFFECT_SHADER_SKY, true);
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
                && !isDepthOfFieldBlockedByIris()
                && depthSamplerSupported;
    }

    public boolean needsWorldSceneDepthCapture() {
        return isActive();
    }

    public boolean needsPreTranslucentDepthCapture() {
        return false;
    }

    public boolean needsResolvedMainDepthCapture() {
        return false;
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
    public boolean prefersStorageOutput(CombatantRhi rhi) {
        return dofComputeSupported && isActive() && PostProcessExecutionPolicy.useCompute(rhi);
    }

    @Override
    public boolean render(PostProcessExecutionContext execution) {
        if (execution == null) return false;
        return renderDepthOfField(
                execution.context(), execution.source(), execution.destination(),
                execution.rhi(), execution.destinationStorage());
    }

    @Override
    public boolean render(PostProcessContext context, GpuTextureView src, GpuTextureView dst) {
        return renderDepthOfField(context, src, dst, CombatantRenderSystem.rhi(), null);
    }

    private boolean renderDepthOfField(PostProcessContext context,
                                       GpuTextureView src,
                                       GpuTextureView dst,
                                       CombatantRhi rhi,
                                       RhiStorageImage destinationStorage) {
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
        boolean focusTextureReady;

        try {
            FullScreenRenderer.ensureInit();

            if (dofComputeSupported && dofComputeBackend.supported(rhi, destinationStorage)) {
                try (TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone("3d:dof:compute")) {
                    dofComputeBackend.render(
                            rhi, destinationStorage, src,
                            depth.mainOr(src), depth.translucentOr(src), depth.itemEntityOr(src),
                            depth.particlesOr(src), depth.weatherOr(src), depth.cloudsOr(src),
                            PostProcessManager.getSampler(), dofProjection,
                            context.width(), context.height(),
                            0.0f, 0.0f, dofFarStart.get(), dofFarTransition.get(),
                            dofStrength.get(), dofMaxRadius.get(), dofQuality.get().taps(), 0.85f,
                            dofDebugCoc.get(),
                            depth.hasMain(), depth.hasTranslucent(), depth.hasItemEntity(),
                            depth.hasParticles(), depth.hasWeather(), depth.hasClouds());
                    PostProcessExecutionPolicy.logComputeActive("depth-of-field", "Depth of Field");
                    return true;
                } catch (Throwable t) {
                    dofComputeSupported = false;
                    PostProcessExecutionPolicy.warnRuntimeFallback("depth-of-field", "Depth of Field", t);
                    dofComputeBackend.close();
                }
            }

            // Raster autofocus is only needed by the compatibility path. Compute resolves the
            // same single frame-invariant focus texel with a tiny dispatch.
            focusTextureReady = depth.hasAnyDepth() && ensureDofFocusTarget();
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
                    dofDebugCoc.get(),
                    focusTextureReady,
                    depth.hasMain(),
                    depth.hasTranslucent(),
                    depth.hasItemEntity(),
                    depth.hasParticles(),
                    depth.hasWeather(),
                    depth.hasClouds()
            );
            if (focusTextureReady) {
                try (TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone("3d:dof:focus")) {
                    FullScreenRenderer.begin("Combatant DepthOfField Focus")
                            .attachment(dofFocusTarget)
                            .pipeline(PostProcessRenderPipelines.DEPTH_OF_FIELD_FOCUS)
                            .uniform("DepthOfField", DepthOfFieldUniforms.get())
                            .sampler("u_MainDepth", depth.mainOr(src), PostProcessManager.getSampler())
                            .sampler("u_TranslucentDepth", depth.translucentOr(src), PostProcessManager.getSampler())
                            .sampler("u_ItemEntityDepth", depth.itemEntityOr(src), PostProcessManager.getSampler())
                            .sampler("u_ParticlesDepth", depth.particlesOr(src), PostProcessManager.getSampler())
                            .sampler("u_WeatherDepth", depth.weatherOr(src), PostProcessManager.getSampler())
                            .sampler("u_CloudsDepth", depth.cloudsOr(src), PostProcessManager.getSampler())
                            .end();
                }
            }
            try (TracyGpuProfiler.Scope ignoredGpu = TracyGpuProfiler.beginZone("3d:dof:blur")) {
                FullScreenRenderer.begin("Combatant DepthOfField Pass")
                        .attachment(dst)
                        .pipeline(PostProcessRenderPipelines.DEPTH_OF_FIELD)
                        .uniform("DepthOfField", DepthOfFieldUniforms.get())
                        .sampler("u_Texture", src, PostProcessManager.getSampler())
                        .sampler("u_FocusTexture", focusTextureReady ? dofFocusTarget.getColorTextureView() : src,
                                PostProcessManager.getSampler())
                        .sampler("u_MainDepth", depth.mainOr(src), PostProcessManager.getSampler())
                        .sampler("u_TranslucentDepth", depth.translucentOr(src), PostProcessManager.getSampler())
                        .sampler("u_ItemEntityDepth", depth.itemEntityOr(src), PostProcessManager.getSampler())
                        .sampler("u_ParticlesDepth", depth.particlesOr(src), PostProcessManager.getSampler())
                        .sampler("u_WeatherDepth", depth.weatherOr(src), PostProcessManager.getSampler())
                        .sampler("u_CloudsDepth", depth.cloudsOr(src), PostProcessManager.getSampler())
                        .end();
            }
        } catch (Throwable t) {
            depthSamplerSupported = false;
            DebugLog.warnOnce(
                    "depth-of-field-depth-sampler-fallback",
                    "Depth of Field depth sampler path failed; disabling depth sampling for this session",
                    t);
            return false;
        }

        return true;
    }

    private boolean ensureDofFocusTarget() {
        if (!dofFocusResolveSupported) return false;
        try {
            if (dofFocusTarget == null) {
                dofFocusTarget = new TextureTarget("combatant-depth-of-field-focus", 1, 1, GpuFormat.RGBA8_UNORM, null);
            }
            return dofFocusTarget.getColorTextureView() != null;
        } catch (Throwable t) {
            dofFocusResolveSupported = false;
            closeDofFocusTarget();
            DebugLog.warnOnce(
                    "depth-of-field-focus-fragment-fallback",
                    "Depth of Field focus resolve failed; using fragment fallback for this session",
                    t);
            return false;
        }
    }

    private void closeDofFocusTarget() {
        if (dofFocusTarget == null) return;
        dofFocusTarget.destroyBuffers();
        dofFocusTarget = null;
    }

    private void buildProjection() {
        Matrix4f projection = CombatantWorldMatrices.renderProjectionMatrix();
        if (projection == null) {
            projection = RenderState.worldProjection;
        }
        dofProjection.set(projection);
    }

    @Override
    public void onEnable() {
        depthSamplerSupported = true;
        dofFocusResolveSupported = true;
        dofComputeSupported = true;
    }

    @Override
    public void onDisable() {
        closeDofFocusTarget();
        dofComputeBackend.close();
    }

    @Override
    public void releaseBackendResources(CombatantRhi owner) {
        dofComputeBackend.release(owner);
        closeDofFocusTarget();
        dofComputeSupported = true;
        dofFocusResolveSupported = true;
    }

    private DepthBindings resolveDepthBindings(PostProcessContext context) {
        return resolveWorldSceneDepth();
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

    public static boolean isDepthOfFieldRequestedStatic() {
        ReimaginedVisual module = module();
        return module != null && module.isEnabled() && module.isEffectSelected(EFFECT_DEPTH_OF_FIELD);
    }

    public static boolean isDepthOfFieldActiveStatic() {
        ReimaginedVisual module = module();
        return module != null && module.isActive();
    }

    private boolean isDepthOfFieldBlockedByIris() {
        return IrisRuntime.isShaderpackRendererActive()
                && !combatant.client.render.iris.IrisCompatibilityGuards.supportsCombatantDepthOfField();
    }

    private String depthOfFieldIrisReason() {
        String translated = I18n.get(IRIS_DOF_REASON_KEY);
        return IRIS_DOF_REASON_KEY.equals(translated) ? IRIS_DOF_REASON_FALLBACK : translated;
    }

    private <V extends ConfigValue<?>> V depthOfFieldNotAppliedWithIris(V value) {
        return notAppliedWhen(value, this::isDepthOfFieldBlockedByIris, this::depthOfFieldIrisReason);
    }

    private boolean isDepthOfFieldSettingsVisible() {
        return isEffectSelected(EFFECT_DEPTH_OF_FIELD);
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
