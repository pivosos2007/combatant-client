/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.anticheat;

import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The values a module recommends for each {@link AntiCheatPreset}. They are written into the module's settings once,
 * when the player picks a preset; the settings are never overridden at runtime, so the player can still move any of
 * them past the recommendation afterwards (for example a reach above 3.0 on a lenient GrimAC config).
 *
 * <pre>{@code
 * AntiCheatPresetDefaults.bind(anticheat)
 *         .cap(range, AntiCheatPreset.GRIM_ENTITY_RANGE, AntiCheatPreset.VANILLA_ENTITY_RANGE)
 *         .set(rotation, PlacementRotationMode.NORMAL, PlacementRotationMode.NO_ROTATION);
 * }</pre>
 */
public final class AntiCheatPresetDefaults {

    private final List<Consumer<AntiCheatPreset>> rules = new ArrayList<>();

    private AntiCheatPresetDefaults() {
    }

    /** Applies the returned rules whenever the player picks a different option of {@code preset}. */
    public static AntiCheatPresetDefaults bind(EnumValue<AntiCheatPreset> preset) {
        AntiCheatPresetDefaults defaults = new AntiCheatPresetDefaults();
        preset.onSelect(defaults::apply);
        return defaults;
    }

    /**
     * GRIM lowers {@code value} to {@code grimMax} when it is above it; VANILLA sets it to {@code vanillaValue}.
     * Written values stay inside the slider's range.
     */
    public <N extends Number> AntiCheatPresetDefaults cap(NumberValue<N> value, double grimMax, double vanillaValue) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.GRIM) {
                if (value.get().doubleValue() > grimMax) write(value, grimMax);
            } else {
                write(value, vanillaValue);
            }
        });
        return this;
    }

    /** GRIM lowers {@code value} to {@code grimMax} when it is above it; VANILLA leaves it as set. */
    public <N extends Number> AntiCheatPresetDefaults capGrim(NumberValue<N> value, double grimMax) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.GRIM && value.get().doubleValue() > grimMax) write(value, grimMax);
        });
        return this;
    }

    private static <N extends Number> void write(NumberValue<N> value, double target) {
        double bounded = Math.max(value.getMin().doubleValue(), Math.min(value.getMax().doubleValue(), target));
        value.set(value.castToType(bounded));
    }

    /** Sets {@code value} to the preset's recommendation. */
    public <T> AntiCheatPresetDefaults set(ConfigValue<T> value, T grim, T vanilla) {
        rules.add(preset -> value.set(preset == AntiCheatPreset.GRIM ? grim : vanilla));
        return this;
    }

    /** Under VANILLA, replaces {@code value} with {@code vanilla} when {@code when} matches it; GRIM keeps it. */
    public <T> AntiCheatPresetDefaults replaceOnVanilla(ConfigValue<T> value, Predicate<T> when, T vanilla) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.VANILLA && when.test(value.get())) value.set(vanilla);
        });
        return this;
    }

    /** GRIM sets {@code value} to {@code grim}; VANILLA leaves it as set. */
    public <T> AntiCheatPresetDefaults setGrim(ConfigValue<T> value, T grim) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.GRIM) value.set(grim);
        });
        return this;
    }

    /** VANILLA sets {@code value} to {@code vanilla}; GRIM leaves it as set. */
    public <T> AntiCheatPresetDefaults setVanilla(ConfigValue<T> value, T vanilla) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.VANILLA) value.set(vanilla);
        });
        return this;
    }

    /** Under GRIM, replaces {@code value} with {@code grim} when the current choice is {@code unsafe}; VANILLA keeps it. */
    public <T> AntiCheatPresetDefaults replaceOnGrim(ConfigValue<T> value, Predicate<T> unsafe, T grim) {
        rules.add(preset -> {
            if (preset == AntiCheatPreset.GRIM && unsafe.test(value.get())) value.set(grim);
        });
        return this;
    }

    /** Writes every rule's recommendation for {@code preset}. */
    public void apply(AntiCheatPreset preset) {
        for (Consumer<AntiCheatPreset> rule : rules) {
            rule.accept(preset);
        }
    }
}
