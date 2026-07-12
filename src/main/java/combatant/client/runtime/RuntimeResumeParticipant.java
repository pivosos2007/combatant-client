/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime;

public interface RuntimeResumeParticipant {
    String id();

    void resume(String reason) throws Exception;
}
