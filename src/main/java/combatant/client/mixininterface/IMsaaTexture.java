/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

public interface IMsaaTexture {
    void combatant$setSamples(int samples);

    int combatant$getSamples();

    boolean combatant$isMsaa();

    int combatant$getGlId();
}
