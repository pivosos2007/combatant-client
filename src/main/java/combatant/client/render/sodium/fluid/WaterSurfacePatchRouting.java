/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import java.util.concurrent.atomic.AtomicReference;

/** Compatibility facade and renderer-facing diagnostics for water replacement routing. */
public final class WaterSurfacePatchRouting {
    private static final AtomicReference<Diagnostics> DIAGNOSTICS =
            new AtomicReference<>(Diagnostics.compatibility("not_initialized"));

    private WaterSurfacePatchRouting() { }

    /** Controls ownership for newly-started Sodium section builds. Existing sections keep their captured owner. */
    public static boolean replacementActive() { return SurfacePatchRouting.waterReplacementActive(); }
    public static boolean setReplacementActive(boolean active) { return SurfacePatchRouting.setWaterReplacementActive(active); }

    public static Diagnostics diagnostics() { return DIAGNOSTICS.get(); }

    public static void publishDiagnostics(Diagnostics diagnostics) {
        DIAGNOSTICS.set(diagnostics == null ? Diagnostics.compatibility("unknown") : diagnostics);
    }

    public record Diagnostics(
            String renderStatus,
            String drawPath,
            String fallbackReason,
            String reflectionPath,
            String environmentSource,
            int extractedSections,
            int replacementOwnedSections,
            int visibleSections,
            int visibleReplacementOwnedSections,
            long extractedPatches,
            long replacementOwnedPatches,
            long submittedPatches,
            boolean replacementActive,
            boolean sodiumSuppressionForNewBuilds,
            boolean motionMrtActive
    ) {
        public Diagnostics {
            renderStatus = safe(renderStatus, "UNKNOWN");
            drawPath = safe(drawPath, "NONE");
            fallbackReason = safe(fallbackReason, "");
            reflectionPath = safe(reflectionPath, "DISABLED");
            environmentSource = safe(environmentSource, "NEUTRAL");
            extractedSections = Math.max(0, extractedSections);
            replacementOwnedSections = Math.max(0, replacementOwnedSections);
            visibleSections = Math.max(0, visibleSections);
            visibleReplacementOwnedSections = Math.max(0, visibleReplacementOwnedSections);
            extractedPatches = Math.max(0L, extractedPatches);
            replacementOwnedPatches = Math.max(0L, replacementOwnedPatches);
            submittedPatches = Math.max(0L, submittedPatches);
        }

        public static Diagnostics compatibility(String reason) {
            WaterSurfaceExtractor.ExtractionStats stats = WaterSurfaceExtractor.stats();
            return new Diagnostics(
                    "COMPATIBILITY", "SODIUM", reason, "DISABLED", "NEUTRAL",
                    stats.sectionsWithWater(), stats.replacementOwnedSections(), 0, 0,
                    stats.waterPatches(), stats.replacementOwnedPatches(), 0L,
                    WaterSurfacePatchRouting.replacementActive(),
                    WaterSurfacePatchRouting.replacementActive(),
                    false
            );
        }

        private static String safe(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
