/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.lod;

import combatant.client.render.engine.scene.spatial.SceneBoundingSphere;
import combatant.client.render.engine.scene.spatial.SceneSpatialRecord;
import combatant.client.render.engine.scene.visibility.SceneViewContext;

/**
 * Resolves LOD from projected screen size without relying on the game-option FOV or TAA jitter.
 */
public final class SceneLodResolver {
    private SceneLodResolver() {
    }

    /**
     * @param previousLevel previous stable level, or -1 when no history exists
     * @param lodBiasStops positive values bias toward coarser LODs; one stop halves effective size
     */
    public static SceneLodSelection resolve(SceneLodProfile profile,
                                            SceneViewContext view,
                                            SceneBoundingSphere sphere,
                                            int previousLevel,
                                            float lodBiasStops) {
        if (profile == null) throw new IllegalArgumentException("LOD profile is required");
        if (view == null) throw new IllegalArgumentException("scene view is required");
        if (sphere == null) throw new IllegalArgumentException("bounding sphere is required");

        float projected = view.projectedSphereDiameterPixels(
                sphere.x(), sphere.y(), sphere.z(), sphere.radius());
        if (!Float.isFinite(projected)) {
            // Missing/degenerate projection is fail-open toward maximum detail, never toward hiding.
            projected = Float.POSITIVE_INFINITY;
        }

        float clampedBias = Float.isFinite(lodBiasStops)
                ? Math.max(-16.0f, Math.min(16.0f, lodBiasStops)) : 0.0f;
        float effective = projected == Float.POSITIVE_INFINITY
                ? projected
                : (float) (projected * Math.pow(2.0, -clampedBias));

        int maxLevel = profile.levelCount() - 1;
        int level;
        if (previousLevel < 0 || previousLevel > maxLevel) {
            level = selectWithoutHistory(profile, effective);
        } else {
            level = previousLevel;
            float hysteresis = profile.hysteresisFraction();

            // Downshift only after crossing below the lower hysteresis edge.
            while (level < maxLevel) {
                float boundary = profile.boundaryToCoarser(level);
                if (!(effective < boundary * (1.0f - hysteresis))) break;
                level++;
            }

            // Upshift only after crossing above the upper hysteresis edge.
            while (level > 0) {
                float boundary = profile.boundaryToCoarser(level - 1);
                if (!(effective > boundary * (1.0f + hysteresis))) break;
                level--;
            }
        }

        return new SceneLodSelection(level, projected, effective, level != previousLevel);
    }

    public static SceneLodSelection resolve(SceneLodProfile profile,
                                            SceneViewContext view,
                                            SceneSpatialRecord record,
                                            int previousLevel,
                                            float lodBiasStops) {
        if (record == null) throw new IllegalArgumentException("spatial record is required");
        return resolve(profile, view, record.sphere(), previousLevel, lodBiasStops);
    }

    private static int selectWithoutHistory(SceneLodProfile profile, float diameterPixels) {
        int level = 0;
        int maxLevel = profile.levelCount() - 1;
        while (level < maxLevel && diameterPixels < profile.boundaryToCoarser(level)) level++;
        return level;
    }
}
