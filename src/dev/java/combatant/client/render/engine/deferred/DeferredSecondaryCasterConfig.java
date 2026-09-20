/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

/**
 * Quality/budget policy for optional non-terrain shadow casters in secondary views.
 *
 * <p>These switches affect only shadow-caster coverage. They never decide what an
 * object is: entity/block-entity identity comes from Minecraft's typed scene structures.</p>
 */
record DeferredSecondaryCasterConfig(
        boolean directionalEntities,
        boolean localLightEntities,
        boolean directionalBlockEntities,
        boolean localLightBlockEntities,
        int maxEntityQueryCandidates,
        int maxEntityCastersPerView,
        int maxBlockEntityChunksPerView,
        int maxBlockEntityCastersPerView,
        double boundsPadding
) {
    private static final DeferredSecondaryCasterConfig DEFAULT = new DeferredSecondaryCasterConfig(
            true,
            true,
            false,
            false,
            1024,
            384,
            196,
            128,
            2.0
    );

    DeferredSecondaryCasterConfig {
        maxEntityQueryCandidates = clamp(maxEntityQueryCandidates, 32, 8192);
        maxEntityCastersPerView = clamp(maxEntityCastersPerView, 16, maxEntityQueryCandidates);
        maxBlockEntityChunksPerView = clamp(maxBlockEntityChunksPerView, 16, 1024);
        maxBlockEntityCastersPerView = clamp(maxBlockEntityCastersPerView, 8, 2048);
        if (!Double.isFinite(boundsPadding)) boundsPadding = 2.0;
        boundsPadding = Math.max(0.0, Math.min(16.0, boundsPadding));
    }

    static DeferredSecondaryCasterConfig current() {
        return Holder.CURRENT;
    }

    private static DeferredSecondaryCasterConfig fromSystemProperties() {
        return new DeferredSecondaryCasterConfig(
                booleanProperty("combatant.renderer.shadow.entities.directional", DEFAULT.directionalEntities),
                booleanProperty("combatant.renderer.shadow.entities.local", DEFAULT.localLightEntities),
                booleanProperty("combatant.renderer.shadow.block_entities.directional", DEFAULT.directionalBlockEntities),
                booleanProperty("combatant.renderer.shadow.block_entities.local", DEFAULT.localLightBlockEntities),
                intProperty("combatant.renderer.shadow.entities.query_budget", DEFAULT.maxEntityQueryCandidates),
                intProperty("combatant.renderer.shadow.entities.view_budget", DEFAULT.maxEntityCastersPerView),
                intProperty("combatant.renderer.shadow.block_entities.chunk_budget", DEFAULT.maxBlockEntityChunksPerView),
                intProperty("combatant.renderer.shadow.block_entities.view_budget", DEFAULT.maxBlockEntityCastersPerView),
                doubleProperty("combatant.renderer.shadow.caster_bounds_padding", DEFAULT.boundsPadding)
        );
    }

    private static boolean booleanProperty(String key, boolean fallback) {
        String value = System.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double doubleProperty(String key, double fallback) {
        try {
            return Double.parseDouble(System.getProperty(key, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class Holder {
        private static final DeferredSecondaryCasterConfig CURRENT = fromSystemProperties();
    }

    boolean entitiesFor(DeferredViewFamily family) {
        return switch (family) {
            case SHADOW_CASCADE -> directionalEntities;
            case LOCAL_LIGHT_SHADOW -> localLightEntities;
            default -> false;
        };
    }

    boolean blockEntitiesFor(DeferredViewFamily family) {
        return switch (family) {
            case SHADOW_CASCADE -> directionalBlockEntities;
            case LOCAL_LIGHT_SHADOW -> localLightBlockEntities;
            default -> false;
        };
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
