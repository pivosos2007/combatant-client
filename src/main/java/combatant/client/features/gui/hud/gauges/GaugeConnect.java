/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.gauges;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GaugeConnect {
    String id();
    String labelKey();
    GaugeRegistry.Readiness readiness() default GaugeRegistry.Readiness.CONTINUOUS;
    float threshold() default 0f;
    GaugeRegistry.Flow flow() default GaugeRegistry.Flow.FILL;
    long hideAfterMillis() default 900L;
}
