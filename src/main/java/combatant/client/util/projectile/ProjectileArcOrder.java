package combatant.client.util.projectile;

/** Pure ordering policy for competing, already simulated ballistic candidates. */
public final class ProjectileArcOrder {
    private ProjectileArcOrder() { }

    /** With a previously aimed rotation, prefer continuity. Otherwise prefer the
     * earliest viable contact instead of an arbitrary high arc with a smaller
     * center-point approximation error. Flight-window eligibility is enforced
     * separately by the solver on the ACTUAL simulated impact tick. */
    public static int compare(int tickA, double errorA, float yawA, float pitchA,
                              int tickB, double errorB, float yawB, float pitchB,
                              boolean hasReference, float referenceYaw, float referencePitch) {
        if (hasReference) {
            int angular = Double.compare(angularDistance(yawA, pitchA, referenceYaw, referencePitch),
                    angularDistance(yawB, pitchB, referenceYaw, referencePitch));
            if (angular != 0) return angular;
        }
        int flight = Integer.compare(tickA, tickB);
        return flight != 0 ? flight : Double.compare(errorA, errorB);
    }

    private static double angularDistance(float yaw, float pitch, float referenceYaw, float referencePitch) {
        double delta = (yaw - referenceYaw) % 360.0;
        if (delta > 180.0) delta -= 360.0;
        if (delta < -180.0) delta += 360.0;
        return Math.abs(delta) + Math.abs(pitch - referencePitch);
    }
}
