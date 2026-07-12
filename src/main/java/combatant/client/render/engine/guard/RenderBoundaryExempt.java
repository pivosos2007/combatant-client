/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.guard;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a deliberate exception to the post-RHI boundary rules.
 * <p>
 * Every usage should be temporary or isolated. New renderer features should not add this annotation unless the code is
 * a backend/profiler/interop implementation.
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface RenderBoundaryExempt {
    LegacyRenderPath value();

    String reason();
}
