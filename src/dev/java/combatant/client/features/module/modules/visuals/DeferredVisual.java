/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.module.modules.visuals;

import combatant.client.config.ConfigSerializer;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.Modules;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.deferred.DeferredCameraPostConfig;
import combatant.client.render.engine.deferred.DeferredDebugDiagnostics;
import combatant.client.render.engine.deferred.DeferredDebugView;
import combatant.client.render.engine.deferred.DeferredDebugVolumeAxis;
import combatant.client.render.engine.deferred.DeferredFeature;
import combatant.client.render.engine.deferred.DeferredFeatureOverride;
import combatant.client.render.engine.deferred.DeferredPostConfig;
import combatant.client.render.engine.deferred.DeferredRuntimeConfig;
import combatant.client.render.engine.deferred.DeferredTemporalConfig;
import combatant.client.render.engine.deferred.DeferredWorldPipeline;
import combatant.client.render.engine.deferred.DevDeferredRuntime;
import combatant.client.render.engine.postprocess.DepthOfFieldQuality;
import combatant.client.render.helpers.SodiumMaterialFlags;
import combatant.client.util.logging.DebugLog;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * User-facing controller for Combatant's world renderer.
 *
 * <p>Rendering code consumes immutable deferred config snapshots; shaders and passes never read
 * this module directly. The only retained pre-deferred feature is Sodium vegetation deformation,
 * because it is a material/geometry policy rather than a legacy post/sky path.</p>
 */
@ModuleInfo(id = "deferredvisual", displayName = "DeferredVisual", category = ModuleCategory.VISUALS)
public final class DeferredVisual extends Module {
    private static final String EFFECT_WAVY_VEGETATION = "wavy_vegetation";
    private static final String EFFECT_TAA = "taa";
    private static final String EFFECT_EXPOSURE = "exposure";
    private static final String EFFECT_BLOOM = "bloom";
    private static final String EFFECT_DEPTH_OF_FIELD = "depth_of_field";
    private static final String EFFECT_MOTION_BLUR = "motion_blur";

    // Smoke overrides remain a diagnostic layer; normal subsystem enablement is the effects map above.
    private static final DeferredFeature[] DEBUG_FEATURES = {
            DeferredFeature.TAA, DeferredFeature.EXPOSURE,
            DeferredFeature.BLOOM, DeferredFeature.DEPTH_OF_FIELD, DeferredFeature.MOTION_BLUR
    };

    private static final Map<String, Boolean> DEFAULT_EFFECTS = createDefaultEffects();
    private final BooleanValue deferredRenderer = bool(
            "reimaginedVisualDeferredRenderer", "deferred_renderer", true);
    private final BooleanMapValue effects = group(
            "reimaginedVisualEffects", "effects", DEFAULT_EFFECTS);

    // UI adapter for Task C renderer-side smoke/debug infrastructure. No compositor logic lives here.
    private final BooleanValue smokeIsolationMode = visibleWhen(
            bool("reimaginedVisualSmokeIsolationMode", "smoke_isolation_mode", false),
            deferredRenderer::get);
    private final EnumValue<DeferredDebugView> debugView = visibleWhen(
            enumSetting("reimaginedVisualDebugView", "debug_view", DeferredDebugView.OFF, DeferredDebugView.values()),
            deferredRenderer::get);
    private final EnumValue<DeferredDebugVolumeAxis> debugVolumeAxis = visibleWhen(
            enumSetting("reimaginedVisualDebugVolumeAxis", "debug_volume_axis",
                    DeferredDebugVolumeAxis.Z, DeferredDebugVolumeAxis.values()),
            this::isVolumeDebugView);
    private final NumberValue<Float> debugVolumeSlice = visibleWhen(
            num("reimaginedVisualDebugVolumeSlice", "debug_volume_slice", 0.5f, 0.0f, 1.0f),
            this::isVolumeDebugView);
    // Toggle-once UI command. It self-clears after resetting typed smoke/debug state.
    private final BooleanValue resetDebugOverrides = visibleWhen(
            bool("reimaginedVisualResetDebugOverrides", "reset_debug_overrides", false),
            deferredRenderer::get);
    private final EnumMap<DeferredFeature, EnumValue<DeferredFeatureOverride>> debugFeatureOverrides =
            createDebugFeatureOverrides();

    // Kept from the old module: this is still consumed by Sodium's material/vertex contract.
    private final NumberValue<Float> wavyVegetationRootedHorizontalAmplitude = visibleWhen(
            num("reimaginedVisualWavyVegetationRootedHorizontalAmplitude",
                    "wavy_vegetation_rooted_horizontal_amplitude", 1.0f, 0.0f, 3.0f),
            this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationRootedVerticalAmplitude = visibleWhen(
            num("reimaginedVisualWavyVegetationRootedVerticalAmplitude",
                    "wavy_vegetation_rooted_vertical_amplitude", 1.0f, 0.0f, 3.0f),
            this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationFreeHorizontalAmplitude = visibleWhen(
            num("reimaginedVisualWavyVegetationFreeHorizontalAmplitude",
                    "wavy_vegetation_free_horizontal_amplitude", 1.0f, 0.0f, 3.0f),
            this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationFreeVerticalAmplitude = visibleWhen(
            num("reimaginedVisualWavyVegetationFreeVerticalAmplitude",
                    "wavy_vegetation_free_vertical_amplitude", 1.0f, 0.0f, 3.0f),
            this::isWavyVegetationSettingsVisible);
    private final NumberValue<Float> wavyVegetationSpeed = visibleWhen(
            num("reimaginedVisualWavyVegetationSpeed", "wavy_vegetation_speed", 1.0f, 0.0f, 3.0f),
            this::isWavyVegetationSettingsVisible);

    // Deferred DoF keeps the old user-visible far blur model, but not its legacy depth-source ABI.
    private final BooleanValue dofAutofocus = visibleWhen(
            bool("reimaginedVisualDofAutofocus", "dof_autofocus", true), this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofFarStart = visibleWhen(
            num("reimaginedVisualDofFarStart", "dof_far_start", 4.0f, 0.0f, 512.0f), this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofFarTransition = visibleWhen(
            num("reimaginedVisualDofFarTransition", "dof_far_transition", 24.0f, 0.1f, 1024.0f), this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofStrength = visibleWhen(
            num("reimaginedVisualDofStrength", "dof_strength", 0.65f, 0.0f, 1.5f), this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofMaxRadius = visibleWhen(
            num("reimaginedVisualDofMaxRadius", "dof_max_radius", 8.0f, 0.0f, 32.0f), this::isDepthOfFieldSettingsVisible);
    private final EnumValue<DepthOfFieldQuality> dofQuality = visibleWhen(
            enumSetting("reimaginedVisualDofQuality", "dof_quality", DepthOfFieldQuality.MEDIUM, DepthOfFieldQuality.values()),
            this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofEdgeProtection = visibleWhen(
            num("reimaginedVisualDofEdgeProtection", "dof_edge_protection", 0.85f, 0.0f, 4.0f), this::isDepthOfFieldSettingsVisible);
    private final NumberValue<Float> dofFocusSmoothing = visibleWhen(
            num("reimaginedVisualDofFocusSmoothing", "dof_focus_smoothing", 8.0f, 0.0f, 32.0f), this::isDepthOfFieldSettingsVisible);
    // Standalone MotionBlur settings are folded into the real deferred velocity-buffer path.
    private final NumberValue<Float> motionBlurStrength = visibleWhen(
            num("reimaginedVisualMotionBlurStrength", "motion_blur_strength", 0.42f, 0.0f, 1.5f), this::isMotionBlurSettingsVisible);
    private final NumberValue<Integer> motionBlurMaxPixels = visibleWhen(
            num("reimaginedVisualMotionBlurMaxPixels", "motion_blur_max_pixels", 18, 0, 64), this::isMotionBlurSettingsVisible);
    private final NumberValue<Float> motionBlurMinMotionPixels = visibleWhen(
            num("reimaginedVisualMotionBlurMinMotionPixels", "motion_blur_min_motion_pixels", 0.45f, 0.0f, 4.0f), this::isMotionBlurSettingsVisible);
    private final NumberValue<Float> motionBlurShutterScale = visibleWhen(
            num("reimaginedVisualMotionBlurShutterScale", "motion_blur_shutter_scale", 1.0f, 0.0f, 6.0f), this::isMotionBlurSettingsVisible);
    private final NumberValue<Integer> motionBlurSamples = visibleWhen(
            num("reimaginedVisualMotionBlurSamples", "motion_blur_samples", 12, 4, 32), this::isMotionBlurSettingsVisible);
    private final NumberValue<Float> motionBlurDepthEdgeProtection = visibleWhen(
            num("reimaginedVisualMotionBlurDepthEdgeProtection", "motion_blur_depth_edge_protection", 1.0f, 0.0f, 4.0f), this::isMotionBlurSettingsVisible);

    private final NumberValue<Float> bloomThreshold = visibleWhen(
            num("reimaginedVisualBloomThreshold", "bloom_threshold", 1.0f, 0.0f, 16.0f), () -> isEffectSelected(EFFECT_BLOOM));
    private final NumberValue<Float> bloomSoftKnee = visibleWhen(
            num("reimaginedVisualBloomSoftKnee", "bloom_soft_knee", 0.5f, 0.0f, 1.0f), () -> isEffectSelected(EFFECT_BLOOM));
    private final NumberValue<Float> bloomIntensity = visibleWhen(
            num("reimaginedVisualBloomIntensity", "bloom_intensity", 0.05f, 0.0f, 2.0f), () -> isEffectSelected(EFFECT_BLOOM));

    private boolean transientDebugStateInitialized;
    private boolean wavyVegetationStateInitialized;
    private boolean lastWavyVegetationActive;
    private int lastWavyVegetationSettingsPacked;

    private EnumMap<DeferredFeature, EnumValue<DeferredFeatureOverride>> createDebugFeatureOverrides() {
        EnumMap<DeferredFeature, EnumValue<DeferredFeatureOverride>> values = new EnumMap<>(DeferredFeature.class);
        for (DeferredFeature feature : DEBUG_FEATURES) {
            String suffix = feature.name().toLowerCase(Locale.ROOT);
            EnumValue<DeferredFeatureOverride> value = visibleWhen(
                    enumSetting("reimaginedVisualDebugOverride_" + suffix, "debug_override_" + suffix,
                            DeferredFeatureOverride.DEFAULT, DeferredFeatureOverride.values()),
                    deferredRenderer::get);
            values.put(feature, value);
        }
        return values;
    }

    private static Map<String, Boolean> createDefaultEffects() {
        LinkedHashMap<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(EFFECT_WAVY_VEGETATION, false);
        // Keep projection stable by default until the temporal path is validated independently.
        defaults.put(EFFECT_TAA, false);
        defaults.put(EFFECT_EXPOSURE, true);
        defaults.put(EFFECT_BLOOM, true);
        defaults.put(EFFECT_DEPTH_OF_FIELD, false);
        defaults.put(EFFECT_MOTION_BLUR, false);
        return defaults;
    }

    public static boolean isWavyVegetationEnabledStatic() {
        DeferredVisual module = module();
        return module != null && module.isEnabled() && module.isEffectSelected(EFFECT_WAVY_VEGETATION);
    }

    public static boolean isDeferredMotionBlurEnabledStatic() {
        DeferredVisual module = module();
        return module != null
                && module.isEnabled()
                && module.deferredRenderer.get()
                && module.isEffectSelected(EFFECT_MOTION_BLUR)
                && DevDeferredRuntime.world().enabled()
                && DevDeferredRuntime.world().smokeTestState()
                .featureEnabled(DeferredFeature.MOTION_BLUR, DeferredRuntimeConfig.current());
    }

    public static int packWavyVegetationSettingsStatic() {
        DeferredVisual module = module();
        return module != null
                ? module.packWavyVegetationSettings()
                : packWavyVegetationSettings(1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public DeferredWorldPipeline.LifecycleState deferredLifecycleState() {
        return DevDeferredRuntime.world().lifecycleState();
    }

    public boolean deferredRequested() {
        return isEnabled() && deferredRenderer.get();
    }

    public DeferredDebugDiagnostics rendererDiagnostics() {
        return DevDeferredRuntime.world().debugDiagnostics();
    }

    @Override
    public void onTick() {
        initializeTransientDebugState();
        publishRendererSettings();
        publishDebugSettings();
        refreshWavyVegetationTerrainState();
    }

    @Override
    public void onEnable() {
        initializeTransientDebugState();
        publishRendererSettings();
        publishDebugSettings();
        DevDeferredRuntime.world().requestEnabled(deferredRenderer.get());
        refreshWavyVegetationTerrainState();
    }

    @Override
    public void onDisable() {
        // Explicit policy: disabling the controller disables deferred rendering; no hidden renderer state survives.
        DeferredWorldPipeline pipeline = DevDeferredRuntime.world();
        pipeline.requestEnabled(false);
        resetTransientDebugControls(pipeline, false);
        clearRendererDebugState(pipeline);
        DeferredCameraPostConfig.apply(DeferredCameraPostConfig.Snapshot.defaults());
        DeferredTemporalConfig.setTaaEnabled(false);
        refreshWavyVegetationTerrainState(false);
    }

    private void publishRendererSettings() {
        if (!isEnabled()) return;
        DevDeferredRuntime.world().requestEnabled(deferredRenderer.get());

        DeferredRuntimeConfig.Snapshot runtime = DeferredRuntimeConfig.current();
        boolean taa = isEffectSelected(EFFECT_TAA);
        if (runtime.waterEnabled()) {
            DeferredRuntimeConfig.update(builder -> builder.waterEnabled(false));
        }

        if (DeferredTemporalConfig.current().taaEnabled() != taa) {
            DeferredTemporalConfig.setTaaEnabled(taa);
        }

        DeferredPostConfig.Snapshot post = DeferredPostConfig.current();
        DeferredPostConfig.Snapshot desiredPost = new DeferredPostConfig.Snapshot(
                post.histogramMinLogLuminance(), post.histogramMaxLogLuminance(), post.histogramMaxSamples(),
                bloomThreshold.get(), bloomSoftKnee.get(), bloomIntensity.get(),
                post.bloomInitialScale(), post.bloomMaxMipCount(),
                isEffectSelected(EFFECT_EXPOSURE), isEffectSelected(EFFECT_BLOOM));
        if (!desiredPost.equals(post)) DeferredPostConfig.apply(desiredPost);

        DeferredCameraPostConfig.Snapshot desiredCamera = new DeferredCameraPostConfig.Snapshot(
                isEffectSelected(EFFECT_DEPTH_OF_FIELD), dofAutofocus.get(), dofFarStart.get(), dofFarTransition.get(),
                dofStrength.get(), dofMaxRadius.get(), dofQuality.get().taps(), dofEdgeProtection.get(),
                dofFocusSmoothing.get(),
                isEffectSelected(EFFECT_MOTION_BLUR), motionBlurStrength.get(), motionBlurMaxPixels.get(),
                motionBlurMinMotionPixels.get(), motionBlurShutterScale.get(), motionBlurSamples.get(),
                motionBlurDepthEdgeProtection.get()).validated();
        if (!desiredCamera.equals(DeferredCameraPostConfig.current())) {
            DeferredCameraPostConfig.apply(desiredCamera);
        }
    }

    private void initializeTransientDebugState() {
        if (transientDebugStateInitialized) return;
        transientDebugStateInitialized = true;
        resetTransientDebugControls(DevDeferredRuntime.world(), true);
    }

    private void resetTransientDebugControls(DeferredWorldPipeline pipeline, boolean startup) {
        boolean changed = smokeIsolationMode.get() || resetDebugOverrides.get();
        smokeIsolationMode.set(false);
        resetDebugOverrides.set(false);
        for (EnumValue<DeferredFeatureOverride> value : debugFeatureOverrides.values()) {
            if (value.get() != DeferredFeatureOverride.DEFAULT) changed = true;
            value.set(DeferredFeatureOverride.DEFAULT);
        }
        pipeline.clearFeatureOverrides();
        pipeline.setSmokeIsolationMode(false);
        if (changed) {
            DebugLog.warnOnce(
                    startup ? "deferred-transient-debug-reset-startup" : "deferred-transient-debug-reset-disable",
                    startup
                            ? "[Deferred][Smoke] cleared persisted transient isolation/feature overrides at session start"
                            : "[Deferred][Smoke] cleared transient isolation/feature overrides while disabling renderer"
            );
            ConfigSerializer.requestSave(this);
        }
    }

    private void publishDebugSettings() {
        if (!isEnabled()) return;
        DeferredWorldPipeline pipeline = DevDeferredRuntime.world();
        if (resetDebugOverrides.get()) {
            resetDebugSettings(pipeline);
            resetDebugOverrides.set(false);
            ConfigSerializer.requestSave(this);
            return;
        }

        pipeline.setSmokeIsolationMode(smokeIsolationMode.get());
        for (Map.Entry<DeferredFeature, EnumValue<DeferredFeatureOverride>> entry : debugFeatureOverrides.entrySet()) {
            pipeline.setFeatureOverride(entry.getKey(), entry.getValue().get());
        }
        pipeline.setDebugView(debugView.get());
        pipeline.setDebugVolumeSlice(debugVolumeAxis.get(), debugVolumeSlice.get());
    }

    private void resetDebugSettings(DeferredWorldPipeline pipeline) {
        smokeIsolationMode.set(false);
        debugView.set(DeferredDebugView.OFF);
        debugVolumeAxis.set(DeferredDebugVolumeAxis.Z);
        debugVolumeSlice.set(0.5f);
        for (EnumValue<DeferredFeatureOverride> value : debugFeatureOverrides.values()) {
            value.set(DeferredFeatureOverride.DEFAULT);
        }
        clearRendererDebugState(pipeline);
    }

    private static void clearRendererDebugState(DeferredWorldPipeline pipeline) {
        pipeline.clearFeatureOverrides();
        pipeline.setSmokeIsolationMode(false);
        pipeline.setDebugView(DeferredDebugView.OFF);
        pipeline.setDebugVolumeSlice(DeferredDebugVolumeAxis.Z, 0.5f);
    }

    private boolean isVolumeDebugView() {
        DeferredDebugView selected = debugView.get();
        return deferredRenderer.get() && selected != null
                && selected.sourceKind() == DeferredDebugView.SourceKind.VOLUME;
    }

    private static DeferredVisual module() {
        return Modules.get(DeferredVisual.class);
    }

    private boolean isEffectSelected(String effect) {
        return effects.get(effect);
    }

    private boolean isWavyVegetationSettingsVisible() {
        return isEffectSelected(EFFECT_WAVY_VEGETATION);
    }

    private boolean isDepthOfFieldSettingsVisible() {
        return isEffectSelected(EFFECT_DEPTH_OF_FIELD);
    }

    private boolean isMotionBlurSettingsVisible() {
        return isEffectSelected(EFFECT_MOTION_BLUR);
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
                wavyVegetationRootedHorizontalAmplitude.get(), wavyVegetationRootedVerticalAmplitude.get(),
                wavyVegetationFreeHorizontalAmplitude.get(), wavyVegetationFreeVerticalAmplitude.get(),
                wavyVegetationSpeed.get());
    }

    private static int packWavyVegetationSettings(float rootedHorizontal, float rootedVertical,
                                                   float freeHorizontal, float freeVertical, float speed) {
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
}
