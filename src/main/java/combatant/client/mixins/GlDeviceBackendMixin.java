/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import combatant.client.mixininterface.IGlBackendInfo;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class GlDeviceBackendMixin implements IGlBackendInfo {
    @Override
    @Invoker("directStateAccess")
    public abstract DirectStateAccess combatant$directStateAccess();

    @Override
    @Invoker("frameBufferCache")
    public abstract FrameBufferCache combatant$frameBufferCache();
}
