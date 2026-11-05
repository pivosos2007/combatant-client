/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.engine.world.WorldRenderState;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit exposure-profile registry. Unknown identifiers resolve to a neutral profile. */
public final class ExposureProfileRegistry {
    public static final Identifier NEUTRAL = WorldRenderState.NEUTRAL;
    public static final Identifier OVERWORLD = id("overworld_exposure");
    public static final Identifier NETHER = id("nether_exposure");
    public static final Identifier END = id("end_exposure");

    private static final Map<Identifier, ExposureProfile> PROFILES = new ConcurrentHashMap<>();
    private static final ExposureProfile NEUTRAL_PROFILE = neutral(NEUTRAL);

    static {
        register(NEUTRAL_PROFILE);
        register(neutral(OVERWORLD));
        register(neutral(NETHER));
        register(neutral(END));
    }

    private ExposureProfileRegistry() {
    }

    public static void register(ExposureProfile profile) {
        if (profile == null || profile.id() == null) return;
        PROFILES.put(profile.id(), profile);
    }

    public static ExposureProfile resolve(Identifier id) {
        if (id == null) return NEUTRAL_PROFILE;
        return PROFILES.getOrDefault(id, NEUTRAL_PROFILE);
    }

    private static ExposureProfile neutral(Identifier id) {
        return new ExposureProfile(
                id,
                -8.0f, 16.0f,
                0.02f, 0.98f, 0.50f,
                ExposureProfile.MeteringPolicy.CLIPPED_LOG_AVERAGE,
                1.5f, 3.0f,
                0.0f,
                ExposureProfile.ResetPolicy.TARGET_IMMEDIATE,
                0.0f,
                ExposureProfile.WeightingPolicy.UNIFORM,
                1.0f, 1.0f
        );
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("combatant", path);
    }
}
