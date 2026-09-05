/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.compat.xaero;

import java.util.List;

/** A Combatant subsystem that can expose immutable waypoints to Xaero adapters. */
public interface XaeroWaypointSource {
    /** Stable namespace used to distinguish this source from every other subsystem. */
    String id();

    List<XaeroWaypointSnapshot> snapshots();
}
