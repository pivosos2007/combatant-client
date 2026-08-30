/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources.asset;

import combatant.client.events.UsedImplicitly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata for one enum constant in a {@link ScriptCatalog}. */
@UsedImplicitly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ScriptAsset {
    String value();

    String addon() default "";

    /** Treat {@link #value()} as a resource directory and load all .js files recursively. */
    boolean tree() default false;

    /** Deterministic order inside one script catalog. */
    int order() default 0;
}
