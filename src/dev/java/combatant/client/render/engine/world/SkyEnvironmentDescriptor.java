/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.world;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Versioned semantic state of one renderer-owned HDR sky environment. */
public record SkyEnvironmentDescriptor(
        Identifier providerId,
        long contentVersion,
        boolean valid,
        UpdatePolicy updatePolicy
) {
    public enum UpdatePolicy {
        STATIC,
        ON_CHANGE,
        EVERY_FRAME
    }

    public static final SkyEnvironmentDescriptor NONE = new SkyEnvironmentDescriptor(
            WorldRenderState.NONE, 0L, false, UpdatePolicy.ON_CHANGE
    );

    public SkyEnvironmentDescriptor {
        providerId = Objects.requireNonNullElse(providerId, WorldRenderState.NONE);
        updatePolicy = updatePolicy == null ? UpdatePolicy.ON_CHANGE : updatePolicy;
    }

    public boolean requiresUpdate(long previouslyRenderedVersion) {
        return switch (updatePolicy) {
            case STATIC -> previouslyRenderedVersion == Long.MIN_VALUE;
            case ON_CHANGE -> contentVersion != previouslyRenderedVersion;
            case EVERY_FRAME -> true;
        };
    }
}
