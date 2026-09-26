/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

import org.jetbrains.annotations.Nullable;

/** Debug identities for resources still produced by the retained temporal/post services. */
public enum DeferredDebugView {
    OFF(null, SourceKind.NONE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    FINAL_DEPTH(DeferredResource.FINAL_RESOLVED_DEPTH, SourceKind.TEXTURE, DeferredDebugDecodeMode.DEPTH, 0, null),
    FINAL_VELOCITY(DeferredResource.FINAL_VELOCITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, null),
    MOTION_VALIDITY(DeferredResource.FINAL_MOTION_VALIDITY, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    REACTIVE(DeferredResource.FINAL_REACTIVE_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    DISOCCLUSION(DeferredResource.FINAL_DISOCCLUSION_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, null),
    TAA_HISTORY_WEIGHT(DeferredResource.TAA_HISTORY_WEIGHT, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_REJECTION(DeferredResource.TAA_REJECTION_MASK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_CONFIDENCE(DeferredResource.TAA_CONFIDENCE, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    TAA_LOCK(DeferredResource.TAA_LOCK, SourceKind.TEXTURE, DeferredDebugDecodeMode.CONFIDENCE_MASK, 0, DeferredFeature.TAA),
    BLOOM(DeferredResource.BLOOM_COLOR, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, DeferredFeature.BLOOM),
    DOF_COC(DeferredResource.DOF_COC, SourceKind.TEXTURE, DeferredDebugDecodeMode.SCALAR, 0, DeferredFeature.DEPTH_OF_FIELD),
    MOTION_TILE_MAX(DeferredResource.MOTION_TILE_MAX, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, DeferredFeature.MOTION_BLUR),
    MOTION_NEIGHBOR_MAX(DeferredResource.MOTION_NEIGHBOR_MAX, SourceKind.TEXTURE, DeferredDebugDecodeMode.SIGNED_VELOCITY, -1, DeferredFeature.MOTION_BLUR),
    POST_HDR_COLOR(DeferredResource.POST_HDR_COLOR, SourceKind.TEXTURE, DeferredDebugDecodeMode.REGULAR_COLOR, -1, null),
    EXPOSURE_VALUE(DeferredResource.EXPOSURE, SourceKind.BUFFER, DeferredDebugDecodeMode.EXPOSURE_VALUE, -1, DeferredFeature.EXPOSURE),
    EXPOSURE_HISTOGRAM(DeferredResource.EXPOSURE_HISTOGRAM, SourceKind.BUFFER, DeferredDebugDecodeMode.HISTOGRAM, -1, DeferredFeature.EXPOSURE);

    public enum SourceKind { NONE, TEXTURE, UINT_TEXTURE, VOLUME, BUFFER, SHARED_INPUTS }

    private final @Nullable DeferredResource resource;
    private final SourceKind sourceKind;
    private final DeferredDebugDecodeMode decodeMode;
    private final int channel;
    private final @Nullable DeferredFeature feature;

    DeferredDebugView(@Nullable DeferredResource resource, SourceKind sourceKind,
                      DeferredDebugDecodeMode decodeMode, int channel, @Nullable DeferredFeature feature) {
        this.resource = resource;
        this.sourceKind = sourceKind;
        this.decodeMode = decodeMode;
        this.channel = channel;
        this.feature = feature;
    }

    public @Nullable DeferredResource resource() { return resource; }
    public SourceKind sourceKind() { return sourceKind; }
    public DeferredDebugDecodeMode decodeMode() { return decodeMode; }
    public int channel() { return channel; }
    public @Nullable DeferredFeature feature() { return feature; }
}
