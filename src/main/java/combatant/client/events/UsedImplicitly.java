/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks code that is intentionally reached through reflection, Fabric entrypoints,
 * addon API calls, event dispatch, serialization, or other non-direct call paths.
 *
 * <p>This is a source-level documentation and IntelliJ IDEA inspection hint. It is
 * not a runtime contract and should not be used for behavior checks.</p>
 */
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.TYPE
})
public @interface UsedImplicitly {
}
