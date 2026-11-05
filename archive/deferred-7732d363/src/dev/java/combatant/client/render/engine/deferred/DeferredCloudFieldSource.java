/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.Std430StructLayout;
import combatant.client.render.engine.rhi.shader.Std430Type;
import combatant.client.render.engine.rhi.shader.Std430Writer;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageBufferDescriptor;
import combatant.client.render.engine.world.environment.CloudDomainProfile;
import combatant.client.render.engine.world.environment.CloudProfile;
import combatant.client.render.engine.world.environment.CloudProfileRegistry;
import combatant.client.render.engine.world.environment.WeatherFieldState;
import combatant.client.render.engine.world.environment.WeatherSample;
import combatant.client.render.engine.world.environment.WeatherState;

/** Shared GPU-side cloud/weather contract consumed by radiance, shadows and media. */
final class DeferredCloudFieldSource implements AutoCloseable {
    static final Std430StructLayout WEATHER_LAYOUT = Std430StructLayout.builder()
            .member("climate", Std430Type.VEC4)
            .member("windFront", Std430Type.VEC4)
            .build();
    static final Std430StructLayout DOMAIN_LAYOUT = Std430StructLayout.builder()
            .member("envelope", Std430Type.VEC4)
            .member("development", Std430Type.VEC4)
            .member("scaleShape", Std430Type.VEC4)
            .member("weatherOptics", Std430Type.VEC4)
            .member("coverageShape", Std430Type.VEC4)
            .member("scatteringPolicy", Std430Type.VEC4)
            .member("windPolicy", Std430Type.VEC4)
            .member("familyShape", Std430Type.VEC4)
            .member("domainPolicy", Std430Type.VEC4)
            .member("densityPolicy", Std430Type.VEC4)
            .build();

    private final DeferredCloudConfig config = DeferredCloudConfig.current();
    private CombatantRhi owner;
    private RhiStorageBuffer weatherData;
    private RhiStorageBuffer macroWeatherData;
    private RhiStorageBuffer domainData;
    private long uploadedFrameId = Long.MIN_VALUE;
    private FrameData uploadedFrame = FrameData.EMPTY;

    FrameData prepareFrame(DeferredPassContext context) {
        ensureOwner(context.rhi());
        ensureBuffers();
        long frameId = context.frame().frameId();
        if (uploadedFrameId == frameId) return uploadedFrame;

        CloudProfile profile = CloudProfileRegistry.resolve(context.worldState().cloudProfile());
        WeatherState weather = context.worldState().weatherState();
        if (weather == null) weather = WeatherState.NONE;
        WeatherFieldState field = weather.field() == null ? WeatherFieldState.EMPTY : weather.field();
        WeatherFieldState macroField = weather.macroField() == null ? WeatherFieldState.EMPTY : weather.macroField();

        int weatherCount = validCount(field, config.maxWeatherSamples());
        int macroWeatherCount = validCount(macroField, config.maxMacroWeatherSamples());
        boolean weatherAvailable = weather.valid() && weatherCount > 0 && macroWeatherCount > 0;
        boolean active = context.featureEnabled(DeferredFeature.CLOUDS)
                && config.enabled()
                && profile.valid()
                && weatherAvailable;
        int domainCount = active ? Math.min(profile.domains().size(), config.maxDomains()) : 0;

        uploadWeather(weatherData, field, weatherCount);
        uploadWeather(macroWeatherData, macroField, macroWeatherCount);
        uploadDomains(profile, domainCount);

        uploadedFrame = new FrameData(
                profile, weather, field, macroField,
                weatherCount, macroWeatherCount, domainCount, active
        );
        uploadedFrameId = frameId;
        return uploadedFrame;
    }

    RhiStorageBuffer weatherData() {
        ensureBuffers();
        return weatherData;
    }

    RhiStorageBuffer macroWeatherData() {
        ensureBuffers();
        return macroWeatherData;
    }

    RhiStorageBuffer domainData() {
        ensureBuffers();
        return domainData;
    }

    void prepare(CombatantRhi rhi) {
        ensureOwner(rhi);
        ensureBuffers();
    }

    void release(CombatantRhi currentOwner) {
        if (owner != null && currentOwner != null && owner != currentOwner) return;
        closeOwned();
        owner = null;
    }

    private static int validCount(WeatherFieldState field, int capacity) {
        if (field == null || !field.valid()) return 0;
        long required = (long) field.gridWidth() * (long) field.gridDepth();
        if (required <= 0L || required > capacity || required > Integer.MAX_VALUE) return 0;
        return (int) required;
    }

    private void uploadWeather(RhiStorageBuffer target, WeatherFieldState field, int count) {
        if (count <= 0) return;
        Std430Writer writer = new Std430Writer(WEATHER_LAYOUT, count);
        for (int i = 0; i < count; i++) {
            WeatherSample sample = field.samples().get(i);
            writer.putVec4(i, "climate", sample.humidity(), sample.pressureAnomaly(),
                            sample.stormPotential(), sample.precipitationIntensity())
                    .putVec4(i, "windFront", sample.windXBlocksPerSecond(), sample.windZBlocksPerSecond(),
                            sample.frontStrength(), sample.valid() ? 1.0f : 0.0f);
        }
        target.upload(writer.buffer(), 0L);
    }

    private void uploadDomains(CloudProfile profile, int count) {
        if (count <= 0) return;
        Std430Writer writer = new Std430Writer(DOMAIN_LAYOUT, count);
        for (int i = 0; i < count; i++) {
            CloudDomainProfile domain = profile.domains().get(i);
            var family = domain.family();
            writer.putVec4(i, "envelope",
                            domain.minimumAltitudeBlocks(), domain.maximumAltitudeBlocks(),
                            domain.meanBaseAltitudeBlocks(), domain.baseVariationBlocks())
                    .putVec4(i, "development",
                            domain.meanThicknessBlocks(), domain.thicknessVariationBlocks(),
                            domain.convectiveThicknessBoostBlocks(), domain.densityScale())
                    .putVec4(i, "scaleShape",
                            domain.macroScaleBlocks(), domain.detailScaleBlocks(),
                            family.erosionStrength(), domain.anisotropy())
                    .putVec4(i, "weatherOptics",
                            domain.humidityResponse(), domain.stormResponse(),
                            domain.frontResponse(), domain.extinctionPerBlock())
                    .putVec4(i, "coverageShape",
                            domain.coverageBias(), domain.clearCoverageThreshold(),
                            domain.overcastCoverageThreshold(), 0.0f)
                    .putVec4(i, "scatteringPolicy",
                            domain.singleScatteringAlbedo(), domain.multiScatteringEnergy(),
                            domain.multiScatteringExtinctionFactor(), domain.multiScatteringAnisotropyFactor())
                    .putVec4(i, "windPolicy",
                            domain.baseWindMultiplier(), domain.topWindMultiplier(),
                            domain.windShear(), domain.detailAdvectionMultiplier())
                    .putVec4(i, "familyShape",
                            family.bottomProfileExponent(), family.topProfileExponent(),
                            family.verticalDevelopment(), family.anvilTendency())
                    .putVec4(i, "domainPolicy",
                            domain.group().gpuCode(), family.horizontalScaleMultiplier(),
                            family.verticalScaleMultiplier(), 0.0f)
                    .putVec4(i, "densityPolicy",
                            domain.coarseDensityThreshold(), domain.lightingDetailFraction(),
                            family.detailStrength(), 0.0f);
        }
        domainData.upload(writer.buffer(), 0L);
    }

    private void ensureOwner(CombatantRhi rhi) {
        if (owner == rhi) return;
        closeOwned();
        owner = rhi;
        uploadedFrameId = Long.MIN_VALUE;
        uploadedFrame = FrameData.EMPTY;
    }

    private void ensureBuffers() {
        if (owner == null) throw new IllegalStateException("Cloud field has no RHI owner");
        if (weatherData == null) weatherData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-weather-data", WEATHER_LAYOUT, config.maxWeatherSamples(), StorageAccess.READ_ONLY, false
        ));
        if (macroWeatherData == null) macroWeatherData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-macro-weather-data", WEATHER_LAYOUT, config.maxMacroWeatherSamples(), StorageAccess.READ_ONLY, false
        ));
        if (domainData == null) domainData = owner.advancedShaders().createStorageBuffer(new StorageBufferDescriptor(
                "combatant-cloud-domain-data", DOMAIN_LAYOUT, config.maxDomains(), StorageAccess.READ_ONLY, false
        ));
    }

    private void closeOwned() {
        close(weatherData); weatherData = null;
        close(macroWeatherData); macroWeatherData = null;
        close(domainData); domainData = null;
        uploadedFrameId = Long.MIN_VALUE;
        uploadedFrame = FrameData.EMPTY;
    }

    @Override
    public void close() {
        closeOwned();
        owner = null;
    }

    private static void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); } catch (Throwable ignored) { }
    }

    record FrameData(
            CloudProfile profile,
            WeatherState weather,
            WeatherFieldState field,
            WeatherFieldState macroField,
            int weatherCount,
            int macroWeatherCount,
            int domainCount,
            boolean active
    ) {
        static final FrameData EMPTY = new FrameData(
                CloudProfile.NONE, WeatherState.NONE, WeatherFieldState.EMPTY, WeatherFieldState.EMPTY,
                0, 0, 0, false
        );
    }
}
