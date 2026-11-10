/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.heuristic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class HeuristicSolver {
    private static final double EPS = 1.0e-9;
    private static final double MIN_CROSS_TRACK_SIGMA = 0.25;
    private static final double CONFIDENCE_95_2D = 2.4477;

    private HeuristicSolver() {}

    /** Compatibility entry point for callers that do not yet provide a source angular-noise model. */
    public static HeuristicEstimate solve(UUID target, List<HeuristicObservation> input,
                                          long segmentId, double forwardTolerance) {
        return solve(target, input, segmentId, forwardTolerance, Math.toRadians(0.75));
    }

    /**
     * Estimates a target from weighted bearing observations using least squares.
     * The second weighting pass scales angular uncertainty by observer-to-estimate distance,
     * so the returned covariance is expressed in squared block units.
     */
    public static HeuristicEstimate solve(UUID target, List<HeuristicObservation> input,
                                          long segmentId, double forwardTolerance,
                                          double bearingNoiseRadians) {
        if (target == null || input == null || input.size() < 2) return null;
        double angularNoise = Math.max(Math.toRadians(0.01), Math.min(Math.toRadians(30.0),
                Math.abs(bearingNoiseRadians)));

        List<W> obs = new ArrayList<>();
        for (HeuristicObservation o : input) {
            if (o != null && target.equals(o.targetUuid())) obs.add(new W(o, o.weight()));
        }
        if (obs.size() < 2) return null;

        S initial = solveWeighted(obs);
        if (initial == null) return null;

        List<Double> residuals = new ArrayList<>(obs.size());
        for (W w : obs) residuals.add(Math.abs(residual(w.o, initial.x, initial.z)));
        residuals.sort(Comparator.naturalOrder());
        double median = median(residuals);
        double robustScale = Math.max(0.35, median * 1.4826);
        double huber = robustScale * 2.5;

        List<W> robust = new ArrayList<>(obs.size());
        for (W w : obs) {
            double r = Math.abs(residual(w.o, initial.x, initial.z));
            double rw = r <= huber ? 1.0 : huber / Math.max(r, EPS);
            if (forward(w.o, initial.x, initial.z) < -forwardTolerance) rw *= 0.05;
            robust.add(new W(w.o, w.w * rw));
        }

        S robustSolution = solveWeighted(robust);
        if (robustSolution == null) return null;

        // Convert angular source noise into positional cross-track noise at the estimated range.
        List<W> geometryWeighted = new ArrayList<>(robust.size());
        List<Double> positionalSigmas = new ArrayList<>(robust.size());
        double tanNoise = Math.tan(angularNoise);
        for (W w : robust) {
            double range = Math.hypot(robustSolution.x - w.o.observerX(), robustSolution.z - w.o.observerZ());
            double sigma = Math.max(MIN_CROSS_TRACK_SIGMA, range * tanNoise);
            positionalSigmas.add(sigma);
            geometryWeighted.add(new W(w.o, w.w / (sigma * sigma)));
        }

        S s = solveWeighted(geometryWeighted);
        if (s == null || violatesSourceRange(obs, s.x, s.z)) return null;

        double sumSq = 0.0;
        int inliers = 0;
        double threshold = Math.max(1.0, robustScale * 3.0);
        for (W w : robust) {
            double r = residual(w.o, s.x, s.z);
            sumSq += r * r;
            if (Math.abs(r) <= threshold && forward(w.o, s.x, s.z) >= -forwardTolerance) inliers++;
        }
        double rms = Math.sqrt(sumSq / robust.size());

        double det = s.a00 * s.a11 - s.a01 * s.a01;
        if (det <= EPS) return null;
        double i00 = s.a11 / det;
        double i01 = -s.a01 / det;
        double i11 = s.a00 / det;
        double trace = i00 + i11;
        double disc = Math.sqrt(Math.max(0.0, (i00 - i11) * (i00 - i11) + 4.0 * i01 * i01));
        double lMax = Math.max(EPS, (trace + disc) * 0.5);
        double lMin = Math.max(EPS, (trace - disc) * 0.5);

        double expectedSigma = Math.max(MIN_CROSS_TRACK_SIGMA, median(positionalSigmas));
        double residualInflation = Math.max(1.0, rms / expectedSigma);
        double major = Math.sqrt(lMax) * CONFIDENCE_95_2D * residualInflation;
        double minor = Math.sqrt(lMin) * CONFIDENCE_95_2D * residualInflation;
        double angle = 0.5 * Math.atan2(2.0 * i01, i00 - i11);
        double condition = lMax / lMin;

        double inlierRatio = inliers / (double) robust.size();
        double residualQuality = 1.0 / (1.0 + rms / expectedSigma);
        double conditionQuality = 1.0 / (1.0 + Math.log1p(Math.max(0.0, condition - 1.0)) / 4.0);
        double sampleQuality = Math.min(1.0, robust.size() / 5.0);
        double geometryQuality = geometryQuality(obs, s.x, s.z);
        double confidence = clamp(inlierRatio * residualQuality * conditionQuality * sampleQuality * geometryQuality);

        long updated = input.stream().mapToLong(HeuristicObservation::observedAtMs).max()
                .orElse(System.currentTimeMillis());
        return new HeuristicEstimate(target, s.x, s.z, major, minor, angle, rms, confidence,
                robust.size(), inliers, updated, segmentId);
    }

    private static double geometryQuality(List<W> obs, double x, double z) {
        double maxBaseline = 0.0;
        double maxAngle = 0.0;
        List<Double> ranges = new ArrayList<>(obs.size());
        for (int i = 0; i < obs.size(); i++) {
            HeuristicObservation a = obs.get(i).o;
            ranges.add(Math.hypot(x - a.observerX(), z - a.observerZ()));
            for (int j = i + 1; j < obs.size(); j++) {
                HeuristicObservation b = obs.get(j).o;
                maxBaseline = Math.max(maxBaseline,
                        Math.hypot(a.observerX() - b.observerX(), a.observerZ() - b.observerZ()));
                double angle = angleDifference(a.bearingRadians(), b.bearingRadians());
                maxAngle = Math.max(maxAngle, Math.min(angle, Math.PI - angle));
            }
        }
        ranges.sort(Comparator.naturalOrder());
        double medianRange = Math.max(1.0, median(ranges));
        // A baseline of roughly 20% of target range is already geometrically useful.
        double baselineQuality = clamp(maxBaseline / Math.max(1.0, medianRange * 0.20));
        // sin(angle) naturally peaks at a 90 degree crossing and collapses for parallel bearings.
        double angularQuality = clamp(Math.sin(Math.max(0.0, Math.min(Math.PI * 0.5, maxAngle))));
        return Math.sqrt(Math.max(0.0, baselineQuality * angularQuality));
    }

    private static S solveWeighted(List<W> obs) {
        double a00 = 0, a01 = 0, a11 = 0, b0 = 0, b1 = 0;
        for (W w : obs) {
            if (!(w.w > 0.0) || !Double.isFinite(w.w)) continue;
            double dx = w.o.dirX(), dz = w.o.dirZ(), nx = -dz, nz = dx;
            a00 += w.w * nx * nx;
            a01 += w.w * nx * nz;
            a11 += w.w * nz * nz;
            double p = nx * w.o.observerX() + nz * w.o.observerZ();
            b0 += w.w * nx * p;
            b1 += w.w * nz * p;
        }
        double det = a00 * a11 - a01 * a01;
        if (Math.abs(det) <= EPS) return null;
        double x = (b0 * a11 - b1 * a01) / det;
        double z = (a00 * b1 - a01 * b0) / det;
        return Double.isFinite(x) && Double.isFinite(z) ? new S(x, z, a00, a01, a11) : null;
    }

    private static boolean violatesSourceRange(List<W> observations, double x, double z) {
        for (W w : observations) {
            double minimum = w.o.minimumHorizontalRange();
            if (!(minimum > 0.0)) continue;
            double range = Math.hypot(x - w.o.observerX(), z - w.o.observerZ());
            if (range + 1.5 < minimum) return true;
        }
        return false;
    }

    private static double residual(HeuristicObservation o, double x, double z) {
        double nx = -o.dirZ(), nz = o.dirX();
        return nx * (x - o.observerX()) + nz * (z - o.observerZ());
    }

    private static double forward(HeuristicObservation o, double x, double z) {
        return o.dirX() * (x - o.observerX()) + o.dirZ() * (z - o.observerZ());
    }

    private static double angleDifference(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2.0);
        return d > Math.PI ? Math.PI * 2.0 - d : d;
    }

    private static double median(List<Double> sortedOrUnsorted) {
        if (sortedOrUnsorted.isEmpty()) return 0.0;
        List<Double> values = new ArrayList<>(sortedOrUnsorted);
        values.sort(Comparator.naturalOrder());
        int middle = values.size() / 2;
        if ((values.size() & 1) != 0) return values.get(middle);
        return (values.get(middle - 1) + values.get(middle)) * 0.5;
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private record W(HeuristicObservation o, double w) {}
    private record S(double x, double z, double a00, double a01, double a11) {}
}
