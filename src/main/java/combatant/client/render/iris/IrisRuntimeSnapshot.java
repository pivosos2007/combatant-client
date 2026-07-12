/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

public record IrisRuntimeSnapshot(
        boolean modLoaded,
        boolean apiAvailable,
        boolean shadersEnabled,
        boolean shaderpackInUse,
        boolean renderingShadowPass,
        String shaderpackName,
        IrisCompatibilityProfile profile,
        String status
) {
    public static final IrisRuntimeSnapshot UNLOADED = new IrisRuntimeSnapshot(
            false,
            false,
            false,
            false,
            false,
            "",
            IrisCompatibilityProfile.NONE,
            "iris mod not loaded"
    );

    public String shortLine() {
        if (!modLoaded) {
            return "iris: not loaded";
        }
        if (!apiAvailable) {
            return "iris: loaded, api unavailable";
        }
        String pack = shaderpackName == null || shaderpackName.isBlank() ? "<unknown>" : shaderpackName;
        return "iris: " + (shadersEnabled ? "on" : "off")
                + ", pack " + (shaderpackInUse ? pack : "<none>")
                + ", profile " + profile.getId();
    }
}
