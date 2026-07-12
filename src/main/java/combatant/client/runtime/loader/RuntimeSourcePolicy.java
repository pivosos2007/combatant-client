/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.loader;

public interface RuntimeSourcePolicy {
    boolean mayLoadClientClass(String className);

    boolean mayLoadClientResource(String resourcePath);

    boolean isJarReplacementLocked();
}
