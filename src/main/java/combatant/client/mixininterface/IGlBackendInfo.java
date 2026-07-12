/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;

public interface IGlBackendInfo {
    DirectStateAccess combatant$directStateAccess();

    FrameBufferCache combatant$frameBufferCache();
}
