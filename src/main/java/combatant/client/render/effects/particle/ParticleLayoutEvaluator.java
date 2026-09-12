/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.effects.particle;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** CPU evaluator for the current renderer. The descriptor contract is reusable by a later GPU backend. */
public final class ParticleLayoutEvaluator {
    private static final double TAU = Math.PI * 2.0;

    private ParticleLayoutEvaluator() {
    }

    public static ParticleLayoutSample sample(ParticleLayoutDescriptor descriptor,
                                              int index,
                                              int count,
                                              float timeSeconds,
                                              long seed) {
        if (descriptor == null || count <= 0) {
            return new ParticleLayoutSample(Vec3.ZERO, 0.0f, 0.0f, 0.0f, 0);
        }
        int safeIndex = Math.floorMod(index, count);
        if (descriptor instanceof ParticleLayoutDescriptor.Helix helix) {
            return helix(helix, safeIndex, count, timeSeconds);
        }
        if (descriptor instanceof ParticleLayoutDescriptor.Orbit orbit) {
            return orbit(orbit, safeIndex, count, timeSeconds);
        }
        if (descriptor instanceof ParticleLayoutDescriptor.Runes runes) {
            return runes(runes, safeIndex, count, timeSeconds, seed);
        }
        if (descriptor instanceof ParticleLayoutDescriptor.Pulse pulse) {
            return pulse(pulse, safeIndex, count, timeSeconds);
        }
        return lightning((ParticleLayoutDescriptor.LightningPath) descriptor, safeIndex, count, timeSeconds, seed);
    }

    private static ParticleLayoutSample helix(ParticleLayoutDescriptor.Helix d,
                                               int index,
                                               int count,
                                               float time) {
        int strand = index % d.strands();
        int localIndex = index / d.strands();
        int localCount = Math.max(1, (count + d.strands() - 1 - strand) / d.strands());
        float t = localCount <= 1 ? 0.5f : localIndex / (float) (localCount - 1);
        double angle = TAU * (d.turns() * t + strand / (double) d.strands()) + time * d.speed();
        double y = (t - 0.5) * d.height() + Math.sin(angle * 0.5) * d.verticalWave();
        float edge = 1.0f - Math.abs(t * 2.0f - 1.0f);
        float alpha = 0.45f + edge * 0.55f;
        float scale = 0.72f + 0.28f * (float) Math.sin(angle * 0.5 + 1.4);
        return new ParticleLayoutSample(
                new Vec3(Math.cos(angle) * d.radius(), y, Math.sin(angle) * d.radius()),
                alpha,
                Math.max(0.35f, scale),
                (float) Math.toDegrees(angle),
                Math.round(t * 360.0f) + strand * 90
        );
    }

    private static ParticleLayoutSample orbit(ParticleLayoutDescriptor.Orbit d,
                                               int index,
                                               int count,
                                               float time) {
        int plane = index % d.planes();
        int localIndex = index / d.planes();
        int localCount = Math.max(1, (count + d.planes() - 1 - plane) / d.planes());
        float t = localCount <= 1 ? 0.0f : localIndex / (float) localCount;
        double theta = TAU * t + time * d.speed() + plane * 0.31;
        double planeYaw = TAU * plane / d.planes();
        double tilt = (plane % 2 == 0 ? 1.0 : -1.0) * d.tiltRadians();

        double x = Math.cos(theta) * d.radius();
        double y = Math.sin(theta) * d.verticalRadius();
        double z = 0.0;

        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);
        double ty = y * cosTilt - z * sinTilt;
        double tz = y * sinTilt + z * cosTilt;

        double cosYaw = Math.cos(planeYaw);
        double sinYaw = Math.sin(planeYaw);
        double wx = x * cosYaw + tz * sinYaw;
        double wz = -x * sinYaw + tz * cosYaw;

        float head = 0.5f + 0.5f * (float) Math.sin(theta + time * 1.7f);
        return new ParticleLayoutSample(
                new Vec3(wx, ty, wz),
                0.35f + head * 0.65f,
                0.65f + head * 0.55f,
                (float) Math.toDegrees(theta),
                Math.round(t * 360.0f) + plane * 120
        );
    }

    private static ParticleLayoutSample runes(ParticleLayoutDescriptor.Runes d,
                                               int index,
                                               int count,
                                               float time,
                                               long seed) {
        int pointsPerRune = Math.max(5, count / d.runeCount());
        int rune = Math.floorMod(index / pointsPerRune, d.runeCount());
        int point = index % pointsPerRune;
        float localT = pointsPerRune <= 1 ? 0.0f : point / (float) (pointsPerRune - 1);
        double baseAngle = TAU * rune / d.runeCount() + time * d.speed();
        double bob = Math.sin(time * d.speed() * 1.4 + rune * 1.73) * d.height() * 0.14;
        double baseY = (rune % 3 - 1) * d.height() * 0.22 + bob;

        int glyph = Math.floorMod((int) mix64(seed + rune * 0x9E3779B97F4A7C15L), 4);
        Vec3 local = runePoint(glyph, localT, d.runeSize());

        double radialX = Math.cos(baseAngle) * d.radius();
        double radialZ = Math.sin(baseAngle) * d.radius();
        double tangentX = -Math.sin(baseAngle);
        double tangentZ = Math.cos(baseAngle);

        Vec3 offset = new Vec3(
                radialX + tangentX * local.x,
                baseY + local.y,
                radialZ + tangentZ * local.x
        );
        float pulse = 0.72f + 0.28f * (float) Math.sin(time * 2.4f + rune * 0.9f + localT * TAU);
        return new ParticleLayoutSample(
                offset,
                Mth.clamp(pulse, 0.25f, 1.0f),
                0.72f + 0.28f * pulse,
                (float) Math.toDegrees(baseAngle),
                rune * 47 + Math.round(localT * 90.0f)
        );
    }

    private static Vec3 runePoint(int glyph, float t, float scale) {
        double a;
        double x;
        double y;
        switch (glyph) {
            case 0 -> {
                a = TAU * t;
                x = Math.cos(a) * 0.55;
                y = Math.sin(a) * 0.55;
            }
            case 1 -> {
                double u = t * 4.0;
                int segment = Math.min(3, (int) u);
                double f = u - segment;
                double[][] p = {{-0.55, -0.55}, {0.0, 0.58}, {0.55, -0.55}, {-0.55, 0.08}, {-0.55, -0.55}};
                x = lerp(p[segment][0], p[segment + 1][0], f);
                y = lerp(p[segment][1], p[segment + 1][1], f);
            }
            case 2 -> {
                a = TAU * t * 2.0;
                double r = 0.18 + 0.42 * t;
                x = Math.cos(a) * r;
                y = Math.sin(a) * r;
            }
            default -> {
                double u = t * 3.0;
                int segment = Math.min(2, (int) u);
                double f = u - segment;
                double[][] p = {{-0.52, 0.48}, {0.42, 0.0}, {-0.52, -0.48}, {0.18, 0.0}};
                x = lerp(p[segment][0], p[segment + 1][0], f);
                y = lerp(p[segment][1], p[segment + 1][1], f);
            }
        }
        return new Vec3(x * scale, y * scale, 0.0);
    }

    private static ParticleLayoutSample pulse(ParticleLayoutDescriptor.Pulse d,
                                               int index,
                                               int count,
                                               float time) {
        int ring = index % d.rings();
        int localIndex = index / d.rings();
        int localCount = Math.max(1, (count + d.rings() - 1 - ring) / d.rings());
        float t = localCount <= 1 ? 0.0f : localIndex / (float) localCount;
        float wave = 0.5f + 0.5f * (float) Math.sin(time * d.speed() - ring * 1.6f);
        double radius = d.baseRadius() + d.radiusAmplitude() * wave + ring * d.radiusAmplitude() * 0.18;
        double angle = TAU * t + ring * 0.37;
        double y = d.height() * (ring - (d.rings() - 1) * 0.5) / Math.max(1.0, d.rings());
        return new ParticleLayoutSample(
                new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                0.25f + wave * 0.75f,
                0.62f + wave * 0.7f,
                (float) Math.toDegrees(angle),
                Math.round(t * 360.0f) + ring * 75
        );
    }

    private static ParticleLayoutSample lightning(ParticleLayoutDescriptor.LightningPath d,
                                                   int index,
                                                   int count,
                                                   float time,
                                                   long seed) {
        int branch = index % d.branches();
        int localIndex = index / d.branches();
        int localCount = Math.max(2, (count + d.branches() - 1 - branch) / d.branches());
        float t = localIndex / (float) (localCount - 1);
        long cellSeed = seed ^ ((long) branch << 32) ^ localIndex * 0x9E3779B97F4A7C15L;
        double noiseA = unitNoise(cellSeed) * 2.0 - 1.0;
        double noiseB = unitNoise(cellSeed ^ 0xC2B2AE3D27D4EB4FL) * 2.0 - 1.0;
        double branchAngle = TAU * branch / d.branches() + time * d.speed();
        double angle = branchAngle + d.twist() * (t - 0.5) + noiseA * d.jitter();
        double radius = d.radius() * (0.45 + 0.55 * Math.sin(Math.PI * t)) + noiseB * d.radius() * d.jitter() * 0.2;
        double y = (t - 0.5) * d.height();
        float center = 1.0f - Math.abs(t * 2.0f - 1.0f);
        return new ParticleLayoutSample(
                new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius),
                0.42f + center * 0.58f,
                0.72f + center * 0.45f,
                (float) Math.toDegrees(angle),
                branch * 120 + Math.round(t * 160.0f)
        );
    }

    private static double unitNoise(long value) {
        long x = mix64(value);
        return (x >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
