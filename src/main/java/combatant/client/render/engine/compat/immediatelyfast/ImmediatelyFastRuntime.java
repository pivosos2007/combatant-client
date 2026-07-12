/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.compat.immediatelyfast;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import combatant.client.util.logging.DebugLog;

public enum ImmediatelyFastRuntime {
    ;
    private static final String MOD_ID = "immediatelyfast";
    private static volatile boolean loggedConfigFailure;

    public static ImmediatelyFastRuntimeSnapshot snapshot() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded(MOD_ID)) {
            return ImmediatelyFastRuntimeSnapshot.UNLOADED;
        }

        String version = loader.getModContainer(MOD_ID)
                .map(ImmediatelyFastRuntime::versionOf)
                .orElse("");

        try {
            return ImmediatelyFastBridge.snapshot(version);
        } catch (LinkageError | RuntimeException t) {
            if (!loggedConfigFailure) {
                loggedConfigFailure = true;
                DebugLog.warn("[ImmediatelyFastCompat] Runtime probe failed: " + t);
            }
            return ImmediatelyFastBridge.defaults(version, t.getClass().getSimpleName());
        }
    }

    public static boolean isModLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static boolean framebufferPassesModified() {
        return snapshot().framebufferPassesModified();
    }

    public static boolean guiBatchingModified() {
        return snapshot().guiBatchingModified();
    }

    public static boolean textRenderingModified() {
        return snapshot().textRenderingModified();
    }

    public static boolean transientBufferHandlingModified() {
        return snapshot().transientBufferHandlingModified();
    }

    private static String versionOf(ModContainer mod) {
        return mod.getMetadata().getVersion().getFriendlyString();
    }
}
