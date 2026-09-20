/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.NumberValue;

import java.util.List;

@ConfigSubsystem(value = "map/heuristic", settingOwner = "map_heuristic")
public final class MapHeuristicConfig extends SubsystemConfig {
    public static final MapHeuristicConfig INSTANCE = new MapHeuristicConfig();

    private final NumberValue<Integer> maxSamplesPerTarget = number("maxSamplesPerTarget", 24, 2, 128);
    private final NumberValue<Integer> maxSampleAgeMs = number("maxSampleAgeMs", 120000, 1000, 900000);
    private final NumberValue<Double> minBaseline = number("minBaseline", 8.0, 0.0, 512.0);
    private final NumberValue<Double> minBearingDeltaDegrees = number("minBearingDeltaDegrees", 1.5, 0.05, 45.0);
    private final NumberValue<Double> forwardRejectTolerance = number("forwardRejectTolerance", 4.0, 0.0, 64.0);
    private final NumberValue<Double> bearingNoiseDegrees = number("bearingNoiseDegrees", 0.75, 0.05, 10.0);
    private final NumberValue<Double> segmentResetDistance = number("segmentResetDistance", 48.0, 1.0, 1024.0);
    private final NumberValue<Double> segmentResetSigma = number("segmentResetSigma", 2.5, 0.5, 10.0);
    private final NumberValue<Double> segmentResetMinConfidence = number("segmentResetMinConfidence", 0.45, 0.0, 1.0);
    private final NumberValue<Double> teleportResidualFloor = number("teleportResidualFloor", 12.0, 1.0, 256.0);
    private final NumberValue<Double> teleportResidualSigma = number("teleportResidualSigma", 1.5, 0.5, 8.0);
    private final NumberValue<Integer> teleportConfirmSamples = number("teleportConfirmSamples", 3, 2, 8);
    private final NumberValue<Integer> teleportCandidateMaxAgeMs = number("teleportCandidateMaxAgeMs", 20000, 1000, 120000);
    private final NumberValue<Integer> liveSourceGraceMs = number("liveSourceGraceMs", 2500, 0, 30000);
    private final NumberValue<Integer> staleEstimateDisplayMs = number("staleEstimateDisplayMs", 30000, 1000, 300000);

    private MapHeuristicConfig() { loadConfig(); }
    public static MapHeuristicConfig get() { return INSTANCE; }
    public int maxSamplesPerTarget() { return maxSamplesPerTarget.get().intValue(); }
    public int maxSampleAgeMs() { return maxSampleAgeMs.get().intValue(); }
    public double minBaseline() { return minBaseline.get().doubleValue(); }
    public double minBearingDeltaDegrees() { return minBearingDeltaDegrees.get().doubleValue(); }
    public double forwardRejectTolerance() { return forwardRejectTolerance.get().doubleValue(); }
    public double bearingNoiseDegrees() { return bearingNoiseDegrees.get().doubleValue(); }
    public double segmentResetDistance() { return segmentResetDistance.get().doubleValue(); }
    public double segmentResetSigma() { return segmentResetSigma.get().doubleValue(); }
    public double segmentResetMinConfidence() { return segmentResetMinConfidence.get().doubleValue(); }
    public double teleportResidualFloor() { return teleportResidualFloor.get().doubleValue(); }
    public double teleportResidualSigma() { return teleportResidualSigma.get().doubleValue(); }
    public int teleportConfirmSamples() { return teleportConfirmSamples.get().intValue(); }
    public int teleportCandidateMaxAgeMs() { return teleportCandidateMaxAgeMs.get().intValue(); }
    public int liveSourceGraceMs() { return liveSourceGraceMs.get().intValue(); }
    public int staleEstimateDisplayMs() { return staleEstimateDisplayMs.get().intValue(); }

    @Override
    public List<SettingDef> getSettingDefs() {
        return List.of(
                SettingDef.number("maxSamplesPerTarget", maxSamplesPerTarget),
                SettingDef.number("maxSampleAgeMs", maxSampleAgeMs),
                SettingDef.number("minBaseline", minBaseline),
                SettingDef.number("minBearingDeltaDegrees", minBearingDeltaDegrees),
                SettingDef.number("forwardRejectTolerance", forwardRejectTolerance),
                SettingDef.number("bearingNoiseDegrees", bearingNoiseDegrees),
                SettingDef.number("segmentResetDistance", segmentResetDistance),
                SettingDef.number("segmentResetSigma", segmentResetSigma),
                SettingDef.number("segmentResetMinConfidence", segmentResetMinConfidence),
                SettingDef.number("teleportResidualFloor", teleportResidualFloor),
                SettingDef.number("teleportResidualSigma", teleportResidualSigma),
                SettingDef.number("teleportConfirmSamples", teleportConfirmSamples),
                SettingDef.number("teleportCandidateMaxAgeMs", teleportCandidateMaxAgeMs),
                SettingDef.number("liveSourceGraceMs", liveSourceGraceMs),
                SettingDef.number("staleEstimateDisplayMs", staleEstimateDisplayMs)
        );
    }
}
