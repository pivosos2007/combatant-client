/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import java.util.EnumSet;
import java.util.Set;

public record IrisCompatibilityProfile(
        String id,
        String displayName,
        String targetShaderpack,
        Set<IrisCompatibilityFeature> features
) {
    public static final IrisCompatibilityProfile NONE = new IrisCompatibilityProfile(
            "none",
            "No Iris profile",
            "",
            Set.of()
    );
    public static final IrisCompatibilityProfile GENERIC_IRIS = new IrisCompatibilityProfile(
            "generic_iris",
            "Generic Iris shaderpack",
            "*",
            EnumSet.of(IrisCompatibilityFeature.INTERACTION_POLICY)
    );
    public static final IrisCompatibilityProfile COMPLEMENTARY_REIMAGINED = new IrisCompatibilityProfile(
            "complementary_reimagined",
            "Complementary Reimagined",
            "Complementary Reimagined",
            EnumSet.of(
                    IrisCompatibilityFeature.FOG_POLICY,
                    IrisCompatibilityFeature.LIGHTING_POLICY,
                    IrisCompatibilityFeature.HAND_RENDERING_POLICY,
                    IrisCompatibilityFeature.DEFERRED_FINALIZATION_POLICY,
                    IrisCompatibilityFeature.MOTION_BLUR_POLICY,
                    IrisCompatibilityFeature.INTERACTION_POLICY
            )
    );
    public static final IrisCompatibilityProfile PHOTON = new IrisCompatibilityProfile(
            "photon",
            "Photon",
            "Photon",
            EnumSet.of(
                    IrisCompatibilityFeature.FOG_POLICY,
                    IrisCompatibilityFeature.LIGHTING_POLICY,
                    IrisCompatibilityFeature.HAND_RENDERING_POLICY,
                    IrisCompatibilityFeature.MOTION_BLUR_POLICY,
                    IrisCompatibilityFeature.INTERACTION_POLICY
            )
    );

    public String getId() {
        return id;
    }

    public boolean supports(IrisCompatibilityFeature feature) {
        return feature != null && features.contains(feature);
    }
}
