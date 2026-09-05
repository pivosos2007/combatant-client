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

    private HeuristicSolver() {}

    public static HeuristicEstimate solve(UUID target, List<HeuristicObservation> input,
                                          long segmentId, double forwardTolerance) {
        if (target == null || input == null || input.size() < 2) return null;
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
        double median = residuals.get(residuals.size() / 2);
        double scale = Math.max(0.35, median * 1.4826);
        double huber = scale * 2.5;

        List<W> robust = new ArrayList<>(obs.size());
        for (W w : obs) {
            double r = Math.abs(residual(w.o, initial.x, initial.z));
            double rw = r <= huber ? 1.0 : huber / Math.max(r, EPS);
            if (forward(w.o, initial.x, initial.z) < -forwardTolerance) rw *= 0.05;
            robust.add(new W(w.o, w.w * rw));
        }

        S s = solveWeighted(robust);
        if (s == null) return null;

        double sumSq = 0.0;
        int inliers = 0;
        double threshold = Math.max(1.0, scale * 3.0);
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
        double disc = Math.sqrt(Math.max(0.0, (i00-i11)*(i00-i11)+4.0*i01*i01));
        double lMax = Math.max(EPS, (trace + disc) * 0.5);
        double lMin = Math.max(EPS, (trace - disc) * 0.5);
        double sigma = Math.max(0.5, rms);
        double major = Math.sqrt(lMax) * sigma * 2.4477;
        double minor = Math.sqrt(lMin) * sigma * 2.4477;
        double angle = 0.5 * Math.atan2(2.0*i01, i00-i11);
        double condition = lMax / lMin;
        double inlierRatio = inliers / (double) robust.size();
        double confidence = clamp(inlierRatio
                * (1.0 / (1.0 + rms / 8.0))
                * (1.0 / (1.0 + Math.log1p(Math.max(0.0, condition - 1.0)) / 4.0))
                * Math.min(1.0, robust.size() / 5.0));

        long updated = input.stream().mapToLong(HeuristicObservation::observedAtMs).max()
                .orElse(System.currentTimeMillis());
        return new HeuristicEstimate(target, s.x, s.z, major, minor, angle, rms, confidence,
                robust.size(), inliers, updated, segmentId);
    }

    private static S solveWeighted(List<W> obs) {
        double a00=0,a01=0,a11=0,b0=0,b1=0;
        for (W w : obs) {
            if (!(w.w > 0.0)) continue;
            double dx=w.o.dirX(), dz=w.o.dirZ(), nx=-dz, nz=dx;
            a00 += w.w*nx*nx;
            a01 += w.w*nx*nz;
            a11 += w.w*nz*nz;
            double p = nx*w.o.observerX() + nz*w.o.observerZ();
            b0 += w.w*nx*p;
            b1 += w.w*nz*p;
        }
        double det=a00*a11-a01*a01;
        if (Math.abs(det)<=EPS) return null;
        double x=(b0*a11-b1*a01)/det;
        double z=(a00*b1-a01*b0)/det;
        return Double.isFinite(x)&&Double.isFinite(z) ? new S(x,z,a00,a01,a11) : null;
    }

    private static double residual(HeuristicObservation o,double x,double z) {
        double nx=-o.dirZ(), nz=o.dirX();
        return nx*(x-o.observerX()) + nz*(z-o.observerZ());
    }

    private static double forward(HeuristicObservation o,double x,double z) {
        return o.dirX()*(x-o.observerX()) + o.dirZ()*(z-o.observerZ());
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private record W(HeuristicObservation o,double w) {}
    private record S(double x,double z,double a00,double a01,double a11) {}
}
