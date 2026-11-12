/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;


import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import combatant.client.config.values.*;
import combatant.client.features.module.*;
import combatant.client.features.module.Module;
import combatant.client.render.engine.postprocess.*;
import combatant.client.render.effects.weather.ProceduralRainRenderer;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.sky.DreamFroxelCloudRenderer;
import combatant.client.render.sky.DreamSkyPalette;
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

    static {
        combatant.client.render.engine.hdr.HdrScene.registerConsumer("reimagined-sky", () -> {
            ReimaginedVisual visual = Modules.get(ReimaginedVisual.class);
            return visual != null && !IrisRuntime.isModLoaded() && visual.isShaderSkyboxEnabled();
        });
    }

    private static final String SETTING_EFFECTS = "effects";
    private static final String EFFECT_SHADER_SKY = "shader_sky";
    private static final String EFFECT_DEPTH_OF_FIELD = "depth_of_field";
    private static final String EFFECT_PROCEDURAL_RAIN = "procedural_rain";
    private static final String SETTING_RAIN_DENSITY = "procedural_rain_density";
    private static final String SETTING_RAIN_DISTANCE = "procedural_rain_distance";
    private static final String SETTING_DREAM_CLOUDS = "dream_clouds";
    private static final String SETTING_DREAM_CLOUD_HEIGHT = "dream_cloud_height";
    private static final String SETTING_DREAM_CLOUD_THICKNESS = "dream_cloud_thickness";
    private static final String SETTING_DREAM_CLOUD_COVERAGE = "dream_cloud_coverage";
    private static final String SETTING_DREAM_CLOUD_SCALE = "dream_cloud_scale";
    private static final String SETTING_DREAM_CLOUD_DENSITY = "dream_cloud_density";
    private static final String SETTING_DREAM_CLOUD_QUALITY = "dream_cloud_quality";
    private static final String SETTING_DREAM_FAR_CLOUDS = "dream_far_clouds";
    private static final String SETTING_DREAM_CIRRUS = "dream_cirrus";
    private static final String SETTING_DREAM_SKY_PALETTE = "dream_sky_palette";
    private static final String SETTING_DREAM_AURORA = "dream_aurora";
    private static final String SETTING_DREAM_AURORA_INTENSITY = "dream_aurora_intensity";
    private static final String SETTING_DREAM_AURORA_SPEED = "dream_aurora_speed";

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
    private final Matrix4f dofProjection = new Matrix4f();
    private TextureTarget dofFocusTarget;
    private final DepthOfFieldComputeBackend dofComputeBackend = new DepthOfFieldComputeBackend();
    private final BooleanMapValue effects = group(
            "reimaginedVisualEffects",
            SETTING_EFFECTS,
            DEFAULT_EFFECTS
    );
    public enum DreamCloudQuality {
        // Froxels cache illumination, not silhouette detail. The lower-resolution
        // raymarch is reconstructed temporally into the full-size image.
        LOW(24, 4, 24), MEDIUM(20, 3, 28), HIGH(16, 2, 36), ULTRA(12, 2, 48);

        private final int pixelStride;
        private final int integrationStride;
        private final int slices;

        DreamCloudQuality(int pixelStride, int integrationStride, int slices) {
            this.pixelStride = pixelStride;
            this.integrationStride = integrationStride;
            this.slices = slices;
        }

        public int pixelStride() { return pixelStride; }
        public int integrationStride() { return integrationStride; }
        public int slices() { return slices; }
    }

    private final BooleanValue dreamClouds = shaderSkyboxNotAppliedWithIris(visibleWhen(
            bool("reimaginedVisualDreamClouds", SETTING_DREAM_CLOUDS, true), this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> dreamCloudHeight = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedVisualDreamCloudHeight", SETTING_DREAM_CLOUD_HEIGHT, 192.0f, -32.0f, 512.0f), this::areDreamCloudSettingsVisible));
    private final NumberValue<Float> dreamCloudThickness = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedVisualDreamCloudThickness", SETTING_DREAM_CLOUD_THICKNESS, 125.0f, 16.0f, 320.0f), this::areDreamCloudSettingsVisible));
    private final NumberValue<Float> dreamCloudCoverage = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedVisualDreamCloudCoverage", SETTING_DREAM_CLOUD_COVERAGE, 0.53f, 0.0f, 1.0f), this::areDreamCloudSettingsVisible));
    private final NumberValue<Float> dreamCloudScale = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedVisualDreamCloudScale", SETTING_DREAM_CLOUD_SCALE, 1.4f, 0.4f, 4.0f), this::areDreamCloudSettingsVisible));
    private final NumberValue<Float> dreamCloudDensity = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedVisualDreamCloudDensity", SETTING_DREAM_CLOUD_DENSITY, 0.75f, 0.05f, 2.0f), this::areDreamCloudSettingsVisible));
    private final EnumValue<DreamCloudQuality> dreamCloudQuality = shaderSkyboxNotAppliedWithIris(visibleWhen(
            enumSetting("reimaginedVisualDreamCloudQuality", SETTING_DREAM_CLOUD_QUALITY,
                    DreamCloudQuality.MEDIUM, DreamCloudQuality.values()), this::areDreamCloudSettingsVisible));
    private final BooleanValue dreamFarClouds = shaderSkyboxNotAppliedWithIris(visibleWhen(
            bool("reimaginedVisualDreamFarClouds", SETTING_DREAM_FAR_CLOUDS, true), this::areDreamCloudSettingsVisible));
    private final BooleanValue dreamCirrus = shaderSkyboxNotAppliedWithIris(visibleWhen(
            bool("reimaginedVisualDreamCirrus", SETTING_DREAM_CIRRUS, true), this::areDreamCloudSettingsVisible));
    public enum SkyPaletteMode { THEME, WORLD_TWEAKS, CUSTOM }

    private final EnumValue<SkyPaletteMode> dreamSkyPaletteMode = shaderSkyboxNotAppliedWithIris(visibleWhen(
            enumSetting("reimaginedVisualDreamSkyPalette", SETTING_DREAM_SKY_PALETTE,
                    SkyPaletteMode.WORLD_TWEAKS, SkyPaletteMode.values()), this::isShaderSkyboxSettingsVisible));
    private boolean areCustomSkyColorsVisible() {
        return isShaderSkyboxSettingsVisible() && dreamSkyPaletteMode.get() == SkyPaletteMode.CUSTOM;
    }
    private final RGBColorValue dreamDayZenith = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamDayZenith", "dream_day_zenith", "#558ECC"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamDayHorizon = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamDayHorizon", "dream_day_horizon", "#B5C9E3"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamNightZenith = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamNightZenith", "dream_night_zenith", "#07122B"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamNightHorizon = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamNightHorizon", "dream_night_horizon", "#302E58"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamSkyGlow = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamSkyGlow", "dream_sky_glow", "#F4A9B7"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamStars = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamStars", "dream_stars", "#E4EEFF"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamCloudLight = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamCloudLight", "dream_cloud_light", "#F4F7FF"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamCloudShadow = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamCloudShadow", "dream_cloud_shadow", "#8295CC"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamAuroraPrimary = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamAuroraPrimary", "dream_aurora_primary", "#6BEBD2"), this::areCustomSkyColorsVisible));
    private final RGBColorValue dreamAuroraSecondary = shaderSkyboxNotAppliedWithIris(visibleWhen(
            colorNoAlpha("reimaginedDreamAuroraSecondary", "dream_aurora_secondary", "#9B7CFF"), this::areCustomSkyColorsVisible));
    private final BooleanValue dreamAurora = shaderSkyboxNotAppliedWithIris(visibleWhen(
            bool("reimaginedDreamAurora", SETTING_DREAM_AURORA, true), this::isShaderSkyboxSettingsVisible));
    private final NumberValue<Float> dreamAuroraIntensity = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedDreamAuroraIntensity", SETTING_DREAM_AURORA_INTENSITY, .55f, 0f, 2f),
            () -> isShaderSkyboxSettingsVisible() && dreamAurora.get()));
    private final NumberValue<Float> dreamAuroraSpeed = shaderSkyboxNotAppliedWithIris(visibleWhen(
            num("reimaginedDreamAuroraSpeed", SETTING_DREAM_AURORA_SPEED, 1f, .05f, 3f),
            () -> isShaderSkyboxSettingsVisible() && dreamAurora.get()));

    public DreamSkyPalette dreamSkyPalette() {
        if (dreamSkyPaletteMode.get() == SkyPaletteMode.THEME) return DreamSkyPalette.fromTheme();
        if (dreamSkyPaletteMode.get() == SkyPaletteMode.WORLD_TWEAKS)
            return DreamSkyPalette.fromWorldTweaks(Modules.get(WorldTweaks.class));
        return new DreamSkyPalette(dreamDayZenith.getArgb(), dreamDayHorizon.getArgb(),
                dreamNightZenith.getArgb(), dreamNightHorizon.getArgb(), dreamSkyGlow.getArgb(),
                dreamStars.getArgb(), dreamCloudLight.getArgb(), dreamCloudShadow.getArgb(),
                dreamAuroraPrimary.getArgb(), dreamAuroraSecondary.getArgb());
    }
    public boolean isDreamWorldTweaksPaletteSelected() {
        return dreamSkyPaletteMode.get() == SkyPaletteMode.WORLD_TWEAKS;
    }
    public boolean isDreamAuroraEnabled() { return dreamAurora.get(); }
    public float dreamAuroraIntensity() { return dreamAuroraIntensity.get(); }
    public float dreamAuroraSpeed() { return dreamAuroraSpeed.get(); }

    private final DreamFroxelCloudRenderer dreamCloudRenderer = new DreamFroxelCloudRenderer();
    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Float> proceduralRainDensity = visibleWhen(
            num("reimaginedVisualRainDensity", SETTING_RAIN_DENSITY, 1.0f, 0.4f, 2.0f),
            this::isProceduralRainSelected);
    private final NumberValue<Integer> proceduralRainDistance = visibleWhen(
            num("reimaginedVisualRainDistance", SETTING_RAIN_DISTANCE, 96, 32, 120),
            this::isProceduralRainSelected);
    private final ProceduralRainRenderer proceduralRainRenderer = new ProceduralRainRenderer();
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
        PostProcessManager.register(proceduralRainRenderer);
    }

    private static Map<String, Boolean> createDefaultEffects() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(EFFECT_SHADER_SKY, true);
        defaults.put(EFFECT_DEPTH_OF_FIELD, false);
        defaults.put(EFFECT_PROCEDURAL_RAIN, false);
        return defaults;
    }

    private static ReimaginedVisual module() {
        return Modules.get(ReimaginedVisual.class);
    }

    public boolean isShaderSkyboxEnabled() {
        return isEnabled() && isEffectSelected(EFFECT_SHADER_SKY);
    }

    public boolean areDreamCloudsEnabled() {
        return isShaderSkyboxEnabled() && dreamClouds.get() && dreamCloudCoverage.get() > 0.001f;
    }

    private boolean areDreamCloudSettingsVisible() {
        return isShaderSkyboxSettingsVisible() && dreamClouds.get();
    }

    public float dreamCloudHeight() { return dreamCloudHeight.get(); }
    public float dreamCloudThickness() { return dreamCloudThickness.get(); }
    public float dreamCloudCoverage() { return dreamCloudCoverage.get(); }
    public float dreamCloudScale() { return dreamCloudScale.get(); }
    public float dreamCloudDensity() { return dreamCloudDensity.get(); }
    public DreamCloudQuality dreamCloudQuality() { return dreamCloudQuality.get(); }
    public boolean areDreamFarCloudsEnabled() { return areDreamCloudsEnabled() && dreamFarClouds.get(); }
    public boolean areDreamCirrusEnabled() { return areDreamCloudsEnabled() && dreamCirrus.get(); }
    public DreamFroxelCloudRenderer dreamCloudRenderer() { return dreamCloudRenderer; }

    /** Only replace vanilla precipitation while the GPU implementation is healthy. */
    public static boolean isProceduralRainEnabledStatic() {
        ReimaginedVisual module = module();
        return module != null && module.isEnabled() && module.isProceduralRainSelected()
                && module.proceduralRainRenderer.healthy();
    }

    /** Weather extraction must not hide vanilla rain before GPU precipitation has rendered. */
    public static boolean shouldSuppressVanillaRainStatic() {
        ReimaginedVisual module = module();
        return module != null && isProceduralRainEnabledStatic()
                && module.mc != null && module.mc.level != null
                && module.proceduralRainRenderer.recentlySubmittedForWorld(module.mc.level);
    }

    private boolean isProceduralRainSelected() {
        return isEffectSelected(EFFECT_PROCEDURAL_RAIN);
    }

    public float getProceduralRainDensity() {
        return proceduralRainDensity.get();
    }

    public int getProceduralRainDistance() {
        return proceduralRainDistance.get();
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
        return isActive() || isProceduralRainEnabledStatic();
    }

    public boolean needsPreTranslucentDepthCapture() {
        return false;
    }

    public boolean needsResolvedMainDepthCapture() {
        return isProceduralRainEnabledStatic();
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
        proceduralRainRenderer.reset();
        dreamCloudRenderer.reset();
        depthSamplerSupported = true;
        dofFocusResolveSupported = true;
        dofComputeSupported = true;
    }

    @Override
    public void onDisable() {
        proceduralRainRenderer.reset();
        dreamCloudRenderer.close();
        closeDofFocusTarget();
        dofComputeBackend.close();
    }

    @Override
    public void releaseBackendResources(CombatantRhi owner) {
        dofComputeBackend.release(owner);
        proceduralRainRenderer.releaseBackendResources(owner);
        dreamCloudRenderer.release(owner);
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
