/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import combatant.client.render.engine.world.WorldRenderState;
import combatant.client.render.engine.world.environment.AtmosphereModel;
import combatant.client.render.engine.world.environment.AtmosphereModelRegistry;
import combatant.client.render.engine.world.environment.AtmosphereState;
import combatant.client.render.engine.world.environment.BiomeClimateSampler;
import combatant.client.render.engine.world.environment.BiomeClimateState;
import combatant.client.render.engine.world.environment.CelestialModel;
import combatant.client.render.engine.world.environment.CelestialModelRegistry;
import combatant.client.render.engine.world.environment.CelestialState;
import combatant.client.render.engine.world.environment.DimensionRenderProfile;
import combatant.client.render.engine.world.environment.DimensionRenderProfileRegistry;
import combatant.client.render.engine.world.environment.EnvironmentCaptureContext;
import combatant.client.render.engine.world.environment.MinecraftBaselineLightState;
import combatant.client.render.engine.world.environment.WeatherProvider;
import combatant.client.render.engine.world.environment.WeatherProviderRegistry;
import combatant.client.render.engine.world.environment.WeatherState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds one explicit renderer-world contract before graph consumers execute. */
final class DeferredWorldRenderStateSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant");

    private final BiomeClimateSampler biomeClimate = new BiomeClimateSampler();

    private Object worldOwner;
    private long epoch;
    private WorldRenderState current = WorldRenderState.unknown(0L);
    private DeferredEnvironmentCaptureDiagnostics diagnostics = DeferredEnvironmentCaptureDiagnostics.unknown(Long.MIN_VALUE);
    private String lastWeatherFailure = "";
    private String lastCelestialFailure = "";
    private String lastAtmosphereFailure = "";
    private String lastBaselineFailure = "";

    WorldRenderState capture(ClientLevel level, DeferredPrimaryViewSource.FrameView view) {
        if (worldOwner != level) {
            worldOwner = level;
            epoch++;
            biomeClimate.reset();
            WeatherProviderRegistry.resetAll();
            AtmosphereModelRegistry.resetAll();
            CelestialModelRegistry.resetAll();
            clearFailureLatches();
        }
        if (level == null) {
            current = WorldRenderState.unknown(epoch);
            diagnostics = DeferredEnvironmentCaptureDiagnostics.unknown(view != null ? view.frameId() : Long.MIN_VALUE);
            return current;
        }

        Identifier dimensionKey = level.dimension().identifier();
        DimensionRenderProfile profile = DimensionRenderProfileRegistry.resolve(level.dimension());
        Vec3 camera = view != null ? view.cameraPosition() : Vec3.ZERO;
        float partialTick = partialTick();
        long frameId = view != null ? view.frameId() : Long.MIN_VALUE;

        BiomeClimateState climate = biomeClimate.capture(level, camera);

        WeatherState weather = WeatherState.NONE;
        WeatherProvider weatherProvider = WeatherProviderRegistry.resolve(profile.weatherProvider());
        DeferredEnvironmentCaptureDiagnostics.Producer weatherDiagnostic;
        if (weatherProvider == null) {
            weatherDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.unavailable(
                    stringId(profile.weatherProvider()), "provider_unavailable", frameId);
        } else {
            try {
                weather = weatherProvider.capture(level, camera, partialTick, climate, frameId);
                if (weather == null) weather = WeatherState.NONE;
                weatherDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.produced(
                        stringId(weatherProvider.id()), frameId);
                lastWeatherFailure = "";
            } catch (Throwable failure) {
                weather = WeatherState.NONE;
                weatherDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.failed(
                        stringId(weatherProvider.id()), "provider_exception", failure, frameId);
                lastWeatherFailure = logFailureOnChange(
                        "weather", stringId(weatherProvider.id()), failure, lastWeatherFailure);
            }
        }

        Camera minecraftCamera = mainCamera(level);
        EnvironmentCaptureContext environmentContext = new EnvironmentCaptureContext(
                level, minecraftCamera, camera, partialTick, climate, weather, frameId
        );

        CelestialState celestial = CelestialState.NONE;
        CelestialModel celestialModel = CelestialModelRegistry.resolve(profile.celestialModel());
        DeferredEnvironmentCaptureDiagnostics.Producer celestialDiagnostic;
        if (celestialModel == null) {
            celestialDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.unavailable(
                    stringId(profile.celestialModel()), "provider_unavailable", frameId);
        } else {
            try {
                celestial = celestialModel.capture(environmentContext);
                if (celestial == null) celestial = CelestialState.NONE;
                celestialDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.produced(
                        stringId(celestialModel.id()), frameId);
                lastCelestialFailure = "";
            } catch (Throwable failure) {
                celestial = CelestialState.NONE;
                celestialDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.failed(
                        stringId(celestialModel.id()), "provider_exception", failure, frameId);
                lastCelestialFailure = logFailureOnChange(
                        "celestial", stringId(celestialModel.id()), failure, lastCelestialFailure);
            }
        }

        AtmosphereState atmosphere = AtmosphereState.NONE;
        AtmosphereModel atmosphereModel = AtmosphereModelRegistry.resolve(profile.environmentModel());
        DeferredEnvironmentCaptureDiagnostics.Producer atmosphereDiagnostic;
        if (atmosphereModel == null) {
            atmosphereDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.unavailable(
                    stringId(profile.environmentModel()), "provider_unavailable", frameId);
        } else {
            try {
                atmosphere = atmosphereModel.capture(environmentContext);
                if (atmosphere == null) atmosphere = AtmosphereState.NONE;
                atmosphereDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.produced(
                        stringId(atmosphereModel.id()), frameId);
                lastAtmosphereFailure = "";
            } catch (Throwable failure) {
                atmosphere = AtmosphereState.NONE;
                atmosphereDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.failed(
                        stringId(atmosphereModel.id()), "provider_exception", failure, frameId);
                lastAtmosphereFailure = logFailureOnChange(
                        "atmosphere", stringId(atmosphereModel.id()), failure, lastAtmosphereFailure);
            }
        }

        BaselineCapture baselineCapture = captureBaselineLight(level, minecraftCamera, partialTick, frameId);

        DirectionalLightDescriptor directional = celestial.valid()
                ? celestial.primaryDirectionalLight()
                : DirectionalLightDescriptor.NONE;
        if (directional.valid() && atmosphere.valid() && atmosphereModel != null) {
            try {
                directional = atmosphereModel.attenuateDirectLight(atmosphere, directional, camera.y);
            } catch (Throwable failure) {
                lastAtmosphereFailure = logFailureOnChange(
                        "atmosphere direct-light attenuation", stringId(atmosphereModel.id()),
                        failure, lastAtmosphereFailure);
                atmosphereDiagnostic = DeferredEnvironmentCaptureDiagnostics.Producer.failed(
                        stringId(atmosphereModel.id()), "direct_light_attenuation_exception", failure, frameId);
            }
        }

        diagnostics = new DeferredEnvironmentCaptureDiagnostics(
                weatherDiagnostic, celestialDiagnostic, atmosphereDiagnostic, baselineCapture.diagnostic()
        );
        current = new WorldRenderState(
                dimensionKey,
                profile.id(),
                profile.environmentModel(),
                profile.skyProvider(),
                profile.celestialModel(),
                profile.mediumProfile(),
                profile.weatherProvider(),
                profile.cloudProfile(),
                profile.ambientPalette(),
                profile.exposureProfile(),
                profile.postProfile(),
                WorldRenderState.NONE,
                atmosphere,
                climate,
                celestial,
                weather,
                baselineCapture.state(),
                directional,
                epoch
        );
        return current;
    }

    WorldRenderState current() {
        return current;
    }

    DeferredEnvironmentCaptureDiagnostics diagnostics() {
        return diagnostics;
    }

    void reset() {
        worldOwner = null;
        epoch++;
        biomeClimate.reset();
        WeatherProviderRegistry.resetAll();
        AtmosphereModelRegistry.resetAll();
        CelestialModelRegistry.resetAll();
        current = WorldRenderState.unknown(epoch);
        diagnostics = DeferredEnvironmentCaptureDiagnostics.unknown(Long.MIN_VALUE);
        clearFailureLatches();
    }

    private BaselineCapture captureBaselineLight(ClientLevel level, Camera camera, float partialTick, long frameId) {
        DimensionType dimension = level.dimensionType();
        boolean hasSkyLight = dimension.hasSkyLight();
        float dimensionAmbient = dimension.ambientLight();
        if (camera == null) {
            float fallbackSkyFactor = hasSkyLight
                    ? clamp01((15.0f - Math.max(0, level.getSkyDarken())) / 15.0f)
                    : 0.0f;
            MinecraftBaselineLightState fallback = fallbackBaseline(
                    hasSkyLight, fallbackSkyFactor, dimensionAmbient);
            return new BaselineCapture(fallback, new DeferredEnvironmentCaptureDiagnostics.Producer(
                    DeferredResourceStatus.FALLBACK, "minecraft:environment_attributes",
                    "camera_unavailable", "main camera unavailable; using dimension baseline", frameId
            ));
        }

        try {
            EnvironmentAttributeProbe probe = camera.attributeProbe();
            float skyFactor = finite(probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick), 0.0f);
            int skyLight = probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, partialTick);
            int blockLight = probe.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, partialTick);
            int ambient = probe.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, partialTick);
            int skyBackground = probe.getValue(EnvironmentAttributes.SKY_COLOR, partialTick);
            MinecraftBaselineLightState state = new MinecraftBaselineLightState(
                    true, hasSkyLight, skyFactor, dimensionAmbient,
                    ARGB.redFloat(skyLight), ARGB.greenFloat(skyLight), ARGB.blueFloat(skyLight),
                    ARGB.redFloat(blockLight), ARGB.greenFloat(blockLight), ARGB.blueFloat(blockLight),
                    ARGB.redFloat(ambient), ARGB.greenFloat(ambient), ARGB.blueFloat(ambient),
                    ARGB.redFloat(skyBackground), ARGB.greenFloat(skyBackground), ARGB.blueFloat(skyBackground)
            );
            lastBaselineFailure = "";
            return new BaselineCapture(state,
                    DeferredEnvironmentCaptureDiagnostics.Producer.produced("minecraft:environment_attributes", frameId));
        } catch (Throwable failure) {
            float fallbackSkyFactor = hasSkyLight
                    ? clamp01((15.0f - Math.max(0, level.getSkyDarken())) / 15.0f)
                    : 0.0f;
            MinecraftBaselineLightState fallback = fallbackBaseline(
                    hasSkyLight, fallbackSkyFactor, dimensionAmbient);
            lastBaselineFailure = logFailureOnChange(
                    "Minecraft baseline light", "minecraft:environment_attributes", failure, lastBaselineFailure);
            return new BaselineCapture(fallback,
                    DeferredEnvironmentCaptureDiagnostics.Producer.failed(
                            "minecraft:environment_attributes", "capture_exception", failure, frameId));
        }
    }

    private static MinecraftBaselineLightState fallbackBaseline(
            boolean hasSkyLight, float skyFactor, float dimensionAmbient) {
        float visibleSky = hasSkyLight ? Math.max(0.08f, skyFactor) : 0.0f;
        return new MinecraftBaselineLightState(
                false, hasSkyLight, skyFactor, dimensionAmbient,
                1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                dimensionAmbient, dimensionAmbient, dimensionAmbient,
                0.12f * visibleSky, 0.12f * visibleSky, 0.12f * visibleSky
        );
    }

    private static Camera mainCamera(ClientLevel expectedLevel) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) return null;
        Camera camera = minecraft.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return null;
        if (camera.entity() != null && camera.entity().level() != expectedLevel) return null;
        return camera;
    }

    private static float partialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        DeltaTracker tracker = minecraft != null ? minecraft.getDeltaTracker() : null;
        if (tracker == null) return 0.0f;
        float value = tracker.getGameTimeDeltaPartialTick(true);
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }

    private static float finite(Float value, float fallback) {
        if (value == null || !Float.isFinite(value)) return fallback;
        return value;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static String stringId(Identifier id) {
        return id == null ? "" : id.toString();
    }

    private static String logFailureOnChange(String component, String producerId, Throwable failure, String previousKey) {
        String key = producerId + "|" + failure.getClass().getName() + "|" + String.valueOf(failure.getMessage());
        if (!key.equals(previousKey)) {
            LOGGER.warn("[Combatant][Renderer] {} provider {} failed; using declared fallback", component, producerId, failure);
        }
        return key;
    }

    private void clearFailureLatches() {
        lastWeatherFailure = "";
        lastCelestialFailure = "";
        lastAtmosphereFailure = "";
        lastBaselineFailure = "";
    }

    private record BaselineCapture(
            MinecraftBaselineLightState state,
            DeferredEnvironmentCaptureDiagnostics.Producer diagnostic
    ) {
    }
}
