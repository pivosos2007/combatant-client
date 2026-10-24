/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.compat.litematica;

import combatant.client.automation.compat.litematica.impl.LitematicaSource;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Entry point of the optional Litematica integration. Nothing here touches a Litematica class until
 * {@link #source()} found the mod loaded: the implementation class is only reachable through {@link Holder}, which
 * the JVM initialises on first use, so Combatant runs unchanged without Litematica on the classpath.
 */
public final class LitematicaCompat {

    public static final String MOD_ID = "litematica";

    private static volatile boolean failed;

    private LitematicaCompat() {
    }

    /** Whether the Litematica mod is loaded and its adapter did not fail to link. */
    public static boolean isAvailable() {
        return !failed && FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /**
     * The adapter, or null when Litematica is absent or its API does not match what the adapter was built against
     * (a linkage error is remembered, so the broken adapter is not retried every tick).
     */
    public static SchematicSource source() {
        if (!isAvailable()) return null;
        try {
            return Holder.SOURCE;
        } catch (LinkageError | RuntimeException e) {
            failed = true;
            return null;
        }
    }

    /** Loads the adapter lazily; only ever initialised after the mod was seen loaded. */
    private static final class Holder {
        static final SchematicSource SOURCE = new LitematicaSource();
    }
}
