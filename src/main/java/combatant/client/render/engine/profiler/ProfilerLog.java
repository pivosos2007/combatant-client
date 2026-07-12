/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.profiler;

import combatant.client.util.logging.DebugLog;

enum ProfilerLog {
    ;

    static void info(String message, Object... args) {
        DebugLog.renderThread("[Profiler] " + message, args);
    }

    static void warn(String message, Object... args) {
        DebugLog.warn("[Profiler] " + message, args);
    }
}
