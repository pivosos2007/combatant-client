/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.compat;

import dev.tr7zw.entityculling.versionless.access.Cullable;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

public enum EntityCullingCompat {
    ;

    private static final String MOD_ID = "entityculling";

    public static void forceVisibleForShaderEsp(Entity entity) {
        if (entity == null || !FabricLoader.getInstance().isModLoaded(MOD_ID)) {
            return;
        }
        if (!(entity instanceof Cullable cullable)) {
            return;
        }

        cullable.setCulled(false);
        cullable.setOutOfCamera(false);
        cullable.setTimeout();
    }
}
