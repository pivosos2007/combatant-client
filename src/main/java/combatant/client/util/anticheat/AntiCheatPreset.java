/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.anticheat;

import combatant.client.config.values.EnumValue;

import java.util.List;

/**
 * Per-module preset for the anticheat on the server. Picking a preset in the GUI writes its recommended values into
 * the module's settings once (see {@link AntiCheatPresetDefaults}); after that every setting is the player's and is
 * used as set, even past the preset's recommendation. {@link #GRIM} additionally keeps the timing and packet
 * behaviour GrimAC accepts (checked against GrimAC 2.3.74 on a local server). {@link #VANILLA} is for servers with no
 * anticheat beyond vanilla's own checks.
 */
public enum AntiCheatPreset implements EnumValue.IdProvider, EnumValue.AliasProvider {
    GRIM("grim"),
    VANILLA("vanilla");

    /** GrimAC Reach: the entity_interaction_range attribute, 3.0 in survival. */
    public static final double GRIM_ENTITY_RANGE = 3.0;
    /**
     * GrimAC FarPlace/FarBreak/RotationPlace: the block_interaction_range attribute, 4.5 in survival, measured from
     * the eye to the nearest point of the clicked block.
     */
    public static final double GRIM_BLOCK_RANGE = 4.5;
    /** Vanilla server: entity interactions within entity_interaction_range + 3.0 of the target's box. */
    public static final double VANILLA_ENTITY_RANGE = 6.0;
    /** Vanilla server: use-item-on and dig actions within block_interaction_range + 1.0 of the block's box. */
    public static final double VANILLA_BLOCK_RANGE = 5.5;

    private final String id;

    AntiCheatPreset(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    /** Configs saved before the custom preset was removed: it applied no anticheat behaviour, like vanilla. */
    @Override
    public List<String> aliases() {
        return this == VANILLA ? List.of("custom") : List.of();
    }
}
