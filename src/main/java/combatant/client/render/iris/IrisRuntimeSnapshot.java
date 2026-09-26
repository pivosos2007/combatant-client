/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import java.util.Set;

public record IrisRuntimeSnapshot(
        boolean modLoaded,
        boolean apiAvailable,
        boolean shadersEnabled,
        boolean shaderpackInUse,
        boolean renderingShadowPass,
        String shaderpackName,
        IrisCompatibilityProfile profile,
        String patchManifestId,
        Set<String> patchFeatures,
        long integrationEpoch,
        String integrationEpochReason,
        String status
) {
    public IrisRuntimeSnapshot {
        shaderpackName = shaderpackName == null ? "" : shaderpackName;
        profile = profile == null ? IrisCompatibilityProfile.NONE : profile;
        patchManifestId = patchManifestId == null ? "" : patchManifestId;
        patchFeatures = patchFeatures == null ? Set.of() : Set.copyOf(patchFeatures);
        if (integrationEpoch < 0L) integrationEpoch = 0L;
        integrationEpochReason = integrationEpochReason == null ? "" : integrationEpochReason;
        status = status == null ? "" : status;
    }

    public static final IrisRuntimeSnapshot UNLOADED = new IrisRuntimeSnapshot(
            false,
            false,
            false,
            false,
            false,
            "",
            IrisCompatibilityProfile.NONE,
            "",
            Set.of(),
            0L,
            "iris_unloaded",
            "iris mod not loaded"
    );

    public IrisRuntimeSnapshot withIntegrationEpoch(long epoch, String reason) {
        return new IrisRuntimeSnapshot(
                modLoaded, apiAvailable, shadersEnabled, shaderpackInUse, renderingShadowPass,
                shaderpackName, profile, patchManifestId, patchFeatures, epoch, reason, status
        );
    }

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
                + ", profile " + profile.getId()
                + ", manifest " + (patchManifestId.isBlank() ? "<none>" : patchManifestId)
                + ", epoch " + integrationEpoch;
    }
}
