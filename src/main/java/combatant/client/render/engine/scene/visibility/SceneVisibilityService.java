/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.visibility;

import combatant.client.render.engine.core.policy.VisibilityProvider;
import combatant.client.render.engine.scene.spatial.SceneBoundingSphere;
import combatant.client.render.engine.scene.spatial.SceneSpatialRecord;
import combatant.client.render.engine.scene.spatial.SceneSpatialRegistry;
import combatant.client.render.engine.scene.spatial.SceneSpatialVisitor;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * View-aware traversal over the shared spatial registry.
 *
 * <p>The service intentionally has two independent rejection layers:</p>
 * <ul>
 *     <li>section visibility: cheap/coarse, no FOV/projection dependency;</li>
 *     <li>stable frustum: camera/FOV/projection-aware fine rejection.</li>
 * </ul>
 *
 * <p>It is synchronous by design for this CUT. The reusable primitive dedup set avoids per-query
 * object allocation after warm-up. If profiling later justifies asynchronous broad-phase work, the
 * correct extension is an immutable/double-buffered spatial snapshot, not querying Sodium from a
 * worker thread.</p>
 */
public final class SceneVisibilityService {
    private final SceneSpatialRegistry registry;
    private final LongOpenHashSet visitedHandles = new LongOpenHashSet();

    private long frameId = Long.MIN_VALUE;
    private long sectionsTested;
    private long sectionsRejected;
    private long recordsConsidered;
    private long duplicateSectionRecordsSuppressed;
    private long largeRecordsConsidered;
    private long frustumRejected;
    private long recordsVisible;

    public SceneVisibilityService(SceneSpatialRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("spatial registry is required");
        this.registry = registry;
    }

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        sectionsTested = 0L;
        sectionsRejected = 0L;
        recordsConsidered = 0L;
        duplicateSectionRecordsSuppressed = 0L;
        largeRecordsConsidered = 0L;
        frustumRejected = 0L;
        recordsVisible = 0L;
        visitedHandles.clear();
    }

    /**
     * Traverses visible records. Returning false from the visitor stops traversal immediately.
     */
    public boolean visitVisible(SceneViewContext view, SceneVisibilityMode mode, SceneSpatialVisitor visitor) {
        if (view == null || visitor == null) return true;
        SceneVisibilityMode effectiveMode = mode == null ? SceneVisibilityMode.SECTION_AND_FRUSTUM : mode;
        beginFrame(view.frameId());
        visitedHandles.clear();

        if (effectiveMode == SceneVisibilityMode.NONE || effectiveMode == SceneVisibilityMode.FRUSTUM_ONLY) {
            return registry.visitAllRecords(record -> visitFine(record, view, effectiveMode.frustum(), visitor));
        }

        VisibilityProvider sectionProvider = view.sectionVisibilityProvider();
        boolean useSectionProvider = view.sectionVisibilityEnabled()
                && sectionProvider != null
                && sectionProvider != VisibilityProvider.ALWAYS_VISIBLE;

        boolean completed = registry.visitSections(section -> {
            sectionsTested++;
            if (useSectionProvider && !sectionProvider.isBoxVisible(section.bounds())) {
                sectionsRejected++;
                return true;
            }
            return section.visitRecords(record -> {
                if (!visitedHandles.add(record.handle())) {
                    duplicateSectionRecordsSuppressed++;
                    return true;
                }
                return visitFine(record, view, effectiveMode.frustum(), visitor);
            });
        });
        if (!completed) return false;

        return registry.visitLargeRecords(record -> {
            if (!visitedHandles.add(record.handle())) return true;
            largeRecordsConsidered++;
            if (useSectionProvider && !sectionProvider.isBoxVisible(record.bounds())) {
                sectionsRejected++;
                return true;
            }
            return visitFine(record, view, effectiveMode.frustum(), visitor);
        });
    }

    public boolean isVisible(SceneViewContext view, SceneVisibilityMode mode, SceneSpatialRecord record) {
        if (view == null || record == null) return true;
        SceneVisibilityMode effectiveMode = mode == null ? SceneVisibilityMode.SECTION_AND_FRUSTUM : mode;
        if (effectiveMode.sectionVisibility() && view.sectionVisibilityEnabled()) {
            VisibilityProvider provider = view.sectionVisibilityProvider();
            if (provider != null && provider != VisibilityProvider.ALWAYS_VISIBLE
                    && !provider.isBoxVisible(record.bounds())) {
                return false;
            }
        }
        return !effectiveMode.frustum() || view.isInStableFrustum(record.bounds());
    }

    /** Projection/FOV-aware helper for CUT 4 LOD and optional tiny-object policies. */
    public float projectedDiameterPixels(SceneViewContext view, SceneSpatialRecord record) {
        if (view == null || record == null) return 0.0f;
        SceneBoundingSphere sphere = record.sphere();
        return view.projectedSphereDiameterPixels(sphere.x(), sphere.y(), sphere.z(), sphere.radius());
    }

    public SceneVisibilityStatsSnapshot statsSnapshot() {
        return new SceneVisibilityStatsSnapshot(
                sectionsTested,
                sectionsRejected,
                recordsConsidered,
                duplicateSectionRecordsSuppressed,
                largeRecordsConsidered,
                frustumRejected,
                recordsVisible
        );
    }

    private boolean visitFine(SceneSpatialRecord record,
                              SceneViewContext view,
                              boolean useFrustum,
                              SceneSpatialVisitor visitor) {
        recordsConsidered++;
        if (useFrustum && !view.isInStableFrustum(record.bounds())) {
            frustumRejected++;
            return true;
        }
        recordsVisible++;
        return visitor.visit(record);
    }
}
