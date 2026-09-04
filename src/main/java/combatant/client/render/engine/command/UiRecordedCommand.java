/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;
import combatant.client.render.engine.renderer.ui.clip.UiScissorSnapshot;

/** A normalized command plus the complete immutable clip state at its recording boundary. */
public record UiRecordedCommand(UiCommand command,
                                UiScissorSnapshot scissorSnapshot,
                                UiClipSnapshot clipSnapshot) {
    public UiRecordedCommand {
        if (command == null) throw new IllegalArgumentException("command");
        scissorSnapshot = scissorSnapshot != null ? scissorSnapshot : UiScissorSnapshot.NONE;
        clipSnapshot = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
    }
}
