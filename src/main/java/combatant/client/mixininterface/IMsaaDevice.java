/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;

@Deprecated
public interface IMsaaDevice {
    GpuTexture combatant$createMsaaTexture(String label,
                                           int usage,
                                           GpuFormat format,
                                           int width,
                                           int height,
                                           int samples);
}
