/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.loader;

import java.nio.file.Path;

public interface LoaderBridge {
    boolean isStandardFabric();

    boolean supportsSafeJarReplacement();

    default boolean supportsManagedRuntime() {
        return false;
    }

    default boolean suspendManagedRuntime(String reason) {
        return false;
    }

    default boolean resumeManagedRuntime(String reason) {
        return false;
    }

    Path currentModJarPath();

    String describeClassSource(String className);

    String describeResourceSource(String resourcePath);
}
