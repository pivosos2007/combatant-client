/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixininterface;

import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.VertexArrayCache;

public interface IGlBackendInfo {
    DirectStateAccess combatant$directStateAccess();

    FrameBufferCache combatant$frameBufferCache();

    VertexArrayCache combatant$vertexArrayCache();

    /** Mojang-resolved GlDevice policy; do not re-probe ARB_direct_state_access in Combatant. */
    boolean combatant$nativeDirectStateAccess();

    /** Mojang-resolved GlDevice policy; do not re-probe KHR_debug in Combatant. */
    boolean combatant$khrDebug();

    /** Raw support flags captured from the exact GLCapabilities instance Mojang creates for GlDevice. */
    boolean combatant$computeShaders();

    boolean combatant$tessellationShaders();

    boolean combatant$geometryShaders();

    boolean combatant$shaderStorageBuffers();

    boolean combatant$imageLoadStore();

    boolean combatant$multiBind();

    boolean combatant$copyImage();

    boolean combatant$attachmentInvalidation();

    /** Driver identity captured from the active Mojang GL context; informational, not a capability re-probe. */
    String combatant$vendor();

    String combatant$renderer();
}
