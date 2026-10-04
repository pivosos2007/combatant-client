/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

public enum EntityCullingCompat {
    ;

    private static final String MOD_ID = "entityculling";

    // EntityCulling moved Cullable between releases; a hard import crashes the render
    // thread with NoClassDefFoundError whenever the installed version uses the other package.
    private static final String[] CULLABLE_CLASS_NAMES = {
            "dev.tr7zw.entityculling.versionless.access.Cullable",
            "dev.tr7zw.entityculling.access.Cullable"
    };

    private static boolean resolved;
    private static Class<?> cullableClass;
    private static MethodHandle setCulled;
    private static MethodHandle setOutOfCamera;
    private static MethodHandle setTimeout;

    public static void forceVisibleForShaderEsp(Entity entity) {
        if (entity == null || !resolve() || !cullableClass.isInstance(entity)) {
            return;
        }

        try {
            setCulled.invoke(entity, false);
            setOutOfCamera.invoke(entity, false);
            setTimeout.invoke(entity);
        } catch (Throwable t) {
            // Signature drifted in some future EntityCulling; stop trying instead of spamming every frame.
            cullableClass = null;
        }
    }

    private static boolean resolve() {
        if (!resolved) {
            resolved = true;
            if (FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                lookupCullable();
            }
        }
        return cullableClass != null;
    }

    private static void lookupCullable() {
        ClassLoader loader = EntityCullingCompat.class.getClassLoader();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (String name : CULLABLE_CLASS_NAMES) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                setCulled = lookup.findVirtual(type, "setCulled", MethodType.methodType(void.class, boolean.class));
                setOutOfCamera = lookup.findVirtual(type, "setOutOfCamera", MethodType.methodType(void.class, boolean.class));
                setTimeout = lookup.findVirtual(type, "setTimeout", MethodType.methodType(void.class));
                cullableClass = type;
                return;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // try the next known location
            }
        }
    }
}
