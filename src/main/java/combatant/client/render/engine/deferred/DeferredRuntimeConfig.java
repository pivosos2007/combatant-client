/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runtime backend policy for the deferred graph.
 *
 * <p>The settings UI is intentionally not part of this class. UI, presets and adaptive-quality
 * controllers publish one immutable snapshot; the world graph freezes that snapshot for the whole
 * frame. Runtime values therefore remain data/resource policy and do not become shader/pipeline
 * identity unless an immutable GPU contract actually changes.</p>
 */
public final class DeferredRuntimeConfig {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicReference<Snapshot> CURRENT = new AtomicReference<>(Snapshot.fromSystemProperties());

    private DeferredRuntimeConfig() {
    }

    public static Snapshot current() {
        return CURRENT.get();
    }

    public static long generation() {
        return GENERATION.get();
    }

    /** Publishes a fully validated runtime snapshot. Returns its generation. */
    public static long apply(Snapshot snapshot) {
        Snapshot next = Objects.requireNonNull(snapshot, "snapshot").validated();
        Snapshot previous = CURRENT.getAndSet(next);
        if (previous.equals(next)) return GENERATION.get();
        return GENERATION.incrementAndGet();
    }

    public static Builder builder() {
        return new Builder(current());
    }

    /** Atomically edits the current policy without exposing partially-updated fields to a frame. */
    public static long update(Consumer<Builder> editor) {
        if (editor == null) return generation();
        Builder builder = builder();
        editor.accept(builder);
        return apply(builder.build());
    }

    /** Re-reads optional JVM defaults; properties are developer defaults, not the public settings API. */
    public static long reloadSystemDefaults() {
        return apply(Snapshot.fromSystemProperties());
    }

    public record Snapshot(
            boolean ambientOcclusionEnabled,
            float ambientOcclusionScale,
            int ambientOcclusionSampleCount,
            float ambientOcclusionRadius,
            float ambientOcclusionThickness,
            int ambientOcclusionMaxMip,
            float ambientOcclusionMipBias,
            boolean indirectLightEnabled,
            float indirectLightScale,
            int indirectLightSampleCount,
            float indirectLightRadius,
            float indirectLightBias,
            int indirectLightMaxMip,
            float indirectLightMipBias,
            boolean reflectionsEnabled,
            float reflectionTraceScale,
            float reflectionOutputScale,
            float reflectionHistoryScale,
            int reflectionTraceMaxSteps,
            float reflectionTraceMaxDistanceScale,
            float reflectionTraceMinStep,
            float reflectionTraceDepthStepScale,
            float reflectionTraceStepGrowth,
            float reflectionTraceThickness,
            float reflectionTraceNormalBias,
            float reflectionTraceEdgeMargin,
            float reflectionTraceMipStepScale,
            float reflectionScreenConfidenceThreshold,
            float reflectionCascadeConfidence,
            int reflectionCascadeCount,
            int reflectionCascadeFaceResolution,
            int reflectionCascadeUpdateIntervalFrames,
            float reflectionCascadeDistanceScale,
            boolean shadowsEnabled,
            float shadowOutputScale,
            boolean contactShadowsEnabled,
            float contactShadowScale,
            int contactShadowMaxSteps,
            float contactShadowMaxDistance,
            float contactShadowMinStep,
            float contactShadowThickness,
            float contactShadowNormalBias,
            float contactShadowEdgeMargin,
            float contactShadowMipStepScale,
            float contactShadowStepGrowth,
            int shadowCascadeCount,
            int shadowResolution,
            float shadowSplitLambda,
            float shadowCasterDistance,
            float shadowCascadeBlendFraction,
            float shadowNormalOffsetTexels,
            float shadowReceiverBiasTexels,
            float shadowFilterRadiusTexels,
            float shadowBlockerSearchRadiusTexels,
            float shadowPenumbraScaleTexels,
            float shadowMaxPenumbraTexels,
            int depthPyramidMaxMipLevels,
            boolean indirectTemporalEnabled,
            float indirectTemporalHistoryWeight,
            float indirectTemporalDepthThreshold,
            boolean reflectionTemporalEnabled,
            float reflectionTemporalHistoryWeight,
            float reflectionTemporalDepthThreshold,
            boolean reflectionDenoiseEnabled,
            int reflectionDenoiseRadius,
            float reflectionDenoiseDepthThreshold,
            float reflectionDenoiseNormalThreshold,
            boolean coloredBlockLightEnabled,
            boolean dynamicLightsEnabled,
            boolean participatingMediaEnabled,
            boolean waterEnabled
    ) {
        public static Snapshot defaults() {
            return new Snapshot(
                    true,
                    0.5f,
                    12,
                    1.5f,
                    0.025f,
                    4,
                    0.0f,
                    true,
                    0.5f,
                    12,
                    6.0f,
                    0.05f,
                    4,
                    0.0f,
                    true,
                    0.5f,
                    0.5f,
                    0.5f,
                    64,
                    1.0f,
                    0.10f,
                    0.004f,
                    1.08f,
                    0.0002f,
                    0.03f,
                    0.001f,
                    0.5f,
                    0.75f,
                    0.35f,
                    2,
                    256,
                    1,
                    1.0f,
                    true,
                    1.0f,
                    false,
                    0.5f,
                    24,
                    16.0f,
                    0.08f,
                    0.08f,
                    0.03f,
                    0.002f,
                    0.5f,
                    1.15f,
                    4,
                    2048,
                    0.65f,
                    64.0f,
                    0.15f,
                    0.0f,
                    0.75f,
                    1.5f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0,
                    true,
                    0.9f,
                    0.01f,
                    true,
                    0.9f,
                    0.01f,
                    true,
                    2,
                    0.02f,
                    0.85f,
                    true,
                    true,
                    true,
                    true
            );
        }

        public static Snapshot fromSystemProperties() {
            Snapshot d = defaults();
            return new Snapshot(
                    booleanProperty("combatant.render.deferred.ao", d.ambientOcclusionEnabled),
                    floatProperty("combatant.render.deferred.aoScale", d.ambientOcclusionScale),
                    intProperty("combatant.render.deferred.aoSamples", d.ambientOcclusionSampleCount),
                    floatProperty("combatant.render.deferred.aoRadius", d.ambientOcclusionRadius),
                    floatProperty("combatant.render.deferred.aoThickness",
                            floatProperty("combatant.render.deferred.aoBias", d.ambientOcclusionThickness)),
                    intProperty("combatant.render.deferred.aoMaxMip", d.ambientOcclusionMaxMip),
                    floatProperty("combatant.render.deferred.aoMipBias", d.ambientOcclusionMipBias),
                    booleanProperty("combatant.render.deferred.indirect", d.indirectLightEnabled),
                    floatProperty("combatant.render.deferred.indirectScale", d.indirectLightScale),
                    intProperty("combatant.render.deferred.indirectSamples", d.indirectLightSampleCount),
                    floatProperty("combatant.render.deferred.indirectRadius", d.indirectLightRadius),
                    floatProperty("combatant.render.deferred.indirectBias", d.indirectLightBias),
                    intProperty("combatant.render.deferred.indirectMaxMip", d.indirectLightMaxMip),
                    floatProperty("combatant.render.deferred.indirectMipBias", d.indirectLightMipBias),
                    booleanProperty("combatant.render.deferred.reflections", d.reflectionsEnabled),
                    floatProperty("combatant.render.deferred.reflectionTraceScale", d.reflectionTraceScale),
                    floatProperty("combatant.render.deferred.reflectionOutputScale", d.reflectionOutputScale),
                    floatProperty("combatant.render.deferred.reflectionHistoryScale", d.reflectionHistoryScale),
                    intProperty("combatant.render.deferred.reflectionTraceMaxSteps", d.reflectionTraceMaxSteps),
                    floatProperty("combatant.render.deferred.reflectionTraceMaxDistanceScale", d.reflectionTraceMaxDistanceScale),
                    floatProperty("combatant.render.deferred.reflectionTraceMinStep", d.reflectionTraceMinStep),
                    floatProperty("combatant.render.deferred.reflectionTraceDepthStepScale", d.reflectionTraceDepthStepScale),
                    floatProperty("combatant.render.deferred.reflectionTraceStepGrowth", d.reflectionTraceStepGrowth),
                    floatProperty("combatant.render.deferred.reflectionTraceThickness", d.reflectionTraceThickness),
                    floatProperty("combatant.render.deferred.reflectionTraceNormalBias", d.reflectionTraceNormalBias),
                    floatProperty("combatant.render.deferred.reflectionTraceEdgeMargin", d.reflectionTraceEdgeMargin),
                    floatProperty("combatant.render.deferred.reflectionTraceMipStepScale", d.reflectionTraceMipStepScale),
                    floatProperty("combatant.render.deferred.reflectionScreenConfidenceThreshold", d.reflectionScreenConfidenceThreshold),
                    floatProperty("combatant.render.deferred.reflectionCascadeConfidence", d.reflectionCascadeConfidence),
                    intProperty("combatant.render.deferred.reflectionCascades", d.reflectionCascadeCount),
                    intProperty("combatant.render.deferred.reflectionCascadeResolution", d.reflectionCascadeFaceResolution),
                    intProperty("combatant.render.deferred.reflectionCascadeUpdateInterval", d.reflectionCascadeUpdateIntervalFrames),
                    floatProperty("combatant.render.deferred.reflectionCascadeDistanceScale", d.reflectionCascadeDistanceScale),
                    booleanProperty("combatant.render.deferred.shadows", d.shadowsEnabled),
                    floatProperty("combatant.render.deferred.shadowOutputScale", d.shadowOutputScale),
                    booleanProperty("combatant.render.deferred.contactShadows", d.contactShadowsEnabled),
                    floatProperty("combatant.render.deferred.contactShadowScale", d.contactShadowScale),
                    intProperty("combatant.render.deferred.contactShadowMaxSteps", d.contactShadowMaxSteps),
                    floatProperty("combatant.render.deferred.contactShadowMaxDistance", d.contactShadowMaxDistance),
                    floatProperty("combatant.render.deferred.contactShadowMinStep", d.contactShadowMinStep),
                    floatProperty("combatant.render.deferred.contactShadowThickness", d.contactShadowThickness),
                    floatProperty("combatant.render.deferred.contactShadowNormalBias", d.contactShadowNormalBias),
                    floatProperty("combatant.render.deferred.contactShadowEdgeMargin", d.contactShadowEdgeMargin),
                    floatProperty("combatant.render.deferred.contactShadowMipStepScale", d.contactShadowMipStepScale),
                    floatProperty("combatant.render.deferred.contactShadowStepGrowth", d.contactShadowStepGrowth),
                    intProperty("combatant.render.deferred.shadowCascades", d.shadowCascadeCount),
                    intProperty("combatant.render.deferred.shadowResolution", d.shadowResolution),
                    floatProperty("combatant.render.deferred.shadowSplitLambda", d.shadowSplitLambda),
                    floatProperty("combatant.render.deferred.shadowCasterDistance", d.shadowCasterDistance),
                    floatProperty("combatant.render.deferred.shadowCascadeBlendFraction", d.shadowCascadeBlendFraction),
                    floatProperty("combatant.render.deferred.shadowNormalOffsetTexels", d.shadowNormalOffsetTexels),
                    floatProperty("combatant.render.deferred.shadowReceiverBiasTexels", d.shadowReceiverBiasTexels),
                    floatProperty("combatant.render.deferred.shadowFilterRadiusTexels", d.shadowFilterRadiusTexels),
                    floatProperty("combatant.render.deferred.shadowBlockerSearchRadiusTexels", d.shadowBlockerSearchRadiusTexels),
                    floatProperty("combatant.render.deferred.shadowPenumbraScaleTexels", d.shadowPenumbraScaleTexels),
                    floatProperty("combatant.render.deferred.shadowMaxPenumbraTexels", d.shadowMaxPenumbraTexels),
                    intProperty("combatant.render.deferred.depthPyramidMaxMipLevels", d.depthPyramidMaxMipLevels),
                    booleanProperty("combatant.render.deferred.indirectTemporal", d.indirectTemporalEnabled),
                    floatProperty("combatant.render.deferred.indirectTemporalHistoryWeight", d.indirectTemporalHistoryWeight),
                    floatProperty("combatant.render.deferred.indirectTemporalDepthThreshold", d.indirectTemporalDepthThreshold),
                    booleanProperty("combatant.render.deferred.reflectionTemporal", d.reflectionTemporalEnabled),
                    floatProperty("combatant.render.deferred.reflectionTemporalHistoryWeight", d.reflectionTemporalHistoryWeight),
                    floatProperty("combatant.render.deferred.reflectionTemporalDepthThreshold", d.reflectionTemporalDepthThreshold),
                    booleanProperty("combatant.render.deferred.reflectionDenoise", d.reflectionDenoiseEnabled),
                    intProperty("combatant.render.deferred.reflectionDenoiseRadius", d.reflectionDenoiseRadius),
                    floatProperty("combatant.render.deferred.reflectionDenoiseDepthThreshold", d.reflectionDenoiseDepthThreshold),
                    floatProperty("combatant.render.deferred.reflectionDenoiseNormalThreshold", d.reflectionDenoiseNormalThreshold),
                    booleanProperty("combatant.render.deferred.coloredBlockLight", d.coloredBlockLightEnabled),
                    booleanProperty("combatant.render.deferred.dynamicLights", d.dynamicLightsEnabled),
                    booleanProperty("combatant.render.deferred.participatingMedia", d.participatingMediaEnabled),
                    booleanProperty("combatant.render.deferred.water", d.waterEnabled)
            ).validated();
        }

        public Snapshot validated() {
            return new Snapshot(
                    ambientOcclusionEnabled,
                    clamp(ambientOcclusionScale, 0.125f, 1.0f),
                    clamp(ambientOcclusionSampleCount, 4, 64),
                    clamp(ambientOcclusionRadius, 0.05f, 16.0f),
                    clamp(ambientOcclusionThickness, 0.0f, 1.0f),
                    clamp(ambientOcclusionMaxMip, 0, 16),
                    clamp(ambientOcclusionMipBias, -4.0f, 4.0f),
                    indirectLightEnabled,
                    clamp(indirectLightScale, 0.125f, 1.0f),
                    clamp(indirectLightSampleCount, 4, 64),
                    clamp(indirectLightRadius, 0.25f, 64.0f),
                    clamp(indirectLightBias, 0.0f, 1.0f),
                    clamp(indirectLightMaxMip, 0, 16),
                    clamp(indirectLightMipBias, -4.0f, 4.0f),
                    reflectionsEnabled,
                    clamp(reflectionTraceScale, 0.125f, 1.0f),
                    clamp(reflectionOutputScale, 0.125f, 1.0f),
                    clamp(reflectionHistoryScale, 0.125f, 1.0f),
                    clamp(reflectionTraceMaxSteps, 8, 256),
                    clamp(reflectionTraceMaxDistanceScale, 0.05f, 2.0f),
                    clamp(reflectionTraceMinStep, 0.001f, 2.0f),
                    clamp(reflectionTraceDepthStepScale, 0.00001f, 0.1f),
                    clamp(reflectionTraceStepGrowth, 1.0f, 1.5f),
                    clamp(reflectionTraceThickness, 0.00001f, 0.02f),
                    clamp(reflectionTraceNormalBias, 0.0f, 0.5f),
                    clamp(reflectionTraceEdgeMargin, 0.0001f, 0.1f),
                    clamp(reflectionTraceMipStepScale, 0.01f, 4.0f),
                    clamp(reflectionScreenConfidenceThreshold, 0.0f, 1.0f),
                    clamp(reflectionCascadeConfidence, 0.0f, 1.0f),
                    clamp(reflectionCascadeCount, 0, DeferredReflectionCascadeSource.MAX_CASCADE_COUNT),
                    clamp(reflectionCascadeFaceResolution, 64, 2048),
                    clamp(reflectionCascadeUpdateIntervalFrames, 1, 240),
                    clamp(reflectionCascadeDistanceScale, 0.1f, 2.0f),
                    shadowsEnabled,
                    clamp(shadowOutputScale, 0.125f, 1.0f),
                    contactShadowsEnabled,
                    clamp(contactShadowScale, 0.125f, 1.0f),
                    clamp(contactShadowMaxSteps, 4, 128),
                    clamp(contactShadowMaxDistance, 0.25f, 128.0f),
                    clamp(contactShadowMinStep, 0.001f, 8.0f),
                    clamp(contactShadowThickness, 0.001f, 2.0f),
                    clamp(contactShadowNormalBias, 0.0f, 2.0f),
                    clamp(contactShadowEdgeMargin, 0.0001f, 0.1f),
                    clamp(contactShadowMipStepScale, 0.01f, 4.0f),
                    clamp(contactShadowStepGrowth, 1.0f, 2.0f),
                    clamp(shadowCascadeCount, 1, DeferredShadowCascadeSource.MAX_CASCADE_COUNT),
                    clamp(shadowResolution, 256, 8192),
                    clamp(shadowSplitLambda, 0.0f, 1.0f),
                    clamp(shadowCasterDistance, 0.0f, 1024.0f),
                    clamp(shadowCascadeBlendFraction, 0.0f, 0.35f),
                    clamp(shadowNormalOffsetTexels, 0.0f, 8.0f),
                    clamp(shadowReceiverBiasTexels, 0.0f, 8.0f),
                    clamp(shadowFilterRadiusTexels, 0.0f, 8.0f),
                    clamp(shadowBlockerSearchRadiusTexels, 0.0f, 16.0f),
                    clamp(shadowPenumbraScaleTexels, 0.0f, 128.0f),
                    clamp(shadowMaxPenumbraTexels, 0.0f, 32.0f),
                    clamp(depthPyramidMaxMipLevels, 0, 32),
                    indirectTemporalEnabled,
                    clamp(indirectTemporalHistoryWeight, 0.0f, 0.99f),
                    clamp(indirectTemporalDepthThreshold, 0.00001f, 0.25f),
                    reflectionTemporalEnabled,
                    clamp(reflectionTemporalHistoryWeight, 0.0f, 0.99f),
                    clamp(reflectionTemporalDepthThreshold, 0.00001f, 0.25f),
                    reflectionDenoiseEnabled,
                    clamp(reflectionDenoiseRadius, 0, 4),
                    clamp(reflectionDenoiseDepthThreshold, 0.00001f, 0.25f),
                    clamp(reflectionDenoiseNormalThreshold, 0.0f, 1.0f),
                    coloredBlockLightEnabled,
                    dynamicLightsEnabled,
                    participatingMediaEnabled,
                    waterEnabled
            );
        }

        private static boolean booleanProperty(String key, boolean fallback) {
            String value = System.getProperty(key);
            return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value.trim());
        }

        private static int intProperty(String key, int fallback) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static float floatProperty(String key, float fallback) {
            String value = System.getProperty(key);
            if (value == null || value.isBlank()) return fallback;
            try {
                return Float.parseFloat(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static float clamp(float value, float min, float max) {
            if (!Float.isFinite(value)) return min;
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class Builder {
        private boolean ambientOcclusionEnabled;
        private float ambientOcclusionScale;
        private int ambientOcclusionSampleCount;
        private float ambientOcclusionRadius;
        private float ambientOcclusionThickness;
        private int ambientOcclusionMaxMip;
        private float ambientOcclusionMipBias;
        private boolean indirectLightEnabled;
        private float indirectLightScale;
        private int indirectLightSampleCount;
        private float indirectLightRadius;
        private float indirectLightBias;
        private int indirectLightMaxMip;
        private float indirectLightMipBias;
        private boolean reflectionsEnabled;
        private float reflectionTraceScale;
        private float reflectionOutputScale;
        private float reflectionHistoryScale;
        private int reflectionTraceMaxSteps;
        private float reflectionTraceMaxDistanceScale;
        private float reflectionTraceMinStep;
        private float reflectionTraceDepthStepScale;
        private float reflectionTraceStepGrowth;
        private float reflectionTraceThickness;
        private float reflectionTraceNormalBias;
        private float reflectionTraceEdgeMargin;
        private float reflectionTraceMipStepScale;
        private float reflectionScreenConfidenceThreshold;
        private float reflectionCascadeConfidence;
        private int reflectionCascadeCount;
        private int reflectionCascadeFaceResolution;
        private int reflectionCascadeUpdateIntervalFrames;
        private float reflectionCascadeDistanceScale;
        private boolean shadowsEnabled;
        private float shadowOutputScale;
        private boolean contactShadowsEnabled;
        private float contactShadowScale;
        private int contactShadowMaxSteps;
        private float contactShadowMaxDistance;
        private float contactShadowMinStep;
        private float contactShadowThickness;
        private float contactShadowNormalBias;
        private float contactShadowEdgeMargin;
        private float contactShadowMipStepScale;
        private float contactShadowStepGrowth;
        private int shadowCascadeCount;
        private int shadowResolution;
        private float shadowSplitLambda;
        private float shadowCasterDistance;
        private float shadowCascadeBlendFraction;
        private float shadowNormalOffsetTexels;
        private float shadowReceiverBiasTexels;
        private float shadowFilterRadiusTexels;
        private float shadowBlockerSearchRadiusTexels;
        private float shadowPenumbraScaleTexels;
        private float shadowMaxPenumbraTexels;
        private int depthPyramidMaxMipLevels;
        private boolean indirectTemporalEnabled;
        private float indirectTemporalHistoryWeight;
        private float indirectTemporalDepthThreshold;
        private boolean reflectionTemporalEnabled;
        private float reflectionTemporalHistoryWeight;
        private float reflectionTemporalDepthThreshold;
        private boolean reflectionDenoiseEnabled;
        private int reflectionDenoiseRadius;
        private float reflectionDenoiseDepthThreshold;
        private float reflectionDenoiseNormalThreshold;
        private boolean coloredBlockLightEnabled;
        private boolean dynamicLightsEnabled;
        private boolean participatingMediaEnabled;
        private boolean waterEnabled;

        private Builder(Snapshot s) {
            if (s == null) s = Snapshot.defaults();
            ambientOcclusionEnabled = s.ambientOcclusionEnabled();
            ambientOcclusionScale = s.ambientOcclusionScale();
            ambientOcclusionSampleCount = s.ambientOcclusionSampleCount();
            ambientOcclusionRadius = s.ambientOcclusionRadius();
            ambientOcclusionThickness = s.ambientOcclusionThickness();
            ambientOcclusionMaxMip = s.ambientOcclusionMaxMip();
            ambientOcclusionMipBias = s.ambientOcclusionMipBias();
            indirectLightEnabled = s.indirectLightEnabled();
            indirectLightScale = s.indirectLightScale();
            indirectLightSampleCount = s.indirectLightSampleCount();
            indirectLightRadius = s.indirectLightRadius();
            indirectLightBias = s.indirectLightBias();
            indirectLightMaxMip = s.indirectLightMaxMip();
            indirectLightMipBias = s.indirectLightMipBias();
            reflectionsEnabled = s.reflectionsEnabled();
            reflectionTraceScale = s.reflectionTraceScale();
            reflectionOutputScale = s.reflectionOutputScale();
            reflectionHistoryScale = s.reflectionHistoryScale();
            reflectionTraceMaxSteps = s.reflectionTraceMaxSteps();
            reflectionTraceMaxDistanceScale = s.reflectionTraceMaxDistanceScale();
            reflectionTraceMinStep = s.reflectionTraceMinStep();
            reflectionTraceDepthStepScale = s.reflectionTraceDepthStepScale();
            reflectionTraceStepGrowth = s.reflectionTraceStepGrowth();
            reflectionTraceThickness = s.reflectionTraceThickness();
            reflectionTraceNormalBias = s.reflectionTraceNormalBias();
            reflectionTraceEdgeMargin = s.reflectionTraceEdgeMargin();
            reflectionTraceMipStepScale = s.reflectionTraceMipStepScale();
            reflectionScreenConfidenceThreshold = s.reflectionScreenConfidenceThreshold();
            reflectionCascadeConfidence = s.reflectionCascadeConfidence();
            reflectionCascadeCount = s.reflectionCascadeCount();
            reflectionCascadeFaceResolution = s.reflectionCascadeFaceResolution();
            reflectionCascadeUpdateIntervalFrames = s.reflectionCascadeUpdateIntervalFrames();
            reflectionCascadeDistanceScale = s.reflectionCascadeDistanceScale();
            shadowsEnabled = s.shadowsEnabled();
            shadowOutputScale = s.shadowOutputScale();
            contactShadowsEnabled = s.contactShadowsEnabled();
            contactShadowScale = s.contactShadowScale();
            contactShadowMaxSteps = s.contactShadowMaxSteps();
            contactShadowMaxDistance = s.contactShadowMaxDistance();
            contactShadowMinStep = s.contactShadowMinStep();
            contactShadowThickness = s.contactShadowThickness();
            contactShadowNormalBias = s.contactShadowNormalBias();
            contactShadowEdgeMargin = s.contactShadowEdgeMargin();
            contactShadowMipStepScale = s.contactShadowMipStepScale();
            contactShadowStepGrowth = s.contactShadowStepGrowth();
            shadowCascadeCount = s.shadowCascadeCount();
            shadowResolution = s.shadowResolution();
            shadowSplitLambda = s.shadowSplitLambda();
            shadowCasterDistance = s.shadowCasterDistance();
            shadowCascadeBlendFraction = s.shadowCascadeBlendFraction();
            shadowNormalOffsetTexels = s.shadowNormalOffsetTexels();
            shadowReceiverBiasTexels = s.shadowReceiverBiasTexels();
            shadowFilterRadiusTexels = s.shadowFilterRadiusTexels();
            shadowBlockerSearchRadiusTexels = s.shadowBlockerSearchRadiusTexels();
            shadowPenumbraScaleTexels = s.shadowPenumbraScaleTexels();
            shadowMaxPenumbraTexels = s.shadowMaxPenumbraTexels();
            depthPyramidMaxMipLevels = s.depthPyramidMaxMipLevels();
            indirectTemporalEnabled = s.indirectTemporalEnabled();
            indirectTemporalHistoryWeight = s.indirectTemporalHistoryWeight();
            indirectTemporalDepthThreshold = s.indirectTemporalDepthThreshold();
            reflectionTemporalEnabled = s.reflectionTemporalEnabled();
            reflectionTemporalHistoryWeight = s.reflectionTemporalHistoryWeight();
            reflectionTemporalDepthThreshold = s.reflectionTemporalDepthThreshold();
            reflectionDenoiseEnabled = s.reflectionDenoiseEnabled();
            reflectionDenoiseRadius = s.reflectionDenoiseRadius();
            reflectionDenoiseDepthThreshold = s.reflectionDenoiseDepthThreshold();
            reflectionDenoiseNormalThreshold = s.reflectionDenoiseNormalThreshold();
            coloredBlockLightEnabled = s.coloredBlockLightEnabled();
            dynamicLightsEnabled = s.dynamicLightsEnabled();
            participatingMediaEnabled = s.participatingMediaEnabled();
            waterEnabled = s.waterEnabled();
        }

        public Builder ambientOcclusionEnabled(boolean value) { ambientOcclusionEnabled = value; return this; }
        public Builder ambientOcclusionScale(float value) { ambientOcclusionScale = value; return this; }
        public Builder ambientOcclusionSampleCount(int value) { ambientOcclusionSampleCount = value; return this; }
        public Builder ambientOcclusionRadius(float value) { ambientOcclusionRadius = value; return this; }
        public Builder ambientOcclusionThickness(float value) { ambientOcclusionThickness = value; return this; }
        public Builder ambientOcclusionMaxMip(int value) { ambientOcclusionMaxMip = value; return this; }
        public Builder ambientOcclusionMipBias(float value) { ambientOcclusionMipBias = value; return this; }
        public Builder indirectLightEnabled(boolean value) { indirectLightEnabled = value; return this; }
        public Builder indirectLightScale(float value) { indirectLightScale = value; return this; }
        public Builder indirectLightSampleCount(int value) { indirectLightSampleCount = value; return this; }
        public Builder indirectLightRadius(float value) { indirectLightRadius = value; return this; }
        public Builder indirectLightBias(float value) { indirectLightBias = value; return this; }
        public Builder indirectLightMaxMip(int value) { indirectLightMaxMip = value; return this; }
        public Builder indirectLightMipBias(float value) { indirectLightMipBias = value; return this; }
        public Builder reflectionsEnabled(boolean value) { reflectionsEnabled = value; return this; }
        public Builder reflectionTraceScale(float value) { reflectionTraceScale = value; return this; }
        public Builder reflectionOutputScale(float value) { reflectionOutputScale = value; return this; }
        public Builder reflectionHistoryScale(float value) { reflectionHistoryScale = value; return this; }
        public Builder reflectionTraceMaxSteps(int value) { reflectionTraceMaxSteps = value; return this; }
        public Builder reflectionTraceMaxDistanceScale(float value) { reflectionTraceMaxDistanceScale = value; return this; }
        public Builder reflectionTraceMinStep(float value) { reflectionTraceMinStep = value; return this; }
        public Builder reflectionTraceDepthStepScale(float value) { reflectionTraceDepthStepScale = value; return this; }
        public Builder reflectionTraceStepGrowth(float value) { reflectionTraceStepGrowth = value; return this; }
        public Builder reflectionTraceThickness(float value) { reflectionTraceThickness = value; return this; }
        public Builder reflectionTraceNormalBias(float value) { reflectionTraceNormalBias = value; return this; }
        public Builder reflectionTraceEdgeMargin(float value) { reflectionTraceEdgeMargin = value; return this; }
        public Builder reflectionTraceMipStepScale(float value) { reflectionTraceMipStepScale = value; return this; }
        public Builder reflectionScreenConfidenceThreshold(float value) { reflectionScreenConfidenceThreshold = value; return this; }
        public Builder reflectionCascadeConfidence(float value) { reflectionCascadeConfidence = value; return this; }
        public Builder reflectionCascadeCount(int value) { reflectionCascadeCount = value; return this; }
        public Builder reflectionCascadeFaceResolution(int value) { reflectionCascadeFaceResolution = value; return this; }
        public Builder reflectionCascadeUpdateIntervalFrames(int value) { reflectionCascadeUpdateIntervalFrames = value; return this; }
        public Builder reflectionCascadeDistanceScale(float value) { reflectionCascadeDistanceScale = value; return this; }
        public Builder shadowsEnabled(boolean value) { shadowsEnabled = value; return this; }
        public Builder shadowOutputScale(float value) { shadowOutputScale = value; return this; }
        public Builder contactShadowsEnabled(boolean value) { contactShadowsEnabled = value; return this; }
        public Builder contactShadowScale(float value) { contactShadowScale = value; return this; }
        public Builder contactShadowMaxSteps(int value) { contactShadowMaxSteps = value; return this; }
        public Builder contactShadowMaxDistance(float value) { contactShadowMaxDistance = value; return this; }
        public Builder contactShadowMinStep(float value) { contactShadowMinStep = value; return this; }
        public Builder contactShadowThickness(float value) { contactShadowThickness = value; return this; }
        public Builder contactShadowNormalBias(float value) { contactShadowNormalBias = value; return this; }
        public Builder contactShadowEdgeMargin(float value) { contactShadowEdgeMargin = value; return this; }
        public Builder contactShadowMipStepScale(float value) { contactShadowMipStepScale = value; return this; }
        public Builder contactShadowStepGrowth(float value) { contactShadowStepGrowth = value; return this; }
        public Builder shadowCascadeCount(int value) { shadowCascadeCount = value; return this; }
        public Builder shadowResolution(int value) { shadowResolution = value; return this; }
        public Builder shadowSplitLambda(float value) { shadowSplitLambda = value; return this; }
        public Builder shadowCasterDistance(float value) { shadowCasterDistance = value; return this; }
        public Builder shadowCascadeBlendFraction(float value) { shadowCascadeBlendFraction = value; return this; }
        public Builder shadowNormalOffsetTexels(float value) { shadowNormalOffsetTexels = value; return this; }
        public Builder shadowReceiverBiasTexels(float value) { shadowReceiverBiasTexels = value; return this; }
        public Builder shadowFilterRadiusTexels(float value) { shadowFilterRadiusTexels = value; return this; }
        public Builder shadowBlockerSearchRadiusTexels(float value) { shadowBlockerSearchRadiusTexels = value; return this; }
        public Builder shadowPenumbraScaleTexels(float value) { shadowPenumbraScaleTexels = value; return this; }
        public Builder shadowMaxPenumbraTexels(float value) { shadowMaxPenumbraTexels = value; return this; }
        public Builder depthPyramidMaxMipLevels(int value) { depthPyramidMaxMipLevels = value; return this; }
        public Builder indirectTemporalEnabled(boolean value) { indirectTemporalEnabled = value; return this; }
        public Builder indirectTemporalHistoryWeight(float value) { indirectTemporalHistoryWeight = value; return this; }
        public Builder indirectTemporalDepthThreshold(float value) { indirectTemporalDepthThreshold = value; return this; }
        public Builder reflectionTemporalEnabled(boolean value) { reflectionTemporalEnabled = value; return this; }
        public Builder reflectionTemporalHistoryWeight(float value) { reflectionTemporalHistoryWeight = value; return this; }
        public Builder reflectionTemporalDepthThreshold(float value) { reflectionTemporalDepthThreshold = value; return this; }
        public Builder reflectionDenoiseEnabled(boolean value) { reflectionDenoiseEnabled = value; return this; }
        public Builder reflectionDenoiseRadius(int value) { reflectionDenoiseRadius = value; return this; }
        public Builder reflectionDenoiseDepthThreshold(float value) { reflectionDenoiseDepthThreshold = value; return this; }
        public Builder reflectionDenoiseNormalThreshold(float value) { reflectionDenoiseNormalThreshold = value; return this; }
        public Builder coloredBlockLightEnabled(boolean value) { coloredBlockLightEnabled = value; return this; }
        public Builder dynamicLightsEnabled(boolean value) { dynamicLightsEnabled = value; return this; }
        public Builder participatingMediaEnabled(boolean value) { participatingMediaEnabled = value; return this; }
        public Builder waterEnabled(boolean value) { waterEnabled = value; return this; }

        public Snapshot build() {
            return new Snapshot(
                    ambientOcclusionEnabled,
                    ambientOcclusionScale,
                    ambientOcclusionSampleCount,
                    ambientOcclusionRadius,
                    ambientOcclusionThickness,
                    ambientOcclusionMaxMip,
                    ambientOcclusionMipBias,
                    indirectLightEnabled,
                    indirectLightScale,
                    indirectLightSampleCount,
                    indirectLightRadius,
                    indirectLightBias,
                    indirectLightMaxMip,
                    indirectLightMipBias,
                    reflectionsEnabled,
                    reflectionTraceScale,
                    reflectionOutputScale,
                    reflectionHistoryScale,
                    reflectionTraceMaxSteps,
                    reflectionTraceMaxDistanceScale,
                    reflectionTraceMinStep,
                    reflectionTraceDepthStepScale,
                    reflectionTraceStepGrowth,
                    reflectionTraceThickness,
                    reflectionTraceNormalBias,
                    reflectionTraceEdgeMargin,
                    reflectionTraceMipStepScale,
                    reflectionScreenConfidenceThreshold,
                    reflectionCascadeConfidence,
                    reflectionCascadeCount,
                    reflectionCascadeFaceResolution,
                    reflectionCascadeUpdateIntervalFrames,
                    reflectionCascadeDistanceScale,
                    shadowsEnabled,
                    shadowOutputScale,
                    contactShadowsEnabled,
                    contactShadowScale,
                    contactShadowMaxSteps,
                    contactShadowMaxDistance,
                    contactShadowMinStep,
                    contactShadowThickness,
                    contactShadowNormalBias,
                    contactShadowEdgeMargin,
                    contactShadowMipStepScale,
                    contactShadowStepGrowth,
                    shadowCascadeCount,
                    shadowResolution,
                    shadowSplitLambda,
                    shadowCasterDistance,
                    shadowCascadeBlendFraction,
                    shadowNormalOffsetTexels,
                    shadowReceiverBiasTexels,
                    shadowFilterRadiusTexels,
                    shadowBlockerSearchRadiusTexels,
                    shadowPenumbraScaleTexels,
                    shadowMaxPenumbraTexels,
                    depthPyramidMaxMipLevels,
                    indirectTemporalEnabled,
                    indirectTemporalHistoryWeight,
                    indirectTemporalDepthThreshold,
                    reflectionTemporalEnabled,
                    reflectionTemporalHistoryWeight,
                    reflectionTemporalDepthThreshold,
                    reflectionDenoiseEnabled,
                    reflectionDenoiseRadius,
                    reflectionDenoiseDepthThreshold,
                    reflectionDenoiseNormalThreshold,
                    coloredBlockLightEnabled,
                    dynamicLightsEnabled,
                    participatingMediaEnabled,
                    waterEnabled
            ).validated();
        }
    }
}
