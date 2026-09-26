/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import java.util.Objects;

/** Owns the lifetime generation used by every Iris-imported scene resource. */
final class IrisIntegrationEpochTracker {
    private long epoch = 1L;
    private String reason = "startup";
    private RuntimeIdentity runtimeIdentity;
    private boolean frameIdentityKnown;
    private Object worldOwner;
    private String dimensionId = "";
    private int width;
    private int height;
    private boolean pipelineKnown;
    private Object pipelineOwner;

    synchronized IrisRuntimeSnapshot stamp(IrisRuntimeSnapshot observed) {
        RuntimeIdentity next = RuntimeIdentity.from(observed);
        if (runtimeIdentity != null && !runtimeIdentity.equals(next)) {
            advance("shaderpack_state_changed");
        }
        runtimeIdentity = next;
        return observed.withIntegrationEpoch(epoch, reason);
    }

    synchronized void observeFrame(Object nextWorldOwner, String nextDimensionId, int nextWidth, int nextHeight) {
        String safeDimension = nextDimensionId == null ? "" : nextDimensionId;
        int safeWidth = Math.max(1, nextWidth);
        int safeHeight = Math.max(1, nextHeight);
        if (frameIdentityKnown) {
            if (worldOwner != nextWorldOwner) {
                advance("world_switch");
            } else if (!Objects.equals(dimensionId, safeDimension)) {
                advance("dimension_switch");
            } else if (width != safeWidth || height != safeHeight) {
                advance("resize");
            }
        }
        frameIdentityKnown = true;
        worldOwner = nextWorldOwner;
        dimensionId = safeDimension;
        width = safeWidth;
        height = safeHeight;
    }

    synchronized void observePipeline(Object nextPipelineOwner) {
        if (nextPipelineOwner == null) return;
        if (pipelineKnown && pipelineOwner != nextPipelineOwner) {
            advance("pipeline_recreated");
        }
        pipelineKnown = true;
        pipelineOwner = nextPipelineOwner;
    }

    synchronized void pipelineDestroyed(Object destroyedPipelineOwner) {
        if (!pipelineKnown || pipelineOwner != destroyedPipelineOwner) return;
        pipelineOwner = null;
        pipelineKnown = false;
        advance("pipeline_destroyed");
    }

    synchronized void invalidate(String nextReason) {
        advance(nextReason == null || nextReason.isBlank() ? "external_invalidation" : nextReason);
    }

    synchronized long epoch() {
        return epoch;
    }

    synchronized String reason() {
        return reason;
    }

    private void advance(String nextReason) {
        epoch = epoch == Long.MAX_VALUE ? 1L : epoch + 1L;
        reason = nextReason;
    }

    private record RuntimeIdentity(boolean apiAvailable,
                                   boolean shadersEnabled,
                                   boolean shaderpackInUse,
                                   String shaderpackName,
                                   String manifestId) {
        private static RuntimeIdentity from(IrisRuntimeSnapshot snapshot) {
            return new RuntimeIdentity(
                    snapshot.apiAvailable(),
                    snapshot.shadersEnabled(),
                    snapshot.shaderpackInUse(),
                    snapshot.shaderpackName(),
                    snapshot.patchManifestId()
            );
        }
    }
}
