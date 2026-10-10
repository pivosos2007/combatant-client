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

    private static final boolean ENTITY_CULLING_LOADED =
            FabricLoader.getInstance().isModLoaded("entityculling");

    public static void forceVisibleForShaderEsp(Entity entity) {
        if (entity == null || !ENTITY_CULLING_LOADED) {
            return;
        }
        EntityCullingAccess.forceVisible(entity);
    }

    // This class is only loaded when EntityCulling is installed. Keep its optional
    // API reference out of the outer class so the compat entry point is safe without it.
    private static final class EntityCullingAccess {
        private static void forceVisible(Entity entity) {
            if (!(entity instanceof Cullable cullable)) {
                return;
            }

            cullable.setCulled(false);
            cullable.setOutOfCamera(false);
            cullable.setTimeout();
        }
    }
}
