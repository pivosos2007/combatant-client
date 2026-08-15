/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ClientBound {
    ClientBoundLevel value() default ClientBoundLevel.FULL;

    String[] packages() default {};

    String[] resources() default {};

    String[] exposedPackages() default {};

    String[] isolatedEntrypoints() default {};

    String[] isolatedPackages() default {};
}
