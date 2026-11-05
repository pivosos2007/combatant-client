/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;

/** Overworld celestial model. Exact Minecraft environment attributes are the primary producer. */
public final class OverworldCelestialModel implements CelestialModel {
    public static final OverworldCelestialModel INSTANCE = new OverworldCelestialModel();
    private static final Identifier ID = DimensionRenderProfileRegistry.OVERWORLD_CELESTIAL;
    private static final double DAY_TICKS = 24000.0;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private OverworldCelestialModel() {
    }

    @Override
    public Identifier id() {
        return ID;
    }

    /** Compatibility entry point; registry-driven capture should be preferred. */
    public static CelestialState capture(ClientLevel level, float partialTick) {
        return INSTANCE.capture(new EnvironmentCaptureContext(
                level, null, net.minecraft.world.phys.Vec3.ZERO, partialTick,
                BiomeClimateState.EMPTY, WeatherState.NONE, Long.MIN_VALUE
        ));
    }

    @Override
    public CelestialState capture(EnvironmentCaptureContext context) {
        ClientLevel level = context != null ? context.level() : null;
        if (level == null) return CelestialState.NONE;

        float partialTick = context.partialTick();
        double clock = level.getOverworldClockTime() + partialTick;
        double dayTime = floorMod(clock, DAY_TICKS);
        float fraction = (float) (dayTime / DAY_TICKS);

        float fallbackSunAngle = fraction * (float) (Math.PI * 2.0) - (float) (Math.PI * 0.5);
        float sunAngle = fallbackSunAngle;
        float moonAngle = fallbackSunAngle + (float) Math.PI;
        float starAngle = fallbackSunAngle;
        float starBrightness = fallbackStarBrightness(directionY(sunAngle));
        int moonPhase = (int) Math.floorMod((long) Math.floor(clock / DAY_TICKS), 8L);
        boolean attributesValid = false;

        Camera camera = context.camera();
        EnvironmentAttributeProbe probe = camera != null ? camera.attributeProbe() : null;
        if (probe != null) {
            try {
                Float sunDegrees = probe.getValue(EnvironmentAttributes.SUN_ANGLE, partialTick);
                Float moonDegrees = probe.getValue(EnvironmentAttributes.MOON_ANGLE, partialTick);
                Float starDegrees = probe.getValue(EnvironmentAttributes.STAR_ANGLE, partialTick);
                Float stars = probe.getValue(EnvironmentAttributes.STAR_BRIGHTNESS, partialTick);
                MoonPhase phase = probe.getValue(EnvironmentAttributes.MOON_PHASE, partialTick);
                if (sunDegrees != null && moonDegrees != null && starDegrees != null && stars != null && phase != null
                        && Float.isFinite(sunDegrees) && Float.isFinite(moonDegrees)
                        && Float.isFinite(starDegrees) && Float.isFinite(stars)) {
                    sunAngle = sunDegrees * DEG_TO_RAD;
                    moonAngle = moonDegrees * DEG_TO_RAD;
                    starAngle = starDegrees * DEG_TO_RAD;
                    starBrightness = clamp01(stars);
                    moonPhase = phase.index();
                    attributesValid = true;
                }
            } catch (Throwable ignored) {
            }
        }

        CelestialRenderProfile profile = CelestialRenderProfileRegistry.resolve(ID);
        if (profile == null || !profile.valid()) return CelestialState.NONE;

        float sunX = directionX(sunAngle);
        float sunY = directionY(sunAngle);
        float moonX = directionX(moonAngle);
        float moonY = directionY(moonAngle);
        boolean sunAboveHorizon = sunY > 0.0f;
        boolean moonAboveHorizon = moonY > 0.0f;

        DirectionalLightDescriptor sun = DirectionalLightDescriptor.of(
                sunX, sunY, 0.0f,
                profile.sunRadianceRed(), profile.sunRadianceGreen(), profile.sunRadianceBlue(),
                profile.sunAngularRadiusRadians(), sunAboveHorizon
        );

        float lunarIllumination = lunarIllumination(moonPhase);
        float moonDirectScale = profile.moonDirectPhaseFloor()
                + (1.0f - profile.moonDirectPhaseFloor())
                * (float) Math.pow(lunarIllumination, profile.moonDirectPhaseExponent());
        DirectionalLightDescriptor moon = DirectionalLightDescriptor.of(
                moonX, moonY, 0.0f,
                profile.moonRadianceRed() * moonDirectScale,
                profile.moonRadianceGreen() * moonDirectScale,
                profile.moonRadianceBlue() * moonDirectScale,
                profile.moonAngularRadiusRadians(), moonAboveHorizon
        );

        DirectionalLightDescriptor primary = sunAboveHorizon
                ? sun
                : (moonAboveHorizon ? moon : DirectionalLightDescriptor.NONE);
        return new CelestialState(
                ID, clock, fraction,
                sunAngle, moonAngle, starAngle, starBrightness,
                sun, moon, primary, moonPhase, attributesValid, true
        );
    }

    private static float directionX(float angle) {
        return -(float) Math.sin(angle);
    }

    private static float directionY(float angle) {
        return (float) Math.cos(angle);
    }

    private static float lunarIllumination(int phase) {
        float angle = Math.floorMod(phase, 8) * ((float) Math.PI * 0.25f);
        return clamp01(0.5f + 0.5f * (float) Math.cos(angle));
    }

    private static float fallbackStarBrightness(float sunY) {
        return clamp01((-sunY - 0.08f) / 0.25f);
    }

    private static double floorMod(double value, double modulus) {
        return value - Math.floor(value / modulus) * modulus;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
