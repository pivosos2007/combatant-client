/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.compat.immediatelyfast;

public record ImmediatelyFastRuntimeSnapshot(
        boolean modLoaded,
        String version,
        boolean framebufferPassesModified,
        boolean guiBatchingModified,
        boolean textRenderingModified,
        boolean transientBufferHandlingModified,
        boolean configAvailable,
        String status
) {
    public static final ImmediatelyFastRuntimeSnapshot UNLOADED = new ImmediatelyFastRuntimeSnapshot(
            false,
            "",
            false,
            false,
            false,
            false,
            false,
            "immediatelyfast mod not loaded"
    );

    private static String yn(boolean value) {
        return value ? "y" : "n";
    }

    public String shortLine() {
        if (!modLoaded) {
            return "immediatelyfast: not loaded";
        }
        String displayVersion = version == null || version.isBlank() ? "<unknown>" : version;
        String config = configAvailable ? "config" : "defaults";
        return "immediatelyfast: " + displayVersion
                + ", " + config
                + ", fb/gui/text/buf "
                + yn(framebufferPassesModified)
                + "/" + yn(guiBatchingModified)
                + "/" + yn(textRenderingModified)
                + "/" + yn(transientBufferHandlingModified);
    }
}
