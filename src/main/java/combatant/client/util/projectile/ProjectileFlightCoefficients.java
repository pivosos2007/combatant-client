package combatant.client.util.projectile;

/** Discrete arrow motion coefficients, without game or rendering dependencies. */
public final class ProjectileFlightCoefficients {
    private ProjectileFlightCoefficients() { }
    public record Sample(double velocitySum, double gravityDisplacement) { }

    public static Sample at(int ticks, double drag, double gravity) {
        if (ticks < 1 || !(drag > 0.0) || !(drag <= 1.0))
            throw new IllegalArgumentException("ticks/drag");
        double velocitySum = 0.0;
        double gravityDisplacement = 0.0;
        double downwardVelocity = 0.0;
        double weight = 1.0;
        for (int i = 0; i < ticks; i++) {
            velocitySum += weight;
            gravityDisplacement += downwardVelocity;
            downwardVelocity = downwardVelocity * drag - gravity;
            weight *= drag;
        }
        return new Sample(velocitySum, gravityDisplacement);
    }
}
