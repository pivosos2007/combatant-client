/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import java.util.Locale;

public enum IrisCompatibilityProfiles {
    ;

    public static IrisCompatibilityProfile resolve(String shaderpackName, boolean shaderpackInUse) {
        if (!shaderpackInUse) {
            return IrisCompatibilityProfile.NONE;
        }
        if (isComplementaryReimagined(shaderpackName)) {
            return IrisCompatibilityProfile.COMPLEMENTARY_REIMAGINED;
        }
        if (isPhoton(shaderpackName)) {
            return IrisCompatibilityProfile.PHOTON;
        }
        return IrisCompatibilityProfile.GENERIC_IRIS;
    }

    private static boolean isComplementaryReimagined(String shaderpackName) {
        if (shaderpackName == null || shaderpackName.isBlank()) {
            return false;
        }
        String normalized = normalize(shaderpackName);
        return normalized.contains("complementary")
                && (normalized.contains("reimagined") || normalized.contains("reimagined latest"));
    }

    private static boolean isPhoton(String shaderpackName) {
        if (shaderpackName == null || shaderpackName.isBlank()) {
            return false;
        }
        return normalize(shaderpackName).contains("photon");
    }

    private static String normalize(String shaderpackName) {
        return shaderpackName.toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replace('_', ' ')
                .replace('+', ' ');
    }
}
