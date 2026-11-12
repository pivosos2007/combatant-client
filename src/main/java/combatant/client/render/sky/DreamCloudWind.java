/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sky;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Continuously integrates low/high cloud advection in world blocks. The supplied
 * monotonic seconds must never be derived from Minecraft's adjustable game time.
 * Suspended rendering limits elapsed integration to avoid large cloud jumps.
 */
public final class DreamCloudWind {
    private final double lowHeading;
    private final double highHeading;
    private final double phaseLow;
    private final double phaseHigh;
    private Object level;
    private double lastSeconds = Double.NaN;
    private double simulationSeconds;
    private double lowX, lowZ, highX, highZ;

    public DreamCloudWind() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        lowHeading = random.nextDouble(-Math.PI, Math.PI);
        highHeading = random.nextDouble(-Math.PI, Math.PI);
        phaseLow = random.nextDouble(0.0, Math.PI * 2.0);
        phaseHigh = random.nextDouble(0.0, Math.PI * 2.0);
    }

    public void update(Object currentLevel, double seconds) {
        if (currentLevel == null || !Double.isFinite(seconds)) return;
        if (level != currentLevel || !Double.isFinite(lastSeconds)) {
            level = currentLevel;
            lastSeconds = seconds;
            return;
        }
        double elapsed = seconds - lastSeconds;
        lastSeconds = seconds;
        if (elapsed <= 0.0) return;
        // Large wall-clock jumps after alt-tab must not teleport the medium.
        double dt = Math.min(elapsed, .10);
        simulationSeconds += dt;
        double t = simulationSeconds - .5 * dt;
        double lowDirection = lowHeading + .0021 * t
                + .91 * Math.sin(.0124 * t + phaseLow)
                + .43 * Math.sin(.0054 * t + phaseHigh);
        double highDirection = highHeading - .0032 * t
                + .68 * Math.sin(.0082 * t + phaseHigh)
                + .54 * Math.sin(.0166 * t + phaseLow);
        double lowSpeed = .68 * (.85 + .24 * Math.sin(.026 * t + phaseHigh));
        double highSpeed = 1.66 * (.85 + .28 * Math.sin(.022 * t + phaseLow));
        lowX += Math.cos(lowDirection) * lowSpeed * dt;
        lowZ += Math.sin(lowDirection) * lowSpeed * dt;
        highX += Math.cos(highDirection) * highSpeed * dt;
        highZ += Math.sin(highDirection) * highSpeed * dt;
    }

    public float lowX(double period) { return (float) (lowX % period); }
    public float lowZ(double period) { return (float) (lowZ % period); }
    public float highX(double period) { return (float) (highX % period); }
    public float highZ(double period) { return (float) (highZ % period); }

    public void reset() {
        level = null;
        lastSeconds = Double.NaN;
        simulationSeconds = 0.0;
        lowX = lowZ = highX = highZ = 0.0;
    }
}
