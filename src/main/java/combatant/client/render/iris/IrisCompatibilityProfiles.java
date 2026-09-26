/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import combatant.client.render.iris.patch.ShaderPatchEngine;

public enum IrisCompatibilityProfiles {
    ;

    public static IrisCompatibilityProfile resolve(String shaderpackName, boolean shaderpackInUse) {
        if (!shaderpackInUse) {
            return IrisCompatibilityProfile.NONE;
        }
        return resolve(ShaderPatchEngine.profile(shaderpackName), true);
    }

    public static IrisCompatibilityProfile resolve(ShaderPatchEngine.ShaderpackProfile selected,
                                                   boolean shaderpackInUse) {
        if (!shaderpackInUse) return IrisCompatibilityProfile.NONE;
        if (selected == null || !selected.matched()) return IrisCompatibilityProfile.GENERIC_IRIS;
        return switch (selected.profileId()) {
            case "photon" -> IrisCompatibilityProfile.PHOTON;
            case "complementary_reimagined" -> IrisCompatibilityProfile.COMPLEMENTARY_REIMAGINED;
            default -> IrisCompatibilityProfile.GENERIC_IRIS;
        };
    }
}
