/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a config object is persisted below config/combatant/subsystems.
 * The serializer infers .json/.json5 from the config object type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigSubsystem {
    /** Relative subsystem path, e.g. "visual" or "map/triangulation". */
    String value();

    /** Root-level legacy config names used only when the subsystem file does not exist yet. */
    String[] legacyNames() default {};

    /** SettingOwner name used for existing setting i18n keys. Empty means the subsystem id. */
    String settingOwner() default "";
}
