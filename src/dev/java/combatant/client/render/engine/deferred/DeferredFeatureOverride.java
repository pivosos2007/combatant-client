/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.deferred;

/** Tri-state smoke-test override. DEFAULT preserves normal renderer/profile policy. */
public enum DeferredFeatureOverride {
    DEFAULT,
    FORCE_ENABLED,
    FORCE_DISABLED
}
