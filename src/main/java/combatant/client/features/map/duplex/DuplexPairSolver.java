/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

public final class DuplexPairSolver {
    private DuplexPairSolver() {}

    public static DuplexEstimate solve(DuplexBearingSample a, DuplexBearingSample b,
                                       double minCrossingAngleRadians, double bearingNoiseRadians) {
        if (a == null || b == null || !a.targetUuid().equals(b.targetUuid())) return null;
        double ax=a.dirX(), az=a.dirZ(), bx=b.dirX(), bz=b.dirZ();
        double cross=ax*bz-az*bx;
        double crossing=Math.acos(Math.max(-1.0,Math.min(1.0,ax*bx+az*bz)));
        crossing=Math.min(crossing,Math.PI-crossing);
        if (Math.abs(cross)<1.0e-8 || crossing<minCrossingAngleRadians) return null;

        double rx=b.observerX()-a.observerX();
        double rz=b.observerZ()-a.observerZ();
        double ta=(rx*bz-rz*bx)/cross;
        double tb=(rx*az-rz*ax)/cross;
        if (ta<0.0 || tb<0.0) return null;

        double x=a.observerX()+ta*ax;
        double z=a.observerZ()+ta*az;
        double baseline=Math.hypot(rx,rz);
        double uncertainty=Math.max(0.25, baseline*Math.tan(Math.max(1.0e-6,bearingNoiseRadians))
                / Math.max(Math.sin(crossing),1.0e-6));
        return new DuplexEstimate(a.targetUuid(),x,z,uncertainty,crossing,
                Math.max(a.observedAtMs(),b.observedAtMs()));
    }
}
