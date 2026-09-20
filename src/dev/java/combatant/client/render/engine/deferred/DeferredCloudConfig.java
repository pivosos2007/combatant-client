/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Numerical quality policy for the renderer-owned volumetric cloud domain. */
record DeferredCloudConfig(
        boolean enabled,
        float renderScale,
        int primarySteps,
        int lightSteps,
        int multiScatteringOrders,
        float maxPrimaryStepBlocks,
        int maxDomains,
        int maxWeatherSamples,
        int maxMacroWeatherSamples,
        int occupancyWidth,
        int occupancyHeight,
        int occupancyDepth,
        int occupancyMipLevels,
        boolean temporalEnabled,
        float temporalHistoryWeight,
        float temporalDepthThresholdFraction,
        float temporalMinDepthThresholdBlocks,
        int shadowMapResolution,
        float shadowMapSpanBlocks,
        int shadowSteps,
        int shadowAltitudeSlices
) {
    private static final DeferredCloudConfig DEFAULT = new DeferredCloudConfig(
            true, 0.5f, 48, 4, 3, 8.0f, 8, 256, 512,
            64, 32, 64, 7,
            true, 0.92f, 0.08f, 12.0f,
            256, 2048.0f, 56, 8
    );

    DeferredCloudConfig {
        renderScale = clamp(renderScale, 0.125f, 1.0f);
        primarySteps = clamp(primarySteps, 8, 192);
        lightSteps = clamp(lightSteps, 0, 8);
        multiScatteringOrders = clamp(multiScatteringOrders, 1, 4);
        maxPrimaryStepBlocks = clamp(maxPrimaryStepBlocks, 0.5f, 64.0f);
        maxDomains = clamp(maxDomains, 1, 8);
        maxWeatherSamples = clamp(maxWeatherSamples, 4, 1024);
        maxMacroWeatherSamples = clamp(maxMacroWeatherSamples, 4, 2048);
        occupancyWidth = clamp(occupancyWidth, 16, 128);
        occupancyHeight = clamp(occupancyHeight, 8, 96);
        occupancyDepth = clamp(occupancyDepth, 16, 128);
        int maximumOccupancyMips = 32 - Integer.numberOfLeadingZeros(Math.max(occupancyWidth, Math.max(occupancyHeight, occupancyDepth)));
        occupancyMipLevels = clamp(occupancyMipLevels, 1, maximumOccupancyMips);
        temporalHistoryWeight = clamp(temporalHistoryWeight, 0.0f, 0.98f);
        temporalDepthThresholdFraction = clamp(temporalDepthThresholdFraction, 0.001f, 0.5f);
        temporalMinDepthThresholdBlocks = clamp(temporalMinDepthThresholdBlocks, 0.25f, 256.0f);
        shadowMapResolution = clamp(shadowMapResolution, 32, 1024);
        shadowMapSpanBlocks = clamp(shadowMapSpanBlocks, 128.0f, 16384.0f);
        shadowSteps = clamp(shadowSteps, 8, 128);
        shadowAltitudeSlices = clamp(shadowAltitudeSlices, 2, 16);
    }

    static DeferredCloudConfig current() {
        return DEFAULT;
    }

    int width(int fullWidth) {
        return Math.max(1, Math.round(Math.max(1, fullWidth) * renderScale));
    }

    int height(int fullHeight) {
        return Math.max(1, Math.round(Math.max(1, fullHeight) * renderScale));
    }

    float shadowTexelBlocks() {
        return shadowMapSpanBlocks / Math.max(1, shadowMapResolution);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
