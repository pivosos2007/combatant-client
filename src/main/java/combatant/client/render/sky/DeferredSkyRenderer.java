/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuSampler;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.engine.pipeline.EffectRenderPipelines;
import combatant.client.render.engine.renderer.FullScreenRenderer;
import combatant.client.render.engine.uniform.impl.DeferredSkyUniforms;
import combatant.client.render.engine.world.environment.*;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.util.logging.DebugLog;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/**
 * Standalone production sky renderer. Sky radiance and volumetric clouds are calculated
 * into independent HDR targets, clouds are accumulated in motion-reprojected history,
 * and only the final resolved sky is written to the vanilla world color target.
 *
 * No terrain lighting/shadow ownership is transferred from the existing world pipeline.
 * All textures belong to this renderer and are invalidated on dimension, resize,
 * quality change, large camera discontinuity, or renderer backend release.
 */
public enum DeferredSkyRenderer {
    ;
    private static TextureTarget atmosphere;
    private static TextureTarget clouds;
    private static final TextureTarget[] cloudHistory = new TextureTarget[2];
    private static int atmosphereWidth, atmosphereHeight, cloudWidth, cloudHeight;
    private static int historyWriteIndex;
    private static int previousQuality = -1;
    private static int previousDimension = -1;
    private static boolean hasHistory;
    private static boolean available = true;
    private static ClientLevel ownerLevel;
    private static long renderedFrames;
    private static final float[] VIEW = new float[4];
    private static final float[] ROTATION = new float[4];
    private static final float[] SUN = new float[4];
    private static final float[] CLIMATE = new float[4];
    private static final float[] CAMERA = new float[4];
    private static final float[] SKY = new float[4];
    private static final float[] RAYLEIGH = new float[4];
    private static final float[] MIE = new float[4];
    private static final float[] QUALITY = new float[4];
    private static final float[] LAYER = new float[4];
    private static final float[] SHAPE = new float[4];
    private static final float[] LIGHTING = new float[4];
    private static final float[] WEATHER = new float[4];
    private static final float[] CIRRUS = new float[4];
    private static final float[] PREVIOUS_CAMERA = new float[4];
    private static final float[] PREVIOUS_VIEW = new float[4];
    private static final float[] TEMPORAL = new float[4];
    private static final float[] MOON = new float[4];
    private static final float[] OPTICS = new float[4];
    private static final float[] OZONE = new float[4];
    private static final float[] STRATUS = new float[4];
    private static final float[] SUN_COLOR = new float[4];
    private static final float[] SUN_VISUAL = new float[4];
    private static final float[] MOON_COLOR = new float[4];
    private static final float[] MOON_VISUAL = new float[4];
    private static final float[] LAST_CLIMATE = new float[4];
    private static final float[] LAST_SUN = new float[4];

    public static void render(Minecraft minecraft, Camera camera, SkyRenderState state,
                              ReimaginedVisual options, RenderTarget destination,
                              GpuBufferSlice fog, float partialTick, Matrix4f projection) {
        if (minecraft == null || minecraft.level == null || camera == null
                || state == null || options == null || destination == null || fog == null
                || !options.isShaderSkyboxEnabled() || IrisRuntime.isShaderpackRendererActive()) return;
        if (destination.width <= 0 || destination.height <= 0) return;
        // @AssetLoad owns lunar residency; do not open/register textures in render().
        SkyCelestialAssets.LunarMaps lunarMaps = SkyCelestialAssets.lunarMaps();
        if (lunarMaps == null) return; // Preserve the already-rendered vanilla sky.
        if (ownerLevel != minecraft.level) {
            close();
            ownerLevel = minecraft.level;
        }
        if (!available) return;
        try {
            int quality = options.getSkyCloudQuality();
            if (!ensureTargets(destination.width, destination.height, quality)) return;
            populateUniforms(minecraft.level, camera, state, options, partialTick,
                    destination.width, destination.height, projection, quality);
            GpuBufferSlice skyData = DeferredSkyUniforms.get();
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

            FullScreenRenderer.begin("Combatant Sky / HDR Atmosphere")
                    .attachment(atmosphere)
                    .pipeline(EffectRenderPipelines.WORLD_SKY_ATMOSPHERE)
                    .uniform("DeferredSky", skyData).end();
            FullScreenRenderer.begin("Combatant Sky / Low-resolution Cloud Raymarch")
                    .attachment(clouds)
                    .pipeline(EffectRenderPipelines.WORLD_SKY_CLOUDS)
                    .uniform("DeferredSky", skyData).end();

            TextureTarget historyDestination = cloudHistory[historyWriteIndex];
            TextureTarget historySource = cloudHistory[1 - historyWriteIndex];
            FullScreenRenderer.begin("Combatant Sky / Motion-reprojected Cloud Resolve")
                    .attachment(historyDestination)
                    .pipeline(EffectRenderPipelines.WORLD_SKY_CLOUD_TEMPORAL)
                    .uniform("DeferredSky", skyData)
                    .sampler("u_CurrentClouds", clouds.getColorTextureView(), sampler)
                    .sampler("u_CloudHistory", hasHistory ? historySource.getColorTextureView()
                            : clouds.getColorTextureView(), sampler)
                    .end();

            FullScreenRenderer.begin("Combatant Sky / HDR Composite")
                    .attachment(destination)
                    .pipeline(EffectRenderPipelines.WORLD_SKY_COMPOSITE)
                    .uniform("DeferredSky", skyData)
                    .sampler("u_Atmosphere", atmosphere.getColorTextureView(), sampler)
                    .sampler("u_Clouds", historyDestination.getColorTextureView(), sampler)
                    .sampler("u_LunarAlbedo", lunarMaps.albedoView(), lunarMaps.albedoSampler())
                    .sampler("u_LunarNormal", lunarMaps.normalView(), lunarMaps.normalSampler())
                    .end();
            historyWriteIndex = 1 - historyWriteIndex;
            hasHistory = true;
            System.arraycopy(CAMERA, 0, PREVIOUS_CAMERA, 0, 4);
            PREVIOUS_VIEW[0] = ROTATION[0];
            PREVIOUS_VIEW[1] = ROTATION[1];
            PREVIOUS_VIEW[2] = VIEW[2];
            PREVIOUS_VIEW[3] = VIEW[3];
            System.arraycopy(CLIMATE, 0, LAST_CLIMATE, 0, 4);
            System.arraycopy(SUN, 0, LAST_SUN, 0, 4);
            renderedFrames++;
        } catch (RuntimeException | LinkageError failure) {
            close();
            available = false;
            DebugLog.warnOnce("reimagined-deferred-sky-fallback",
                    "Deferred sky pass failed; keeping the already-rendered vanilla sky", failure);
        }
    }

    private static boolean ensureTargets(int width, int height, int quality) {
        int aw = Math.max(1, (width + 2) / 3);
        int ah = Math.max(1, (height + 2) / 3);
        float fraction = quality <= 1 ? 0.33f : quality >= 3 ? 0.60f : 0.50f;
        int cw = Math.max(1, Math.round(width * fraction));
        int ch = Math.max(1, Math.round(height * fraction));
        boolean invalidated = atmosphere == null || clouds == null || cloudHistory[0] == null
                || cloudHistory[1] == null || previousQuality != quality
                || aw != atmosphereWidth || ah != atmosphereHeight
                || cw != cloudWidth || ch != cloudHeight;
        if (invalidated) {
            destroyTargets();
            atmosphere = new TextureTarget("combatant-sky-atmosphere", aw, ah, GpuFormat.RGBA16_FLOAT, null);
            clouds = new TextureTarget("combatant-sky-cloud-raw", cw, ch, GpuFormat.RGBA16_FLOAT, null);
            cloudHistory[0] = new TextureTarget("combatant-sky-cloud-history-0", cw, ch, GpuFormat.RGBA16_FLOAT, null);
            cloudHistory[1] = new TextureTarget("combatant-sky-cloud-history-1", cw, ch, GpuFormat.RGBA16_FLOAT, null);
            atmosphereWidth = aw;
            atmosphereHeight = ah;
            cloudWidth = cw;
            cloudHeight = ch;
            previousQuality = quality;
            hasHistory = false;
            historyWriteIndex = 0;
        }
        return atmosphere.getColorTextureView() != null && clouds.getColorTextureView() != null
                && cloudHistory[0].getColorTextureView() != null
                && cloudHistory[1].getColorTextureView() != null;
    }

    private static void populateUniforms(ClientLevel level, Camera camera, SkyRenderState sky,
                                         ReimaginedVisual options, float partialTick,
                                         int width, int height, Matrix4f projection, int quality) {
        float m11 = projection != null ? Math.abs(projection.m11()) : 0.0f;
        float fov = m11 > 0.001f && Float.isFinite(m11)
                ? (float) (2.0 * Math.atan(1.0 / m11)) : (float) Math.toRadians(70.0);
        VIEW[0] = width;
        VIEW[1] = height;
        VIEW[2] = fov;
        VIEW[3] = width / (float) height;
        ROTATION[0] = (float) Math.toRadians(-camera.yRot());
        ROTATION[1] = (float) Math.toRadians(camera.xRot());
        int dimension = level.dimension().equals(Level.OVERWORLD) ? 0 :
                level.dimension().equals(Level.END) ? 1 :
                level.dimension().equals(Level.NETHER) ? 2 : 3;
        ROTATION[3] = dimension;
        if (previousDimension != dimension) {
            hasHistory = false;
            previousDimension = dimension;
        }

        DimensionRenderProfile profile = DimensionRenderProfileRegistry.resolve(level.dimension());
        AtmosphereState optical = OverworldAtmosphereModel.capture();
        if (dimension == 0 && !profile.environmentModel().equals(OverworldAtmosphereModel.ID)) {
            optical = AtmosphereState.NONE;
        }
        CelestialRenderProfile celestialProfile = CelestialRenderProfileRegistry.resolve(
                DimensionRenderProfileRegistry.OVERWORLD_CELESTIAL);
        CelestialState celestial = dimension == 0
                ? OverworldCelestialModel.INSTANCE.capture(new EnvironmentCaptureContext(
                level, camera, camera.position(), partialTick, BiomeClimateState.EMPTY,
                WeatherState.NONE, Long.MIN_VALUE)) : CelestialState.NONE;
        float solarAngle = celestial.valid() ? celestial.sunAngleRadians() : sky.sunAngle;
        SUN[0] = -(float) Math.sin(solarAngle);
        SUN[1] = (float) Math.cos(solarAngle);
        SUN[2] = 0.0f;
        SUN[3] = celestial.valid() ? celestial.starBrightness() : 0.0f;
        float moonAngle = celestial.valid() ? celestial.moonAngleRadians() : solarAngle + (float) Math.PI;
        MOON[0] = -(float) Math.sin(moonAngle);
        MOON[1] = (float) Math.cos(moonAngle);
        MOON[2] = 0.0f;
        // Appearance-only disk radius. IterEclipse uses a substantially larger
        // photoreal moon than the true 0.25-degree lunar angular radius.
        // World lighting and shadow geometry keep their physical descriptors.
        MOON[3] = Math.max(celestial.valid() ? celestial.moon().angularRadiusRadians() : 0.00436f,
                0.025f);
        ROTATION[2] = celestial.valid() ? celestial.moonPhase() : 0.0f;
        // Sky appearance consumes the same celestial producer parameters as the
        // world-lighting pipeline. Brightness multipliers remain sky-specific.
        SUN_COLOR[0] = celestial.valid() ? celestial.sun().radianceRed() : 1.0f;
        SUN_COLOR[1] = celestial.valid() ? celestial.sun().radianceGreen() : 1.0f;
        SUN_COLOR[2] = celestial.valid() ? celestial.sun().radianceBlue() : 1.0f;
        SUN_COLOR[3] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.starIntensity() : 0.018f;
        SUN_VISUAL[0] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.sunDiskRadianceScale() : 1.0f;
        SUN_VISUAL[1] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.sunLimbDarkening() : 0.52f;
        SUN_VISUAL[2] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.starDensity() : 0.018f;
        SUN_VISUAL[3] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.starSize() : 0.105f;
        MOON_COLOR[0] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonRadianceRed() : 0.002f;
        MOON_COLOR[1] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonRadianceGreen() : 0.0021f;
        MOON_COLOR[2] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonRadianceBlue() : 0.0024f;
        MOON_COLOR[3] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonDiskRadianceScale() : 1.0f;
        MOON_VISUAL[0] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonEarthshine() : 0.035f;
        MOON_VISUAL[1] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.moonSurfacePhaseExponent() : 0.85f;
        MOON_VISUAL[2] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.starHorizonFadeStart() : 0.015f;
        MOON_VISUAL[3] = celestialProfile != null && celestialProfile.valid()
                ? celestialProfile.starHorizonFadeEnd() : 0.1f;

        CLIMATE[0] = dimension == 0 ? level.getRainLevel(partialTick) : 0.0f;
        CLIMATE[1] = dimension == 0 ? level.getThunderLevel(partialTick) : 0.0f;
        CLIMATE[2] = options.getSkyCloudCoverage();
        CLIMATE[3] = options.getSkyWindSpeed();
        CAMERA[0] = (float) camera.position().x;
        CAMERA[1] = (float) camera.position().y;
        CAMERA[2] = (float) camera.position().z;
        CAMERA[3] = level.getGameTime() / 20.0f + partialTick / 20.0f;
        SKY[0] = sky.skyColor.x();
        SKY[1] = sky.skyColor.y();
        SKY[2] = sky.skyColor.z();
        SKY[3] = options.getSkyStars();
        RAYLEIGH[0] = optical.valid() ? optical.rayleighRed() : 0.005802f;
        RAYLEIGH[1] = optical.valid() ? optical.rayleighGreen() : 0.013558f;
        RAYLEIGH[2] = optical.valid() ? optical.rayleighBlue() : 0.033100f;
        RAYLEIGH[3] = optical.valid() ? optical.rayleighScaleHeightKm() : 8.0f;
        float weatherMultiplier = 1.0f + CLIMATE[0] * 0.30f + CLIMATE[1] * 0.25f;
        MIE[0] = (optical.valid() ? optical.mieScatteringRed() : 0.003996f) * weatherMultiplier;
        MIE[1] = (optical.valid() ? optical.mieScatteringGreen() : 0.003996f) * weatherMultiplier;
        MIE[2] = (optical.valid() ? optical.mieScatteringBlue() : 0.003996f) * weatherMultiplier;
        MIE[3] = optical.valid() ? optical.mieScaleHeightKm() : 1.2f;
        OPTICS[0] = optical.planetRadiusKm();
        OPTICS[1] = optical.atmosphereRadiusKm();
        OPTICS[2] = optical.valid() ? optical.mieAnisotropy() : 0.76f;
        OPTICS[3] = optical.valid() ? optical.ozoneCenterKm() : 25.0f;
        OZONE[0] = optical.valid() ? optical.ozoneAbsorptionRed() : 0.00065f;
        OZONE[1] = optical.valid() ? optical.ozoneAbsorptionGreen() : 0.00188f;
        OZONE[2] = optical.valid() ? optical.ozoneAbsorptionBlue() : 0.00008f;
        OZONE[3] = optical.valid() ? optical.ozoneWidthKm() : 15.0f;
        QUALITY[0] = quality <= 1 ? 12.0f : quality >= 3 ? 32.0f : 20.0f;
        QUALITY[1] = quality <= 1 ? 0.33f : quality >= 3 ? 0.60f : 0.50f;
        QUALITY[2] = Math.max(celestial.valid()
                ? celestial.sun().angularRadiusRadians() : 0.00465f, 0.0085f);
        QUALITY[3] = 0.0f;
        CloudProfile cloudProfile = CloudProfileRegistry.resolve(profile.cloudProfile());
        if (dimension == 0 && cloudProfile.valid()) {
            // Registered profiles are the sole numerical source of shape and lighting.
            CloudDomainProfile low = cloudProfile.domains().get(Math.min(1, cloudProfile.domains().size() - 1));
            LAYER[0] = low.meanBaseAltitudeBlocks();
            LAYER[1] = Math.min(low.maximumAltitudeBlocks(),
                    LAYER[0] + low.meanThicknessBlocks() + low.convectiveThicknessBoostBlocks() * CLIMATE[1] * 0.7f);
            LAYER[2] = low.densityScale();
            LAYER[3] = low.coverageBias();
            SHAPE[0] = 1.0f / low.macroScaleBlocks();
            SHAPE[1] = 1.0f / low.detailScaleBlocks();
            SHAPE[2] = low.extinctionPerBlock();
            SHAPE[3] = cloudProfile.maxRayDistanceBlocks();
            LIGHTING[0] = low.anisotropy();
            LIGHTING[1] = low.singleScatteringAlbedo();
            LIGHTING[2] = low.multiScatteringEnergy();
            LIGHTING[3] = low.coarseDensityThreshold();
            WEATHER[0] = low.baseVariationBlocks();
            WEATHER[1] = low.thicknessVariationBlocks();
            WEATHER[2] = low.stormResponse();
            WEATHER[3] = low.convectiveThicknessBoostBlocks();
            CloudDomainProfile flat = cloudProfile.domains().get(0);
            STRATUS[0] = flat.meanBaseAltitudeBlocks();
            STRATUS[1] = flat.meanThicknessBlocks();
            STRATUS[2] = flat.densityScale();
            STRATUS[3] = flat.coverageBias();
            CloudDomainProfile high = cloudProfile.domains().get(Math.min(2, cloudProfile.domains().size() - 1));
            CIRRUS[0] = high.meanBaseAltitudeBlocks();
            CIRRUS[1] = high.densityScale();
            CIRRUS[2] = 1.0f / high.macroScaleBlocks();
            CIRRUS[3] = 1.0f / high.detailScaleBlocks();
        } else {
            LAYER[0] = 140.0f;
            LAYER[1] = 262.0f;
            LAYER[2] = 0.0f;
            LAYER[3] = 0.0f;
            SHAPE[0] = 0.0033f;
            SHAPE[1] = 0.015f;
            SHAPE[2] = 0.032f;
            SHAPE[3] = 2000.0f;
            LIGHTING[0] = 0.48f;
            LIGHTING[1] = 0.98f;
            LIGHTING[2] = 0.55f;
            LIGHTING[3] = 0.02f;
            WEATHER[0] = WEATHER[1] = WEATHER[2] = WEATHER[3] = 0.0f;
            CIRRUS[0] = 650.0f;
            CIRRUS[1] = 0.0f;
            CIRRUS[2] = 0.0007f;
            CIRRUS[3] = 0.005f;
            STRATUS[0] = STRATUS[1] = STRATUS[2] = STRATUS[3] = 0.0f;
        }
        // Discard stale history after teleports, projection jumps, weather edits, or time skips.
        float dx = CAMERA[0] - PREVIOUS_CAMERA[0];
        float dy = CAMERA[1] - PREVIOUS_CAMERA[1];
        float dz = CAMERA[2] - PREVIOUS_CAMERA[2];
        float dt = CAMERA[3] - PREVIOUS_CAMERA[3];
        boolean valid = hasHistory && dimension == 0 && dt >= -0.01f && dt < 0.5f
                && dx * dx + dy * dy + dz * dz < 24.0f * 24.0f
                && Math.abs(PREVIOUS_VIEW[2] - VIEW[2]) < 0.01f
                && Math.abs(PREVIOUS_VIEW[3] - VIEW[3]) < 0.01f
                && Math.abs(LAST_CLIMATE[0] - CLIMATE[0]) < 0.03f
                && Math.abs(LAST_CLIMATE[1] - CLIMATE[1]) < 0.03f
                && Math.abs(LAST_CLIMATE[2] - CLIMATE[2]) < 0.015f
                && Math.abs(LAST_CLIMATE[3] - CLIMATE[3]) < 0.015f
                && Math.abs(LAST_SUN[1] - SUN[1]) < 0.012f;
        TEMPORAL[0] = valid ? 1.0f : 0.0f;
        TEMPORAL[1] = (float) (renderedFrames % 256);
        TEMPORAL[2] = quality <= 1 ? 0.82f : quality >= 3 ? 0.91f : 0.89f;
        TEMPORAL[3] = 0.0f;
        DeferredSkyUniforms.update(VIEW, ROTATION, SUN, CLIMATE, CAMERA, SKY,
                RAYLEIGH, MIE, QUALITY, LAYER, SHAPE, LIGHTING, WEATHER, CIRRUS,
                PREVIOUS_CAMERA, PREVIOUS_VIEW, TEMPORAL, MOON, OPTICS, OZONE, STRATUS,
                SUN_COLOR, SUN_VISUAL, MOON_COLOR, MOON_VISUAL);
    }

    public static boolean hasWorkingTargets() {
        return available && atmosphere != null && clouds != null && cloudHistory[0] != null
                && cloudHistory[1] != null && Minecraft.getInstance().level == ownerLevel;
    }

    private static void destroyTargets() {
        if (atmosphere != null) { atmosphere.destroyBuffers(); atmosphere = null; }
        if (clouds != null) { clouds.destroyBuffers(); clouds = null; }
        for (int i = 0; i < cloudHistory.length; i++) {
            if (cloudHistory[i] != null) {
                cloudHistory[i].destroyBuffers();
                cloudHistory[i] = null;
            }
        }
        atmosphereWidth = atmosphereHeight = cloudWidth = cloudHeight = 0;
        hasHistory = false;
        historyWriteIndex = 0;
    }

    public static void close() {
        destroyTargets();
        previousQuality = -1;
        previousDimension = -1;
        renderedFrames = 0;
        ownerLevel = null;
        available = true;
    }
}
