/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.renderer.ui.runtime.script;

/** Shared entry point for subsystems that reuse Combatant's bundled Javet V8 runtime. */
public final class JavetRuntimeBootstrap {
    private JavetRuntimeBootstrap() {
    }

    public static void installNativeLoader() {
        JavetNativeResourceLoader.install();
    }
}
