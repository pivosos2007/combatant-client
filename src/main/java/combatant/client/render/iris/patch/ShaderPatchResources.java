/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris.patch;

import combatant.client.util.resources.asset.ResourceAsset;
import combatant.client.util.resources.asset.ResourceCatalog;

/** Shader-patch resources resolved through the central asset metadata registry. */
@ResourceCatalog(namespace = "combatant", root = "shaders/iris-patches")
public enum ShaderPatchResources {
    @ResourceAsset("index.json")
    INDEX
}
