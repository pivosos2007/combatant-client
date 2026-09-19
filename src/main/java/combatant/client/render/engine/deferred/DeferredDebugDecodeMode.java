/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Generic display transforms understood by the single deferred debug compositor. */
public enum DeferredDebugDecodeMode {
    REGULAR_COLOR(0),
    SCALAR(1),
    SIGNED_VELOCITY(2),
    DEPTH(3),
    INTEGER_ID(4),
    CONFIDENCE_MASK(5),
    NORMAL_OCT(6),
    VOLUME_COLOR(7),
    VOLUME_SCALAR(8),
    EXPOSURE_VALUE(9),
    HISTOGRAM(10),

    /** Raw octahedral payload as RG for producer-side inspection. */
    NORMAL_OCT_ENCODED(11),
    /** Decoded-normal unit-length/finite diagnostic. */
    NORMAL_LENGTH_ERROR(12),
    /** View-linear depth shown with logarithmic far-plane normalization. */
    LINEAR_DEPTH(13),
    /** Packed renderer-owned baseline block/sky light pair (R=block, G=sky). */
    LIGHT_PAIR(14),
    /** Texture already stores a linear view-space distance/value in R. */
    LINEAR_VALUE(15),
    /** Signed XYZ vector mapped from [-1,1] to display RGB. */
    SIGNED_VECTOR(16),

    /** Current reflection trace eligibility: exactly the shared G-buffer ownership policy. */
    SHARED_REFLECTION_ELIGIBILITY(20),
    /** Reconstructed current view-space position diagnostic. */
    SHARED_RECONSTRUCTED_POSITION(21),
    /** Projection(reconstruct(depth)) UV/depth residual. */
    SHARED_REPROJECTION_ERROR(22),
    /** Local view-depth discontinuity diagnostic. */
    SHARED_DEPTH_DISCONTINUITY(23),
    /** Render-resolution to output-resolution mapping diagnostic. */
    SHARED_UV_SCALE(24);

    private final int shaderId;

    DeferredDebugDecodeMode(int shaderId) {
        this.shaderId = shaderId;
    }

    int shaderId() {
        return shaderId;
    }
}
