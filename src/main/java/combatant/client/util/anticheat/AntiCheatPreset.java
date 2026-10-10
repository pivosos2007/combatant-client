/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.anticheat;

import combatant.client.config.values.EnumValue;

/**
 * Per-module preset for the anticheat on the server. {@link #CUSTOM} leaves every setting as the player set it.
 * {@link #GRIM} caps the settings that GrimAC measures at vanilla limits (they were checked against GrimAC
 * 2.3.74 on a local server). {@link #VANILLA} is for servers with no anticheat beyond vanilla's own checks:
 * reach, range and packet tricks go to their maximum.
 */
public enum AntiCheatPreset implements EnumValue.IdProvider {
    CUSTOM("custom"),
    GRIM("grim"),
    VANILLA("vanilla");

    private final String id;

    AntiCheatPreset(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    /** The player's value, never above {@code grimMax} under GRIM, and {@code vanillaValue} under VANILLA. */
    public double limit(double user, double grimMax, double vanillaValue) {
        return switch (this) {
            case CUSTOM -> user;
            case GRIM -> Math.min(user, grimMax);
            case VANILLA -> vanillaValue;
        };
    }

    public float limit(float user, float grimMax, float vanillaValue) {
        return (float) limit((double) user, (double) grimMax, (double) vanillaValue);
    }

    /** One value per preset. */
    public <T> T pick(T user, T grim, T vanilla) {
        return switch (this) {
            case CUSTOM -> user;
            case GRIM -> grim;
            case VANILLA -> vanilla;
        };
    }
}
